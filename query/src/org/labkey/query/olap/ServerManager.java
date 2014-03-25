/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.query.olap;

import com.drew.lang.annotations.NotNull;
import mondrian.olap.MondrianServer;
import mondrian.rolap.RolapConnection;
import mondrian.rolap.RolapConnectionProperties;
import mondrian.rolap.RolapHierarchy;
import mondrian.rolap.agg.AggregationKey;
import mondrian.server.RepositoryContentFinder;
import mondrian.server.StringRepositoryContentFinder;
import mondrian.spi.CatalogLocator;
import mondrian.spi.DataSourceChangeListener;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
//import org.labkey.api.concurrent.CountingSemaphore;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.security.User;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemTrackerListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.olap4j.OlapConnection;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: matthew
 * Date: 10/31/13
 * Time: 8:12 AM

 [ ] try to have exactly one MondrianServer (start with one per container)
 [ ] figure out cube/catalog reload, or do we have to blow away the server(s)?
 [ ] investigate shutdown, ref counting?, mondrian 3.5
 [ ] what is change listener for?

*/

public class ServerManager
{
    private static final Logger _log = Logger.getLogger(ServerManager.class);

    private static final Map<String, ServerReferenceCount> _servers = new HashMap<>();
    private static final Object _serverLock = new Object();

    public static final ModuleResourceCache<OlapSchemaDescriptor> SCHEMA_DESCRIPTOR_CACHE = ModuleResourceCaches.create(new Path(OlapSchemaCacheHandler.DIR_NAME), "Olap cube defintions", new OlapSchemaCacheHandler());

    private static final String DATA_SOURCE_NAME = "dsn_LABKEY";

    static
    {
        ContainerManager.addContainerListener(new ContainerManager.ContainerListener()
        {
            @Override
            public void containerCreated(Container c, User user)
            {

            }

            @Override
            public void containerDeleted(Container c, User user)
            {
                cubeDataChanged(c);
            }

            @Override
            public void containerMoved(Container c, Container oldParent, User user)
            {

            }

            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {

            }
        });
    }


    static String getServerCacheKey(Container c)
    {        return MondrianServer.class.getName() + "/" + c.getId();
    }


    @Nullable
    public static OlapSchemaDescriptor getDescriptor(@NotNull Container c, @NotNull String schemaId)
    {
        OlapSchemaDescriptor d = SCHEMA_DESCRIPTOR_CACHE.getResource(schemaId);
        if (null != d && c.getActiveModules().contains(d.getModule()))
            return d;
        return null;
    }

    /*
     * Start with one MondrianServer per container.  We'd like to get down to one MondrianServer.
     *
     * Need to detect changes to available catalogs in this container to close current server
     *
     * TODO: Investigate getting down to one MondrianServer instance
     */

    private static ServerReferenceCount getServer(Container c, User user) throws SQLException
    {
        synchronized (_serverLock)
        {
            ServerReferenceCount ref = _servers.get(getServerCacheKey(c));
            MondrianServer s = null != ref ? ref.get() : null;
            if (null == s)
            {
                Collection<OlapSchemaDescriptor> descriptors = SCHEMA_DESCRIPTOR_CACHE.getResources(c);

                StringBuilder sb = new StringBuilder();
                sb.append(
                        "<?xml version=\"1.0\"?>\n" +
                        "<DataSources>\n" +
                        "<DataSource>\n" +
                        "<DataSourceName>" + DATA_SOURCE_NAME + "</DataSourceName>\n" +
                        "<DataSourceDescription>" + PageFlowUtil.filter(c.getPath()) + "</DataSourceDescription>\n" +
                        "<URL></URL>\n" +
                        "<DataSourceInfo>" +
                        RolapConnectionProperties.Provider.name() + "=Mondrian;" +
                        RolapConnectionProperties.Jdbc.name() + "=" + getDatabaseConnectionString(c, user) +
//                        ";" + RolapConnectionProperties.DataSourceChangeListener.name() + "=" + _DataSourceChangeListener.class.getName() +
                        "</DataSourceInfo>\n" +
                        "<ProviderName>Mondrian</ProviderName>\n" +
                        "<ProviderType>MDP</ProviderType>\n" +
                        "<AuthenticationMode>Unauthenticated</AuthenticationMode>\n" +
                        "<Catalogs>\n");
                for (OlapSchemaDescriptor d : descriptors)
                {
                    sb.append(
                            "\n" +
                            "  <Catalog name=\"" + OlapSchemaDescriptor.makeCatalogName(d, c) + "\">\n" +
                            "  <Definition>" + d.getId() + "</Definition>\n" +
                            "  </Catalog>\n");
                }
                sb.append(
                        "\n</Catalogs>\n" +
                        "</DataSource>\n" +
                        "</DataSources>");
                _log.debug(sb.toString());
                RepositoryContentFinder rcf = new StringRepositoryContentFinder(sb.toString());
                s = MondrianServer.createWithRepository(rcf, new _CatalogLocator());
                _log.debug("Create new Mondrian server: " + c.getPath() + " " + s.toString());
                MemTracker.getInstance().put(s);
                ref = new ServerReferenceCount(s, c);
                _servers.put(getServerCacheKey(c), ref);
            }
            return ref;
        }
    }


