package org.labkey.study.query.studydesign;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.study.query.BaseStudyTable;
import org.labkey.study.query.StudyQuerySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: 7/23/13
 */
public class StudyDesignLookupBaseTable extends BaseStudyTable
{
    public StudyDesignLookupBaseTable(StudyQuerySchema schema, TableInfo tableInfo)
    {
        super(schema, tableInfo);

        wrapAllColumns(true);
        List<FieldKey> defaultColumns = new ArrayList<>(Arrays.asList(
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("Inactive")
        ));
        setDefaultVisibleColumns(defaultColumns);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new AdminQueryUpdateService(this, table);
        return null;
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return canReadOrIsAdminPermission(user, perm);
    }

    private class AdminQueryUpdateService extends DefaultQueryUpdateService
    {
        public AdminQueryUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (!hasPermission(user, AdminPermission.class))
                throw new QueryUpdateServiceException("Only admins are allowed to insert into this table.");

            validateValues(row);
            return super.insertRow(user, container, row);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (!hasPermission(user, AdminPermission.class))
                throw new QueryUpdateServiceException("Only admins are allowed to update records in this table.");

            validateValues(row);
            return super.updateRow(user, container, row, oldRow);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            if (!hasPermission(user, AdminPermission.class))
                throw new QueryUpdateServiceException("Only admins are allowed to delete records from this table.");

            return super.deleteRow(user, container, oldRowMap);
        }

        private void validateValues(Map<String, Object> row) throws ValidationException
        {
            // Issue 18313
            for (ColumnInfo col : getRealTable().getColumns())
            {
                if (col != null && row.get(col.getName()) != null && col.getJdbcType() == JdbcType.VARCHAR && col.getScale() > 0)
                {
                    String value = row.get(col.getName()).toString();
                    if (value != null && value.length() > col.getScale())
                        throw new ValidationException("Value is too long for field " + col.getLabel() + ", a maximum length of " + col.getScale() + " is allowed.");
                }
            }
        }
    }
}
