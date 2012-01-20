/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import org.labkey.api.query.ValidationException;
import org.labkey.api.admin.ImportException;
import org.labkey.api.study.TimepointType;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: May 17, 2009
 * Time: 8:11:51 AM
 */
public class VisitImporter implements InternalStudyImporter
{
    private boolean _ensureDataSets = true;

    @Override
    public String getDescription()
    {
        return "Visit Importer";
    }

    public boolean isEnsureDataSets()
    {
        return _ensureDataSets;
    }

    public void setEnsureDataSets(boolean ensureDataSets)
    {
        _ensureDataSets = ensureDataSets;
    }

    public void process(ImportContext ctx, VirtualFile vf, BindException errors) throws IOException, SQLException, ImportException, ValidationException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer());
        // Visit map
        StudyDocument.Study.Visits visitsXml = ctx.getXml().getVisits();

        if (null != visitsXml)
        {
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
            {
                ctx.getLogger().warn("Can't import visits for an continuous date based study.");
                return;
            }

            String visitMapFile = visitsXml.getFile();
            ctx.getLogger().info("Loading visit map from " + visitMapFile);

            VisitMapImporter importer = new VisitMapImporter();
            importer.setEnsureDataSets(_ensureDataSets);
            List<String> errorMsg = new LinkedList<String>();

            if (!importer.process(ctx.getUser(), study, vf, visitMapFile, VisitMapImporter.Format.getFormat(visitMapFile), errorMsg, ctx.getLogger()))
            {
                for (String error : errorMsg)
                    errors.reject("uploadVisitMap", error);
            }
        }
    }
}
