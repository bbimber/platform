/*
 * Copyright (c) 2005 LabKey Software, LLC
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
package org.labkey.pipeline.status;

import org.labkey.api.data.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.pipeline.api.PipelineStatusManager;

import java.io.Writer;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * StatusDataRegion class
 * <p/>
 * Created: Mar 22, 2006
 *
 * @author bmaclean
 */
public class StatusDataRegion extends DataRegion
{
    private void renderTab(Writer out, String text, ActionURL url, boolean selected) throws IOException
    {
        String selectStyle = "";
        if (selected)
            selectStyle = " style=\"background-color: #e1ecfc\"";
        out.write("<td");
        out.write(selectStyle);
        out.write(">&nbsp;&nbsp;<a href=\"");
        out.write(url.getEncodedLocalURIString());
        out.write("\">");
        out.write(text);
        out.write("</a>&nbsp;&nbsp;</td>\n");
    }

    protected void _renderTable(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        ActionURL url = StatusController.urlShowList(ctx.getContainer(), false);
        ActionURL urlFilter = ctx.getSortFilterURLHelper();

        boolean selSeen = false;

        out.write("<table class=\"dataRegion\" border=\"0\"><tr>");
        out.write("<td>Show:</td>");

        String name = "StatusFiles.Status~neqornull";
        String value = PipelineJob.COMPLETE_STATUS;
        url.deleteParameters();
        url.addParameter(name, value);
        boolean selected = value.equals(urlFilter.getParameter(name));
        if (!selected && ctx.getBaseFilter() != null)
        {
            TableInfo tinfo = PipelineStatusManager.getTableInfo();
            // UNDONE: MAB this seems too implementation dependant!
            List values = ctx.getBaseFilter().getSQLFragment(tinfo, tinfo.getColumns("Status")).getParams();
            selected = (values != null && values.size() == 1 && value.equals(values.get(0)));
        }
        renderTab(out, "Running", url, selected);
        selSeen = selSeen || selected;

        name = "StatusFiles.Status~eq";
        value = PipelineJob.ERROR_STATUS;
        url.deleteParameters();
        url.addParameter(name, value);
        selected = !selSeen && value.equals(urlFilter.getParameter(name));
        renderTab(out, "Errors", url, selected);
        selSeen = selSeen || selected;

        url.deleteParameters();
        selected = !selSeen && urlFilter.getParameters().length == 0;
        renderTab(out, "All", url, selected);
        selSeen = selSeen || selected;

        out.write("</tr></table>");

        super._renderTable(ctx, out);
    }
}
