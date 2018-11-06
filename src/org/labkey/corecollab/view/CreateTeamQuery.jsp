<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.security.permissions.ReadPermission"%>
<%@ page import="org.labkey.api.study.Dataset"%>
<%@ page import="org.labkey.api.study.StudyService"%>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.labkey.corecollab.CoreCollabController" %>
<%@ page import="org.labkey.api.corecollab.CoreCollabService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>

<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<CoreCollabController.CreateTeamQueryForm> me = (JspView<CoreCollabController.CreateTeamQueryForm>) HttpView.currentView();

    boolean makeDSDropdown = false;
    String qName = "";
    String folderId = "";
    Container folder = null;
    String strFolder = request.getParameter("folder");
    if(request.getParameter("qName") != null)
        qName = request.getParameter("qName");

    String coreFolderNameHintStr = "";
    Container coreFolder = CoreCollabService.get().getCoreFolder();
    if(coreFolder != null)
        coreFolderNameHintStr = "the " + coreFolder.getTitle() + " and";

    Map<Integer, String> sharedDatasetMap = new HashMap<>();
    List<Integer> sharedIds = new ArrayList<>();

    if(strFolder != null)
    {
        folder = ContainerManager.getForPath(strFolder);
        folderId = folder.getEntityId().toString();
    }

    if(folder != null)
    {
        sharedIds = CoreCollabService.get().getSharedDatasets(folder);

        for(Integer dsid : sharedIds)
        {
            Dataset ds = StudyService.get().getDataset(folder, dsid);
            sharedDatasetMap.put(dsid, ds.getName());
            makeDSDropdown = true;
        }
    }

%>

<script type="Application/Javascript">
    function reloadWithFolder(folder)
    {
        var folderId = folder;
        var newReportName = document.getElementById("qName").value;
        window.location.search = "qName=" + newReportName + " &folder=" + folderId;
    }

    var link = document.createElement('link')
    link.setAttribute('rel', 'stylesheet')
    link.setAttribute('type', 'text/css')
    link.setAttribute('href', LABKEY.ActionURL.getContextPath() + '/corecollab/css/popup_help.css')
    document.getElementsByTagName('head')[0].appendChild(link)
</script>

<table>
    <%
        for (ObjectError e : me.getErrors().getAllErrors())
        {
    %>      <tr><td colspan=3><font class="labkey-error"><%=h(getViewContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<h2 style="display:inline">Create Shared Query Report</h2> <span id="createQueryHelp" class="tooltip">?<span class="tooltiptext"><p>Use this form to create a data report that will be shared with your collaborators.  This report can contain data from all shared datasets from <%= coreFolderNameHintStr %> labs.</p><p>To start, enter a unique name for your report.  Then, you have to choose a shared dataset that will serve as the foundation for your report. To do this, select the folder that contains this dataset, then the dataset name, and then press Save.  Once the report is created, you can join data from other shared datasets using the normal Customize Grid controls and create and save custom views to share.</p></span></span>
<br />
<labkey:form action="corecollab-CreateTeamQuery.post" method="POST">
    <p>Enter a name for your new report:<br />
        <input type="text" id="qName" name="newReportName" value= <%= qName %>>
    </p>
    <p>Select a folder that contains the dataset on which your report will be based:<br />
        <select name="folderName" onchange="reloadWithFolder(this.value)">
            <option value="None">Select folder</option>

            <% if(CoreCollabService.get().getCoreFolder() != null && CoreCollabService.get().getCoreFolder().hasPermission(getUser(), ReadPermission.class))
            { %>
                <option value=<%= CoreCollabService.get().getCoreFolder().getEntityId() %>
                    <% if(folderId.equals(CoreCollabService.get().getCoreFolder().getEntityId().toString()))
                    { %>
                        selected
                    <% } %>
                >
                    <%= CoreCollabService.get().getCoreFolder().getTitle() %>
                </option>
            <% } %>

            <% for(Container c : CoreCollabService.get().getLabFolders())
            {
                if(CoreCollabService.get().getSharedDatasets(c) != null && c.hasPermission(getUser(), ReadPermission.class))
                { %>
                    <option value=<%= c.getEntityId() %>
                    <% if(folderId.equals(c.getEntityId().toString()))
                    { %>
                        selected
                    <% } %>
                    >
                        <%= c.getTitle() %>
                    </option>
                <% }
            } %>

        </select>
    </p>
    <% if(makeDSDropdown)
    { %>
        <p>Select a Dataset on which to base your report:<br />
            <select name="datasetName">
                <% for(Integer key : sharedDatasetMap.keySet())
                { %>
                    <option value=<%= key %>><%= sharedDatasetMap.get(key) %></option>
                <% } %>
            </select>
        </p>
    <% }  else { %>
        <p>Select a Dataset on which to base your report:<br />
            <select disabled>
                <option value="None" selected>Select folder first</option>
            </select>
        </p>
    <% } %>
    <div id="submitBtn">
        <%= button("Save").submit(true) %>
    </div>
</labkey:form>