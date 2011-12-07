/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * User: jeckels
 * Date: Aug 29, 2008
 */
public class RequeueLostJobsRequest implements StatusRequest
{
    private static final Logger _log = Logger.getLogger(RequeueLostJobsRequest.class);

    private Collection<String> _locations;
    private Collection<String> _jobIds;

    public RequeueLostJobsRequest(Collection<String> locations, Collection<String> jobIds)
    {
        _locations = locations;
        _jobIds = jobIds;
    }

    public void performRequest()
    {
        if (PipelineService.get().getPipelineQueue().isLocal())
        {
            _log.error("Attempted to requeue lost jobs for location " + Arrays.asList(_locations) + " but this server " +
                "is not using an external JMS queue. Change your configuration to point to a different JMS queue.");
        }

        // Do this in a separate thread because Mule doesn't deal with queuing different events while processing an
        // event
        new Thread()
        {
            public void run()
            {
                for (String location : _locations)
                {
                    _log.info("Requeueing jobs for location " + location);
                    for (PipelineStatusFileImpl sf : PipelineStatusManager.getStatusFilesForLocation(location, true))
                    {
                        if (!_jobIds.contains(sf.getJobId()) && sf.getJobStore() != null)
                        {
                            try
                            {
                                PipelineJobService.get().getJobStore().retry(sf);
                            }
                            catch (IOException e)
                            {
                                PipelineJobService.get().getJobStore().fromXML(sf.getJobStore()).error("Failed to requeue job", e);
                            }
                        }
                    }
                }
            }
        }.start();
    }
}