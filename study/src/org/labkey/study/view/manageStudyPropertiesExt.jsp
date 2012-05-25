<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    ViewContext context = getViewContext();
    boolean canEdit = context.getContainer().hasPermission(context.getUser(), AdminPermission.class);
    boolean emptyStudy = getStudy().isEmptyStudy();
    String timepointType = getStudy().getTimepointType().toString();
    String cancelLink = context.getActionURL().getParameter("returnURL");
    String startDate = null;
    if (timepointType == "DATE")
    {
        startDate = getStudy().getStartDate().toString();
    }
    if (cancelLink == null || cancelLink.length() == 0)
        cancelLink = new ActionURL(StudyController.ManageStudyAction.class, context.getContainer()).toString();
%>

<%!

    public String shortenFileName(String fileName)
    {
        if (fileName.length() > 55)
        {
            fileName = fileName.substring(0, 54) + "...";
        }

        return fileName;
    }
%>

<div class="extContainer" id="manageStudyPropertiesDiv"></div>

<script type="text/javascript">
    LABKEY.requiresScript("FileUploadField.js");
</script>

<script type="text/javascript">

var canEdit = <%=canEdit?"true":"false"%>;
var editableFormPanel = canEdit;
var studyPropertiesFormPanel = null;
var emptyStudy = <%=emptyStudy?"true":"false"%>;
var timepointType = "<%=timepointType%>";

function removeProtocolDocument(name, xid)
{
    if (Ext)
    {
        function remove()
        {
            var params =
            {
                name: name
            };

            Ext.Ajax.request(
            {
                url    : LABKEY.ActionURL.buildURL('study', 'removeProtocolDocument'),
                method : 'POST',
                success: function()
                {
                    var el = document.getElementById(xid);
                    if (el)
                    {
                        el.parentNode.removeChild(el);
                    }
                },
                failure: function()
                {
                    alert('Failed to remove study protocol document.');
                },
                params : params
            });
        }

        Ext.Msg.show({
            title : 'Remove Attachment',
            msg : 'Please confirm you would like to remove this study protocol document. This cannot be undone.',
            buttons: Ext.Msg.OKCANCEL,
            icon: Ext.MessageBox.QUESTION,
            fn  : function(b) {
                if (b == 'ok') {
                    remove();
                }
            }
        });
    }
}

function addExtFilePickerHandler()
{
    var fibasic = new Ext.form.FileUploadField(
    {
        width: 300
    });

    var removeBtn = new Ext.Button({
        text: "remove",
        name: "remove",
        uploadId: fibasic.id,
        handler: removeNewAttachment
    });

    var uploadField = new Ext.form.CompositeField({
        renderTo: 'filePickers',
        items:[fibasic, removeBtn]
    });

    studyPropertiesFormPanel.add(uploadField);
}

function removeNewAttachment(btn)
{
    // In order to 'remove' an attachment before it is submitted we must hide and disable it. We CANNOT destroy the
    // elements related to the upload field, if we do the form will not validate. This is a known Issue with Ext 3.4.
    // http://www.sencha.com/forum/showthread.php?25479-2.0.1-2.1-Field.destroy()-on-Fields-rendered-by-FormLayout-does-not-clean-up.
    Ext.getCmp(btn.uploadId).disable();
    btn.ownerCt.hide();
}

var maskEl = null;

function mask()
{
    maskEl = Ext.getBody();
    maskEl.mask();
}

function unmask()
{
    if (maskEl)
        maskEl.unmask();
    maskEl = null;
}


function showSuccessMessage(message, after)
{
    Ext.get("formError").update("");
    var el = Ext.get("formSuccess");
    el.update(message);
    el.pause(3).fadeOut({callback:function(){el.update("");}});
}


function onSaveSuccess_updateRows()
{
    // if you want to stay on page, you need to refresh anyway to udpate attachments
    var msgbox = Ext.Msg.show({
       title:'Status',
       msg: '<span class="labkey-message">Changes saved</span>',
       buttons: false
    });
    var el = msgbox.getDialog().el;
    el.pause(1).fadeOut({callback:cancelButtonHandler});
}

function onSaveSuccess_formSubmit()
{
    // if you want to stay on page, you need to refresh anyway to udpate attachments
    var msgbox = Ext.Msg.show({
       title:'Status',
       msg: '<span class="labkey-message">Changes saved</span>',
       buttons: false
    });
    var el = msgbox.getDialog().el;
    el.pause(1).fadeOut({callback:cancelButtonHandler});
}


