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
%><span class=normal><%=h(settings.getConversationName())%> not found</span><%
    return;
}

if (null != bean.message)
{
    %><span class=normal><%=h(bean.message)%></span><%
}

// is this an embedded discussion?
boolean embedded = (null != announcement.getDiscussionSrcURL() && !context.getActionURL().getPageFlow().equalsIgnoreCase("announcements"));
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
    <td align="left" class="normal"><%
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

<table width="100%" cellpadding=0>
<tr>
    <td class="normal" style="padding-top:14px; padding-bottom:2px; width:33%;" align=left><span class="ms-announcementtitle"><%=h(announcement.getTitle())%></span></td>
    <td class="normal" style="padding-top:14px; padding-bottom:2px; width:33%;" align=center><%=h(announcement.getCreatedByName(bean.includeGroups, context))%></td>
    <td class="normal" style="padding-top:14px; padding-bottom:2px; width:33%;" align="right" nowrap><%

if (false && !bean.print && null != discussionSrc)
{
    %>[<a href="<%=h(discussionSrc.getLocalURIString())%>#discussionArea">view&nbsp;in&nbsp;context</a>]&nbsp;<%
}

ActionURL returnUrl = context.getActionURL();

if (bean.perm.allowUpdate(announcement) && !bean.print)
{
    ActionURL update = AnnouncementsController.getUpdateURL(c, announcement.getEntityId(), returnUrl);
    %>[<a href="<%=h(update.getLocalURIString())%>">edit</a>]<%
}
%>&nbsp;<%=h(DateUtil.formatDateTime(announcement.getCreated()))%></td>
</tr>
<tr style="height:1px;">
    <td colspan=3 class=ms-titlearealine><img alt="" height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td>
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
    <td colspan="3" class="normal"><%=announcement.translateBody(context.getContainer())%></td>
</tr><%

if (0 < announcement.getAttachments().size())
{ %>
<tr>
    <td colspan="3"><div class="normal"><%

        for (Attachment d : announcement.getAttachments())
        { %>
        <a href="<%=h(d.getDownloadUrl("announcements"))%>"><img alt="" border=0 src="<%=request.getContextPath() + d.getFileIcon()%>">&nbsp;<%=h(d.getName())%></a>&nbsp;<%
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

<table width="100%" border="0" cellspacing="0" cellpadding="2">
    <tr>
    <td width="2%">&nbsp;</td>
    <td colspan="2" width="100%">
        <table width="100%" border="0" cellspacing="0" cellpadding="2"><%

        for (Announcement r : announcement.getResponses())
        {%>
            <tr>
                <td class="normal" style="background-color: #dddddd"><a name="row:<%=r.getRowId()%>"></a><%=h(r.getCreatedByName(bean.includeGroups, context)) + " responded:"%></td>
                <td class="normal" align="right" style="background-color: #dddddd"><%
                if (bean.perm.allowUpdate(r) && !bean.print)
                {
                    ActionURL update = AnnouncementsController.getUpdateURL(c, r.getEntityId(), returnUrl);
                    %>[<a href="<%=h(update.getLocalURIString())%>">edit</a>]<%
                    }
                    if (bean.perm.allowDeleteMessage(r) && !bean.print)
                    {
                        ActionURL deleteResponse = AnnouncementsController.getDeleteResponseURL(c, r.getEntityId(), returnUrl);
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
                <td colspan="2" class="normal"><%=r.translateBody(context.getContainer())%></td>
            </tr><%
            if (0 < r.getAttachments().size())
            { %>
            <tr>
                <td colspan="2"><div class="normal"><%
                for (Attachment rd : r.getAttachments())
                { %>
                    <a href="<%=h(rd.getDownloadUrl("announcements"))%>"><img alt="" border=0 src="<%=request.getContextPath()+ rd.getFileIcon()%>">&nbsp;<%=rd.getName()%></a>&nbsp;<%
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
    <td colspan="3" class="normal"><%

if (!bean.isResponse && !bean.print)
{
    if (bean.perm.allowResponse(announcement))
    {
        // There are two cases here.... I'm in the wiki controller or I'm not (e.g. I'm a discussion)
        if (embedded)
        {
            // UNDONE: respond in place
            ActionURL url = context.cloneActionURL();
            url.replaceParameter("discussion.id",""+announcement.getRowId());
            url.replaceParameter("discussion.reply","1");
            %>
        <a href="<%=h(url.getLocalURIString())%>"><img src='<%=PageFlowUtil.buttonSrc("Post Response")%>' border="0" alt="[post response]"></a>&nbsp;<%
        }
        else
        {
            ActionURL respond = announcementURL(context, "respond", "parentId", announcement.getEntityId(), "returnUrl", context.getActionURL().getEncodedLocalURIString());
            %>
        <a href="<%=h(respond.getLocalURIString())%>"><img src='<%=PageFlowUtil.buttonSrc("Post Response")%>' border="0" alt="[post response]"></a>&nbsp;<%
        }
    }
    if (bean.perm.allowDeleteMessage(announcement))
    {
        ActionURL deleteThread = announcementURL(context, "deleteThread", "entityId", announcement.getEntityId());
        deleteThread.addParameter("cancelUrl", context.getActionURL().getLocalURIString());
        if (embedded)
        {
            ActionURL redirect = context.cloneActionURL().deleteScopeParameters("discussion");
            deleteThread.addParameter("returnUrl", redirect.getLocalURIString());
        }
        else
        {
            deleteThread.addParameter("returnUrl", bean.messagesURL.getLocalURIString());
        }
        %>
        <a href="<%=deleteThread.getEncodedLocalURIString()%>"><img src='<%=PageFlowUtil.buttonSrc("Delete " + settings.getConversationName())%>' border="0" alt="[delete <%=h(settings.getConversationName().toLowerCase())%>]"></a>&nbsp;<%
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
    ActionURL announcementURL(ViewContext context, String action, String... params)
    {
        ActionURL url = new ActionURL("announcements", action, context.getContainer());
        for (int i=0 ; i<params.length ; i+=2)
            url.addParameter(params[i], params[i+1]);
        return url;
    }
%>
