CREATE TABLE corecollab.shared_datasets
(
    Container ENTITYID NOT NULL,
    DatasetIds VARCHAR(255) NOT NULL,
    Created TIMESTAMP,
    CreatedBy USERID,
    Modified TIMESTAMP,
    ModifiedBy USERID,

    CONSTRAINT PK_shared_datasets PRIMARY KEY (Container)
);