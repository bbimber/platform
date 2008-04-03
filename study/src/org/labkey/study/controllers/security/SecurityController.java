package org.labkey.study.controllers.security;

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.struts.action.ActionMapping;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.Group;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.reports.ReportManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 24, 2006
 * Time: 5:31:02 PM
 */
public class SecurityController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(SecurityController.class);

    public SecurityController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("studySecurity", HelpTopic.Area.STUDY));
            Study study = StudyManager.getInstance().getStudy(getContainer());
            if (null == study)
                return HttpView.redirect(new ActionURL("Study", "begin", getContainer()));

            return new Overview(study);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Study Security");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class SaveStudyPermissionsAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            HttpServletRequest request = getViewContext().getRequest();
            Group[] groups = SecurityManager.getGroups(study.getContainer().getProject(), true);
            HashSet<Integer> set = new HashSet<Integer>(groups.length*2);
            for (Group g : groups)
                set.add(g.getUserId());

            ACL acl = aclFromPost(request, set);
            acl.setPermission(Group.groupAdministrators, ACL.PERM_READ);

            study.updateACL(acl);
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            String redirect = (String)getViewContext().get("redirect");
            if (redirect != null)
                return new ActionURL(redirect);

            return new ActionURL("Study-Security", "begin", getContainer());
        }

        private ACL aclFromPost(HttpServletRequest request, HashSet<Integer> set)
        {
            ACL acl = new ACL();
            Enumeration i = request.getParameterNames();
            while (i.hasMoreElements())
            {
                String name = (String)i.nextElement();
                if (!name.startsWith("group."))
                    continue;
                String s = name.substring("group.".length());
                int groupid;
                try
                {
                    groupid = Integer.parseInt(s);
                }
                catch (NumberFormatException x)
                {
                    continue;
                }
                if (!set.contains(groupid))
                    continue;
                int perm = 0;
                s = request.getParameter(name);
                if (s.equals("READ"))
                    perm = ACL.PERM_READ;
                else if (s.equals("READOWN"))
                    perm = ACL.PERM_READOWN;
                if (perm != 0)
                    acl.setPermission(groupid, perm);
            }
            return acl;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ApplyDatasetPermissionsAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            HttpServletRequest request = getViewContext().getRequest();
            Group[] groups = SecurityManager.getGroups(study.getContainer().getProject(), true);
            HashSet<Integer> set = new HashSet<Integer>(groups.length*2);
            for (Group g : groups)
                set.add(g.getUserId());

            for (DataSetDefinition dsDef : study.getDataSets())
            {
                List<String> gid = getViewContext().getList("dataset." + dsDef.getDataSetId());
                ACL acl = dsDef.getACL();

                if (gid != null || !dsDef.getACL().isEmpty())
                {
                    dsDef.updateACL(aclFromPost(gid, set));
                }
            }
            return true;
        }

        private ACL aclFromPost(List<String> groups, HashSet<Integer> studyGroups)
        {
            ACL acl = new ACL();
            if (groups == null) return acl;

            for (String id : groups)
            {
                int gid = NumberUtils.toInt(id);
                if (studyGroups.contains(gid))
                    acl.setPermission(gid, ACL.PERM_READ);
            }
            return acl;
        }

        public ActionURL getSuccessURL(Object o)
        {
            String redirect = (String)getViewContext().get("redirect");
            if (redirect != null)
                return new ActionURL(redirect);

            return new ActionURL("Study-Security", "begin", getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ReportPermissionsAction extends FormViewAction<PermissionsForm>
    {
        public ModelAndView getView(PermissionsForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("reportPermissions", HelpTopic.Area.STUDY));
            VBox v = new VBox();
            v.addView(new JspView<PermissionsForm>("/org/labkey/study/view/securityTabHeader.jsp", form));

            if (TAB_STUDY.equals(form.getTabId()))
            {
                Study study = StudyManager.getInstance().getStudy(getContainer());
                if (null == study)
                    return HttpView.redirect(new ActionURL("Study", "begin", getContainer()));

                v.addView(new Overview(study, getViewContext().getActionURL()));
            }
            else
            {
                Report report = null;
                if (form.getReportId() != -1)
                    report = ReportManager.get().getReport(getContainer(), form.getReportId());

                if (null == report)
                {
                    HttpView.throwNotFound();
                    return null;
                }
                v.addView(new JspView<Report>("/org/labkey/study/view/reportPermission.jsp", report));
            }
            v.addView(new HttpView() {
                protected void renderInternal(Object model, PrintWriter out) throws Exception {
                    out.write("</td></tr></table>");
                }
            });
            return v;
        }

        public void validateCommand(PermissionsForm target, Errors errors)
        {
        }

        public boolean handlePost(PermissionsForm form, BindException errors) throws Exception
        {
            Report report = null;
            if (form.getReportId() != -1)
                report = ReportManager.get().getReport(getContainer(), form.getReportId());

            if (null == report)
            {
                HttpView.throwNotFound();
                return false;
            }

            ACL acl = report.getDescriptor().getACL();
            Integer owner = null;

            if (form.getPermissionType().equals(PermissionType.privatePermission.toString()))
            {
                acl = new ACL(true);
                owner = getUser().getUserId();
            }
            else if (form.getPermissionType().equals(PermissionType.defaultPermission.toString()))
            {
                acl = new ACL(true);    // empty ACL
            }
            else
            {
                // modify one at a time
                if (form.getRemove() != 0 || form.getAdd() != 0)
                {
                    if (form.getRemove() != 0)
                        acl.setPermission(form.getRemove(), 0);
                    if (form.getAdd() != 0)
                        acl.setPermission(form.getAdd(), ACL.PERM_READ);
                }
                // set all at once
                else
                {
                    acl = new ACL(false);
                    if (form.getGroups() != null)
                        for (int gid : form.getGroups())
                            acl.setPermission(gid, ACL.PERM_READ);
                }
            }
            report.getDescriptor().updateACL(getViewContext(), acl);
            report.getDescriptor().setOwner(owner);
            ReportService.get().saveReport(getViewContext(), report.getDescriptor().getReportKey(), report);
            return true;
        }

        public ActionURL getSuccessURL(PermissionsForm saveReportForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try {
                Study study = StudyManager.getInstance().getStudy(getContainer());
                ActionURL url = getViewContext().getActionURL();
                root.addChild(study.getLabel(), url.relativeUrl("overview", null, "Study"));

                if (getUser().isAdministrator())
                    root.addChild("Manage Reports and Views", ActionURL.toPathString("Study-Reports", "manageReports.view", getContainer()));
            }
            catch (Exception e)
            {
                return root.addChild("Report and View Permissions");
            }
            return root.addChild("Report and View Permissions");
        }
    }

    private static class StudySecurityForm extends ViewForm
    {
        private boolean _studySecurity;

        public boolean isStudySecurity()
        {
            return _studySecurity;
        }

        public void setStudySecurity(boolean studySecurity)
        {
            _studySecurity = studySecurity;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class StudySecurityAction extends FormHandlerAction<StudySecurityForm>
    {
        public void validateCommand(StudySecurityForm target, Errors errors)
        {
        }

        public boolean handlePost(StudySecurityForm form, BindException errors) throws Exception
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null)
            {
                Study updated = study.createMutable();
                updated.setStudySecurity(form.isStudySecurity());
                StudyManager.getInstance().updateStudy(getUser(), updated);
            }
            return true;
        }

        public ActionURL getSuccessURL(StudySecurityForm studySecurityForm)
        {
            String redirect = (String)getViewContext().get("redirect");
            if (redirect != null)
                return new ActionURL(redirect);

            return new ActionURL("Study-Security", "begin", getContainer());
        }
    }

    public enum PermissionType
    {
        defaultPermission,
        explicitPermission,
        privatePermission,
    }

    public static final String TAB_REPORT = "tabReport";
    public static final String TAB_STUDY = "tabStudy";

    public static class PermissionsForm extends FormData
    {
        private int reportId = -1;
        private Integer remove = 0;
        private Integer add = 0;
        private Set<Integer> groups = null;
        private String _permissionType;
        private String _tabId;

        /* use group (multi values) to set acl all at once */

        public void reset(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            super.reset(actionMapping, httpServletRequest);

            String[] groupParams = httpServletRequest.getParameterValues("group");
            if (null == groupParams || groupParams.length == 0)
                return;

            groups = new TreeSet<Integer>();
            for (String groupParam : groupParams)
            {
                //noinspection EmptyCatchBlock
                try
                {
                    groups.add(Integer.parseInt(groupParam));
                }
                catch (NumberFormatException x)
                {
                }
            }
        }

        public Set<Integer> getGroups()
        {
            return groups;
        }

        public int getReportId()
        {
            return reportId;
        }

        public void setReportId(int reportId)
        {
            this.reportId = reportId;
        }

        /* add and remove can be used to add/remove single principal */

        public int getRemove()
        {
            return remove == null ? 0 : remove;
        }

        public void setRemove(Integer remove)
        {
            this.remove = remove;
        }

        public int getAdd()
        {
            return add == null ? 0 : remove;
        }

        public void setAdd(Integer add)
        {
            this.add = add;
        }
        public void setPermissionType(String type){_permissionType = type;}
        public String getPermissionType(){return _permissionType;}

        public void setTabId(String id){_tabId = id;}
        public String getTabId(){return _tabId;}
    }

    static class Overview extends WebPartView
    {
        HttpView impl;

        Overview(Study study)
        {
            this(study, null);
        }

        Overview(Study study, ActionURL redirect)
        {
            JspView<Study> studySecurityView = new JspView<Study>("/org/labkey/study/security/studySecurity.jsp", study);
            JspView<Study> studyView = new JspView<Study>("/org/labkey/study/security/study.jsp", study);
            studyView.setTitle("Study Security");
            if (redirect != null)
                studyView.addObject("redirect", redirect.getLocalURIString());
            JspView<Study> dsView = new JspView<Study>("/org/labkey/study/security/datasets.jsp", study);
            dsView.setTitle("Per Dataset Permissions");
            if (redirect != null)
                dsView.addObject("redirect", redirect.getLocalURIString());
            JspView<Study> siteView = new JspView<Study>("/org/labkey/study/security/sites.jsp", study);
            siteView.setTitle("Restricted Dataset Permissions (per Site)");

            VBox v = new VBox();
            v.addView(studySecurityView);
            if (study.isStudySecurity())
            {
                v.addView(studyView);
                v.addView(dsView);
            }
            impl = v;
        }


        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            include(impl, out);
        }
    }


    public static class StudySecurityViewFactory implements SecurityManager.ViewFactory
    {
        public HttpView createView(ViewContext context)
        {
            if (StudyManager.getInstance().getStudy(context.getContainer()) != null)
                return new StudySecurityPermissionsView();
            else
                return null;
        }
    }


    private static class StudySecurityPermissionsView extends WebPartView
    {
        private StudySecurityPermissionsView()
        {
            setTitle("Study Security");
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            ActionURL urlStudy = new ActionURL("Study-Security", "begin", getViewContext().getContainer());
            out.print("<br>Click here to manage permissions for the Study module.<br>");
            out.print(PageFlowUtil.buttonLink("Study Security", urlStudy));
        }
    }
}
