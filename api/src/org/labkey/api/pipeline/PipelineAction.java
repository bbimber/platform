package org.labkey.api.pipeline;

import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;

/**
 * Represents an action that might be performed on a set of files in the pipeline.
 * User: jeckels
 * Date: Dec 16, 2009
 */
public class PipelineAction
{
    String _description;
    NavTree _links;
    File[] _files;

    /** Use NavTree to create a drop-down menu with submenus for the specified files */
    public PipelineAction(NavTree links, File[] files)
    {
        _links = links;
        _files = files;
    }

    /** Use a simple button for the specified files */
    public PipelineAction(String label, ActionURL href, File[] files)
    {
        this(new NavTree(label, href), files);
    }

    public String getLabel()
    {
        return _links.getKey();
    }

    public File[] getFiles()
    {
        return _files;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public JSONObject toJSON()
    {
        JSONObject o = new JSONObject();

        o.put("description", _description==null ? "" : _description);

        JSONObject links = _links.toJSON();
        o.put("links", links);

        JSONArray files = new JSONArray();
        if (null != _files)
            for (File f : _files)
                files.put(f.getName());
        o.put("files", files);

        return o;
    }
}
