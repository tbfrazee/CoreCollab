/*
This class iterates through shared datasets of the passed containerToJoin (or registered Core folder, if null) and creates a foreign key lookup to each.
For demographic datasets, join is simply on ptid.
For non-demographic datasets, 2 join styles are supported - simple date matching and "most recent previous" row.

Unapologetically based on the DatasetAutoJoinTable from the study module.
*/

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

public class CoreCollabAutoJoinTable extends VirtualTable
{
    private static final String PTID_NAME = "participantId";

    private UserSchema _joinedSchema;
    private AbstractTableInfo _source;
    private ColumnInfo _idCol;
    private FieldKey _dateKey;
    private Study _joinedStudy;
    private Container _joinedContainer;

    public CoreCollabAutoJoinTable(AbstractTableInfo source, @Nullable ColumnInfo idCol, @Nullable FieldKey dateKey, @Nullable Container containerToJoin, @Nullable CCJoinType joinType, @Nullable AbstractCoreCollabCustomizer sourceCustomizer)
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

        Map<String, Integer> sharedQueries = CoreCollabManager.get().getSharedQueryMap(_joinedContainer);

        for(String qName : sharedQueries.keySet())
        {
            try
            {
                //QueryDefinition qd = QueryService.get().getQueryDef(_sourceSchema.getUser(), _joinedContainer, "study", qName);
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

        /*
        ****************************
        NOT YET IMPLEMENTED - This part is probably not going to happen
        ****************************
        Joining custom queries to Autojoin table

        //This part was an attempt at parsing the query SQL to get dataset names instead of asking the user for a primary dataset
        //For now, this is abandoned, as there are too many edge cases that can result in undesired results

        //Shared queries
        //All in a Try block, because SQL errors we can't control can throw exceptions (which we'll ignore and skip the query)
        Map<String, QueryDefinition> queryMap = QueryService.get().getQueryDefs(_sourceSchema.getUser(), _joinedContainer, "study");
        for(Map.Entry<String, QueryDefinition> entry : queryMap.entrySet())
        {
            try
            {
                List<QueryException> errors = new ArrayList<>();
                TableInfo queryTable = entry.getValue().getTable(errors, true);
                FilteredTable filteredTable = new CCFilteredTable(queryTable);

                if(_idCol == null)
                {
                    ColumnInfo newCol = makeColumn(filteredTable, ptidColName, false, joinType);
                    defaultVisible.add(FieldKey.fromParts(newCol.getName()));
                }
                else if(_dateKey != null)
                {
                    //In order to avoid having to fully parse out every possible query format, we'll set some rules.
                    //1) The _dateKey column must be defined in the first SELECT block
                    //2) The participantId must come from the same real table as _dateKey
                    //3) _dateKey must be a value directly from the database - no function calls

                    String alias = null;

                    SQLFragment qFrag = queryTable != null ? queryTable.getFromSQL("query") : null;
                    String qSQL = qFrag != null ? qFrag.getSQL() : null;
                    qSQL = qSQL.replaceAll("--.*?$", ""); //Remove comment lines

                    String mainSelect = qSQL.substring(qSQL.indexOf("SELECT"), qSQL.indexOf("FROM"));
                    if(mainSelect.toUpperCase().contains("AS " + _dateKey.getName().toUpperCase()))
                    {
                        //Do stuff
                        Matcher match = Pattern.compile("(?i)^(\\S+)\\sAS\\s" + _dateKey.getName()).matcher(mainSelect);
                        if(match.find())
                            mainSelect = match.group(1);
                    }
                    if(mainSelect.toUpperCase().contains("." + _dateKey.getName().toUpperCase()))
                    {
                        //Do stuff
                        Matcher match = Pattern.compile("(?i)(\\S+)\\." + _dateKey.getName()).matcher(mainSelect);
                        if(match.find())
                        {
                            alias = match.group(1);
                        }
                    }
                    else if(mainSelect.toUpperCase().contains(_dateKey.getName().toUpperCase()))
                    {
                        String from = qSQL.substring(qSQL.toUpperCase().indexOf("FROM"), qSQL.length());
                        Matcher match = Pattern.compile("\\(SELECT\\s\\*\\s(\\S+)\\sx").matcher(from);
                        if(match.find())
                            alias = match.group(1);
                    }


                    //First we need to figure out if the _dateKey is defined using an AS clause.
                    //If it is, there could be anything before it, which we'll need to attempt to parse out.
                    Matcher aliasMatches = Pattern.compile("(?i)[\\s\\n\\t,](.*)\\sAS\\s" + _dateKey.getName()).matcher(qSQL);

                    while (aliasMatches.find())
                    {
                        if (!aliasMatches.group(1).contains("--"))   //Ignore if commented
                        {
                            //If the captured group is a single word without a ".", then we can go directly to the next FROM clause
                            //If it does have a decimal, then we can capture the alias from it
                            //If it's more complicated, we'll have to parse things out
                            String capture = aliasMatches.group(1);

                            //First, is this a function? For now, we're going to ignore it - too many possibilities
                            if (Pattern.compile("(?i)^[A-Z0-9_$]*+\\(.*\\)$").matcher(capture).find())
                            {
                                alias = "~~SKIP~~";
                                break;
                            }
                            else if (Pattern.compile("(?i)^[A-Z0-9_$]*+\\.[A-Z0-9_$]*+$").matcher(capture).find())
                            {
                                aliasMatches = Pattern.compile("(?i)^([A-Z0-9_$]*+)\\.[A-Z0-9_$]*+$").matcher(capture);
                                aliasMatches.find();
                                alias = aliasMatches.group(1);
                            }
                            else if (!Pattern.compile("[\\.()\"']").matcher(capture).find())
                            {
                                aliasMatches = Pattern.compile("(?i)FROM\\s([A-Z0-9_$]+)").matcher(capture);
                                aliasMatches.find();
                                if (Pattern.matches("(?i)\\(*SELECT", aliasMatches.group(1)))
                                    alias = aliasMatches.group(1);
                                else
                                {
                                    alias = "~~SKIP~~";
                                    break;
                                }
                            }
                        }

                        if (alias != null)
                            break;
                    }

                    if(alias != null && alias.equals("~~SKIP~~"))
                        continue;

                    //If _dateKey is not specified by an AS clause, search it out as an explicit field name
                    if (alias == null)
                    {
                        if (Pattern.compile("(?i)[\\s\\n\\t,]([A-Z0-9_$]+)\\." + _dateKey.getName()).matcher(qSQL).find())
                        {
                            aliasMatches = Pattern.compile("(?i)[\\s\\n\\t,]([A-Z0-9_$]+)\\." + _dateKey.getName()).matcher(qSQL);
                            aliasMatches.find();
                            alias = aliasMatches.group(1);
                        }
                        else if (!Pattern.compile("[\\.()\"']").matcher(qSQL).find())
                        {
                            aliasMatches = Pattern.compile("(?i)FROM\\s([A-Z0-9_$]+)").matcher(qSQL);
                            aliasMatches.find();
                            if (Pattern.matches("(?i)\\(*SELECT", aliasMatches.group(1)))
                                alias = aliasMatches.group(1);
                            else
                                continue;
                        }
                    }

                    if (alias != null)
                    {
                        String realTableName;
                        String dsSchemaName = StudyService.get().getDatasetSchema().getName();
                        String regex = dsSchemaName + "\\.(\\S*).*?" + alias;
                        Matcher rtnMatches = Pattern.compile(regex).matcher(qSQL);
                        rtnMatches.find();
                        realTableName = rtnMatches.group(1);
                        if(realTableName != null)
                        {
                            ColumnInfo newCol = makeColumn(filteredTable, realTableName, ptidColName, false, joinType);
                            defaultVisible.add(FieldKey.fromParts(newCol.getName()));
                        }
                    }
                }
            }
            catch(Exception ignored){}
        }
        */

        setDefaultVisibleColumns(defaultVisible);

    }

    private ColumnInfo makeColumn(FilteredTable foreignTable, String ptidColName, boolean isDemographic, CCJoinType joinType)
    {
        return makeColumn(foreignTable, null, ptidColName, isDemographic, joinType);
    }

    //Makes a lookup column for each shared dataset
    //Join criteria are based on dataset type and passed joinType
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
                    foreignTableName = realTableDomain != null ? StorageProvisioner.createTableInfo(realTableDomain).getSelectName() : null;
                }

