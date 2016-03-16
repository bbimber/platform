package org.labkey.experiment.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.FieldKey;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 3/16/16
 */
public abstract class LineageClause extends CompareType.CompareClause
{
    public LineageClause(@NotNull FieldKey fieldKey, Object value)
    {
        super(fieldKey, CompareType.MEMBER_OF, value);
    }

    protected ExpProtocolOutput getStart()
    {
        Object o = getParamVals().length == 0 ? null : getParamVals()[0];
        if (o == null)
            return null;

        // TODO: support rowId as well
        String lsid = String.valueOf(o);

        ExperimentServiceImpl svc = ExperimentServiceImpl.get();
        ExpProtocolOutput start = svc.getExpMaterial(lsid);
        if (start == null)
            start = svc.getExpData(lsid);

        if (start == null || svc.isUnknownMaterial(start))
            return null;

        return start;
    }

    protected abstract ExpLineageOptions createOptions();

    protected abstract String getLsidColumn();

    @Override
    public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
    {
        ColumnInfo colInfo = columnMap != null ? columnMap.get(getFieldKey()) : null;
        String alias = colInfo != null ? colInfo.getAlias() : getFieldKey().getName();

        ExpProtocolOutput start = getStart();
        if (start == null)
            return new SQLFragment("(1 = 2)");

        ExperimentServiceImpl svc = ExperimentServiceImpl.get();
        ExpLineageOptions options = createOptions();
        List<ExpRun> runsToInvestigate = svc.collectRunsToInvestigate(start, options);
        if (runsToInvestigate.isEmpty())
            return new SQLFragment("(1 = 2)");

        SQLFragment tree = svc.generateExperimentTreeSQL(runsToInvestigate, options);

        SQLFragment sql = new SQLFragment();
        sql.append("(").append(alias).append(") IN (");
        sql.append("SELECT ").append(getLsidColumn()).append(" FROM (");
        sql.append(tree);
        sql.append(") AS X)");

        return sql;
    }

    protected abstract String filterTextType();

    @Override
    protected void appendFilterText(StringBuilder sb, SimpleFilter.ColumnNameFormatter formatter)
    {
        ExpProtocolOutput start = getStart();
        if (start == null)
            sb.append("Invalid '").append(filterTextType()).append("' filter");
        else
            sb.append("Is ").append(filterTextType()).append(" '").append(start.getName()).append("'");
    }

}
