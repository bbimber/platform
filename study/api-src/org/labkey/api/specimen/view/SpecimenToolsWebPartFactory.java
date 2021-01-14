package org.labkey.api.specimen.view;

import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.SpecimenUrls;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.study.view.StudyToolsWebPart;
import org.labkey.api.study.view.ToolsWebPartFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;

import java.util.ArrayList;
import java.util.List;

public class SpecimenToolsWebPartFactory extends ToolsWebPartFactory
{
    public static final String SPECIMEN_TOOLS_WEBPART_NAME = "Specimen Tools";

    public SpecimenToolsWebPartFactory()
    {
        super(SPECIMEN_TOOLS_WEBPART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
    }

    @Override
    protected List<StudyToolsWebPart.Item> getItems(ViewContext portalCtx)
    {
        String iconBase = portalCtx.getContextPath() + "/study/tools/";
        List<StudyToolsWebPart.Item> items = new ArrayList<>();

        ActionURL vialSearchURL = PageFlowUtil.urlProvider(SpecimenUrls.class).getShowSearchURL(portalCtx.getContainer());
        vialSearchURL.addParameter("showVials", true);
        items.add(new StudyToolsWebPart.Item("Vial Search", iconBase + "specimen_search.png", vialSearchURL));

        if (SettingsManager.get().isSpecimenRequestEnabled(portalCtx.getContainer()))
        {
            if (portalCtx.getContainer().hasPermission(portalCtx.getUser(), RequestSpecimensPermission.class))
                items.add(new StudyToolsWebPart.Item("New Request", iconBase + "specimen_request.png", PageFlowUtil.urlProvider(SpecimenUrls.class).getShowCreateSpecimenRequestURL(portalCtx.getContainer())));
        }
        items.add(new StudyToolsWebPart.Item("Specimen Reports", iconBase + "specimen_report.png", PageFlowUtil.urlProvider(SpecimenUrls.class).getAutoReportListURL(portalCtx.getContainer())));

        if (portalCtx.getContainer().hasPermission(portalCtx.getUser(), ManageStudyPermission.class))
            items.add(new StudyToolsWebPart.Item("Settings", iconBase + "settings.png", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(portalCtx.getContainer())));

        return items;
    }

    @Override
    protected String getTitle()
    {
        return SPECIMEN_TOOLS_WEBPART_NAME;
    }
}