                if(foreignTableName != null)
                {
                    SQLFragment sql = new SQLFragment("(SELECT " + _dateKey.getName() + " FROM " + foreignTableName + " ch WHERE ch." + _dateKey.getName() + " <= " + ExprColumn.STR_TABLE_ALIAS + ".date AND ch.participantId = " + ExprColumn.STR_TABLE_ALIAS + "." + PTID_NAME + " ORDER BY " + _dateKey.getName() + " DESC LIMIT 1)");
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
        col.setDescription("Values from table " + foreignTable.getName() + " from folder " + _joinedContainer.getName() + " joined on " + fk.getJoinDescription());
        col.setIsUnselectable(false);
        col.setUserEditable(false);

        return col;
    }

    //TEST
    /*
    private class CCLookupColumn extends LookupColumn
    {
        public CCLookupColumn(ColumnInfo foreignKey, ColumnInfo lookupKey, ColumnInfo lookupColumn)
        {
            super(foreignKey, lookupKey, lookupColumn);
        }

        @Override
        protected SQLFragment getJoinCondition(String tableAliasName, ColumnInfo fk, ColumnInfo pk, boolean equalOrIsNull)
        {
            SQLFragment condition = new SQLFragment();

            condition.append("(");
            condition.append("(SELECT ").append(fk.getValueSql(tableAliasName)).append(" FROM ").append(fk.getSelectName()).append(")");
            condition.append(" <= ");
            condition.append("(SELECT ").append(pk.getValueSql(tableAliasName)).append(" FROM ").append(pk.getSelectName()).append(")");
            condition.append(" AND ");
            condition.append(fk.getSelectName()).append(".participantId = ").append(pk.getSelectName()).append(".participantId");

            return condition;
        }
    }
    */


    //CoreFK simply extends LookupForeignKey to implement getLookupTableInfo to return the table that is passed into it
    //It also adds a String joinDescription for use as the column description
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

    //A simple extension of FilteredTable that passes through all columns from the root table, hiding a predefined set
    //TODO: Maybe find a way to hide unwanted columns more intelligently rather than a static predefined set
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
                metadata = QueryService.get().findMetadataOverride(_joinedSchema, "StudyData", false, false, errors, null);
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