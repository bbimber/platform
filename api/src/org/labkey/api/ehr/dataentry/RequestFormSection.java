/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: bimber
 * Date: 6/9/13
 * Time: 4:15 PM
 */
public class RequestFormSection extends SimpleFormSection
{
    public RequestFormSection()
    {
        super("ehr", "requests", "Request", "ehr-formpanel");
        setConfigSources(Collections.singletonList("Task"));
        setTemplateMode(TEMPLATE_MODE.NONE);
    }

    @Override
    public JSONObject toJSON(DataEntryFormContext ctx)
    {
        JSONObject ret = super.toJSON(ctx);

        Map<String, Object> formConfig = new HashMap<String, Object>();
        Map<String, Object> bindConfig = new HashMap<String, Object>();
        bindConfig.put("createRecordOnLoad", true);
        formConfig.put("bindConfig", bindConfig);
        ret.put("formConfig", formConfig);

        return ret;
    }
}
