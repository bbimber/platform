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
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.SourceForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    SourceForm form = (SourceForm)HttpView.currentModel();
    boolean builtIn = form.getQueryDef().isTableQueryDefinition();
    String sqlHelpTopic = "labkeySql";
    String metadataHelpTopic = "metadataSql";
    ActionURL exeUrl = form.getQueryDef().urlFor(QueryAction.executeQuery, getViewContext().getContainer());
%>
<style type="text/css">

    /* Back Panel */
    .x-border-layout-ct {
        background: none repeat scroll 0 0 transparent;
    }

    .x-panel-body {
        background-color: transparent;
    }

    /* Strip behind Tabs */
    .x-tab-panel-header, .x-tab-panel-footer {
        background-color: transparent;
    }

    ul.x-tab-strip-top {
        background: transparent;
    }

    /* Buttons on Panels */
    .query-button {
        float: left;
        padding: 3px 5px;
    }

    .query-editor-panel {
        background-color: transparent;
    }

    /* Allow fullscreen on editor */
    .x-panel-body {
        position: static;
    }

    table.labkey-data-region {
        width: 100%;
    }

    .error-container {
        margin-left: 30px !important;
        margin-top: 25px !important;
    }

    .labkey-status-info {
        height : 12px;
        font-size: 12px;
        margin : 0 0 10px 0;
    }

    /* Masking style */
    .ext-el-mask-msg {
        border: none;
        background-color: transparent;
    }

    .indicator-helper {
        margin: auto !important;
        margin-left: 3px !important;
        padding-left: 25px !important;
    }

</style>
<script type="text/javascript">
    LABKEY.requiresScript("query/QueryEditorPanel.js", true);
</script>
<div id="status" class="labkey-status-info" style="visibility: hidden;" width="99%">(status)</div>
<div id="query-editor-panel" class="extContainer"></div>
<script type="text/javascript">

    // CONSIDER : These events are used by both this window and the iFrame of the editAreaLoader.
    // If you are adding another event please be aware of both instances. 
    function saveEvent(e) {
        var cmp = Ext.getCmp('qep');
        if (cmp) {  cmp.getSourceEditor().save(); }
    }

    function editEvent(e) {
        var cmp = Ext.getCmp('qep');
        if (cmp) {  cmp.openSourceEditor(true); }
    }

    function executeEvent(e) {
        var cmp = Ext.getCmp('qep');
        if (cmp) {  cmp.getSourceEditor().execute(true); }
    }

    Ext.onReady(function(){

        Ext.QuickTips.init();

        Ext.Ajax.timeout = 86400000; // 1 day

        // TODO: Replace the following object with an Ajax call
        var query = {
            schema    : LABKEY.ActionURL.getParameter('schemaName'),
            query     : LABKEY.ActionURL.getParameter('query.queryName'),
            executeUrl: <%= PageFlowUtil.jsString(exeUrl.toString()) %>,
            canEdit   : <%= form.canEditSql() %>,
            builtIn   : <%= builtIn %>,
            metadataEdit : <%= form.canEditMetaData() %>,
            propEdit     : <%= form.canEditMetaData() && !builtIn %>,
            queryText    : <%=PageFlowUtil.jsString(form.ff_queryText)%>,
            metadataText : <%=PageFlowUtil.jsString(form.ff_metadataText)%>,
            help         : <%=PageFlowUtil.jsString(new HelpTopic(sqlHelpTopic).toString())%>,
            metadataHelp : <%=PageFlowUtil.jsString(new HelpTopic(metadataHelpTopic).toString())%>
        };

        var queryEditor = new LABKEY.query.QueryEditorPanel({
            id          : 'qep',
            border      : false,
            layout      : 'fit',
            bodyCssClass: 'query-editor-panel',
            query       : query
        });

        var panel = new Ext.Panel({
            renderTo   : 'query-editor-panel',
            layout     : 'fit',
            frame      : false,
            border     : false,
            boxMinHeight: 450,
            items      : [queryEditor]
        });

        var _resize = function(w, h) {
            LABKEY.Utils.resizeToViewport(panel, w, h, 40, 50);
        };

        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();

        function beforeSave() {
            setStatus('Saving...');
        }
        function afterSave(saved, json)  {
            if (saved) {
                setStatus("Saved", true);
            }
            else {
                var msg = "Failed to Save";
                if (json && json.exception)
                    msg += ": " + json.exception;
                setError(msg);
            }
        }
        queryEditor.getSourceEditor().on('beforeSave', beforeSave);
        queryEditor.getSourceEditor().on('save', afterSave);
        queryEditor.getMetadataEditor().on('beforeSave', beforeSave);
        queryEditor.getMetadataEditor().on('save', afterSave);

        function clearStatus() {
            var elem = Ext.get("status");
            elem.update("&nbsp;");
            elem.setVisible(false);
        }

        function setStatus(msg, autoClear)
        {
            var elem = Ext.get("status");
            elem.update(msg);
            elem.dom.className = "labkey-status-info";
            elem.setDisplayed(true);
            elem.setVisible(true);
            var clear = clearStatus;
            if(autoClear) clearStatus.defer(5000);
        }

        function setError(msg)
        {
            var elem = Ext.get("status");
            elem.update(msg);
            elem.dom.className = "labkey-status-error";
            elem.setVisible(true);
        }

        function onKeyDown(evt) {
            var handled = false;

            if(evt.ctrlKey && !evt.altKey && !evt.shiftKey) {
                if (83 == evt.getKey()) {  // s
                    saveEvent(evt);
                    handled = true;
                }
                if (69 == evt.getKey()) {  // e
                    editEvent(evt);
                    handled = true;
                }
                if (13 == evt.getKey()) {  // enter
                    executeEvent(evt);
                    handled = true;
                }
            }

            if(handled) {
                evt.preventDefault();
                evt.stopPropagation();
            }
        }

        Ext.EventManager.addListener(document, "keydown", onKeyDown);
    });
</script>
