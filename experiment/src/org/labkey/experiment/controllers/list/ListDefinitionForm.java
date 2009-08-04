/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.action.HasValidator;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ActionURLException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewForm;
import org.springframework.validation.Errors;

public class ListDefinitionForm extends ViewForm implements HasValidator
{
    protected ListDefinition _listDef;
    private String _returnUrl;
    private String[] _deletedAttachments;
    private boolean _showHistory = false;
    private Integer _listId = null;

    public void validate(Errors errors)
    {
        if (null == getListId())
            throw new NotFoundException("Missing listId parameter");

        _listDef = ListService.get().getList(getContainer(), getListId().intValue());

        if (null == _listDef)
            throw new NotFoundException("List does not exist in this container");
    }

    public ListDefinition getList()
    {
        return _listDef;
    }

    public String getReturnUrl()
    {
        return _returnUrl;
    }

    public void setReturnUrl(String returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public ActionURL getReturnActionURL()
    {
        try
        {
            return new ActionURL(_returnUrl);
        }
        catch(IllegalArgumentException e)
        {
            throw new ActionURLException(_returnUrl, "returnUrl parameter", e);
        }
        catch(NullPointerException e)
        {
            throw new ActionURLException(_returnUrl, "returnUrl parameter", e);
        }
    }

    public boolean isShowHistory()
    {
        return _showHistory;
    }

    public void setShowHistory(boolean showHistory)
    {
        _showHistory = showHistory;
    }

    public String[] getDeletedAttachments()
    {
        return _deletedAttachments;
    }

    public void setDeletedAttachments(String[] deletedAttachments)
    {
        _deletedAttachments = deletedAttachments;
    }

    public Integer getListId()
    {
        return _listId;
    }

    public void setListId(Integer listId)
    {
        _listId = listId;
    }
}
