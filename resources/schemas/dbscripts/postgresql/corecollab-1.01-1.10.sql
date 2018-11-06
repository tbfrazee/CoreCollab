CREATE TABLE corecollab.shared_queries
(
    Container ENTITYID NOT NULL,
    QueryNames VARCHAR(255) NOT NULL,
    Created TIMESTAMP,
    CreatedBy USERID,
    Modified TIMESTAMP,
    ModifiedBy USERID,

    CONSTRAINT PK_shared_queries PRIMARY KEY (Container)
);