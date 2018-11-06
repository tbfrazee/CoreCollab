package org.labkey.corecollab;

import org.labkey.api.corecollab.AbstractCoreCollabCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FilteredTable;

/**
 * Default implementation of AbstractCoreCollabCustomizer
 * Nothing special - just add the CoreCollabAutoJoinTable and move on.
 */

public class DeafultCoreCollabCustomizer extends AbstractCoreCollabCustomizer
{
    public void customizeAfterAutoJoin(TableInfo ti) {}

    public void customizeSubTable(TableInfo sourceTable, TableInfo parentTable, FilteredTable childTable) {}

}
