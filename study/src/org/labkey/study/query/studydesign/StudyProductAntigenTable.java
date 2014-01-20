/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.study.query.studydesign;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 12/13/13.
 */
public class StudyProductAntigenTable extends DefaultStudyDesignTable
{
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("ProductId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Gene"));
        defaultVisibleColumns.add(FieldKey.fromParts("SubType"));
        defaultVisibleColumns.add(FieldKey.fromParts("GenBankId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Sequence"));
    }

    public StudyProductAntigenTable(Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(domain, dbSchema, schema);

        setName(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        setDescription("Contains one row per study product antigen");
    }

    @Override
    protected void initColumn(ColumnInfo col)
    {
        if ("ProductId".equalsIgnoreCase(col.getName()))
        {
            col.setFk(new LookupForeignKey("RowId")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
                }
            });
        }
        else if ("Gene".equalsIgnoreCase(col.getName()))
        {
            col.setFk(new LookupForeignKey("Name")
            {
                public TableInfo getLookupTableInfo()
                {
                    StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                    if (study != null)
                    {
                        StudyQuerySchema schema = new StudyQuerySchema(study, _userSchema.getUser(), false);
                        StudyDesignGenesTable result = new StudyDesignGenesTable(schema);
                        result.setContainerFilter(ContainerFilter.Type.CurrentPlusProject.create(_userSchema.getUser()));
                        return result;
                    }
                    else
                        return null;
                }
            });
        }
        else if ("SubType".equalsIgnoreCase(col.getName()))
        {
            col.setFk(new LookupForeignKey("Name")
            {
                public TableInfo getLookupTableInfo()
                {
                    StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                    if (study != null)
                    {
                        StudyQuerySchema schema = new StudyQuerySchema(study, _userSchema.getUser(), false);
                        StudyDesignSubTypesTable result = new StudyDesignSubTypesTable(schema);
                        result.setContainerFilter(ContainerFilter.Type.CurrentPlusProject.create(_userSchema.getUser()));
                        return result;
                    }
                    else
                        return null;
                }
            });
        }
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }
}
