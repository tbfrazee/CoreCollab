<%@ page import="org.labkey.api.query.QueryDefinition"%>
<%@ page import="org.labkey.api.query.QueryService"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.study.Dataset"%>
<%@ page import="org.labkey.api.study.StudyService"%>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.labkey.corecollab.CoreCollabController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<Map<String, Object>> me = (JspView<Map<String, Object>>)HttpView.currentView();
    Map<String, Object> params = me.getModelBean();
    Errors errors = getErrors("form");

    String folderType = (String) params.get("folderType");
    if(folderType == null)
        folderType = "NONE";
    String customizerPath = (String) params.get("customizer");
    List<Integer> datasetIds = params.get("sharedDatasets") == null ? new ArrayList<>() : (List<Integer>) params.get("sharedDatasets");
    Map<String, Integer> sharedQueries = params.get("sharedQueries") == null ? new HashMap<>() : (Map<String, Integer>) params.get("sharedQueries");
    String responseText = params.get("response") == null ? "" : (String) params.get("response");
%>

<script type="application/javascript">
    var link = document.createElement('link')
    link.setAttribute('rel', 'stylesheet')
    link.setAttribute('type', 'text/css')
    link.setAttribute('href', LABKEY.ActionURL.getContextPath() + '/corecollab/css/popup_help.css')
    document.getElementsByTagName('head')[0].appendChild(link)
</script>

<!--<link id="csslink" href="/labkey/corecollab/css/popup_help.css" rel="stylesheet" >-->

