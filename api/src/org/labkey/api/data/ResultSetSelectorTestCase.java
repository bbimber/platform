package org.labkey.api.data;

import org.junit.Test;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 12/18/12
 * Time: 8:04 PM
 */
public class ResultSetSelectorTestCase extends AbstractSelectorTestCase<ResultSetSelector>
{
    @Test
    public void test() throws SQLException
    {
        CoreSchema core = CoreSchema.getInstance();
        test(core.getTableInfoActiveUsers(), User.class);
        test(core.getTableInfoModules(), ModuleContext.class);

        SqlSelector sqlSelector = new SqlSelector(core.getSchema(), "SELECT RowId, Body FROM comm.Announcements");
        test(core.getSchema().getScope(), sqlSelector.getResultSet(), SqlSelectorTestCase.TestClass.class);

        // Test using metadata ResultSet
        DbScope scope = core.getSchema().getScope();
        ResultSet rs = null;
        Connection conn = null;

        try
        {
            conn = scope.getConnection();
            DatabaseMetaData dbmd = conn.getMetaData();

            rs = dbmd.getSchemas();

            test(scope, rs, SchemaBean.class);
        }
        finally
        {
            ResultSetUtil.close(rs);
            scope.releaseConnection(conn);
        }
    }

    private <K> void test(TableInfo tinfo, Class<K> clazz) throws SQLException
    {
        test(tinfo.getSchema().getScope(), new TableSelector(tinfo).getResultSet(), clazz);
    }

    private <K> void test(DbScope scope, ResultSet rs, Class<K> clazz) throws SQLException
    {
        try
        {
            ResultSetSelector selector = new ResultSetSelector(scope, rs, null);
            selector.setCompletionAction(ResultSetSelector.CompletionAction.ScrollToTop);

            test(selector, clazz);
        }
        finally
        {
            // Just in case a failure occurs during the test
            ResultSetUtil.close(rs);
        }
    }

    @Override
    protected void verifyResultSets(ResultSetSelector selector, int expectedRowCount, boolean expectedComplete) throws SQLException
    {
        ResultSet rs = selector.getUnderlyingResultSet();

        // Operations prior to this should not have closed the ResultSet
        verifyClosedStatus(rs, false);

        // This test method closes the ResultSet
        super.verifyResultSets(selector, expectedRowCount, expectedComplete);

        // Verify closed
        verifyClosedStatus(rs, true);
    }

    private void verifyClosedStatus(ResultSet rs, boolean expectedClosed) throws SQLException
    {
        try
        {
            if (expectedClosed)
                assertTrue("ResultSet should be closed", rs.isClosed());
            else
                assertFalse("ResultSet shouldn't be closed", rs.isClosed());
        }
        catch (AbstractMethodError e)
        {
            // We are testing a mix of JDBC3 and JDBC4 ResultSets (with no good way to distinguish them). JDBC3
            // ResultSets don't support isClosed(), so just ignore any AbstractMethodError.
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static class SchemaBean
    {
        private String _table_schem;
        private String _table_catalog;

        public String getTable_schem()
        {
            return _table_schem;
        }

        public void setTable_schem(String table_schema)
        {
            _table_schem = table_schema;
        }

        public String getTable_catalog()
        {
            return _table_catalog;
        }

        public void setTable_catalog(String table_catalog)
        {
            _table_catalog = table_catalog;
        }
    }
}
