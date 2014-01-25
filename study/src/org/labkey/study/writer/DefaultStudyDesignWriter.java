package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.Results;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.writer.VirtualFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Created by klum on 1/24/14.
 */
public abstract class DefaultStudyDesignWriter
{
    protected void writeTableData(StudyExportContext ctx, VirtualFile vf, Set<TableInfo> tables, @Nullable ContainerFilter containerFilter) throws SQLException, IOException
    {
        for (TableInfo tinfo : tables)
        {
            writeTableData(ctx, vf, tinfo, tinfo.getColumns(), containerFilter);
        }
    }

    protected void writeTableData(StudyExportContext ctx, VirtualFile vf, TableInfo table, List<ColumnInfo> columns,
                                @Nullable ContainerFilter containerFilter) throws SQLException, IOException
    {
        // Write each table as a separate .tsv
        if (table != null)
        {
            if (containerFilter != null)
            {
                if (table instanceof ContainerFilterable)
                {
                    ((ContainerFilterable)table).setContainerFilter(containerFilter);
                }
            }
            Results rs = QueryService.get().select(table, columns, null, null);
            writeResultsToTSV(rs, vf, getFileName(table));
        }
    }

    protected String getFileName(TableInfo tableInfo)
    {
        return tableInfo.getName().toLowerCase() + ".tsv";
    }

    protected void writeResultsToTSV(Results rs, VirtualFile vf, String fileName) throws SQLException, IOException
    {
        TSVGridWriter tsvWriter = new TSVGridWriter(rs);
        tsvWriter.setApplyFormats(false);
        tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
        PrintWriter out = vf.getPrintWriter(fileName);
        tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
    }
}
