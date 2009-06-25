<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.apache.commons.lang.math.NumberUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<!-- saveReportView.jsp -->
<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dom");</script>

<%
    JspView<org.labkey.study.controllers.reports.ReportsController.SaveReportViewForm> me = (JspView<ReportsController.SaveReportViewForm>) HttpView.currentView();
    ReportsController.SaveReportViewForm bean = me.getModelBean();

    Report report = bean.getReport();
    org.labkey.api.view.ViewContext context = bean.getContext();
    boolean confirm = bean.getConfirmed() != null ? Boolean.parseBoolean(bean.getConfirmed()) : false;

    Container c = context.getContainer();
    Study study = StudyManager.getInstance().getStudy(c);
    DataSet[] defs = StudyManager.getInstance().getDataSetDefinitions(study);
    int showWithDataset = NumberUtils.toInt(report.getDescriptor().getProperty("showWithDataset"));

    String dsName = "parent";
    DataSet dsDef = StudyManager.getInstance().getDataSetDefinition(study, showWithDataset);
    if (dsDef != null)
        dsName = dsDef.getLabel();

%>

<style type="text/css">
    .chartSection td {
        padding: 0px 20px 0px 0px;
    }
</style>

<script type="text/javascript">

    function participantPlot()
    {
        var plotView = YAHOO.util.Dom.get("plotViewCheck");
        var dataset = YAHOO.util.Dom.get("datasetSelection");
        if (plotView && dataset)
        {
            dataset.disabled = plotView.checked;
        }
    }

    function validateForm()
    {
        var reportName = YAHOO.util.Dom.get('reportName');
        if (reportName && (reportName.value==null || reportName.value.length == 0))
        {
            alert("View name cannot be blank");
            return false;
        }
        return true;
    }
</script>

<table>
<%
    if (bean.getErrors() != null)
    {
        for (ObjectError e : (List<ObjectError>) bean.getErrors().getAllErrors())
        {
            %><tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
        }
    }
%>
</table>

<form method="post" action="<%=PageFlowUtil.filter(context.getActionURL().relativeUrl("saveReportView", null, "Study-Reports"))%>" onsubmit="return validateForm();">
    <input type="hidden" name="datasetId" value="<%=bean.getDatasetId()%>">
    <table>
    <tr>
<%
    if (confirm)
    {
%>
        <td>There is already a view called: <i><%=report.getDescriptor().getReportName()%></i>.<br/>Overwrite the existing view?
        <input type=hidden name=confirmed value=1>
        <input type=hidden name=label value="<%=bean.getLabel()%>">
<%
    } else {
%>
        <td><b>Save View</b></td>
        <td>Name:&nbsp;<input id="reportName" name="label" value="<%=PageFlowUtil.filter(bean.getLabel())%>">
        <input type=hidden name=srcURL value="<%=context.getActionURL().getLocalURIString()%>">
<%
    }
%>
        <input type=hidden name=reportType value="<%=report.getDescriptor().getReportType()%>">
        <input type=hidden name=chartsPerRow value="<%=bean.getChartsPerRow()%>">
        <input type=hidden name=isPlotView value="<%=bean.getIsPlotView()%>">
        <input type=hidden name=params value="<%=PageFlowUtil.filter(bean.getParams())%>"></td>

        <td>Add as Custom View For:
            <select id="datasetSelection" name="showWithDataset">
<%
        for (DataSet def : defs)
        {
            if (def.canRead(context.getUser())) %>
                <option <%=def.getDataSetId() == showWithDataset ? " selected" : ""%> value="<%=def.getDataSetId()%>"><%=PageFlowUtil.filter(def.getLabel())%></option>
<%
        }
%>
            </select>
        </td>
        <td><%=PageFlowUtil.generateSubmitButton((confirm ? "Overwrite" : "Save"))%>
<%
    if (confirm)
    {
%>
        &nbsp;<%=PageFlowUtil.generateButton("Cancel", bean.getSrcURL())%>
<%
    } 
%>
    </td>
    </tr>
<%
    if (context.hasPermission(ACL.PERM_ADMIN)) {
%>
        <tr>
            <td><input type="checkbox" value="true" name="shareReport" <%=bean.getShareReport() ? "checked" : ""%>>Make this view available to all users.</td>
            <td colspan=2>description:<textarea name="description" style="width: 100%;" rows="2"><%=StringUtils.trimToEmpty(bean.getDescription())%></textarea></td>
        </tr>
<%
    } else {
%>
        <tr>
            <td></td>
            <td colspan=2>description:&nbsp;<textarea name="description" style="width: 100%;" rows="2"><%=StringUtils.trimToEmpty(bean.getDescription())%></textarea></td>
        </tr>
<%
    }
%>
    </table>
<%--
<%
    if (allowPlotView) {
%>
    &nbsp;    
    <table>
        <tr>
            <td><input id="plotViewCheck" type="checkbox" name="isPlotView" value="true" <%=bean.getIsPlotView() ? "checked" : ""%> onclick="participantPlot();"><b>Show chart for only one participant at a time</b></td>
        </tr>
        <tr>
            <td><i>Select this check box to chart measures for an individual participant in the dataset. The report will
                be available from all datasets.</i></td>
        </tr>
    </table>
<%
    }
%>
--%>
</form><hr/>
