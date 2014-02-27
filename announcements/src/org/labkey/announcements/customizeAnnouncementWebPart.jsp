<%
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    Portal.WebPart webPart = (Portal.WebPart)HttpView.currentModel();
    ViewContext context = HttpView.currentContext();
    String selected = StringUtils.defaultString(webPart.getPropertyMap().get("style"), "full");
%>

<form name="frmCustomize" method="post" action="<%=h(webPart.getCustomizePostURL(context))%>">
    <input type="radio" name="style" value="full" <%=text("full".equals(selected)?"checked":"")%>>&nbsp;full<br>
    <input type="radio" name="style"value="simple" <%=text("simple".equals(selected)?"checked":"")%>>&nbsp;simple<br>
    <input type="submit">
</form>
