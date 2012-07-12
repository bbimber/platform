<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.view.AssayBaseWebPartFactory" %>
<%@ page import="org.labkey.study.view.AssayBaseWebPartFactory.EditViewBean" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayBaseWebPartFactory.EditViewBean> me = (JspView<EditViewBean>) HttpView.currentView();
    EditViewBean bean = me.getModelBean();
    Portal.WebPart webPart = bean.webPart;
    ViewContext ctx = me.getViewContext();
    ActionURL postUrl = webPart.getCustomizePostURL(ctx);
    Integer viewProtocolId = AssayBaseWebPartFactory.getProtocolId(webPart);

    // show buttons should be checked by default for a new assay details webpart.  Otherwise, we preserve the persisted setting:
    boolean showButtons = true;
    if (viewProtocolId != null)
    {
        showButtons = Boolean.parseBoolean(webPart.getPropertyMap().get(AssayBaseWebPartFactory.SHOW_BUTTONS_KEY));
    }

    Map<String, Integer> nameToId = new TreeMap<String, Integer>();
    for (ExpProtocol protocol : AssayService.get().getAssayProtocols(ctx.getContainer()))
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        nameToId.put(provider.getName() + ": " + protocol.getName(), protocol.getRowId());
    }
%>
<p><%=bean.description%></p>

<form action="<%=postUrl%>" method="post">
    <table>
        <tr>
            <td class="labkey-form-label">Assay</td>
            <td>
                <select name="<%=AssayBaseWebPartFactory.PROTOCOL_ID_KEY %>">
                    <%
                        for (Map.Entry<String, Integer> entry : nameToId.entrySet())
                        {
                    %>
                         <option value="<%= entry.getValue() %>" <%=entry.getValue().equals(viewProtocolId) ? "SELECTED" : ""%>>
                            <%= h(entry.getKey()) %></option>
                    <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Show buttons in web part</td>
            <td><input type="checkbox" name="<%=AssayBaseWebPartFactory.SHOW_BUTTONS_KEY%>" value="true" <%= showButtons ? "CHECKED" : "" %>></td>
        </tr>
        <tr>
            <td/>
            <td><%=generateSubmitButton("Submit")%> <%=generateButton("Cancel", ctx.getContainer().getStartURL(ctx.getUser()))%></td>
        </tr>
    </table>
</form>