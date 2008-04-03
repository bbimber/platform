package org.labkey.study.assay.query;

import org.labkey.api.query.QueryView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryPicker;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;
import org.labkey.api.security.ACL;

import java.util.List;
import java.util.Collections;

/**
 * User: brittp
 * Date: Jun 28, 2007
 * Time: 5:07:02 PM
 */
public class AssayListQueryView extends QueryView
{
    public AssayListQueryView(ViewContext context, QuerySettings settings)
    {
        super(new AssaySchema(context.getUser(), context.getContainer()), settings);
        setShowExportButtons(false);
        setShowCustomizeViewLinkInButtonBar(true);
        setShowDetailsColumn(false);
        setShowRecordSelectors(false);
        setShadeAlternatingRows(true);
        setShowColumnSeparators(true);
    }

    protected List<QueryPicker> getQueryPickers()
    {
        return Collections.emptyList();
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        ActionURL insertURL = new ActionURL("assay", "chooseAssayType.view", view.getViewContext().getContainer());
        ActionButton insert = new ActionButton("New Assay Design", insertURL);
        insert.setActionType(ActionButton.Action.LINK);
        insert.setDisplayPermission(ACL.PERM_INSERT);
        bar.add(insert);

        Container project = getContainer().getProject();
        if (!project.equals(getContainer()) && project.hasPermission(getUser(), ACL.PERM_INSERT))
        {
            ActionURL manageProjectAssays = new ActionURL("assay", "begin.view", project);
            ActionButton sharedButton = new ActionButton("Manage Project Assays", manageProjectAssays);
            sharedButton.setActionType(ActionButton.Action.LINK);
            bar.add(sharedButton);
        }
    }
}
