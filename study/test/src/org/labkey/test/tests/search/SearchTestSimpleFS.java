package org.labkey.test.tests.search;

import org.junit.experimental.categories.Category;
import org.labkey.test.categories.Weekly;
import org.labkey.test.util.search.SearchAdminAPIHelper;

@Category({Weekly.class})
public class SearchTestSimpleFS extends SearchTest
{
    @Override
    public SearchAdminAPIHelper.DirectoryType directoryType()
    {
        return SearchAdminAPIHelper.DirectoryType.SimpleFSDirectory;
    }
}