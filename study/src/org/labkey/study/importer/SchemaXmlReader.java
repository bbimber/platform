/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.exp.property.Type;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.study.importer.DatasetImporter.DatasetImportProperties;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: May 26, 2009
 * Time: 10:31:36 AM
 */
public class SchemaXmlReader implements SchemaReader
{
    private static final String NAME_KEY = "PlateName";

    private final List<Map<String, Object>> _importMaps;
    private final Map<Integer, DataSetImportInfo> _datasetInfoMap;


    public SchemaXmlReader(StudyImpl study, File metaDataFile, Map<String, DatasetImportProperties> extraImportProps) throws IOException, XmlException
    {
        TablesDocument tablesDoc = TablesDocument.Factory.parse(metaDataFile);
        TablesDocument.Tables tablesXml = tablesDoc.getTables();

        _datasetInfoMap = new HashMap<Integer, DataSetImportInfo>(tablesXml.getTableArray().length);
        _importMaps = new ArrayList<Map<String, Object>>();

        for (TableType tableXml : tablesXml.getTableArray())
        {
            String datasetName = tableXml.getTableName();

            DataSetImportInfo info = new DataSetImportInfo(datasetName);
            DatasetImportProperties tableProps = extraImportProps.get(datasetName);

            info.category = tableProps.getCategory();
            info.name = datasetName;
            info.isHidden = !tableProps.isShowByDefault();
            info.label = tableXml.getTableTitle();

            // TODO:
            info.startDatePropertyName = null;
            info.visitDatePropertyName = null;

            _datasetInfoMap.put(tableProps.getId(), info);

            // Set up RowMap with all the keys that OntologyManager.importTypes() handles
            RowMapFactory<Object> mapFactory = new RowMapFactory<Object>(NAME_KEY, "Property", "Label", "ConceptURI", "RangeURI", "NotNull", "Hidden", "MvEnabled", "Description", "Format");

            for (ColumnType columnXml : tableXml.getColumns().getColumnArray())
            {
                String columnName = columnXml.getColumnName();

                // filter out the built-in types
                if (DataSetDefinition.isDefaultFieldName(columnName, study))
                    continue;

                String dataType = columnXml.getDatatype();
                Type t = Type.getTypeBySqlTypeName(dataType);

                if (t == null)
                    throw new IllegalStateException("Unknown property type '" + dataType + "' for property '" + columnXml.getColumnName() + "' in dataset '" + datasetName + "'.");

                // Assume nullable if not specified
                boolean notNull = columnXml.isSetNullable() && !columnXml.getNullable();

                Map<String, Object> map = mapFactory.getRowMap(new Object[]{
                    datasetName,
                    columnName,
                    columnXml.getColumnTitle(),
                    null,  // TODO: conceptURI
                    t.getXsdType(),
                    notNull,
                    columnXml.getIsHidden(),
                    null != columnXml.getMvColumnName(),
                    columnXml.getDescription(),
                    columnXml.getFormatString()
                });

                _importMaps.add(map);

                if (columnXml.getIsKeyField())
                {
                    if (null != info.keyPropertyName)
                        throw new IllegalStateException("More than one key specified: '" + info.keyPropertyName + "' and '" + columnName + "'");

                    info.keyPropertyName = columnName;

                    if (columnXml.getIsAutoInc())
                        info.keyManaged = true;
                }
            }
        }
    }

    public List<Map<String, Object>> getImportMaps()
    {
        return _importMaps;
    }

    public Map<Integer, DataSetImportInfo> getDatasetInfo()
    {
        return _datasetInfoMap;
    }

    public String getTypeNameColumn()
    {
        return NAME_KEY;
    }
}