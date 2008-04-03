/*
 * Copyright (c) 2005 LabKey Software, LLC
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
package org.labkey.pipeline.xstream;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.SingleValueConverter;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * URIXStreamConveter class
 * URI marshalling handler for XML.<p/>
 * Created: Oct 4, 2007
 *
 * @author bmaclean
 */
public class URIXStreamConverter implements SingleValueConverter
{
    public boolean canConvert(Class type)
    {
        return type.equals(URI.class);
    }

    /**
     * Load local URI for UMO from URI file spec on LabKey Server.
     * @param str remote URI string
     * @return
     */
    public Object fromString(String str)
    {
        try
        {
            return new URI(PathMapper.getInstance().remoteToLocal(str));
        }
        catch (URISyntaxException e)
        {
            throw new ConversionException(e);
        }
    }

    /**
     * Marshal URI from UMO on local file system to URI file spec on LabKey Server.
     * @param obj local URI
     * @return
     */
    public String toString(Object obj)
    {
        return PathMapper.getInstance().localToRemote(obj == null ? null : obj.toString());
    }
}