    public static OlapConnection getConnection(Container c, User u, String catalog) throws SQLException
    {
        ServerReferenceCount ref = ServerManager.getServer(c, u);
        if (null == ref || null == ref.get())
            return null;

        MondrianServer server = ref.get();
        OlapConnection olap = server.getConnection(DATA_SOURCE_NAME, catalog, null);
        MemTracker.getInstance().put(olap);
        OlapConnection wrap = OlapConnectionProxy.wrap(olap, ref);
        MemTracker.getInstance().put(wrap);
        return wrap;
    }



    /*
     * This is to support XmlaServlet, don't hang on to this for more than one request.  If you need to hold on
     * longer we need a different interface
     */
    public static MondrianServer getMondrianServer(Container c, User u) throws SQLException
    {
        ServerReferenceCount ref = ServerManager.getServer(c, u);
        if (null == ref || null == ref.get())
            return null;

        MondrianServer server = ref.get();
        MondrianServer wrap = MondrianServerProxy.wrap(server, ref);
        MemTracker.getInstance().put(wrap);
        return wrap;
    }


    public static void olapSchemaDescriptorChanged(OlapSchemaDescriptor d)
    {
        synchronized (_serverLock)
        {
            for (ServerReferenceCount ref : _servers.values())
                ref.decrement();
            _servers.clear();
            BitSetQueryImpl.invalidateCache(d);
        }
    }


    public static void cubeDataChanged(Container c)
    {
        synchronized (_serverLock)
        {
            ServerReferenceCount ref = _servers.remove(getServerCacheKey(c));
            if (null != ref)
                ref.decrement();
            BitSetQueryImpl.invalidateCache(c);
        }
    }


    static void closeServer(MondrianServer s, @NotNull Container container)
    {
        _log.debug("Shutdown Mondrian server: " + s.toString());

        try
        {
            Collection<OlapSchemaDescriptor> descriptors = SCHEMA_DESCRIPTOR_CACHE.getResources(container);
            for (OlapSchemaDescriptor d : descriptors)
            {
                String catalogName = OlapSchemaDescriptor.makeCatalogName(d, container);
                OlapConnection c = s.getConnection(DATA_SOURCE_NAME, catalogName, null);
                RolapConnection r = c.unwrap(RolapConnection.class);
                r.getCacheControl(null).flushSchemaCache();
            }
        }
        catch (Exception x)
        {
            _log.debug("Shutdown Mondrian server flush cache failed: " + s.toString());
            _log.debug(x.getMessage());
        }

        s.shutdown();
        BitSetQueryImpl.invalidateCache(container);
    }


    @SuppressWarnings("UnusedParameters")
    static String getDatabaseConnectionString(Container c, User user)
    {
        //Currently all internal connections must be done with a limited service user
        return "jdbc:labkey:query:" + getDatabaseName(c) + ":container=" + c.getRowId() + ":schema=core";
    }


    static String getDatabaseName(Container c)
    {
        return "dn_" + c.getRowId();
    }


    static Map<_DataSourceChangeListener, Boolean> changeListeners = Collections.synchronizedMap(new WeakHashMap<_DataSourceChangeListener, Boolean>());

    public static class _DataSourceChangeListener implements DataSourceChangeListener
    {
        final Map<String,Boolean> ischanged = Collections.synchronizedMap(new HashMap<String, Boolean>());

        public _DataSourceChangeListener()
        {
            changeListeners.put(this,Boolean.TRUE);
        }

