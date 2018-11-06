//CoreCollaboration Service

package org.labkey.api.corecollab;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.study.Study;

import java.util.List;
import java.util.Map;

/**
 * Service class for the CoreCollab module
 * Provides outside modules the ability to register and release folders within the CoreCollab system.
 * Also provides access to shared datasets and queries, as well as CoreCollabAutoJoinTables
 */

abstract public class CoreCollabService
{
    static CoreCollabService instance;

    public static CoreCollabService get(){return instance;}
    static public void setInstance(CoreCollabService instance)
    {
        CoreCollabService.instance = instance;
    }

    /**
     * Register a folder as the site's Core folder.
     * Only one Core folder can be registered at any one time.
     * @param container The folder to register as Core
     */
    abstract public void registerCoreFolder(Container container);

    /**
     * Unregisters the Core folder.
     */
    abstract public void releaseCoreFolder();

    /**
     * Registers a container as a Lab folder.
     * Lab folders have access to Core data and can share their data with the Team folder.
     * @param container The folder to add as a Lab
     */
    abstract public void registerLabFolder(Container container);

    /**
     * Unregisters a folder as a Lab folder.
     * This folder will no longer participate in the CoreCollab environment.
     * @param container The folder to unregister.
     */
    abstract public void releaseLabFolder(Container container);

    /**
     * Unregisters all Lab folders.
     */
    abstract public void releaseAllLabFolders();

    /**
     * Registers a folder as the Team folder.
     * There can only be one Team folder at any given time.
     * Data from the Core folder and Lab folders are shared with the Team folder.
     * @param container The folder to register as the Team folder.
     */
    abstract public void registerTeamFolder(Container container);

    /**
     * Unregister the Team folder.
     */
    abstract public void releaseTeamFolder();

    /**
     * Determines whether minimum folder registrations and setups exist for CoreCollab to function.
     * @return true if CoreCollab can function, else false.
     */
    abstract public boolean validateMinimumConfig();

    /**
     * Gets the currently registered Core folder
     * @return the Core container or null
     */
    abstract public Container getCoreFolder();

    /**
     * Gets the currently registered Team folder
     * @return the Team container or null
     */
    abstract public Container getTeamFolder();

    /**
     * Gets a list of all currently registered Lab folders
     * @return an ArrayList of Lab containers or an empty ArrayList
     */
    abstract public List<Container> getLabFolders();

    /**
     * Gets the Study from the Core container
     * @return The Study from the Core container or null
     */
    abstract public Study getCoreStudy();

    /**
     * Gets the Study from the Team container
     * @return The study from the Team container or null
     */
    abstract public Study getTeamStudy();

    /**
     * Gets the Study from a given Lab container
     * @param ctr The registered Lab folder
     * @return The Study from ctr or null
     */
    abstract public Study getLabStudy(Container ctr);

    /**
     * Finds if the Core Study is a Dataspace study.
     * This affects some of the internal joins, and may affect how other folders need to interact with the Core.
     * @return true if the Core Study is a Dataspace, else false
     */
    abstract public boolean isCoreDataspace();

    /**
     * Gets a List of IDs of Datasets that have been shared from a registered folder (Core or Lab)
     * @param c The folder to check
     * @return An ArrayList of Integers representing shared Dataset IDs, or an empty list
     */
    abstract public List<Integer> getSharedDatasets(Container c);

    /**
     * Gets a List of Query names that have been shared from a registered folder (Core or Lab)
     * @param c The folder to check
     * @return An ArrayList of Strings representing shared Query names, or an empty list
     */
    abstract public List<String> getSharedQueries(Container c);

    /**
     * Gets a Map of shared Query names and primary Dataset IDs from a registered folder (Core or Lab)
     * Map<String QueryName, Integer DatasetID>
     * @param c The folder to check
     * @return A HashMap containing each shared Query name as keys and corresponding primary Dataset IDs as values, or an empty map
     */
    abstract public Map<String, Integer> getSharedQueryMap(Container c);

    /**
     * Adds a CoreCollabAutoJoinTable to a provided AbstractTableInfo.
     * @param table The AbstractTableInfo to join the CoreCollabAutoJoinTable to
     * @param dateColName The String field name of the date column of the above TableInfo
     * @param containerToJoin The container to be joined via the CoreCollabAutoJoinTable. If null, the Core folder will be joined.
     * @param joinType The CCJoinType to use to join the containerToJoin. This is usually set in an extension of AbstractCoreCollabCustomizer. If null, defaults to DATE.
     * @param customizer An extension of AbstractCoreCollabCustomizer. The customizeAfterAutoJoin and customizeSubTable methods of this object will be called to customize joined tables and queries. If null, these methods will not be called.
     * @return The ColumnInfo with the CoreCollabAutoJoinTable as its ForeignKey
     */
    abstract public ColumnInfo addCCAutoJoinTable(AbstractTableInfo table, String dateColName, @Nullable Container containerToJoin, @Nullable CCJoinType joinType, @Nullable AbstractCoreCollabCustomizer customizer);

    //All methods below are convenience overloads of addCCAutoJoinTable above, potentially excluding optional @Nullable params.

    /**
     * Joins Core folder on DATE with no customizer
     */
    abstract public ColumnInfo addCoreAutoJoinTable(AbstractTableInfo ti, String dateColName);

    /**
     * Joins containerToJoin on DATE with no customizer
     */
    abstract public ColumnInfo addCCAutoJoinTable(AbstractTableInfo ti, String dateColName, @Nullable Container containerToJoin);

    /**
     * Joins Core folder on joinType with no customizer
     */
    abstract public ColumnInfo addCCAutoJoinTable(AbstractTableInfo ti, String dateColName, @Nullable CCJoinType joinType);

    /**
     * Joins containerToJoin on joinType with no customizer
     */
    abstract public ColumnInfo addCCAutoJoinTable(AbstractTableInfo ti, String dateColName, @Nullable Container containerToJoin, @Nullable CCJoinType joinType);

}