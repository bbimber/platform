<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.query.QueryParam"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.query.controllers.NewQueryForm" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    NewQueryForm form = (NewQueryForm) HttpView.currentModel();
    List<String> tableAndQueryNames = Collections.emptyList();
    if (form.getSchema() != null)
        tableAndQueryNames = form.getSchema().getTableAndQueryNames(false);
%>
<labkey:errors />

<% if (tableAndQueryNames.size() == 0) { %>
    Cannot create a new query: no tables/queries exist in the current schema to base the new query on.
<% } else { %>
    <form action="<%=urlFor(QueryController.NewQueryAction.class)%>" method="POST">
        <input type="hidden" name="<%=QueryParam.schemaName%>" value="<%=h(form.getSchemaName())%>" />
        <input type="hidden" name="ff_redirect" id="ff_redirect" value="sourceQuery" />

        <p>What do you want to call the new query?<br>
            <input type="text" id="ff_newQueryName" name="ff_newQueryName" value="<%=h(form.ff_newQueryName)%>">
        </p>

        <p>
            Which query/table do you want this new query to be based on?<br>
            <select name="ff_baseTableName">
                <% for (String queryName : tableAndQueryNames) { %>
                <option name="<%=h(queryName)%>"<%=queryName.equals(form.ff_baseTableName) ? " selected" : ""%>><%=h(queryName)%></option>
                <% } %>
            </select>
        </p>
        <labkey:button text="Create and Edit Source" />
    </form>
<% } %>