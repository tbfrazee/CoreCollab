<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="org.labkey.corecollab.CoreCollabController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ActionURL CCNewTeamQueryLink = new ActionURL(CoreCollabController.CreateTeamQueryAction.class, getContainer());
%>

<script type="application/javascript">
    var link = document.createElement('link')
    link.setAttribute('rel', 'stylesheet')
    link.setAttribute('type', 'text/css')
    link.setAttribute('href', LABKEY.ActionURL.getContextPath() + '/corecollab/css/popup_help.css')
    document.getElementsByTagName('head')[0].appendChild(link)
</script>

<div id="linkdiv">
    <span id="linkspan"></span>
        &nbsp;
    <span id="helpspan" class="tooltip">
        ?
        <span class="tooltiptext">
            This feature creates both a query and query report that is shared with all other users. Use this to create reports that combine data from all shared studies and datasets.
        </span>
    </span>
</div>

<script type="Application/javascript">
    Ext4.create('Ext.Button', {
        text: 'Click Here to Create a New Shared Query',
        renderTo: "linkspan",
        handler: function() {
            window.location = "<%= CCNewTeamQueryLink %>"
        }
    });
</script>