/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.query.controllers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * User: dave
 * Date: Sep 3, 2009
 * Time: 1:09:00 PM
 *
 * This class is used by the schema explorer query tree only
 */

@RequiresPermissionClass(ReadPermission.class)
public class GetSchemaQueryTreeAction extends ApiAction<GetSchemaQueryTreeAction.Form>
{
    // the schema browser behaves very badly if the table list gets too long, so we stop after a reasonable number
    // of tables.  The user can view the full list in the query window.
    private static final int MAX_TABLES_TO_LIST  = 100;
    public ApiResponse execute(Form form, BindException errors) throws Exception
    {
        JSONArray respArray = new JSONArray();
        Container container = getViewContext().getContainer();
        User user = getViewContext().getUser();
        DefaultSchema defSchema = DefaultSchema.get(user, container);

        if ("root".equals(form.getNode()))
        {
            Map<DbScope, JSONArray> map = new LinkedHashMap<DbScope, JSONArray>();

            // Initialize a JSONArray for each scope; later, we'll enumerate and skip the scopes that aren't actually
            // used in this folder.  This approach ensures we order the scopes naturally (i.e., labkey scope first).
            for (DbScope scope : DbScope.getDbScopes())
                map.put(scope, new JSONArray());

            //return list of schemas grouped by datasource
            for (String name : defSchema.getUserSchemaNames())
            {
                QuerySchema schema = DefaultSchema.get(user, container).getSchema(name);
                if (null == schema || null == schema.getDbSchema())
                    continue;

                DbScope scope = schema.getDbSchema().getScope();
                JSONArray schemas = map.get(scope);

                JSONObject schemaProps = new JSONObject();
                schemaProps.put("id", "s:" + name);
                schemaProps.put("text", PageFlowUtil.filter(name));
                schemaProps.put("description", PageFlowUtil.filter(schema.getDescription()));
                schemaProps.put("qtip", PageFlowUtil.filter(schema.getDescription()));
                schemaProps.put("schemaName", name);

                schemas.put(schemaProps);
            }

            for (Map.Entry<DbScope, JSONArray> entry : map.entrySet())
            {
                DbScope scope = entry.getKey();
                JSONArray schemas = entry.getValue();

                if (schemas.length() > 0)
                {
                    String dsName = scope.getDataSourceName();
                    JSONObject ds = new JSONObject();
                    ds.put("id", "ds:" + dsName);
                    ds.put("text", "Schemas in " + scope.getDisplayName());
                    ds.put("qtip", "Schemas in data source '" + dsName + "'");
                    ds.put("expanded", true);
                    ds.put("children", schemas);
                    ds.put("dataSourceName", dsName);

                    respArray.put(ds);
                }
            }
        }
        else
        {
            //node id is "s:<schema-name>"
            if (null != form.getNode() || form.getNode().startsWith("s:"))
            {
                String schemaName = form.getNode().substring(2);
                QuerySchema schema = defSchema.getSchema(schemaName);

                if (null != schema && schema instanceof UserSchema)
                {
                    UserSchema uschema = (UserSchema)schema;
                    JSONArray userDefined = new JSONArray();
                    JSONArray builtIn = new JSONArray();

                    //get built-in queries
                    List<String> queryNames = new ArrayList<String>(uschema.getVisibleTableNames());
                    Collections.sort(queryNames, new Comparator<String>(){
                        public int compare(String name1, String name2)
                        {
                            return name1.compareToIgnoreCase(name2);
                        }
                    });

                    int addedQueryCount = 0;
                    for (int i = 0; i < queryNames.size() && addedQueryCount < MAX_TABLES_TO_LIST; i++)
                    {
                        String qname = queryNames.get(i);
                        TableInfo tinfo = uschema.getTable(qname);
                        if (null == tinfo)
                            continue;
                        addQueryToList(schemaName, qname, tinfo.getDescription(), builtIn);
                        addedQueryCount++;
                    }

                    if (addedQueryCount == MAX_TABLES_TO_LIST)
                        addMoreLinkToList(schemaName, builtIn);

                    //get user-defined queries
                    Map<String, QueryDefinition> queryDefMap = QueryService.get().getQueryDefs(user, container, uschema.getSchemaName());
                    queryNames = new ArrayList<String>(queryDefMap.keySet());
                    Collections.sort(queryNames, new Comparator<String>(){
                        public int compare(String name1, String name2)
                        {
                            return name1.compareToIgnoreCase(name2);
                        }
                    });

                    addedQueryCount = 0;
                    for (int i = 0; i < queryNames.size() && addedQueryCount < MAX_TABLES_TO_LIST; i++)
                    {
                        String qname = queryNames.get(i);
                        QueryDefinition qdef = queryDefMap.get(qname);
                        if (!qdef.isHidden())
                        {
                            addQueryToList(schemaName, qname, qdef.getDescription(), userDefined);
                            addedQueryCount++;
                        }
                    }

                    if (addedQueryCount == MAX_TABLES_TO_LIST)
                        addMoreLinkToList(schemaName, userDefined);

                    //group the user-defined and built-in queries into folders
                    if (userDefined.length() > 0)
                    {
                        JSONObject fldr = new JSONObject();
                        fldr.put("id", "s:" + schemaName + ":ud");
                        fldr.put("text", "user-defined queries");
                        fldr.put("qtip", "Custom queries created by you and those shared by others.");
                        fldr.put("expanded", true);
                        fldr.put("children", userDefined);
                        fldr.put("schemaName", schemaName);
                        respArray.put(fldr);
                    }

                    if (builtIn.length() > 0)
                    {
                        JSONObject fldr = new JSONObject();
                        fldr.put("id", "s:" + schemaName + ":bi");
                        fldr.put("text", PageFlowUtil.filter("built-in queries & tables"));
                        fldr.put("qtip", "Queries and tables that are part of the schema by default.");
                        fldr.put("expanded", true);
                        fldr.put("children", builtIn);
                        fldr.put("schemaName", schemaName);
                        respArray.put(fldr);
                    }
                }
            }
        }

        HttpServletResponse resp = getViewContext().getResponse();
        resp.setContentType("application/json");
        resp.getWriter().write(respArray.toString());

        return null;
    }

    protected void addMoreLinkToList(String schemaName, JSONArray list)
    {
        JSONObject props = new JSONObject();
        props.put("schemaName", schemaName);
        props.put("text", PageFlowUtil.filter("More..."));
        props.put("leaf", true);
        String description = "Only the first " + MAX_TABLES_TO_LIST +
                " queries are shown.  Click to view the full list in the main pane.";
        props.put("description", description);
        props.put("qtip", PageFlowUtil.filter(description));
        list.put(props);
    }


    protected void addQueryToList(String schemaName, String qname, String description, JSONArray list)
    {
        JSONObject qprops = new JSONObject();
        qprops.put("schemaName", schemaName);
        qprops.put("queryName", qname);
        qprops.put("id", "q:" + schemaName + ":" + qname);
        qprops.put("text", PageFlowUtil.filter(qname));
        qprops.put("leaf", true);
        if (null != description)
        {
            qprops.put("description", description);
            qprops.put("qtip", PageFlowUtil.filter(description));
        }
        list.put(qprops);
    }

    public static class Form
    {
        private String _node;

        public String getNode()
        {
            return _node;
        }

        public void setNode(String node)
        {
            _node = node;
        }
    }
}
