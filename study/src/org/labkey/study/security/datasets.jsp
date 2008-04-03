<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.model.Study"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.security.SecurityManager"%>
<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="org.labkey.api.security.Group"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    String groupName(Group g)
    {
        if (g.getUserId() == Group.groupUsers)
            return "All site users";
        else
            return g.getName();
    }
%>
<%
    HttpView<Study> me = (HttpView<Study>) HttpView.currentView();
    Study study = me.getModelBean();
//    String contextPath = request.getContextPath();
//    User user = (User)request.getUserPrincipal();
//    Container root = ContainerManager.getRoot();
    ACL studyAcl = study.getACL();

    Group[] groups = SecurityManager.getGroups(study.getContainer().getProject(), true);

    ArrayList<Group> readGroups = new ArrayList<Group>();
    ArrayList<Group> restictedGroups = new ArrayList<Group>();
    ArrayList<Group> noreadGroups = new ArrayList<Group>();
    for (Group g : groups)
    {
        if (g.getUserId() == Group.groupAdministrators)
            continue;
        int perm = studyAcl.getPermissions(g) & (ACL.PERM_READ | ACL.PERM_READOWN);
        if (perm == 0)
            noreadGroups.add(g);
        else if (perm == ACL.PERM_READOWN)
            restictedGroups.add(g);
        else
            readGroups.add(g);
    }
%>
These groups can read ALL datasets.
<ul class="minus">
<%
    boolean guestsCanRead = false;
    boolean usersCanRead = false;
    if (readGroups.size() == 0)
    {
        %><li><i>none</i></li><%
    }
    for (Group g : readGroups)
    {
        if (g.getUserId() == Group.groupUsers)
            usersCanRead = true;
        if (g.getUserId() == Group.groupGuests)
            guestsCanRead = true;
        %><li><%=h(groupName(g))%></li><%
    }
%>
</ul>
<%
if (guestsCanRead || usersCanRead)
{
    restictedGroups.clear();
%>
    Since <i>All site users</i> and/or <i>Guests</i> have read permissions, this is effectively an <b>open</b> study.<p/>
    To restrict access to individual datasets, these groups should not have read permission.
<%
}
else
{
%>
    These groups do not have read permissions.  (Note: a user may belong to more that one group, see documentation.)
    <ul class="minus">
    <%
        if (noreadGroups.size() == 0)
        {
    %>
        <li><i>none</i></li>
    <%
        }
        for (Group g : noreadGroups)
        {
    %>
        <li><%=h(groupName(g))%></li>
    <%
        }
    %>
    </ul>
    <%
        if (restictedGroups.size() == 0)
        {
    %>
        There are no other groups with access to some datasets. To grant access to individual datasets select the
        'some' option for the specific group above.
    <%
        }
        else
        {
    %>
        These groups may be given access to individual datasets.
    <%
        }
}
%>

<form id="datasetSecurityForm" action="applyDatasetPermissions.post" method="POST">
<%
    String redir = (String)HttpView.currentContext().get("redirect");
    if (redir != null)
        out.write("<input type=\"hidden\" name=\"redirect\" value=\"" + h(redir) + "\">");

    int row = 0;
    %><br/><table class=normal cellspacing=0>
    <tr bgcolor="<%=row++%2==0?"#eeeeee":"#ffffff"%>"><th>&nbsp;</th><%
    for (Group g : restictedGroups)
    {
        %><th style="padding: 0 5px 0 5px;"><%=h(groupName(g))%></th><%
    }
    %></tr><%
    DataSetDefinition[] datasets = study.getDataSets();
    for (DataSetDefinition ds : datasets)
    {
        ACL acl = ds.getACL();
        String inputName = "dataset." + ds.getDataSetId();
        %><tr bgcolor="<%=row++%2==0?"#eeeeee":"#ffffff"%>"><td><%=h(ds.getLabel())%></td><%
        for (Group g : restictedGroups)
        {
            %><td align=center><input type=checkbox name="<%=inputName%>" value="<%=g.getUserId()%>" <%=acl.hasPermission(g, ACL.PERM_READ) ? "checked" : ""%>></td><%
        }
        %></tr><%
    }
    %>
    </table>
    <table><tr><td><img src="<%=PageFlowUtil.buttonSrc("Select All")%>" alt="Select All" onclick="selectAllDatasets();"></td><td><img src="<%=PageFlowUtil.buttonSrc("Clear All")%>" alt="Clear All" onclick="clearAllDatasets();"></td><td><input type=image src="<%=PageFlowUtil.buttonSrc("Update")%>"></td></tr></table>
</form>

<script type="text/javascript">
function selectAllDatasets()
{
    var f = document.getElementById("datasetSecurityForm");
    setAllCheckboxes(f,true);
}
function clearAllDatasets()
{
    var f = document.getElementById("datasetSecurityForm");
    setAllCheckboxes(f,false);
}
</script>
