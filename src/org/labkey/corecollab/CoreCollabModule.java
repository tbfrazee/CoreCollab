/*
 * Copyright (c) 2017 LabKey Corporation
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

package org.labkey.corecollab;

import org.labkey.api.corecollab.CoreCollabService;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.labkey.api.view.WebPartView;
import org.labkey.corecollab.view.CCFolderSettingsWebPart;
import org.labkey.corecollab.view.CCNewTeamQueryLinkWebPart;

public class CoreCollabModule extends DefaultModule
{
    public static final String NAME = "CoreCollab";

    @Override
    protected void init()
    {
        CoreCollabServiceImpl serviceImpl = new CoreCollabServiceImpl();
        CoreCollabService.setInstance(serviceImpl);

        addController(CoreCollabController.NAME, CoreCollabController.class);
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 1.20;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        List<WebPartFactory> webParts = new ArrayList<>();

        //Team Query Link webpart - a simple button that links to the new shared team query view
        webParts.add(
            new BaseWebPartFactory("New CoreCollab Team Query", WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
            {
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
                {
                    if(!portalCtx.hasPermission(portalCtx.getUser(), InsertPermission.class))
                        return new HtmlView("Views", "You do not have permission to create shared query views.");

                    return new CCNewTeamQueryLinkWebPart();
                }
            }
        );

        //CCFolderSettings webpart
        webParts.add(
            new BaseWebPartFactory("CoreCollab Folder Settings", WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
            {
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
                {
                    if(!portalCtx.hasPermission(portalCtx.getUser(), AdminPermission.class))
                        return new HtmlView("Views", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to change folder settings.");

                    return new CCFolderSettingsWebPart();
                }
            }
        );

        return webParts;
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        //Register folders referenced in the corecollab.folder_registration table with CoreCollabService
        CoreCollabManager.get().registerCCFolders();

        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new CoreCollabContainerListener());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(CoreCollabSchema.NAME);
    }

    protected void registerSchemas()
    {
        CoreCollabUserSchema.register(this);
    }
}