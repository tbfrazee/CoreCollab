package org.labkey.corecollab;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class CoreCollabUserSchema extends SimpleUserSchema
{

    private CoreCollabUserSchema(User user, Container container, DbSchema schema)
    {
        super("study", null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = DbSchema.get("study", DbSchemaType.Module);
        DefaultSchema.registerProvider("study", new DefaultSchema.SchemaProvider(m)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new CoreCollabUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    public Map<String, AbstractTableInfo> getCoreTables()
    {
        List<QueryDefinition> TaQs = getTablesAndQueries(true);
        Map<String, AbstractTableInfo> ret = new HashMap<>();
        List<QueryException> errors = new ArrayList<>();
        for(QueryDefinition qd : TaQs)
        {
            AbstractTableInfo table = (AbstractTableInfo)qd.getTable(errors, true);
            ret.put(table.getName(), table);
        }
        return ret;
    }



}