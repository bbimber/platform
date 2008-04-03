package org.labkey.pipeline.browse;

import org.labkey.api.pipeline.browse.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.URIUtil;
import org.labkey.api.jsp.FormPage;
import org.labkey.common.util.Pair;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.io.File;
import java.util.*;

public class BrowseViewImpl extends BrowseView
{
    static abstract public class Page extends FormPage<BrowseForm>
    {
        protected List<Map.Entry<String, BrowseFile>> parents;
        protected List<BrowseFile> browseFiles;
        private Set<String> selectedFiles;

        @Override
        public void setForm(BrowseForm viewForm)
        {
            super.setForm(viewForm);
            BrowseForm form = getForm();
            PipeRoot pipeRoot;
            try
            {
                pipeRoot = PipelineService.get().findPipelineRoot(getContainer());
            }
            catch (SQLException e)
            {
                throw UnexpectedException.wrap(e);
            }
            FileFilter fileFilter = form.getFileFilterObject();
            File browsePath = null;
            if (form.getPath() == null)
            {
                String path = pipeRoot.getStartingPath(getContainer(), getUser());
                if (path != null)
                {
                    browsePath = new File(URIUtil.resolve(pipeRoot.getUri(), path));
                }
            }
            else
            {
                browsePath = pipeRoot.resolvePath(form.getPath());
                if (browsePath != null)
                {
                    pipeRoot.rememberStartingPath(getContainer(), getUser(), URIUtil.relativize(pipeRoot.getUri(), browsePath.toURI()).toString());
                }
            }
            if (browsePath == null)
            {
                browsePath = pipeRoot.getRootPath();
            }
            parents = new ArrayList();
            File currentPath = browsePath;
            while(true)
            {
                BrowseFile bf = new BrowseFile(pipeRoot, currentPath);
                if (bf.getRelativePath().length() == 0)
                {
                    break;
                }
                parents.add(0, new Pair(bf.getName(), bf));
                currentPath = currentPath.getParentFile();
            }
            parents.add(0, new Pair("root", new BrowseFile(pipeRoot, pipeRoot.getRootPath())));
            browseFiles = new ArrayList();
            File[] files = browsePath.listFiles();
            if (files != null)
            {
                for (File file : files)
                {
                    BrowseFile bf = new BrowseFile(pipeRoot, file);
                    if (bf.isDirectory())
                    {
                        browseFiles.add(bf);
                    }
                    else
                    {
                        if (fileFilter.accept(bf))
                        {
                            browseFiles.add(bf);
                        }
                    }
                }
            }
            Collections.sort(browseFiles, form.getBrowseFileComparator());

            selectedFiles = new HashSet(Arrays.asList(form.getFile()));

        }

        public boolean isFileSelected(BrowseFile file)
        {
            return selectedFiles.contains(file.getRelativePath());
        }

        public boolean isMultiSelect()
        {
            return getForm().isMultiSelect();
        }

        public boolean isDirectoriesSelectable()
        {
            return getForm().isDirectoriesSelectable();
        }

        protected BrowseForm getForm()
        {
            return (BrowseForm) __form;
        }

        public ActionURL getUrlBrowsePath()
        {
            ActionURL ret = getViewContext().cloneActionURL();
            ret.deleteParameter("path");
            return ret;
        }

        public String paramName(BrowseForm.Param param)
        {
            return getForm().paramName(param); 
        }
    }
    Page page;
    public BrowseViewImpl(BrowseForm form)
    {
        page = (Page) FormPage.get(BrowseViewImpl.class, form, "browse.jsp");
    }

    protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        JspView view = new JspView(page);
        view.render(request, response);
    }

}
