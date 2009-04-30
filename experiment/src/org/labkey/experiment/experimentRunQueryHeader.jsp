<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.exp.ExperimentRunType"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.experiment.ChooseExperimentTypeBean" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
    JspView<ChooseExperimentTypeBean> me = (JspView<ChooseExperimentTypeBean>) HttpView.currentView();
    ChooseExperimentTypeBean bean = me.getModelBean();
    ActionURL baseURL = bean.getUrl().clone().deleteParameters();
%>
<form method="get" action="<%= baseURL %>">
    <% for (Pair<String, String> params : bean.getUrl().getParameters())
    {
        if (!"experimentRunFilter".equals(params.getKey()))
        { %>
            <input type="hidden" name="<%= PageFlowUtil.filter(params.getKey())%>" value="<%= PageFlowUtil.filter(params.getValue())%>" />
    <%  }
    } %>
    Filter by run type: <select name="experimentRunFilter" onchange="form.submit()">
        <% for (ExperimentRunType type : bean.getFilters()) { %>
            <option <% if (type == bean.getSelectedFilter()) { %>selected <% } %> value="<%= type.getDescription() %>"><%=
                type.getDescription() %></option>
        <% } %>
    </select>
</form>
