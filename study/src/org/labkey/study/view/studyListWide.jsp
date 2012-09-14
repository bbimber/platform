<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<style type="text/css">
    div.labkey-study-list div.study-header {padding-top:6px;}
    div.labkey-study-list div.study-header:first-child {padding-top:0;}
    span.labkey-study-title {font-weight: bold; font-size: larger; text-align: left}
    span.labkey-study-investigator {float: right; width:150px}
</style>
<%
    JspView me = (JspView) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();

    Study[] studies = StudyManager.getInstance().getAllStudies(ctx.getContainer(), ctx.getUser());
    if (studies.length == 0)
    {
        out.print(text("No Studies found in " + (c.equals(c.getProject()) ? "project " : "folder ") + h(c.getName()) + " or child folders."));
    }
    int i = 0;
    FolderType studyFolderType = ModuleLoader.getInstance().getFolderType("Study");
%>
<div class="labkey-study-list"><%
    for (Study study: studies)
    {
        %>
        <div class='study-header'>
        <%
        ActionURL url;
        if (studyFolderType.equals(study.getContainer().getFolderType()))
            url = studyFolderType.getStartURL(study.getContainer(), ctx.getUser());
        else
            url = new ActionURL(StudyController.BeginAction.class,study.getContainer());
        %>
        <span class="labkey-study-title"><a href="<%=url%>"><%=h(study.getLabel())%></a></span>
    <%if(null != study.getInvestigator()) { %>
        <span class='labkey-study-investigator'><%=h(study.getInvestigator())%></span>
    <%}%>
        </div>
            <%=text(study.getDescriptionHtml())%>
<%
    }
%>
</div>
