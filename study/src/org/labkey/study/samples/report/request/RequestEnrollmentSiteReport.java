package org.labkey.study.samples.report.request;

import org.labkey.api.data.SimpleFilter;
import org.labkey.study.model.Visit;
import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.SpecimenTypeVisitReport;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.SampleManager;

/**
 * Copyright (c) 2007 LabKey Software Foundation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Jan 14, 2008 1:37:24 PM
 */
public class RequestEnrollmentSiteReport extends SpecimenTypeVisitReport
{
    private int _siteId;
    public RequestEnrollmentSiteReport(String titlePrefix, SimpleFilter filter, SpecimenVisitReportParameters parameters, Visit[] visits, int siteId)
    {
        super(titlePrefix, visits, filter, parameters);
        _siteId = siteId;
    }

    protected String getFilterQueryString(Visit visit, SampleManager.SummaryByVisitType summary)
    {
        return super.getFilterQueryString(visit, summary)  + "&" + SpecimenQueryView.PARAMS.showRequestedByEnrollmentSite + "=" + _siteId;
    }
}