package org.labkey.study.controllers;

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.query.AliasManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.util.Pair;
import org.labkey.study.StudySchema;
import org.labkey.study.dataset.client.DatasetService;
import org.labkey.study.dataset.client.model.GWTDataset;
import org.labkey.study.model.*;
import org.labkey.study.pipeline.DatasetBatch;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.view.BaseStudyPage;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.util.*;


@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class OldStudyController extends BaseController
{
    static Logger _log = Logger.getLogger(OldStudyController.class);

    public Forward _renderInTemplate(HttpView view, String title, NavTree... navtrail) throws Exception
    {
        return _renderInTemplate(view, title, null, navtrail);
    }

    /** make compatible with HomeTemplate.getNavTrailView() */
    public Forward _renderInTemplate(HttpView view, String title, String helpTopic, NavTree... navtrail) throws Exception
    {
        // add study link automatically
        Forward begin = forwardBegin();
        if (navtrail.length == 0 || !begin.toString().equals(navtrail[0].second))
        {
            NavTree[] temp = new NavTree[navtrail.length+1];
            temp[0] = new NavTree(null == getStudy(true) ? "New Study" : getStudy().getLabel(), forwardBegin());
            System.arraycopy(navtrail,0,temp,1,navtrail.length);
            navtrail=temp;
        }
        return super._renderInTemplate(view, title, helpTopic, navtrail);
    }


    private ViewForward forwardBegin()
    {
        try {return new ViewForward(studyURL("begin"));} catch (Exception x) {throw new RuntimeException(x);}
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_READ)
    protected Forward begin() throws Exception
    {
        return null;
    }

    private ViewForward forwardOverview() throws ServletException
    {
        return new ViewForward(studyURL("overview"));
    }

    private ViewForward forwardManageStudy() throws Exception
    {
        return new ViewForward(studyURL("manageStudy"));
    }

    private ViewForward forwardManageTypes() throws Exception
    {
        return new ViewForward(studyURL("manageTypes"));
    }

    private ViewForward forwardManageVisits() throws Exception
    {
        return new ViewForward(studyURL("manageVisits"));
    }

    public static class StudyJspView<T> extends JspView<T>
    {
        public StudyJspView(Study study, String name, T bean)
        {
            super("/org/labkey/study/view/" + name, bean);
            if (getPage() instanceof BaseStudyPage)
                ((BaseStudyPage)getPage()).init(study);
        }
    }


    public class DatasetDetailsBean
    {
        public DatasetDetailsBean(Study study, User user, DataSetDefinition def)
        {
            this.study = study;
            this.permissions = study.getContainer().getAcl().getPermissions(user);
            this.dataset = def;
        }

        public OldStudyController controller = OldStudyController.this;
        public Study study;
        public int permissions;
        public DataSetDefinition dataset;
    }


    private ViewForward forwardDatasetDetails(int id) throws Exception
    {
        return new ViewForward(studyURL("datasetDetails", "id", String.valueOf(id)));
    }


/*    @Jpf.Action
    protected Forward viewDataType(IdForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);

        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getContainer(), form.getId());
        if (def == null)
            return HttpView.throwNotFound();
        String typeURI = def.getTypeURI();

        DbSchema s = ExperimentService.get().getSchema();
        Filter uriFilter = s.getSqlDialect().createSimpleFilter("DomainURI", typeURI);
        PropertyDescriptor[] props = Table.select(s.getTable("PropertyDescriptor"),
                Table.ALL_COLUMNS, uriFilter, s.getSqlDialect().createSort("PropertyId"), PropertyDescriptor.class);

        TypeSummaryPage page = (TypeSummaryPage) JspLoader.createPage(getRequest(),
                VisitSummaryPage.class, "typeSummary.jsp");
        page.init(typeURI, props);
        Study study = getStudy();
        NavTree[] navTrail = new NavTree[]{
                new NavTree(study.getLabel(), forwardOverview()),
                new NavTree("Manage Study", forwardManageStudy()),
                new NavTree("Manage Forms/Assays", forwardManageTypes()),
                new NavTree(def.getLabel(), getActionURL().relativeUrl("dataSetSummary", "id=" + def.getDataSetId())),
                new NavTree(def.getLabel() + " Properties")};
        return _renderInTemplate(page, typeURI, navTrail);
    } */


    Forward forwardDefineDatasetType(int datasetId)
    {
        try {return new ViewForward(studyURL("defineDatasetType", DataSetDefinition.DATASETKEY, String.valueOf(datasetId)));} catch (Exception x) {throw new RuntimeException(x);}
    }




/*    public static class BulkImportDataTypes
    {
        private String labelColumn;
        private String typeNameColumn;
        private String typeIdColumn;
        private String tsv;

        public BulkImportDataTypes(String name, String labelColumn, String typeIdColumn, String tsv)
        {
            this.typeNameColumn = name;
            this.labelColumn = labelColumn;
            this.typeIdColumn = typeIdColumn;
            this.tsv = tsv;
        }

        public String getTsv()
        {
            return tsv;
        }

        public String getTypeIdColumn()
        {
            return StringUtils.trimToEmpty(typeIdColumn);
        }

        public String getLabelColumn()
        {
            return StringUtils.trimToEmpty(labelColumn);
        }

        public String getTypeNameColumn()
        {
            return typeNameColumn;
        }

        public void setTypeNameColumn(String typeNameColumn)
        {
            this.typeNameColumn = typeNameColumn;
        }
    } */


    private String getDomainURI(Container c, DataSetDefinition def)
    {
        if (null == def)
            return getDomainURI(c, null, 0);
        else
            return getDomainURI(c, def.getName(), def.getDataSetId());
    }

    private String getDomainURI(Container c, String name, int datasetId)
    {
        return (new DatasetDomainKind()).generateDomainURI(c, name, datasetId);
/*        StringBuilder builder = new StringBuilder();
        ActionURL helper = getActionURL();
        Container c = ContainerManager.getForPath(helper.getExtraPath());
        
        builder.append(helper.getBaseServerURI());
        builder.append(helper.getContextPath());
        builder.append(helper.getExtraPath());
        builder.append(".").append(c.getId());
        if (typeName != null)
            builder.append("#").append(typeName);
        return builder.toString(); */
    }



    public static class ImportDataSetForm extends FormData
    {
        private int datasetId = 0;
        private String typeURI;
        private String tsv;
        private String keys;
        private Container container;


        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

        public String getKeys()
        {
            return keys;
        }

        public void setKeys(String keys)
        {
            this.keys = keys;
        }

        public String getTypeURI()
        {
            return typeURI;
        }

        public void setTypeURI(String typeURI)
        {
            this.typeURI = typeURI;
        }

        public Container getContainer() {
            return container;
        }

        public void setContainer(Container container) {
            this.container = container;
        }
    }



    public ViewForward forwardDataset(int datasetId)
    {
        try
        {
            return new ViewForward(studyURL("dataset", DataSetDefinition.DATASETKEY, String.valueOf(datasetId)));
        }
        catch (Exception x) {throw new RuntimeException(x);}
    }


    public ViewForward forwardDataset(int datasetId, Visit visit)
    {
        try
        {
            return new ViewForward(studyURL("dataset",
                    DataSetDefinition.DATASETKEY, String.valueOf(datasetId),
                    Visit.VISITKEY, "" + visit.getRowId()));
        }
        catch (Exception x) {throw new RuntimeException(x);}
    }

    public static class SourceLsidForm extends FormData
    {
        private String _sourceLsid;

        public String getSourceLsid()
        {
            return _sourceLsid;
        }

        public void setSourceLsid(String sourceLsid)
        {
            _sourceLsid = sourceLsid;
        }
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_READ)
    protected Forward defaultDatasetReport() throws Exception
    {
        ViewContext context = getViewContext();
        int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));

        ActionURL url = cloneActionURL();
        url.setAction("datasetReport");

        String defaultView = OldStudyController.getDefaultView(context, datasetId);
        if (!StringUtils.isEmpty(defaultView))
            url.addParameter("Dataset.viewName", defaultView);

        HttpView.throwRedirect(url);
        return null;
    }

    public static class ReportHeader extends HttpView
    {
        private Report _report;

        public ReportHeader(Report report)
        {
            _report = report;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (!StringUtils.isEmpty(_report.getDescriptor().getReportDescription()))
            {
                out.print("<table class='normal'>");
                out.print("<tr><td><span class='navPageHeader'>Report Description:</span>&nbsp;</td>");
                out.print("<td>" + _report.getDescriptor().getReportDescription() + "</td></tr>");
                out.print("</table>");
            }
        }
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_READ)
    protected Forward template() throws Exception
    {
        Study study = getStudy();
        ViewContext context = getViewContext();

        int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));
        DataSetDefinition def = getStudyManager().getDataSetDefinition(study, datasetId);
        if (null == def)
            return typeNotFound(datasetId);
        String typeURI = def.getTypeURI();
        if (null == typeURI)
            return typeNotFound(datasetId);

        //TODO: This may unnecessarily select into temp table.
        //Make public entry point for tableInfo without temp table
        TableInfo tinfo = def.getTableInfo(getUser());

        DataRegion dr = new DataRegion();
        dr.setTable(tinfo);

        Set ignoreColumns = new CaseInsensitiveHashSet("lsid", "datasetid", "visitdate", "sourcelsid", "created", "modified", "visitrowid", "day");
        if (study.isDateBased())
            ignoreColumns.add("SequenceNum");

        for (ColumnInfo col : tinfo.getColumns())
        {
            if (ignoreColumns.contains(col.getName()))
                continue;
            DataColumn dc = new DataColumn(col);
            //DO NOT use friendly names. We will import this later.
            dc.setCaption(col.getAlias());
            dr.addColumn(dc);
        }
        DisplayColumn replaceColumn = new SimpleDisplayColumn();
        replaceColumn.setCaption("replace");
        dr.addColumn(replaceColumn);

        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause("0 = 1", new Object[]{});

        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(getContainer());
        ctx.setBaseFilter(filter);

        ResultSet rs = dr.getResultSet(ctx);
        List<DisplayColumn> cols = dr.getDisplayColumns();
        ExcelWriter xl = new ExcelWriter(rs, cols);
        xl.write(getResponse());
        return null;
    }


    /*
    private boolean isPlottable(PropertyColumn[] propCols)
    {
        int count = 0;
        for (PropertyColumn propCol : propCols)
        {
            PropertyDescriptor pd = propCol.getPropertyDescriptor();
            if (pd.getPropertyType().getStorageType() == 'f')
                if (++count >= 2)
                    return true;
        }

        return false;
    }
    */


    private ViewForward typeNotFound(int datasetId)
            throws URISyntaxException
    {
        return new ViewForward(getActionURL().relativeUrl("typeNotFound", "id=" + datasetId));
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_DELETE)
    public Forward purgeDataset() throws Exception
    {
        // UNDONE: confirm page
        // CONSIDER: deleteDataset() that supports sequenceNum and filters

        ViewContext context = getViewContext();
        int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));

        if (isPost())
        {
            DataSetDefinition dataset = getStudyManager().getDataSetDefinition(getStudy(), datasetId);
            if (null == dataset)
                return HttpView.throwNotFound();

            String typeURI = dataset.getTypeURI();
            if (typeURI == null)
                return typeNotFound(datasetId);

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try
            {
                scope.beginTransaction();
                getStudyManager().purgeDataset(getStudy(), dataset);
                scope.commitTransaction();
            }
            finally
            {
                if (scope.isTransactionActive())
                    scope.rollbackTransaction();
            }
            DataRegionSelection.clearAll(getViewContext());
        }
        return forwardDataset(datasetId);
    }

    private ActionURL studyURL(String action, String... args) throws ServletException
    {
        return studyURL(getContainer(), action, args);
    }


    private static ActionURL studyURL(Container c, String action, String... args)
    {
        ActionURL url = new ActionURL("Study", action, c);
        if (null != args)
        {
            for (int i=0 ; i<args.length ; i+=2)
                url.addParameter(args[i], args[i+1]);
        }
        return url;
    }


    private NavTree[] getDataNavTrail(int datasetId, Visit visit, boolean includeDatasetLink) throws ServletException
    {
        List<NavTree> navTrail = new ArrayList<NavTree>();
        Study study = getStudy();
        navTrail.add(new NavTree(study.getLabel(), forwardBegin()));
        navTrail.add(new NavTree("Study Overview", forwardOverview()));
        if (datasetId > 0)
        {
            DataSetDefinition dataSet = getStudyManager().getDataSetDefinition(getStudy(), datasetId);
            if (dataSet != null)
            {
                String label = dataSet.getLabel() != null ? dataSet.getLabel() : "" + dataSet.getDataSetId();
                if (visit != null)
                    navTrail.add(new NavTree(label, forwardDataset(dataSet.getDataSetId())));
                else if (includeDatasetLink)
                    navTrail.add(new NavTree(label + " (All Visits)", forwardDataset(dataSet.getDataSetId())));
            }
        }
        if (includeDatasetLink && visit != null)
            navTrail.add(new NavTree(visit.getLabel() != null ? visit.getLabel() : "" + visit.getRowId(), forwardDataset(datasetId, visit)));
        return navTrail.toArray(new NavTree[navTrail.size()]);
    }


    private StudyManager getStudyManager()
    {
        return StudyManager.getInstance();
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward importStudyBatch(PipelineForm form) throws Exception
    {
        Container c = getContainer();
        String path = form.getPath();
        File definitionFile = null;

        if (path != null)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (root != null)
                definitionFile = root.resolvePath(path);
        }

        if (path == null || null == definitionFile || !definitionFile.exists() || !definitionFile.isFile())
        {
            HttpView.throwNotFound();
            return null;
        }

        File lockFile = StudyPipeline.lockForDataset(getStudy(), definitionFile);
        ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);

        if (!definitionFile.canRead())
            errors.add("main", new ActionMessage("Error", "Can't read dataset file: " + path));
        if (lockFile.exists())
            errors.add("main", new ActionMessage("Error", "Lock file exists.  Delete file before running import. " + lockFile.getName()));

        DatasetBatch batch = new DatasetBatch(new ViewBackgroundInfo(this), definitionFile);
        if (errors.size() == 0)
        {
            List<String> parseErrors = new ArrayList<String>();
            batch.prepareImport(parseErrors);
            for (String error : parseErrors)
                errors.add("main", new ActionMessage("Error", error));
        }

        GroovyView view = new GroovyView("/org/labkey/study/view/importStudyBatch.gm");
        view.addObject("batch", batch);
        view.addObject("path", path);
        return _renderInTemplate(view, "Import Study Batch - " + path);
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward submitStudyBatch(PipelineForm form) throws Exception
    {
        Study study = getStudy();
        Container c = getContainer();
        String path = form.getPath();
        File f = null;

        if (path != null)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (root != null)
                f = root.resolvePath(path);
        }

        if (null == f || !f.exists() || !f.isFile())
        {
            HttpView.throwNotFound();
            return null;
        }