<labkey:form action="corecollab-CCFolderSettings.post" method="POST">
    <% if(errors.hasErrors())
    { %>
        <div id="errors" style="color: red; border: 1px solid red;">
            <div id="errorHeader" style="background-color: red; color: white;">
                <%= errors.getErrorCount() %> errors reported.
            </div>
            <div id="errorDetail">
                <% for(ObjectError e : errors.getAllErrors())
                { %>
                    <%= e.getDefaultMessage() %>
                    <br />
                <% } %>
            </div>
        </div>
    <% } %>

    <% if(!responseText.equals(""))
    { %>
        <div id="responseText" class="lk-text-theme-light>
            <%= responseText %>
        </div>
    <% } %>

    <div id="folderTypeDiv">
        <h3 style="display: inline-block;">Select folder type:</h3>
        <span id="folderTypeHelp" class="popup-tooltip-icon lk-text-theme-light lk-border-theme-light">?</span>
        <div class="popup-tooltip-container">
            <div class="popup-tooltip-text">This folder's type determines how it interacts with other collaborating folders.<br /><b>Core:</b>&nbsp;&nbsp;Selected datasets and queries are shared with all collaborating folders. <i>There can only be one Core folder.</i><br /><b>Team:</b>&nbsp;&nbsp;Selected datasets and queries from the Core and all Lab folders are shared with the Team folder. <i>There can only be one Team folder.</i><br /><b>Lab:</b>&nbsp;&nbsp;Lab folders have access to shared Core data and share selected datasets and queries with the Team folder.</div>
        </div>
        <br />
        <fieldset>
            <select name="folderType">
                <option <%if(folderType.equals("NONE")){%>selected="selected"<%}%> value="NONE">None</option>
                <option <%if(folderType.equals("CORE")){%>selected="selected"<%}%> value="CORE">Core</option>
                <option <%if(folderType.equals("TEAM")){%>selected="selected"<%}%> value="TEAM">Team</option>
                <option <%if(folderType.equals("LAB")){%>selected="selected"<%}%> value="LAB">Lab</option>
            </select>
        </fieldset>
    </div>

    <% if(folderType.equals("TEAM"))
    { %>
        <div id="customizerDiv">
            <div>
                <h3 style="display: inline-block;">Enter Java customizer class name:</h3>
                <span id="customizerHelp" class="popup-tooltip-icon lk-text-theme-light lk-border-theme-light">?</span>
                <div class="popup-tooltip-container">
                    <div class="popup-tooltip-text">ADVANCED:<br /><br />If you have another Java module activated in this folder that includes a customizer class, you can enter its fully-qualified class name here to have that customizer applied. In order to include data from the Core and Lab folders, it should extend from org.labkey.api.corecollab.AbstractCoreCollabCustomizer.<br /><br />If you leave this blank, it will use the default CoreCollab customizer, which adds all the CoreCollab sharing features you'd expect (and nothing more).</div>
                </div>
            </div>
            <input type="text" name="customizer" size="50" <%if(customizerPath != null){%>value=<%=customizerPath%><%}%> >
        </div>
    <% } %>

    <% if(!folderType.equals("NONE") && !folderType.equals("TEAM"))
    { %>
        <div id="sharedDatasetsDiv">
            <h3 style="display: inline-block;">Select Datasets to share with collaborators:</h3>
            <span id="sharedDatasetsHelp" class="popup-tooltip-icon lk-text-theme-light lk-border-theme-light">?</span>
            <div class="popup-tooltip-container">
                <div class="popup-tooltip-text">Only selected datasets are shared with other collaborating folders. Collaborators can then join their data to yours in their lab datasets, or in the Team folder.</div>
            </div>
            <br />
            <fieldset>
            <%

                for(Dataset ds : StudyService.get().getStudy(getContainer()).getDatasets())
                { %>
                    <label><input type="checkbox" name="sharedData" value=
                        <%= ds.getDatasetId() %>
                        <% if(datasetIds.contains(ds.getDatasetId()))
                        { %>
                            checked
                        <%}%>
                    >
                        <%= ds.getLabel() %>
                    &nbsp;

                    </label><br />
                <% }
            %>
            </fieldset>
        </div>

        <!--
            Allows users to join custom queries to CoreCollabAutoJoinTable
            It requires a query be selected and a Primary Dataset be selected for each selected query
            The primary dataset is the dataset overlaying the real table from which participantId and date can be reliably extracted for that query
            If a query does not have a primary dataset or does not include participantId and date, it will be ignored by CoreCollabAutoJoinTable even if selected.
        -->
        <div id="sharedQueryDiv">
            <h3 style="display: inline-block;">Select Queries to share with collaborators:</h3>
            <span id="sharedDatasetsHelp" class="popup-tooltip-icon lk-text-theme-light lk-border-theme-light">?</span>
            <div class="popup-tooltip-container">
                <div class="popup-tooltip-text">Only selected queries are shared with other collaborating folders.<br/><br/>For each query you select to share, you must select a primary dataset. This should be the dataset from which the query gets its date and participant ID fields, both of which must exist in the database. At this time, calculated date and participant ID fields are not supported.</div>
            </div>
            <br />
            <fieldset>
            <table>
                <%
                    int ct = 0;
                    for(Map.Entry<String, QueryDefinition> entry : (QueryService.get().getQueryDefs(getUser(), getContainer(), "study")).entrySet())
                    {
                        QueryDefinition qd = entry.getValue();
                        if(!qd.isHidden()) { %>

						<tr>
							<td style="padding-bottom: 1em;">
								<label><input type="checkbox" name="sharedQueries" value="<%= qd.getName() %>"
									<% if(sharedQueries.containsKey(qd.getName()))
									{ %>
										checked
									<%}%>
								>
									<%= qd.getName() %>
								&nbsp;
								</label>
							</td>
							<td style="padding-bottom: 1em;">
								<select name="queryDatasets[<%=ct++%>]">
									<option value="0">Select Primary Dataset</option>
									<% for(Dataset ds : StudyService.get().getStudy(getContainer()).getDatasets())
									{ %>
										<option value='<%= qd.getName() + ":" + ds.getDatasetId() %>' <% if(sharedQueries.containsKey(qd.getName()) && sharedQueries.get(qd.getName()) == ds.getDatasetId()){%>selected<%}%> ><%= ds.getLabel() %></option>
									<% } %>
								</select>
							</td>
						</tr>
                    <% }}
                %>
            </table>

            </fieldset>
        </div>

    <% } %>
    <br />
    <div id="submitBtn">
        <%= button("Save").submit(true) %>
    </div>
</labkey:form>