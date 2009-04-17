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
package org.labkey.api.data;

import org.labkey.api.util.Cache;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.util.*;

/**
 * User: jgarms
 * Date: Jan 14, 2009
 */
public class QcUtil
{
    private static final String CACHE_PREFIX = QcUtil.class.getName() + "/";

    // Sentinel for the cache: if a container has no qc values set, we use this to indicate,
    // as null means a cache miss.
    private static final Map<String,String> NO_VALUES = new HashMap<String,String>();

    private QcUtil() {}

    public static Set<String> getQcValues(Container c)
    {
        assert c != null : "Attempt to get QC values without a container";
        return getValuesAndLabels(c).keySet();
    }

    /**
     * Allows nulls and ""
     */
    public static boolean isValidQcValue(String value, Container c)
    {
        if (value == null || "".equals(value))
            return true;
        return isQcValue(value, c);
    }

    public static boolean isQcValue(String value, Container c)
    {
        return getQcValues(c).contains(value);
    }

    public static String getQcLabel(String qcValue, Container c)
    {
        Map<String,String> map = getValuesAndLabels(c);
        String label = map.get(qcValue);
        if (label != null)
            return label;
        return "";
    }

    public static Map<String,String> getValuesAndLabels(Container c)
    {
        String cacheKey = getCacheKey(c);

        //noinspection unchecked
        Map<String,String> result = (Map<String,String>)getCache().get(cacheKey);
        if (result == null)
        {
            result = getFromDb(c);
            if (result.isEmpty())
            {
                result = NO_VALUES;
                getCache().put(cacheKey, NO_VALUES);
            }
            else
            {
                getCache().put(cacheKey, result);
                return Collections.unmodifiableMap(Collections.unmodifiableMap(result));
            }
        }
        if (result == NO_VALUES)
        {
            // recurse
            assert !c.isRoot() : "We have no QC values for the root container. This should never happen";
            return getValuesAndLabels(c.getParent());
        }

        return result;
    }

    private static Map<String,String> getFromDb(Container c)
    {
        Map<String,String> valuesAndLabels = new CaseInsensitiveHashMap<String>();
        try
        {
            TableInfo qcTable = CoreSchema.getInstance().getTableInfoQcValues();
            Set<String> selectColumns = new HashSet<String>();
            selectColumns.add("qcvalue");
            selectColumns.add("label");
            Filter filter = new SimpleFilter("container", c.getId());
            Map[] selectResults = Table.select(qcTable, selectColumns, filter, null, Map.class);

            //noinspection unchecked
            for (Map<String,String> m : selectResults)
            {
                valuesAndLabels.put(m.get("qcvalue"), m.get("label"));
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        return valuesAndLabels;
    }

    private static String getCacheKey(Container c)
    {
        return CACHE_PREFIX + c.getId();
    }

    private static Cache getCache()
    {
        return Cache.getShared();
    }

    public static void containerDeleted(Container c)
    {
        clearCache(c);
    }

    public static void clearCache(Container c)
    {
        getCache().removeUsingPrefix(getCacheKey(c));
    }

    /**
     * Returns the default QC values as originally implemented: "Q" and "N",
     * mapped to their labels.
     *
     * This should only be necessary at upgrade time.
     */
    public static Map<String,String> getDefaultQcValues()
    {
        Map<String,String> qcMap = new HashMap<String,String>();
        qcMap.put("Q", "Data currently under quality control review.");
        qcMap.put("N", "Required field marked by site as 'data not available'.");

        return qcMap;
    }
}
