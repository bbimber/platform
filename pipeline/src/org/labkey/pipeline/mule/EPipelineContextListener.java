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
package org.labkey.pipeline.mule;

import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.StartupListener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContext;

/**
 * <code>EPipelineContextListener</code>
 *
 * @author brendanx
 */
public class EPipelineContextListener implements StartupListener, ShutdownListener
{
    private static MuleListenerHelper _muleListenerHelper;

    public void moduleStartupComplete(ServletContext servletContext)
    {
        // The Enterprise Pipeline is currently the only thing that uses Mule,
        // so don't initialize if it is not in use.
        if (PipelineService.get().isEnterprisePipeline())
        {
            _muleListenerHelper = new MuleListenerHelper(servletContext,
                    "org/labkey/pipeline/mule/config/webserverMuleConfig.xml");
        }
    }

    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        if (_muleListenerHelper != null)
            _muleListenerHelper.contextDestroyed(servletContextEvent);
    }
}
