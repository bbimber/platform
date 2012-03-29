/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.study.query;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.study.StudyService;
import org.labkey.study.SampleManager;
import org.labkey.study.model.PrimaryType;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.model.SpecimenTypeSummary;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 14, 2012
 */
public abstract class BaseSpecimenPivotTable extends FilteredTable
{
    protected static final String AGGREGATE_DELIM = "::";
    protected static final String TYPE_DELIM = "-";

    public BaseSpecimenPivotTable(final TableInfo tinfo, final StudyQuerySchema schema)
    {
        super(tinfo, schema.getContainer());

        Logger.getInstance(BaseSpecimenPivotTable.class).debug("creating specimen pivot\n" +
                "SCHEMA=" + schema.getName() + " " + schema.getClass().getSimpleName()+"@"+System.identityHashCode(schema) + "\n" +
                "TABLE=" + tinfo.getName() + " " + this.getClass().getSimpleName() + "@" + System.identityHashCode(this),
                new Throwable("stack trace")
        );

        addWrapColumn(_rootTable.getColumn(StudyService.get().getSubjectColumnName(getContainer())));
        addWrapColumn(_rootTable.getColumn(StudyService.get().getSubjectVisitColumnName(getContainer())));
        addWrapColumn(_rootTable.getColumn("Visit"));
    }

    protected ColumnInfo wrapPivotColumn(ColumnInfo col, String descriptionFormat, String ...parts)
    {
        StringBuilder name = new StringBuilder();
        StringBuilder label = new StringBuilder();
        String delim = "";
        String labelDelim = "";

        for (String part : parts)
        {
            if (part != null)
            {
                name.append(delim).append(part);
                label.append(labelDelim).append(part);

                delim = "_";
                labelDelim = ":";
            }
        }
        ColumnInfo colInfo = new AliasedColumn(this, ColumnInfo.legalNameFromName(name.toString()), col);
        colInfo.setLabel(ColumnInfo.labelFromName(label.toString()));
        if (descriptionFormat != null)
            colInfo.setDescription(String.format(descriptionFormat, parts));

        return addColumn(colInfo);
    }
    
    /**
     * Returns a map of primary type id's to labels
     */
    protected Map<Integer, String> getPrimaryTypeMap(Container container)
    {
        Map<Integer, String> typeMap = new HashMap<Integer, String>();
        Map<String, Boolean> dupMap = new CaseInsensitiveHashMap<Boolean>();
        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());

        for (SpecimenTypeSummary.TypeCount type : summary.getPrimaryTypes())
        {
            if (type.getId() != null)
            {
                if (dupMap.containsKey(type.getLabel()))
                    dupMap.put(type.getLabel(), true);
                else
                    dupMap.put(type.getLabel(), false);
            }
        }

        for (SpecimenTypeSummary.TypeCount type : summary.getPrimaryTypes())
        {
            if (type.getId() != null)
                typeMap.put(type.getId(), getLabel(type.getLabel(), type.getId(), dupMap));
        }
        return typeMap;
    }

    /**
     * Returns a map of all primary types
     */
    protected Map<Integer, String> getAllPrimaryTypesMap(Container container) throws SQLException
    {
        Map<Integer, String> typeMap = new HashMap<Integer, String>();
        Map<String, Boolean> dupMap = new CaseInsensitiveHashMap<Boolean>();

        for (PrimaryType type : SampleManager.getInstance().getPrimaryTypes(container))
        {
            if (dupMap.containsKey(type.getPrimaryType()))
                dupMap.put(type.getPrimaryType(), true);
            else
                dupMap.put(type.getPrimaryType(), false);
        }


        for (PrimaryType type : SampleManager.getInstance().getPrimaryTypes(container))
        {
            typeMap.put((int)type.getRowId(), getLabel(type.getPrimaryType(), (int)type.getRowId(), dupMap));
        }
        return typeMap;
    }

    /**
     * Returns a map of derivative type id's to labels
     */
    protected Map<Integer, String> getDerivativeTypeMap(Container container)
    {
        Map<Integer, String> typeMap = new HashMap<Integer, String>();
        Map<String, Boolean> dupMap = new CaseInsensitiveHashMap<Boolean>();
        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());

        for (SpecimenTypeSummary.TypeCount type : summary.getDerivatives())
        {
            if (dupMap.containsKey(type.getLabel()))
                dupMap.put(type.getLabel(), true);
            else
                dupMap.put(type.getLabel(), false);
        }

        for (SpecimenTypeSummary.TypeCount type : summary.getDerivatives())
        {
            if (type.getId() != null)
                typeMap.put(type.getId(), getLabel(type.getLabel(), type.getId(), dupMap));
        }
        return typeMap;
    }

    /**
     * Returns a map of site id's to labels
     */
    protected Map<Integer, String> getSiteMap(Container container) throws SQLException
    {
        Map<Integer, String> siteMap = new HashMap<Integer, String>();
        Map<String, Boolean> dupMap = new CaseInsensitiveHashMap<Boolean>();

        for (SiteImpl site : SampleManager.getInstance().getSites(container))
        {
            if (dupMap.containsKey(site.getLabel()))
                dupMap.put(site.getLabel(), true);
            else
                dupMap.put(site.getLabel(), false);
        }

        for (SiteImpl site : SampleManager.getInstance().getSites(container))
            siteMap.put(site.getRowId(), getLabel(site.getLabel(), site.getRowId(), dupMap));

        return siteMap;
    }

    /**
     * use the row id to uniquify the column name, else just return the name
     */
    private String getLabel(String label, int id, Map<String, Boolean> dupMap)
    {
        if (dupMap.containsKey(label) && dupMap.get(label))
            return String.format("%s(%s)", label, id);
        else
            return label;
    }
}
