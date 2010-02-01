/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.experiment.api.property;

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.query.*;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.*;

/**
 * User: jeckels
 * Date: Jan 26, 2010
 */
public class LookupValidator extends DefaultPropertyValidator implements ValidatorKind
{
    public String getName()
    {
        return "Lookup Property Validator";
    }

    public IPropertyValidator createInstance()
    {
        PropertyValidatorImpl validator = new PropertyValidatorImpl(new PropertyValidator());
        validator.setTypeURI(getTypeURI());

        return validator;
    }

    public String getTypeURI()
    {
        return createValidatorURI(GWTPropertyValidator.TYPE_LOOKUP).toString();
    }

    public String getDescription()
    {
        return null;
    }

    public boolean isValid(IPropertyValidator validator, List<ValidationError> errors)
    {
        return true;
    }

    private static class LookupKey
    {
        private String _schema;
        private String _query;
        private String _container;

        public LookupKey(PropertyDescriptor field)
        {
            _schema = field.getLookupSchema();
            _query = field.getLookupQuery();
            _container = field.getLookupContainer();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LookupKey that = (LookupKey) o;

            if (_container != null ? !_container.equals(that._container) : that._container != null) return false;
            if (_query != null ? !_query.equals(that._query) : that._query != null) return false;
            return !(_schema != null ? !_schema.equals(that._schema) : that._schema != null);
        }

        @Override
        public int hashCode()
        {
            int result = _schema != null ? _schema.hashCode() : 0;
            result = 31 * result + (_query != null ? _query.hashCode() : 0);
            result = 31 * result + (_container != null ? _container.hashCode() : 0);
            return result;
        }
    }

    private class LookupValues extends HashSet<Object>
    {
        private TableInfo _tableInfo;
        private Container _container;

        public LookupValues(PropertyDescriptor field, Container defaultContainer, User user, List<ValidationError> errors)
        {
            if (field.getLookupContainer() != null)
            {
                _container = ContainerManager.getForId(field.getLookupContainer());
            }
            else
            {
                _container = defaultContainer;
            }

            if (user == null)
            {
                throw new IllegalArgumentException("Must supply a user");
            }

            if (_container == null)
            {
                errors.add(new SimpleValidationError("Could not find the lookup's target folder for field '" + field.getNonBlankCaption() + "'"));
            }
            else
            {
                UserSchema userSchema = QueryService.get().getUserSchema(user, defaultContainer, field.getLookupSchema());
                if (userSchema == null)
                {
                    errors.add(new SimpleValidationError("Could not find the lookup's target schema ('" + field.getLookupSchema() + "') for field '" + field.getNonBlankCaption() + "'"));
                }
                else
                {
                    _tableInfo = userSchema.getTable(field.getLookupQuery());
                    if (_tableInfo == null)
                    {
                        errors.add(new SimpleValidationError("Could not find the lookup's target query ('" + field.getLookupQuery() + "') for field '" + field.getNonBlankCaption() + "'"));
                    }
                    else
                    {
                        List<ColumnInfo> keyCols = _tableInfo.getPkColumns();
                        if (keyCols.size() != 1)
                        {
                            errors.add(new SimpleValidationError("Could not validate target query ('" + field.getLookupQuery() + "') because it has " + keyCols.size() + " columns instead of one for the field '" + field.getNonBlankCaption() + "'"));
                        }
                        else
                        {
                            try
                            {
                                Object[] keys = Table.executeArray(_tableInfo, keyCols.get(0), null, null, keyCols.get(0).getJavaObjectClass());
                                addAll(Arrays.asList(keys));
                            }
                            catch (SQLException e)
                            {
                                throw new RuntimeSQLException(e);
                            }
                        }
                    }
                }
            }
        }

        public Container getContainer()
        {
            return _container;
        }
    }

    public boolean validate(IPropertyValidator validator, PropertyDescriptor field, Object value, List<ValidationError> errors, ValidatorContext validatorCache)
    {
        if (field.getLookupQuery() != null && field.getLookupSchema() != null)
        {
            LookupKey key = new LookupKey(field);

            LookupValues validValues = (LookupValues)validatorCache.get(LookupValidator.class, key);
            if (validValues == null)
            {
                validValues = new LookupValues(field, validatorCache.getContainer(), validatorCache.getUser(), errors);
                validatorCache.put(LookupValidator.class, key, validValues);
            }

            if (validValues.contains(value))
            {
                return true;
            }

            errors.add(new PropertyValidationError("Value '" + value + "' was not present in lookup '" + field.getLookupSchema() + "." + field.getLookupQuery() + "' for field '" + field.getNonBlankCaption() + "'", field.getNonBlankCaption()));
        }
        return true;
    }
}
