//CoreCollaboration Service

package org.labkey.corecollab;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.corecollab.AbstractCoreCollabCustomizer;
import org.labkey.api.corecollab.CCJoinType;
import org.labkey.api.corecollab.CoreCollabService;

import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.StringExpression;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CoreCollabServiceImpl extends CoreCollabService
{

    Container _coreContainer;
    boolean _isCoreStudyDataspace;
    Container _teamContainer;
    Set<Container> _labContainers = new HashSet<>();

    public CoreCollabServiceImpl(){}

    public static CoreCollabServiceImpl get(){return (CoreCollabServiceImpl) CoreCollabService.get();}

    public void registerCoreFolder(Container container)
    {
        StudyService svc = StudyService.get();
        if(svc != null && svc.getStudy(container) != null)
        {
            _coreContainer = container;
            Study study = svc.getStudy(container);
            if(study != null && study.isDataspaceStudy())
                _isCoreStudyDataspace = true;
        }
        else
            throw new IllegalStateException("StudyService is not available, or there is no Study in folder " + container.getName());
    }

    public void releaseCoreFolder()
    {
        _coreContainer = null;
        _isCoreStudyDataspace = false;
    }

    public void registerLabFolder(Container container)
    {
        StudyService svc = StudyService.get();
        if(svc != null && svc.getStudy(container) != null)
            _labContainers.add(container);
        else
            throw new IllegalStateException("StudyService is not available, or there is no Study in folder " + container.getName());
    }

    public void releaseLabFolder(Container container)
    {
        _labContainers.remove(container);
    }

    public void releaseAllLabFolders()
    {
        _labContainers.clear();
    }

    public void registerTeamFolder(Container container)
    {
        StudyService svc = StudyService.get();
        if(svc != null && svc.getStudy(container) != null)
            _teamContainer = container;
        else
            throw new IllegalStateException("StudyService is not available, or there is no Study in folder " + container.getName());
    }

    public void releaseTeamFolder()
    {
        _teamContainer = null;
    }

    public boolean validateMinimumConfig()
    {
        if(getCoreFolder() == null)
            return false;
        if(getCoreStudy() == null)
            return false;
        if(getLabFolders().isEmpty() && getTeamFolder() == null)
            return false;
        if(getTeamFolder() != null && getTeamStudy() == null)
            return false;
        for(Container c : getLabFolders())
        {
            if(getLabStudy(c) == null)
                return false;
        }
        return true;
    }

    public Container getCoreFolder()
    {
        return _coreContainer;
    }

    public Container getTeamFolder()
    {
        return _teamContainer;
    }

    public List<Container> getLabFolders()
    {
        return new ArrayList<>(_labContainers);
    }

    public Study getCoreStudy()
    {
        StudyService svc = StudyService.get();
        if(svc != null &&_coreContainer != null)
            return svc.getStudy(_coreContainer);
        else
            return null;
    }

    public Study getTeamStudy()
    {
        StudyService svc = StudyService.get();
        if(svc != null && _teamContainer != null)
            return svc.getStudy(_teamContainer);
        else
            return null;
    }

    public Study getLabStudy(Container ctr)
    {
        StudyService svc = StudyService.get();
        if(svc != null && _labContainers.contains(ctr))
            return svc.getStudy(ctr);
        else
            return null;
    }

    public List<Integer> getSharedDatasets(Container c)
    {
        return CoreCollabManager.get().getSharedDatasets(c);
    }

    public List<String> getSharedQueries(Container c)
    {
        return CoreCollabManager.get().getSharedQueryNames(c);
    }

    public Map<String, Integer> getSharedQueryMap(Container c)
    {
        return CoreCollabManager.get().getSharedQueryMap(c);
    }

    public boolean isCoreDataspace()
    {
        return _isCoreStudyDataspace;
    }

    public ColumnInfo addCoreAutoJoinTable(AbstractTableInfo ti, String dateColName)
    {
        return addCCAutoJoinTable(ti, dateColName, null, CCJoinType.DATE, null);
    }

    public ColumnInfo addCCAutoJoinTable(AbstractTableInfo ti, String dateColName, @Nullable Container containerToJoin)
    {
        return addCCAutoJoinTable(ti, dateColName, containerToJoin, CCJoinType.DATE, null);
    }

    public ColumnInfo addCCAutoJoinTable(AbstractTableInfo ti, String dateColName, @Nullable CCJoinType joinType)
    {
        return addCCAutoJoinTable(ti, dateColName, null, joinType, null);
    }

    public ColumnInfo addCCAutoJoinTable(AbstractTableInfo ti, String dateColName, @Nullable Container containerToJoin, @Nullable CCJoinType joinType)
    {
        return addCCAutoJoinTable(ti, dateColName, containerToJoin, joinType, null);
    }

    public ColumnInfo addCCAutoJoinTable(AbstractTableInfo ti, String dateColName, @Nullable Container containerToJoin, @Nullable CCJoinType joinType, @Nullable AbstractCoreCollabCustomizer customizer)
    {
        final Container targetContainer;
        UserSchema userSchema = ti.getUserSchema();
        if(userSchema != null)
        {
            Container sourceContainer = ti.getUserSchema().getContainer();
            boolean sourceIsTeam = getTeamFolder() != null && getTeamFolder().equals(sourceContainer);
            boolean sourceIsLab = getLabFolders().contains(sourceContainer);

            //If containerToJoin is null, default to Core
            if (containerToJoin == null)
                targetContainer = getCoreFolder();
            else
                targetContainer = containerToJoin;

            //First, check prereqs and permissions to add the CoreCollabAutoJoinTable
            boolean shouldAddCCAJT =
                    (
                            validateMinimumConfig() &&  //Core and either Team or at least 1 Lab are registered
                                    ti.getColumn(dateColName) != null &&    //Date column is valid
                                    !CoreCollabManager.get().getSharedDatasets(targetContainer).isEmpty() &&    //At least one dataset is shared from the target folder
                                    (sourceIsLab || sourceIsTeam)   //The calling folder should be registered and have permission to get this data
                    );

            if (shouldAddCCAJT)
            {
                StudyService svc = StudyService.get();
                Container lookupContainer = userSchema.getContainer();
                String colName = targetContainer.getName() + "Data";
                Study tableStudy = svc != null ? svc.getStudy(lookupContainer) : null;
                if (tableStudy == null)
                    return null;
                String subjectColName = tableStudy.getSubjectColumnName();
                if (ti.getColumn(subjectColName) == null)
                    return null;
                ColumnInfo autoJoinColumn = new AliasedColumn(ti, colName, ti.getColumn(subjectColName));
                autoJoinColumn.setDescription("Lookups to " + getCoreFolder().getName() + " data for matching " + getCoreStudy().getSubjectNounSingular() + " and date");
                autoJoinColumn.setKeyField(false);
                autoJoinColumn.setIsUnselectable(true);
                autoJoinColumn.setUserEditable(false);
                autoJoinColumn.setCalculated(true);
                autoJoinColumn.setLabel(targetContainer.getTitle() + " Data");
                final FieldKey dateKey = new FieldKey(null, dateColName);
                AbstractForeignKey autoJoinFk = new AbstractForeignKey()
                {
                    @Override
                    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                    {
                        if (displayField == null)
                            return null;

                        CoreCollabAutoJoinTable table = new CoreCollabAutoJoinTable(ti, parent, getRemappedField(dateKey), targetContainer, joinType, customizer);
                        return table.getColumn(displayField);
                    }

                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return new CoreCollabAutoJoinTable(ti, null, null, containerToJoin, joinType, customizer);
                    }

                    @Override
                    public StringExpression getURL(ColumnInfo parent)
                    {
                        return null;
                    }
                };
                autoJoinFk.addSuggested(dateKey);
                autoJoinColumn.setFk(autoJoinFk);
                return ti.addColumn(autoJoinColumn);
            }
        }
        return null;
    }
}