/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.mothership.query;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.mothership.MothershipManager;
import org.labkey.mothership.MothershipController;
import org.labkey.mothership.StackTraceDisplayColumn;

import java.sql.Types;
import java.util.*;

/**
 * User: jeckels
 * Date: Mar 28, 2007
 */
public class MothershipSchema extends UserSchema
{
    private static final String SCHEMA_NAME = "mothership";

    public static final String SERVER_INSTALLATIONS_TABLE_NAME = "ServerInstallations";
    public static final String SERVER_SESSIONS_TABLE_NAME = "ServerSessions";
    public static final String EXCEPTION_REPORT_TABLE_NAME = "ExceptionReport";
    public static final String EXCEPTION_STACK_TRACE_TABLE_NAME = "ExceptionStackTrace";
    public static final String SOFTWARE_RELEASES_TABLE_NAME = "SoftwareReleases";

    private static Set<String> TABLE_NAMES = Collections.unmodifiableSet(new LinkedHashSet<String>(
        Arrays.asList(SERVER_INSTALLATIONS_TABLE_NAME, SERVER_SESSIONS_TABLE_NAME, EXCEPTION_REPORT_TABLE_NAME, EXCEPTION_STACK_TRACE_TABLE_NAME)
    ));

    public MothershipSchema(User user, Container container)
    {
        super(SCHEMA_NAME, user, container, MothershipManager.get().getSchema());
    }

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new MothershipSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }


    public TableInfo createTable(String name)
    {
        if (name.equalsIgnoreCase(SERVER_INSTALLATIONS_TABLE_NAME))
        {
            return createServerInstallationTable();
        }
        else if (name.equalsIgnoreCase(SERVER_SESSIONS_TABLE_NAME))
        {
            return createServerSessionTable();
        }
        else if (name.equalsIgnoreCase(EXCEPTION_STACK_TRACE_TABLE_NAME))
        {
            FilteredTable result = createExceptionStackTraceTable();
            return result;
        }
        else if (name.equalsIgnoreCase(SOFTWARE_RELEASES_TABLE_NAME))
        {
            FilteredTable result = createSoftwareReleasesTable();
            return result;
        }
        else if (name.equalsIgnoreCase(EXCEPTION_REPORT_TABLE_NAME))
        {
            FilteredTable result = createExceptionReportTable();
            return result;
        }
        return null;
    }

    public FilteredTable createSoftwareReleasesTable()
    {
        FilteredTable result = new FilteredTable(MothershipManager.get().getTableInfoSoftwareRelease(), getContainer());
        result.wrapAllColumns(true);

        result.getColumn("SVNURL").setWidth("500");

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts("Description"));
        defaultCols.add(FieldKey.fromParts("SVNRevision"));
        defaultCols.add(FieldKey.fromParts("SVNURL"));
        result.setDefaultVisibleColumns(defaultCols);

        result.setDetailsURL(new DetailsURL(new ActionURL(MothershipController.ShowUpdateAction.class, getContainer()), Collections.singletonMap("softwareReleaseId", "SoftwareReleaseId")));

        return result;
    }

    public FilteredTable createServerSessionTable()
    {
        FilteredTable result = new FilteredTable(MothershipManager.get().getTableInfoServerSession(), getContainer());
        result.wrapAllColumns(true);
        result.setTitleColumn("RowId");

        result.getColumn("ServerInstallationId").setFk(new LookupForeignKey("ServerInstallationId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createServerInstallationTable();
            }
        });
        result.getColumn("ServerInstallationId").setCaption("Server");

        ColumnInfo earliestCol = result.getColumn("EarliestKnownTime");
        ColumnInfo latestCol = result.getColumn("LastKnownTime");

        ExprColumn durationCol = new ExprColumn(result, "Duration", new SQLFragment(MothershipManager.get().getDialect().getDateDiff(Calendar.DATE, "LastKnownTime", "EarliestKnownTime")), Types.INTEGER, earliestCol, latestCol);
        result.addColumn(durationCol);

        ExprColumn exceptionCountCol = new ExprColumn(result, "ExceptionCount", new SQLFragment("(SELECT COUNT(*) FROM " + MothershipManager.get().getTableInfoExceptionReport() + " WHERE ServerSessionId = " + ExprColumn.STR_TABLE_ALIAS + ".ServerSessionId)"), Types.INTEGER);
        exceptionCountCol.setFormatString("#.#");
        result.addColumn(exceptionCountCol);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromString("SVNRevision"));
        defaultCols.add(FieldKey.fromString("Duration"));
        defaultCols.add(FieldKey.fromString("LastKnownTime"));
        defaultCols.add(FieldKey.fromString("DatabaseProductName"));
        defaultCols.add(FieldKey.fromString("RuntimeOS"));
        defaultCols.add(FieldKey.fromString("JavaVersion"));
        defaultCols.add(FieldKey.fromString("UserCount"));
        defaultCols.add(FieldKey.fromString("ActiveUserCount"));
        defaultCols.add(FieldKey.fromString("ContainerCount"));
        defaultCols.add(FieldKey.fromString("ExceptionCount"));
        result.setDefaultVisibleColumns(defaultCols);

        ActionURL base = new ActionURL(MothershipController.ShowServerSessionDetailAction.class, getContainer());
        result.setDetailsURL(new DetailsURL(base, Collections.singletonMap("serverSessionId", "ServerSessionId")));

        return result;
    }

    public TableInfo createServerInstallationTable()
    {
        FilteredTable result = new FilteredTable(MothershipManager.get().getTableInfoServerInstallation(), getContainer());
        result.wrapAllColumns(true);

        ActionURL url = new ActionURL(MothershipController.ShowInstallationDetailAction.class, getContainer());
        result.getColumn("ServerHostName").setURL(url + "serverInstallationId=${ServerInstallationId}");

        SQLFragment firstPingSQL = new SQLFragment("(SELECT MIN(EarliestKnownTime) FROM ");
        firstPingSQL.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        firstPingSQL.append(" WHERE ss.ServerInstallationId = ");
        firstPingSQL.append(ExprColumn.STR_TABLE_ALIAS);
        firstPingSQL.append(".ServerInstallationId)");
        result.addColumn(new ExprColumn(result, "FirstPing", firstPingSQL, Types.TIMESTAMP));

        SQLFragment lastPing = new SQLFragment("(SELECT MAX(LastKnownTime) FROM ");
        lastPing.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        lastPing.append(" WHERE ss.ServerInstallationId = ");
        lastPing.append(ExprColumn.STR_TABLE_ALIAS);
        lastPing.append(".ServerInstallationId)");
        result.addColumn(new ExprColumn(result, "LastPing", lastPing, Types.TIMESTAMP));

        SQLFragment exceptionCount = new SQLFragment("(SELECT COUNT(*) FROM ");
        exceptionCount.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        exceptionCount.append(",");
        exceptionCount.append(MothershipManager.get().getTableInfoExceptionReport(), "er");
        exceptionCount.append(" WHERE ss.ServerInstallationId = ");
        exceptionCount.append(ExprColumn.STR_TABLE_ALIAS);
        exceptionCount.append(".ServerInstallationId AND ss.serversessionid = er.serversessionid)");
        ExprColumn exceptionCountCol = new ExprColumn(result, "ExceptionCount", exceptionCount, Types.INTEGER);
        exceptionCountCol.setFormatString("#.#");
        result.addColumn(exceptionCountCol);

        SqlDialect dialect = MothershipManager.get().getSchema().getSqlDialect();

        SQLFragment daysActive = new SQLFragment("(SELECT ");
        daysActive.append(dialect.getDateDiff(Calendar.DATE, "MAX(LastKnownTime)", "MIN(EarliestKnownTime)"));
        daysActive.append(" FROM ");
        daysActive.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        daysActive.append(" WHERE ss.ServerInstallationId = ");
        daysActive.append(ExprColumn.STR_TABLE_ALIAS);
        daysActive.append(".ServerInstallationId)");
        ExprColumn daysActiveColumn = new ExprColumn(result, "DaysActive", daysActive, Types.INTEGER);
        daysActiveColumn.setFormatString("#.#");
        result.addColumn(daysActiveColumn);

        SQLFragment versionCount = new SQLFragment("(SELECT COUNT(DISTINCT(softwarereleaseid)) FROM ");
        versionCount.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        versionCount.append(" WHERE ss.ServerInstallationId = ");
        versionCount.append(ExprColumn.STR_TABLE_ALIAS);
        versionCount.append(".ServerInstallationId)");
        result.addColumn(new ExprColumn(result, "VersionCount", versionCount, Types.INTEGER));

        SQLFragment currentVersion = new SQLFragment("(SELECT MAX(ServerSessionID) FROM ");
        currentVersion.append(MothershipManager.get().getTableInfoServerSession(), "ss");
        currentVersion.append(" WHERE ss.ServerInstallationId = ");
        currentVersion.append(ExprColumn.STR_TABLE_ALIAS);
        currentVersion.append(".ServerInstallationId)");
        ExprColumn currentVersionColumn = new ExprColumn(result, "MostRecentSession", currentVersion, Types.INTEGER);
        currentVersionColumn.setFk(new LookupForeignKey("ServerSessionID")
        {
            public TableInfo getLookupTableInfo()
            {
                return createServerSessionTable();
            }
        });
        result.addColumn(currentVersionColumn);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromString("ServerHostName"));
        defaultCols.add(FieldKey.fromString("ServerIP"));
        defaultCols.add(FieldKey.fromString("Note"));
        defaultCols.add(FieldKey.fromString("DaysActive"));
        defaultCols.add(FieldKey.fromString("LastPing"));
        defaultCols.add(FieldKey.fromString("ExceptionCount"));
        defaultCols.add(FieldKey.fromString("VersionCount"));
        defaultCols.add(FieldKey.fromString("MostRecentSession/SVNRevision"));
        result.setDefaultVisibleColumns(defaultCols);

        return result;
    }

    public FilteredTable createExceptionStackTraceTable()
    {
        FilteredTable result = new FilteredTable(MothershipManager.get().getTableInfoExceptionStackTrace());
        result.wrapAllColumns(true);
        result.getColumn("StackTrace").setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new StackTraceDisplayColumn(colInfo);
            }
        });
        ExprColumn lastReportColumn = new ExprColumn(result, "LastReport", new SQLFragment("(SELECT MAX(Created) FROM " +
                MothershipManager.get().getTableInfoExceptionReport() + " er WHERE er.ExceptionStackTraceId = " +
                ExprColumn.STR_TABLE_ALIAS + ".ExceptionStackTraceId)"), Types.TIMESTAMP);
        result.addColumn(lastReportColumn);

        ExprColumn firstReportColumn = new ExprColumn(result, "FirstReport", new SQLFragment("(SELECT MIN(Created) FROM " +
                MothershipManager.get().getTableInfoExceptionReport() + " er WHERE er.ExceptionStackTraceId = " +
                ExprColumn.STR_TABLE_ALIAS + ".ExceptionStackTraceId)"), Types.TIMESTAMP);
        result.addColumn(firstReportColumn);

        ExprColumn countColumn = new ExprColumn(result, "Instances", new SQLFragment("(SELECT COUNT(*) FROM " +
                MothershipManager.get().getTableInfoExceptionReport() + " er WHERE er.ExceptionStackTraceId = " +
                ExprColumn.STR_TABLE_ALIAS + ".ExceptionStackTraceId)"), Types.INTEGER);
        result.addColumn(countColumn);

        ExprColumn maxRevisionColumn = new ExprColumn(result, "MaxSVNRevision", new SQLFragment("(SELECT MAX(SVNRevision) FROM " +
                MothershipManager.get().getTableInfoExceptionReport() + " er, " +
                MothershipManager.get().getTableInfoSoftwareRelease() + " sr, " +
                MothershipManager.get().getTableInfoServerSession() + " ss " +
                " WHERE er.ExceptionStackTraceId = " + ExprColumn.STR_TABLE_ALIAS + ".ExceptionStackTraceId" +
                " AND ss.ServerSessionId = er.ServerSessionId AND ss.SoftwareReleaseId = sr.SoftwareReleaseId)"), Types.INTEGER);
        result.addColumn(maxRevisionColumn);

        ExprColumn minRevisionColumn = new ExprColumn(result, "MinSVNRevision", new SQLFragment("(SELECT MIN(SVNRevision) FROM " +
                MothershipManager.get().getTableInfoExceptionReport() + " er, " +
                MothershipManager.get().getTableInfoSoftwareRelease() + " sr, " +
                MothershipManager.get().getTableInfoServerSession() + " ss " +
                " WHERE er.ExceptionStackTraceId = " + ExprColumn.STR_TABLE_ALIAS + ".ExceptionStackTraceId" +
                " AND ss.ServerSessionId = er.ServerSessionId AND ss.SoftwareReleaseId = sr.SoftwareReleaseId)"), Types.INTEGER);
        result.addColumn(minRevisionColumn);

        ActionURL issueURL = new ActionURL("Issues", "details.view", MothershipManager.get().getIssuesContainer(getContainer()));
        result.getColumn("BugNumber").setURL(issueURL.toString() + "issueId=${BugNumber}");

        result.getColumn("ExceptionStackTraceId").setURL(new ActionURL(MothershipController.ShowStackTraceDetailAction.class, getContainer()) + "exceptionStackTraceId=${ExceptionStackTraceId}");
        result.getColumn("ExceptionStackTraceId").setCaption("Exception");
        result.getColumn("ExceptionStackTraceId").setFormatString("'#'0");


        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts("ExceptionStackTraceId"));
        defaultCols.add(FieldKey.fromParts("Instances"));
        defaultCols.add(FieldKey.fromParts("MinSVNRevision"));
        defaultCols.add(FieldKey.fromParts("MaxSVNRevision"));
        defaultCols.add(FieldKey.fromParts("FirstReport"));
        defaultCols.add(FieldKey.fromParts("LastReport"));
        defaultCols.add(FieldKey.fromParts("BugNumber"));
        defaultCols.add(FieldKey.fromParts("AssignedTo"));
        defaultCols.add(FieldKey.fromParts("StackTrace"));
        result.setDefaultVisibleColumns(defaultCols);

        return result;
    }

    public FilteredTable createExceptionReportTable()
    {
        FilteredTable result = new FilteredTable(MothershipManager.get().getTableInfoExceptionReport());
        result.wrapAllColumns(true);
        result.getColumn("URL").setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                DataColumn result = new DataColumn(colInfo);
                result.setURLExpression(StringExpressionFactory.create("${URL}", false));
                return result;
            }
        });
        result.getColumn("ReferrerURL").setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                DataColumn result = new DataColumn(colInfo);
                result.setURLExpression(StringExpressionFactory.create("${ReferrerURL}", false));
                return result;
            }
        });

        result.getColumn("ExceptionStackTraceId").setCaption("Exception");
        result.getColumn("ExceptionStackTraceId").setFk(new LookupForeignKey("ExceptionStackTraceId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createExceptionStackTraceTable();
            }
        });

        result.getColumn("PageflowName").setCaption("Controller");
        result.getColumn("PageflowAction").setCaption("Action");

        result.getColumn("ServerSessionId").setURL("showServerSessionDetail.view?serverSessionId=${ServerSessionId}");
        result.getColumn("ServerSessionId").setCaption("Session");
        result.getColumn("ServerSessionId").setFormatString("'#'0");
        LookupForeignKey fk = new LookupForeignKey("ServerSessionId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createServerSessionTable();
            }
        };
        fk.setPrefixColumnCaption(false);
        result.getColumn("ServerSessionId").setFk(fk);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts("ServerSessionId"));
        defaultCols.add(FieldKey.fromParts("Created"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "SVNRevision"));
        defaultCols.add(FieldKey.fromParts("PageflowName"));
        defaultCols.add(FieldKey.fromParts("PageflowAction"));
        defaultCols.add(FieldKey.fromParts("Username"));
        defaultCols.add(FieldKey.fromParts("URL"));
        defaultCols.add(FieldKey.fromParts("ReferrerURL"));
        defaultCols.add(FieldKey.fromParts("SQLState"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "DatabaseProductName"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "DatabaseProductVersion"));
        defaultCols.add(FieldKey.fromParts("Browser"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "RuntimeOS"));
        defaultCols.add(FieldKey.fromParts("ServerSessionId", "JavaVersion"));
        result.setDefaultVisibleColumns(defaultCols);

        return result;
    }
}
