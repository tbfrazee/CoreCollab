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