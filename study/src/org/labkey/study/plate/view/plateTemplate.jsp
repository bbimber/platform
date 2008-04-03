<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.PlateTemplate" %>
<%@ page import="org.labkey.api.study.Position" %>
<%@ page import="org.labkey.api.study.WellGroupTemplate" %>
<%@ page import="org.labkey.study.controllers.plate.PlateController" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.study.WellGroup" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PlateController.TemplateViewBean> me = (JspView<PlateController.TemplateViewBean>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    PlateController.TemplateViewBean bean = me.getModelBean();
    PlateTemplate template = bean.getTemplate();
%>
<form action="plateTemplate.view" method="GET">
    <input type="hidden" name="name" value="<%= template.getName() %>">
    <select name="type" onChange="form.submit();">
        <%
            for (WellGroup.Type type : WellGroup.Type.values())
            {
        %>
        <option value="<%= h(type.name()) %>" <%= type == bean.getType() ? "SELECTED" : ""%>><%= h(type.name()) %></option>
        <%
            }
        %>
    </select>
</form>
<table class="normal" border="0" cellspacing="2" cellpadding="0">
    <tr>
        <td>&nbsp;</td>
        <%
            for (int col = 0; col < template.getColumns(); col++)
            {
        %>
        <td><center><b><%= col + 1 %></b></center></td>
        <%
            }
        %>
    </tr>
<%
    Map<Integer, String> groupToColor = new HashMap<Integer, String>();
    char rowChar = 'A';
    for (int row = 0; row < template.getRows(); row++)
    {
%>
        <tr>
            <td><b><%= rowChar %></b></td>
        <%
            for (int col = 0; col < template.getColumns(); col++)
            {
                List<? extends WellGroupTemplate> groups = template.getWellGroups(template.getPosition(row, col));
                StringBuilder wellDisplayString = new StringBuilder();
                TreeMap<String, String> currentGroupColors = new TreeMap<String, String>();
                for (WellGroupTemplate group : groups)
                {
                    if (bean.getType() == null || bean.getType() == group.getType())
                    {
                        String color = groupToColor.get(group.getRowId());
                        if (color == null)
                        {
                            color = me.getModelBean().getColorGenerator().next();
                            groupToColor.put(group.getRowId(), color);
                        }
                        currentGroupColors.put(group.getName(), color);
                    }
                }

        %>
            <td style="border:solid 1px #808080">
                <table cellspacing="0" cellpadding="3">
                    <%
                        if (currentGroupColors.isEmpty())
                        {
                    %>
                            <td>Non-<%= h(bean.getType().name()) %></td>
                    <%
                        }
                        else
                        {
                            for (Map.Entry<String, String> groupColor : currentGroupColors.entrySet())
                            {
                    %>
                            <td bgcolor="<%= groupColor.getValue() %>"><%= h(groupColor.getKey()) %><br></td>
                    <%
                            }
                        }
                    %>
                </table>
            </td>
        <%
        }
        %>
        </tr>
        <%
        rowChar++;
    }
%>
</table>