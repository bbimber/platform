/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.study.view;

import org.labkey.api.admin.ImportContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.assay.AssayRunsView;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.view.*;
import org.labkey.api.util.PageFlowUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jul 19, 2007
 */
public class AssayRunsWebPartFactory extends AssayBaseWebPartFactory
{

    public AssayRunsWebPartFactory()
    {
        super("Assay Runs");
        this.addLegacyNames("Assay Details");
    }

    public String getDescription()
    {
        return "This web part displays a list of runs for a specific assay.";
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart, ExpProtocol protocol, boolean showButtons)
    {
        // XXX: filtering by batch not yet implemented
        //Integer batchId = getBatchId(webPart);

        AssayRunsView runsView = new AssayRunsView(protocol, !showButtons);
        runsView.setTitleHref(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(portalCtx.getContainer(), protocol));
        runsView.setTitle(protocol.getName() + " Runs");
        return runsView;
    }

    @Override
    public Map<String, String> serializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> serializedPropertyMap = new HashMap<String, String>(propertyMap);

        // serialize the assay name instead of Id, we'll try to resolve the protocolId on import based on the name
        if (serializedPropertyMap.containsKey("viewProtocolId"))
        {
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(Integer.parseInt(serializedPropertyMap.get("viewProtocolId")));
            if (null != protocol)
            {
                serializedPropertyMap.put("assayName", protocol.getName());
            }
            serializedPropertyMap.remove("viewProtocolId");
        }

        return serializedPropertyMap;
    }

    @Override
    public Map<String, String> deserializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> deserializedPropertyMap = new HashMap<String, String>(propertyMap);

        // try to resolve the protocolId from the assayName that was exported
        if (deserializedPropertyMap.containsKey("assayName"))
        {
            List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(ctx.getContainer()); 
            if (protocols.size() == 1)
            {
                deserializedPropertyMap.put("viewProtocolId", String.valueOf(protocols.get(0).getRowId()));
            }
            deserializedPropertyMap.remove("assayName");
        }

        return deserializedPropertyMap;
    }
}
