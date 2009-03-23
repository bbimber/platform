/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.query.sql;

import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.data.*;

import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;


public class QMethodCall extends QExpr
{
    public QMethodCall()
    {

    }

    public void appendSql(SqlBuilder builder)
    {
        MethodInfo method = getMethod();
        if (method == null)
        {
            builder.appendStringLiteral("Unrecognized method " + getField().getFieldKey());
            return;
        }
        List<SQLFragment> arguments = new ArrayList<SQLFragment>();
        for (QNode n : getLastChild().children())
        {
			QExpr expr = (QExpr)n;
            arguments.add(expr.getSqlFragment(builder.getDbSchema()));
        }
        QNode first = getFirstChild();
        if (first instanceof QField && null != ((QField)first).getTable())
            builder.append(method.getSQL(((QField)first).getRelationColumn().getTable().getAlias(), builder.getDbSchema(), arguments.toArray(new SQLFragment[arguments.size()])));
        else
            builder.append(method.getSQL(builder.getDbSchema(), arguments.toArray(new SQLFragment[arguments.size()])));
    }

    public ColumnInfo createColumnInfo(SQLTableInfo table, String alias)
    {
        MethodInfo method = getMethod();
        if (method == null)
        {
            return super.createColumnInfo(table, alias);
        }
        List<ColumnInfo> arguments = new ArrayList<ColumnInfo>();

        for (ListIterator<QNode> it = getLastChild().childList().listIterator(); it.hasNext();)
        {
            QExpr expr = (QExpr)it.next();
            arguments.add(expr.createColumnInfo(table, "arg" + it.previousIndex()));
        }
        return method.createColumnInfo(table, arguments.toArray(new ColumnInfo[0]), alias);
    }

    public MethodInfo getMethod()
    {
        return getField().getMethod();
    }

    public void appendSource(SourceBuilder builder)
    {
        getFirstChild().appendSource(builder);
        builder.append("(");
        builder.pushPrefix("");

        for (QNode n : getLastChild().children())
        {
			QExpr child = (QExpr)n;
            child.appendSource(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        builder.append(")");
    }

    public QField getField()
    {
        if (!(getFirstChild() instanceof QField))
        {
            throw new QueryException("Fields should have been resolved");
        }
        return (QField) getFirstChild();
    }

    public QueryParseException fieldCheck(QNode parent)
    {
        if (getMethod() == null)
        {
            return new QueryParseException("Unknown method " + getField().getName(), null, getLine(), getColumn());
        }
        return null;
    }
}
