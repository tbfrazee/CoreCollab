<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="org.labkey.corecollab.CoreCollabController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ActionURL CCFolderSettingsLink = new ActionURL(CoreCollabController.CCFolderSettingsAction.class, getContainer());
%>

<div id="linkdiv"></div>

<script type="Application/javascript">
    Ext4.create('Ext.Button', {
        text: "Click Here to Change Which Datasets You're Sharing",
        renderTo: "linkdiv",
        handler: function() {
            window.location = "<%= CCFolderSettingsLink %>"
        }
    });
</script>