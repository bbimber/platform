<%
/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.model.ReportPropsManager" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.view.ReportDesignBean" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Collections" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ReportDesignBean> me = (JspView<ReportDesignBean>) HttpView.currentView();
    ReportDesignBean bean = me.getModelBean();
    ViewContext context = me.getViewContext();
    Report report = bean.getReport(context);
    ReportDescriptor reportDescriptor = report.getDescriptor();

    String reportName = reportDescriptor.getReportName();
    String description = reportDescriptor.getReportDescription();
    Integer authorId = null;
    Integer createdBy = reportDescriptor.getCreatedBy();
    Date createdDate = reportDescriptor.getCreated();
    Date modifiedDate = reportDescriptor.getModified();
    Boolean isShared = reportDescriptor.getOwner() == null ? true : false;
    Date refreshDate = null;

    ActionURL vewReportURL = report.getRunReportURL(context);
    ActionURL editReportURL = report.getEditReportURL(context, context.getActionURL());
    Map<String, String> reportURLAttributes =  report.getRunReportTarget() != null ?
            Collections.singletonMap("target", report.getRunReportTarget()) :
            Collections.<String, String>emptyMap();

    ActionURL thumbnailUrl = PageFlowUtil.urlProvider(ReportUrls.class).urlThumbnail(bean.getContainer(), report);
    String type = report.getTypeDescription();
    String category = "";
    String status = "";
    try
    {
        category = reportDescriptor.getCategory().getLabel();
        status =  ReportPropsManager.get().getPropertyValue(reportDescriptor.getEntityId(), bean.getContainer(), "status").toString();
        authorId = ((Double) ReportPropsManager.get().getPropertyValue(reportDescriptor.getEntityId(), bean.getContainer(), "author")).intValue();
        refreshDate = (Date) ReportPropsManager.get().getPropertyValue(reportDescriptor.getEntityId(), bean.getContainer(), "refreshDate");
    }
    catch (Exception e)
    {
        // do nothing, status already set to blank.
    }
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
            Author:
        </td>
        <td>
            <%
                if(authorId != null)
                {
            %>
                <%= UserManager.getUser(authorId) != null ? h(UserManager.getUser(authorId).getDisplayName(context.getUser())) : ""%>
            <%
                }
            %>
        </td>
    </tr>

    <tr>
        <td class="labkey-form-label">
            Created By:
        </td>
        <td>
            <%
                if(createdBy != null)
                {
            %>
                <%= UserManager.getUser(createdBy) != null ? h(UserManager.getUser(createdBy).getDisplayName(context.getUser())) : ""%>
            <%
                }
            %>
        </td>
    </tr>

    <tr>
        <td class="labkey-form-label">
            Status:
        </td>

        <td>
            <%=h(status)%>
        </td>
    </tr>

    <tr>
        <td class="labkey-form-label">
            Data Cut Date:
        </td>
        <td>

            <%=refreshDate != null && refreshDate.getTime() > 0 ? h(DateUtil.formatDateTime(refreshDate)) : ""%>
        </td>
    </tr>

    <tr>
        <td class="labkey-form-label">
            Category:
        </td>

        <td>
            <%=h(category)%>
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

     <tr>
        <td class="labkey-form-label">
            Shared:
        </td>
        <td>
            <%=isShared ? "Yes" : "No"%>
        </td>
    </tr>

    <tr>
        <td class="labkey-form-label">
            Type:
        </td>
        <td>
             <%=h(type)%>
        </td>
    </tr>
    
    <tr>
        <td class="labkey-form-label">
            Created:
        </td>
        <td>
             <%=createdDate != null && createdDate.getTime() > 0 ?  h(DateUtil.formatDateTime(createdDate)) : ""%>
        </td>
    </tr>

    <tr>
        <td class="labkey-form-label">
            Modified:
        </td>
        <td>
            <%=modifiedDate != null && modifiedDate.getTime() > 0 ? h(DateUtil.formatDateTime(modifiedDate)) : ""%>
        </td>
    </tr>

    <tr>
        <td valign="top" class="labkey-form-label">
            Report Thumbnail:
        </td>
        <td>
            <img src="<%=h(thumbnailUrl)%>">
        </td>
    </tr>

    <tr>
        <td colspan="2">&nbsp;</td>
    </tr>

    <tr>
        <td colspan="2">
            <%=PageFlowUtil.generateButton("View Report", vewReportURL, null, reportURLAttributes)%>
            <%=report.canEdit(context.getUser(), context.getContainer()) ? PageFlowUtil.generateButton("Edit Report", editReportURL) : ""%>
        </td>
    </tr>
</table>
