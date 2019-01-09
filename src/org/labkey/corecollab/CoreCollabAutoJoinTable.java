/*
* Created by tfrazee
* 5/23/17
* Updated 10/28/17
 */

package org.labkey.corecollab;

import org.jetbrains.annotations.Nullable;

import org.labkey.api.corecollab.AbstractCoreCollabCustomizer;
import org.labkey.api.corecollab.CCJoinType;
import org.labkey.api.corecollab.CoreCollabService;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.data.xml.TableType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates a junction table connecting an active query with all shared CoreCollab queries.
 */

public class CoreCollabAutoJoinTable extends VirtualTable
{
    private static final String PTID_NAME = "participantId";

    private UserSchema _joinedSchema;
    private AbstractTableInfo _source;
    private ColumnInfo _idCol;
    private FieldKey _dateKey;
    private Study _joinedStudy;
    private Container _joinedContainer;

    /**
     * Creates a new CoreCollabAutoJoinTable to be joined to a source query or table.
     * @param source The active table that this should be joined to.
     * @param idCol The source table participant id column, if it exists.
     * @param dateKey The source table date column, if it exists.
     * @param containerToJoin The Container that contains the shared queries that this will join to the source table
     * @param joinType The CCJoinType representing how to join shared data, by date or most recent previous date
     * @param sourceCustomizer The customizer object for the source table. customizeSubTable will be called on this.
     */

    public CoreCollabAutoJoinTable(AbstractTableInfo source, @Nullable ColumnInfo idCol, @Nullable FieldKey dateKey,
                                   @Nullable Container containerToJoin, @Nullable CCJoinType joinType,
                                   @Nullable AbstractCoreCollabCustomizer sourceCustomizer)
    {
        super(DbSchema.get("study", DbSchemaType.Module), "CCAutoJoin");

        StudyService studySvc = StudyService.get();
        if(studySvc == null)
            throw new RuntimeException("Study service class unavailable.");

        _source = source;
        UserSchema _sourceSchema = _source.getUserSchema();
        User user = _sourceSchema != null ? _sourceSchema.getUser() : null;

        if(_sourceSchema == null)
            throw new IllegalArgumentException("Table source does not contain valid schema reference");

        if(containerToJoin == null || containerToJoin.equals(CoreCollabService.get().getCoreFolder()))
        {
            _joinedContainer = CoreCollabService.get().getCoreFolder();
            _joinedStudy = CoreCollabService.get().getCoreStudy();
        }
        else if(CoreCollabService.get().getLabFolders().contains(containerToJoin))
        {
            _joinedContainer = containerToJoin;
            _joinedStudy = CoreCollabService.get().getLabStudy(containerToJoin);
        }

        if(_joinedContainer != null && _joinedStudy != null)
            _joinedSchema = new SimpleUserSchema("study", null, user, _joinedContainer, DbSchema.get("study", DbSchemaType.Module));
        else
            throw new IllegalArgumentException("Joined container is invalid or does not contain a Study.");

        _idCol = idCol;
        _dateKey = dateKey;

        ColumnInfo ptidCol;
        String ptidColName = PTID_NAME;

        if(joinType == null)
            joinType = CCJoinType.DATE;

        if(_idCol != null)
        {
            assert _dateKey != null;

            TableInfo parent = _idCol.getParentTable();

            //If Core study is a Dataspace, add Container column (is this really required?)
            if(CoreCollabService.get().isCoreDataspace())
            {
                ColumnInfo containerCol = new AliasedColumn(parent, "Container", parent.getColumn("Container"));
                addColumn(containerCol);
            }

            ptidCol = parent.getColumn(PTID_NAME);

            if(ptidCol == null) {
                UserSchema parentSchema = parent.getUserSchema();
                Container parentContainer = parentSchema != null ? parentSchema.getContainer() : null;
                ptidCol = parentContainer != null ? parent.getColumn(studySvc.getSubjectColumnName(parentContainer)) : null;
            }

            if(ptidCol != null)
            {
                ptidColName = ptidCol.getName();
                ColumnInfo localIdCol = new AliasedColumn(parent, ptidCol.getName(), ptidCol);
                addColumn(localIdCol);
            } else
                throw new QueryException("Participant Id column not found.");

            //Get the date column and add it to the AutoJoinTable
            ColumnInfo dateCol = new AliasedColumn(parent, _dateKey.getName(), parent.getColumn(_dateKey.getName()));
            addColumn(dateCol);
        }

        Set<FieldKey> defaultVisible = new LinkedHashSet<>();
        List _datasets = _joinedStudy.getDatasets();
        List<Integer> sharedDatasets = CoreCollabManager.get().getSharedDatasets(_joinedContainer);

        //Get shared Dataset tables to join
        for(Object dsObj : _datasets)
        {
            try
            {
                //Object should be extension of Dataset, but catch and continue if something weird gets through
                Dataset ds = (Dataset) dsObj;
                if(sharedDatasets.contains(ds.getDatasetId()))
                {
                    if (ds.canRead(_sourceSchema.getUser()))
                    {
                        //This returns a DatasetSchemaTableInfo
                        TableInfo datasetTable = ds.getTableInfo(_sourceSchema.getUser(), true, true);

                        CCFilteredTable filteredTable = new CCFilteredTable(datasetTable);
                        if(sourceCustomizer != null)
                            sourceCustomizer.customizeSubTable(source, this, filteredTable);

                        ColumnInfo newCol = makeColumn(filteredTable, ptidColName, ds.isDemographicData(), joinType);
                        if (newCol != null)
                        {
                            addColumn(newCol);
                            defaultVisible.add(FieldKey.fromParts(newCol.getName()));
                        }
                    }
                }
            }
            catch (Exception ignored){}
        }

        //Get shared user-defined queries to join
        //Requires that a "Primary Dataset" has been defined for each shared query in CCFolderSettings
        Map<String, Integer> sharedQueries = CoreCollabManager.get().getSharedQueryMap(_joinedContainer);
        for(String qName : sharedQueries.keySet())
        {
            try
            {
                QueryDefinition qd = _joinedSchema.getQueryDef(qName);
                Dataset ds = _joinedStudy.getDataset(sharedQueries.get(qName));
                if(qd != null && ds != null && ds.canRead(_sourceSchema.getUser()))
                {
                    List<QueryException> errors = new ArrayList<>();
                    TableInfo queryTable = qd.getTable(errors, true);
                    CCFilteredTable filteredTable = new CCFilteredTable(queryTable);

                    ColumnInfo newCol = makeColumn(filteredTable, ds.getName(), ptidColName, ds.isDemographicData(), joinType);
                    if(newCol != null)
                    {
                        addColumn(newCol);
                        defaultVisible.add(FieldKey.fromParts(newCol.getName()));
                    }
                }
            }
            catch(Exception ignored){}
        }

        setDefaultVisibleColumns(defaultVisible);

    }

