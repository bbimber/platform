/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.data.DataColumn;
import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.StudyService;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jgarms
* Date: Dec 15, 2008
*/
class StudyDisplayColumn extends DataColumn
{
    private final String title;
    private final Container container;

    public StudyDisplayColumn(String title, Container container, ColumnInfo datasetIdColumn)
    {
        super(datasetIdColumn);
        this.title = title;
        this.container = container;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Integer datasetId = (Integer)getBoundColumn().getValue(ctx);
        if (datasetId != null)
        {
            ActionURL url = StudyService.get().getDatasetURL(container, datasetId.intValue());

            out.write("<a href=\"");
            out.write(url.getLocalURIString());
            out.write("\">copied</a>");
        }
    }

    @Override
    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        out.write(title);
    }
}
