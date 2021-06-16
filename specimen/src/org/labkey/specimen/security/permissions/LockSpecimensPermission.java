/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.specimen.security.permissions;

import org.labkey.api.security.permissions.AbstractPermission;
import org.labkey.specimen.SpecimenModule;

/*
* User: Dave
* Date: May 18, 2009
* Time: 11:40:13 AM
*/
public class LockSpecimensPermission extends AbstractPermission
{
    public LockSpecimensPermission()
    {
        super("Lock Specimens",
                "Allows locking of specimens",
                SpecimenModule.class);
    }
}