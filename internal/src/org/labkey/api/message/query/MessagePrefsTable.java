/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.message.query;

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.message.MessageSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 17, 2011
 * Time: 12:59:59 PM
 */
public class MessagePrefsTable extends FilteredTable
{
    public MessagePrefsTable(TableInfo tInfo, Container container)
    {
        super(tInfo, container);

        ColumnInfo userIdCol = addWrapColumn(_rootTable.getColumn("UserId"));
        userIdCol.setKeyField(true);
        userIdCol.setReadOnly(true);

        ColumnInfo entityIdCol = addWrapColumn(_rootTable.getColumn("EntityId"));
        entityIdCol.setHidden(true);

        ColumnInfo displayNameCol = addWrapColumn(_rootTable.getColumn("DisplayName"));
        displayNameCol.setReadOnly(true);
        addWrapColumn(_rootTable.getColumn("FirstName"));
        addWrapColumn(_rootTable.getColumn("LastName"));
        addWrapColumn(_rootTable.getColumn("Description"));
        addWrapColumn(_rootTable.getColumn("Created"));
        addWrapColumn(_rootTable.getColumn("Modified"));
        addWrapColumn(_rootTable.getColumn("Email"));

        addWrapColumn(_rootTable.getColumn("Phone"));
        addWrapColumn(_rootTable.getColumn("Mobile"));
        addWrapColumn(_rootTable.getColumn("Pager"));
        addWrapColumn(_rootTable.getColumn("IM"));
        addWrapColumn(_rootTable.getColumn("Active"));
        addWrapColumn(_rootTable.getColumn("LastLogin"));

//        addGroupsColumn();
        
        ColumnInfo msgCol = addColumn(new EmailSettingsColumn("MessageSettings", "messages", this));
        msgCol.setDisplayColumnFactory(new DisplayColumnFactory(){
            public DisplayColumn createRenderer(ColumnInfo col)
            {
                return new NotificationSettingColumn(col);
            }
        });

        ColumnInfo fileCol = addColumn(new EmailSettingsColumn("FileSettings", "files", this));
        fileCol.setDisplayColumnFactory(new DisplayColumnFactory(){
            public DisplayColumn createRenderer(ColumnInfo col)
            {
                return new NotificationSettingColumn(col);
            }
        });

        setDefaultVisibleColumns(getDefaultColumns());
    }

    private List<FieldKey> getDefaultColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("UserId"));
        columns.add(FieldKey.fromParts("DisplayName"));
        columns.add(FieldKey.fromParts("FirstName"));
        columns.add(FieldKey.fromParts("LastName"));

        columns.add(FieldKey.fromParts("Email"));
        columns.add(FieldKey.fromParts("LastLogin"));
        columns.add(FieldKey.fromParts("FileSettings"));
        columns.add(FieldKey.fromParts("MessageSettings"));

        return columns;
    }

    public static class EmailSettingsColumn extends ExprColumn
    {
        private String EMAIL_PREFS_JOIN = "EmailPrefsJoin$";
        private String EMAIL_OPTIONS_JOIN = "EmailOptionsJoin$";

        private TableInfo _emailPrefsTable;
        private TableInfo _emailOptionsTable;
        private Container _container;
        private String _type;

        public EmailSettingsColumn(String name, String type, FilteredTable parent)
        {
            super(parent, name, new SQLFragment(), Types.VARCHAR);

            _type = type;
            _container = parent.getContainer();
            _emailPrefsTable = CommSchema.getInstance().getTableInfoEmailPrefs();
            _emailOptionsTable = CommSchema.getInstance().getTableInfoEmailOptions();

            // set up the join aliases
            EMAIL_PREFS_JOIN = name + "$" + "EmailPrefsJoin$";
            EMAIL_OPTIONS_JOIN = name + "$" + "EmailOptionsJoin$";

            SQLFragment sql = new SQLFragment();
            sql.append(ExprColumn.STR_TABLE_ALIAS).append("$").append(EMAIL_OPTIONS_JOIN).append(".EmailOption\n");
            setValueSQL(sql);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + EMAIL_PREFS_JOIN;
            String tableOptionsAlias = parentAlias + "$" + EMAIL_OPTIONS_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();

            joinSql.append(" LEFT JOIN ").append(_emailPrefsTable).append(" AS ").append(tableAlias);
            joinSql.append(" ON ").append(tableAlias).append(".UserId = ").append(parentAlias).append(".UserId");
            joinSql.append(" AND ").append(tableAlias).append(".Container = ").append("'").append(_container.getId()).append("'");
            joinSql.append(" AND ").append(tableAlias).append(".Type = ").append("'").append(_type).append("'");
            joinSql.append(" LEFT JOIN ").append(_emailOptionsTable).append(" AS ").append(tableOptionsAlias);
            joinSql.append(" ON ").append(tableAlias).append(".EmailOptionId = ").append(tableOptionsAlias).append(".EmailOptionId");

            map.put(tableAlias, joinSql);
        }
    }

    static public class NotificationSettingColumn extends DataColumn
    {
        public NotificationSettingColumn(ColumnInfo column)
        {
            super(column);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getValue(ctx);

            if (value == null)
                out.write("&lt;folder&nbsp;default&gt;");
            else
                super.renderGridCellContents(ctx, out);
        }
    }
}

