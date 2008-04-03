/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.experiment.api;

import org.labkey.experiment.api.IdentifiableEntity;

/**
 * User: migra
 * Date: Aug 15, 2005
 * Time: 2:59:39 PM
 */
public class MaterialSource extends IdentifiableEntity
{
    private int rowId;
    private String materialLSIDPrefix;
    private String description;
    private String _idCol1;
    private String _idCol2;
    private String _idCol3;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getMaterialLSIDPrefix()
    {
        return materialLSIDPrefix;
    }

    public void setMaterialLSIDPrefix(String materialLSIDPrefix)
    {
        this.materialLSIDPrefix = materialLSIDPrefix;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getIdCol1()
    {
        return _idCol1;
    }

    public void setIdCol1(String idCol1)
    {
        _idCol1 = idCol1;
    }

    public String getIdCol2()
    {
        return _idCol2;
    }

    public void setIdCol2(String idCol2)
    {
        _idCol2 = idCol2;
    }

    public String getIdCol3()
    {
        return _idCol3;
    }

    public void setIdCol3(String idCol3)
    {
        _idCol3 = idCol3;
    }
}
