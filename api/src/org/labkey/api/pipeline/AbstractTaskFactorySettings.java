/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.pipeline;

/**
 * <code>AbstractTaskFactorySettings</code> is used for Spring configuration of a
 * <code>TaskFactory</code> in the <code>TaskRegistry</code>.  Extend this
 * class, and override <code>TaskFactory.cloneAndConfigure()</code> to create
 * specific types of <code>TaskPipeline</code> objects that can be configured
 * with Spring beans.
 *
 * @author brendanx
 */
abstract public class AbstractTaskFactorySettings implements TaskFactorySettings
{
    private TaskId _id;
    private TaskId _dependencyId;
    private Boolean _join;
    private String _location;
    private int _autoRetry = -1;
    private String _groupParameterName;

    public AbstractTaskFactorySettings(TaskId id)
    {
        _id = id;
    }

    /**
     * Convenience constructor for Spring XML configuration.
     *
     * @param namespaceClass namespace class for TaskId
     */
    public AbstractTaskFactorySettings(Class namespaceClass)
    {
        this(namespaceClass, null);
    }

    /**
     * Convenience constructor for Spring XML configuration.
     *
     * @param namespaceClass namespace class for TaskId
     * @param name name for TaskId
     */
    public AbstractTaskFactorySettings(Class namespaceClass, String name)
    {
        this(new TaskId(namespaceClass, name));
    }

    public TaskId getId()
    {
        return _id;
    }

    public TaskId getImplId()
    {
        return new TaskId(TaskPipeline.class);
    }

    public TaskId getDependencyId()
    {
        return _dependencyId;
    }

    public void setDependencyId(TaskId dependencyId)
    {
        _dependencyId = dependencyId;
    }

    public boolean isJoinSet()
    {
        return _join != null;
    }

    public boolean isJoin()
    {
        return isJoinSet() && _join.booleanValue();
    }

    public void setGroupParameterName(String name)
    {
        _groupParameterName = name;
    }

    public String getGroupParameterName()
    {
        return _groupParameterName;
    }

    public void setJoin(boolean join)
    {
        _join = join;
    }

    public String getLocation()
    {
        return _location;
    }

    public void setLocation(String location)
    {
        _location = location;
    }

    public boolean isAutoRetrySet()
    {
        return _autoRetry != -1;
    }

    public int getAutoRetry()
    {
        return _autoRetry;
    }

    public void setAutoRetry(int autoRetry)
    {
        _autoRetry = autoRetry;
    }
}