    private ColumnInfo makeColumn(FilteredTable foreignTable, String ptidColName, boolean isDemographic, CCJoinType joinType)
    {
        return makeColumn(foreignTable, null, ptidColName, isDemographic, joinType);
    }

    /**
     * Makes a column joining to a foreign table
     * @param foreignTable Table to be joined
     * @param foreignTableName Name of table to be joined, if known
     * @param ptidColName Source table participant id column name
     * @param isDemographic True if source is demographic data (no date column)
     * @param joinType CCJoinType to determine if foreign table is joined on exact date, or most recent previous date
     * @return A ColumnInfo containing a Foreign Key to the foreignTable
     */
    private ColumnInfo makeColumn(FilteredTable foreignTable, @Nullable String foreignTableName, String ptidColName, boolean isDemographic, CCJoinType joinType)
    {
        String colName = foreignTable.getName();
        ColumnInfo col = null;
        CoreFK fk = null;
        boolean addPtidJoin = false;

        if(_idCol == null)
        {
            col = new ColumnInfo(colName, this);
            col.setSqlTypeName("VARCHAR");
            fk = new CoreFK(foreignTable, ptidColName);
        }
        else if(isDemographic)
        {
            col = new AliasedColumn(colName, _idCol);
            fk = new CoreFK(foreignTable, ptidColName);
        }
        else if(_dateKey != null)
        {
            addPtidJoin = true;
            if(joinType == CCJoinType.DATE)
            {
                col = new AliasedColumn(_source, colName, getColumn(_dateKey));
                fk = new CoreFK(foreignTable, ptidColName);
            }
            else if(joinType == CCJoinType.PREV)
            {
                if(foreignTableName == null)
                {
                    Domain realTableDomain = foreignTable.getRealTable().getDomain();
                    foreignTableName = realTableDomain != null ?
                            StorageProvisioner.createTableInfo(realTableDomain).getSelectName() : null;
                }

                if(foreignTableName != null)
                {
                    SQLFragment sql = new SQLFragment("(SELECT " + _dateKey.getName() + " FROM " + foreignTableName +
                            " ch WHERE ch." + _dateKey.getName() + " <= " + ExprColumn.STR_TABLE_ALIAS +
                            ".date AND ch.participantId = " + ExprColumn.STR_TABLE_ALIAS + "." + PTID_NAME +
                            " ORDER BY " + _dateKey.getName() + " DESC LIMIT 1)");
                    col = new ExprColumn(_source, colName, sql, JdbcType.DATE, _idCol, getColumn(_dateKey));
                    fk = new CoreFK(foreignTable, _dateKey.getName());
                }
            }
        }

        if(col == null)
            return null;

        if(addPtidJoin)
        {
            fk.addJoin(new FieldKey(null, ptidColName), _joinedStudy.getSubjectColumnName(), false);
            fk.setJoinDescription("most recent " + _dateKey.getName() + " and " + _joinedStudy.getSubjectColumnName());
        }

        col.setFk(fk);
        col.setName(foreignTable.getName());
        col.setLabel(foreignTable.getTitle());
        col.setDescription("Values from table " + foreignTable.getName() + " from folder " + _joinedContainer.getName() +
                " joined on " + fk.getJoinDescription());
        col.setIsUnselectable(false);
        col.setUserEditable(false);

        return col;
    }

