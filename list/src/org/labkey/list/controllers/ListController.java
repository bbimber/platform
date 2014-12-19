/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.list.controllers;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.DownloadURL;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.defaults.ClearDefaultValuesAction;
import org.labkey.api.defaults.SetDefaultValuesListAction;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.list.ListUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.query.AbstractQueryImportAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpPostRedirectView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.ZipFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.list.model.ListAuditProvider;
import org.labkey.list.model.ListAuditViewFactory;
import org.labkey.list.model.ListEditorServiceImpl;
import org.labkey.list.model.ListImporter;
import org.labkey.list.model.ListManager;
import org.labkey.list.model.ListManagerSchema;
import org.labkey.list.model.ListWriter;
import org.labkey.list.view.ListDefinitionForm;
import org.labkey.list.view.ListImportServiceImpl;
import org.labkey.list.view.ListItemAttachmentParent;
import org.labkey.list.view.ListQueryForm;
import org.labkey.list.view.ListQueryView;
import org.springframework.beans.PropertyValue;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: adam
 * Date: Dec 30, 2007
 * Time: 12:44:30 PM
 */
public class ListController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ListController.class,
            SetDefaultValuesListAction.class,
            ClearDefaultValuesAction.class
            );

    public ListController()
    {
        setActionResolver(_actionResolver);
    }


    private NavTree appendRootNavTrail(NavTree root)
    {
        return appendRootNavTrail(root, getContainer(), getUser());
    }

    public static class ListUrlsImpl implements ListUrls
    {
        @Override
        public ActionURL getManageListsURL(Container c)
        {
            return new ActionURL(ListController.BeginAction.class, c);
        }

        @Override
        public ActionURL getCreateListURL(Container c)
        {
            return new ActionURL(EditListDefinitionAction.class, c);
        }

    }


    public static NavTree appendRootNavTrail(NavTree root, Container c, User user)
    {
        if (c.hasPermission(user, AdminPermission.class) || user.isDeveloper())
        {
            root.addChild("Lists", getBeginURL(c));
        }
        return root;
    }


    private NavTree appendListNavTrail(NavTree root, ListDefinition list, @Nullable String title)
    {
        appendRootNavTrail(root);
        root.addChild(list.getName(), list.urlShowData());

        if (null != title)
            root.addChild(title);

        return root;
    }


    public static ActionURL getBeginURL(Container c)
    {
        return new ActionURL(BeginAction.class, c);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        return config.setHelpTopic(new HelpTopic("lists"));
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm queryForm, BindException errors) throws Exception
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), ListManagerSchema.SCHEMA_NAME);
            QuerySettings settings = schema.getSettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, ListManagerSchema.LIST_MANAGER);
            QueryView queryView = schema.createView(getViewContext(), settings, errors);
            return queryView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Available Lists");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DomainImportServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ListImportServiceImpl(getViewContext());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ShowListDefinitionAction extends SimpleRedirectAction<ListDefinitionForm>
    {
        @Override
        public ActionURL getRedirectURL(ListDefinitionForm listDefinitionForm) throws Exception
        {
            if (listDefinitionForm.getListId() == null)
            {
                throw new NotFoundException();
            }
            return new ActionURL(EditListDefinitionAction.class, getContainer()).addParameter("listId", listDefinitionForm.getListId().intValue());
        }
    }


    @RequiresPermissionClass(DesignListPermission.class)
    public class EditListDefinitionAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = null;

            boolean createList = (null == form.getListId() || 0 == form.getListId()) && form.getName() == null;
            if (!createList)
                _list = form.getList();

            Map<String, String> props = new HashMap<>();

            URLHelper returnURL = form.getReturnURLHelper();

            props.put("listId", null == _list ? "0" : String.valueOf(_list.getListId()));
            props.put(ActionURL.Param.returnUrl.name(), returnURL.toString());
            props.put("allowFileLinkProperties", "0");
            props.put("allowAttachmentProperties", "1");
            props.put("showDefaultValueSettings", "1");
            props.put("hasDesignListPermission", getContainer().hasPermission(getUser(), DesignListPermission.class) ? "true":"false");
            props.put("hasInsertPermission", getContainer().hasPermission(getUser(), InsertPermission.class) ? "true":"false");
            // Why is this different than DesignListPermission???
            props.put("hasDeleteListPermission", getContainer().hasPermission(getUser(), AdminPermission.class) ? "true":"false");
            props.put("loading", "Loading...");

            return new GWTView("org.labkey.list.Designer", props);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (null == _list)
                root.addChild("Create new List");
            else
                appendListNavTrail(root, _list, null);
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    @Action(ActionType.SelectMetaData)
    public class ListEditorServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ListEditorServiceImpl(getViewContext());
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteListDefinitionAction extends ConfirmAction<ListDefinitionForm>
    {
        private ArrayList<Integer> _listIDs = new ArrayList<>();
        private ArrayList<Container> _containers = new ArrayList<>();

        public void validateCommand(ListDefinitionForm form, Errors errors)
        {
            if (form.getListId() == null)
            {
                String failMessage = "You do not have permission to delete: \n";
                Set<String> listIDs = DataRegionSelection.getSelected(form.getViewContext(), true);
                for (String s : listIDs)
                {
                    String[] parts = s.split(",");
                    Container c = ContainerManager.getForId(parts[1]);
                    if(c.hasPermission(getUser(), AdminPermission.class)){
                        _listIDs.add(Integer.parseInt(parts[0]));
                        _containers.add(c);
                    }
                    else
                    {
                        failMessage = failMessage + "\t" + ListService.get().getList(c, Integer.parseInt(parts[0])).getName() + " in Container: " + c.getName() +"\n";
                    }
                }
                if(!failMessage.equals("You do not have permission to delete: \n"))
                    errors.reject("DELETE PERMISSION ERROR", failMessage);
            }
            else
            {
                //Accessed from the edit list page, where selection is not possible
                _listIDs.add(form.getListId());
                _containers.add(getContainer());
            }
        }

        @Override
        public ModelAndView getConfirmView(ListDefinitionForm form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/list/view/deleteListDefinition.jsp", form, errors);
        }

        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
        {
            for(int i = 0; i < _listIDs.size(); i++)
            {
                ListService.get().getList(_containers.get(i), _listIDs.get(i)).delete(getUser());
            }
            return true;
        }

        @NotNull
        public URLHelper getSuccessURL(ListDefinitionForm form)
        {
            try
            {
                ReturnURLString ret = form.getReturnUrl();
                if(ret != null)
                {
                    return new URLHelper(ret);
                }
                else
                {
                    return getBeginURL(getContainer());
                }
            }
            catch (URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class GridAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;
        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            if (null == _list)
                throw new NotFoundException("List does not exist in this container");
            return new ListQueryView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, null);
        }
    }


    public abstract class InsertUpdateAction extends FormViewAction<ListDefinitionForm>
    {
        protected abstract ActionURL getActionView(ListDefinition list, BindException errors);
        protected abstract Collection<Pair<String, String>> getInputs(ListDefinition list, ActionURL url, PropertyValue[] propertyValues);

        @Override
        public void validateCommand(ListDefinitionForm form, Errors errors)
        {
            /* No-op */
        }

        @Override
        public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors) throws Exception
        {
            ListDefinition list = form.getList(); // throws NotFoundException

            ActionURL url = getActionView(list, errors);
            Collection<Pair<String, String>> inputs = getInputs(list, url, getPropertyValues().getPropertyValues());

            if (getViewContext().getRequest().getMethod().equalsIgnoreCase("POST"))
            {
                getPageConfig().setTemplate(PageConfig.Template.None);
                return new HttpPostRedirectView(url.toString(), inputs);
            }

            throw new RedirectException(url);
        }

        @Override
        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ListDefinitionForm form)
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    /**
     * DO NOT USE. This action has been deprecated in 13.2 in favor of the standard query/insertQueryRow action.
     * Only here for backwards compatibility to resolve requests and redirect.
     */
    @Deprecated
    @RequiresPermissionClass(InsertPermission.class)
    public class InsertAction extends InsertUpdateAction
    {
        @Override
        protected ActionURL getActionView(ListDefinition list, BindException errors)
        {
            TableInfo listTable = list.getTable(getUser());
            return listTable.getUserSchema().getQueryDefForTable(listTable.getName()).urlFor(QueryAction.insertQueryRow, getContainer());
        }

        @Override
        protected Collection<Pair<String, String>> getInputs(ListDefinition list, ActionURL url, PropertyValue[] propertyValues)
        {
            Collection<Pair<String, String>> inputs = new ArrayList<>();

            for (PropertyValue value : propertyValues)
            {
                if (value.getName().equalsIgnoreCase("returnURL"))
                    url.addParameter("returnUrl", (String) value.getValue());
                else
                    inputs.add(Pair.of(value.getName(), value.getValue().toString()));
            }

            return inputs;
        }
    }


    /**
     * DO NOT USE. This action has been deprecated in 13.2 in favor of the standard query/updateQueryRow action.
     * Only here for backwards compatibility to resolve requests and redirect.
     */
    @Deprecated
    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateAction extends InsertUpdateAction
    {
        @Override
        protected ActionURL getActionView(ListDefinition list, BindException errors)
        {
            TableInfo listTable = list.getTable(getUser());
            return listTable.getUserSchema().getQueryDefForTable(listTable.getName()).urlFor(QueryAction.updateQueryRow, getContainer());
        }

        @Override
        protected Collection<Pair<String, String>> getInputs(ListDefinition list, ActionURL url, PropertyValue[] propertyValues)
        {
            Collection<Pair<String, String>> inputs = new ArrayList<>();
            final String FORM_PREFIX = "quf_";

            for (PropertyValue value : getPropertyValues().getPropertyValues())
            {
                if (value.getName().equalsIgnoreCase("returnURL"))
                    url.addParameter("returnUrl", (String) value.getValue());
                else if (value.getName().equalsIgnoreCase(list.getKeyName()) || (FORM_PREFIX + list.getKeyName()).equalsIgnoreCase(value.getName()))
                {
                    url.addParameter(list.getKeyName(), (String) value.getValue());
                    inputs.add(Pair.of(value.getName(), value.getValue().toString()));
                }
                else
                    inputs.add(Pair.of(value.getName(), value.getValue().toString()));
            }

            // support for .oldValues
            try
            {
                // convert to map
                HashMap<String, Object> oldValues = new HashMap<>();
                for (Pair<String, String> entry : inputs)
                {
                    oldValues.put(entry.getKey().replace(FORM_PREFIX, ""), entry.getValue());
                }
                inputs.add(Pair.of(".oldValues", PageFlowUtil.encodeObject(oldValues)));
            }
            catch (IOException e)
            {
                throw new RuntimeException("Bad .oldValues on List.UpdateAction");
            }


            return inputs;
        }
    }


    // Unfortunate query hackery that orders details columns based on default view
    // TODO: Fix this... build into InsertView (or QueryInsertView or something)
    private void setDisplayColumnsFromDefaultView(int listId, DataRegion rgn)
    {
        ListQueryView lqv = new ListQueryView(new ListQueryForm(listId, getViewContext()), null);
        List<DisplayColumn> defaultGridColumns = lqv.getDisplayColumns();
        List<DisplayColumn> displayColumns = new ArrayList<>(defaultGridColumns.size());

        // Save old grid column list
        List<String> currentColumns = rgn.getDisplayColumnNames();

        rgn.setTable(lqv.getTable());

        for (DisplayColumn dc : defaultGridColumns)
        {
            assert null != dc;

            // Occasionally in production this comes back null -- not sure why.  See #8088
            if (null == dc)
                continue;

            if (dc instanceof UrlColumn)
                continue;

            if (dc.getColumnInfo() != null && dc.getColumnInfo().isShownInDetailsView())
            {
                displayColumns.add(dc);
            }
        }

        rgn.setDisplayColumns(displayColumns);

        // Add all columns that aren't in the default grid view
        for (String columnName : currentColumns)
            if (null == rgn.getDisplayColumn(columnName))
                rgn.addColumn(rgn.getTable().getColumn(columnName));
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            TableInfo table = _list.getTable(getUser(), getContainer());

            if (null == table)
                throw new NotFoundException("List does not exist");

            ListQueryUpdateForm tableForm = new ListQueryUpdateForm(table, getViewContext(), _list, errors);
            DetailsView details = new DetailsView(tableForm);

            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);

            if (table.hasPermission(getUser(), UpdatePermission.class))
            {
                ActionURL updateUrl = _list.urlUpdate(getUser(), getContainer(), tableForm.getPkVal(), getViewContext().getActionURL());
                ActionButton editButton = new ActionButton("Edit", updateUrl);
                bb.add(editButton);
            }

            ActionButton gridButton;
            ActionURL gridUrl = _list.urlShowData(getViewContext().getContainer());
            if (form.getReturnUrl() != null)
            {
                URLHelper url = form.getReturnURLHelper();
                String text = "Return";
                if(url != null && gridUrl.getPath().equalsIgnoreCase(url.getPath()))
                    text = "Show Grid";

                gridButton = new ActionButton(text, url);
            }
            else
                gridButton = new ActionButton("Show Grid", gridUrl);

            bb.add(gridButton);
            details.getDataRegion().setButtonBar(bb);
            setDisplayColumnsFromDefaultView(_list.getListId(), details.getDataRegion());

            VBox view = new VBox();
            ListItem item;
            item = _list.getListItem(tableForm.getPkVal(), getUser(), getContainer());

            if (null == item)
                throw new NotFoundException("List item '" + tableForm.getPkVal() + "' does not exist");

            view.addView(details);

            if (form.isShowHistory())
            {
                WebPartView linkView = new HtmlView(PageFlowUtil.textLink("hide item history", getViewContext().cloneActionURL().deleteParameter("showHistory")));
                linkView.setFrame(WebPartView.FrameType.NONE);
                view.addView(linkView);

                if (AuditLogService.get().isMigrateComplete() || AuditLogService.get().hasEventTypeMigrated(ListManager.LIST_AUDIT_EVENT))
                {
                    UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
                    if (schema != null)
                    {
                        QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                        SimpleFilter filter = new SimpleFilter();
                        filter.addCondition(FieldKey.fromParts(ListAuditProvider.COLUMN_NAME_LIST_ITEM_ENTITY_ID), item.getEntityId());

                        settings.setBaseFilter(filter);
                        settings.setQueryName(ListManager.LIST_AUDIT_EVENT);
                        QueryView history = schema.createView(getViewContext(), settings, errors);

                        history.setTitle("List Item History:");
                        history.setFrame(WebPartView.FrameType.NONE);
                        view.addView(history);
                    }
                }
                else
                {
                    WebPartView history = ListAuditViewFactory.getInstance().createListItemDetailsView(getViewContext(), item.getEntityId());
                    history.setFrame(WebPartView.FrameType.NONE);
                    view.addView(history);
                }
            }
            else
            {
                view.addView(new HtmlView(PageFlowUtil.textLink("show item history", getViewContext().cloneActionURL().addParameter("showHistory", "1"))));
            }

            if (_list.getDiscussionSetting().isLinked())
            {
                String entityId = item.getEntityId();

                DomainProperty titleProperty = null;
                Domain d = _list.getDomain();
                if (null != d)
                    titleProperty = d.getPropertyByName(table.getTitleColumn());

                Object title = (null != titleProperty ? item.getProperty(titleProperty) : null);
                String discussionTitle = (null != title ? title.toString() : "Item " + tableForm.getPkVal());

                ActionURL linkBackURL = _list.urlFor(ResolveAction.class).addParameter("entityId", entityId);
                DiscussionService.Service service = DiscussionService.get();
                boolean multiple = _list.getDiscussionSetting() == ListDefinition.DiscussionSetting.ManyPerItem;

                // Display discussion by default in single-discussion case, #4529
                DiscussionService.DiscussionView discussion = service.getDisussionArea(getViewContext(), entityId, linkBackURL, discussionTitle, multiple, !multiple);
                view.addView(discussion);

                getPageConfig().setFocusId(discussion.getFocusId());
            }

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "View List Item");
        }
    }


    // Override to ensure that pk value type matches column type.  This is critical for PostgreSQL 8.3.
    public static class ListQueryUpdateForm extends QueryUpdateForm
    {
        private ListDefinition _list;

        public ListQueryUpdateForm(TableInfo table, ViewContext ctx, ListDefinition list, BindException errors)
        {
            super(table, ctx, errors);
            _list = list;
        }

        public Object[] getPkVals()
        {
            Object[] pks = super.getPkVals();
            assert 1 == pks.length;
            pks[0] = _list.getKeyType().convertKey(pks[0]);
            return pks;
        }

        public Domain getDomain()
        {
            return _list != null ? _list.getDomain() : null;
        }
    }


    // Users can change the PK of a list item, so we don't want to store PK in discussion source URL (back link
    // from announcements to the object).  Instead, we tell discussion service to store a URL with ListId and
    // EntityId.  This action resolves to the current details URL for that item.
    @RequiresPermissionClass(ReadPermission.class)
    public class ResolveAction extends SimpleRedirectAction<ListDefinitionForm>
    {
        public ActionURL getRedirectURL(ListDefinitionForm form) throws Exception
        {
            ListDefinition list = form.getList();
            ListItem item = list.getListItemForEntityId(getViewContext().getActionURL().getParameter("entityId"), getUser()); // TODO: Use proper form, validate
            ActionURL url = getViewContext().cloneActionURL().setAction(DetailsAction.class);   // Clone to preserve discussion params
            url.deleteParameter("entityId");
            url.addParameter("pk", item.getKey().toString());

            return url;
        }
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class UploadListItemsAction extends AbstractQueryImportAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public UploadListItemsAction()
        {
            super(ListDefinitionForm.class);
        }
        
        @Override
        protected void initRequest(ListDefinitionForm form) throws ServletException
        {
            _list = form.getList();
            setTarget(_list.getTable(getUser(), getContainer()));
        }

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            initRequest(form);
            return getDefaultImportView(form, errors);
        }

        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors) throws IOException
        {
            int count = _list.insertListItems(getUser(),getContainer() , dl, errors, null, null, false);
            return count;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Import Data");
        }
    }

    
    @RequiresPermissionClass(ReadPermission.class)
    public class HistoryAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            if (_list != null)
            {
                if (AuditLogService.get().isMigrateComplete() || AuditLogService.get().hasEventTypeMigrated(ListManager.LIST_AUDIT_EVENT))
                {
                    UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
                    if (schema != null)
                    {
                        VBox box = new VBox();
                        String domainUri = _list.getDomain().getTypeURI();

                        // list audit events
                        QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);
                        settings.setQueryName(ListManager.LIST_AUDIT_EVENT);
                        QueryView view = schema.createView(getViewContext(), settings, errors);
                        view.setTitle("List Events");
                        box.addView(view);

                        // domain audit events associated with this list
                        QuerySettings domainSettings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                        SimpleFilter domainFilter = new SimpleFilter();
                        domainFilter.addCondition(FieldKey.fromParts(DomainAuditProvider.COLUMN_NAME_DOMAIN_URI), domainUri);

                        domainSettings.setBaseFilter(domainFilter);
                        domainSettings.setQueryName(DomainAuditProvider.EVENT_TYPE);
                        QueryView domainView = schema.createView(getViewContext(), domainSettings, errors);

                        domainView.setTitle("List Design Changes");
                        box.addView(domainView);

                        return box;
                    }
                    return new HtmlView("Unable to create the List history view");
                }
                else
                    return ListAuditViewFactory.getInstance().createListHistoryView(getViewContext(), _list);
            }
            else
                return new HtmlView("Unable to find the specified List");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_list != null)
                return appendListNavTrail(root, _list, _list.getName() + ":History");
            else
                return root.addChild(":History");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ListItemDetailsAction extends SimpleViewAction
    {
        private ListDefinition _list;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int id = NumberUtils.toInt((String)getViewContext().get("rowId"));
            int listId = NumberUtils.toInt((String)getViewContext().get("listId"));
            _list = ListService.get().getList(getContainer(), listId);
            if (_list == null)
            {
                return new HtmlView("This list is no longer available.");
            }

            String comment = null;
            String oldRecord = null;
            String newRecord = null;

            if (AuditLogService.get().isMigrateComplete() || AuditLogService.get().hasEventTypeMigrated(ListManager.LIST_AUDIT_EVENT))
            {
                ListAuditProvider.ListAuditEvent event = AuditLogService.get().getAuditEvent(getUser(), ListManager.LIST_AUDIT_EVENT, id);

                if (event != null)
                {
                    comment = event.getComment();
                    oldRecord = event.getOldRecordMap();
                    newRecord = event.getNewRecordMap();
                }
            }
            else
            {
                AuditLogEvent event = AuditLogService.get().getEvent(id);
                if (event != null && event.getLsid() != null)
                {
                    comment = event.getComment();
                    Map<String, Object> dataMap = OntologyManager.getProperties(ContainerManager.getSharedContainer(), event.getLsid());
                    if (dataMap != null)
                    {
                        if (dataMap.containsKey(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, ListAuditViewFactory.OLD_RECORD_PROP_NAME)) ||
                                dataMap.containsKey(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, ListAuditViewFactory.NEW_RECORD_PROP_NAME)))
                        {
                            oldRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, ListAuditViewFactory.OLD_RECORD_PROP_NAME));
                            newRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, ListAuditViewFactory.NEW_RECORD_PROP_NAME));
                        }
                        else
                        {
                            oldRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "oldRecord"));
                            newRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "newRecord"));
                        }
                    }
                }
                else
                    throw new NotFoundException("Unable to find the audit history detail for this event");
            }

            if (!StringUtils.isEmpty(oldRecord) || !StringUtils.isEmpty(newRecord))
            {
                Map<String,String> oldData = ListAuditViewFactory.decodeFromDataMap(oldRecord);
                Map<String,String> newData = ListAuditViewFactory.decodeFromDataMap(newRecord);

                String srcUrl = getViewContext().getActionURL().getParameter(ActionURL.Param.redirectUrl);
                if (srcUrl == null)
                    srcUrl = getViewContext().getActionURL().getParameter(ActionURL.Param.returnUrl);
                if (srcUrl == null)
                    srcUrl = getViewContext().getActionURL().getParameter(QueryParam.srcURL);
                AuditChangesView view = new AuditChangesView(comment, oldData, newData);
                view.setReturnUrl(srcUrl);

                return view;
            }
            else
                return new HtmlView("No details available for this event.");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_list != null)
                return appendListNavTrail(root, _list, "List Item Details");
            else
                return root.addChild("List Item Details"); 
        }
    }


    /**
     * @deprecated delete after audit hard table migration
     */
    private static class ItemDetails extends WebPartView
    {
        String _comment;
        String _oldRecord;
        String _newRecord;
        boolean _isEncoded;
        String _returnUrl;

        public ItemDetails(String comment, String oldRecord, String newRecord, boolean isEncoded, String returnUrl)
        {
            _comment = comment;
            _oldRecord = oldRecord;
            _newRecord = newRecord;
            _isEncoded = isEncoded;
            _returnUrl = returnUrl;
        }

        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            if (_isEncoded)
            {
                _renderViewEncoded(out);
            }
            else
            {
                out.write("<table>\n");
                out.write("<tr><td>");
                if (_returnUrl != null)
                    out.write(PageFlowUtil.button("Done").href(_returnUrl).toString());
                out.write("</tr></td>");
                out.write("<tr><td></td></tr>");

                out.write("<tr class=\"labkey-wp-header\"><th align=\"left\">Item Changes</th></tr>");
                out.write("<tr><td>Comment:&nbsp;<i>" + PageFlowUtil.filter(_comment) + "</i></td></tr>");
                out.write("<tr><td><table>\n");
                if (!StringUtils.isEmpty(_oldRecord))
                    _renderRecord("previous:", _oldRecord, out);

                if (!StringUtils.isEmpty(_newRecord))
                    _renderRecord("current:", _newRecord, out);
                out.write("</table></td></tr>\n");
                out.write("</table>\n");
            }
        }

        private void _renderRecord(String title, String record, PrintWriter out)
        {
            out.write("<tr><td><b>" + title + "</b></td>");
            for (Pair<String, String> param : PageFlowUtil.fromQueryString(record))
            {
                out.write("<td>" + param.getValue() + "</td>");
            }
        }

        private void _renderViewEncoded(PrintWriter out)
        {
            Map<String, String> prevProps = ListAuditViewFactory.decodeFromDataMap(_oldRecord);
            Map<String, String> newProps = ListAuditViewFactory.decodeFromDataMap(_newRecord);
            int modified = 0;

            out.write("<table>\n");
            out.write("<tr class=\"labkey-wp-header\"><th colspan=\"2\" align=\"left\">Item Changes</th></tr>");
            out.write("<tr><td colspan=\"2\">Comment:&nbsp;<i>" + PageFlowUtil.filter(_comment) + "</i></td></tr>");
            out.write("<tr><td/>\n");

            for (Map.Entry<String, String> entry : prevProps.entrySet())
            {
                String newValue = newProps.remove(entry.getKey());
                if (!Objects.equals(newValue, entry.getValue()))
                {
                    out.write("<tr><td class=\"labkey-form-label\">");
                    out.write(PageFlowUtil.filter(entry.getKey()));
                    out.write("</td><td>");

                    modified++;
                    out.write(PageFlowUtil.filter(entry.getValue()));
                    out.write("&nbsp;&raquo;&nbsp;");
                    out.write(PageFlowUtil.filter(Objects.toString(newValue, "")));
                    out.write("</td></tr>\n");
                }
                else
                {
                    out.write("<tr><td class=\"labkey-form-label\">");
                    out.write(PageFlowUtil.filter(entry.getKey()));
                    out.write("</td><td>");
                    out.write(PageFlowUtil.filter(entry.getValue()));
                    out.write("</td></tr>\n");
                }
            }

            for (Map.Entry<String, String> entry : newProps.entrySet())
            {
                modified++;
                out.write("<tr><td class=\"labkey-form-label\">");
                out.write(PageFlowUtil.filter(entry.getKey()));
                out.write("</td><td>");

                out.write("&nbsp;&raquo;&nbsp;");
                out.write(PageFlowUtil.filter(Objects.toString(entry.getValue(), "")));
                out.write("</td></tr>\n");
            }
            out.write("<tr><td/>\n");
            out.write("<tr><td colspan=\"2\">Summary:&nbsp;<i>");
            if (1 == modified)
                out.write(modified + " field was modified");
            else
                out.write(modified + " fields were modified");
            out.write("</i></td></tr>");


            out.write("<tr><td>&nbsp;</td></tr>");
            out.write("<tr><td>");
            if (_returnUrl != null)
                out.write(PageFlowUtil.button("Done").href(_returnUrl).toString());
            out.write("</tr></td>");

            out.write("</table>\n");
        }
    }


    public static ActionURL getDownloadURL(Container c, String entityId, String filename)
    {
        return new DownloadURL(DownloadAction.class, c, entityId, filename);
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadAction extends SimpleViewAction<AttachmentForm>
    {
        public ModelAndView getView(final AttachmentForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            final AttachmentParent parent = new ListItemAttachmentParent(form.getEntityId(), getContainer());

            return new HttpView()
            {
                protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                {
                    AttachmentService.get().download(response, parent, form.getName());
                }
            };
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(DesignListPermission.class)
    public class ExportListArchiveAction extends ExportAction<ListDefinitionForm>
    {
        public void export(ListDefinitionForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            Set<String> listIDs = DataRegionSelection.getSelected(form.getViewContext(), true);
            Integer[] IDs = new Integer[listIDs.size()];
            int i = 0;
            for(String s : listIDs)
            {
                IDs[i] = Integer.parseInt(s.substring(0, s.indexOf(',')));
                i++;
            }
            Container c = getContainer();
            String datatype = ("lists");
            FolderExportContext ctx = new FolderExportContext(getUser(), c, PageFlowUtil.set(datatype), "List Export", new StaticLoggerGetter(Logger.getLogger(ListController.class)));
            ctx.setListIds(IDs);
            ListWriter writer = new ListWriter();

            try (ZipFile zip = new ZipFile(response, FileUtil.makeFileNameWithTimestamp(c.getName(), "lists.zip")))
            {
                writer.write(c, getUser(), zip, ctx);
            }
        }
    }


    @Deprecated // TODO: Delete me... this is just for manage lists migration purposes. See issues #22078 & #22080
    @RequiresPermissionClass(ReadPermission.class)
    public class OldBeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/list/view/begin.jsp", null, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Available Lists");
        }
    }

    @RequiresPermissionClass(DesignListPermission.class)
    public class ImportListArchiveAction extends FormViewAction<ListDefinitionForm>
    {
        public void validateCommand(ListDefinitionForm target, Errors errors)
        {
        }

        public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/list/view/importLists.jsp", null, errors);
        }

        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
        {
            Map<String, MultipartFile> map = getFileMap();

            if (map.isEmpty())
            {
                errors.reject("listImport", "You must select a .list.zip file to import.");
            }
            else if (map.size() > 1)
            {
                errors.reject("listImport", "Only one file is allowed.");
            }
            else
            {
                MultipartFile file = map.values().iterator().next();

                if (0 == file.getSize() || StringUtils.isBlank(file.getOriginalFilename()))
                {
                    errors.reject("listImport", "You must select a .list.zip file to import.");
                }
                else
                {
                    InputStream is = file.getInputStream();

                    File dir = FileUtil.createTempDirectory("list");
                    ZipUtil.unzipToDirectory(is, dir);

                    ListImporter li = new ListImporter();

                    List<String> errorList = new LinkedList<>();

                    try
                    {
                        li.process(new FileSystemFile(dir), getContainer(), getUser(), errorList, Logger.getLogger(ListController.class));

                        for (String error : errorList)
                            errors.reject(ERROR_MSG, error);
                    }
                    catch (InvalidFileException e)
                    {
                        errors.reject(ERROR_MSG, "Invalid list archive");
                    }
                }
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(ListDefinitionForm form)
        {
            ReturnURLString returnURLString = form.getReturnUrl();
            if (null != returnURLString)
                return new ActionURL(returnURLString);
            return getBeginURL(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Import List Archive");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseListsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            response.put("lists", getJSONLists(ListService.get().getLists(getContainer())));
            response.put("success", true);

            return response;
        }

        private List<JSONObject> getJSONLists(Map<String, ListDefinition> lists){
            List<JSONObject> listsJSON = new ArrayList<>();
            for(ListDefinition def : new TreeSet<>(lists.values())){
                JSONObject listObj = new JSONObject();
                listObj.put("name", def.getName());
                listObj.put("id", def.getListId());
                listObj.put("description", def.getDescription());
                listsJSON.add(listObj);
            }
            return listsJSON;
        }
    }
}
