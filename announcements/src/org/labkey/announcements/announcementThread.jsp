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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.announcements.AnnouncementsController" %>
<%@ page import="org.labkey.announcements.model.Announcement" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager" %>
<%@ page import="org.labkey.announcements.model.DiscussionServiceImpl" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<!--ANNOUNCEMENTS-->
<%
    AnnouncementsController.ThreadView me = (AnnouncementsController.ThreadView) HttpView.currentView();
    ViewContext context = me.getViewContext();
    Container c = context.getContainer();
    AnnouncementsController.ThreadViewBean bean = me.getModelBean();
    Announcement announcement = bean.announcement;
    AnnouncementManager.Settings settings = bean.settings;
    String contextPath = context.getContextPath();

    if (null == announcement)
    {
%><span><%=h(settings.getConversationName())%> not found</span><%
    return;
}

if (null != bean.message)
{
    %><span><%=h(bean.message)%></span><%
}

// is this an embedded discussion?
boolean embedded = (null != announcement.getDiscussionSrcURL() && !context.getActionURL().getPageFlow().equalsIgnoreCase("announcements"));  // TODO: Should have explicit flag for discussion case
ActionURL discussionSrc = null;

if (!embedded && null != announcement.getDiscussionSrcURL())
{
    discussionSrc = DiscussionServiceImpl.fromSaved(announcement.getDiscussionSrcURL());
    discussionSrc.replaceParameter("discussion.id", "" + announcement.getRowId());
}

if (!bean.print && !embedded)
{ %>
<table width="100%">
<tr>
    <td align="left"><%
    if (null != bean.listURL)
    {
        %>[<a href="<%=h(bean.listURL)%>">view list</a>]&nbsp;<%
    }
    if (!bean.isResponse)
    {
        %>[<a href="<%=h(bean.printURL)%>" target="printAnn">print</a>]&nbsp;<%
    }
    %></td>
</tr>
</table><%
}
if (!bean.print && null != discussionSrc)
{ %>
    <p></p><img src="<%=contextPath%>/_images/exclaim.gif">&nbsp;This is a <%=h(settings.getConversationName().toLowerCase())%> about another page.  [<a href='<%=h(discussionSrc.getLocalURIString())%>'>view&nbsp;page</a>]<%
}
%>

<table width=100%>
<tr>
    <td class="labkey-announcement-title" width="33%" align=left><span><%=h(announcement.getTitle())%></span></td>
    <td class="labkey-announcement-title" width="33%" align=center><%=h(announcement.getCreatedByName(bean.includeGroups, context))%></td>
    <td class="labkey-announcement-title" width="33%" align="right" nowrap><%

if (false && !bean.print && null != discussionSrc)
{
    %>[<a href="<%=h(discussionSrc.getLocalURIString())%>#discussionArea">view&nbsp;in&nbsp;context</a>]&nbsp;<%
}

if (bean.perm.allowUpdate(announcement) && !bean.print)
{
    ActionURL update = AnnouncementsController.getUpdateURL(c, announcement.getEntityId(), bean.currentURL);
    %>[<a href="<%=h(update.getLocalURIString())%>">edit</a>]<%
}
%>&nbsp;<%=h(DateUtil.formatDateTime(announcement.getCreated()))%></td>
</tr>
<tr>
    <td colspan=3 class="labkey-title-area-line"><img alt="" height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td>
</tr><%

if (settings.hasMemberList() && null != announcement.getEmailList())
{ %>
<tr>
    <td colspan="3">Members: <%=h(announcement.getEmailList())%></td>
</tr><%
}

if (settings.hasStatus() && null != announcement.getStatus())
{ %>
<tr>
    <td colspan="3">Status: <%=h(announcement.getStatus())%></td>
</tr><%
}

if (settings.hasExpires() && null != announcement.getExpires())
{ %>
<tr>
    <td align=left colspan="3">Expires: <%=h(DateUtil.formatDate(announcement.getExpires()))%>&nbsp;</td>
</tr><%
}

if (settings.hasAssignedTo() && null != announcement.getAssignedTo())
{ %>
<tr>
    <td colspan="3">Assigned&nbsp;To: <%=h(announcement.getAssignedToName(context))%></td>
</tr><%
}

if (null != announcement.getBody())
{ %>
<tr>
    <td colspan="3">&nbsp;</td>
</tr><%
}

%>
<tr>
    <td colspan="3"><%=announcement.translateBody(c)%></td>
</tr><%

if (0 < announcement.getAttachments().size())
{ %>
<tr>
    <td colspan="3"><div><%

        for (Attachment d : announcement.getAttachments())
        { %>
        <a href="<%=h(d.getDownloadUrl("announcements"))%>"><img alt="" src="<%=request.getContextPath() + d.getFileIcon()%>">&nbsp;<%=h(d.getName())%></a>&nbsp;<%
        } %>
    </div></td>
</tr><%
}%>
<tr>
    <td colspan="3">&nbsp;</td>
</tr><%

