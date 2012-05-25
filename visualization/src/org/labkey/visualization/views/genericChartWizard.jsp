<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.visualization.GenericChartReport" %>
<%@ page import="org.labkey.visualization.VisualizationController" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<VisualizationController.GetVisualizationForm> me = (JspView<VisualizationController.GetVisualizationForm>) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    VisualizationController.GetVisualizationForm form = me.getModelBean();
    GenericChartReport.RenderType renderType = GenericChartReport.getRenderType(form.getRenderType());

    String renderId = "generic-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresScript("vis/genericChartPanel.js");

    Ext.QuickTips.init();
</script>

<script type="text/javascript">

    Ext4.onReady(function(){

        var panel = Ext4.create('LABKEY.ext4.GenericChartPanel', {
            height          : 600,
            schemaName      : '<%=form.getSchemaName()%>',
            queryName       : '<%=form.getQueryName()%>',
            dataRegionName  : '<%=form.getDataRegionName()%>',
            baseUrl         : '<%=ctx.getActionURL()%>',
            renderTo        : '<%= renderId %>'
        });
    });

</script>

<div id="<%= renderId%>" class="labkey-folder-header" style="width:100%;"></div>

