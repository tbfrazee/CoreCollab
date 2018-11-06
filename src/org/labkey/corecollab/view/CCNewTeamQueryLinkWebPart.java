package org.labkey.corecollab.view;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.corecollab.CoreCollabController;
import org.labkey.corecollab.CoreCollabManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by frazee on 7/17/17.
 */

public class CCNewTeamQueryLinkWebPart extends JspView
{

    public CCNewTeamQueryLinkWebPart()
    {
        super("/org/labkey/corecollab/view/CCNewTeamQueryLink.jsp", null);

        setTitle("Create New Shared Query");
        setTitleHref(new ActionURL(CoreCollabController.CreateTeamQueryAction.class, getViewContext().getContainer()));

    }
}
