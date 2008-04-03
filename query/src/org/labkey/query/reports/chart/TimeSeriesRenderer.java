package org.labkey.query.reports.chart;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.urls.StandardXYURLGenerator;
import org.jfree.chart.urls.XYURLGenerator;
import org.jfree.data.time.*;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYDataItem;
import org.labkey.api.reports.chart.ChartRenderer;
import org.labkey.api.reports.chart.ChartRenderInfo;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.QueryView;

import java.sql.ResultSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 2, 2006
 */
public class TimeSeriesRenderer extends AbstractChartRenderer implements ChartRenderer
{
    private static final TimeSeriesRenderer _instance = new TimeSeriesRenderer();

    public static TimeSeriesRenderer getInstance()
    {
        return _instance;
    }

    private TimeSeriesRenderer(){}

    public String getType()
    {
        return "2";
    }

    public String getName()
    {
        return "Time Series Plot";
    }

    public Plot createPlot(ChartReportDescriptor descriptor, ReportQueryView view) throws Exception
    {
        ResultSet rs = generateResultSet(view);
        if (rs != null)
        {
            try {
                Map<String, String> labels = getLabelMap(view);
                Map<String, TimePeriodValues> datasets = new HashMap<String, TimePeriodValues>();
                for (String columnName : descriptor.getColumnYName())
                {
                    if (!StringUtils.isEmpty(columnName))
                        datasets.put(columnName, new TimePeriodValues(getLabel(labels, columnName)));//, Day.class));
                }
                Map<String, Object> rowMap = null;
                String columnX = descriptor.getProperty(ChartReportDescriptor.Prop.columnXName);

                // create a jfreechart dataset
                while (rs.next())
                {
                    rowMap = ResultSetUtil.mapRow(rs, rowMap);

                    for (Map.Entry<String, TimePeriodValues> series : datasets.entrySet())
                    {
                        addDataItem(series.getValue(), rowMap, columnX, series.getKey());
                    }
                }

                boolean isMultiYAxis = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.showMultipleYAxis));
                boolean showLines = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.showLines));
                boolean showMultiPlot = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.showMultipleCharts));

                XYPlot plot = new XYPlot();
                TimePeriodValuesCollection collection = new TimePeriodValuesCollection();

                for (TimePeriodValues series : datasets.values())
                    collection.addSeries(series);

                if (showMultiPlot && datasets.size() > 1)
                {
                    boolean isVertical = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.isVerticalOrientation));
                    plot = isVertical ?
                            new CombinedDomainXYPlot((ValueAxis)createAxis(X_AXIS, descriptor, null)) :
                            new CombinedRangeXYPlot((ValueAxis)createAxis(Y_AXIS, descriptor, null));

                    for (TimePeriodValues series : datasets.values())
                    {
                        XYPlot subPlot = new XYPlot();

                        subPlot.setDataset(new TimePeriodValuesCollection(series));
                        subPlot.setRangeAxis((ValueAxis)createAxis(Y_AXIS, descriptor, series.getKey().toString()));
                        subPlot.setDomainAxis((ValueAxis)createAxis(X_AXIS, descriptor, getLabel(labels, columnX)));
                        subPlot.setRenderer(new XYLineAndShapeRenderer(showLines, true));
                        subPlot.getRenderer().setURLGenerator(new UrlGenerator(getRenderInfo()));
                        subPlot.getRenderer().setToolTipGenerator(new XYReportToolTipGenerator());

                        if (isVertical)
                            ((CombinedDomainXYPlot)plot).add(subPlot);
                        else
                            ((CombinedRangeXYPlot)plot).add(subPlot);
                    }
                }
                else if (isMultiYAxis && collection.getSeriesCount() > 0)
                {
                    for (int idx=0; idx < collection.getSeriesCount(); idx++)
                    {
                        TimePeriodValues timeSeries = collection.getSeries(idx);
                        plot.setDataset(idx, new TimePeriodValuesCollection(timeSeries));

                        Axis axis = createAxis(Y_AXIS, descriptor, timeSeries.getKey().toString());
                        plot.setRangeAxis(idx, (ValueAxis)axis);
                        plot.setRenderer(idx, new XYLineAndShapeRenderer(showLines, true));
                        plot.getRenderer().setURLGenerator(new UrlGenerator(getRenderInfo()));
                        plot.getRenderer().setToolTipGenerator(new XYReportToolTipGenerator());

                        plot.mapDatasetToRangeAxis(idx, idx);
                        idx++;
                    }
                }
                else
                {
                    Axis axis = createAxis(Y_AXIS, descriptor, "");
                    if (collection.getSeriesCount() == 1)
                        axis.setLabel(collection.getSeries(0).getKey().toString());
                    plot.setRangeAxis((ValueAxis)axis);
                    plot.setDataset(collection);
                    plot.setRenderer(new XYLineAndShapeRenderer(showLines, true));
                    plot.getRenderer().setURLGenerator(new UrlGenerator(getRenderInfo()));
                    plot.getRenderer().setToolTipGenerator(new XYReportToolTipGenerator());
                }

                plot.setDomainAxis((ValueAxis)createAxis(X_AXIS, descriptor, getLabel(labels, columnX)));
                return plot;
            }
            finally
            {
                rs.close();
            }
        }
        return null;
    }

    public Map<String, String> getDisplayColumns(QueryView view, boolean isXAxis)
    {
        if (isXAxis)
            return getDisplayColumns(view, false, true);
        else
            return getDisplayColumns(view, true, false);
    }

    protected void addDataItem(TimePeriodValues series, Map<String, Object> rowMap, String xcol, String ycol)
    {
        Object oX = rowMap.get(xcol);
        Object oY = rowMap.get(ycol);

        if (oX == null || oY == null) return;

        final Class cY = oY.getClass();
        final Class cX = oX.getClass();

        // right now we only deal with numeric types on the Y axis
        if (cY.isPrimitive() || Number.class.isAssignableFrom(cY))
        {
            if (Date.class.isAssignableFrom(cX))
            {
                ChartRenderInfo info = getRenderInfo();
                Map<String, Object> colMap = null;

                if (info != null && info.getImageMapCallbackColumns().length != 0)
                {
                    colMap = new HashMap<String, Object>();
                    for (String colName : info.getImageMapCallbackColumns())
                    {
                        if (rowMap.containsKey(colName))
                            colMap.put(colName, rowMap.get(colName));
                    }
                }
                if (colMap != null)
                    series.add(new ExtraDataItem(new Day((Date)oX, RegularTimePeriod.DEFAULT_TIME_ZONE), (Number)oY, colMap));
                else
                    series.add(new Day((Date)oX, RegularTimePeriod.DEFAULT_TIME_ZONE), (Number)oY);
            }
        }
    }

    public Axis createAxis(int type, ChartReportDescriptor descriptor, String label)
    {
        if (type == X_AXIS)
            return new DateAxis(label);

        return super.createAxis(type, descriptor, label);
    }

    protected static class ExtraDataItem extends TimePeriodValue implements ImageMapDataItem
    {
        private Map<String, Object> _extraInfo;

        public ExtraDataItem(RegularTimePeriod period, Number y, Map<String, Object> extraInfo)
        {
            super(period, y);
            _extraInfo = extraInfo;
        }

        public Map<String, Object> getExtraInfo()
        {
            return _extraInfo;
        }
    }

    protected static class UrlGenerator extends StandardUrlGenerator
    {
        public UrlGenerator(ChartRenderInfo info)
        {
            super(info);
        }

        protected void renderExtraColumns(XYDataset dataset, int series, int item, StringBuffer sb)
        {
            if (dataset instanceof TimePeriodValuesCollection)
            {
                TimePeriodValue dataItem = ((TimePeriodValuesCollection)dataset).getSeries(series).getDataItem(item);
                if (dataItem instanceof ImageMapDataItem)
                {
                    for (Map.Entry<String, Object> entry : ((ImageMapDataItem)dataItem).getExtraInfo().entrySet())
                    {
                        sb.append(" ,");
                        sb.append(entry.getKey());
                        sb.append(":");
                        sb.append(String.valueOf(entry.getValue()));
                    }
                }
            }
            else
                super.renderExtraColumns(dataset, series, item, sb);
        }
    }
}
