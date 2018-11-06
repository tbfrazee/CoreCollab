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

CREATE TABLE corecollab.shared_queries
(
    Container ENTITYID NOT NULL,
    QueryNames VARCHAR(255) NOT NULL,
    PrimaryDatasets VARCHAR(255) NOT NULL,
    Created TIMESTAMP,
    CreatedBy USERID,
    Modified TIMESTAMP,
    ModifiedBy USERID,

    CONSTRAINT PK_shared_queries PRIMARY KEY (Container)
);

CREATE TABLE corecollab.team_query_customizers
(
    Container ENTITYID NOT NULL,
    Customizer VARCHAR(255) NOT NULL,
    Created TIMESTAMP,
    CreatedBy USERID,
    Modified TIMESTAMP,
    ModifiedBy USERID,

    CONSTRAINT PK_team_query_customizers PRIMARY KEY (Container)
);