/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.requirements;

import org.labkey.study.model.SampleRequest;
import org.labkey.study.model.SampleRequestRequirement;
import org.labkey.study.model.Specimen;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.SampleManager;
import org.labkey.api.data.RuntimeSQLException;

import java.util.*;
import java.sql.SQLException;

/**
 * User: brittp
* Date: Jun 7, 2007
* Time: 11:26:49 AM
*/
public enum SpecimenRequestRequirementType implements RequirementType
{
    ORIGINATING_SITE
            {
                public List<SampleRequestRequirement> generateRequirements(SampleRequest owner, SampleRequestRequirement defaultRequirement)
                {
                    try
                    {
                        List<SampleRequestRequirement> requirements = new ArrayList<SampleRequestRequirement>();
                        Specimen[] specimens = owner.getSpecimens();
                        if (specimens != null && specimens.length > 0)
                        {
                            // get a list of all providing and originating sites:
                            Set<Integer> originatingSiteIds = new HashSet<Integer>();
                            for (Specimen specimen : specimens)
                            {
                                SiteImpl originatingSite = SampleManager.getInstance().getOriginatingSite(specimen);
                                if (originatingSite != null)
                                    originatingSiteIds.add(originatingSite.getRowId());
                            }
                            for (Integer siteId : originatingSiteIds)
                            {
                                SampleRequestRequirement requirement = defaultRequirement.createMutable();
                                requirement.setSiteId(siteId);
                                requirement.setRequestId(owner.getRowId());
                                requirements.add(requirement);
                            }
                        }
                        return requirements;
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                }
            },
    PROVIDING_SITE
            {
                public List<SampleRequestRequirement> generateRequirements(SampleRequest owner, SampleRequestRequirement defaultRequirement)
                {
                    try
                    {
                        List<SampleRequestRequirement> requirements = new ArrayList<SampleRequestRequirement>();
                        Specimen[] specimens = owner.getSpecimens();
                        if (specimens != null && specimens.length > 0)
                        {
                            // get a list of all providing and originating sites:
                            Set<Integer> providerSiteIds = new HashSet<Integer>();
                            for (Specimen specimen : specimens)
                            {
                                SiteImpl providingSite = SampleManager.getInstance().getCurrentSite(specimen);
                                if (providingSite != null)
                                    providerSiteIds.add(providingSite.getRowId());
                            }
                            for (Integer siteId : providerSiteIds)
                            {
                                SampleRequestRequirement requirement = defaultRequirement.createMutable();
                                requirement.setRequestId(owner.getRowId());
                                requirement.setSiteId(siteId);
                                requirements.add(requirement);
                            }
                        }
                        return requirements;
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                }
            },
    RECEIVING_SITE
            {
                public List<SampleRequestRequirement> generateRequirements(SampleRequest owner, SampleRequestRequirement defaultRequirement)
                {
                    if (owner.getDestinationSiteId() != null)
                    {
                        defaultRequirement.setSiteId(owner.getDestinationSiteId());
                        defaultRequirement.setRequestId(owner.getRowId());
                        return Collections.singletonList(defaultRequirement);
                    }
                    else
                        return Collections.emptyList();
                }
            },
    NON_SITE_BASED
            {
                public List<SampleRequestRequirement> generateRequirements(SampleRequest owner, SampleRequestRequirement defaultRequirement)
                {
                    defaultRequirement.setRequestId(owner.getRowId());
                    return Collections.singletonList(defaultRequirement);
                }
            };

    abstract public List<SampleRequestRequirement> generateRequirements(SampleRequest owner, SampleRequestRequirement defaultRequirement);
}
