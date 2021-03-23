/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
package org.labkey.api.issues.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Entity;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.User;

/**
 * Created by klum on 4/5/2016.
 */
public class IssueListDef extends Entity
{
    public final static String DEFAULT_ISSUE_LIST_NAME = "issues";

    private int _rowId;
    private String _name;
    private String _label;
    private String _kind;
    private Container _domainContainer;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getKind()
    {
        return _kind;
    }

    public void setKind(String kind)
    {
        _kind = kind;
    }

    @Nullable
    public Container getDomainContainer(User user)
    {
        if (_domainContainer == null)
        {
            String id = getContainerId();
            if (id != null)
            {
                Container container = ContainerManager.getForId(id);
                if (container != null)
                {
                    Domain domain = findExistingDomain(container, user, getName(), getKind());

                    // if a domain already existing for this definition, return the domain container, else
                    // create the domain in the current container
                    if (domain != null)
                    {
                        _domainContainer = domain.getContainer();
                    }
                    else
                    {
                        _domainContainer = container;
                    }
                }
            }
        }
        return _domainContainer;
    }

    public Domain getDomain(User user)
    {
        return PropertyService.get().getDomain(getDomainContainer(user), getDomainURI(user));
    }

    public String getDomainURI(User user)
    {
        return generateDomainURI(getDomainContainer(user), user, getName(), getKind());
    }

    private static String generateDomainURI(Container c, User user, String name, String kindName)
    {
        return PropertyService
                .get()
                .getDomainKindByName(kindName)
                .generateDomainURI(IssuesSchema.getInstance().getSchemaName(), name, c, user);
    }

    public boolean isNew()
    {
        return _rowId == 0;
    }

    /**
     * Search folder, project, and shared for an existing domain
     *
     * @return null if no domain was located
     */
    @Nullable
    public static Domain findExistingDomain(Container c, User user, String name, String kind)
    {
        Domain domain;
        String uri = generateDomainURI(c, user, name, kind);
        domain = PropertyService.get().getDomain(c, uri);

        if (domain == null)
        {
            uri = generateDomainURI(c.getProject(), user, name, kind);
            domain = PropertyService.get().getDomain(c.getProject(), uri);
            if (domain == null)
            {
                uri = generateDomainURI(ContainerManager.getSharedContainer(), user, name, kind);
                domain = PropertyService.get().getDomain(ContainerManager.getSharedContainer(), uri);
            }
        }
        return domain;
    }
}
