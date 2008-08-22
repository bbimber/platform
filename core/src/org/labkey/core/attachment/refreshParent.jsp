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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<String> me = (JspView<String>) HttpView.currentView();
    String message = me.getModelBean();
%>
<script type="text/javascript">
if (window.opener && window.opener != window)
    window.opener.location.reload();
</script><%
if (null != message)
    {
    %><%= message %>
        <%=PageFlowUtil.generateButton("Continue", "#continue", "window.close();")%>
<%
    }
else
    {
    %>Close this window. (You shouldn't be seeing this...)
<script type="text/javascript">
window.close();
</script>
<% } %>
