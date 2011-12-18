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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.reports.report.view.ReportDesignBean" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="java.util.Date" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%
    JspView<ReportDesignBean> me = (JspView<ReportDesignBean>) HttpView.currentView();
    ReportDesignBean bean = me.getModelBean();
    ReportDescriptor reportDescriptor = bean.getReport().getDescriptor();

    ViewContext context = HttpView.currentContext();

    String reportName = reportDescriptor.getReportName();
    String description = reportDescriptor.getReportDescription();
    Integer authorId = reportDescriptor.getAuthor();
    Integer createdBy = reportDescriptor.getCreatedBy();
    Date createdDate = reportDescriptor.getCreated();
    Date modifiedDate = reportDescriptor.getModified();
    ActionURL reportURL = bean.getReport().getRunReportURL(context);
    ActionURL thumbnailUrl = PageFlowUtil.urlProvider(ReportUrls.class).urlThumbnail(bean.getContainer(), bean.getReport());
    String type = bean.getReport().getTypeDescription();
%>

<table name="reportDetails" id="reportDetails">
    <tr class="labkey-wp-header">
    </tr>

    <tr>
        <td class="labkey-form-label">
            Name:
        </td>
            
        <td>
            <%=h(reportName)%>
        </td>
    </tr>

    <tr>
        <td class="labkey-form-label">
            Description:
        </td>
        <td>
            <%=h(description)%>
        </td>
    </tr>
    <%
        if(authorId != null)
        {
    %>
    <tr>
        <td class="labkey-form-label">
            Author:
        </td>
        <td>
            <%= UserManager.getUser(authorId) != null ? h(UserManager.getUser(authorId).getDisplayName(context.getUser())) : ""%>
        </td>
    </tr>
    <%
        }

        if(createdBy != null)
        {
    %>
    <tr>
        <td class="labkey-form-label">
            Created By:
        </td>
        <td>
            <%= UserManager.getUser(createdBy) != null ? h(UserManager.getUser(createdBy).getDisplayName(context.getUser())) : ""%>
        </td>
    </tr>
    <%
        }
    %>

    <tr>
        <td class="labkey-form-label">
            Type:
        </td>
        <td>
             <%=type%>
        </td>
    </tr>
    
    <tr>
        <td class="labkey-form-label">
            Date Created:
        </td>
        <td>
             <%=h(createdDate.toString())%>
        </td>
    </tr>

    <tr>
        <td class="labkey-form-label">
            Last Modified:
        </td>
        <td>
            <%=h(modifiedDate)%>
        </td>
    </tr>

    <tr>
        <td valign="top" class="labkey-form-label">
            Report Thumbnail:
        </td>
        <td>
            <img src="<%=thumbnailUrl.getURIString()%>">
        </td>
    </tr>

    <tr>
        <td class="labkey-form-label">
            Report URL:
        </td>
        <td>
            <a href="<%=reportURL.getURIString()%>"><%=reportURL.getURIString()%></a>
        </td>
    </tr>
</table>