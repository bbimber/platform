/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HString;
import org.labkey.api.util.StringExpression;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Map;

/**
 * User: matthewb
 * Date: Feb 13, 2007
 * Time: 9:01:58 AM
 *
 * Parameter is bound to a particular parameter of a particular PreparedStatement
 *
 * TypedValue is useful for dragging along a sqlType with a raw value, usually this is not necessary as
 * type can be inferred.  However, this can be useful for NULL parameters and
 * for distiguishing unicode, non-unicode types
 *
 * NOTE: jdbc does not have separate Type values for varchar nvarchar
 * NOTE: does not do implicit conversion, just sets the parameter type
 */

public class Parameter
{
    private static Logger LOG = Logger.getLogger(Parameter.class);

    public static class TypedValue
    {
        Object _value;
        int    _sqlType;

        public TypedValue(Object value, int sqlType)
        {
            this._value = value;
            this._sqlType = sqlType;
        }

        public String toString()
        {
            return String.valueOf(_value);
        }
    }


    public static Object NULL_MARKER = new TypedValue(null, Types.JAVA_OBJECT)
    {
        @Override
        public String toString()
        {
            return "NULL";
        }
    };

    
    public static TypedValue nullParameter(int sqlType)
    {
        return new TypedValue(null, sqlType);
    }


    private final int _sqlType;
    private PreparedStatement _stmt;
    private final int _index;
    private String _name;


    public Parameter(PreparedStatement stmt, int index)
    {
        this(stmt, index, Types.JAVA_OBJECT);
    }

    public Parameter(PreparedStatement stmt, int index, int sqlType)
    {
        _stmt = stmt;
        _index = index;
        _sqlType = sqlType;
    }

    public Parameter(String name, int index, int sqlType)
    {
        _name = name;
        _index = index;
        _sqlType = sqlType;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setValue(Object in) throws SQLException
    {
        Object value = getValueToBind(in);
        int sqlType = _sqlType;

        try
        {
            if (sqlType == Types.JAVA_OBJECT)
            {
                if (in instanceof TypedValue)
                    sqlType = ((TypedValue)in)._sqlType;
                else if (in instanceof StringExpression)
                    sqlType = Types.VARCHAR;
            }

            if (null == value)
            {
                _stmt.setNull(_index, sqlType==Types.JAVA_OBJECT ? Types.VARCHAR : sqlType);
                return;
            }

            if (value instanceof AttachmentFile)
            {
                try
                {
                    InputStream is = ((AttachmentFile) value).openInputStream();
                    long len = ((AttachmentFile) value).getSize();

                    if (len > Integer.MAX_VALUE)
                        throw new IllegalArgumentException("File length exceeds " + Integer.MAX_VALUE);
                    _stmt.setBinaryStream(_index, is, (int)len);
                    return;
                }
                catch (Exception x)
                {
                    SQLException sqlx = new SQLException();
                    sqlx.initCause(x);
                    throw sqlx;
                }
            }

            if (sqlType == Types.JAVA_OBJECT)
                _stmt.setObject(_index, value);
            else
                _stmt.setObject(_index, value, sqlType);
        }
        catch (SQLException e)
        {
            LOG.error("Exception converting \"" + value + "\" to type " + _sqlType);
            throw e;
        }
    }

    
    public static Object getValueToBind(Object value)
    {
        if (value instanceof TypedValue)
            value = ((TypedValue)value)._value;

        if (value == null)
            return null;

        if (value instanceof Number || value instanceof String)
            return value;
        else if (value instanceof java.util.Date)
        {
            if (!(value instanceof java.sql.Date) && !(value instanceof java.sql.Time) && !(value instanceof java.sql.Timestamp))
                return new java.sql.Timestamp(((java.util.Date) value).getTime());
        }
        else if (value instanceof AttachmentFile)
            return value;
        else if (value instanceof GregorianCalendar)
            return new java.sql.Timestamp(((java.util.GregorianCalendar) value).getTimeInMillis());
        else if (value instanceof HString)
            return ((HString)value).getSource();
        else if (value.getClass() == java.lang.Character.class || value instanceof CharSequence)
            return value.toString();
        else if (value instanceof StringExpression)
            return ((StringExpression)value).getSource();
        else if (value.getClass() == Container.class)
            return ((Container) value).getId();
        else if (value instanceof Enum)
            return ((Enum)value).name();
        else if (value instanceof UserPrincipal)
            return ((UserPrincipal)value).getUserId();
        else if (value instanceof Role)
            return ((Role)value).getUniqueName();
        else if (value instanceof GUID)
            return value.toString();

        return value;
    }


    public String toString()
    {
        return "[" + _index + (null==_name?"":":"+_name) + "]";
    }


    public static class ParameterMap
    {
        PreparedStatement _stmt;
        CaseInsensitiveHashMap<Parameter> _map;

        public ParameterMap(PreparedStatement stmt, Collection<Parameter> parameters)
        {
            _map = new CaseInsensitiveHashMap<Parameter>(parameters.size() * 2);
            for (Parameter p : parameters)
            {
                if (null == p._name)
                    throw new IllegalStateException();
                p._stmt = stmt;
                if (_map.containsKey(p._name))
                    throw new IllegalArgumentException("duplicate parameter name");
                _map.put(p._name, p);
            }
            _stmt = stmt;
        }


        public PreparedStatement getStatement()
        {
            return _stmt;
        }


        public void clearParameters() throws SQLException
        {
            _stmt.clearParameters();
            for (Parameter p : _map.values())
                p.setValue(null);
        }


        public void put(String name, Object value)
        {
            try
            {
                Parameter p = _map.get(name);
                if (null != p)
                    p.setValue(value);
            }
            catch (SQLException sqlx)
            {
                throw new RuntimeSQLException(sqlx);
            }
        }


        public void putAll(Map<String,Object> values)
        {
            try
            {
                for (Map.Entry<String,Object> e : values.entrySet())
                {
                    Parameter p = _map.get(e.getKey());
                    if (null != p)
                        p.setValue(e.getValue());
                }
            }
            catch (SQLException sqlx)
            {
                throw new RuntimeSQLException(sqlx);
            }
        }
    }
}
