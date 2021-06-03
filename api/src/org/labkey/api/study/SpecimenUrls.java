/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.api.study;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.view.ActionURL;

/**
 * User: adam
 * Date: Jan 21, 2011
 */
public interface SpecimenUrls extends UrlProvider
{
    ActionURL getCommentURL(Container c, String globalUniqueId);
    ActionURL getDeleteRequestURL(Container c, String id);
    ActionURL getInsertSpecimenQueryRowURL(Container c, String schemaName, TableInfo table);
    ActionURL getManageActorsURL(Container c);
    ActionURL getManageRequestURL(Container c);
    ActionURL getManageRequestStatusURL(Container c, int requestId);
    ActionURL getRequestDetailsURL(Container c, String requestId);
    ActionURL getRequestDetailsURL(Container c, int requestId);
    ActionURL getSpecimensURL(Container c);
    ActionURL getSpecimensURL(Container c, boolean showVials);
    ActionURL getSelectedSpecimensURL(Container c, boolean showVials);
    ActionURL getSubmitRequestURL(Container c, String id);
    ActionURL getUpdateSpecimenQueryRowURL(Container c, String schemaName, TableInfo table);
}
