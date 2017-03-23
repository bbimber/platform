package org.labkey.core.view.template.bootstrap;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class BootstrapTemplate extends HomeTemplate
{
    public BootstrapTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/BootstrapTemplate.jsp", context, context.getContainer(), body, page);

        setView("bodyTemplate", getBodyTemplate(page));
    }

    @Override
    protected HttpView getAppBarView(ViewContext context, PageConfig page, AppBar model)
    {
        return null;
    }

    protected HttpView getBodyTemplate(PageConfig page)
    {
        HttpView view = new JspView<>("/org/labkey/core/view/template/bootstrap/body.jsp", page);
        view.setBody(getBody());
        return view;
    }

    @Override
    protected HttpView getHeaderView(PageConfig page)
    {
        String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
        Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
        return new BootstrapHeader(upgradeMessage, moduleFailures, page);
    }

    @Override
    protected HttpView getNavigationView(ViewContext context, PageConfig page, AppBar appBar)
    {
        NavigationModel model = new NavigationModel(context, page, appBar);
        addClientDependencies(model.getClientDependencies());

        return new JspView<>("/org/labkey/core/view/template/bootstrap/navigation.jsp", model);
    }

    public static class NavigationModel
    {
        public AppBar appBar;
        public PageConfig page;

        private LinkedHashSet<ClientDependency> _clientDependencies;
        private ViewContext _context;
        private List<Portal.WebPart> _menus;

        private static final Logger LOG = Logger.getLogger(NavigationModel.class);

        private NavigationModel(ViewContext context, PageConfig page, AppBar appBar)
        {
            this.appBar = appBar;
            this._context = context;
            this.page = page;

            this._clientDependencies = new LinkedHashSet<>();
            this._menus = initMenus();
        }

        private void addClientDependencies(LinkedHashSet<ClientDependency> dependencies)
        {
            _clientDependencies.addAll(dependencies);
        }

        public LinkedHashSet<ClientDependency> getClientDependencies()
        {
            return _clientDependencies;
        }

        public List<Portal.WebPart> getCustomMenus()
        {
            return _menus;
        }

        public String getProjectTitle()
        {
            Container c = _context.getContainer();
            Container p = c.getProject();
            String projectTitle = "";
            if (null != p)
            {
                projectTitle = p.getTitle();
                if (null != projectTitle && projectTitle.equalsIgnoreCase("home"))
                    projectTitle = "Home";
            }

            return projectTitle;
        }

        public List<NavTree> getTabs()
        {
            if (null == appBar)
                return Collections.emptyList();

            // TODO: switch getButtons() to offer a List
            return Arrays.asList(appBar.getButtons());
        }

        private List<Portal.WebPart> initMenus()
        {
            Container c = _context.getContainer();
            Container project = c.getProject();

            if (null != project)
            {
                Collection<Portal.WebPart> allParts = Portal.getParts(project, _context);
                MultiValuedMap<String, Portal.WebPart> locationMap = Portal.getPartsByLocation(allParts);
                List<Portal.WebPart> menuParts = (List<Portal.WebPart>) locationMap.get("menubar");

                if (null == menuParts)
                    menuParts = Collections.emptyList();

                for (Portal.WebPart part : menuParts)
                {
                    try
                    {
                        WebPartFactory factory = Portal.getPortalPart(part.getName());
                        if (null != factory)
                        {
                            WebPartView view = factory.getWebPartView(_context, part);
                            if (!view.isEmpty())
                            {
                                addClientDependencies(view.getClientDependencies());
                            }
                        }
                    }
                    catch (Exception x)
                    {
                        LOG.error("Failed to add client dependencies", x);
                    }
                }

                return menuParts;
            }

            return Collections.emptyList();
        }
    }
}
