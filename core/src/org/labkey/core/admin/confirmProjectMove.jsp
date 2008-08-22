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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ManageFoldersForm> view = (JspView<ManageFoldersForm>)HttpView.currentView();
    ManageFoldersForm f = view.getModelBean();
    Container c = view.getViewContext().getContainer();
    ActionURL cancelURL = AdminController.getManageFoldersURL(c);
%>

<form action="moveFolder.post" method="post">
<p>
You are moving folder '<%=h(c.getName())%>' from one project into another.
This will remove all permission settings from this folder, any subfolders, and any contained objects.
</p>
<p>
This action cannot be undone.
</p>
    <input type="hidden" name="addAlias" value="<%=h(f.isAddAlias())%>">
    <input type="hidden" name="target" value="<%=h(f.getTarget())%>">
    <input type="hidden" name="confirmed" value="1">
    <%=PageFlowUtil.generateSubmitButton("Confirm Move")%>
    <%=PageFlowUtil.generateButton("Cancel", cancelURL)%>
</form>