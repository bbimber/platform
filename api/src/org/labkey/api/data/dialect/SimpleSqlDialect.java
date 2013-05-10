/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.api.data.dialect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Aug 18, 2010
 * Time: 1:10:39 PM
 */

// Extend this to implement a dialect that will work as an external data source.
public abstract class SimpleSqlDialect extends SqlDialect
{
    // The following methods must be implemented by all dialects.  Standard implementations are provided; override them
    // if your dialect requires it.

    @Override
    public boolean isSqlServer()
    {
        return false;
    }

    @Override
    public boolean isPostgreSQL()
    {
        return false;
    }

    @Override
    public boolean isOracle()
    {
        return false;
    }

    @Override
    public void initializeConnection(Connection conn) throws SQLException
    {
    }

    @Override
    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return true;
    }

    @Override
    public boolean supportsComments()
    {
        return true;
    }

    @Override
    public boolean requiresStatementMaxRows()
    {
        return false;
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }

    @Override
    public String getCaseInsensitiveLikeOperator()
    {
        return "LIKE";
    }

    @Override
    public boolean supportsGroupConcat()
    {
        return false;
    }

    @Override
    public boolean supportsSelectConcat()
    {
        return false;
    }

    @Override
    public boolean treatCatalogsAsSchemas()
    {
        return false;
    }

    @Override
    public boolean canShowExecutionPlan()
    {
        return false;
    }

    @Override
    public Collection<String> getQueryExecutionPlan(DbScope scope, SQLFragment sql)
    {
        throw new IllegalStateException("Should not call when canShowExecutionPlan() returns false");
    }

    // The following methods may or may not need to be implemented in a simple dialect... if these exceptions appear
    // then either provide a standard implementation above or remove the stub implementation from this class.

    @Override
    public String getBooleanLiteral(boolean b)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    protected String sqlTypeNameFromSqlType(PropertyStorageSpec prop)
    {
        if (prop.isAutoIncrement())
        {
            throw new IllegalArgumentException("AutoIncrement is not supported for SQL type " + prop.getJdbcType().sqlType + " (" + sqlTypeNameFromSqlType(prop.getJdbcType().sqlType) + ")");
        }
        else
        {
            return sqlTypeNameFromSqlType(prop.getJdbcType().sqlType);
        }
    }

    @Override
    public void appendStatement(Appendable sql, String statement)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public ResultSet executeWithResults(@NotNull PreparedStatement stmt) throws SQLException
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public void appendSelectAutoIncrement(Appendable sql, TableInfo table, String columnName, @Nullable String variable)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public void addReselect(SQLFragment sql, String columnName, @Nullable String variable)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public SQLFragment limitRows(SQLFragment sql, int maxRows)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int rowCount, long offset)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean supportsOffset()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public SQLFragment execute(DbSchema schema, String procedureName, SQLFragment parameters)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isSystemSchema(String schemaName)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected String getSIDQuery()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getCharClassLikeOperator()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getVarcharLengthFunction()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getStdDevFunction()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getClobLengthFunction()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public SQLFragment getStringIndexOfFunction(SQLFragment toFind, SQLFragment toSearch)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getSubstringFunction(String s, String start, String length)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted, @NotNull String delimiterSQL)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public SQLFragment getSelectConcat(SQLFragment selectSql, String delimeter)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getDefaultDateTimeDataType()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getUniqueIdentType()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getGuidType()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getTempTableKeyword()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getTempTablePrefix()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getGlobalTempTablePrefix()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getDropIndexCommand(String tableName, String indexName)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getDateDiff(int part, String value1, String value2)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getDatePart(int part, String value)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getDateTimeToDateCast(String expression)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getRoundFunction(String valueToRound)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public boolean supportsRoundDouble()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public void overrideAutoIncrement(StringBuilder statements, TableInfo tinfo)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String sanitizeException(SQLException ex)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getAnalyzeCommandForTable(String tableName)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getBooleanDataType()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public boolean isCaseSensitive()
    {
        // This is a reasonable default assumption... nothing bad happens if the database happens to be case-insensitive
        return true;
    }

    // The following methods should never be called on a simple dialect.

    @Override
    public String getSQLScriptPath()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getMasterDataBaseName()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public void runSql(DbSchema schema, String sql, UpgradeCode upgradeCode, ModuleContext moduleContext, Connection conn)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public boolean isNoDatabaseException(SQLException e)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getCreateDatabaseSql(String dbName)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getCreateSchemaSql(String schemaName)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public List<String> getChangeStatements(TableChange change)
    {
        throw new RuntimeException("schema changes not currently supported via SimpleSqlDialect");
    }
}
