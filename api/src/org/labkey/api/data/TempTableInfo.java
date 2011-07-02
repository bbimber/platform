/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.labkey.api.util.GUID;

import java.sql.SQLException;
import java.util.List;

/**
* User: matt
* Date: Oct 23, 2010
* Time: 3:08:13 PM
*/
public class TempTableInfo extends SchemaTableInfo
{
    private final String _tempTableName;

    private TempTableTracker _ttt;

    static private String shortGuid()
    {
        String guid = GUID.makeGUID();
        StringBuilder sb = new StringBuilder(guid.length());

        for (int i = 0; i < guid.length(); i++)
        {
            char ch = guid.charAt(i);
            if (ch != '-')
                sb.append(ch);
        }

        return sb.toString();
    }

    public TempTableInfo(DbSchema parentSchema, String name, List<ColumnInfo> cols, List<String> pk)
    {
        super(name, parentSchema.getSqlDialect().getGlobalTempTablePrefix() + name + "$" + shortGuid(), parentSchema);

        // TODO: Do away with _tempTableName?  getSelectName() is synonymous.
        _tempTableName = getSelectName();

        for (ColumnInfo col : cols)
            col.setParentTable(this);

        _columns.addAll(cols);

        if (pk != null)
            setPkColumnNames(pk);

        setTableType(TABLE_TYPE_TABLE);
    }

    public void setButtonBarConfig(ButtonBarConfig bbarConfig)
    {
        _buttonBarConfig = bbarConfig;
    }

    public String getTempTableName()
    {
        return _tempTableName;
    }


    /** Call this method when table is physically created */
    public void track()
    {
        _ttt = TempTableTracker.track(getSchema(), getTempTableName(), this);
    }


    public void delete()
    {
        _ttt.delete();
    }

    public boolean verify()
    {
        try
        {
            Table.isEmpty(this);
            return true;
        }
        catch (SQLException e)
        {
            return false;
        }
    }
}
