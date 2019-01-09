# CoreCollab - A LabKey Module for Collaborators Using a Central Resource Folder

## Overview
CoreCollab is a LabKey module that allows selected datasets and queries to be shared seamlessly and joined to queries in other folders. It is built around the idea of a "Core" folder that contains data to be shared with all other folders, numerous "Lab" folders that contain their own data, and a "Collaboration" folder that brings all the data together.

## Installation

## Usage
Each folder that is going to share or join shared data must have a defined CoreCollab Folder Type. This can be defined using the CoreCollab Folder Settings (`CCFolderSettings`) view. Once folder types and shared datasets are set up, shared datasets should show up (sorted by folder) in the Grid Views menu.

### CoreCollab Folder Settings
You can access the CoreCollab Folder Settings view by navigating to:

    YourLabKeyDomain/Folder/corecollab-ccfoldersettings.view
    
From this page, you can set the following settings:

**Folder type**
* Core: The Core folder is shared with all Lab folders and the Collaboration folder. There can be only 1 Core folder.
* Lab: Lab folders share their shared datasets/queries with the Collaboration folder. There can be any number of Lab folders.
* Team: The Team folder is where shared dataset/queries from the Core folder and Lab folders comes together. Everyone who should be able to view this data should have read/write permissions to this folder. Collaborators can create grid views in the Team folder and join all shared data together to create unique, combined data views. There can be only 1 Team folder.

**Shared Datasets**

Selected datasets are shared according to the folder types above.

**Shared Queries**

Shared queries are shared in the same way as shared datasets. A shared query requires that a Primary Dataset by defined for it.  This is the dataset in which the query's participantId and date fields are defined.

**Customizer**
* (Team folder) A Java TableCustomizer class that applies to created Team queries. If not defined, DefaultCoreCollabCustomizer is used. It adds the autojoin tables that enable CoreCollab's table-joining functionality, but nothing else.

## Integration

CoreCollab's customizer can integrate with other module customizers, but it requires a small change to the normal customizer flow.

Your module's TableCustomizer should extent AbstractCoreCollabCustomizer. Do not override the customize method (it is responsible for adding CoreCollabAutoJoinTable). Instead, define the method customizeAfterAutoJoin - AbstractCoreCollabCustomizer will call this method after the auto-join tables are added.

Additionally, you can define a method called customizeSubTable. This allows you to customize shared tables and queries before they're joined to CoreCollabAutoJoinTable. It is called for each table as it is joined.
