/*
 * Copyright (c) 2009-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.data;

import org.apache.commons.collections15.map.ReferenceMap;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ByteArrayHashKey;
import org.labkey.api.util.Compress;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;

import javax.servlet.ServletContextEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

/*
* User: adam
* Date: Oct 14, 2009
* Time: 6:31:40 PM
*/
public class QueryProfiler
{
    private static final Logger LOG = Logger.getLogger(QueryProfiler.class);
    private static final BlockingQueue<Query> QUEUE = new LinkedBlockingQueue<>(1000);
    private static final QueryProfilerThread THREAD = new QueryProfilerThread();
    private static final Map<String, QueryTracker> QUERIES = new ReferenceMap<>(ReferenceMap.HARD, ReferenceMap.WEAK);
    private static final Object LOCK = new Object();
    private static final Collection<QueryTrackerSet> TRACKER_SETS = new ArrayList<>();

    // All access to these guarded by LOCK
    private static long _requestQueryCount;
    private static long _requestQueryTime;
    private static long _backgroundQueryCount;
    private static long _backgroundQueryTime;
    private static long _uniqueQueryCountEstimate;  // This is a ceiling; true unique count is likely less than this since we're limiting capacity
    private static int _requestCountAtLastReset;
    private static long _upTimeAtLastReset;
    private static boolean _hasBeenReset = false;

