package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * A simple wrapper around another QueryUpdateService. All of the real work is delegated through, but
 * this class is responsible for clearing a cache in a subclass-specific way
 * after a database change has happened.
 *
 * User: jeckels
 * Date: 10/31/2014
 */
public abstract class CacheClearingQueryUpdateService implements QueryUpdateService
{
    private final QueryUpdateService _service;

    public CacheClearingQueryUpdateService(QueryUpdateService service)
    {
        _service = service;
    }

    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        return _service.getRows(user, container, keys);
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<String, Object> extraScriptContext) throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = _service.insertRows(user, container, rows, errors, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<String, Object> extraScriptContext) throws SQLException
    {
        int result = _service.importRows(user, container, rows, errors, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws SQLException
    {
        int result = _service.importRows(user, container, rows, errors, configParameters, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<String, Object> extraScriptContext) throws SQLException
    {
        int result = _service.mergeRows(user, container, rows, errors, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = _service.updateRows(user, container, rows, oldKeys, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = _service.deleteRows(user, container, keys, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public int truncateRows(User user, Container container, Map<String, Object> extraScriptContext) throws BatchValidationException, QueryUpdateServiceException, SQLException
    {
        int result = _service.truncateRows(user, container, extraScriptContext);
        clearCache();
        return result;
    }

    /**
     * Clear the cache after some sort of database change has happened
     */
    protected abstract void clearCache();

    @Override
    public void setBulkLoad(boolean bulkLoad)
    {
        _service.setBulkLoad(bulkLoad);
    }

    @Override
    public boolean isBulkLoad()
    {
        return _service.isBulkLoad();
    }
}
