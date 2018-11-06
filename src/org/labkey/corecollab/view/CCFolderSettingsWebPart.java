package org.labkey.corecollab.view;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.corecollab.CoreCollabController;

/**
 * Created by frazee on 7/17/17.
 */

public class CCFolderSettingsWebPart extends JspView
{

    public CCFolderSettingsWebPart()
    {
        super("/org/labkey/corecollab/view/CCFolderSettingsLink.jsp", null);

        setTitle("Change CoreCollab Folder Settings");
        setTitleHref(new ActionURL(CoreCollabController.CCFolderSettingsAction.class, getViewContext().getContainer()));

    }
}
