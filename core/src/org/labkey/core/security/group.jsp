<%
/*
 * Copyright (c) 2005-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.security.UserUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.security.GroupView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.security.UserPrincipal" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.PrincipalType" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    GroupView.GroupBean bean = ((JspView<GroupView.GroupBean>)HttpView.currentView()).getModelBean();
    Container c = getViewContext().getContainer();
%>

<script type="text/javascript">
    LABKEY.requiresScript('completion.js');

    var form;
    
    function selectAllCheckboxes(form, value)
    {
        var elems = form.elements;
        var l = elems.length;
        for (var i = 0; i < l; i++)
        {
            var e = elems[i];
            if (e.type == 'checkbox' && !e.disabled && e.name == 'delete') e.checked = value;
        }
        return false;
    }

    function selectForRemoval(id, name, value)
    {
        var el = document.getElementById(id);
        if (el && el.type == 'checkbox' && !el.disabled && el.name == 'delete' && el.value == name)
        {
            el.checked = value;
        }
        return false;
    }

    function selectAllForRemoval(value)
    {
        <% for (UserPrincipal redundantMember : bean.redundantMembers.keySet()) {
            %>selectForRemoval('<%= redundantMember.getUserId() %>', '<%= redundantMember.getName() %>', value);<%
        } %>
        return false;
    }

    function confirmRemoveUsers()
    {
        var elems = document.getElementsByName("delete");
        var l = elems.length;
        var selected = false;
        for (var i = 0; i < l; i++)
        {
            if (elems[i].checked)
            {
                selected = true;
                break;
            }
        }
        var ok = true;
        if (selected)
            ok = confirm("Permanently remove selected users from this group?");
        if (ok)
            form.setClean(); 
        return ok;
    }

Ext.onReady(function()
{
    form = new LABKEY.Form('groupMembersForm');

    Ext.QuickTips.init();
    Ext.apply(Ext.QuickTips.getQuickTip(), {
        dismissDelay: 15000,
        trackMouse: true
    });
});

</script>

<%if (bean.group.getUserId() > 0){%><%=generateButton("Rename Group", "renameGroup.view?id=" + bean.group.getUserId())%><%}%>

<form id="groupMembersForm" action="updateMembers.post" method="POST">
<labkey:csrf />
<%
if (bean.messages.size() > 0)
{
    %><b>System membership status for new group members:</b><br>
    <div id="messages"><%
    for (String message : bean.messages)
    {
        %><%= message %><br><%
    }
    %></div><br><%
}
%><labkey:errors /><%
WebPartView.startTitleFrame(out, "Group members", null, "100%", null);
if (bean.members.size() <= 0)
{
    %><p>This group currently has no members.</p><%
}
else
{
    %>

    <div id="current-members">
    <table>
        <tr>
            <th width=70>Remove</th>
            <th>Name</th>
            <th width="110px">&nbsp;</th>
        </tr>
    <%
    for (UserPrincipal member : bean.members)
        {
        Integer userId = member.getUserId();
        String memberName = member.getName();
        boolean isGroup = member.getPrincipalType() == PrincipalType.GROUP;
        %>
        <tr>
            <td>
                <input type="checkbox" name="delete" id="<%= userId %>" value="<%= h(memberName) %>">
            </td>
            <td>
        <%
        if (isGroup)
        {
            Group g = (Group)member;
            if (g.isProjectGroup())
            {
                %><a href="<%= urlProvider(SecurityUrls.class).getManageGroupURL(c, c.getPath() + "/" + h(memberName)) %>"><%
            }
            else
            {
                %><a href="<%= urlProvider(SecurityUrls.class).getManageGroupURL(ContainerManager.getRoot(), h(memberName)) %>"><%
            }
            %>
                <span style="font-weight:bold;">
                    <%= h(memberName) %>
                </span>
              </a>
            <%
        }
        else
        {
            %><%= h(memberName) %>&nbsp;<%
        }

        if (bean.redundantMembers.containsKey(member))
        {
            %><a ext:qtitle="Redundant Member" ext:qtip="<%=bean.displayRedundancyReasonHTML(member)%>">*</a><%
        }
        %>
            </td>
            <td>
                <% if (!isGroup)
                   {
                    %><%= textLink("permissions", urlProvider(UserUrls.class).getUserAccessURL(c, userId)) %><%
                   }
                   else
                   {
                    %><%= textLink("permissions", urlProvider(SecurityUrls.class).getGroupPermissionURL(c, userId)) %><%
                   }
                %>
            </td>
        </tr>
        <%
        }
        ActionURL urlGroup = getViewContext().cloneActionURL();
        urlGroup.setAction("groupExport");
        urlGroup.replaceParameter("group", bean.groupName);
    %>
    </table>
    </div><%
    if (bean.redundantMembers.size() > 0)
    {
        %><div style="padding:5px;">* These group members already appear in other included member groups and can be safely removed.</div><%
    }

    %><div style="padding:5px;"><%
    if (bean.redundantMembers.size() > 0)
    {
        %><%=PageFlowUtil.generateSubmitButton("Select Redundant Members", "return selectAllForRemoval(true);")%><%
    }
    %>
    <%=PageFlowUtil.generateSubmitButton("Select All", "return selectAllCheckboxes(this.form, true);")%>
    <%=PageFlowUtil.generateSubmitButton("Clear All", "return selectAllCheckboxes(this.form, false);")%>
    <%= generateButton("Export All to Excel", urlGroup)%>
    </div><%
}
%><br>
<div id="add-members">
<span style="font-weight:bold">Add New Members</span> (enter one email address or group per line):<br>
<textarea name="names" cols="60" rows="8"
         onKeyDown="return ctrlKeyCheck(event);"
         onBlur="hideCompletionDiv();"
         autocomplete="off"
         onKeyUp="return handleChange(this, event, 'completeMember.view?groupId=<%= bean.group.getUserId() %>&prefix=');">
</textarea><br>
<input type="checkbox" name="sendEmail" value="true" checked>Send notification emails to all new<%
if (null != bean.ldapDomain)
{
    %>, non-<%= bean.ldapDomain %><%
}
%> users.<br><br>
<span style="font-weight:bold">Include a message</span> with the new user mail (optional):<br>
    <textarea rows="8" cols="60" name="mailPrefix"></textarea><br>
<input type="hidden" name="group" value="<%= bean.groupName %>">
<%=PageFlowUtil.generateSubmitButton("Update Group Membership", "return confirmRemoveUsers();")%>
</div>
</form>
<%
if (!bean.isSystemGroup)
{
    %><br><br><div id="delete-group"><%
    if (bean.members.size() == 0)
    {
        %>
        <form action="standardDeleteGroup.post" method="POST">
        <labkey:csrf/>
        <%=PageFlowUtil.generateSubmitButton("Delete Empty Group", "return confirm('Permanently delete group " + bean.groupName + "?')")%>
        <input type="hidden" name="group" value="<%= bean.groupName %>">
        </form>
        <%
    }
    else
    {
        %>To delete this group, first remove all members.<%
    }
    %></div><%
}

    WebPartView.endTitleFrame(out);
%>
