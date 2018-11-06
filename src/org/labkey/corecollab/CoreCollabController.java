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

import org.labkey.api.corecollab.CoreCollabService;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.DataViewEditForm;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.model.ViewInfo;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

@SuppressWarnings("unused")
public class CoreCollabController extends SpringActionController {
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(CoreCollabController.class);
    public static final String NAME = "corecollab";

    public CoreCollabController() {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class CoreTheWorldAction extends SimpleViewAction {
        @Override
        public ModelAndView getView(Object o, BindException errors) {
            JspView view = new JspView("/org/labkey/corecollab/view/CoreTheWorld.jsp");
            view.setTitle("CORE TEH WORLD!");
            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root) {
            return root;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CCFolderSettingsAction extends FormViewAction<CCFolderSettingsForm>
    {
        public CCFolderSettingsAction()
        {
            //Nothing!
        }

        public CCFolderSettingsAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        public ModelAndView getView(CCFolderSettingsForm form, boolean reShow, BindException errors)
        {
            Map<String, Object> params = new HashMap<>();
            params.put("folderType", CoreCollabManager.get().getFolderTypeString(getViewContext().getContainer()));
            params.put("customizer", CoreCollabManager.get().getTeamQueryCustomizer(getViewContext().getContainer()));
            params.put("sharedDatasets", CoreCollabManager.get().getSharedDatasets(getViewContext().getContainer()));
            params.put("sharedQueries", CoreCollabManager.get().getSharedQueryMap(getViewContext().getContainer()));
            params.put("response", form.getResponseText());

            JspView view = new JspView<>("/org/labkey/corecollab/view/CCFolderSettings.jsp", params, errors);
            view.setTitle("Update folder Core Collaboration settings");
            return view;
        }

        public boolean handlePost(CCFolderSettingsForm form, BindException errors)
        {
            Container c = getViewContext().getContainer();
            String fType = form.getFolderType();
            String customizer = form.getCustomizer();
            List<Integer> sharedData = form.getSharedData();
            List<String> sharedQueries = form.getSharedQueries();
            List<Integer> queryDatasets = form.getQueryDatasetIds();

            Container coreFolder = CoreCollabService.get().getCoreFolder();
            Container teamFolder = CoreCollabService.get().getTeamFolder();

            //Set folder type, if it has changed
            if (fType != null && !fType.equals(""))
            {
                if ((fType.equals("CORE") && (coreFolder == null || !coreFolder.equals(c))) ||
                        (fType.equals("TEAM") && (teamFolder == null || !teamFolder.equals(c))) ||
                        (fType.equals("LAB") && !CoreCollabService.get().getLabFolders().contains(c)))
                {
                    try
                    {
                        CoreCollabManager.get().setFolderType(getViewContext().getUser(), c, CoreCollabManager.FolderType.valueOf(fType));
                        form.addResponseText("Folder Type set to " + fType + ".");
                    }
                    catch (Exception e)
                    {
                        errors.reject("DatabaseError", e.getMessage());
                    }
                }
                else if(fType.equals("NONE"))
                    CoreCollabManager.get().deleteFolderReg(c.getId());
            }

            //Set Team Query Customizer, if it has changed
            if(customizer != null)
            {
                if(customizer.equals(""))
                {
                    customizer = "org.labkey.corecollab.DefaultCoreCollabCustomizer";
                }
                if(!customizer.equals(CoreCollabManager.get().getTeamQueryCustomizer(c)))
                {
                    CoreCollabManager.get().setTeamQueryCustomizer(getViewContext().getUser(), c, customizer);
                }
            }

            //Set shared datsets, if they have changed
            if(!sharedData.equals(CoreCollabManager.get().getSharedDatasets(c)))
            {
                if (!sharedData.isEmpty())
                {
                    CoreCollabManager.get().setSharedDatasetsForContainer(getViewContext().getUser(), c, sharedData);
                    form.addResponseText("Shared Datasets updated.");
                }
                else
                {
                    CoreCollabManager.get().clearSharedDatasetsForContainer(c);
                    form.addResponseText("Shared Datasets cleared.");
                }
            }

            //Set shared queries, if they have changed
            if(!sharedQueries.equals(CoreCollabManager.get().getSharedQueryNames(c)) || !queryDatasets.equals(CoreCollabManager.get().getQueryPrimaryDatasetIds(c)))
            {
                if(!sharedQueries.isEmpty())
                {
                    CoreCollabManager.get().setSharedQueriesForContainer(getViewContext().getUser(), c, sharedQueries, queryDatasets);
                    form.addResponseText("Shared Queries updated.");
                }
                else
                {
                    CoreCollabManager.get().clearSharedQueriesForContainer(c);
                    form.addResponseText("Shared Queries cleared.");
                }
            }

            return true;
        }

        public void validateCommand(CCFolderSettingsForm form, Errors errors)
        {
            form.validate(errors);
        }

        public ActionURL getSuccessURL(CCFolderSettingsForm form)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    public static class CCFolderSettingsForm
    {
        String _folderType;
        String _customizer;
        List<Integer> _sharedData = new ArrayList<>();
        List<String> _sharedQueries = new ArrayList<>();
        List<String> _queryDatasets = new ArrayList<>(); //Has to be converted to Integer
        List<Integer> _queryDatasetIds = new ArrayList<>();
        String _responseText;

        public boolean setFolderType(String strFType)
        {
            try
            {
                _folderType = strFType;
                return true;
            }
            catch (Exception e)
            {
                _folderType = null;
                return false;
            }
        }

        public String getFolderType()
        {
            return _folderType;
        }

        public boolean setCustomizer(String customizerPath)
        {
            try
            {
                _customizer = customizerPath;
                return true;
            }
            catch(Exception e)
            {
                _customizer = null;
                return false;
            }
        }

        public String getCustomizer()
        {
            return _customizer;
        }

        public boolean setSharedData(Integer[] data)
        {
            try
            {
                _sharedData = Arrays.asList(data);
                return true;
            }
            catch(Exception e)
            {
                return false;
            }
        }

        public List<Integer> getSharedData()
        {
            return _sharedData;
        }

        public boolean setSharedQueries(String[] data)
        {
            try
            {
                _sharedQueries = Arrays.asList(data);
                return true;
            }
            catch(Exception e)
            {
                return false;
            }
        }

        public List<String> getSharedQueries()
        {
            return _sharedQueries;
        }

        public boolean setQueryDatasets(String[] data)
        {
            try
            {
                List<String> ret = new ArrayList<>();
                List<String> queries = getSharedQueries();
                for(String s : data)
                {
                    String qName = s.substring(0, s.lastIndexOf(":") - 1);
                    String strDSId = s.substring(s.lastIndexOf(":") + 1, s.length() - 1);
                    if(queries.contains(qName) && !strDSId.equals("0"))
                    {
                        ret.add(strDSId);
                    }

                }
                _queryDatasets = ret;
                return true;
            }
            catch(Exception e)
            {
                return false;
            }
        }

        public List<String> getQueryDatasets()
        {
            return _queryDatasets;
        }

        public List<Integer> getQueryDatasetIds()
        {
            return _queryDatasetIds;
        }

        public void validate(Errors errors)
        {
            //Make sure FolderType isn't going to throw an exception
            try
            {
                CoreCollabManager.FolderType.valueOf(_folderType);
            }
            catch (Exception e)
            {
                errors.rejectValue("folderType", "InvalidFolderType", null, null);
            }

            //Get rid of queryDatasets with value "0"
            for(String qName : _sharedQueries)
            {
                boolean added = false;
                for (String strDS : getQueryDatasets())
                {
                    if(strDS != null && strDS.contains(":") && qName.equals(strDS.substring(0, strDS.lastIndexOf(":"))))
                    {
                        String strDsId = strDS.substring(strDS.lastIndexOf(":") + 1, strDS.length());
                        try
                        {
                            Integer dsId = Integer.parseInt(strDsId);
                            _queryDatasetIds.add(dsId);
                            added = true;
                            break;
                        }
                        catch (Exception e)
                        {
                            errors.rejectValue("sharedQueries", "InvalidPrimaryDatasetSelection", null, "Error setting primary dataset for query " + qName);
                        }
                    }
                }
                if(!added)
                    errors.rejectValue("sharedQueries", "InvalidPrimaryDatasetSelection", null, "No primary dataset chosen for selected query " + qName);
            }

        }

        public void setResponseText(String text)
        {
            _responseText = text;
        }

        public void addResponseText(String text)
        {
            if(_responseText == null || _responseText.equals(""))
            {
                setResponseText(text);
            }
            else
            {
                _responseText += "<br />" + text;
            }
        }

        public String getResponseText()
        {
            return _responseText;
        }
    }

    public static class CreateTeamQueryForm extends DataViewEditForm
    {
        private String newReportName;
        private String folderName;
        private Integer datasetName;
        private ActionURL srcURL;

        public String getNewReportName()
        {
            return newReportName;
        }

        public void setNewReportName(String newReportName)
        {
            this.newReportName = newReportName;
        }

        public String getFolderName()
        {
            return folderName;
        }

        public void setFolderName(String folderName)
        {
            this.folderName = folderName;
        }

        public Integer getDatasetName()
        {
            return datasetName;
        }

        public void setDatasetName(Integer datasetName)
        {
            this.datasetName = datasetName;
        }

        public ActionURL getSrcURL()
        {
            return srcURL;
        }

        public void setSrcURL(ActionURL srcURL)
        {
            this.srcURL = srcURL;
        }
    }

    @RequiresPermission(EditSharedViewPermission.class)
    public class CreateTeamQueryAction extends FormViewAction<CreateTeamQueryForm>
    {
        private ActionURL successurl;

        @Override
        public ModelAndView getView(CreateTeamQueryForm form, boolean reShow, BindException errors)
        {
            Map<String, Object> params = new HashMap<>();
            params.put("folderType", CoreCollabManager.get().getFolderTypeString(getContainer()));
            params.put("sharedDatasets", CoreCollabManager.get().getSharedDatasets(getContainer()));

            JspView view = new JspView<>("/org/labkey/corecollab/view/CreateTeamQuery.jsp", params, errors);
            view.setTitle("Create shared Team data view");
            return view;
        }

        @Override
        public void validateCommand(CreateTeamQueryForm form, Errors errors)
        {
            //dostuff
        }

        @Override
        public boolean handlePost(CreateTeamQueryForm form, BindException errors)
        {
            StudyService svc = StudyService.get();
            Container c = ContainerManager.getForId(form.folderName);
            Dataset ds = svc != null ? svc.getDataset(c, form.datasetName) : null;
            if(ds != null)
            {
                String dsName = ds.getName();
                String newReportName = form.newReportName;
                successurl = c != null ? c.getStartURL(getUser()) : null;

                QueryDefinition qDef = makeQuery(form, errors, c, dsName, ds.getTableInfo(getUser(), true, true));
                if (qDef == null)
                {
                    errors.reject("Query error", "An error has occurred in creating or retrieving the backing query. Please contact your server admin.");
                }

                try
                {
                    Report report = createReport(form, errors, c, newReportName, dsName);
                    successurl = report.getRunReportURL(getViewContext());
                    return true;
                }
                catch (Exception e)
                {
                    errors.reject("Report error", "An error has occurred in creating the report. Please contact your server admin.");
                    return false;
                }
            }
            return false;
        }

        public QueryDefinition makeQuery(CreateTeamQueryForm form, BindException errors, Container c, String dsName, TableInfo dsTable)
        {
            UserSchema targetSchema = new SimpleUserSchema("study", null, getUser(), c, DbSchema.get("study", DbSchemaType.Module));

            QueryDefinition existing = QueryService.get().getQueryDef(getUser(), getContainer(), "study", c.getName() + "_" + dsName);
            if (existing != null)
            {
                return existing;
            }

            SchemaKey qSchemaKey = new SchemaKey(null, targetSchema.getSchemaName());
            QueryDefinition newDef = QueryService.get().createQueryDef(getUser(), getContainer(), qSchemaKey, c.getName() + "_" + dsName);

            String sql = "SELECT * FROM \"" + c.getPath() + "\".study." + dsName;

            List<String> metaDataCols = new ArrayList<>();
            for(String colName : CoreCollabManager.defaultHiddenCols)
            {
                if(dsTable.getColumn(colName) != null)
                {
                    metaDataCols.add(colName);
                }
            }
            String metadata = makeMetaData(c, c.getName() + "_" + dsName, metaDataCols);

            newDef.setSql(sql);
            newDef.setMetadataXml(metadata);

            try
            {
                newDef.save(getUser(), getContainer());
                return newDef;
            }
            catch (SQLException x)
            {
                return null;
            }
        }

        public String makeMetaData(Container c, String qName, List<String> cols)
        {
            StringBuilder sb = new StringBuilder();
            StudyService svc = StudyService.get();
            Study targetStudy = svc != null ? svc.getStudy(c) : null;
            String customizer = CoreCollabManager.get().getTeamQueryCustomizer(getViewContext().getContainer());

            if(targetStudy != null)
            {
                sb.append("<tables xmlns=\"http://labkey.org/data/xml\">");
                sb.append("<table tableName=\"");
                sb.append(qName);
                sb.append("\" tableDbType=\"NOT_IN_DB\">");
                if (customizer != null)
                {
                    sb.append("<javaCustomizer>");
                    sb.append(customizer);
                    sb.append("</javaCustomizer>");
                }
                sb.append("<columns>");
                    sb.append("<column columnName=\"");
                    sb.append(targetStudy.getSubjectColumnName());
                    sb.append("\">");
                        sb.append("<fk>");
                            sb.append("<fkColumnName>");
                                sb.append(targetStudy.getSubjectColumnName());
                            sb.append("</fkColumnName>");
                            sb.append("<fkTable>");
                                sb.append(targetStudy.getSubjectNounSingular());
                            sb.append("</fkTable>");
                            sb.append("<fkDbSchema>study</fkDbSchema>");
                        sb.append("</fk>");
                sb.append("</column>");
                for (String colName : cols)
                {
                    sb.append("<column columnName=\"");
                    sb.append(colName);
                    sb.append("\">");
                        sb.append("<isHidden>true</isHidden>");
                    sb.append("</column>");
                }
                sb.append("</columns>");
                sb.append("</table>");
                sb.append("</tables>");
            }

            return sb.toString();
        }

        public Report createReport(CreateTeamQueryForm form, BindException errors, Container c, String newReportName, String dsName)
        {
            DbScope scope = CoreSchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction tx = scope.ensureTransaction())
            {
                // save the category information then the report
                Integer categoryId = form.getCategory();

                QueryReport report = (QueryReport)ReportService.get().createReportInstance(QueryReport.TYPE);

                if(report != null)
                {
                    report.setSchemaName("study");
                    report.setQueryName(c.getName() + "_" + dsName);

                    ReportDescriptor descriptor = report.getDescriptor();

                    descriptor.setContainer(getContainer().getId());
                    descriptor.setReportName(newReportName);
                    descriptor.setModified(form.getModifiedDate());

                    descriptor.setReportDescription(form.getDescription());

                    // Validate that categoryId matches a category in this container
                    ViewCategory category = null != categoryId ? ViewCategoryManager.getInstance().getCategory(getContainer(), categoryId) : null;
                    if (null != category)
                        descriptor.setCategoryId(category.getRowId());

                    descriptor.setOwner(null);

                    User author = getUser();
                    ViewInfo.Status status = form.getStatus();
                    Date refreshDate = form.getRefreshDate();

                    if (author != null)
                        descriptor.setAuthor(getUser().getUserId());


                    int id = ReportService.get().saveReport(getViewContext(), newReportName, report);

                    report = (QueryReport) ReportService.get().getReport(getContainer(), id);

                    tx.commit();

                    ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

                    if (null != svc)
                        svc.queueThumbnailRendering(report, ThumbnailService.ImageType.Large, DataViewProvider.EditInfo.ThumbnailType.AUTO);

                    return report;
                }
            }
            return null;
        }

        public ActionURL getSuccessURL(CreateTeamQueryForm form)
        {
            return successurl;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }

    }
}