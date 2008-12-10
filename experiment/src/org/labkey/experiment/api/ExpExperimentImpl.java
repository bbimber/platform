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

package org.labkey.experiment.api;

import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.util.URLHelper;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.Date;

public class ExpExperimentImpl extends ExpIdentifiableBaseImpl<Experiment> implements ExpExperiment
{
    static final private Logger _log = Logger.getLogger(ExpExperimentImpl.class);

    public ExpExperimentImpl(Experiment experiment)
    {
        super(experiment);
    }

    public String getContainerId()
    {
        return _object.getContainer();
    }

    public URLHelper detailsURL()
    {
        return null;
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public ExpRun[] getRuns()
    {
        ExperimentRun[] runs = ExperimentServiceImpl.get().getRunsForExperiment(getLSID());
        return ExpRunImpl.fromRuns(runs);
    }

    public Date getCreated()
    {
        return _object.getCreated();
    }

    public User getCreatedBy()
    {
        return UserManager.getUser(_object.getCreatedBy());
    }

    public ExpRun[] getRuns(ExpProtocol parentProtocol, ExpProtocol childProtocol)
    {
        try
        {
            SQLFragment sql = new SQLFragment(" SELECT ER.* "
                        + " FROM exp.ExperimentRun ER "
                        + " INNER JOIN exp.RunList RL ON ( ER.RowId = RL.ExperimentRunId ) "
                        + " WHERE RL.ExperimentId = ? ");
            sql.add(getRowId());
            if (parentProtocol != null)
            {
                sql.append("\nAND ER.ProtocolLSID = ?");
                sql.add(parentProtocol.getLSID());
            }
            if (childProtocol != null)
            {
                sql.append("\nAND ER.RowId IN (SELECT PA.RunId "
                    + " FROM exp.ProtocolApplication PA "
                    + " WHERE PA.ProtocolLSID = ? ) ");
                sql.add(childProtocol.getLSID());
            }
            ExperimentRun[] runs = Table.executeQuery(ExperimentService.get().getSchema(), sql.getSQL(), sql.getParams().toArray(new Object[0]), ExperimentRun.class);
            return ExpRunImpl.fromRuns(runs);
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return new ExpRun[0];
        }
    }

    public ExpProtocol[] getProtocols()
    {
        return ExperimentServiceImpl.get().getProtocolsForExperiment(getRowId());
    }

    public void removeRun(User user, ExpRun run) throws Exception
    {
        ExperimentServiceImpl.get().dropRunsFromExperiment(getLSID(), run.getRowId());
    }

    public void addRuns(User user, ExpRun... runs)
    {
        ExperimentServiceImpl.get().addRunsToExperiment(this, runs);
    }

    public void save(User user)
    {
        if (getRowId() == 0)
        {
            _object = ExperimentServiceImpl.get().insertExperiment(user, _object);
        }
        else
        {
            throw new UnsupportedOperationException();
        }
    }

    public void setContainerId(String containerId)
    {
        _object.setContainer(containerId);
    }
}
