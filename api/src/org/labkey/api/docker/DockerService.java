/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.docker;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.Result;
import org.labkey.api.view.UnauthorizedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Moved from git, see history at https://github.com/LabKey/docker/commits/release18.1/api-src/org/labkey/api/docker/DockerService.java

/**
 * Created by matthew on 5/24/2016.
 *
 * This is a low-level wrapper for docker it's not responsible for security or mapping users to containers, etc.
 * Just starting and stopping containers.
 */
public interface DockerService
{
    String VOLUME_PREFIX = "lk_volume_";

    static DockerService get()
    {
        return ServiceRegistry.get().getService(DockerService.class);
    }

    static void setInstance(DockerService impl)
    {
        ServiceRegistry.get().registerService(DockerService.class, impl);
    }

    class DockerConfig
    {
        public final String endpoint;
        public final File certDirectory;

        public DockerConfig(String url, File cert)
        {
            this.endpoint = url;
            this.certDirectory = cert;
        }
    }

    class ImageConfig
    {
        public final String imageName;
        public final int httpPort;
        public final String mountHomeDirectory;
        public final String user;       // initial command user
        public final String appArmorProfile;

        public final Map<String,String> environment;
        public final Map<String,String> hosts;
        public final Map<String,String> readOnlyVolumes; // image->host

        public ImageConfig(String name, int port, String mount)
        {
            this(name, port, mount, Collections.emptyMap(), Collections.emptyMap(), null, null, Collections.emptyMap());
        }

        public ImageConfig(String name, int port, String mount,
                   Map<String,String> env,
                   Map<String,String> hosts,
                   @Nullable String commandUser, @Nullable String appArmor,
                   Map<String,String> roVolumes)
        {
            this.imageName = name;
            this.httpPort = port;
            this.mountHomeDirectory = mount;
            this.user = commandUser;
            this.environment = Collections.unmodifiableMap(new HashMap<>(env));
            this.hosts = Collections.unmodifiableMap(new HashMap<>(hosts));
            this.appArmorProfile = appArmor;
            this.readOnlyVolumes = Collections.unmodifiableMap(new HashMap<>(roVolumes));
        }

        @Override
        public int hashCode()
        {
            int result = imageName.hashCode();
            result = 31 * result + Integer.hashCode(httpPort);
            result = 31 * result + mountHomeDirectory.hashCode();
            result = 31 * result + this.environment.hashCode();
            return result;
        }


        /** Matches subset of image configuration,
         *
         * This should not check settings that are stashed in environment, because they won't be picked up when
         * inspecting running containers.
         * We don't know which environment variables are per-container and which are per-image
         * (NOTE: we could add the names of env vars to match as part of the canonical image config)
         */
        public boolean matches(ImageConfig ic)
        {
            return StringUtils.equals(imageName,ic.imageName) && httpPort == ic.httpPort;
            // TODO need to check that VOLUME is specified in image so it gets picked up by inspectImage
            // && StringUtils.equals(mountHomeDirectory,ic.mountHomeDirectory);
        }


        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ImageConfig ic = (ImageConfig)o;

            return (imageName.equals(ic.imageName)
                    && httpPort == ic.httpPort
                    && mountHomeDirectory.equals(ic.mountHomeDirectory)
                    && environment.equals(ic.environment));
        }

