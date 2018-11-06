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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;

public class CoreCollabSchema
{
    private static final CoreCollabSchema _instance = new CoreCollabSchema();
    public static final String NAME = "corecollab";

    public static final String REG_TABLE = "folder_registration";
    public static final String TEAM_QUERY_CUSTOMIZER_TABLE = "team_query_customizers";
    public static final String SHARED_DATASET_TABLE = "shared_datasets";
    public static final String SHARED_QUERY_TABLE = "shared_queries";

    public static CoreCollabSchema getInstance()
    {
        return _instance;
    }

    private CoreCollabSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.corecollab.CoreCollabSchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(NAME, DbSchemaType.Module);
    }

    public TableInfo getTable(String table)
    {
        return getSchema().getTable(table);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
