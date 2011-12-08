/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.collections.NullPreventingSet;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RenderContext extends BoundMap // extends ViewContext
{
    private static final Logger _log = Logger.getLogger(RenderContext.class);

    private boolean _useContainerFilter = true;
    private ViewContext _viewContext;
    private Errors _errors;
    private TableViewForm _form;
    private DataRegion _currentRegion;
    private Filter _baseFilter;
    private Map<String, Object> _row;
    private Sort _baseSort;
    private int _mode = DataRegion.MODE_NONE;
    private boolean _cache = true;
    protected Set<String> _ignoredColumnFilters = new LinkedHashSet<String>();
    private Set<String> _selected = null;
    private ShowRows _showRows = ShowRows.PAGINATED;
    private List<String> _recordSelectorValueColumns;
    private CustomView _view;
    private List<Aggregate> _aggregates;

    private Results _rs;
//    private Map<FieldKey, ColumnInfo> _fieldMap;

    public RenderContext(ViewContext context)
    {
        this(context, null);
    }

    public RenderContext(ViewContext context, Errors errors)
    {
        setBean(this);
        _viewContext = context;
        setErrors(errors);
        assert MemTracker.put(this);
    }

    protected RenderContext()
    {
    }

    /* used by DataView */
    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
    }

    public Errors getErrors()
    {
        return _errors;
    }

    public void setErrors(Errors errors)
    {
        _errors = errors;
    }

    public TableViewForm getForm()
    {
        return _form;
    }

    public void setForm(TableViewForm form)
    {
        _form = form;
    }

    public DataRegion getCurrentRegion()
    {
        return _currentRegion;
    }

    public void setCurrentRegion(DataRegion currentRegion)
    {
        _currentRegion = currentRegion;
        if (_currentRegion != null)
        {
            _showRows = _currentRegion.getShowRows();
            _recordSelectorValueColumns = _currentRegion.getRecordSelectorValueColumns();

            if (_currentRegion.getSelectionKey() != null)
            {
                _selected = DataRegionSelection.getSelected(getViewContext(),
                        _currentRegion.getSelectionKey(), true, false);
            }
        }
        else
        {
            _showRows = ShowRows.PAGINATED;
            _recordSelectorValueColumns = null;
            _selected = null;
        }
    }

    public String getSelectionKey()
    {
        return _currentRegion != null ? _currentRegion.getSelectionKey() : null;
    }

    public Filter getBaseFilter()
    {
        return _baseFilter;
    }

    public void setBaseFilter(Filter filter)
    {
        _baseFilter = filter;
    }

    public Sort getBaseSort()
    {
        return _baseSort;
    }

    public void setBaseSort(Sort sort)
    {
        _baseSort = sort;
    }

    public List<Aggregate> getBaseAggregates()
    {
        return _aggregates;
    }

    public void setBaseAggregates(List<Aggregate> aggregates)
    {
        _aggregates = aggregates;
    }
    
    public Results getResults()
    {
        return _rs;
    }

    @Deprecated // use getResults()
    public ResultSet getResultSet()
    {
        return _rs;
    }

    public void setResults(Results rs)
    {
        _rs = rs;
    }

    public static List<ColumnInfo> getSelectColumns(List<DisplayColumn> displayColumns, TableInfo tinfo)
    {
        Table.checkAllColumns(tinfo, tinfo.getColumns(), "RenderContext.getSelectColumns() tinfo.getColumns()");

        Set<ColumnInfo> ret = new NullPreventingSet<ColumnInfo>(new LinkedHashSet<ColumnInfo>());
        LinkedHashSet<FieldKey> keys = new LinkedHashSet<FieldKey>();

        if (null == displayColumns || displayColumns.size() == 0)
        {
            ret.addAll(tinfo.getColumns());
        }
        else
        {
            for (DisplayColumn displayColumn : displayColumns)
            {
                assert null != displayColumn;
                if (null == displayColumn)
                    continue;
                displayColumn.addQueryColumns(ret);

                // TODO: Remove this check -- it's overkill, but should help narrow down a TeamCity failure
                assert Table.checkAllColumns(tinfo, ret, "RenderContext.getSelectColumns(), after adding " + displayColumn.getName());
            }

            // add any additional columns specified by FieldKey
            for (DisplayColumn dc : displayColumns)
                dc.addQueryFieldKeys(keys);
        }

        Table.checkAllColumns(tinfo, ret, "RenderContext.getSelectColumns() ret, after adding display columns");

        Set<FieldKey> detailsUrlKeys = tinfo.getDetailsURLKeys();
        keys.addAll(detailsUrlKeys);
        Collection<ColumnInfo> infoCollection = QueryService.get().getColumns(tinfo, keys, ret).values();
        ret.addAll(infoCollection);

        List<ColumnInfo> pkColumns = tinfo.getPkColumns();

        if (null != pkColumns)
        {
            //always need to select pks
            ret.addAll(pkColumns);
        }

        String versionCol = tinfo.getVersionColumnName();

        if (null != versionCol)
        {
            ColumnInfo col = tinfo.getColumn(versionCol);
            ret.add(col);
        }

        Table.checkAllColumns(tinfo, ret, "RenderContext.getSelectColumns() ret, method end");

        return new ArrayList<ColumnInfo>(ret);
    }


    public ActionURL getSortFilterURLHelper()
    {
        return getSortFilterURLHelper(getViewContext());
    }


    public static ActionURL getSortFilterURLHelper(ViewContext context)
    {
        return context.cloneActionURL();
    }

    /** valid after call to getResultSet() */
    public Map<FieldKey, ColumnInfo> getFieldMap()
    {
        return null==_rs ? null : _rs.getFieldMap();
    }

    public Results getResultSet(Map<FieldKey, ColumnInfo> fieldMap, TableInfo tinfo, Map<String,Object> parameters, int maxRows, long offset, String name, boolean async) throws SQLException, IOException
    {
        ActionURL url = getViewContext().cloneActionURL();

        Sort sort = buildSort(tinfo, url, name);
        SimpleFilter filter = buildFilter(tinfo, url, name, maxRows, offset, sort);

        Collection<ColumnInfo> cols = fieldMap.values();
        if (null != QueryService.get())
            cols = QueryService.get().ensureRequiredColumns(tinfo, cols, filter, sort, _ignoredColumnFilters);

        _rs = selectForDisplay(tinfo, cols, parameters, filter, sort, maxRows, offset, async);
        return _rs;
    }

    public Map<String, Aggregate.Result> getAggregates(List<DisplayColumn> displayColumns, TableInfo tinfo, String dataRegionName, List<Aggregate> aggregatesIn, Map<String,Object> parameters, boolean async) throws SQLException, IOException
    {
        if (aggregatesIn == null || aggregatesIn.isEmpty())
            return Collections.emptyMap();

        Set<String> ignoredAggregateFilters = new HashSet<String>();
        ActionURL url = getViewContext().cloneActionURL();
        Collection<ColumnInfo> cols = getSelectColumns(displayColumns, tinfo);

        Sort sort = buildSort(tinfo, url, dataRegionName);
        SimpleFilter filter = buildFilter(tinfo, url, dataRegionName, Table.ALL_ROWS, 0, sort);

        if (null != QueryService.get())
            cols = QueryService.get().ensureRequiredColumns(tinfo, cols, filter, sort, ignoredAggregateFilters);

        if (!ignoredAggregateFilters.equals(_ignoredColumnFilters))
        {
            // This should never happen, but if it did, the totals wouldn't match, so we won't calculate them.
            _log.error("Aggregate filter columns do not match main.  Aggregate:" + ignoredAggregateFilters + " Main:" + _ignoredColumnFilters);
            return Collections.emptyMap();
        }

        List<Aggregate> aggregates = new ArrayList<Aggregate>();
        Map<String, ColumnInfo> availableColNames = Table.createColumnMap(tinfo, cols);

        for (Aggregate aggregate : aggregatesIn)
        {
            if (aggregate.isCountStar() || availableColNames.containsKey(aggregate.getColumnName()))
                aggregates.add(aggregate);
        }

        if (!aggregates.isEmpty())
        {
            if (async)
            {
                return Table.selectAggregatesForDisplayAsync(tinfo, aggregates, cols, parameters, filter, getCache(), getViewContext().getResponse());
            }

            return Table.selectAggregatesForDisplay(tinfo, aggregates, cols, parameters, filter, getCache());
        }

        return Collections.emptyMap();
    }


    public Sort buildSort(TableInfo tinfo, ActionURL url, String name)
    {
        Sort baseSort = getBaseSort();
        Sort sort = null != baseSort ? baseSort : new Sort();
        sort.addURLSort(url, name);
        return sort;
    }


    public SimpleFilter buildFilter(TableInfo tinfo, ActionURL url, String name, int maxRows, long offset, Sort sort)
    {
        SimpleFilter filter = new SimpleFilter(getBaseFilter());
        //HACK.. Need to fix up the casing of columns throughout so don't have to do
        //this...
        ColumnInfo containerCol = tinfo.getColumn("container");
        Container c = getContainer();

        if (null != c && null != containerCol && isUseContainerFilter() && tinfo.needsContainerClauseAdded())
        {
            // This CAST improves performance on Postgres for some queries by choosing a more efficient query plan
            filter.addClause(new SimpleFilter.SQLClause(containerCol.getName() + " = CAST('" + c.getId() + "' AS UniqueIdentifier)", new Object[0], containerCol.getName()));
        }

        if (_currentRegion != null && _showRows == ShowRows.SELECTED || _showRows == ShowRows.UNSELECTED)
            buildSelectedFilter(filter, tinfo, _showRows == ShowRows.UNSELECTED);
        else
            filter.addUrlFilters(url, name);

        return filter;
    }

    protected void buildSelectedFilter(SimpleFilter filter, TableInfo tinfo, boolean inverted)
    {
        List<String> selectorColumns = getRecordSelectorValueColumns();

        if (selectorColumns == null)
        {
            selectorColumns = tinfo.getPkColumnNames();
        }

        Set<String> selected = getAllSelected();
        SimpleFilter.FilterClause clause;

        if (selected.isEmpty())
        {
            clause = new SimpleFilter.SQLClause("1 = 0", null);
        }
        else if (selectorColumns.size() == 1)
        {
            clause = new SimpleFilter.InClause(selectorColumns.get(0), selected, true);
        }
        else
        {
            SimpleFilter.OrClause or = new SimpleFilter.OrClause();

            for (String row : selected)
            {
                SimpleFilter.AndClause and = new SimpleFilter.AndClause();
                String[] parts = row.split(",");
                assert parts.length == selectorColumns.size() : "Selected item and columns don't match in length: " + row;
                for (int i = 0; i < parts.length; i++)
                {
                    and.addClause(CompareType.EQUAL.createFilterClause(selectorColumns.get(i), parts[i]));
                }
                or.addClause(and);
            }

            clause = or;
        }

        if (inverted)
        {
            clause = new SimpleFilter.NotClause(clause);
        }

        filter.addClause(clause);
    }

    protected Results selectForDisplay(TableInfo table, Collection<ColumnInfo> columns, Map<String,Object> parameters, SimpleFilter filter, Sort sort, int maxRows, long offset, boolean async) throws SQLException, IOException
    {
        if (async)
        {
            return Table.selectForDisplayAsync(table, columns, parameters, filter, sort, maxRows, offset, getCache(), false, getViewContext().getResponse());
        }
        else
        {
            return Table.selectForDisplay(table, columns, parameters, filter, sort, maxRows, offset, getCache(), false);
        }
    }


    public boolean getCache()
    {
        return _cache;
    }

    public void setCache(boolean cache)
    {
        _cache = cache;
    }

    public Map getRow()
    {
        return _row;
    }

    public void setRow(Map row)
    {
        _row = row;
    }

    public int getMode()
    {
        return _mode;
    }

    public void setMode(int mode)
    {
        _mode = mode;
    }

    /**
     * Overrides isEmpty() to include current row
     */
    @Override
    public boolean isEmpty()
    {
        return super.isEmpty() && (_row == null || _row.isEmpty());
    }

    /**
     * Overrides containsKey() to include current row
     */
    @Override
    public boolean containsKey(Object key)
    {
        if (key instanceof FieldKey)
        {
            if (null != _rs)
            {
                if (_rs.hasColumn((FieldKey)key))
                    return true;
            }
            // <UNDONE>
            FieldKey f = (FieldKey)key;
            key = f.getParent() == null ? f.getName() : f.encode();
            // </UNDONE>
        }

        return super.containsKey(key) || (_row != null && _row.containsKey(key));
    }

    /**
     * Overrides containsValue() to include current row
     */
    @Override
    public boolean containsValue(Object value)
    {
        return super.containsValue(value) || (_row != null && _row.containsValue(value));
    }

    /**
     * Overrides values() to combine keys from map and current row
     */
    @Override
    public Collection values()
    {
        Collection values = super.values();
        if (null != _row)
            values.addAll(_row.values());

        return values;
    }

    /**
     * Overrides entrySet to combine entries from map and current row
     */
    @Override
    public Set entrySet()
    {
        Set entrySet = super.entrySet();

        if (null != _row)
        {
            entrySet = new HashSet(entrySet);
            entrySet.addAll(_row.entrySet());
        }

        return entrySet;
    }

    /**
     * Overrides keySet to combine keys from map and current row
     */
    @Override
    public Set keySet()
    {
        Set<String> keySet = super.keySet();

        if (null != _row)
        {
            keySet = new HashSet<String>(keySet);
            keySet.addAll(_row.keySet());
        }

        return keySet;
    }

    /**
     * Overrides get to look first in the map & if not found there look in the current row
     *
     * @param key
     * @return
     */
    @Override
    public Object get(Object key)
    {
        Object val = null;

        if (key instanceof FieldKey)
        {
            if (null != _rs && _rs.hasColumn((FieldKey)key))
            {
                try
                {
                    ColumnInfo col = _rs.findColumnInfo((FieldKey)key);
                    return col == null ? null : col.getValue(_row);
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
            }

            // NOTE: Ideally we should not need to convert FieldKey to String at all
            // but _row is currently a <String,Object> map (not <FieldKey,Object>)
            FieldKey f = (FieldKey)key;
            key = f.getParent() == null ? f.getName() : f.encode();

            // 13607 : Nonconforming field names in datasets cause data loss on edit
            // use FieldKey->ColumnInfo.getAlias() mapping if available
            if (null != getFieldMap())
            {
                ColumnInfo col = getFieldMap().get(key);
                if (null != col)
                    key = getFieldMap().get(key).getAlias();
            }
        }

        if (null != _row)
            val = _row.get(key);

        if (null == val)
            val = super.get(key);

        return val;
    }


    // for backward compatibility in URL substitution
    public String getContainerPath()
    {
//        assert false : "don't use ${containerPath}";
        return getViewContext().getContainer().getPath();
    }

    // for backward compatibility in URL substitution
    public String getContextPath()
    {
//        assert false : "don't use ${contextPath}";
        return getViewContext().getContextPath();
    }


    /** ViewContext wrappers */
    public Container getContainer()
    {
        return _viewContext.getContainer();
    }


    public void setContainer(Container c)
    {
        _viewContext.setContainer(c);
    }

    public HttpServletRequest getRequest()
    {
        return _viewContext.getRequest();
    }


    public void setRequest(HttpServletRequest request)
    {
        _viewContext.setRequest(request);
    }

    
    public ViewContext getViewContext()
    {
        return _viewContext;
    }

    public boolean isUseContainerFilter()
    {
        return _useContainerFilter;
    }

    public void setUseContainerFilter(boolean useContainerFilter)
    {
        _useContainerFilter = useContainerFilter;
    }

    public Set<FieldKey> getIgnoredFilterColumns()
    {
        if (_ignoredColumnFilters.isEmpty())
            return Collections.emptySet();

        Set<FieldKey> ret = new LinkedHashSet<FieldKey>();

        for (String column : _ignoredColumnFilters)
        {
            ret.add(FieldKey.fromString(column));
        }

        return ret;
    }


    /*
     * Moved getErrors() from TableViewForm
     * 4927 : DataRegion needs to support Spring errors collection
     */
    public String getErrors(String paramName)
    {
        Errors errors = getErrors();
        if (null == errors)
            return "";
        List list;
        if ("main".equals(paramName))
            list = errors.getGlobalErrors();
        else
            list = errors.getFieldErrors(paramName);
        if (list == null || list.size() == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        String br = "<font class=\"labkey-error\">";
        for (Object m : list)
        {
            sb.append(br);
            sb.append(PageFlowUtil.filter(getViewContext().getMessage((MessageSourceResolvable)m)));
            br = "<br>";
        }
        if (sb.length() > 0)
            sb.append("</font>");
        return sb.toString();
    }

    public String getErrors(ColumnInfo column)
    {
        String errors = getErrors(getForm().getFormFieldName(column));
        if ("".equals(errors))
            errors = getErrors(column.getName());
        return errors;
    }

    public List<String> getRecordSelectorValueColumns()
    {
        return _recordSelectorValueColumns;
    }

    public Set<String> getAllSelected()
    {
        if (_selected == null)
        {
            return Collections.emptySet();
        }

        return _selected;
    }

    public void setView(CustomView view)
    {
        _view = view;
    }

    public CustomView getView()
    {
        return _view;
    }

    /** Gets the value and does type conversion */
    public <Type> Type get(FieldKey fieldKey, Class<? extends Type> clazz)
    {
        Object value = get(fieldKey);
        if (value != null && !clazz.isInstance(value))
        {
            return (Type)ConvertUtils.convert(value.toString(), clazz);
        }
        return (Type)value;
    }
}
