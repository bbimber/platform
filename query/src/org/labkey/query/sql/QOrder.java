package org.labkey.query.sql;

import org.labkey.query.sql.SqlTokenTypes;
import org.labkey.api.query.FieldKey;
import org.labkey.common.util.Pair;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class QOrder extends QNode<QNode>
{
    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("\nORDER BY ");
        boolean fComma = false;
        for (QNode node : children())
        {
            switch (node.getTokenType())
            {
                default:
                    if (fComma)
                    {
                        builder.append(",");
                    }
                    fComma = true;
                    node.appendSource(builder);
                    break;
                case SqlTokenTypes.ASCENDING:
                    break;
                case SqlTokenTypes.DESCENDING:
                    builder.append(" DESC");
                    break;
            }
        }
        builder.popPrefix();
    }

    public List<Map.Entry<QExpr, Boolean>> getSort()
    {
        List<Map.Entry<QExpr, Boolean>> ret = new ArrayList();
        Pair<QExpr,Boolean> entry = null;
        for (QNode child : children())
        {
            switch (child.getTokenType())
            {
                default:
                    if (entry != null)
                    {
                        ret.add(entry);
                    }
                    entry = new Pair((QExpr) child, Boolean.TRUE);
                    break;
                case SqlTokenTypes.DESCENDING:
                    assert entry != null;
                    entry.setValue(Boolean.FALSE);
                    break;
                case SqlTokenTypes.ASCENDING:
                    assert entry != null;
                    entry.setValue(Boolean.TRUE);
                    break;
            }
        }
        if (entry != null)
        {
            ret.add(entry);
        }
        return ret;
    }

    public Map<FieldKey, Boolean> getOrderByMap()
    {
        LinkedHashMap<FieldKey, Boolean> ret = new LinkedHashMap();
        List<Map.Entry<QExpr, Boolean>> list = getSort();
        for (Map.Entry<QExpr, Boolean> entry : list)
        {
            FieldKey fieldKey = entry.getKey().getFieldKey();
            if (fieldKey == null)
            {
                continue;
            }
            ret.put(fieldKey, entry.getValue());
        }
        return ret;
    }

    public void addOrderByClause(QExpr expr, boolean ascending)
    {
        appendChild(expr);
        if (!ascending)
        {
            QNode desc = new QUnknownNode();
            desc.setTokenType(SqlTokenTypes.DESCENDING);
            appendChild(desc);
        }
    }
}
