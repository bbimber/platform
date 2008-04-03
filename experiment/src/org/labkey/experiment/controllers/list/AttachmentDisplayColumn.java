package org.labkey.experiment.controllers.list;

import org.labkey.api.data.*;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.Collections;

/**
 * User: adam
 * Date: Feb 12, 2008
 * Time: 1:52:50 PM
 */
public class AttachmentDisplayColumn extends DataColumn
{
    private ColumnInfo _colEntityId;

    public AttachmentDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    public String getURL(RenderContext ctx)
    {
        String filename = (String)getValue(ctx);
        String entityId = (String)_colEntityId.getValue(ctx);
        return ListController.getDownloadURL(ctx.getContainer(), entityId, filename).getLocalURIString();
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true);
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        String filename = (String)value;
        String formFieldName = ctx.getForm().getFormFieldName(getBoundColumn());
        String labelName = getBoundColumn().getAlias();

        // TODO: modify outputName to return a String and use that here
        String filePicker = "<input name=\"" + formFieldName + "\"";

        String setFocusId = (String)ctx.get("setFocusId");
        if (null != setFocusId)
        {
            filePicker += (" id=\"" + setFocusId + "\"");
            ctx.remove("setFocusId");
        }

        filePicker += " type=\"file\" size=\"60\" onChange=\"showPathname(this, &quot;" + labelName + "&quot;)\">&nbsp;<label class=\"normal\" id=\"" + labelName + "\"></label>\n";

        if (null == filename)
        {
            out.write(filePicker);
        }
        else
        {
            String divId = "div_" + getBoundColumn().getAlias();

            out.write("<div id=\"" + divId + "\">");
            renderIconAndFilename(ctx, out, filename, false);
            out.write("&nbsp;[<a href=\"javascript:{}\" onClick=\"");

            out.write("document.getElementById('" + divId + "').innerHTML = " + PageFlowUtil.jsString(filePicker + "&nbsp;<input type=\"hidden\" name=\"deletedAttachments\" value=\"" + filename + "\">[<span style=\"color:green;\">Previous file, " + filename + ", has been deleted; cancel to restore it.</span>]").replaceAll("\"", "\\\\'") + "\" style=\"color:green;\"");
            out.write(">Delete");
            out.write("</a>]\n");
            out.write("</div>");
        }
    }

    private void renderIconAndFilename(RenderContext ctx, Writer out, String filename, boolean link) throws IOException
    {
        if (null != filename)
        {
            if (link)
            {
                out.write("<a href=\"");
                out.write(PageFlowUtil.filter(getURL(ctx)));
                out.write("\">");
            }

            out.write("<img border=0 class=\"link\" src=\"" + ctx.getRequest().getContextPath() + Attachment.getFileIcon(filename) + "\" alt=\"icon\"/>&nbsp;" + filename);

            if (link)
            {
                out.write("</a>");
            }
        }
        else
        {
            out.write("&nbsp;");
        }
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        TableInfo table = getBoundColumn().getParentTable();
        FieldKey currentKey = FieldKey.fromString(getBoundColumn().getName());
        FieldKey parentKey = currentKey.getParent();

        FieldKey entityKey = new FieldKey(parentKey, "EntityId");

        _colEntityId = QueryService.get().getColumns(table, Collections.singleton(entityKey)).get(entityKey);
        columns.add(_colEntityId);
    }
}
