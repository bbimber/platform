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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.announcements.AnnouncementsController" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.DeleteThreadAction" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.DownloadAction" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.RespondAction" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.ThreadView" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.ThreadViewBean" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.announcements.model.DiscussionServiceImpl" %>
<%@ page import="org.labkey.api.announcements.DiscussionService" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.springframework.web.servlet.mvc.Controller" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<!--ANNOUNCEMENTS-->
<%
    ThreadView me = (ThreadView) HttpView.currentView();
    ViewContext context = me.getViewContext();
    Container c = context.getContainer();
    User user = context.getUser();
    ThreadViewBean bean = me.getModelBean();
    AnnouncementModel announcementModel = bean.announcementModel;
    DiscussionService.Settings settings = bean.settings;
    String contextPath = context.getContextPath();

    if (null == announcementModel)
    {
%><span><%=h(settings.getConversationName())%> not found</span><%
    return;
}

if (null != bean.message)
{
    %><span><%=h(bean.message)%></span><%
}

// is this an embedded discussion?
boolean embedded = (null != announcementModel.getDiscussionSrcURL() && !context.getActionURL().getPageFlow().equalsIgnoreCase("announcements"));  // TODO: Should have explicit flag for discussion case
ActionURL discussionSrc = null;

if (!embedded && null != announcementModel.getDiscussionSrcURL())
{
    discussionSrc = DiscussionServiceImpl.fromSaved(announcementModel.getDiscussionSrcURL());
    discussionSrc.replaceParameter("discussion.id", "" + announcementModel.getRowId());
}

if (!bean.print && !embedded)
{ %>
<table width="100%">
<tr>
    <td align="left"><%
    if (null != bean.listURL)
    {
        %><%=textLink("view list", bean.listURL)%>&nbsp;<%
    }
    if (!bean.isResponse)
    {
        %><%=textLink("print", bean.printURL.toString())%>&nbsp;<%
    }
    %></td>
</tr>
</table><%
}
if (!bean.print && null != discussionSrc)
{ %>
    <p></p><img src="<%=contextPath%>/_images/exclaim.gif">&nbsp;This is a <%=h(settings.getConversationName().toLowerCase())%> about another page.  <%=textLink("view page", discussionSrc.getLocalURIString())%><%
}
%>

<table style="table-layout:fixed;width:100%">
<tr>
    <td class="labkey-announcement-title labkey-force-word-break" width="33%" align=left><span><%=h(announcementModel.getTitle())%></span></td>
    <td class="labkey-announcement-title" width="33%" align=center><%=h(announcementModel.getCreatedByName(bean.includeGroups, user))%></td>
    <td class="labkey-announcement-title" width="33%" align="right" nowrap><%

if (false && !bean.print && null != discussionSrc)
{
    %><%=textLink("view in context", discussionSrc.getLocalURIString())%>&nbsp;<%
}

if (bean.perm.allowUpdate(announcementModel) && !bean.print)
{
    ActionURL update = AnnouncementsController.getUpdateURL(c, announcementModel.getEntityId(), bean.currentURL);
    %><%=textLink("edit", update.getLocalURIString())%><%
}
%>&nbsp;<%=h(DateUtil.formatDateTime(announcementModel.getCreated()))%></td>
</tr>
<tr>
    <td colspan=3 class="labkey-title-area-line"></td>
</tr><%

if (settings.hasMemberList() && null != announcementModel.getEmailList())
{ %>
<tr>
    <td colspan="3">Members: <%=h(announcementModel.getEmailList())%></td>
</tr><%
}

if (settings.hasStatus() && null != announcementModel.getStatus())
{ %>
<tr>
    <td colspan="3">Status: <%=h(announcementModel.getStatus())%></td>
</tr><%
}

if (settings.hasExpires() && null != announcementModel.getExpires())
{ %>
<tr>
    <td align=left colspan="3">Expires: <%=h(DateUtil.formatDate(announcementModel.getExpires()))%>&nbsp;</td>
</tr><%
}