if (0 < announcement.getResponses().size())
{
    Announcement prev = announcement;
    %>
<tr><td colspan="3">

<table width=100%>
    <tr>
    <td width="2%">&nbsp;</td>
    <td colspan="2" width="100%">
        <table class="labkey-announcement-thread" width=100%><%

        for (Announcement r : announcement.getResponses())
        {%>
            <tr class="labkey-alternate-row">
                <td class="labkey-bordered" style="border-right: 0 none"><a name="row:<%=r.getRowId()%>"></a><%=h(r.getCreatedByName(bean.includeGroups, context)) + " responded:"%></td>
                <td class="labkey-bordered" style="border-left: 0 none" align="right"><%
                if (bean.perm.allowUpdate(r) && !bean.print)
                {
                    ActionURL update = AnnouncementsController.getUpdateURL(c, r.getEntityId(), bean.currentURL);
                    %>[<a href="<%=h(update.getLocalURIString())%>">edit</a>]<%
                    }
                    if (bean.perm.allowDeleteMessage(r) && !bean.print)
                    {
                        ActionURL deleteResponse = AnnouncementsController.getDeleteResponseURL(c, r.getEntityId(), bean.currentURL);
                %>&nbsp;[<a href="<%=h(deleteResponse.getLocalURIString())%>">delete</a>]<%
                }
                %>&nbsp;<%=h(DateUtil.formatDateTime(r.getCreated()))%></td>
            </tr><%

            if (settings.hasMemberList() && !StringUtils.equals(r.getEmailList(), prev.getEmailList()))
            { %>
            <tr>
                <td colspan="2">Members: <%=h(r.getEmailList())%></td>
            </tr><%
            }

            if (settings.hasStatus() && !StringUtils.equals(r.getStatus(), prev.getStatus()))
            { %>
            <tr>
                <td colspan="2">Status: <%=h(r.getStatus())%></td>
            </tr><%
            }

            if (settings.hasExpires() && !PageFlowUtil.nullSafeEquals(r.getExpires(), prev.getExpires()))
            { %>
            <tr>
                <td colspan="2">Expires: <%=h(DateUtil.formatDate(r.getExpires()))%></td>
            </tr><%
            }

            if (settings.hasAssignedTo() && !PageFlowUtil.nullSafeEquals(r.getAssignedTo(), prev.getAssignedTo()))
            { %>
            <tr>
                <td colspan="2">Assigned&nbsp;To: <%=h(r.getAssignedToName(context))%></td>
            </tr><%
            }

            if (null != r.getTitle() && !StringUtils.equals(r.getTitle(), prev.getTitle()))
            { %>
            <tr>
                <td colspan="2">Title: <%=h(r.getTitle())%></td>
            </tr><%
            } %>
            <tr>
                <td colspan="2"><%=r.translateBody(c)%></td>
            </tr><%
            if (0 < r.getAttachments().size())
            { %>
            <tr>
                <td colspan="2"><div><%
                for (Attachment rd : r.getAttachments())
                { %>
                    <a href="<%=h(rd.getDownloadUrl("announcements"))%>"><img alt="" src="<%=request.getContextPath()+ rd.getFileIcon()%>">&nbsp;<%=rd.getName()%></a>&nbsp;<%
                }
                %>
                </div></td>
            </tr><%
            }
            prev = r;
            %>
            <tr>
                <td colspan="2">&nbsp;</td>
            </tr><%
        }%>
        </table>
    </td>
    </tr>
</table>

</td></tr><%
} %>
<tr>
    <td colspan="3"><%

if (!bean.isResponse && !bean.print)
{
    if (bean.perm.allowResponse(announcement))
    {
        // There are two cases here.... I'm in the wiki controller or I'm not (e.g. I'm a discussion)
        if (embedded)
        {
            // UNDONE: respond in place
            URLHelper url = bean.currentURL.clone();
            url.replaceParameter("discussion.id",""+announcement.getRowId());
            url.replaceParameter("discussion.reply","1");
            %>
        <%=PageFlowUtil.generateButton("Post Response", url.getLocalURIString())%>&nbsp;<%
        }
        else
        {
            ActionURL respond = announcementURL(c, "respond", "parentId", announcement.getEntityId());
            respond.addReturnURL(bean.currentURL);
            %>
        <%=PageFlowUtil.generateButton("Post Response", respond.getLocalURIString())%>&nbsp;<%
        }
    }
    if (bean.perm.allowDeleteMessage(announcement))
    {
        ActionURL deleteThread = announcementURL(c, "deleteThread", "entityId", announcement.getEntityId());
        deleteThread.addParameter("cancelUrl", bean.currentURL.getLocalURIString());
        if (embedded)
        {
            URLHelper redirect = bean.currentURL.clone().deleteScopeParameters("discussion");
            deleteThread.addReturnURL(redirect);
        }
        else
        {
            deleteThread.addReturnURL(bean.messagesURL);
        }
        %>
        <%=PageFlowUtil.generateButton("Delete " + settings.getConversationName(), deleteThread.getLocalURIString())%>&nbsp;<%
    }
}
%>
    </td>
</tr>
</table><%
if (bean.isResponse)
{
    %><a name="response"/><%
}

%>

<%!
    ActionURL announcementURL(Container c, String action, String... params)
    {
        ActionURL url = new ActionURL("announcements", action, c);
        for (int i=0 ; i<params.length ; i+=2)
            url.addParameter(params[i], params[i+1]);
        return url;
    }
%>
