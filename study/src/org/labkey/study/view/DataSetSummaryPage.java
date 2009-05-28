/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.study.view;

import org.labkey.api.data.Container;
import org.labkey.api.study.DataSet;

/**
 * User: brittp
 * Date: Jan 9, 2006
 * Time: 10:53:39 AM
 */
public abstract class DataSetSummaryPage extends BaseStudyPage
{
    private DataSet _dataSet;

    public void init(Container container, DataSet dataSet)
    {
        super.init(container);
        _dataSet = dataSet;
    }

    public DataSet getDataSetDefinition()
    {
        return _dataSet;
    }
}
