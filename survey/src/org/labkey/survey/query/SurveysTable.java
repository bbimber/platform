/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.survey.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleTableDomainKind;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.view.ActionURL;
import org.labkey.survey.SurveyController;
import org.labkey.survey.SurveyManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 12/7/12
 */
public class SurveysTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public SurveysTable(TableInfo table, SurveyQuerySchema schema)
    {
        super(schema, table);

        List<FieldKey> defaultColumns = new ArrayList<FieldKey>(Arrays.asList(
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("CreatedBy"),
                FieldKey.fromParts("Created"),
                FieldKey.fromParts("SubmittedBy"),
                FieldKey.fromParts("Submitted"),
                FieldKey.fromParts("ModifiedBy"),
                FieldKey.fromParts("Modified"),
                FieldKey.fromParts("Status")
        ));
        setDefaultVisibleColumns(defaultColumns);

        ActionURL updateUrl = new ActionURL(SurveyController.UpdateSurveyAction.class, schema.getContainer());
        setUpdateURL(new DetailsURL(updateUrl, Collections.singletonMap("rowId", FieldKey.fromString("RowId"))));

        ActionURL deleteRowsURL = new ActionURL(SurveyController.DeleteSurveysAction.class, schema.getContainer());
        setDeleteURL(new DetailsURL(deleteRowsURL, Collections.singletonMap("rowId", FieldKey.fromString("RowId"))));
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public SimpleTableDomainKind getDomainKind()
    {
        if (getObjectUriColumn() == null)
            return null;

        return (SurveyTableDomainKind)PropertyService.get().getDomainKindByName(SurveyTableDomainKind.NAME);
    }

    @Override
    public Domain getDomain()
    {
        if (getObjectUriColumn() == null)
            return null;

        if (_domain == null)
        {
            String domainURI = getDomainURI();
            _domain = PropertyService.get().getDomain(SurveyTableDomainKind.getDomainContainer(getContainer()), domainURI);
        }
        return _domain;
    }

    @Override
    public String getDomainURI()
    {
        if (getObjectUriColumn() == null)
            return null;

        return SurveyTableDomainKind.getDomainURI(getUserSchema().getName(), getName(),
                SurveyTableDomainKind.getDomainContainer(getContainer()),
                getUserSchema().getUser());
    }

    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null)
        {
            DefaultQueryUpdateService.DomainUpdateHelper helper = new SimpleQueryUpdateService.SimpleDomainUpdateHelper(this)
            {
                @Override
                public Container getDomainContainer(Container c)
                {
                    return SurveyTableDomainKind.getDomainContainer(c);
                }
            };
            return new SurveysTableQueryUpdateService(this, table, helper);
        }
        return null;
    }

    private static class SurveysTableQueryUpdateService extends SimpleQueryUpdateService
    {
        public SurveysTableQueryUpdateService(SimpleUserSchema.SimpleTable queryTable, TableInfo dbTable, DomainUpdateHelper helper)
        {
            super(queryTable, dbTable, helper);
        }

        @Override
        protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
        {
            Map<String, Object> ret = super._insert(user, c, row);

            if (ret.containsKey("rowId"))
            {
                Survey survey = SurveyManager.get().getSurvey(c, user, (Integer)ret.get("rowId"));
                if (survey != null)
                    SurveyManager.get().fireCreatedSurvey(c, user, survey, ret);
            }
            return ret;
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            Map<String, Object> ret = super._update(user, c, row, oldRow, keys);

            Survey survey = SurveyManager.get().getSurvey(c, user, (Integer)keys[0]);
            if (survey != null)
                SurveyManager.get().fireUpdateSurvey(c, user, survey, oldRow, ret);

            return ret;
        }
    }
}