    /**
     * CoreFK simply extends LookupForeignKey to implement getLookupTableInfo to return the table that is passed into it.
     * It also adds a String joinDescription for use as the column description.
     */
    private class CoreFK extends LookupForeignKey
    {
        private final TableInfo _foreignTable;
        private String _joinDescription;

        public CoreFK(TableInfo foreignTable, String lookupColumnName)
        {
            super(lookupColumnName);
            _foreignTable = foreignTable;

            setJoinDescription(lookupColumnName);
        }

        public TableInfo getLookupTableInfo()
        {
            return _foreignTable;
        }

        public void setJoinDescription(String description)
        {
            _joinDescription = description;
        }

        public String getJoinDescription()
        {
            return _joinDescription;
        }
    }

    /**
     * A simple extension of FilteredTable that passes through all columns from the root table, hiding a predefined set.
     * TODO: Maybe find a way to hide unwanted columns more intelligently than with a static predefined set
     */
    private class CCFilteredTable extends FilteredTable<UserSchema>
    {

        public CCFilteredTable(TableInfo baseTable)
        {
            super(baseTable, _joinedSchema, new ContainerFilter.AllFolders(_joinedSchema.getUser()));

            for(ColumnInfo baseColumn: getRealTable().getColumns())
            {
                ColumnInfo col = addWrapColumn(baseColumn);

                if(baseColumn.isHidden() || baseColumn.isUnselectable() || CoreCollabManager.defaultHiddenCols.contains(col.getName()))
                    col.setHidden(true);
            }

            if(getColumn("Container") != null)
                clearConditions(baseTable.getColumn("Container").getFieldKey());

            //If this is a SchemaTableInfo (as in a Dataset table), it will not automatically overlay metadata, so do that here.
            //If this is a query, QueryDefinition creates a DatasetTableImpl for it, which does handle this automatically.
            if(!(baseTable instanceof AbstractTableInfo))
            {
                Collection<QueryException> errors = new ArrayList<>();
                Collection<TableType> metadata;
                //Get study.StudyData metadata, which applies to all queries and datasets
                metadata = QueryService.get().findMetadataOverride(_joinedSchema, "StudyData",
                        false, false, errors, null);
                if (metadata != null)
                    overlayMetadata(metadata, _joinedSchema, errors);
                //Get metadata for this table. This is picky about the XML filename - it has to be the name of the table, no label nonsense
                metadata = QueryService.get().findMetadataOverride(_joinedSchema, _name, false, false, errors, null);
                if (metadata != null)
                    overlayMetadata(metadata, _joinedSchema, errors);
            }
        }
    }

}