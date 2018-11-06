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

import org.apache.log4j.Logger;

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.corecollab.CoreCollabService;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Results;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;


public class CoreCollabManager
{
    private static final CoreCollabManager _instance = new CoreCollabManager();
    private Logger _log;

    private CoreCollabManager()
    {
        // prevent external construction with a private default constructor
        _log = Logger.getLogger(CoreCollabManager.class);
    }

    public static CoreCollabManager get()
    {
        return _instance;
    }

    public enum FolderType
    {
        NONE,
        CORE,
        TEAM,
        LAB
    }

    public static final Set<String> defaultHiddenCols = new CaseInsensitiveHashSet("Container", "Folder", "VisitRowId", "SequenceNum", "Created", "CreatedBy", "ModifiedBy", "Modified", "lsid", "SourceLsid", "DSRowID", "QCState", "ParticipantSequenceNum", "dataset", "_key");

    public void registerCCFolders()
    {
        DbSchema CoreSchema = CoreCollabSchema.getInstance().getSchema();
        TableInfo ti = CoreSchema.getTable(CoreCollabSchema.REG_TABLE);
        try(Results regs = new TableSelector(ti).getResults())
        {
            if (regs.first())
            {
                FieldKey containerKey = new FieldKey(null, "container");
                FieldKey typeKey = new FieldKey(null, "type");
                if (regs.hasColumn(containerKey) && regs.hasColumn(typeKey))
                {
                    CoreCollabService.get().releaseCoreFolder();
                    CoreCollabService.get().releaseTeamFolder();
                    CoreCollabService.get().releaseAllLabFolders();
                    do
                    {
                        Container ctr = ContainerManager.getForId(regs.getString(containerKey));
                        try
                        {
                            String strFType = regs.getString(typeKey);
                            FolderType enumFType = FolderType.valueOf(strFType);
                            if (ctr != null)
                            {
                                switch (enumFType)
                                {
                                    case CORE:
                                        CoreCollabService.get().registerCoreFolder(ctr);
                                        break;
                                    case TEAM:
                                        CoreCollabService.get().registerTeamFolder(ctr);
                                        break;
                                    case LAB:
                                        CoreCollabService.get().registerLabFolder(ctr);
                                        break;
                                }
                            }
                            else
                                deleteFolderReg(regs.getString(containerKey));
                        }
                        catch(Exception e)
                        {
                            String cName = ctr != null ? ctr.getName() : "";
                            _log.error("Error registering CoreCollab folder " + cName, e);
                        }
                    } while(regs.next());
                }
            }
        }catch(Exception e)
        {
            _log.error("Error getting CoreCollab folder registrations. Registrations may be in an inconsistent state.", e);
        }

    }

    public FolderType getFolderType(Container c)
    {
        FieldKey containerKey = new FieldKey(null, "container");
        FieldKey typeKey = new FieldKey(null, "type");
        try(Results folderRegs = new TableSelector(CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.REG_TABLE), new SimpleFilter(containerKey, c), null).getResults())
        {
            //If there is not 1 row and only 1 row returned, something went wrong
            if(folderRegs.last())
            {
                if (folderRegs.getRow() != 1)
                {
                    return FolderType.NONE;
                }
            }
            else
            {
                return FolderType.NONE;
            }

            folderRegs.first();
            if (folderRegs.hasColumn(containerKey) && folderRegs.hasColumn(typeKey))
            {
                return FolderType.valueOf(folderRegs.getString(typeKey));
            }
        }
        catch(Exception e)
        {
            _log.error("Error getting CoreCollab folder type for container " + c.getName(), e);
        }