function onSaveFailure_updateRows(error)
{
    unmask();
    Ext.get("formSuccess").update("");
    Ext.get("formError").update(error.exception);
}


function onSaveFailure_formSubmit(form, action)
{
    unmask();
    switch (action.failureType)
    {
        case Ext.form.Action.CLIENT_INVALID:
            Ext.Msg.alert('Failure', 'Form fields may not be submitted with invalid values');
            break;
        case Ext.form.Action.CONNECT_FAILURE:
            Ext.Msg.alert('Failure', 'Ajax communication failed');
            break;
        case Ext.form.Action.SERVER_INVALID:
           Ext.Msg.alert('Failure', action.result.msg);
    }
}


function submitButtonHandler()
{
    var form = studyPropertiesFormPanel.getForm();
    if (form.isValid())
    {
        /* This works except for the file attachment
        var rows = studyPropertiesFormPanel.getFormValues();
        LABKEY.Query.updateRows(
        {
            schemaName:'study',
            queryName:'StudyProperties',
            rows:rows,
            success : onSaveSuccess_updateRows,
            failure : onSaveFailure_updateRows
        });
        */
        form.fileUpload = true;
        form.submit(
        {
            success : onSaveSuccess_formSubmit,
            failure : onSaveFailure_formSubmit
        });
        mask();
    }
    else
    {
        Ext.MessageBox.alert("Error Saving", "There are errors in the form.");
    }
}


function editButtonHandler()
{
    editableFormPanel = true;
    destroyFormPanel();
    createPage();
}


function cancelButtonHandler()
{
    LABKEY.setSubmit(true);
    window.location = <%=q(cancelLink)%>;
}


function doneButtonHandler()
{
    LABKEY.setSubmit(true);
    window.location = <%=q(cancelLink)%>;
}


function destroyFormPanel()
{
    if (studyPropertiesFormPanel)
    {
        studyPropertiesFormPanel.destroy();
        studyPropertiesFormPanel = null;
    }
}


var renderTypes = {<%
String comma = "";
for (WikiRendererType type : getRendererTypes())
{
    %><%=comma%><%=q(type.name())%>:<%=q(type.getDisplayName())%><%
    comma = ",";
}%>};

