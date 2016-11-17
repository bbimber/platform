/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.test.tests.study;

import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyC;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.LogMethod;

import java.io.File;

@Category({DailyC.class})
public class StudyDatasetIndexTest extends StudyBaseTest
{
    private static final File STUDY_WITH_DATASET_INDEX = TestFileUtils.getSampleData("studies/StudyWithDatasetIndex.folder.zip");
    private static final File STUDY_WITH_DATASET_SHARED_INDEX = TestFileUtils.getSampleData("studies/StudyWithDatasetSharedIndex.folder.zip");

    protected String getProjectName()
    {
        return "StudyDatasetSharedColumnAndIndexProject";
    }

    protected String getFolderName()
    {
        return "Study Dataset Shared Column and Index";
    }

    @Override
    @LogMethod
    protected void doCreateSteps()
    {
        initializeFolder();
        initializePipeline(null);
        clickFolder(getFolderName());

        log("Import study with index on dataset");
        importFolderFromZip(STUDY_WITH_DATASET_INDEX);
    }

    @Override
    @LogMethod
    protected void doVerifySteps()
    {
        beginAt("/query/" + getProjectName() + "/" + getFolderName()  + "/schema.view?schemaName=study");
        selectQueryInSubfolder("study","built-in queries & tables", "DEM-1");
        waitForText(10000, "view raw table metadata");
        clickAndWait(Locator.linkWithText("view raw table metadata"));
        assertTextPresentCaseInsensitive("dem_minus_1_indexedColumn");

        reloadStudyFromZip(STUDY_WITH_DATASET_SHARED_INDEX);

        beginAt("/query/" + getProjectName() + "/" + getFolderName()  + "/schema.view?schemaName=study");
        selectQueryInSubfolder("study","built-in queries & tables", "DEM-1");
        waitForText(10000, "view raw table metadata");
        clickAndWait(Locator.linkWithText("view raw table metadata"));
        assertTextPresentCaseInsensitive("dem_minus_1_indexedColumn");
        assertTextPresentCaseInsensitive("dem_minus_1_sharedColumn");

        // Verify size columns specified in datasets_metadata
        assertTableRowOnPage("indexedColumn", 28, 1);
        assertTableRowOnPage("20", 28, 4);

        assertTableRowOnPage("sharedColumn", 34, 1);
        assertTableRowOnPage("20", 34, 4);

        // Verify default sizes
        assertTableRowOnPage("multiLineColumn", 32, 1);
        assertTableRowOnPage("4000", 32, 4);

        assertTableRowOnPage("flagColumn", 33, 1);
        assertTableRowOnPage("4000", 33, 4);

        beginAt("/query/" + getProjectName() + "/" + getFolderName()  + "/schema.view?schemaName=study");
        selectQueryInSubfolder("study","built-in queries & tables", "DEM-2");
        waitForText(10000, "view raw table metadata");
        clickAndWait(Locator.linkWithText("view raw table metadata"));
        assertTextNotPresent("indexedColumn");
        assertTextPresentCaseInsensitive("dem_minus_2_sharedColumn");
    }
}
