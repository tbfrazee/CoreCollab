package org.labkey.corecollab;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.LookupColumn;

/**
 * Not currently in use
 */

public class CoreCollabAutoJoinColumn extends LookupColumn
{
    String _joinOperator;

    public CoreCollabAutoJoinColumn(ColumnInfo foreignKey, ColumnInfo lookupKey, ColumnInfo lookupColumn, String joinOperator)
    {
        super(foreignKey, lookupKey, lookupColumn);
        _joinOperator = joinOperator;
    }

    /*@Override
    protected SQLFragment getJoinCondition(String tableAliasName, ColumnInfo fk, ColumnInfo pk, boolean equalOrIsNull)
    {

    }*/



}