function renderFormPanel(data, editable)
{
    destroyFormPanel();
    var buttons = [];

    if (editable)
    {
        buttons.push({
            text:"Submit",
            handler: submitButtonHandler,
            scope: this
        });
        buttons.push({text: "Cancel", handler: cancelButtonHandler});
    }
    else if (<%=canEdit ? "true" : "false"%>)
    {
        buttons.push({text:"Edit", handler: editButtonHandler});
        buttons.push({text:"Done", handler: doneButtonHandler});
    }

    var renderTypeCombo = new Ext.form.ComboBox(
    {
        hiddenName : 'DescriptionRendererType',
        name : 'RendererTypeDisplayName',
        mode: 'local',
        triggerAction: 'all',
        valueField: 'renderType',
        displayField: 'displayText',
        value: data.rows[0]['DescriptionRendererType'], //Set default value to the current render type.
        store : new Ext.data.ArrayStore(
        {
            id : 0, fields:['renderType', 'displayText'],
            data : [
<%
                comma = "";
                for (WikiRendererType type : getRendererTypes())
                {
                    %><%=comma%>[<%=q(type.name())%>,<%=q(type.getDisplayName())%>]<%
                    comma = ",";
                }
%>
            ]
        })
    });

    var timepointTypeRadioGroup = new Ext.form.RadioGroup({
        fieldLabel: "Timepoint Type",
        disabled: !emptyStudy,
        items: [{
            xtype: 'radio',
            disabled: !emptyStudy,
            boxLabel: 'VISIT',
            inputValue: 'VISIT',
            name: 'TimepointType',
            checked: timepointType == 'VISIT'
        },{
            xtype: 'radio',
            disabled: !emptyStudy,
            boxLabel: 'DATE',
            inputValue: 'DATE',
            name: 'TimepointType',
            checked: timepointType == 'DATE'
        }],
//        listeners: {
//            change: function(group, checkedRadio){
//                if(checkedRadio.inputValue == "DATE"){
//                    startDateField.setDisabled(false);
//                    startDateField.setVisible(true);
//                } else {
//                    startDateField.setDisabled(true);
//                    startDateField.setVisible(false);
//                }
//            }
//        }
    });

//    We should eventually get the startDateField working. Currently it doesn't send the correct date format
//    back to the server, due to some issues with posting files to the server at the same time.

//    var startDateField = new Ext.form.DateField({
//        fieldLabel: "Start Date",
//        name: "StartDate",
//        editable: false,
//        allowBlank: false,
        <%--value: "<%=startDate%>",--%>
//        altFormats: LABKEY.Utils.getDateAltFormats(),
//        disabled: timepointType == "VISIT",
//        hidden: timepointType == "VISIT"
//    });

    // fields we've handled (whether or now we're showing them)
    var handledFields = {};
    var items = [];

    items.push({name:'Label'});
    handledFields['Label'] = true;
    items.push({name:'Investigator'});
    handledFields['Investigator'] = true;
    items.push({name:'Grant'});
    handledFields['Grant'] = true;
    items.push({name:'Description', width:500});
    handledFields['Description'] = true;
    if (editableFormPanel)
        items.push(renderTypeCombo);
    handledFields[renderTypeCombo.hiddenName] = true;
    items.push({fieldLabel:'Protocol Documents', width:500, border: false, xtype:'panel', contentEl:'attachmentsDiv'});
    // the original form didn't include these, but we can decide later
    items.push(timepointTypeRadioGroup);
    handledFields['TimepointType'] = true;
//    items.push(startDateField);
    handledFields['StartDate'] = true;
    handledFields['Container'] = true;

    // Now let's add all the other fields
    var cm = data.columnModel;
    var col, i;
    for (i=0 ; i<cm.length ; i++)
    {
        col = cm[i];
        col = cm[i];
        if (col.hidden) continue;
        if (handledFields[col.dataIndex]) continue;
        items.push({name:col.dataIndex});
    }
    for (i=0 ; i<items.length ; i++)
    {
        items[i].disabled = !editableFormPanel;
        items[i].disabledClass = 'noop';    // TODO the default disabledClass makes everything unreadable
    }

    studyPropertiesFormPanel = new LABKEY.ext.FormPanel(
    {
        selectRowsResults : data,
        labelSeparator    : '',
        padding           : 10,
        defaults          : { width:500, disabled : !editableFormPanel, disabledClass:'noop' },
        labelWidth        : 150,   <%-- don't wrap even on Large font theme --%>
        buttonAlign       : 'left',
        buttons           : buttons,
        items             : items
    });
    studyPropertiesFormPanel.render('formDiv');
}


function onQuerySuccess(data) // e.g. callback from Query.selectRows
{
    renderFormPanel(data, editableFormPanel);
}


function onQueryFailure(a)
{
    alert(a);
}


function createPage()
{
    LABKEY.Query.selectRows({
        schemaName: 'study',
        queryName: 'StudyProperties',
        columns: '*',
        success: onQuerySuccess,
        failure: onQueryFailure
    });
}

function isFormDirty()
{
    return studyPropertiesFormPanel && studyPropertiesFormPanel.getForm().isDirty();
}

window.onbeforeunload = LABKEY.beforeunload(isFormDirty);

Ext.onReady(createPage);

</script>

<span id=formSuccess class=labkey-message-strong></span><span id=formError class=labkey-error></span>&nbsp;</br>
<div id='formDiv'></div>
<div id='attachmentsDiv' class='x-hidden'>
<table>
<%
        int x = -1;
        for (Attachment att : getStudy().getProtocolDocuments())
        {
            x++;
            %><tr id="attach-<%=x%>" style="min-width:20px;">
                <td>&nbsp;<img src="<%=request.getContextPath() + att.getFileIcon()%>" alt="logo"/></td>
                <td>&nbsp;<%= h(shortenFileName(att.getName())) %></td>
                <td>&nbsp;[<a onclick="removeProtocolDocument(<%=PageFlowUtil.jsString(att.getName())%>, 'attach-<%=x%>'); ">remove</a>]</td>
            </tr ><%
        }
%>
</table>
<div id="filePickers">
</div>
<div>
&nbsp;<a onclick="addExtFilePickerHandler(); return false;" href="#addFile"><img src="<%=request.getContextPath()%>/_images/paperclip.gif">&nbsp;&nbsp;Attach a file</a>
</div>
</div>