        return FolderType.NONE;
    }

    public String getFolderTypeString(Container c)
    {
        return getFolderType(c).toString();
    }

    public void setFolderType(User u, Container c, FolderType fType) throws RuntimeException
    {
        CoreCollabService svc = CoreCollabService.get();

        Map<String, String> dataObj = new HashMap<>();
        dataObj.put("container", c.getEntityId().toString());
        dataObj.put("type", fType.toString());

        FolderType oldFType = getFolderType(c) == null ? FolderType.NONE : getFolderType(c);

        if(fType != oldFType)
        {
            try
            {
                if (oldFType == FolderType.NONE)
                    Table.insert(u, CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.REG_TABLE), dataObj);
                else
                    Table.update(u, CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.REG_TABLE), dataObj, c.getEntityId().toString());
            }
            catch (Exception e)
            {
                _log.error("Error setting folder type " + fType.toString() + " for folder " + c.getName(), e);
                throw e;
            }

            switch (oldFType)
            {
                case CORE:
                    svc.releaseCoreFolder();
                case TEAM:
                    svc.releaseTeamFolder();
                case LAB:
                    svc.releaseLabFolder(c);
            }

            switch (fType)
            {
                case CORE:
                    svc.registerCoreFolder(c);
                case TEAM:
                    svc.registerTeamFolder(c);
                case LAB:
                    svc.registerLabFolder(c);
            }
        }
    }

    public void deleteFolderReg(String containerID)
    {
        Table.delete(CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.REG_TABLE), containerID);
    }

    public String getTeamQueryCustomizer(Container c)
    {
        FieldKey containerKey = new FieldKey(null, "container");
        FieldKey customizerKey = new FieldKey(null, "customizer");
        String ret;
        try(Results results = new TableSelector(CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.TEAM_QUERY_CUSTOMIZER_TABLE), new SimpleFilter(containerKey, c), null).getResults())
        {
            if(results.last())
            {
                if(results.getRow() != 1)
                {
                    return null;
                }
            }
            else
            {
                return null;
            }

            results.first();
            if(results.hasColumn(containerKey) && results.hasColumn(customizerKey))
            {
                ret = results.getString(customizerKey);
                return ret;
            }

        } catch(Exception e){return null;}

        return null;

    }

    public void setTeamQueryCustomizer(User u, Container c, String customizerPath) throws RuntimeException
    {
        Map<String, String> dataObj = new HashMap<>();

        String oldCustomizer = getTeamQueryCustomizer(c);
        dataObj.put("container", c.getEntityId().toString());
        dataObj.put("customizer", customizerPath);

        try
        {
            if(oldCustomizer == null)
                Table.insert(u, CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.TEAM_QUERY_CUSTOMIZER_TABLE), dataObj);
            else
                Table.update(u, CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.TEAM_QUERY_CUSTOMIZER_TABLE), dataObj, c.getEntityId().toString());
        }
        catch (Exception e)
        {
            _log.error("Error setting Team Query Customizer for container " + c.getName(), e);
            throw e;
        }
    }

    public List<Integer> getSharedDatasets(Container c)
    {
        FieldKey containerKey = new FieldKey(null, "container");
        FieldKey idKey = new FieldKey(null, "datasetIds");
        List<Integer> ret = new ArrayList<>();
        try(Results results = new TableSelector(CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_DATASET_TABLE), new SimpleFilter(containerKey, c), null).getResults())
        {
            //If there is not 1 row and only 1 row returned, something went horribly, horribly wrong
            if(results.last())
            {
                if (results.getRow() != 1)
                {
                    return ret;
                }
            }
            else
            {
                return ret;
            }

            results.first();
            if (results.hasColumn(containerKey) && results.hasColumn(idKey))
            {
                String[] retAsStrings = results.getString(idKey).split(";");
                for(String val : retAsStrings)
                {
                    try
                    {
                        ret.add(Integer.valueOf(val));
                    }
                    catch (Exception ignored){}
                }
                return ret;
            }
        }
        catch(Exception e)
        {
            _log.error("Error getting shared datasets for container " + c.getName(), e);
        }

        return ret;
    }

    public Map<String, Integer> getSharedQueryMap(Container c)
    {
        FieldKey containerKey = new FieldKey(null, "container");
        FieldKey idKey = new FieldKey(null, "queryNames");
        FieldKey dsKey = new FieldKey(null, "primaryDatasets");
        Map<String, Integer> ret = new HashMap<>();
        try(Results results = new TableSelector(CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_QUERY_TABLE), new SimpleFilter(containerKey, c), null).getResults())
        {
            //If there is not 1 row and only 1 row returned, something went horribly, horribly wrong
            if(results.last())
            {
                if (results.getRow() != 1)
                {
                    return ret;
                }
            }
            else
            {
                return ret;
            }

            results.first();
            if (results.hasColumn(containerKey) && results.hasColumn(idKey))
            {
                String queryString = results.getString(idKey);
                String datasetString = results.getString(dsKey);
                List<String> queryNames = queryString != null ? Arrays.asList(queryString.split(";")) : new ArrayList<>();
                List<String> primaryDatasets = datasetString != null ? Arrays.asList(datasetString.split(";")) : new ArrayList<>();
                for(int i = 0; i < queryNames.size(); i++)
                {
                    Integer dsId = 0;
                    try
                    {
                        dsId = Integer.parseInt(primaryDatasets.get(i));
                    }
                    catch(Exception ignored){}
                    ret.put(queryNames.get(i),dsId);
                }
            }
        }
        catch(Exception e)
        {
            _log.error("Error getting shared queries for container " + c.getName(), e);
        }

        return ret;
    }

    public List<String> getSharedQueryNames(Container c)
    {
        FieldKey containerKey = new FieldKey(null, "container");
        FieldKey idKey = new FieldKey(null, "queryNames");
        List<String> ret = new ArrayList<>();
        try(Results results = new TableSelector(CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_QUERY_TABLE), new SimpleFilter(containerKey, c), null).getResults())
        {
            //If there is not 1 row and only 1 row returned, something went horribly, horribly wrong
            if(results.last())
            {
                if (results.getRow() != 1)
                {
                    return ret;
                }
            }
            else
            {
                return ret;
            }

            results.first();
            if (results.hasColumn(containerKey) && results.hasColumn(idKey))
                return Arrays.asList(results.getString(idKey).split(";"));
        }
        catch(Exception e)
        {
            _log.error("Error getting shared queries for container " + c.getName(), e);
        }

        return ret;
    }

    public List<Integer> getQueryPrimaryDatasetIds(Container c)
    {
        FieldKey containerKey = new FieldKey(null, "container");
        FieldKey idKey = new FieldKey(null, "primaryDatasets");
        List<Integer> ret = new ArrayList<>();
        try(Results results = new TableSelector(CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_QUERY_TABLE), new SimpleFilter(containerKey, c), null).getResults())
        {
            //If there is not 1 row and only 1 row returned, something went horribly, horribly wrong
            if(results.last())
            {
                if (results.getRow() != 1)
                {
                    return ret;
                }
            }
            else
            {
                return ret;
            }

            results.first();
            if (results.hasColumn(containerKey) && results.hasColumn(idKey))
            {
                String[] strIds = results.getString(idKey).split(";");
                for(String id : strIds)
                {
                    try
                    {
                        ret.add(Integer.parseInt(id));
                    }
                    catch(Exception ignored){}
                }
            }
        }
        catch(Exception e)
        {
            _log.error("Error getting shared query primary datasets for container " + c.getName(), e);
        }

        return ret;
    }

    public void setSharedDatasetsForContainer(User u, Container c, List<Integer> sharedDatasetIds) throws RuntimeException
    {
        Map<String, String> dataObj = new HashMap<>();
        String datasetIds = sharedDatasetIds.stream().map(Object::toString).collect(Collectors.joining(";"));

        List<Integer> oldIds = getSharedDatasets(c);
        dataObj.put("container", c.getEntityId().toString());
        dataObj.put("datasetIds", datasetIds);

        try
        {
            if(oldIds == null || oldIds.isEmpty())
                Table.insert(u, CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_DATASET_TABLE), dataObj);
            else
                Table.update(u, CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_DATASET_TABLE), dataObj, c.getEntityId().toString());
        }
        catch (Exception e){
            _log.error("Error setting shared datasets for container " + c.getName(), e);
            throw e;
        }

    }

    public void setSharedQueriesForContainer(User u, Container c, List<String> sharedQueryNames, List<Integer> primaryDatasets) throws RuntimeException
    {
        Map<String, String> dataObj = new HashMap<>();
        String queryNames = sharedQueryNames.stream().map(Object::toString).collect(Collectors.joining(";"));
        String datasetString = primaryDatasets.stream().map(Object::toString).collect(Collectors.joining(";"));

        List<String> oldNames = getSharedQueryNames(c);
        dataObj.put("container", c.getEntityId().toString());
        dataObj.put("queryNames", queryNames);
        dataObj.put("primaryDatasets", datasetString);

        try
        {
            if(oldNames == null || oldNames.isEmpty())
                Table.insert(u, CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_QUERY_TABLE), dataObj);
            else
                Table.update(u, CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_QUERY_TABLE), dataObj, c.getEntityId().toString());
        }
        catch(Exception e){
            _log.error("Error setting shared queries for container " + c.getName(), e);
            throw e;
        }
    }

    public void clearSharedDatasetsForContainer(Container c)
    {
        SimpleFilter filter = new SimpleFilter(new FieldKey(null, "container"), c.getEntityId());
        Table.delete(CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_DATASET_TABLE), filter);
    }

    public void clearSharedQueriesForContainer(Container c)
    {
        SimpleFilter filter = new SimpleFilter(new FieldKey(null, "container"), c.getEntityId());
        Table.delete(CoreCollabSchema.getInstance().getSchema().getTable(CoreCollabSchema.SHARED_QUERY_TABLE), filter);
    }
}