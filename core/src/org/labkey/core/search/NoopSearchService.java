/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.core.search;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.Resource;
import org.labkey.api.data.Container;

import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 1:10:55 PM
 */
public class NoopSearchService implements SearchService
{
    IndexTask _dummyTask = new IndexTask()
    {
        public void addRunnable(@NotNull Runnable r, @NotNull PRIORITY pri)
        {
            NoopSearchService.this.addRunnable(r, pri);
        }

        public void addResource(@NotNull SearchCategory category, ActionURL url, PRIORITY pri)
        {
        }

        public void addResource(@NotNull String identifier, PRIORITY pri)
        {
        }

        public void addResource(@NotNull Resource r, PRIORITY pri)
        {
        }

        public void setReady()
        {
        }

        protected void checkDone()
        {
        }

        public String getDescription()
        {
            return "Dummy Search Service";
        }

        public void cancel()
        {
        }

        public boolean isCancelled()
        {
            return false;
        }

        public int getDocumentCountEstimate()
        {
            return 0;
        }

        public int getIndexedCount()
        {
            return 0;
        }

        public int getFailedCount()
        {
            return 0;
        }

        public long getStartTime()
        {
            return 0;
        }

        public long getCompleteTime()
        {
            return 0;
        }

        public void log(String message)
        {
        }

        public Reader getLog()
        {
            return null;
        }

        public void addToEstimate(int i)
        {
        }

        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return false;
        }

        public boolean isDone()
        {
            return false;
        }

        public IndexTask get() throws InterruptedException, ExecutionException
        {
            return null;
        }

        public IndexTask get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
        {
            return null;
        }

        public void onSuccess(Runnable r)
        {
        }
    };


    protected void addRunnable(@NotNull Runnable r, @NotNull PRIORITY pri)
    {
    }


    public IndexTask defaultTask()
    {
        return _dummyTask;
    }

    public IndexTask createTask(String description)
    {
        return _dummyTask;
    }

    public void addPathToCrawl(Path path, Date d)
    {
    }

    public void addParticipantIds(Collection<Pair<String,String>> ptids)
    {
    }

    public void addParticipantIds(ResultSet ptids) throws SQLException
    {
        while (ptids.next()) {};
    }

    public void deleteResource(String identifier, PRIORITY pri)
    {
    }

    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver)
    {
    }

    public List<SearchHit> search(String queryString, User user, Container root, int page) throws IOException
    {
        return Collections.emptyList();
    }

    public boolean isParticipantId(User user, String ptid)
    {
        return false;
    }

    public void clear()
    {
    }

    public List<SearchCategory> getSearchCategories()
    {
        return null;
    }

    public void addResource(@Nullable IndexTask task, @NotNull SearchCategory category, ActionURL url, PRIORITY pri)
    {
    }

    public void addSearchCategory(SearchCategory category)
    {

    }

    public List<IndexTask> getTasks()
    {
        return Collections.emptyList();
    }

    public void addTask(IndexTask task)
    {
    }

    public boolean isBusy()
    {
        return false;
    }

    public void setLastIndexedForPath(Path path, long time)
    {
    }

    public void deleteContainer(String id)
    {
    }

    public String escapeTerm(String term)
    {
        return StringUtils.trimToEmpty(term);
    }

    public void purgeQueues()
    {
    }

    public void start()
    {
    }

    public void pause()
    {
    }

    public boolean isRunning()
    {
        return false;
    }

    public IndexTask indexContainer(@Nullable IndexTask task, Container c, Date since)
    {
        return null==task?_dummyTask:task;
    }

    public IndexTask indexProject(@Nullable IndexTask task, Container project)
    {
        return null==task?_dummyTask:task;
    }

    public IndexTask indexFull()
    {
        return _dummyTask;
    }

    public void addDocumentProvider(DocumentProvider provider)
    {
    }
}
