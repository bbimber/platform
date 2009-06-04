/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.security;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

/**
 * Created by IntelliJ IDEA.
 * User: arauch
 * Date: Jul 5, 2005
 * Time: 10:34:01 AM
 */
public class Group extends UserPrincipal
{
    public static final int groupAdministrators = -1;
    public static final int groupUsers = -2;
    public static final int groupGuests = -3;
    public static final int groupDevelopers = -4;

    private String ownerId;
    private String container;

    public Group()
    {
        super(typeProject);
    }

    public String getOwnerId()
    {
        return ownerId;
    }

    public void setOwnerId(String ownerId)
    {
        this.ownerId = ownerId;
    }

    public boolean isAdministrators()
    {
        return getUserId() == groupAdministrators;
    }

    public boolean isGuests()
    {
        return getUserId() == groupGuests;
    }

    public boolean isUsers()
    {
        return getUserId() == groupUsers;
    }

    public boolean isProjectGroup()
    {
        return getContainer() != null;
    }

    public boolean isSystemGroup()
    {
        int id = getUserId();
        return (id == groupAdministrators || id == groupGuests || id == groupUsers || id == groupDevelopers);
    }

    public String getContainer()
    {
        return container;
    }

    public void setContainer(String containerId)
    {
        this.container = containerId;
    }


    private volatile String _path = null;
    
    // @deprecated
    public String getPath()
    {
        if (null == _path)
        {
            if (null == container)
                return "/" + getName();
            Container c = ContainerManager.getForId(container);
            if (c == null)
                return "/" + container + "/" + getName();
            return "/" + c.getName() + "/" + getName();
        }
        return _path;
    }
}
