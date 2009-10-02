<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.action.ConfirmAction"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.springframework.beans.PropertyValue" %>
<%@ page import="org.springframework.beans.PropertyValues" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    JspView me = (JspView) HttpView.currentView();
    ViewContext context = me.getViewContext();
    ConfirmAction confirmAction = (ConfirmAction) context.get(ConfirmAction.CONFIRMACTION);
    PropertyValues propertyValues = confirmAction.getPropertyValues();
    URLHelper cancelUrl = confirmAction.getCancelUrl();
%>
<form action="<%=context.getActionURL().getAction()%>.post" method="POST"><%
    me.include(me.getBody(), out);
    writePropertyValues(out, propertyValues);

    %>
<br><%=PageFlowUtil.generateSubmitButton(confirmAction.getConfirmText())%>&nbsp;<%
    if (null != cancelUrl)
    {
        %><%=PageFlowUtil.generateButton(confirmAction.getCancelText(), cancelUrl)%><%
    }
    else if (confirmAction.isPopupConfirmation())
    {
        %><%=PageFlowUtil.generateSubmitButton(confirmAction.getCancelText(), "window.close(); return false;")%><%
    }
    else
    {
        %><%=PageFlowUtil.generateSubmitButton(confirmAction.getCancelText(), "window.history.back(); return false;")%><%
    }
%></form>
<%!
    void writePropertyValues(JspWriter out, PropertyValues pvs) throws IOException
    {
        if (pvs == null)
            return;
        for (PropertyValue pv : pvs.getPropertyValues())
        {
            String name = pv.getName();
            Object v = pv.getValue();
            if (v == null)
                continue;
            if (v.getClass().isArray()) // only object arrays
                v = Arrays.asList((Object[]) v);
            if (v instanceof Collection)
            {
                for (Object o : (Collection) v)
                {
                    writeHidden(out, name, o);
                }
            }
            else
            {
                writeHidden(out, name, v);
            }
        }
    }

    void writeHidden(JspWriter out, String name, Object v) throws IOException
    {
        out.print("<input type=\"hidden\" name=\"");
        out.print(PageFlowUtil.filter(name));
        out.print("\" value=\"");
        out.print(PageFlowUtil.filter(String.valueOf(v)));
        out.print("\">");
    }
%>