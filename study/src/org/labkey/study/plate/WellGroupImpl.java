/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.study.plate;

import org.labkey.api.study.*;

import java.util.*;

/**
 * User: brittp
* Date: Oct 20, 2006
* Time: 10:26:38 AM
*/
public class WellGroupImpl extends WellGroupTemplateImpl implements WellGroup
{
    private PlateImpl _plate;
    private Double _mean = null;
    private Double _stdDev = null;
    private Double _min;
    private Double _max;
    private Set<WellGroup> _overlappingGroups;
    private List<WellData> _replicateWellData;

    public WellGroupImpl()
    {
        // no-param constructor for reflection
    }

    public WellGroupImpl(PlateImpl plate, String name, WellGroup.Type type, List<Position> positions)
    {
        super(plate, name, type, positions);
        _plate = plate;
    }

    public WellGroupImpl(PlateImpl plate, WellGroupTemplateImpl template)
    {
        super(plate, template.getName(), template.getType(), template.getPositions());
        _plate = plate;
        for (Map.Entry<String, Object> entry : template.getProperties().entrySet())
            setProperty(entry.getKey(), entry.getValue());
    }

    public synchronized Set<WellGroup> getOverlappingGroups()
    {
        if (_overlappingGroups == null)
        {
            _overlappingGroups = new LinkedHashSet<WellGroup>();
            for (Position position : getPositions())
            {
                List<? extends WellGroup> groups = _plate.getWellGroups(position);
                for (WellGroup group : groups)
                {
                    if (group != this)
                        _overlappingGroups.add(group);
                }
            }
        }
        return _overlappingGroups;
    }

    public Set<WellGroup> getOverlappingGroups(Type type)
    {
        Set<WellGroup> typedGroups = new LinkedHashSet<WellGroup>();
        for (WellGroup overlappingGroup : getOverlappingGroups())
        {
            if (type == overlappingGroup.getType())
                typedGroups.add(overlappingGroup);
        }
        return typedGroups;
    }

    public synchronized List<WellData> getWellData(boolean combineReplicates)
    {
        if (!combineReplicates)
        {
            List<WellData> returnList = new ArrayList<WellData>(_positions.size());
            for (Position position : _positions)
                returnList.add(_plate.getWell(position.getRow(), position.getColumn()));
            return returnList;
        }
        else
        {
            if (_replicateWellData == null)
            {
                _replicateWellData = new ArrayList<WellData>();
                // first, we find all replicate groups that overlap with this group:
                Set<WellGroup> replicateGroups = getOverlappingGroups(Type.REPLICATE);
                // create a mapping from each position to its replicate group, if one exists:
                Map<Position, WellGroup> positionToReplicateGroup = new HashMap<Position, WellGroup>();
                for (WellGroup replicateGroup : replicateGroups)
                {
                    for (Position position : replicateGroup.getPositions())
                        positionToReplicateGroup.put(position, replicateGroup);
                }

                Set<WellGroup> addedReplicateGroups = new HashSet<WellGroup>();
                // go through each well in order, adding the well to our data list if there is no
                // associated replicate group, or adding the replicate group if that position's
                // group hasn't been added before:
                for (Position position : _positions)
                {
                    WellGroup replicateGroup = positionToReplicateGroup.get(position);
                    if (replicateGroup != null)
                    {
                        if (!addedReplicateGroups.contains(replicateGroup))
                        {
                            _replicateWellData.add(replicateGroup);
                            addedReplicateGroups.add(replicateGroup);
                        }
                    }
                    else
                        _replicateWellData.add(_plate.getWell(position.getRow(), position.getColumn()));
                }
            }
            return _replicateWellData;
        }
    }

    public double getStdDev()
    {
        computeStats();
        return _stdDev;
    }

    public double getMax()
    {
        computeStats();
        return _max;
    }

    public double getMin()
    {
        computeStats();
        return _min;
    }

    public double getMean()
    {
        computeStats();
        return _mean;
    }

    public Plate getPlate()
    {
        return _plate;
    }

    public Double getDilution()
    {
        Double dilution = null;
        for (Position position: _positions)
        {
            Double current = _plate.getWell(position.getRow(), position.getColumn()).getDilution();
            if (dilution == null)
                dilution = current;
            else if (!dilution.equals(current))
                return null;
        }
        return dilution;
    }

    public void setDilution(Double dilution)
    {
        for (Position position : _positions)
            _plate.getWell(position.getRow(), position.getColumn()).setDilution(dilution);
    }

    public DilutionCurve getDilutionCurve(DilutionCurve.PercentCalculator calculator, boolean expectedDecreasing, DilutionCurve.FitType fitType) throws DilutionCurve.FitFailedException
    {
        return CurveFitFactory.getCurveImpl(this, expectedDecreasing, calculator, fitType);
    }

    public Double getMaxDilution()
    {
        List<WellData> datas = getWellData(true);
        Double max = null;
        for (WellData data : datas)
        {
            Double nextDilution = data.getDilution();
            if (max == null || (nextDilution != null && max < nextDilution))
                max = nextDilution;
        }
        return max;
    }

    public Double getMinDilution()
    {
        List<WellData> datas = getWellData(true);
        Double min = null;
        for (WellData data : datas)
        {
            Double nextDilution = data.getDilution();
            if (min == null || (nextDilution != null && min > nextDilution))
                min = nextDilution;
        }
        return min;
    }

    public void setPlate(PlateImpl plate)
    {
        _plate = plate;
    }

    public boolean isTemplate()
    {
        return false;
    }

    private synchronized void computeStats()
    {
        if (null == _stdDev)
            recomputeStats();
    }

    private synchronized void recomputeStats()
    {
        List<WellData> data = getWellData(true);
        double[] values = new double[data.size()];
        for (int i = 0; i < data.size(); i++)
            values[i] = data.get(i).getMean();

        computeStats(values);
    }

    private synchronized void computeStats(double[] data)
    {
        // sd is sqrt of sum of (values-mean) squared divided by n - 1
        // Calculate the mean
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        if (null == data || data.length == 0)
        {
            _mean = Double.NaN;
            _stdDev = Double.NaN;
            return;
        }

        final int n = data.length;
        if (data.length == 1)
        {
            _mean = data[0];
            _stdDev = Double.NaN;
            this._min = data[0];
            this._max = data[0];
            return;
        }

        _mean = 0.0;
        for (int i = 0; i < n; i++)
        {
            double d = data[i];
            _mean += d;
            if (d < min)
                min = d;
            if (d > max)
                max = d;
        }
        _mean /= n;
        this._min = min;
        this._max = max;

        // calculate the sum of squares
        double sum = 0;
        for (int i = 0; i < n; i++)
        {
            final double v = data[i] - _mean;
            sum += v * v;
        }
        _stdDev = Math.sqrt(sum / (n - 1));
    }
}
