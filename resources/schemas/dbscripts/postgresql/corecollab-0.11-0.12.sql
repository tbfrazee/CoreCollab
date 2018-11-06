CREATE SCHEMA corecollab;

CREATE TABLE corecollab.folder_registration
(
    Container ENTITYID NOT NULL,
    Type VARCHAR(255) NOT NULL,
    Created TIMESTAMP,
    CreatedBy USERID,
    Modified TIMESTAMP,
    ModifiedBy USERID,

    CONSTRAINT PK_folder_registration PRIMARY KEY (Container)
);