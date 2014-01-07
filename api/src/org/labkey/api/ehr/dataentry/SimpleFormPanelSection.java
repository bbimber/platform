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

import java.util.HashMap;
import java.util.Map;

/**
 * User: bimber
 * Date: 11/15/13
 * Time: 12:49 PM
 */
public class SimpleFormPanelSection extends SimpleFormSection
{
    private boolean _createRecordOnLoad;

    public SimpleFormPanelSection(String schemaName, String queryName, String label)
    {
        this(schemaName, queryName, label, true);
    }

    public SimpleFormPanelSection(String schemaName, String queryName, String label, boolean createRecordOnLoad)
    {
        super(schemaName, queryName, label, "ehr-formpanel");
        _createRecordOnLoad = createRecordOnLoad;
    }

    @Override
    public JSONObject toJSON(DataEntryFormContext ctx)
    {
        JSONObject ret = super.toJSON(ctx);

        Map<String, Object> formConfig = new HashMap<>();
        Map<String, Object> bindConfig = new HashMap<>();
        bindConfig.put("createRecordOnLoad", _createRecordOnLoad);
        formConfig.put("bindConfig", bindConfig);
        ret.put("formConfig", formConfig);

        return ret;
    }
}
