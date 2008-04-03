<%@ page import="org.labkey.announcements.AnnouncementsController.BulkEditView.BulkEditBean"%>
<%@ page import="org.labkey.announcements.model.AnnouncementManager"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<BulkEditBean> me = (HttpView<BulkEditBean>) HttpView.currentView();
    BulkEditBean bean = me.getModelBean();
%>
<%=formatMissedErrors("form")%>
<form action="bulkEdit.post" method="POST">

<table class="normal">
    <tr><td></td></tr>
    <tr>
        <td>The current folder default setting is: <%=bean.folderEmailOption%></td>
    </tr>
    <tr><td></td></tr>
</table>


<table style="border:solid 1px #AAAAAA" cellspacing="0" cellpadding="1">
    <tr>
        <th class='header'  style='border-right:solid 1px #AAAAAA;border-bottom:solid 1px #AAAAAA'>Email</th>
        <th class='header'  style='border-right:solid 1px #AAAAAA;border-bottom:solid 1px #AAAAAA'>First Name</th>
        <th class='header'  style='border-right:solid 1px #AAAAAA;border-bottom:solid 1px #AAAAAA'>Last Name</th>
        <th class='header'  style='border-right:solid 1px #AAAAAA;border-bottom:solid 1px #AAAAAA'>Display Name</th>
        <th class='header'  style='border-right:solid 1px #AAAAAA;border-bottom:solid 1px #AAAAAA'>Email Option</th>
        <th class='header'  style='border-right:solid 1px #AAAAAA;border-bottom:solid 1px #AAAAAA'>Project Member?</th>
    </tr>

<%
    int i = 1;
    for (AnnouncementManager.EmailPref emailPref : bean.emailPrefList)
    {
        String bgColor = "#FFFFFF";
        //shade odd rows in gray
        if (i % 2 == 1)
            bgColor = "#EEEEEE";

        int userId = emailPref.getUserId();
        String email = emailPref.getEmail();
        String firstName = emailPref.getFirstName();
        String lastName = emailPref.getLastName();
        String displayName = emailPref.getDisplayName();
        //integer value
        Integer emailOptionId = emailPref.getEmailOptionId();

        int emailOptionValue = -1;
        if (emailOptionId != null)
            emailOptionValue = emailOptionId.intValue();
%>
            <tr bgcolor="<%=bgColor%>">
                <td style='border-right:solid 1px #AAAAAA'>
                    <input type="hidden" name="userId" value="<%=userId%>">
                    <%= email %>
                </td>
                <td style='border-right:solid 1px #AAAAAA'>
                    <%= firstName %>&nbsp;
                </td>
                <td style='border-right:solid 1px #AAAAAA'>
                    <%= lastName %>&nbsp;
                </td>
                <td style='border-right:solid 1px #AAAAAA'>
                    <%= displayName %>
                </td>
                <td style='border-right:solid 1px #AAAAAA'>
                    <select name="emailOptionId"><%

                        if (emailPref.isProjectMember())
                        { %>
                        <option value='<%=AnnouncementManager.EMAIL_PREFERENCE_DEFAULT%>'<%=emailOptionValue == AnnouncementManager.EMAIL_PREFERENCE_DEFAULT ? " selected" : "" %>>&lt;folder default&gt;</option><%
                        } %>
                        <option value='<%=AnnouncementManager.EMAIL_PREFERENCE_NONE%>'<%=emailOptionValue == AnnouncementManager.EMAIL_PREFERENCE_NONE ? " selected" : "" %>>No email</option>
                        <option value='<%=AnnouncementManager.EMAIL_PREFERENCE_ALL%>'<%=emailOptionValue == AnnouncementManager.EMAIL_PREFERENCE_ALL ? " selected" : "" %>>All conversations</option>
                        <option value='<%=AnnouncementManager.EMAIL_PREFERENCE_MINE%>'<%=emailOptionValue == AnnouncementManager.EMAIL_PREFERENCE_MINE ? " selected" : "" %>>My conversations</option>
                        <option value='<%=AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST | AnnouncementManager.EMAIL_PREFERENCE_ALL%>'<%=emailOptionValue == (AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST | AnnouncementManager.EMAIL_PREFERENCE_ALL) ? " selected" : "" %>>Daily digest of all conversations</option>
                        <option value='<%=AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST | AnnouncementManager.EMAIL_PREFERENCE_MINE%>'<%=emailOptionValue == (AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST | AnnouncementManager.EMAIL_PREFERENCE_MINE) ? " selected" : "" %>>Daily digest of my conversations</option>
                    </select>
                </td>
                <td align="center" style='border-right:solid 1px #AAAAAA'>
                    <%  if (emailPref.isProjectMember())
                            out.write("Yes");
                        else
                            out.write("No");
                    %>
                </td>
            </tr>
            <%
            i++;
    }%>
<tr>
    <td colspan="5"></td>
</tr>
</table>
<input type="hidden" name="returnUrl" value="<%=bean.returnUrl%>">
<input type="image" src="<%=PageFlowUtil.submitSrc()%>">&nbsp;<input type=image src="<%=PageFlowUtil.buttonSrc("Cancel")%>" value="Cancel" onclick="javascript:window.history.back(); return false;">
</form>
