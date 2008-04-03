package org.labkey.audit.query;

import org.labkey.api.audit.query.AuditDisplayColumnFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryPicker;
import org.labkey.api.view.DataView;
import org.labkey.audit.model.LogManager;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
public class AuditQueryViewImpl extends AuditLogQueryView
{
    protected Map<Integer, DisplayColumn> _indexedColumns = new HashMap();

    public AuditQueryViewImpl(UserSchema schema, QuerySettings settings, SimpleFilter filter)
    {
        super(schema, settings, filter);
    }

    public void addDisplayColumn(int index, DisplayColumn dc)
    {
        _indexedColumns.put(index, dc);
    }

    protected DataView createDataView()
    {
        setShowDetailsColumn(false);

        DataView view = super.createDataView();

        view.getDataRegion().setShadeAlternatingRows(true);
        view.getDataRegion().setShowColumnSeparators(true);

        if (!_columns.isEmpty())
        {
            if (getCustomView() == null || !_showCustomizeLink)
            {
                for (DisplayColumn dc : view.getDataRegion().getDisplayColumns())
                {
                    if (!_columns.contains(dc.getName().toLowerCase()))
                        dc.setVisible(false);
                }
            }
        }

        if (_filter != null)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (filter != null)
                filter.addAllClauses(_filter);
            else
                filter = _filter;
            view.getRenderContext().setBaseFilter(filter);
        }
        if (_sort != null && view.getRenderContext().getBaseSort() == null)
            view.getRenderContext().setBaseSort(_sort);

        for (DisplayColumn dc : _displayColumns)
            view.getDataRegion().addColumn(dc);

        for (Map.Entry<Integer, DisplayColumn> entry : _indexedColumns.entrySet())
        {
            view.getDataRegion().addColumn(entry.getKey(), entry.getValue());
        }

        setupView(view);

        return view;
    }

    private void setupView(DataView view)
    {
        AuditLogService.AuditViewFactory factory = AuditLogService.get().getAuditViewFactory(getSettings().getQueryName());
        if (factory != null)
        {
            factory.setupView(view);
        }
    }

    protected void renderDataRegion(PrintWriter out) throws Exception
    {
        if (_title != null)
            out.print("<br/>" + _title + "<br/>");
        super.renderDataRegion(out);
    }

    protected TableInfo createTable()
    {
        AuditLogTable table = new AuditLogTable(getSchema(), LogManager.get().getTinfoAuditLog(), getSettings().getQueryName());
        for (Map.Entry<String, AuditDisplayColumnFactory> entry : _displayColFactory.entrySet())
        {
            table.setDisplayColumnFactory(entry.getKey(), entry.getValue());
        }
        return table;
    }

    protected List<QueryPicker> getQueryPickers()
    {
        return Collections.emptyList();
    }

    public void renderCustomizeLinks(PrintWriter out) throws Exception
    {
        if (_showCustomizeLink || isShowCustomizeViewLinkInButtonBar())
            super.renderCustomizeLinks(out);
    }

    protected void renderChangeViewPickers(PrintWriter out)
    {
        if (_showCustomizeLink || isShowCustomizeViewLinkInButtonBar())
            super.renderChangeViewPickers(out); 
    }
}