        @Override
        public boolean isHierarchyChanged(RolapHierarchy hierarchy)
        {
            Boolean waschanged = ischanged.put(hierarchy.getUniqueName(),Boolean.FALSE);
            return waschanged != Boolean.FALSE;
        }

        @Override
        public boolean isAggregationChanged(AggregationKey aggregation)
        {
            Boolean waschanged = ischanged.put(aggregation.toString(),Boolean.FALSE);
            return waschanged != Boolean.FALSE;
        }
    }


    private static class _CatalogLocator implements CatalogLocator
    {
        public String locate(String catalogPath)
        {
            try
            {
                // Need a way to get a URL or something to the resource
                OlapSchemaDescriptor d = SCHEMA_DESCRIPTOR_CACHE.getResource(catalogPath);
                if (null == d)
                    throw new IOException("catalog not found: " + catalogPath);
                File f = d.getFile();
                return f.getAbsolutePath();
            }
            catch (IOException x)
            {
                throw new UnexpectedException(x);
            }
        }
    }



    static abstract class ReferenceCount
    {
        AtomicInteger counter = new AtomicInteger(1);

        void increment()
        {
            counter.incrementAndGet();
        }

        void decrement()
        {
            int c = counter.decrementAndGet();
            if (0 == c)
                close();
        }

        abstract void close();
    }

    static class ServerReferenceCount extends ReferenceCount
    {
        MondrianServer _server;
        Container _container;

        ServerReferenceCount(@NotNull MondrianServer s, @NotNull Container c)
        {
            _server = s;
            _container = c;
        }

        MondrianServer get()
        {
            return (counter.get() > 0) ? _server : null;
        }

        @Override
        void increment()
        {
            super.increment();
            _log.debug("increment reference: " + counter.get() + " " + _server.toString());
        }

        @Override
        void decrement()
        {
            _log.debug("decrement reference: " + (counter.get()-1) + " " + _server.toString());
            super.decrement();
        }

        @Override
        void close()
        {
            closeServer(_server, _container);
            _server = null;
        }
    }


    public static class OlapConnectionProxy implements InvocationHandler
    {
        final OlapConnection _inner;
        final ReferenceCount _count;

        static OlapConnection wrap(OlapConnection conn, ReferenceCount ref)
        {
            OlapConnection wrapper = (OlapConnection) Proxy.newProxyInstance(
                    conn.getClass().getClassLoader(),
                    new Class[] {OlapConnection.class},
                    new OlapConnectionProxy(conn, ref));
            return wrapper;
        }

        OlapConnectionProxy(OlapConnection c, ReferenceCount ref)
        {
            _inner = c;
            _count = ref;
            _count.increment();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            try
            {
                return method.invoke(_inner,args);
            }
            finally
            {
                if ("close".equals(method.getName()))
                    _count.decrement();
            }
        }
    }


    public static class MondrianServerProxy implements InvocationHandler
    {
        final MondrianServer _inner;
        final ReferenceCount _count;
//        final CountingSemaphore _semaphore = new CountingSemaphore(8, true);

        static MondrianServer wrap(MondrianServer conn, ReferenceCount ref)
        {
            MondrianServer wrapper = (MondrianServer) Proxy.newProxyInstance(
                    conn.getClass().getClassLoader(),
                    new Class[] {MondrianServer.class},
                    new MondrianServerProxy(conn, ref));
            return wrapper;
        }

        MondrianServerProxy(MondrianServer c, ReferenceCount ref)
        {
            _inner = c;
            _count = ref;
            _count.increment();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            if ("shutdown".equals(method.getName()))
            {
                _count.decrement();
                return null;
//            }
//            else if ("executeOlapQuery".equals(method.getName()))
//            {
//                try (AutoCloseable permit = _semaphore.acquire())
//                {
//                    return method.invoke(_inner,args);
//                }
            }
            else
                return method.invoke(_inner,args);
        }
    }

    static
    {
        MemTracker.getInstance().register(new _MemTrackerListener());
    }

    public static class _MemTrackerListener implements MemTrackerListener
    {
        @Override
        public void beforeReport(Set<Object> set)
        {
            synchronized (_serverLock)
            {
                for (ServerReferenceCount ref : _servers.values())
                {
                    MondrianServer s = ref.get();
                    if (null != s)
                        set.add(s);
                }
            }
        }
    }
}