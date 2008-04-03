package org.labkey.query;

import org.labkey.data.xml.TableType;
import org.labkey.data.xml.ColumnType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.FieldKey;

import java.util.Collection;
import java.util.Arrays;
import java.util.Collections;

public class TableXML
{
    static public void initTable(TableType xbTable, TableInfo table, FieldKey key, Collection<ColumnInfo> colInfos)
    {
        if (key != null)
        {
            xbTable.setTableName(key.toString());
            xbTable.setTableTitle(key.getName());
        }
        TableType.Columns columns = xbTable.addNewColumns();
        if (table == null)
            return;
        for (ColumnInfo column : colInfos)
        {
            ColumnType xbColumn = columns.addNewColumn();
            xbColumn.setColumnName(column.getName());
            xbColumn.setColumnTitle(column.getCaption());
            xbColumn.setDatatype(column.getSqlTypeName());
            if (column.isHidden())
            {
                xbColumn.setIsHidden(column.isHidden());
            }
            if (column.isUnselectable())
            {
                xbColumn.setIsUnselectable(column.isUnselectable());
            }
            ForeignKey fk = column.getFk();
            if (fk instanceof RowIdForeignKey)
            {
                // We only allow drilling down into the rowid column if it was selected into a different table.
                if (((RowIdForeignKey) fk).getOriginalColumn() == column)
                {
                    fk = null;
                }
            }
            if (fk != null)
            {
                TableInfo lookupTable = fk.getLookupTableInfo();
                if (lookupTable != null)
                {
                    ColumnType.Fk xbFk = xbColumn.addNewFk();
                    xbFk.setFkTable(new FieldKey(key, column.getName()).toString());
                }
            }
        }
    }

    static public void initTable(TableType xbTable, TableInfo table, FieldKey key)
    {
        initTable(xbTable, table, key, table == null ? Collections.EMPTY_LIST : Arrays.asList(table.getColumns()));
    }

}
