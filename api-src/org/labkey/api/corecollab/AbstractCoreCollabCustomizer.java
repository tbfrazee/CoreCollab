package org.labkey.api.corecollab;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserSchema;

/**
 * Base customizer for study data in folders using CoreCollab.
 * Adds CCAutoJoinTables by default to appropriate tables.
 * Customizers of other modules for sites that implement CoreCollab should extend this class.
 * Typically, an extension of this class will be set as the customizer in the folder's StudyData query's metadata XML
 */

public abstract class AbstractCoreCollabCustomizer implements TableCustomizer
{
    /**Establishes how the CCAutoJoinTable joins tables - either by date (table.date = joinedTable.date) or by most recent date (table.date >= joinTable.date)
    * PREV incurs additional cost by adding a subquery to each row to find the proper date to match
    * If you want to change it, this should be set in the constructor of your customizer (subclass of this)
     */
    protected CCJoinType _joinType = CCJoinType.DATE;

    public AbstractCoreCollabCustomizer()
    {

    }

    /**
     * The customize method adds the CoreCollabAutoJoinTable(s) to your tables.
     * Unless you have good reason to, leave this be and put your customizer's implementation in the customizeAfterAutoJoin method.
     * Alternatively, override this and call super(TableInfo ti) before your own implementation.
     * @param ti The table to be customized
     */
    public void customize(TableInfo ti)
    {
        if(ti instanceof AbstractTableInfo)
        {
            UserSchema schema = ti.getUserSchema();
            Container c = schema != null ? schema.getContainer() : null;
            if(c != null)
            {
                //Determine which containers should be joined
                //If source container is Core, join Core only (minus self)
                //If source container is Lab, join Core only
                //If source container is Team, join Core and all Labs
                if ((CoreCollabService.get().getTeamFolder() != null && CoreCollabService.get().getTeamFolder().equals(ti.getUserSchema().getContainer()))
                        || CoreCollabService.get().getLabFolders().contains(ti.getUserSchema().getContainer()))
                {
                    CoreCollabService.get().addCCAutoJoinTable((AbstractTableInfo) ti, "date", null, _joinType, this);
                    if (CoreCollabService.get().getTeamFolder() != null && CoreCollabService.get().getTeamFolder().equals(ti.getUserSchema().getContainer()))
                    {
                        for (Container lab : CoreCollabService.get().getLabFolders())
                        {
                            CoreCollabService.get().addCCAutoJoinTable((AbstractTableInfo) ti, "date", lab, _joinType, this);
                        }
                    }
                }
            }
        }
        customizeAfterAutoJoin(ti);
    }

    /**
     * Override this method in your own extension of this class in order to customize the base table. This will be called after the CoreCollabAutoJoinTable has been added.
     * This does not customize tables joined to CoreCollabAutoJoinTable - for that, use customizeSubTable.
     * @param table The table to be customized after the CoreCollabAutoJoinTable has been added
     */
    public abstract void customizeAfterAutoJoin(TableInfo table);

    /**
     * This function is called for each table joined to the CoreCollabAutoJoinTable that is added to the source table.
     * @param sourceTable The base table that initialized the customizer. Typically the table representing a dataset or query that has been called up by a user.
     * @param parentTable The CoreCollabAutoJoinTable that is the parent to the childTable.
     * @param childTable The table being added to the CoreCollabAutoJoinTable. This method is called once for each childTable on the parentTable.
     */
    public abstract void customizeSubTable(TableInfo sourceTable, TableInfo parentTable, FilteredTable childTable);
}
