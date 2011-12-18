package org.labkey.visualization.sql;

import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;

import java.util.*;

/**
* Copyright (c) 2011 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
* <p/>
* User: brittp
* Date: Jan 27, 2011 11:14:20 AM
*/
public class VisualizationSourceQuery implements IVisualizationSourceQuery
{
    private Container _container;
    private UserSchema _schema;
    private String _queryName;
    private TableInfo _tinfo;
    private VisualizationSourceColumn _pivot;
    private Set<VisualizationSourceColumn> _measures = new LinkedHashSet<VisualizationSourceColumn>();
    private Set<VisualizationSourceColumn> _selects = new LinkedHashSet<VisualizationSourceColumn>();
    private Set<VisualizationSourceColumn> _allSelects = null;
    private Set<VisualizationAggregateColumn> _aggregates = new LinkedHashSet<VisualizationAggregateColumn>();
    private Set<VisualizationSourceColumn> _sorts = new LinkedHashSet<VisualizationSourceColumn>();
    private IVisualizationSourceQuery _joinTarget;  // query this query must join to when building SQL
    private List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> _joinConditions;
    private SimpleFilter _filter;

    VisualizationSourceQuery(Container container, UserSchema schema, String queryName, VisualizationSourceQuery joinTarget)
    {
        _container = container;
        _schema = schema;
        _queryName = queryName;
        _joinTarget = joinTarget;
    }

    private TableInfo getTableInfo()
    {
        if (_tinfo == null)
            _tinfo = _schema.getTable(_queryName);
        if (_tinfo == null)
        {
            throw new NotFoundException("Could not find query '" + _queryName + "' in schema '" + _schema.getName() + "'");
        }
        return _tinfo;
    }

    private void ensureSameQuery(VisualizationSourceColumn measure)
    {
        if (!measure.getSchemaName().equals(getSchemaName()) || !measure.getQueryName().equals(_queryName))
        {
            throw new IllegalArgumentException("Attempt to add measure from " + measure.getSchemaName() + "." +
                    measure.getQueryName() + " to source query " + getSchemaName() + "." + _queryName);
        }
    }

    public void setJoinConditions(List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinConditions)
    {
        _joinConditions = joinConditions;
    }

    public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinConditions()
    {
        return _joinConditions;
    }

    public Container getContainer()
    {
        return _container;
    }

    public boolean requireInnerJoin()
    {
        for (VisualizationSourceColumn col : _selects)
        {
            if (!col.isAllowNullResults())
                return true;
        }
        for (VisualizationAggregateColumn aggregate : _aggregates)
        {
            if (!aggregate.isAllowNullResults())
                return true;
        }
        return false;
    }

    public void addSelect(VisualizationSourceColumn select, boolean measure)
    {
        ensureSameQuery(select);
        _selects.add(select);
        if (measure)
        {
            _measures.add(select);
        }
    }

    @Override
    public Set<VisualizationSourceColumn> getSelects(VisualizationSourceColumn.Factory factory, boolean includeRequiredExtraCols)
    {
        if (includeRequiredExtraCols)
        {
            if (_allSelects == null)
            {
                _allSelects = new LinkedHashSet<VisualizationSourceColumn>(_selects);
                _allSelects.addAll(getOORColumns(factory));
            }
            return _allSelects;
        }
        else
            return _selects;
    }

    public void addAggregate(VisualizationAggregateColumn aggregate)
    {
        ensureSameQuery(aggregate);
        _aggregates.add(aggregate);
    }

    public Set<VisualizationAggregateColumn> getAggregates()
    {
        return _aggregates;
    }

    @Override
    public boolean contains(VisualizationSourceColumn column)
    {
        return column.getSchemaName().equals(this.getSchemaName()) && column.getQueryName().equals(this.getQueryName());
    }

    @Override
    public String getSelectListName(Set<String> selectAliases)
    {
        // If there is more than one available alias for a given value, just choose the first: 
        return selectAliases.iterator().next();
    }

    private static void addToColMap(Map<String, Set<String>> colMap, String name, String alias)
    {
        Set<String> aliases = colMap.get(name);
        if (aliases == null)
        {
            aliases = new LinkedHashSet<String>();
            colMap.put(name, aliases);
        }
        aliases.add(alias);
    }


