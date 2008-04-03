package org.labkey.study.requirements;

import org.labkey.api.security.User;
import org.labkey.api.data.Table;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;

import java.sql.SQLException;

/**
 * User: brittp
 * Date: Jun 7, 2007
 * Time: 4:29:24 PM
 */
public abstract class DefaultRequirement<R extends DefaultRequirement<R>> implements Requirement<R>
{
    public R update(User user)
    {
        try
        {
            return Table.update(user, getTableInfo(), (R) this, getPrimaryKeyValue(), null);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void delete()
    {
        try
        {
            Table.delete(getTableInfo(), getPrimaryKeyValue(), null);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public R persist(User user, String ownerEntityId)
    {
        if (getContainer() == null)
            throw new IllegalArgumentException("Container must be set for all requirements.");
        if (ownerEntityId == null)
            throw new IllegalArgumentException("Owner entity Id must be provided for all requirements.");
        try
        {
            R mutable = createMutable();
            mutable.setOwnerEntityId(ownerEntityId);
            return Table.insert(user, getTableInfo(), mutable);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    protected abstract TableInfo getTableInfo();
    protected abstract Object getPrimaryKeyValue();
}
