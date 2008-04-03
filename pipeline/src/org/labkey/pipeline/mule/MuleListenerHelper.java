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
package org.labkey.pipeline.mule;

import org.apache.activemq.thread.Scheduler;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.log4j.Logger;
import org.labkey.api.module.SpringModule;
import org.labkey.api.util.URIUtil;
import org.mule.config.ThreadingProfile;
import org.mule.config.builders.MuleXmlBuilderContextListener;
import org.mule.providers.service.TransportFactory;

import javax.servlet.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * MuleInitializer class
 * <p/>
 * Created: Sep 28, 2007
 *
 * @author bmaclean
 */
public class MuleListenerHelper implements ServletContext
{
    private MuleXmlBuilderContextListener _muleContextListener;

    private ServletContext _parentContext = null;
    private HashMap<String,Object> _attributes = new HashMap<String,Object>();
    private HashMap<String,String> _initParameters = new HashMap<String,String>();
    private Logger _log = Logger.getLogger(this.getClass());

    public MuleListenerHelper(ServletContext parentContext, String muleConfigPaths)
    {
        _parentContext = parentContext;
        _initParameters.put(MuleXmlBuilderContextListener.INIT_PARAMETER_MULE_CONFIG, muleConfigPaths);
        _initParameters.put(MuleXmlBuilderContextListener.INIT_PARAMETER_WEBAPP_CLASSPATH,
                _parentContext.getInitParameter(SpringModule.INIT_PARAMETER_CONFIG_PATH));
        _muleContextListener = new MuleXmlBuilderContextListener();

        // HACK: Fix for MULE-2289
        final Converter conv = ConvertUtils.lookup(Integer.TYPE);
        ConvertUtils.register(new Converter() {
            private final Map POOL_EXHAUSTED_ACTIONS = new CaseInsensitiveMap()
            {
                // static initializer
                {
                    Integer value = new Integer(ThreadingProfile.WHEN_EXHAUSTED_WAIT);
                    this.put("WHEN_EXHAUSTED_WAIT", value);
                    this.put("WAIT", value);

                    value = new Integer(ThreadingProfile.WHEN_EXHAUSTED_DISCARD);
                    this.put("WHEN_EXHAUSTED_DISCARD", value);
                    this.put("DISCARD", value);

                    value = new Integer(ThreadingProfile.WHEN_EXHAUSTED_DISCARD_OLDEST);
                    this.put("WHEN_EXHAUSTED_DISCARD_OLDEST", value);
                    this.put("DISCARD_OLDEST", value);

                    value = new Integer(ThreadingProfile.WHEN_EXHAUSTED_ABORT);
                    this.put("WHEN_EXHAUSTED_ABORT", value);
                    this.put("ABORT", value);

                    value = new Integer(ThreadingProfile.WHEN_EXHAUSTED_RUN);
                    this.put("WHEN_EXHAUSTED_RUN", value);
                    this.put("RUN", value);
                }
            };

            public Object convert(Class clazz, Object obj)
            {
                // MULE-2289: Threading-profile tag always sets poolExhaustedAction to WAIT.
                //            String value for poolExhaustedAction will always pass through
                //            this conversion.  Our registered converter will throw an
                //            exception in this case, so we catch it, and do what Mule
                //            intends but fails to do.
                Integer val = (Integer) POOL_EXHAUSTED_ACTIONS.get(obj);
                if (val != null)
                {
                    _log.info("Hack for MULE-2289, converting " + obj + " to integer value " + val);
                    return val.intValue();
                }
                return conv.convert(clazz, obj);
            }
        }, Integer.TYPE);

        try
        {
            _muleContextListener.initialize(this);
        }
        catch (Exception x)
        {
            _log.error("Failed to load mule config.", x);
        }
        finally
        {
            ConvertUtils.register(conv, Integer.TYPE);
        }
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        boolean hasJms;
        try
        {
            hasJms = (TransportFactory.getConnectorByProtocol("jms") != null);
        }
        catch (IllegalStateException e)
        {
            hasJms = true;
        }

        _muleContextListener.contextDestroyed(servletContextEvent);

        // If we have JMS, we assume it is ActiveMQ for now.  Hopefully
        // this will get fixed with MULE-2496
        if (hasJms)
            Scheduler.clockDaemon.shutdown();
    }

    public ServletContext getContext(String string)
    {
        return null;
    }

    public int getMajorVersion()
    {
        return 0;
    }

    public int getMinorVersion()
    {
        return 0;
    }

    public String getMimeType(String string)
    {
        return "text/html";
    }

    public Set getResourcePaths(String string)
    {
        return _parentContext.getResourcePaths(string);
    }

    public URL getResource(String string) throws MalformedURLException
    {
        return _parentContext.getResource(string);
    }

    public InputStream getResourceAsStream(String string)
    {
        InputStream is = _parentContext.getResourceAsStream(string);
        if (is == null)
        {
            // If the path starts with the mule config root, try creating
            // a raw FileInputStream for it.
            String muleConfigPath = getInitParameter(MuleXmlBuilderContextListener.INIT_PARAMETER_WEBAPP_CLASSPATH);
            if (muleConfigPath == null)
                return null;

            File muleConfigRoot = new File(muleConfigPath);
            File muleConfigFile = new File(string);
            if (!URIUtil.isDescendent(muleConfigRoot.toURI(), muleConfigFile.toURI()))
                return null;

            try
            {
                is = new FileInputStream(muleConfigFile);
            }
            catch (FileNotFoundException e)
            {
                _log.debug("Could not find mule config override " + string);
            }
        }

        return is;
    }

    public RequestDispatcher getRequestDispatcher(String string)
    {
        return _parentContext.getRequestDispatcher(string);
    }

    public RequestDispatcher getNamedDispatcher(String string)
    {
        return _parentContext.getNamedDispatcher(string);
    }

    public Servlet getServlet(String string) throws ServletException
    {
        return _parentContext.getServlet(string);
    }

    public Enumeration getServlets()
    {
        return _parentContext.getServlets();
    }

    public Enumeration getServletNames()
    {
        return _parentContext.getServletNames();
    }

    public void log(String string)
    {
        _log.info(string);
    }

    public void log(Exception exception, String string)
    {
        _log.error(string, exception);
    }

    public void log(String string, Throwable throwable)
    {
        _log.error(string, throwable);
    }

    public String getRealPath(String string)
    {
        return _parentContext.getRealPath(string);
    }

    public String getServerInfo()
    {
        return _parentContext.getServerInfo();
    }

    public String getInitParameter(String string)
    {
        String param = _initParameters.get(string);
        if (param == null)
            param = _parentContext.getInitParameter(string);
        return param;
    }

    public Enumeration getInitParameterNames()
    {
        return null;
    }

    public Object getAttribute(String string)
    {
        return _attributes.get(string);
    }

    public Enumeration getAttributeNames()
    {
        return null;
    }

    public void setAttribute(String string, Object object)
    {
        _attributes.put(string,object);
    }

    public void removeAttribute(String string)
    {
        _attributes.remove(string);
    }

    public String getServletContextName()
    {
        return null;
    }
}
