<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotService" %>
<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.DisplayColumn" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<QuerySnapshotForm> me = (JspView<QuerySnapshotForm>) HttpView.currentView();
    QuerySnapshotForm bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    Map<String, String> columnMap = new HashMap<String, String>();
    for (String name : bean.getSnapshotColumns())
        columnMap.put(name, name);

    boolean isAutoUpdateable = false;//QuerySnapshotService.get(bean.getSchemaName()) instanceof QuerySnapshotService.AutoUpdateable;
    boolean isEdit = QueryService.get().getSnapshotDef(context.getContainer(), bean.getSchemaName(), bean.getSnapshotName()) != null;
%>

<labkey:errors/>

<form action="" method="post">
    <table cellpadding="0" class="normal">
        <tr><td colspan="10" style="padding-top:14; padding-bottom:2"><span class="ms-announcementtitle">Snapshot Name and Type</span></td></tr>
        <tr><td colspan="10" width="100%" class="ms-titlearealine"><img height="1" width="1" src="<%=AppProps.getInstance().getContextPath() + "/_.gif"%>"></td></tr>

        <tr><td>&nbsp;</td></tr>
        <tr><td>Snapshot Name:</td><td><input type="text" name="snapshotName" <%=isEdit ? "readonly" : ""%> value="<%=StringUtils.trimToEmpty(bean.getSnapshotName())%>"></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td>Manual Refresh</td><td><input <%=isAutoUpdateable ? "" : "disabled"%> checked type="radio" name="manualRefresh"></td></tr>
        <tr><td>Automatic Refresh</td><td><input disabled="<%=isAutoUpdateable ? "" : "disabled"%>" type="radio" name="automaticRefresh"></td></tr>

        <tr><td></td><td><table class="normal">
    <%  for (DisplayColumn col : QuerySnapshotService.get(bean.getSchemaName()).getDisplayColumns(bean)) { %>
            <tr><td><input type="hidden" name="snapshotColumns" value="<%=col.getName()%>"></td></tr>
    <%  } %>
        </table></td></tr>

        <tr><td><input type="image" src="<%=PageFlowUtil.buttonSrc("Next")%>"></td></tr>
    </table>
</form>