if (settings.hasAssignedTo() && null != announcementModel.getAssignedTo())
{ %>
<tr>
    <td colspan="3">Assigned&nbsp;To: <%=h(announcementModel.getAssignedToName(user))%></td>
</tr><%
}

if (null != announcementModel.getBody())
{ %>
<tr>
    <td colspan="3">&nbsp;</td>
</tr><%
}

%>
<tr>
    <td colspan="3" class="labkey-force-word-break"><%=announcementModel.translateBody(c)%></td>
</tr><%

if (0 < announcementModel.getAttachments().size())
{ %>
<tr>
    <td colspan="3"><div><%

        for (Attachment d : announcementModel.getAttachments())
        { %>
        <a href="<%=h(d.getDownloadUrl(DownloadAction.class))%>"><img alt="" src="<%=request.getContextPath() + d.getFileIcon()%>">&nbsp;<%=h(d.getName())%></a>&nbsp;<%
        } %>
    </div></td>
</tr><%
}%>
<tr>
    <td colspan="3">&nbsp;</td>
</tr><%

if (0 < announcementModel.getResponses().size())
{
    AnnouncementModel prev = announcementModel;
    %>
<tr><td colspan="3">

<table width=100%>
    <tr>
    <td width="2%">&nbsp;</td>
    <td colspan="2" width="100%">
        <table class="labkey-announcement-thread" width=100%><%

        for (AnnouncementModel r : announcementModel.getResponses())
        {%>
            <tr class="labkey-alternate-row">
                <td class="labkey-bordered" style="border-right: 0 none"><a name="row:<%=r.getRowId()%>"></a><%=h(r.getCreatedByName(bean.includeGroups, user)) + " responded:"%></td>
                <td class="labkey-bordered" style="border-left: 0 none" align="right"><%
                if (bean.perm.allowUpdate(r) && !bean.print)
                {
                    ActionURL update = AnnouncementsController.getUpdateURL(c, r.getEntityId(), bean.currentURL);
                    %><%=textLink("edit", update.getLocalURIString())%><%
                    }
                    if (bean.perm.allowDeleteMessage(r) && !bean.print)
                    {
                        ActionURL deleteResponse = AnnouncementsController.getDeleteResponseURL(c, r.getEntityId(), bean.currentURL);
                %>&nbsp;<%=textLink("delete", deleteResponse.getLocalURIString())%><%
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
                <td colspan="2">Assigned&nbsp;To: <%=h(r.getAssignedToName(user))%></td>
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
                    <a href="<%=h(rd.getDownloadUrl(DownloadAction.class))%>"><img alt="" src="<%=request.getContextPath()+ rd.getFileIcon()%>">&nbsp;<%=rd.getName()%></a>&nbsp;<%
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
    if (bean.perm.allowResponse(announcementModel))
    {
        // There are two cases here.... I'm in the wiki controller or I'm not (e.g. I'm a discussion)
        if (embedded)
        {
            // UNDONE: respond in place
            URLHelper url = bean.currentURL.clone();
            url.replaceParameter("discussion.id",""+ announcementModel.getRowId());
            url.replaceParameter("discussion.reply","1");
            %>
        <%=generateButton("Post Response", url)%>&nbsp;<%
        }
        else
        {
            ActionURL respond = announcementURL(c, RespondAction.class, "parentId", announcementModel.getEntityId());
            respond.addReturnURL(bean.currentURL);
            %>
        <%=generateButton("Post Response", respond)%>&nbsp;<%
        }
    }
    if (bean.perm.allowDeleteMessage(announcementModel))
    {
        ActionURL deleteThread = announcementURL(c, DeleteThreadAction.class, "entityId", announcementModel.getEntityId());
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
        <%=generateButton("Delete " + settings.getConversationName(), deleteThread)%>&nbsp;<%
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
    ActionURL announcementURL(Container c, Class<? extends Controller> action, String... params)
    {
        ActionURL url = new ActionURL(action, c);
        for (int i=0 ; i<params.length ; i+=2)
            url.addParameter(params[i], params[i+1]);
        return url;
    }
%>
