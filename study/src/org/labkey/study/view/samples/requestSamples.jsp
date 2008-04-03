<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController"%>
<%@ page import="org.labkey.study.model.Site"%>
<%@ page import="org.labkey.study.model.StudyManager"%>
<%@ page import="org.labkey.study.model.Specimen"%>
<%@ page import="org.labkey.study.SampleManager"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="java.util.Map"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.NewRequestBean> me = (JspView<SpringSpecimenController.NewRequestBean>) HttpView.currentView();
    SpringSpecimenController.NewRequestBean bean = me.getModelBean();
    ViewContext context = me.getViewContext();
    Site[] sites = StudyManager.getInstance().getSites(context.getContainer());
    boolean shoppingCart = SampleManager.getInstance().isSpecimenShoppingCartEnabled(context.getContainer());
    Specimen[] specimens = bean.getSamples();
    SampleManager.SpecimenRequestInput[] inputs = bean.getInputs();
%>
<span class="labkey-error">
    <%
        BindException errors = bean.getErrors();
        if (errors != null)
        {
            for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
            {
                %><%=PageFlowUtil.filter(HttpView.currentContext().getMessage(e))%><br><%
            }
        }
    %>
</span>

<script type="text/javascript">
var LastSetValues = new Object();
var DefaultValues = new Object();
    <%
    for (int i = 0; i < inputs.length; i++)
    {
        SampleManager.SpecimenRequestInput input = inputs[i];
        if (input.isRememberSiteValue())
        {
    %>
LastSetValues['input<%= i %>'] = '';
DefaultValues['input<%= i %>'] = new Object();
    <%
            Map<Integer, String> defaults = input.getDefaultSiteValues(context.getContainer());
            for (Map.Entry<Integer,String> entry : defaults.entrySet())
            {
    %>DefaultValues['input<%= i %>']['<%= entry.getKey() %>'] = <%= PageFlowUtil.jsString(entry.getValue()) %>;
    <%
            }
        }
    }
    %>

function setDefaults()
{
    var siteId = document.getElementById('destinationSite').value;
    for (var elementId in DefaultValues)
    {
        var elem = document.getElementById(elementId);
        var value = DefaultValues[elementId][siteId];
        if (value && (!elem.value || elem.value == LastSetValues[elementId]))
        {
            elem.value = value;
            LastSetValues[elementId] = elem.value;
        }
    }
    return true;
}
</script>
<form action="handleCreateSampleRequest.post" method="POST">
    <%
        if (specimens != null)
        {
            for (Specimen specimen : specimens)
            {
    %>
    <input type="hidden" name="sampleIds" value="<%= specimen.getRowId() %>">
    <%
            }
        }
    %>
    <table class="normal">
    <%
        if (shoppingCart)
        {
    %>
        <tr>
            <td>Please fill out this form to create a new specimen request.  You will have the chance to add or remove specimens before the request is submitted.</td>
        </tr>
    <%
        }
    %>
        <tr>
            <th align="left">Requesting Location (Required):</th>
        </tr>
        <tr>
            <td>
                <select id='destinationSite' name="destinationSite" onChange="setDefaults()">
                    <option value="0"></option>
                    <%
                        for (Site site : sites)
                        {
                    %>
                    <option value="<%= site.getRowId() %>" <%= bean.getSelectedSite() == site.getRowId() ? "SELECTED" : ""%>>
                        <%= h(site.getDisplayName()) %>
                    </option>
                    <%
                        }
                    %>
                </select>

            </td>
        </tr>
        <%
            for (int i = 0; i < inputs.length; i++)
            {
                SampleManager.SpecimenRequestInput input = inputs[i];
        %>
        <tr>
            <th align="left">
                <input type="hidden" name="required" value="<%= input.isRequired() %>">
                <br><%= h(input.getTitle()) %> <%= input.isRequired() ? "(Required)" : "(Optional)" %>:
            </th>
        </tr>
        <tr>
            <td><%= h(input.getHelpText()) %></td>
        </tr>
        <tr>
            <td>
                <%
                    if (input.isMultiLine())
                    {
                %>
                <textarea rows="5" id="input<%= i %>" cols="50" name="inputs"><%= h(bean.getValue(i)) %></textarea>
                <%
                    }
                    else
                    {
                %>
                <input type="text" id="input<%= i %>" size="40" name="inputs" value="<%= h(bean.getValue(i)) %>">
                <%
                    }
                %>
            </td>
        </tr>
        <%
            }
        %>
        <tr>
            <td>
                <%= buttonImg((shoppingCart ? "Create" : "Submit") + " Request")%>&nbsp;
                <%= buttonLink("Cancel", "viewRequests.view")%>
            </td>
        </tr>


        <%
            if (specimens != null)
            {
        %>
            <tr>
                <th align="left">Selected Specimens:</th>
            </tr>
            <tr>
                <td><% me.include(bean.getSpecimenQueryView(), out); %></td>
            </tr>
        <%
            }
        %>

    </table>
</form>