        @Override
        public String toString()
        {
            return imageName + " " + httpPort + " " + mountHomeDirectory;
        }
    }

    ImageConfigBuilder getImageConfigBuilder(String imageName);

    interface ImageConfigBuilder
    {
        ImageConfigBuilder inspectImage();

        ImageConfigBuilder setHttpPort(int port);

        ImageConfigBuilder setHomeDirectory(String dir);

        ImageConfigBuilder setUser(String user);

        ImageConfigBuilder setAppArmorProfile(String app);

        ImageConfigBuilder addEnvironment(String key, String value);

        ImageConfigBuilder addHost(String host, String ip);

        ImageConfigBuilder addReadOnlyVolume(String imagePath, String hostPath);

        ImageConfig build();
    }

    class ContainerUsage
    {
        public final String workingDirectory;
        public final boolean exposePorts;
        public final boolean mountVolumes;
        public final boolean reuseExisting;
        public final Map<InputStream, String> streamsForContainer = new LinkedHashMap<>(); // <Stream, Target location>
        public final List<String> extraSessionContext = new ArrayList<>();

        public ContainerUsage(String workingDirectory, boolean exposePorts, boolean mountVolumes, boolean reuseExisting)
        {
            this.workingDirectory = workingDirectory;
            this.exposePorts = exposePorts;
            this.mountVolumes = mountVolumes;
            this.reuseExisting = reuseExisting;
        }

        // with streamsForContainer field added, ContainerUsage is no longer immutable so need a new instance each time instead of using class variable
        public static ContainerUsage getDefaultUsage()
        {
            return new ContainerUsage(null, true, true, true);
        }
    }
    
    enum ContainerStatus
    {
        NOTSTARTED,         // e.g. does not exist, not started yet
        STARTING, RUNNING, STOPPED;

        public static ContainerStatus mapDockerNativeStatus(String status)
        {
            // Map Docker API statuses to ours
            switch (status)
            {
                case "running":
                    return RUNNING;
                case "restarting":
                    return STARTING;
                case "exited":
                    return STOPPED;
                // TODO: Created, Paused, Dead
                default:
                    return NOTSTARTED;
            }
        }
    }

    enum ContainerAction
    {
        START, STOP, DELETE
    }

    public class DockerContainer
    {
        public final String containerName;
        public final String containerId;
        private ContainerStatus status;
        public final ImageConfig imageConfig;
        public final String imageName;
        public final String home;
        public final String host;
        public final int port;
        public final String created;
        public final Map<String, String> labels;
        public final Map<String, String> environment;
        private final Integer userId;
        private final String entityId;
        private final String lkContainer;
        private final String filename;
//        private final boolean isReportContainer; //TODO refactor after branch merge
        private boolean isReportContainer;

        public synchronized void setStatus(ContainerStatus status)
        {
            this.status = status;
        }

        public ContainerStatus getStatus()
        {
            return status;
        }

        public Integer getUserId()
        {
            return userId;
        }

        public User getUser()
        {
            return null == userId ? null : UserManager.getUser(userId);
        }

        public String getEntityId()
        {
            return entityId;
        }

        public String getLkContainer()
        {
            return lkContainer;
        }

        public String getFilename()
        {
            return filename;
        }

        public boolean isReportContainer()
        {
            return isReportContainer;
        }

        public static DockerContainer makeDockerContainer(
                String name, String id, ImageConfig image, String home, String host, int port, String created,
                Map<String, String> labels,
                String[] environment)
        {
            HashMap<String,String> map = new HashMap<>();
            if (null != environment)
            {
                Arrays.stream(environment).forEach(s -> {
                    int eq = s.indexOf("=");
                    map.put(s.substring(0,eq),s.substring(eq+1));
                });
            }
            return new DockerContainer(name, id, image, home, host, port, created, labels, map);
        }

        public static DockerContainer makeDockerContainer(
                String name, String id, ImageConfig image, String home, String host, int port, String created,
                Map<String, String> labels,
                String[] environment, boolean isReportContainer)
        {
            HashMap<String,String> map = new HashMap<>();
            if (null != environment)
            {
                Arrays.stream(environment).forEach(s -> {
                    int eq = s.indexOf("=");
                    map.put(s.substring(0,eq),s.substring(eq+1));
                });
            }
            return new DockerContainer(name, id, image, home, host, port, created, labels, map, isReportContainer);
        }

        public static DockerContainer makeDockerContainer(
                String name, String id, ImageConfig image, String home, String host, int port, String created,
                Map<String, String> labels,
                Map<String,String> environment)
        {
            return new DockerContainer(name, id, image, home, host, port, created, labels, environment);
        }

        public static DockerContainer makeDockerContainer(
                String name, String id, ImageConfig image, String home, String host, int port, String created,
                Map<String, String> labels,
                Map<String,String> environment, boolean isReportContainer)
        {
            return new DockerContainer(name, id, image, home, host, port, created, labels, environment, isReportContainer);
        }

        public DockerContainer(
                String name, String id, ImageConfig image, String home, String host, int port, String created,
                Map<String, String> labels,
                Map<String,String> environment)
        {
            this.containerName = name;
            this.containerId = id;
            this.imageConfig = image;
            this.imageName = null==image ? "<unknown>" : image.imageName;
            this.home = home;
            this.host = host;
            this.port = port;
            this.created = created;
            this.labels = Collections.unmodifiableMap(new HashMap<>(labels));
            this.environment = Collections.unmodifiableMap(new HashMap<>(environment));

            Integer tmpUserId;
            try
            {
                tmpUserId = Integer.parseInt(labels.get("labkey:userid"));
            }
            catch (NumberFormatException e)
            {
                tmpUserId = null;
            }
            userId = tmpUserId;

            this.entityId = labels.get("labkey:entityid");
            this.lkContainer = labels.get("labkey:lkContainer");
            this.filename = labels.get("labkey:filename");
        }

        public DockerContainer(
                String name, String id, ImageConfig image, String home, String host, int port, String created,
                Map<String, String> labels,
                Map<String,String> environment, boolean isReportContainer)
        {
            this(name, id, image, home, host, port, created, labels, environment);
            this.isReportContainer = isReportContainer;
        }


    }

    DockerContainer getContainer(String id);

    ContainerStatus getContainerStatus(String id);

    List<DockerContainer> list();

    List<DockerContainer> list(ImageConfig image);

    List<String> listVolumes();

    DockerContainer start(ImageConfig image, String prefix, User user, Map<String, String> labels, Map<String, String> env, Map<File, String> filesForContainer, Map<InputStream, String> streamsForContainer, List<List<String>> postStartCmds, ContainerUsage usage) throws IOException;

    default DockerContainer start(ImageConfig image, String prefix, User user, Map<String, String> labels, Map<String, String> env, Map<File, String> filesForContainer, Map<InputStream, String> streamsForContainer, List<List<String>> postStartCmds, ContainerUsage usage, boolean isReportContainer) throws IOException
    {
        return null;
    }

    String readFileFromContainer(String containerId, String filepath) throws IOException;

    boolean pingContainer(String host, int port);

    boolean pingDocker();

    void stop(String containerId, Map<String, String> archivesFromContainer, boolean delete);

    void stop(DockerContainer dc);

    void stop(String containerId);

    void delete(DockerContainer dc, boolean force);

    void delete(String containerId);

    void deleteVolume(String volumeName);

    Set<String> pruneContainers(ImageConfig image, float hoursStopped);

    boolean isDockerEnabled();

    boolean isUseDockerVolumes();

    static String mnemonic_from_email(String email)
    {
        StringBuilder sb = new StringBuilder();
        int at = email.indexOf('@');
        if (at == -1) at = email.length();
        for (int i=0 ; i<4 && i<at ; i++)
        {
            char ch = email.charAt(i);
            if (ch == '@')
                break;
            if (ch <= 'z' && Character.isJavaIdentifierPart(ch))
                sb.append(ch);
        }
        if (at < email.length())
            sb.append("_");
        for (int i=at+1 ; i<at+7 && i<email.length() ; i++)
        {
            char ch = email.charAt(i);
            if (ch <= 'z' && Character.isJavaIdentifierPart(ch))
                sb.append(ch);
        }
        return sb.toString();
    }

    class Process
    {
        public final DockerContainer dockerContainer;
        public final User user;
        public long created = HeartBeat.currentTimeMillis();
        public long accessed = HeartBeat.currentTimeMillis();
        private String lastEntityId;

        public String getHost()
        {
            return dockerContainer.host;
        }

        public int getPort()
        {
            return dockerContainer.port;
        }

        public String getImageId()
        {
            return dockerContainer.imageName;
        }

        public Process(DockerContainer dockerContainer, User user)
        {
            this.dockerContainer = dockerContainer;
            this.user = user;
        }

        public String getLastEntityId()
        {
            return this.lastEntityId;
        }

        public void setLastEntityId(String entityId) //TODO update
        {
            this.lastEntityId = entityId;
        }
    }

    static File getHomeDirectory(User user)
    {
        Result r = UserManager.getHomeDirectory(user);
        if (r.isPresent())
            return (File)r.get();
        else
        {
            try
            {
                r.get();
            }
            catch (Exception e)
            {
                Logger.getLogger(DockerService.class).error(e.getMessage());
            }
            throw new UnauthorizedException("User does not have a home directory");
        }
    }


    class NoPortAvailableException extends IOException
    {
        public NoPortAvailableException()
        {
            super("All available ports are in-use");
        }
    }

}
