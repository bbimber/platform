/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.list.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PkFilter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.QueryService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.SimpleDocumentResource;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ListManager implements SearchService.DocumentProvider
{
    private static final Logger LOG = Logger.getLogger(ListManager.class);
    private static ListManager INSTANCE = new ListManager();
    public static final String LIST_AUDIT_EVENT = "ListAuditEvent";

    public static ListManager get()
    {
        return INSTANCE;
    }

    public DbSchema getSchema()
    {
        return ExperimentService.get().getSchema();
    }

    public TableInfo getTinfoList()
    {
        return getSchema().getTable("list");
    }

    public ListDef[] getLists(Container container)
    {
        ListDef.Key key = new ListDef.Key(container);
        return key.select();
    }


    public ListDef getList(Container container, int id)
    {
        try
        {
            SimpleFilter filter = new PkFilter(getTinfoList(), id);
            filter.addCondition("Container", container);
            return Table.selectObject(getTinfoList(), filter, null, ListDef.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    
    public ListDef insert(User user, ListDef def) throws SQLException
    {
        ListDef ret = Table.insert(user, getTinfoList(), def);
        Container c = ContainerManager.getForId(def.getContainerId());
        if (null != c)
            enumerateDocuments(null, c, null);
        return ret;
    }


    // Note: caller must invoke indexer (can't invoke here since we may already be in a transaction)
    ListDef update(User user, ListDef def) throws SQLException
    {
        Container c = ContainerManager.getForId(def.getContainerId());
        if (null == c)
            throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);

        DbScope scope = getSchema().getScope();
        ListDef ret;

        try
        {
            scope.ensureTransaction();
            ListDef old = getList(c, def.getRowId());
            ret = Table.update(user, getTinfoList(), def, def.getRowId());
            if (!old.getName().equals(ret.getName()))
                QueryService.get().updateCustomViewsAfterRename(c, ListSchema.NAME, old.getName(), def.getName());

            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }

        return ret;
    }


    // COMBINE WITH DATASET???
    public static final SearchService.SearchCategory listCategory = new SearchService.SearchCategory("list", "List");

    public void enumerateDocuments(@Nullable SearchService.IndexTask t, final @NotNull Container c, @Nullable Date since)   // TODO: Use since?
    {
        final SearchService.IndexTask task = null==t ? ServiceRegistry.get(SearchService.class).defaultTask() : t;
        
        Runnable r = new Runnable()
        {
            public void run()
            {
                Map<String, ListDefinition> lists = ListService.get().getLists(c);

                for (ListDefinition list : lists.values())
                {
                    indexList(task, list);
                }
            }
        };

        task.addRunnable(r, SearchService.PRIORITY.group);
    }

    
    private void indexList(SearchService.IndexTask task, ListDefinition list)
    {
        String documentId = "list:" + ((ListDefinitionImpl)list).getEntityId();
        Domain domain = list.getDomain();

        // Delete from index if list has just been deleted or admin has chosen not to index it at all
        if (null == domain || (!list.getMetaDataIndex() && !list.getEntireListIndex()))
        {
            // TODO: Shouldn't be necessary... triggers should delete on delete/change
            ServiceRegistry.get(SearchService.class).deleteResource(documentId);
            return;
        }

        // First check if meta data needs to be indexed: if the setting is enabled and the definition has changed
        boolean needToIndex = (list.getMetaDataIndex() && hasDefinitionChangedSinceLastIndex(list));

        // If that didn't hold true then check for entire list data indexing: if the definition has changed or any item has been modified
        if (!needToIndex && list.getEntireListIndex())
            needToIndex = hasDefinitionChangedSinceLastIndex(list) || hasModifiedItems(list);

        if (!needToIndex)
            return;

        StringBuilder body = new StringBuilder();
        Map<String, Object> props = new HashMap<String, Object>();
        String title = list.getEntireListTitleSetting() == ListDefinition.TitleSetting.Standard ? "List " + list.getName() : list.getEntireListTitleTemplate();

        props.put(SearchService.PROPERTY.categories.toString(), listCategory.toString());
        props.put(SearchService.PROPERTY.displayTitle.toString(), title);

        if (!StringUtils.isEmpty(list.getDescription()))
            body.append(list.getDescription()).append("\n");

        String sep = "";

        if (list.getMetaDataIndex())
        {
            String comma = "";
            for (DomainProperty property : domain.getProperties())
            {
                String n = StringUtils.trimToEmpty(property.getName());
                String l = StringUtils.trimToEmpty(property.getLabel());
                if (n.equals(l))
                    l = "";
                body.append(comma).append(sep).append(StringUtilsLabKey.joinNonBlank(" ", n, l));
                comma = ",";
                sep = "\n";
            }
        }

        if (list.getEntireListIndex())
        {
            final StringBuilder data = new StringBuilder();
            final StringExpression template = createBodyTemplate(list, list.getEntireListBodySetting(), list.getEntireListBodyTemplate());
            TableInfo ti = list.getTable(User.getSearchUser());

            new TableSelector(ti).forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
            {
                @Override
                public void exec(Map<String, Object> map) throws SQLException
                {
                    data.append(template.eval(map)).append("\n");
                }
            });

            body.append(sep);
            body.append(data);
        }

        ActionURL url = list.urlShowData();
        url.setExtraPath(list.getContainer().getId()); // Use ID to guard against folder moves/renames
        final int listId = list.getListId();

        SimpleDocumentResource r = new SimpleDocumentResource(
                new Path(documentId),
                documentId,
                list.getContainer().getId(),
                "text/plain",
                body.toString().getBytes(),
                url,
                props) {
            @Override
            public void setLastIndexed(long ms, long modified)
            {
                ListManager.get().setLastIndexed(listId, ms);
            }
        };

        task.addResource(r, SearchService.PRIORITY.item);
    }


    private StringExpression createBodyTemplate(ListDefinition list, ListDefinition.BodySetting setting, @Nullable String customTemplate)
    {
        String template;

        if (setting == ListDefinition.BodySetting.Custom)
        {
            template = customTemplate;
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            TableInfo ti = list.getTable(User.getSearchUser());
            String sep = "";

            for (ColumnInfo column : ti.getColumns())
            {
                if (setting.accept(column))
                {
                    sb.append(sep);
                    sb.append("${");
                    sb.append(column.getSelectName());  // TODO: Check this... which name?
                    sb.append("}");
                    sep = " ";
                }
            }

            template = sb.toString();
        }

        return StringExpressionFactory.create(template, false);
    }


    private boolean hasDefinitionChangedSinceLastIndex(ListDefinition list)
    {
        return list.getLastIndexed() == null || list.getModified().compareTo(list.getLastIndexed()) > 0;
    }


    // Checks for existence of list items that have been modified since the entire list was last indexed
    private boolean hasModifiedItems(ListDefinition list)
    {
        // Using EXISTS query should be reasonably efficient.  This form (using case) seems to work on PostgreSQL and SQL Server
        SQLFragment sql = new SQLFragment("SELECT CASE WHEN EXISTS (SELECT 1 FROM " +
                ((ListDefinitionImpl) list).getIndexTable().getSelectName() +
                " WHERE ListId = ? AND Modified > ?) THEN 1 ELSE 0 END", list.getListId(), list.getLastIndexed());

        return new SqlSelector(getSchema(), sql).getObject(Boolean.class);
    }


    public void setLastIndexed(int listId, long ms)
    {
        new SqlExecutor(getSchema(), new SQLFragment("UPDATE " + getTinfoList() +
                " SET LastIndexed = ? WHERE RowId = ?", new Timestamp(ms), listId)).execute();
    }


    public void indexDeleted() throws SQLException
    {
        new SqlExecutor(getSchema(), new SQLFragment("UPDATE " + getTinfoList() +
                " SET LastIndexed = NULL")).execute();
    }
}
