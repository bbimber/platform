<%
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
%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<List<org.labkey.api.study.assay.AssayProvider>> me = (JspView<List<AssayProvider>>) HttpView.currentView();
    List<AssayProvider> providers = me.getModelBean();
%>
<form action="designerRedirect.view">
    <div>
        Select the assay design type:
        <select name="providerName">
            <% for (AssayProvider provider : providers)
            { %>
                <option value="<%= h(provider.getName()) %>"><%= h(provider.getName()) %></option>
            <% } %>
        </select>
    </div>
    <div> <%= generateSubmitButton("Next" )%> <%= generateButton("Cancel", getViewContext().cloneActionURL().setAction("begin")) %></div>
</form>