    @Override
    public Map<String, Set<String>> getColumnNameToValueAliasMap(VisualizationSourceColumn.Factory factory, boolean measuresOnly)
    {
        Map<String, Set<String>> colMap = new LinkedHashMap<String, Set<String>>();
        Set<VisualizationAggregateColumn> aggregates = getAggregates();
        if (!aggregates.isEmpty())
        {
            for (VisualizationAggregateColumn aggregate : aggregates)
            {
                if (getPivot() != null)
                {
                    // Aggregate with pivot:
                    for (Object pivotValue : getPivot().getValues())
                    {
                        addToColMap(colMap, pivotValue.toString(), pivotValue.toString() + "::" + aggregate.getAlias());
                    }
                }
                else
                {
                    // Aggregate without pivot (simple grouping)
                    addToColMap(colMap, aggregate.getOriginalName(), aggregate.getAlias());
                }
            }
        }

        if (measuresOnly)
        {
            for (VisualizationSourceColumn select : _measures)
            {
                addToColMap(colMap, select.getOriginalName(), select.getAlias());
            }
        }
        else
        {
            for (VisualizationSourceColumn select : getSelects(factory, true))
                addToColMap(colMap, select.getOriginalName(), select.getAlias());

            if (getJoinConditions() != null)
            {
                for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> join : getJoinConditions())
                {
                    addToColMap(colMap, join.getKey().getOriginalName(), join.getKey().getAlias());
                    addToColMap(colMap, join.getValue().getOriginalName(), join.getValue().getAlias());
                }
            }
        }
        return colMap;
    }

    @Override
    public VisualizationSourceColumn getPivot()
    {
        return _pivot;
    }

    public void setPivot(VisualizationSourceColumn pivot)
    {
        ensureSameQuery(pivot);
        if (_pivot != null)
        {
            throw new IllegalArgumentException("Can't pivot a single dataset by more than one column.  Attempt to pivot " +
                getSchemaName() + "." + _queryName + " by both " + _pivot.getSelectName() + " and " + pivot.getSelectName());
        }
        _pivot = pivot;
    }

    public void addSort(VisualizationSourceColumn sort)
    {
        ensureSameQuery(sort);
        _sorts.add(sort);
    }

    public Set<VisualizationSourceColumn> getSorts()
    {
        return _sorts;
    }

    public String getDisplayName()
    {
        return getSchemaName() + "." + _queryName;
    }

    @Override
    public String getAlias()
    {
        return ColumnInfo.legalNameFromName(getSchemaName() + "_" + _queryName);
    }

    public void appendColumnNames(StringBuilder sql, Set<? extends VisualizationSourceColumn> columns, boolean aggregate, boolean aliasInsteadOfName, boolean appendAlias)
    {
        if (columns == null || columns.size() == 0)
            return;
        assert !(aliasInsteadOfName && appendAlias) : "Can't both use only alias and append alias";
        String leadingSep = "";

        for (VisualizationSourceColumn column : columns)
        {
            sql.append(leadingSep);
            if (aggregate && column instanceof VisualizationAggregateColumn)
            {
                VisualizationAggregateColumn agg = (VisualizationAggregateColumn) column;
                sql.append(agg.getAggregate().name()).append("(");
            }

            if (aliasInsteadOfName)
                sql.append(column.getAlias());
            else
                sql.append(column.getSelectName());

            if (aggregate && column instanceof VisualizationAggregateColumn)
                sql.append(")");

            if (appendAlias)
                sql.append(" AS ").append(column.getAlias());
            leadingSep = ", ";
        }
    }


    public String getSelectClause(VisualizationSourceColumn.Factory factory)
    {
        StringBuilder selectList = new StringBuilder("SELECT ");
        Set<VisualizationSourceColumn> selects = new LinkedHashSet<VisualizationSourceColumn>();
        if (_pivot != null)
            selects.add(_pivot);
        selects.addAll(getSelects(factory, true));
        selects.addAll(_sorts);
        selects.addAll(_aggregates);
        appendColumnNames(selectList, selects, true, false, true);
        selectList.append("\n");
        return selectList.toString();
    }

    public String getFromClause()
    {
        String schemaName = "\"" + getSchemaName() + "\"";
        String queryName = "\"" + _queryName + "\"";
        return "FROM " + schemaName + "." + queryName + "\n";
    }


    public String getGroupByClause()
    {
        if (_aggregates != null && !_aggregates.isEmpty())
        {
            StringBuilder groupBy = new StringBuilder("GROUP BY ");

            Set<VisualizationSourceColumn> groupBys = new LinkedHashSet<VisualizationSourceColumn>();
            if (_pivot != null)
                    groupBys.add(_pivot);
            groupBys.addAll(_selects);
            groupBys.addAll(_sorts);

            appendColumnNames(groupBy, groupBys, false, false, false);
            groupBy.append("\n");
            return groupBy.toString();
        }
        else
            return "";
    }

    private String appendValueList(StringBuilder sql, VisualizationSourceColumn col) throws VisualizationSQLGenerator.GenerationException
    {
        if (col.getValues() != null && col.getValues().size() > 0)
        {
            sql.append(" IN (");
            String sep = "";
            if (col.getValues().isEmpty())
            {
                sql.append(" NULL");
            }
            else
            {
                for (Object value : col.getValues())
                {
                    sql.append(sep);
                    if (col.getType().isNumeric() || col.getType() == JdbcType.BOOLEAN)
                        sql.append(value);
                    else
                        sql.append("'").append(value).append("'");
                    sep = ", ";
                }
            }
            sql.append(")");
        }
        return sql.toString();
    }

    public String getPivotClause() throws VisualizationSQLGenerator.GenerationException
    {
        if (_pivot != null)
        {
            StringBuilder pivotClause = new StringBuilder("PIVOT ");
            appendColumnNames(pivotClause, _aggregates, false, true, false);
            pivotClause.append(" BY ");
            appendColumnNames(pivotClause, Collections.singleton(_pivot), false, true, false);
            appendValueList(pivotClause, _pivot);
            pivotClause.append("\n");
            return pivotClause.toString();
        }
        else
            return "";
    }

    /**
     * Currently, query does not have a notion of dependent columns- this happens only at the query view level.  As
     * a result, it's possible to select a set of columns that will not correctly render in a data region.  One common
     * example of this occurs with out-of-range indicators, where a failure to select a column's sibling out-of-range
     * indicator column produces an error at render time.  We address this here in a nasty way by checking every selected
     * column to see if an OOR sibling column is present, and adding it to the select if so.
     * @return A set of additional columns that should be selected to ensure that the columns actually requested
     * by the user correctly display their out-of-range indicators.
     */
    private Set<VisualizationSourceColumn> getOORColumns(VisualizationSourceColumn.Factory factory)
    {
        Set<FieldKey> fieldKeys = new HashSet<FieldKey>();
        for (VisualizationSourceColumn selectCol : this.getSelects(factory, false))
        {
            FieldKey oorSelect = FieldKey.fromString(selectCol.getOriginalName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX);
            fieldKeys.add(oorSelect);
        }
        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(getTableInfo(), fieldKeys);
        Set<VisualizationSourceColumn> oorSelects = new HashSet<VisualizationSourceColumn>();
        for (FieldKey key : cols.keySet())
            oorSelects.add(factory.create(getSchema(), getQueryName(), key.toString(), true));
        return oorSelects;
    }

    private Map<FieldKey, ColumnInfo> getFilterColumns(SimpleFilter filter)
    {
        Map<FieldKey, String> fieldKeys = new HashMap<FieldKey, String>();
        for (SimpleFilter.FilterClause clause : filter.getClauses())
        {
            for (String colName : clause.getColumnNames())
                fieldKeys.put(FieldKey.fromString(colName), colName);
        }
        return QueryService.get().getColumns(getTableInfo(), fieldKeys.keySet());
    }

    private String appendSimpleFilter(StringBuilder where, SimpleFilter filter, String separator)
    {
        Map<FieldKey, ColumnInfo> filterColTypes = getFilterColumns(filter);
        List<SimpleFilter.FilterClause> clauses = new ArrayList<SimpleFilter.FilterClause>(filter.getClauses());
        for (SimpleFilter.FilterClause clause : clauses)
        {
            List<String> colNames = clause.getColumnNames();
            boolean allColsFound = true;
            for (String colName : colNames)
            {
                if (!filterColTypes.containsKey(FieldKey.fromString(colName)))
                    allColsFound = false;
            }
            if (allColsFound)
            {
                where.append(separator).append(clause.getLabKeySQLWhereClause(filterColTypes));
                separator = " AND\n";
            }
            else
            {
                // Remove filter clauses for columns that are no longer found on the specified query.
                // Removing them here ensures that we send an accurate description of the current filters to the client.
                for (String colName : colNames)
                    filter.deleteConditions(colName);
            }
        }
        return separator;
    }

    public String getWhereClause() throws VisualizationSQLGenerator.GenerationException
    {
        StringBuilder where = new StringBuilder();
        String sep = "WHERE ";
        if (_filter != null)
            sep = appendSimpleFilter(where, _filter, sep);

        for (VisualizationSourceColumn select : _selects)
        {
            if (select.getValues() != null && !select.getValues().isEmpty())
            {
                where.append(sep);
                appendColumnNames(where, Collections.singleton(select), false, false, false);
                appendValueList(where, select);
                sep = " AND\n";
            }
        }
        for (VisualizationSourceColumn sort : _sorts)
        {
            if (sort.getValues() != null && !sort.getValues().isEmpty() && !_selects.contains(sort))
            {
                where.append(sep);
                appendColumnNames(where, Collections.singleton(sort), false, false, false);
                appendValueList(where, sort);
                sep = " AND\n";
            }
        }
        where.append("\n");
        return where.toString();
    }

    @Override
    public String getSQL(VisualizationSourceColumn.Factory factory) throws VisualizationSQLGenerator.GenerationException
    {
        StringBuilder sql = new StringBuilder();
        sql.append(getSelectClause(factory)).append("\n");
        sql.append(getFromClause()).append("\n");
        sql.append(getWhereClause()).append("\n");
        sql.append(getGroupByClause()).append("\n");
        sql.append(getPivotClause()).append("\n");
        return sql.toString();
    }

    @Override
    public IVisualizationSourceQuery getJoinTarget()
    {
        return _joinTarget;
    }

    public void setJoinTarget(IVisualizationSourceQuery joinTarget)
    {
        _joinTarget = joinTarget;
    }

    public String getSchemaName()
    {
        return _schema.getSchemaName();
    }

    public UserSchema getSchema()
    {
        return _schema;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setFilter(SimpleFilter filter)
    {
        _filter = filter;
    }

    public SimpleFilter getFilter()
    {
        return _filter;
    }
}
