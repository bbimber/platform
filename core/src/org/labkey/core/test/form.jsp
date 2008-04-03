<%@ page import="org.labkey.core.test.TestController" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="java.util.Enumeration" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="org.springframework.validation.FieldError" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.springframework.validation.BindingResult" %>
<%@ page import="java.io.IOException" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    void errorRow(JspWriter out, String path) throws IOException
    {
        String err = formatErrorsForPath(path);
        if (null != err && err.length() > 0)
        {
            out.println("<tr><td colspan=2>" + err + "</td></tr>");
        }
    }
%>
<%
    TestController.SimpleForm form = (TestController.SimpleForm) getModelBean();
    String enctype = StringUtils.defaultString((String) request.getAttribute("enctype"), "application/x-www-form-urlencoded");
    assert enctype.equals("multipart/form-data") || enctype.equals("application/x-www-form-urlencoded");
%>
<%=formatErrorsForPath("form")%>
<form enctype="<%=enctype%>" method="POST">
    <table>
        <%errorRow(out,"form.a");%>
        <tr><td>a</td><td><input type=checkbox name="a" <%=form.getA()?"checked":""%>><input type=hidden name="<%=SpringActionController.FIELD_MARKER%>a"></td></tr>
        <%errorRow(out,"form.b");%>
        <tr><td>b</td><td><input name="b" value="<%=h(form.getB())%>"></td></tr>
        <%errorRow(out,"form.c");%>
        <tr><td colspan=2><%=formatErrorsForPath("form.c")%></td></tr>
        <tr><td>c</td><td><input name="c" value="<%=h(form.getC())%>"></td></tr>
        <%errorRow(out,"form.int");%>
        <tr><td>int</td><td><input name="int" value="<%=h(form.getInt())%>"></td></tr>
        <%errorRow(out,"form.positive");%>
        <tr><td>Positive Number</td><td><input name="positive" value="<%=h(form.getPositive())%>"></td></tr>
        <%errorRow(out,"form.required");%>
        <tr><td>Required String</td><td><input name="required" value="<%=h(form.getRequired())%>"></td></tr>
        <tr><td>Text</td><td><textarea name="text" rows="12" cols="60"><%=h(form.getText())%></textarea></td></tr>
        <tr><td>x</td><td><input name="x" value="<%=h(form.getX())%>"></td></tr>
        <tr><td>y</td><td><input name="y" value="<%=h(form.getY())%>"></td></tr>
        <tr><td>z</td><td><input name="z" value="<%=h(form.getZ())%>"></td></tr>
<%
    if (enctype.startsWith("multipart"))
    {
        %><tr><td>file</td><td><input name="file"></td></tr><%
    }
%>
    </table>
    <%=formatMissedErrors("form")%><br>
    <input type=image name="submit" src="<%=h(PageFlowUtil.submitSrc())%>">
</form>
<%--



// DEBUG OUTPUT BELOW

--%>
<br><br><br>
<div style="background-color:#f8f8f8;">
<hr><b>errors</b><br>
<%
for (ObjectError e : getAllErrors(pageContext))
{
    String path = e.getObjectName();
    if (e instanceof FieldError)
        path = path + "." + ((FieldError)e).getField();
    %><b><%=h(path)%>:</b>&nbsp;<%=h(getViewContext().getMessage(e))%><br><%
}
%>
<hr><b>form</b>
<pre>
<%=h(form.toString())%>
</pre>
<%
    out.println("<hr><b>attributes</b><br>");
    Enumeration e = request.getAttributeNames();
    while (e.hasMoreElements())
    {
        String name = (String)e.nextElement();
        out.println("<b>" + h(name) + ":</b> " + h(String.valueOf(request.getAttribute(name))) + "<br>");
    }

    out.println("<hr><b>parameters</b><br>");
    Enumeration f = request.getParameterNames();
    while (f.hasMoreElements())
    {
        String name = (String)f.nextElement();
        out.println("<b>" + h(name) + ":</b> " + h(String.valueOf(request.getParameter(name))) + "<br>");
    }
    out.println("<br>");
%><hr>
</div>
<%!
    List<ObjectError> getAllErrors(PageContext pageContext)
    {
        List<ObjectError> l = new ArrayList<ObjectError>();

        Enumeration e = pageContext.getAttributeNamesInScope(PageContext.REQUEST_SCOPE);
        while (e.hasMoreElements())
        {
            String s = (String) e.nextElement();
            if (s.startsWith(BindingResult.MODEL_KEY_PREFIX))
            {
                Object o = pageContext.getAttribute(s, PageContext.REQUEST_SCOPE);
                if (!(o instanceof Errors))
                    continue;
                l.addAll(((BindingResult) o).getAllErrors());
            }
        }
        return l;
    }
%>