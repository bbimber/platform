package org.labkey.study.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.ImportTypesHelper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.SchemaReader;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of a SchemaReader that can infer dataset metadata from only the datafiles in the
 * datasets location of the study archive
 */
public class DatasetInferSchemaReader extends DatasetFileReader implements SchemaReader
{
    private Map<Integer, DatasetImportInfo> _datasetInfoMap = new LinkedHashMap<>();
    private List<ImportTypesHelper.Builder> _builders = new ArrayList<>();
    private static final Pattern DEFAULT_PATTERN = Pattern.compile("(^\\D*).(?:tsv|txt|xls|xlsx)$");

    public DatasetInferSchemaReader(VirtualFile datasetsDirectory, String datasetsFileName, StudyImpl study, StudyImportContext studyImportContext)
    {
        super(datasetsDirectory, datasetsFileName, study, studyImportContext);
    }

    @Override
    public OntologyManager.ImportPropertyDescriptorsList getImportPropertyDescriptors(DomainURIFactory factory, Collection<String> errors, Container defaultContainer)
    {
        initialize();
        return ImportTypesHelper.getImportPropertyDescriptors(_builders, factory, errors, defaultContainer);
    }

    @Override
    public Map<Integer, DatasetImportInfo> getDatasetInfo()
    {
        initialize();
        return _datasetInfoMap;
    }

    @Override
    protected Map<String, DatasetDefinition> getDatasetDefinitionMap()
    {
        Map<String, DatasetDefinition> dsMap = super.getDatasetDefinitionMap();
        Pattern filePattern = getDefaultDatasetPattern();

        // inferred datasets that do not yet exist on the server
        for (String name : _datasetsDirectory.list())
        {
            Matcher m = filePattern.matcher(name);
            if (!m.find())
                continue;
            String dsKey = getKeyFromDatasetName(m);
            DatasetDefinition ds = dsMap.get(dsKey.toLowerCase());
            if (ds == null)
            {
                dsMap.put(dsKey, new DatasetDefinition(_study, -1, dsKey, dsKey, null, null, null));
            }
        }
        return dsMap;
    }

    private void initialize()
    {
        if (_datasetInfoMap.isEmpty() && _builders.isEmpty())
        {
            List<Integer> datasetIds = _study.getDatasets().stream()
                .map(DatasetDefinition::getDatasetId)
                .sorted(Comparator.comparingInt(o -> o))
                .collect(Collectors.toList());

            // next available dataset ID
            int nextId = datasetIds.isEmpty() ? 1000 : datasetIds.get(datasetIds.size()-1) + 1;

            for (DatasetImportRunnable runnable : getRunnables())
            {
                ColumnDescriptor[] columns = runnable.getColumns();
                if (columns.length > 0)
                {
                    String name = FileUtil.getBaseName(runnable.getFileName());
                    DatasetDefinition def = _study.getDatasetByName(name);
                    DatasetImportInfo datasetInfo;
                    if (def != null)
                    {
                        datasetInfo = new DatasetImportInfo(name);

                        datasetInfo.category = def.getCategory();
                        datasetInfo.demographicData = def.isDemographicData();
                        datasetInfo.description = def.getDescription();

                        _datasetInfoMap.put(def.getDatasetId(), datasetInfo);
                    }
                    else
                    {
                        datasetInfo = new DatasetImportInfo(name);
                        _datasetInfoMap.put(nextId++, datasetInfo);
                    }

                    for (ColumnDescriptor col : columns)
                    {
                        PropertyType pt = PropertyType.getFromURI(null, col.getRangeURI(), null);
                        ImportTypesHelper.Builder pdb = new ImportTypesHelper.Builder(_study.getContainer(), pt);

                        pdb.setDomainName(datasetInfo.name);
                        pdb.setName(col.getColumnName());
                        pdb.setPropertyURI(col.propertyURI);
                        pdb.setNullable(true);
                        pdb.setMvEnabled(col.isMvEnabled());

                        _builders.add(pdb);
                    }
                }
            }
        }
    }

    @Override
    public String getTypeNameColumn()
    {
        return "PlateName";
    }

    protected Pattern getDefaultDatasetPattern()
    {
        return DEFAULT_PATTERN;
    }

    protected String getKeyFromDatasetName(Matcher matcher)
    {
        String key = matcher.group(1);
        if (key != null)
            return key;
        else
            return null;
    }
}