//        File logFile = StudyPipeline.logForDataset(study, f);
        File lockFile = StudyPipeline.lockForDataset(study, f);
        if (!f.canRead() || lockFile.exists())
            return importStudyBatch(form);

        DatasetBatch batch = new DatasetBatch(new ViewBackgroundInfo(this), f);
        batch.submit();

        HttpView.throwRedirect(PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c));
        return null;
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward resetPipeline() throws Exception
    {
        Container c = getContainer();
        String path = (String)getViewContext().get("path");
        String redirect = (String)getViewContext().get("redirect");

        File f = null;

        if (path != null)
        {

            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (root != null)
                f = root.resolvePath(path);
        }

        if (null != f && f.exists() && f.isFile() && f.getPath().endsWith(".lock"))
        {
            f.delete();
        }

        if (null != redirect)
            return HttpView.throwRedirect(redirect);
        else
            return HttpView.throwRedirect(PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c));
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward visitDisplayOrder(ReorderForm form) throws Exception
    {
        if (isPost())
        {
            String order = form.getOrder();
            if (order != null && order.length() > 0)
            {
                String[] orderedIds = order.split(",");
                for (int i = 0; i < orderedIds.length; i++)
                {
                    int id = Integer.parseInt(orderedIds[i]);
                    Visit visit = getStudyManager().getVisitForRowId(getStudy(), id);
                    if (visit.getDisplayOrder() != i)
                    {
                        visit = visit.createMutable();
                        visit.setDisplayOrder(i);
                        getStudyManager().updateVisit(getUser(), visit);
                    }
                }
            }
            return forwardManageVisits();
        }
        Study study = getStudy();
        NavTree[] navTrail = new NavTree[]{
                new NavTree(study.getLabel(), forwardBegin()),
                new NavTree("Manage Study", forwardManageStudy()),
                new NavTree("Manage Visits", forwardManageVisits()),
                new NavTree("Display Order")};
        return _renderInTemplate(new StudyJspView<Object>(getStudy(), "visitDisplayOrder.jsp", null), "Visit Display Order", navTrail);
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward dataSetDisplayOrder(ReorderForm form) throws Exception
    {
        if (isPost())
        {
            String order = form.getOrder();
            if (order != null && order.length() > 0)
            {
                String[] orderedIds = order.split(",");
                for (int i = 0; i < orderedIds.length; i++)
                {
                    int id = Integer.parseInt(orderedIds[i]);
                    DataSetDefinition def = getStudyManager().getDataSetDefinition(getStudy(), id);
                    if (def.getDisplayOrder() != i)
                    {
                        def = def.createMutable();
                        def.setDisplayOrder(i);
                        getStudyManager().updateDataSetDefinition(getUser(), def);
                    }
                }
            }
            return forwardManageTypes();
        }
        Study study = getStudy();
        NavTree[] navTrail = new NavTree[]{
                new NavTree(study.getLabel(), forwardBegin()),
                new NavTree("Manage Study", forwardManageStudy()),
                new NavTree("Manage Datasets", forwardManageTypes()),
                new NavTree("Display Order")};
        return _renderInTemplate(new StudyJspView<Object>(getStudy(), "dataSetDisplayOrder.jsp", null), "Dataset Display Order", "manageDatasets", navTrail);
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward dataSetVisibility(DatasetPropertyForm form) throws Exception
    {
        if (isPost())
        {
            int[] allIds = form.getIds();
            int[] visibleIds = form.getVisible();
            Set<Integer> visible = new HashSet<Integer>(visibleIds.length);
            for (int id : visibleIds)
                  visible.add(id);
            for (int i = 0; i < allIds.length; i++)
            {
                DataSetDefinition def = getStudyManager().getDataSetDefinition(getStudy(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String category = form.getExtraData()[i];
                Integer cohortId = form.getCohort()[i];
                if (cohortId == -1)
                    cohortId = null;
                String label = form.getLabel()[i];
                if (def.isShowByDefault() != show || !stringsEqual(category, def.getCategory()) || !stringsEqual(label, def.getLabel()) || cohortId != def.getCohortId())
                {
                    def = def.createMutable();
                    def.setShowByDefault(show);
                    def.setCategory(category);
                    def.setCohortId(cohortId);
                    def.setLabel(label);
                    getStudyManager().updateDataSetDefinition(getUser(), def);
                }
            }
            return forwardManageTypes();
        }
        Study study = getStudy();
        NavTree[] navTrail = new NavTree[]{
                new NavTree(study.getLabel(), forwardBegin()),
                new NavTree("Manage Study", forwardManageStudy()),
                new NavTree("Manage Datasets", forwardManageTypes()),
                new NavTree("Properties")};
        return _renderInTemplate(new StudyJspView<Object>(getStudy(), "dataSetVisibility.jsp", null), "Dataset Properties", "manageDatasets", navTrail);
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward visitVisibility(VisitPropertyForm form) throws Exception
    {
        if (isPost())
        {
            int[] allIds = form.getIds() == null ? new int[0] : form.getIds();
            int[] visibleIds = form.getVisible() == null ? new int[0] : form.getVisible();
            Set<Integer> visible = new HashSet<Integer>(visibleIds.length);
            for (int id : visibleIds)
                visible.add(id);
            if (allIds.length != form.getLabel().length)
                throw new IllegalStateException("Arrays must be the same length.");
            for (int i = 0; i < allIds.length; i++)
            {
                Visit def = getStudyManager().getVisitForRowId(getStudy(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String label = form.getLabel()[i];
                String typeStr = form.getExtraData()[i];
                Integer cohortId = form.getCohort()[i];
                if (cohortId == -1)
                    cohortId = null;
                Character type = typeStr != null && typeStr.length() > 0 ? typeStr.charAt(0) : null;
                if (def.isShowByDefault() != show || !stringsEqual(label, def.getLabel()) || type != def.getTypeCode() || cohortId != def.getCohortId())
                {
                    def = def.createMutable();
                    def.setShowByDefault(show);
                    def.setLabel(label);
                    def.setCohortId(cohortId);
                    def.setTypeCode(type);
                    getStudyManager().updateVisit(getUser(), def);
                }
            }
            return forwardManageVisits();
        }
        Study study = getStudy();
        NavTree[] navTrail = new NavTree[]{
                new NavTree(study.getLabel(), forwardBegin()),
                new NavTree("Manage Study", forwardManageStudy()),
                new NavTree("Manage Visits", forwardManageVisits()),
                new NavTree("Properties")};
        return _renderInTemplate(new StudyJspView<Object>(getStudy(), "visitVisibility.jsp", null), "Visit Properties", "editVisits", navTrail);
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward manageUndefinedTypes() throws Exception
    {
        Study study = getStudy();
        NavTree[] navTrail = new NavTree[]{
                new NavTree(study.getLabel(), forwardBegin()),
                new NavTree("Manage Study", forwardManageStudy()),
                new NavTree("Manage Datasets", forwardManageTypes()),
                new NavTree("Define Dataset Schemas")};
        return _renderInTemplate(new StudyJspView<Object>(getStudy(), "manageUndefinedTypes.jsp", null), "Define Dataset Schemas", "manuallyDefineSchema", navTrail);
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward confirmDeleteDataset(IdForm form) throws Exception
    {
        int datasetId = form.getId();
        DataSetDefinition def = getStudyManager().getDataSetDefinition(getStudy(), datasetId);
        HttpView view = new HtmlView(
                "<form method=post action=\"deleteDataset.post?id=" + datasetId + "\">" +
                "Are you sure you want to delete dataset '" + def.getDisplayString() + "'?  All related data and visitmap entries will also be deleted.<p />" +
                "<input type=image src=\"" + PageFlowUtil.buttonSrc("Delete Dataset") + "\" value=\"Delete\">&nbsp;\n" +
                "<input type=image src=\"" + PageFlowUtil.buttonSrc("Cancel") + "\" value=\"Cancel\" onclick=\"javascript:window.history.back(); return false;\">" +
                "</form>"
        );
        includeView(new DialogTemplate(view));
        return null;
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward deleteDataset(IdForm form) throws Exception
    {
        if (!"POST".equals(getRequest().getMethod()))
            return confirmDeleteDataset(form);

        DataSetDefinition ds = getStudyManager().getDataSetDefinition(getStudy(), form.getId());
        if (null == ds)
            return typeNotFound(form.getId());

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.beginTransaction();
            getStudyManager().deleteDataset(getStudy(), getUser(), ds);
            scope.commitTransaction();
            return forwardManageTypes();
        }
        finally
        {
            if (scope.isTransactionActive())
                scope.rollbackTransaction();
        }
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward confirmDeleteDatasetType(IdForm form) throws Exception
    {
        int datasetId = form.getId();
        DataSetDefinition def = getStudyManager().getDataSetDefinition(getStudy(), datasetId);
        HttpView view = new HtmlView(
                "<form method=post action=\"deleteDatasetType.post?id=" + datasetId + "\">" +
                "Are you sure you want to delete type '" + def.getTypeURI() + "'?  All data associated with this dataset will also be deleted.<p />" +
                "<input type=image src=\"" + PageFlowUtil.buttonSrc("Delete Type") + "\" value=\"Delete\">&nbsp;\n" +
                "<input type=image src=\"" + PageFlowUtil.buttonSrc("Cancel") + "\" value=\"Cancel\" onclick=\"javascript:window.history.back(); return false;\">" +
                "</form>"
        );
        includeView(new DialogTemplate(view));
        return null;
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward deleteDatasetType(IdForm form) throws Exception
    {
        if (!"POST".equals(getRequest().getMethod()))
            return confirmDeleteDatasetType(form);

        DataSetDefinition ds = getStudyManager().getDataSetDefinition(getStudy(), form.getId());
        if (null == ds)
            return typeNotFound(form.getId());

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.beginTransaction();
            getStudyManager().deleteDatasetType(getStudy(), getUser(), ds);
            scope.commitTransaction();
            return forwardDatasetDetails(form.getId());
        }
        finally
        {
            if (scope.isTransactionActive())
                scope.rollbackTransaction();
        }
    }


    public Forward fowardParticipant(String ptid)
    {
        try {return new ViewForward(studyURL("participant", "participantId", ptid));} catch (Exception x) {throw new RuntimeException(x);}
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_READ)
    public Forward plotChart() throws Exception
    {
        final ViewContext context = getViewContext();
/*
        final ReportDescriptor descriptor = ReportDescriptor.createFromURL(context.getActionURL().getQueryString());
        ReportService.get().renderReport(context, descriptor.getBean());

*/
        Report report = ReportService.get().createFromQueryString(context.getActionURL().getQueryString());
        if (report != null)
            report.renderReport(context);
        return null;
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_READ)
    /**
     * called from the participanAll.jsp view
     */
    public Forward savedChart(ParticipantForm form) throws Exception
    {
        final String participantId = form.getParticipantId();
        final int reportId = form.getReportId();

        Report report = ReportService.get().getReport(reportId);

        if ("plot".equals(form.getAction()))
        {
            if (report != null)
            {
                report.getDescriptor().setProperty("participantId", participantId);
                report.renderReport(getViewContext());
            }
        }
        else if ("delete".equals(form.getAction()))
        {
            ReportService.get().deleteReport(getViewContext(), report);
            ActionURL url = cloneActionURL();
            url.setAction("participant");
            url.replaceParameter("action", "none");

            return new ViewForward(url);
        }
        return null;
    }

    public static class StudyChartReport extends ChartQueryReport
    {
        public static final String TYPE = "Study.chartReport";

        public String getType()
        {
            return TYPE;
        }

        private TableInfo getTable(ViewContext context, ReportDescriptor descriptor) throws Exception
        {
            final int datasetId = Integer.parseInt(descriptor.getProperty("datasetId"));
            final Study study = StudyManager.getInstance().getStudy(context.getContainer());
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

            return def.getTableInfo(context.getUser());
        }

        public ResultSet generateResultSet(ViewContext context) throws Exception
        {
            ReportDescriptor descriptor = getDescriptor();
            final String participantId = descriptor.getProperty("participantId");
            final TableInfo tableInfo = getTable(context, descriptor);
            DataRegion dr = new DataRegion();
            dr.setTable(tableInfo);

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("participantId", participantId, CompareType.EQUAL);

            RenderContext ctx = new RenderContext(context);
            ctx.setContainer(context.getContainer());
            ctx.setBaseFilter(filter);

            return dr.getResultSet(ctx);
        }

        public ChartReportDescriptor.LegendItemLabelGenerator getLegendItemLabelGenerator()
        {
            return new ChartReportDescriptor.LegendItemLabelGenerator() {
                public String generateLabel(ViewContext context, ReportDescriptor descriptor, String itemName) throws Exception
                {
                    TableInfo table = getTable(context, descriptor);
                    if (table != null)
                    {
                        ColumnInfo info = table.getColumn(itemName);
                        return info != null ? info.getCaption() : itemName;
                    }
                    return itemName;
                }
            };
        }
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    public Forward updateParticipantVisits() throws Exception
    {
        getStudyManager().recomputeStudyDataVisitDate(getStudy());
        getStudyManager().getVisitManager(getStudy()).updateParticipantVisits();

        TableInfo tinfoParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        Integer visitDates = Table.executeSingleton(StudySchema.getInstance().getSchema(),
                "SELECT Count(VisitDate) FROM " + tinfoParticipantVisit + "\nWHERE Container = ?",
                new Object[] {getContainer()}, Integer.class);
        int count = null == visitDates ? 0 : visitDates;

        HttpView view = new HtmlView(
                "<div class=normal>" + count + " rows were updated.<p/>" +
                PageFlowUtil.buttonLink("Done", "manageVisits.view") +
                "</div>");
        includeView(new DialogTemplate(view));
        return null;
    }


    public static class ParticipantForm extends FormData
    {
        String participantId;
        Study study;
        int datasetId;
        double sequenceNum;
        Map<Integer, String> expandedState;
        String _action;
        int _reportId;
        Integer _cohortId;

        public String getParticipantId()
        {
            return participantId;
        }

        public void setParticipantId(String participantId)
        {
            this.participantId = participantId;
        }

        public Study getStudy()
        {
            return study;
        }

        public int getDatasetId(){return datasetId;}
        public void setDatasetId(int datasetId){this.datasetId = datasetId;}

        public double getSequenceNum(){return sequenceNum;}
        public void setSequenceNum(double sequenceNum){this.sequenceNum = sequenceNum;}

        public Map<Integer, String> getExpandedState(){return expandedState;}

        public String getAction(){return _action;}
        public void setAction(String action){_action = action;}

        public int getReportId(){return _reportId;}
        public void setReportId(int reportId){_reportId = reportId;}

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }
    }


    public static class DataSetForm extends ViewForm
    {
        private String _name;
        private String _label;
        private int _datasetId;
        private String _category;
        private boolean _showByDefault;
        private String _visitDatePropertyName;
        private String[] _visitStatus;
        private int[] _visitRowIds;
        private String _description;
        private Integer _cohortId;
        private boolean _demographicData;
        private boolean _create;

        public ActionErrors validate(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            if (_datasetId < 1)
                addActionError("DatasetId must be greater than zero.");
            if (null == StringUtils.trimToNull(_label))
                addActionError("Label is required.");
            return getActionErrors();
        }


        public boolean isShowByDefault()
        {
            return _showByDefault;
        }

        public void setShowByDefault(boolean showByDefault)
        {
            _showByDefault = showByDefault;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }

        public String getDatasetIdStr()
        {
            return _datasetId > 0 ? String.valueOf(_datasetId) : "";
        }

        /**
         * Don't blow up when posting bad value
         * @param dataSetIdStr
         */
        public void setDatasetIdStr(String dataSetIdStr)
        {
            try
            {
                if (null == StringUtils.trimToNull(dataSetIdStr))
                    _datasetId = 0;
                else
                    _datasetId = Integer.parseInt(dataSetIdStr);
            }
            catch (Exception x)
            {
                _datasetId = 0;
            }
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String[] getVisitStatus()
        {
            return _visitStatus;
        }

        public void setVisitStatus(String[] visitStatus)
        {
            _visitStatus = visitStatus;
        }

        public int[] getVisitRowIds()
        {
            return _visitRowIds;
        }

        public void setVisitRowIds(int[] visitIds)
        {
            _visitRowIds = visitIds;
        }

        public String getVisitDatePropertyName()
        {
            return _visitDatePropertyName;
        }

        public void setVisitDatePropertyName(String _visitDatePropertyName)
        {
            this._visitDatePropertyName = _visitDatePropertyName;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public boolean isDemographicData()
        {
            return _demographicData;
        }

        public void setDemographicData(boolean demographicData)
        {
            _demographicData = demographicData;
        }

        public boolean isCreate()
        {
            return _create;
        }

        public void setCreate(boolean create)
        {
            _create = create;
        }

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }
    }


    public static class StudyPropertiesForm extends FormData
    {
        private String _label;
        private boolean _dateBased;
        private Date _startDate;
        private String _startDateString;
        private Study _study;
        private boolean _simpleRepository = true;

        @Override
        public void reset(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            _study = StudyManager.getInstance().getStudy(HttpView.currentContext().getContainer());
            if (null != _study)
            {
                _dateBased = _study.isDateBased();
                setStartDate(_study.getStartDate());
                _label = _study.getLabel();
            }
            else
            {
                _label = HttpView.currentContext().getContainer().getName() + " Study";
                setStartDate(new Date());
            }
        }


        @Override
        public ActionErrors validate(ActionMapping mapping, HttpServletRequest request)
        {
            ActionErrors actionErrors = PageFlowUtil.getActionErrors(request, true);
            _startDateString = StringUtils.trimToNull(_startDateString);
            if (null != _startDateString)
            {
                try
                {
                    _startDate = (Date) ConvertUtils.convert(_startDateString, Date.class);
                }
                catch (ConversionException e)
                {
                    actionErrors.add("main", new ActionMessage("Error", "Start Date is not a legal date"));
                }
            }

            if (_dateBased && null == _startDate)
                actionErrors.add("main", new ActionMessage("Error", "Start date must be supplied for a date-based study."));

            _label = StringUtils.trimToNull(_label);
            if (null == _label)
                actionErrors.add("main", new ActionMessage("Error", "Please supply a label"));

            return actionErrors;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public boolean isDateBased()
        {
            return _dateBased;
        }

        public void setDateBased(boolean dateBased)
        {
            _dateBased = dateBased;
        }

        public Date getStartDate()
        {
            return _startDate;
        }

        public void setStartDate(Date startDate)
        {
            _startDateString = startDate == null ? null : DateUtil.formatDate(startDate);
            _startDate = startDate;
        }

        public String getStartDateString()
        {
            return _startDateString;
        }

        public void setStartDateString(String startDateString)
        {
            _startDateString = startDateString;
        }

        public boolean isSimpleRepository()
        {
            return _simpleRepository;
        }

        public void setSimpleRepository(boolean simpleRepository)
        {
            _simpleRepository = simpleRepository;
        }
    }

    public static class ReorderForm extends FormData
    {
        private String _order;

        public String getOrder()
        {
            return _order;
        }

        public void setOrder(String order)
        {
            _order = order;
        }
    }

    public abstract static class PropertyForm extends FormData
    {
        private String[] _label;
        private String[] _extraData;
        private int[] _cohort;

        public String[] getExtraData()
        {
            return _extraData;
        }

        public void setExtraData(String[] extraData)
        {
            _extraData = extraData;
        }

        public String[] getLabel()
        {
            return _label;
        }

        public void setLabel(String[] label)
        {
            _label = label;
        }

        public int[] getCohort()
        {
            return _cohort;
        }

        public void setCohort(int[] cohort)
        {
            _cohort = cohort;
        }
    }

    public static class VisitPropertyForm extends PropertyForm
    {
        private int[] _ids;
        private int[] _visible;

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public int[] getVisible()
        {
            return _visible;
        }

        public void setVisible(int[] visible)
        {
            _visible = visible;
        }
    }

    public static class DatasetPropertyForm extends PropertyForm
    {
        private int[] _ids;
        private int[] _visible;

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public int[] getVisible()
        {
            return _visible;
        }

        public void setVisible(int[] visible)
        {
            _visible = visible;
        }
    }

    /**
     * Adds next and prev buttons to the participant view
     */
    public static class ParticipantNavView extends HttpView
    {
        private String _prevURL;
        private String _nextURL;
        private String _display;

        public ParticipantNavView(String prevURL, String nextURL, String display)
        {
            _prevURL = prevURL;
            _nextURL = nextURL;
            _display = display;
        }

        public ParticipantNavView(String prevURL, String nextURL)
        {
            this(prevURL, nextURL, null);
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            out.print("<table><tr><td align=\"left\">");
            if (_prevURL == null)
                out.print("[< Previous Participant]");
            else
                out.print("[<a href=\"" + _prevURL + "\">< Previous Participant</a>]");
            out.print("&nbsp;");

            if (_nextURL == null)
                out.print("[Next Participant >]");
            else
                out.print("[<a href=\"" + _nextURL + "\">Next Participant ></a>]");

            if (_display != null)
            {
                out.print("</td><td class=\"ms-searchform\">");
                out.print(PageFlowUtil.filter(_display));
            }
            out.print("</td></tr></table>");
        }
    }


    private static void _logError(Exception x)
    {
        Logger.getLogger(OldStudyController.class).error("Unexpected error", x);
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward manageSnapshot(SnapshotForm form) throws Exception
    {
        Study study = getStudy();
        NavTree[] navTrail = new NavTree[]{
                new NavTree(study.getLabel(), forwardBegin()),
                new NavTree("Manage Study", forwardManageStudy()),
                new NavTree("Manage Snapshot"),};

        return _renderInTemplate(new StudyJspView<SnapshotForm>(study, "snapshotData.jsp", form), "Snapshot Study Data", navTrail);
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path="manageSnapshot.do", name = "validate")) @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward snapshot(SnapshotForm form) throws Exception
    {
        StudyManager.getInstance().createSnapshot(getUser(), form.getBean());
        form.setComplete(true);
        return manageSnapshot(form);
    }

    public static class SnapshotForm extends FormData
    {
        private boolean confirm;
        private boolean complete;
        private String message;
        private StudyManager.SnapshotBean snapshotBean;
        private String[] sourceName;
        private String[] destName;
        private String[] category;
        private boolean[] snapshot;

        public boolean isConfirm()
        {
            return confirm;
        }

        public void setConfirm(boolean confirm)
        {
            this.confirm = confirm;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        @Override
        public void reset(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            try
            {
                snapshotBean = StudyManager.getInstance().getSnapshotInfo(HttpView.currentContext().getUser(), HttpView.currentContext().getContainer());
            }
            catch (ServletException e)
            {
                throw new RuntimeException(e);
            }

            int tableCount = 0;
            for (String category : snapshotBean.getCategories())
                tableCount += snapshotBean.getSourceNames(category).size();

            category = new String[tableCount];
            destName = new String[tableCount];
            sourceName = new String[tableCount];
            snapshot = new boolean[tableCount];
        }


        @Override
        public ActionErrors validate(ActionMapping mapping, HttpServletRequest request)
        {
            ActionErrors errors = new ActionErrors();
            String schemaName = StringUtils.trimToNull(getSchemaName());
            if (null == schemaName)
                errors.add("main", new ActionMessage("Error", "You must supply a schema name."));
            else if (!AliasManager.isLegalName(schemaName))
                errors.add("main", new ActionMessage("Error", "Schema name must be a legal database identifier"));
            else
            {
                boolean badName = false;
                for (Module module : ModuleLoader.getInstance().getModules())
                {
                    for (String schema : module.getSchemaNames())
                        if (schemaName.equalsIgnoreCase(schema))
                        {
                            errors.add("main", new ActionMessage("Error", "The schema name " + schema + " is already in use by the " + module.getName() + " module. Please pick a new name"));
                            badName = true;
                            break;
                        }
                    if (badName)
                        break;
                }
                if (schemaName.equalsIgnoreCase("temp"))
                    errors.add("main", new ActionMessage("Error", "'Temp' is a reserved schema name. Please choose a new name"));
            }

            CaseInsensitiveHashSet names = new CaseInsensitiveHashSet();
            StudyManager.SnapshotBean bean = getBean();
            for (String category : bean.getCategories())
                for (String sourceName : bean.getSourceNames(category))
                {
                    String destTableName = bean.getDestTableName(category, sourceName);
                    if (!AliasManager.isLegalName(destTableName))
                        errors.add("main", new ActionMessage("Error", "Not a legal table name: " + destTableName));
                    if (bean.isSaveTable(category, sourceName) && !names.add(destTableName))
                        errors.add("main", new ActionMessage("Error", "Duplicate table name: " + destTableName));
                }

            return errors;
        }

        public String getSchemaName()
        {
            return snapshotBean.getSchemaName();
        }

        public void setSchemaName(String schemaName)
        {
            snapshotBean.setSchemaName(schemaName);
        }

        public StudyManager.SnapshotBean getBean()
        {
            for (int i = 0; i < category.length; i++)
            {
                if (null != category[i] && null != sourceName[i])
                {
                    snapshotBean.setSnapshot(category[i], sourceName[i], snapshot[i]);
                    snapshotBean.setDestTableName(category[i], sourceName[i], destName[i]);
                }
            }

            return snapshotBean;
        }

        public String[] getSourceName()
        {
            return sourceName;
        }

        public void setSourceName(String[] sourceName)
        {
            this.sourceName = sourceName;
        }

        public String[] getDestName()
        {
            return destName;
        }

        public void setDestName(String[] destName)
        {
            this.destName = destName;
        }

        public String[] getCategory()
        {
            return category;
        }

        public void setCategory(String[] category)
        {
            this.category = category;
        }

        public boolean[] getSnapshot()
        {
            return snapshot;
        }

        public void setSnapshot(boolean[] snapshot)
        {
            this.snapshot = snapshot;
        }

        public boolean isComplete()
        {
            return complete;
        }

        public void setComplete(boolean complete)
        {
            this.complete = complete;
        }
    }


    private Forward forwardEditType(int id, boolean create) throws ServletException
    {
        return new ViewForward(studyURL("editType", "datasetId", String.valueOf(id), "create", Boolean.toString(create)));
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_READ)
    protected Forward viewPreferences() throws Exception
    {
        Study study = getStudy();
        List<NavTree> navTrail = new ArrayList<NavTree>();

        navTrail.add(new NavTree(study.getLabel(), forwardBegin()));

        String id = getRequest().getParameter(DataSetDefinition.DATASETKEY);
        String defaultView = getRequest().getParameter("defaultView");

        if (NumberUtils.isNumber(id))
        {
            int dsid = NumberUtils.toInt(id);
            DataSetDefinition def = getStudyManager().getDataSetDefinition(study, dsid);
            if (def != null)
            {
                List<Pair<String, String>> views = ReportManager.get().getReportLabelsForDataset(getViewContext(), def);
                if (defaultView != null)
                {
                    setDefaultView(getViewContext(), dsid, defaultView);
                }
                else
                {
                    defaultView = getDefaultView(getViewContext(), def.getDataSetId());
                    if (!StringUtils.isEmpty(defaultView))
                    {
                        boolean defaultExists = false;
                        for (Pair<String, String> view : views)
                        {
                            if (StringUtils.equals(view.getValue(), defaultView))
                            {
                                defaultExists = true;
                                break;
                            }
                        }
                        if (!defaultExists)
                            setDefaultView(getViewContext(), dsid, "");
                    }
                }
                String label = def.getLabel() != null ? def.getLabel() : "" + def.getDataSetId();

                ActionURL datasetUrl = cloneActionURL();
                datasetUrl.setAction("dataset");
                datasetUrl.setPageFlow("Study");
                navTrail.add(new NavTree(label, datasetUrl.getLocalURIString()));
                navTrail.add(new NavTree("View Preferences"));

                ViewPrefsBean bean = new ViewPrefsBean(views, def);
                return _renderInTemplate(new StudyJspView<ViewPrefsBean>(study, "viewPreferences.jsp", bean), "Set Default View", navTrail.toArray(new NavTree[navTrail.size()]));
            }
        }
        HttpView.throwNotFound("Invalid dataset ID");
        return null;
    }

    public static class ViewPrefsBean
    {
        private List<Pair<String, String>> _views;
        private DataSetDefinition _def;

        public ViewPrefsBean(List<Pair<String, String>> views, DataSetDefinition def)
        {
            _views = views;
            _def = def;
        }

        public List<Pair<String, String>> getViews(){return _views;}
        public DataSetDefinition getDataSetDefinition(){return _def;}
    }

    private static final String DEFAULT_DATASET_VIEW = "Study.defaultDatasetView";

    public static String getDefaultView(ViewContext context, int datasetId)
    {
        Map<String, String> viewMap = PropertyManager.getProperties(context.getUser().getUserId(),
                context.getContainer().getId(), DEFAULT_DATASET_VIEW, false);

        final String key = Integer.toString(datasetId);
        if (viewMap != null && viewMap.containsKey(key))
        {
            return viewMap.get(key);
        }
        return "";
    }

    public void setDefaultView(ViewContext context, int datasetId, String view)
    {
        Map<String, String> viewMap = PropertyManager.getWritableProperties(context.getUser().getUserId(),
                context.getContainer().getId(), DEFAULT_DATASET_VIEW, true);

        viewMap.put(Integer.toString(datasetId), view);
        PropertyManager.saveProperties(viewMap);
    }


    public static class RequirePipelineView extends StudyJspView<Boolean>
    {
        public RequirePipelineView(Study study, boolean showGoBack)
        {
            super(study, "requirePipeline.jsp", showGoBack);
        }
    }

    /* GWT Actions */

    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward datasetService() throws Exception
    {
        DatasetServiceImpl s = new DatasetServiceImpl(getViewContext());
        s.doPost(getRequest(), getResponse());
        return null;
    }


    class DatasetServiceImpl extends DomainEditorServiceBase implements DatasetService
    {
        public DatasetServiceImpl(ViewContext context)
        {
            super(context);
        }


        public GWTDataset getDataset(int id) throws Exception
        {
            try
            {
                DataSetDefinition dd = getStudy().getDataSet(id);
                if (null == dd)
                    return null;
                GWTDataset ds = new GWTDataset();
                PropertyUtils.copyProperties(ds, dd);
                ds.setDatasetId(dd.getDataSetId()); // upper/lowercase problem

                Cohort[] cohorts = StudyManager.getInstance().getCohorts(getContainer(), getUser());
                Map<String, String> cohortMap = new HashMap<String, String>();
                if (cohorts != null && cohorts.length > 0)
                {
                    cohortMap.put("All", "");
                    for (Cohort cohort : cohorts)
                        cohortMap.put(cohort.getLabel(), String.valueOf(cohort.getRowId()));
                }
                ds.setCohortMap(cohortMap);

                Map<String, String> visitDateMap = new HashMap<String, String>();
                TableInfo tinfo = dd.getTableInfo(null, false, false);
                for (ColumnInfo col : tinfo.getColumns())
                {
                    if (!Date.class.isAssignableFrom(col.getJavaClass()))
                        continue;
                    if (col.getName().equalsIgnoreCase("visitdate"))
                        continue;
                    if (col.getName().equalsIgnoreCase("modified"))
                        continue;
                    if (visitDateMap.isEmpty())
                        visitDateMap.put("", "");
                    visitDateMap.put(col.getName(), col.getName());
                }
                ds.setVisitDateMap(visitDateMap);
                return ds;
            }
            catch (Exception x)
            {
                _log.error("unexpected exception", x);
                throw x;
            }
        }


        public List updateDatasetDefinition(GWTDataset ds, GWTDomain orig, GWTDomain update) throws Exception
        {
            assert orig.getDomainURI().equals(update.getDomainURI());
            List<String> errors = new ArrayList<String>();

            if (!getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
            {
                errors.add("Unauthorized");
                return errors;
            }

            Domain d = PropertyService.get().getDomain(getContainer(), update.getDomainURI());
            if (null == d)
            {
                errors.add("Domain not found: " + update.getDomainURI());
                return errors;
            }

            if (!ds.getTypeURI().equals(orig.getDomainURI()) ||
                !ds.getTypeURI().equals(update.getDomainURI()))
            {
                errors.add("Illegal Argument");
                return errors;
            }

            errors = updateDomainDescriptor(orig, update);
            if (errors == null)
                errors = updateDataset(ds, orig.getDomainURI());

            return errors.isEmpty() ? null : errors;
        }

        private List updateDataset(GWTDataset ds, String domainURI) throws Exception
        {
            try
            {
                List<String> errors = new ArrayList<String>();

                // CONSIDER: optimistic concurrency validate against current
                // validate that this smells right
                DataSetDefinition def = getStudy().getDataSet(ds.getDatasetId());
                if (null == def)
                {
                    errors.add("Dataset not found");
                    return errors;
                }

                if (ds.getDemographicData() && !def.isDemographicData() && !StudyManager.getInstance().isDataUniquePerParticipant(def))
                {
                    errors.add("This dataset currently contains more than one row of data per participant. Demographic data includes one row of data per participant.");
                    return errors;
                }

                DataSetDefinition updated = def.createMutable();
                BeanUtils.copyProperties(updated, ds);

                String keyPropertyName = null;
                if (ds.getKeyPropertyName() != null)
                {
                    Domain domain = PropertyService.get().getDomain(getContainer(), domainURI);

                    for (DomainProperty dp : domain.getProperties())
                    {
                        if (dp.getName().equalsIgnoreCase(ds.getKeyPropertyName()))
                        {
                            keyPropertyName = dp.getName();
                            break;
                        }
                    }
                }
                updated.setKeyPropertyName(keyPropertyName);

                getStudyManager().updateDataSetDefinition(getUser(), updated);
                getStudyManager().uncache(def);

                return errors;
            }
            catch (Exception x)
            {
                _log.error("unexpected exception", x);
                throw x;
            }
        }

        public List updateDatasetDefinition(GWTDataset ds, GWTDomain domain, String tsv) throws Exception
        {
            List<String> errors = new ArrayList<String>();

            if (!getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
            {
                errors.add("Unauthorized");
                return errors;
            }

            Map[] maps = null;
            if (null != tsv && tsv.length() > 0)
            {
                TabLoader loader = new TabLoader(tsv, true);
                loader.setLowerCaseHeaders(true);
                maps = (Map[]) loader.load();
            }

            PropertyDescriptor[] pds = OntologyManager.importOneType(domain.getDomainURI(), maps, errors, getContainer());
            if (pds == null || pds.length == 0)
                errors.add("No properties were successfully imported.");

            if (errors.isEmpty())
                errors = updateDataset(ds, domain.getDomainURI());
            
            return errors.isEmpty() ? null : errors;
        }
    }
}
