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
package org.labkey.study.writer;

import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.util.VirtualFile;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:39:43 PM
 */
public class ReportWriter implements Writer<StudyImpl>
{
    private static final String DEFAULT_DIRECTORY = "reports";

    public String getSelectionText()
    {
        return "Reports";
    }

    public void write(StudyImpl study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        Report[] reports = ReportService.get().getReports(ctx.getUser(), ctx.getContainer());

        if (reports.length > 0)
        {
            StudyDocument.Study.Reports reportsXml = ctx.getStudyXml().addNewReports();
            reportsXml.setDir(DEFAULT_DIRECTORY);
            VirtualFile reportsDir = fs.getDir(DEFAULT_DIRECTORY);

            for (Report report : reports)
            {
                report.serializeToFolder(reportsDir);
            }
        }
    }
}
