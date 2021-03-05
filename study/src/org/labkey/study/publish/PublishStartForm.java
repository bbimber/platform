package org.labkey.study.publish;

import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.view.ActionURL;

public interface PublishStartForm extends DataRegionSelection.DataSelectionKeyForm
{
    ActionURL getReturnActionURL();

    String getContainerFilterName();
}
