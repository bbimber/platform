/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.pipeline.cmd;

import java.util.Collections;
import java.util.List;

/**
 * <code>ValueToSwitch</code> returns the switch name regardless of what the value is, as long as it is not null
 * most likely used in conjuction with another cmd type (ex. ValueToSwitch and ValueToMultiCommandArgs) 
*/
public class ValueToSwitch extends AbstractValueToNamedSwitch
{
    public List<String> toArgs(String value)
    {
        if (value != null && value.length() > 0)
            return getSwitchFormat().format(getSwitchName());

        return Collections.emptyList();
    }
}