    static
    {
        TRACKER_SETS.add(new InvocationQueryTrackerSet());

        TRACKER_SETS.add(new QueryTrackerSet("Total", "highest cumulative execution time", false, true, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCount();
            }
        }));

        TRACKER_SETS.add(new QueryTrackerSet("Avg", "highest average execution time", false, true, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getAverage();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }
        }));

        TRACKER_SETS.add(new QueryTrackerSet("Max", "highest maximum execution time", false, true, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getMax();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }
        }));

        TRACKER_SETS.add(new QueryTrackerSet("Last", "most recent invocation time", false, true, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getLastInvocation();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }

            @Override
            String getFormattedPrimaryStatistic(QueryTracker qt)
            {
                return PageFlowUtil.filter(DateUtil.formatDateTime(new Date(getPrimaryStatisticValue(qt))));
            }
        }));

        // Not displayed, but gives new queries some time to get above one of the other thresholds.  Without this,
        // the first N unique queries would dominate the statistics.
        TRACKER_SETS.add(new QueryTrackerSet("First", "first invocation time", true, false, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getFirstInvocation();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return 0;   // Don't care about secondary sort -- we don't display this anyway
            }
        }));

        initializeCounters();
        THREAD.start();
    }

    private QueryProfiler()
    {
    }

    public static void track(@Nullable DbScope scope, String sql, @Nullable List<Object> parameters, long elapsed, @Nullable StackTraceElement[] stackTrace, boolean requestThread)
    {
        if (null == stackTrace)
            stackTrace = Thread.currentThread().getStackTrace();

        // Don't block if queue is full
        QUEUE.offer(new Query(scope, sql, parameters, elapsed, stackTrace, requestThread));
    }

    public static void resetAllStatistics()
    {
        synchronized (LOCK)
        {
            for (QueryTrackerSet set : TRACKER_SETS)
                set.clear();

            QUERIES.clear();

            initializeCounters();

            _hasBeenReset = true;
        }
    }

    private static void initializeCounters()
    {
        synchronized (LOCK)
        {
            _requestQueryCount = 0;
            _requestQueryTime = 0;
            _backgroundQueryCount = 0;
            _backgroundQueryTime = 0;
            _uniqueQueryCountEstimate = 0;
            _requestCountAtLastReset = ViewServlet.getRequestCount();

            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            if (runtimeBean != null)
                _upTimeAtLastReset = runtimeBean.getUptime();
        }
    }

    public static HttpView getReportView(String statName, String buttonHTML, ActionURLFactory captionURLFactory, ActionURLFactory stackTraceURLFactory)
    {
        for (QueryTrackerSet set : TRACKER_SETS)
        {
            if (set.getCaption().equals(statName))
            {
                StringBuilder sb = new StringBuilder();

                sb.append("\n<table>\n");

                StringBuilder rows = new StringBuilder();

                // Don't update anything while we're rendering the report or vice versa
                synchronized (LOCK)
                {
                    int requests = ViewServlet.getRequestCount() - _requestCountAtLastReset;

                    sb.append("  <tr><td>").append(buttonHTML).append("</td></tr>\n");

                    sb.append("  <tr><td style=\"border-top:1px solid\" colspan=5 align=center>Queries Executed Within HTTP Requests</td></tr>\n");
                    sb.append("  <tr><td>Query Count:</td><td align=\"right\">").append(Formats.commaf0.format(_requestQueryCount)).append("</td>");
                    sb.append("<td width=10>&nbsp;</td>");
                    sb.append("<td>Query Time:</td><td align=\"right\">").append(Formats.commaf0.format(_requestQueryTime)).append("</td>");
                    sb.append("</tr>\n  <tr>");
                    sb.append("<td>Queries per Request:</td><td align=\"right\">").append(Formats.f1.format((double) _requestQueryCount / requests)).append("</td>");
                    sb.append("<td width=10>&nbsp;</td>");
                    sb.append("<td>Query Time per Request:</td><td align=\"right\">").append(Formats.f1.format((double) _requestQueryTime / requests)).append("</td>");
                    sb.append("</tr>\n  <tr>");
                    sb.append("<td>").append(_hasBeenReset ? "Request Count Since Last Reset" : "Request Count").append(":</td><td align=\"right\">").append(Formats.commaf0.format(requests)).append("</td></tr>\n");
                    sb.append("  <tr><td style=\"border-top:1px solid\" colspan=5>&nbsp;</td></tr>\n");

                    sb.append("  <tr><td style=\"border-top:1px solid\" colspan=5 align=center>Queries Executed Within Background Threads</td></tr>\n");
                    sb.append("  <tr><td>Query Count:</td><td align=\"right\">").append(Formats.commaf0.format(_backgroundQueryCount)).append("</td>");
                    sb.append("<td width=10>&nbsp;</td>");
                    sb.append("<td>Query Time:</td><td align=\"right\">").append(Formats.commaf0.format(_backgroundQueryTime)).append("</td>");
                    sb.append("</tr>\n");
                    sb.append("  <tr><td style=\"border-top:1px solid\" colspan=5>&nbsp;</td></tr>\n");
                    sb.append("  <tr><td colspan=5>&nbsp;</td></tr>\n");

                    sb.append("  <tr><td>Total Unique Queries");

                    if (_uniqueQueryCountEstimate > QueryTrackerSet.STANDARD_LIMIT)
                        sb.append(" (Estimate)");

                    sb.append(":</td><td align=\"right\">").append(Formats.commaf0.format(_uniqueQueryCountEstimate)).append("</td>");
                    sb.append("<td width=10>&nbsp;</td>");

                    RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
                    if (runtimeBean != null)
                    {
                        long upTime = runtimeBean.getUptime() - _upTimeAtLastReset;
                        upTime = upTime - (upTime % 1000);
                        sb.append("<td>").append(_hasBeenReset ? "Elapsed Time Since Last Reset" : "Server Uptime").append(":</td><td align=\"right\">").append(DateUtil.formatDuration(upTime)).append("</td>");
                    }
                    sb.append("</tr>\n");
                    sb.append("</table><br><br>\n");

                    sb.append("<table>\n");
                    sb.append("  <tr><td>").append("Unique queries with the ").append(set.getDescription()).append(" (top ").append(Formats.commaf0.format(set.size())).append("):</td></tr>\n");
                    sb.append("</table><br>\n");

                    int row = 0;
                    for (QueryTracker tracker : set)
                        tracker.insertRow(rows, (0 == (++row) % 2) ? "labkey-alternate-row" : "labkey-row", stackTraceURLFactory);
                }

                sb.append("<table cellspacing=0 cellpadding=3>\n");
                QueryTracker.appendRowHeader(sb, set, captionURLFactory);
                sb.append(rows);
                sb.append("</table>\n");

                return new HtmlView(sb.toString());
            }
        }

        return new HtmlView("<font class=\"labkey-error\">Error: Query statistic \"" + PageFlowUtil.filter(statName) + "\" does not exist</font>");
    }


    public static HttpView getStackTraceView(int hashCode, ActionURLFactory executeFactory)
    {
        // Don't update anything while we're rendering the report or vice versa
        synchronized (LOCK)
        {
            QueryTracker tracker = findTracker(hashCode);

            if (null == tracker)
                return new HtmlView("<font class=\"labkey-error\">Error: That query no longer exists</font>");

            StringBuilder sb = new StringBuilder();

            sb.append("<table>\n");
            sb.append("  <tr>\n    <td><b>SQL</b></td>\n    <td style=\"padding-left: 20px;\"><b>SQL&nbsp;With&nbsp;Parameters</b></td>\n  </tr>\n");
            sb.append("  <tr>\n");
            sb.append("    <td>").append(PageFlowUtil.filter(tracker.getSql(), true)).append("</td>\n");
            sb.append("    <td style=\"padding-left: 20px;\">").append(PageFlowUtil.filter(tracker.getSqlAndParameters(), true)).append("</td>\n");
            sb.append("  </tr>\n");
            sb.append("</table>\n<br>\n");

            if (tracker.canShowExecutionPlan())
            {
                sb.append("<table>\n  <tr><td>");
                ActionURL url = executeFactory.getActionURL(tracker.getSql());
                sb.append(PageFlowUtil.textLink("Show Execution Plan", url));
                sb.append("  </td></tr></table>\n<br>\n");
            }

            sb.append("<table>\n");
            tracker.appendStackTraces(sb);
            sb.append("</table>\n");

            return new HtmlView(sb.toString());
        }
    }


    public static HttpView getExecutionPlanView(int hashCode)
    {
        SQLFragment sql;
        DbScope scope;

        // Don't update anything while we're gathering the SQL and parameters
        synchronized (LOCK)
        {
            QueryTracker tracker = findTracker(hashCode);

            if (null == tracker)
                return new HtmlView("<font class=\"labkey-error\">Error: That query no longer exists</font>");

            if (!tracker.canShowExecutionPlan())
                throw new IllegalStateException("Can't show the execution plan for this query");

            scope = tracker.getScope();

            if (null == scope)
                throw new IllegalStateException("Scope should not be null");

            sql = tracker.getSQLFragment();
        }

        Collection<String> executionPlan = scope.getSqlDialect().getExecutionPlan(scope, sql);
        StringBuilder html = new StringBuilder();

        for (String row : executionPlan)
        {
            html.append(PageFlowUtil.filter(row, true));
            html.append("</br>\n");
        }

        return new HtmlView(html.toString());
    }


    private static @Nullable QueryTracker findTracker(int hashCode)
    {
        QueryTracker tracker = null;

        for (QueryTracker candidate : QUERIES.values())
        {
            if (candidate.hashCode() == hashCode)
            {
                tracker = candidate;
                break;
            }
        }

        return tracker;
    }


    public static class QueryStatTsvWriter extends TSVWriter
    {
        protected void write()
        {
            QueryTrackerSet export = new InvocationQueryTrackerSet() {
                protected int getLimit()
                {
                    return Integer.MAX_VALUE;
                }
            };

            StringBuilder rows = new StringBuilder();

            // Don't update anything while we're rendering the report or vice versa
            synchronized (LOCK)
            {
                for (QueryTrackerSet set : TRACKER_SETS)
                    if (set.shouldDisplay())
                        export.addAll(set);

                for (QueryTracker tracker : export)
                    tracker.exportRow(rows);

                long upTime = 0;
                RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
                if (runtimeBean != null)
                {
                    upTime = runtimeBean.getUptime() - _upTimeAtLastReset;
                    upTime = upTime - (upTime % 1000);
                }
                _pw.printf("#Summary - unique queries: %,d, elapsed time: %s\n", _uniqueQueryCountEstimate, DateUtil.formatDuration(upTime));

                int requests = ViewServlet.getRequestCount() - _requestCountAtLastReset;
                _pw.printf("#HTTP Requests - query count: %,d, query time (ms): %,d, request count: %d\n", _requestQueryCount, _requestQueryTime, requests);
                _pw.printf("#Background Threads - query count: %,d, query time (ms): %,d\n", _backgroundQueryCount, _backgroundQueryTime);

                QueryTracker.exportRowHeader(_pw);
                _pw.println(rows);
            }
        }
    }

    private static class Query
    {
        private final @Nullable DbScope _scope;
        private final String _sql;
        private final @Nullable List<Object> _parameters;
        private final long _elapsed;
        private final StackTraceElement[] _stackTrace;
        private final boolean _isRequestThread;
        private Boolean _validSql = null;

        private Query(@Nullable DbScope scope, String sql, @Nullable List<Object> parameters, long elapsed, StackTraceElement[] stackTrace, boolean isRequestThread)
        {
            _scope = scope;
            _sql = sql;
            _parameters = null != parameters ? new ArrayList<>(parameters) : null;    // Make a copy... callers might modify the collection
            _elapsed = elapsed;
            _stackTrace = stackTrace;
            _isRequestThread = isRequestThread;
        }

        @Nullable
        public DbScope getScope()
        {
            return _scope;
        }

        private String getSql()
        {
            // Do any transformations on the SQL on the way out, in the background thread
            return transform(_sql);
        }

        public boolean isValidSql()
        {
            if (null == _validSql)
                throw new IllegalStateException("Must call getSql() before calling isValidSql()");

            return _validSql;
        }

        @Nullable
        private List<Object> getParameters()
        {
            return _parameters;  // TODO: Check parameters? Ignore InputStream, BLOBs, etc.?
        }

        private long getElapsed()
        {
            return _elapsed;
        }

        private String getStackTrace()
        {
            StringBuilder sb = new StringBuilder();

            for (int i = 3; i < _stackTrace.length; i++)
            {
                String line = _stackTrace[i].toString();

                // Ignore all the servlet container stuff, #11159
                // Ignore everything before HttpView.render, standard action classes, etc., #13753
                if  (
                        line.startsWith("org.labkey.api.view.HttpView.render") ||
                        line.startsWith("org.labkey.jsp.compiled.org.labkey.api.view.template.CommonTemplate_jsp._jspService") ||
                        line.startsWith("org.labkey.api.view.WebPartView.renderInternal") ||
                        line.startsWith("org.labkey.api.view.JspView.renderView") ||
                        line.startsWith("org.labkey.api.action.SimpleViewAction.handleRequest") ||
                        line.startsWith("org.labkey.api.action.FormViewAction.handleRequest") ||
                        line.startsWith("org.junit.internal.runners.TestMethodRunner.executeMethodBody") ||
                        line.startsWith("org.apache.catalina.core.ApplicationFilterChain.internalDoFilter") ||
                        line.startsWith("javax.servlet.http.HttpServlet.service")
                    )
                    break;

                sb.append("at ");  // Improves compatibility with IntelliJ "Analyze Stacktrace" feature
                sb.append(line);
                sb.append('\n');
            }

            return sb.toString();
        }

        public boolean isRequestThread()
        {
            return _isRequestThread;
        }


        private static final Pattern TEMP_TABLE_PATTERN = Pattern.compile("([ix_|temp\\.][\\w]+)\\$?\\p{XDigit}{32}");
        private static final Pattern SPECIMEN_TEMP_TABLE_PATTERN = Pattern.compile("(SpecimenUpload)\\d{9}");
        private static final int MAX_SQL_LENGTH = 1000000;  // Arbitrary limit to avoid saving insane SQL, #16646

        // Transform the SQL to improve coalescing, etc.
        private String transform(String in)
        {
            String out;

            if (in.length() > MAX_SQL_LENGTH)
            {
                out = in.substring(0, MAX_SQL_LENGTH);
            }
            else
            {
                // Remove the randomly-generated parts of temp table names
                out = TEMP_TABLE_PATTERN.matcher(in).replaceAll("$1");
                out = SPECIMEN_TEMP_TABLE_PATTERN.matcher(out).replaceAll("$1");
            }

            _validSql = out.equals(in);   // If we changed the SQL then it's no longer valid

            return out;
        }
    }

    private static class QueryTracker
    {
        private final @Nullable DbScope _scope;
        private final String _sql;
        private final boolean _validSql;
        private final long _firstInvocation;
        private final Map<ByteArrayHashKey, AtomicInteger> _stackTraces = new ReferenceMap<>(ReferenceMap.SOFT, ReferenceMap.HARD, true); // Not sure about purgeValues

        private @Nullable List<Object> _parameters = null;  // Keep parameters from the longest running query

        private long _count = 0;
        private long _max = 0;
        private long _cumulative = 0;
        private long _lastInvocation;

        private QueryTracker(@Nullable DbScope scope, @NotNull String sql, long elapsed, String stackTrace, boolean validSql)
        {
            _scope = scope;
            _sql = sql;
            _validSql = validSql;
            _firstInvocation = System.currentTimeMillis();

            addInvocation(elapsed, stackTrace);
        }

        private void addInvocation(long elapsed, String stackTrace)
        {
            _count++;
            _cumulative += elapsed;
            _lastInvocation = System.currentTimeMillis();

            if (elapsed > _max)
                _max = elapsed;

            ByteArrayHashKey compressed = new ByteArrayHashKey(Compress.deflate(stackTrace));
            AtomicInteger frequency = _stackTraces.get(compressed);

            if (null == frequency)
                _stackTraces.put(compressed, new AtomicInteger(1));
            else
                frequency.incrementAndGet();
        }

        @Nullable
        public DbScope getScope()
        {
            return _scope;
        }

        public String getSql()
        {
            return _sql;
        }

        public SQLFragment getSQLFragment()
        {
            return null != _parameters ? new SQLFragment(getSql(), _parameters) : new SQLFragment(getSql());
        }

        public String getSqlAndParameters()
        {
            return getSQLFragment().toString();
        }

        private void setParameters(@Nullable List<Object> parameters)
        {
            _parameters = parameters;
        }

        @Nullable
        public List<Object> getParameters()
        {
            return _parameters;
        }

        public boolean canShowExecutionPlan()
        {
            return null != _scope && _scope.getSqlDialect().canShowExecutionPlan() && _validSql && Table.isSelect(_sql);
        }

        public long getCount()
        {
            return _count;
        }

        public long getMax()
        {
            return _max;
        }

        public long getCumulative()
        {
            return _cumulative;
        }

        public long getFirstInvocation()
        {
            return _firstInvocation;
        }

        public long getLastInvocation()
        {
            return _lastInvocation;
        }

        public long getAverage()
        {
            return _cumulative / _count;
        }

        public int getStackTraceCount()
        {
            return _stackTraces.size();
        }

        public void appendStackTraces(StringBuilder sb)
        {
            // Descending order by occurrences (the value)
            Set<Pair<String, AtomicInteger>> set = new TreeSet<>(new Comparator<Pair<String, AtomicInteger>>() {
                public int compare(Pair<String, AtomicInteger> e1, Pair<String, AtomicInteger> e2)
                {
                    int compare = e2.getValue().intValue() - e1.getValue().intValue();

                    if (0 == compare)
                        compare = e2.getKey().compareTo(e1.getKey());

                    return compare;
                }
            });

            // Save the stacktraces separately to find common prefix
            List<String> stackTraces = new LinkedList<>();

            for (Map.Entry<ByteArrayHashKey, AtomicInteger> entry : _stackTraces.entrySet())
            {
                try
                {
                    String decompressed = Compress.inflate(entry.getKey().getBytes());
                    set.add(new Pair<>(decompressed, entry.getValue()));
                    stackTraces.add(decompressed);
                }
                catch (DataFormatException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }

            int commonLength = 0;
            String formattedCommonPrefix = "";

            if (set.size() > 1)
            {
                String commonPrefix = StringUtilsLabKey.findCommonPrefix(stackTraces);
                int idx = commonPrefix.lastIndexOf('\n');

                if (-1 != idx)
                {
                    commonLength = idx;
                    formattedCommonPrefix = "<b>" + PageFlowUtil.filter(commonPrefix.substring(0, commonLength), true) + "</b>";
                }
            }

            sb.append("<tr><td>").append("<b>Count</b>").append("</td><td style=\"padding-left:10;\">").append("<b>Traces</b>").append("</td></tr>\n");

            int alt = 0;
            String[] classes = new String[]{"labkey-alternate-row", "labkey-row"};

            for (Map.Entry<String, AtomicInteger> entry : set)
            {
                String stackTrace = entry.getKey();
                String formattedStackTrace = formattedCommonPrefix + PageFlowUtil.filter(stackTrace.substring(commonLength), true);
                int count = entry.getValue().get();

                sb.append("<tr class=\"").append(classes[alt]).append("\"><td valign=top align=right>").append(count).append("</td><td style=\"padding-left:10;\">").append(formattedStackTrace).append("</td></tr>\n");
                alt = 1 - alt;
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            QueryTracker that = (QueryTracker) o;

            return _sql.equals(that._sql);
        }

        @Override
        public int hashCode()
        {
            return _sql.hashCode();
        }

        private static void appendRowHeader(StringBuilder sb, QueryTrackerSet currentSet, ActionURLFactory factory)
        {
            sb.append("  <tr>");

            for (QueryTrackerSet set : TRACKER_SETS)
                if (set.shouldDisplay())
                    appendColumnHeader(set.getCaption(), set == currentSet, sb, factory);

            sb.append("<td>");
            sb.append("Traces");
            sb.append("</td><td style=\"padding-left:10;\">");
            sb.append("SQL");
            sb.append("</td>");
            sb.append("<td>");
            sb.append("SQL&nbsp;With&nbsp;Parameters");
            sb.append("</td>");
            sb.append("</tr>\n");
        }

        private static void appendColumnHeader(String name, boolean highlight, StringBuilder sb, ActionURLFactory factory)
        {
            sb.append("<td><a href=\"");
            sb.append(PageFlowUtil.filter(factory.getActionURL(name)));
            sb.append("\">");

            if (highlight)
                sb.append("<b>");

            sb.append(name);

            if (highlight)
                sb.append("</b>");

            sb.append("</a></td>");
        }

        private static void exportRowHeader(PrintWriter pw)
        {
            String tab = "";

            for (QueryTrackerSet set : TRACKER_SETS)
            {
                if (set.shouldDisplay())
                {
                    pw.print(tab);
                    pw.print(set.getCaption());
                    tab = "\t";
                }
            }

            pw.print(tab);
            pw.println("SQL");
            pw.print(tab);
            pw.println("SQL With Parameters");
        }

        private void insertRow(StringBuilder sb, String className, ActionURLFactory factory)
        {
            StringBuilder row = new StringBuilder();
            row.append("  <tr class=\"").append(className).append("\">");

            for (QueryTrackerSet set : TRACKER_SETS)
                if (set.shouldDisplay())
                    row.append("<td valign=top align=right>").append(((QueryTrackerComparator) set.comparator()).getFormattedPrimaryStatistic(this)).append("</td>");

            ActionURL url = factory.getActionURL(getSql());
            row.append("<td valign=top align=right><a href=\"").append(PageFlowUtil.filter(url.getLocalURIString())).append("\">").append(Formats.commaf0.format(getStackTraceCount())).append("</a></td>");
            row.append("<td style=\"padding-left:10;\">").append(PageFlowUtil.filter(getSql(), true)).append("</td>");
            row.append("<td style=\"padding-left:10;\">").append(PageFlowUtil.filter(getSqlAndParameters(), true)).append("</td>");
            row.append("</tr>\n");
            sb.insert(0, row);
        }

        private void exportRow(StringBuilder sb)
        {
            StringBuilder row = new StringBuilder();
            String tab = "";

            for (QueryTrackerSet set : TRACKER_SETS)
            {
                if (set.shouldDisplay())
                {
                    row.append(tab).append((((QueryTrackerComparator)set.comparator()).getFormattedPrimaryStatistic(this)));
                    tab = "\t";
                }
            }

            row.append(tab).append(getSql().trim().replaceAll("(\\s)+", " "));
            row.append(tab).append(getSqlAndParameters().trim().replaceAll("(\\s)+", " ")).append("\n");
            sb.insert(0, row);
        }
    }

    private static class QueryProfilerThread extends Thread implements ShutdownListener
    {
        private QueryProfilerThread()
        {
            setDaemon(true);
            setName(QueryProfilerThread.class.getSimpleName());
            ContextListener.addShutdownListener(this);
        }

        @Override
        public void run()
        {
            try
            {
                //noinspection InfiniteLoopStatement
                while (!interrupted())
                {
                    Query query = QUEUE.take();

                    // Don't update or add while we're rendering the report or vice versa
                    synchronized (LOCK)
                    {
                        if (query.isRequestThread())
                        {
                            _requestQueryCount++;
                            _requestQueryTime += query.getElapsed();
                        }
                        else
                        {
                            _backgroundQueryCount++;
                            _backgroundQueryTime += query.getElapsed();
                        }

                        QueryTracker tracker = QUERIES.get(query.getSql());

                        if (null == tracker)
                        {
                            tracker = new QueryTracker(query.getScope(), query.getSql(), query.getElapsed(), query.getStackTrace(), query.isValidSql());

                            // First instance of this query, so always save its parameters
                            tracker.setParameters(query.getParameters());

                            _uniqueQueryCountEstimate++;

                            for (QueryTrackerSet set : TRACKER_SETS)
                                set.add(tracker);

                            QUERIES.put(query.getSql(), tracker);
                        }
                        else
                        {
                            for (QueryTrackerSet set : TRACKER_SETS)
                                set.beforeUpdate(tracker);

                            tracker.addInvocation(query.getElapsed(), query.getStackTrace());

                            for (QueryTrackerSet set : TRACKER_SETS)
                                set.update(tracker);

                            // Save the parameters of the longest running query
                            if (tracker.getMax() == query.getElapsed())
                                tracker.setParameters(query.getParameters());
                        }
                    }
                }
            }
            catch (InterruptedException e)
            {
                LOG.debug(getClass().getSimpleName() + " is terminating due to interruption");
            }
        }

        // stupid tomcat won't let me construct one of these at shutdown, so stash one statically
        private static final QueryProfiler.QueryStatTsvWriter shutdownWriter = new QueryProfiler.QueryStatTsvWriter();


        public void shutdownPre(ServletContextEvent servletContextEvent)
        {
            interrupt();
        }

        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
            Logger logger = Logger.getLogger(QueryProfilerThread.class);

            if (null != logger)
            {
                Appender appender = logger.getAppender("QUERY_STATS");
                if (null != appender && appender instanceof RollingFileAppender)
                    ((RollingFileAppender)appender).rollOver();
                else
                    LOG.warn("Could not rollover the query stats tsv file--there was no appender named QUERY_STATS, or it is not a RollingFileAppender.");

                StringBuilder buf = new StringBuilder();

                try
                {
                    shutdownWriter.write(buf);
                }
                catch (IOException e)
                {
                    LOG.error("Exception writing query stats", e);
                }

                logger.info(buf.toString());
            }
        }
    }

    private static class QueryTrackerSet extends TreeSet<QueryTracker>
    {
        private static final int STANDARD_LIMIT = 1000;

        private final String _caption;
        private final String _description;
        private final boolean _stable;   // Is this statistic stable, i.e., will it never change once a QueryTracker has been added to the set?
        private final boolean _display;  // Should we display this statistic in the report?

        private QueryTrackerSet(String caption, String description, boolean stable, boolean display, Comparator<? super QueryTracker> comparator)
        {
            super(comparator);
            _caption = caption;
            _description = description;
            _display = display;
            _stable = stable;
        }

        private String getCaption()
        {
            return _caption;
        }

        private String getDescription()
        {
            return _description;
        }

        private boolean shouldDisplay()
        {
            return _display;
        }

        private void beforeUpdate(QueryTracker tracker)
        {
            // If the statistic changes at each update, then we need to remove and re-add it
            if (!_stable)
                remove(tracker);
        }

        private void update(QueryTracker tracker)
        {
            // If the statistic changes at each update, then we need to remove and re-add it
            if (!_stable)
                add(tracker);
        }

        protected int getLimit()
        {
            return STANDARD_LIMIT;
        }

        @Override
        public boolean add(QueryTracker tracker)
        {
            assert size() <= getLimit();

            if (size() == getLimit())
            {
                if (comparator().compare(tracker, first()) < 0)
                    return false;

                remove(first());
            }

            return super.add(tracker);
        }

        @Override
        public String toString()
        {
            return getCaption();
        }
    }

    // Static class since we use this in a couple places
    private static class InvocationQueryTrackerSet extends QueryTrackerSet
    {
        InvocationQueryTrackerSet()
        {
            super("Count", "highest number of invocations", false, true, new QueryTrackerComparator()
            {
                long getPrimaryStatisticValue(QueryTracker qt)
                {
                    return qt.getCount();
                }

                long getSecondaryStatisticValue(QueryTracker qt)
                {
                    return qt.getCumulative();
                }
            });
        }
    }

    public interface ActionURLFactory
    {
        ActionURL getActionURL(String name);
    }

    // Comparator that allows defining a primary and secondary sort order, and ensures the Set
    // "consistent with equals" requirement.  If we didn't compare the sql, the set would reject new
    // queries where a statistic happens to match the value of that statistic in an existing query.
    private static abstract class QueryTrackerComparator implements Comparator<QueryTracker>
    {
        public int compare(QueryTracker qt1, QueryTracker qt2)
        {
            // Can use simple subtraction here since we won't have MAX_VALUE, MIN_VALUE, etc.
            int ret = Long.signum(getPrimaryStatisticValue(qt1) - getPrimaryStatisticValue(qt2));

            if (0 == ret)
                ret = Long.signum(getSecondaryStatisticValue(qt1) - getSecondaryStatisticValue(qt2));

            if (0 == ret)
                ret = qt1.getSql().compareTo(qt2.getSql());

            return ret;
        }

        String getFormattedPrimaryStatistic(QueryTracker qt)
        {
            return Formats.commaf0.format(getPrimaryStatisticValue(qt));
        }

        abstract long getPrimaryStatisticValue(QueryTracker qt);
        abstract long getSecondaryStatisticValue(QueryTracker qt);
    }
}
