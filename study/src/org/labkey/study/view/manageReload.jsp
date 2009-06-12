<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.study.importer.StudyReload.*" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    boolean allowReload = getStudy().isAllowReload();
    ReloadInterval currentInterval = ReloadInterval.getForSeconds(getStudy().getReloadInterval());
%>
<form action="" method="post">
    <table>
        <tr>
            <td colspan=2>A study can be configured to reload study data from the file system, either manually or automatically at pre-set intervals.</td>
        </tr>
        <tr>
            <td colspan=2>&nbsp;</td>
        </tr>
        <tr>
            <th align="left" width=200>Allow Study Reload</th>
            <td><input name="allowReload" type="checkbox"<%=allowReload ? " checked" : ""%>></td>
        </tr>
        <tr>
            <th align="left" width=200>Reload Interval</th>
            <td align="left"><select name="interval"><%
                for (ReloadInterval interval : ReloadInterval.values())
                    if (interval.shouldDisplay())
                        out.println("<option value=\"" + interval.getSeconds() + "\"" + (interval == currentInterval ? " selected>" : ">") + h(interval.getDropDownLabel()) + "</option>");
            %>
            </select></td>
        </tr>
        <tr>
            <td width=200>&nbsp;</td>
            <td><%= generateSubmitButton("Update")%>&nbsp;<%= generateButton("Cancel", "manageStudy.view")%></td>
        </tr>
    </table>
</form>