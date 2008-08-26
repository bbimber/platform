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
package org.labkey.pipeline.mule;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;

import java.net.URLEncoder;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.IOException;

/**
 * <code>HttpCallbackPipelineStatusWriter</code>
 *
 * @author brendanx
 */
public class HttpCallbackPipelineStatusWriter implements PipelineStatusFile.StatusWriter
{
    private static Logger _log = Logger.getLogger(HttpCallbackPipelineStatusWriter.class);

    public void setStatusFile(PipelineJob job, String status, String statusInfo)
    {
        _log.info("STATUS = " + status);
        PipelineJobService.RemoteServerProperties remoteProps = PipelineJobService.get().getRemoteServerProperties();
        PipelineJobService.ApplicationProperties appProps = PipelineJobService.get().getAppProperties();

        if (remoteProps != null && remoteProps.getBaseServerUrl() != null)
        {
            try
            {
                String baseServerURL = remoteProps.getBaseServerUrl();
                String urlString = baseServerURL + "Pipeline-Status/" + job.getContainerId() + "/setJobStatus.view?job=" + job.getJobGUID();
                if (status != null)
                {
                    urlString += "&status=" + URLEncoder.encode(status, "UTF-8");
                }
                if (statusInfo != null)
                {
                    urlString += "&statusInfo=" + URLEncoder.encode(statusInfo, "UTF-8");
                }
                if (appProps.getCallbackPassword() != null)
                {
                    urlString += "&callbackPassword=" + URLEncoder.encode(appProps.getCallbackPassword(), "UTF-8");
                }
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                {
                    job.getLogger().info("Got response code " + connection.getResponseCode() + " from server when trying to set status to " + status);
                }
            }
            catch (IOException e)
            {
                job.getLogger().info("Failed to submit status to " + status, e);
            }
        }
    }

    public void ensureError(PipelineJob job) throws Exception
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }
}
