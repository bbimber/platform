/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.controllers.list;

import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.list.ListSchema;

public class ListQueryForm extends QueryForm
{
    ListDefinition _def;
    boolean _exportAsWebPage = false;

    public ListQueryForm()
    {
        super(ListSchema.NAME, null);
    }

    public ListQueryForm(int listId, ViewContext context)
    {
        this();
        setViewContext(context);
        _def = getListDef(listId);
    }

    public void setListId(int listId)
    {
        _def = getListDef(listId);
    }

    private ListDefinition getListDef(int listId)
    {
        ListDefinition listDef = ListService.get().getList(listId);

        if (null == listDef)
            throw new NotFoundException("List does not exist");

        return listDef;
    }

    protected QuerySettings createQuerySettings(UserSchema schema)
    {
        QuerySettings ret = super.createQuerySettings(schema);
        ret.setQueryName(_def.getName());
        return ret;
    }

    public ListDefinition getList()
    {
        return _def;
    }

    public boolean isExportAsWebPage()
    {
        return _exportAsWebPage;
    }

    public void setExportAsWebPage(boolean exportAsWebPage)
    {
        _exportAsWebPage = exportAsWebPage;
    }
}
