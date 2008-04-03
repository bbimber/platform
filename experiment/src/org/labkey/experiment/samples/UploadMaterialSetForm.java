package org.labkey.experiment.samples;

import org.apache.log4j.Logger;
import org.labkey.api.view.ViewForm;
import org.labkey.experiment.api.ExpSampleSetImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.Map;

public class UploadMaterialSetForm extends ViewForm
{
    static final private Logger _log = Logger.getLogger(UploadMaterialSetForm.class);
    private String name;
    private boolean nameReadOnly;
    private String data;
    private Integer idColumn1;
    private Integer idColumn2;
    private Integer idColumn3;
    private OverwriteChoice overwriteChoice;
    public enum OverwriteChoice
    {
        ignore,
        replace,
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public void setNameReadOnly(boolean b)
    {
        nameReadOnly = b;
    }

    public boolean getNameReadOnly()
    {
        return nameReadOnly;
    }

    public void setData(String data)
    {
        this.data = data;
    }

    public String getData()
    {
        return data;
    }

    public void setIdColumn1(Integer idColumn)
    {
        this.idColumn1 = idColumn;
    }


    public Integer getIdColumn1()
    {
        return idColumn1;
    }

    public void setIdColumn2(Integer idColumn)
    {
        this.idColumn2 = idColumn;
    }

    public Integer getIdColumn2()
    {
        return idColumn2;
    }

    public void setIdColumn3(Integer idColumn)
    {
        this.idColumn3 = idColumn;
    }

    public Integer getIdColumn3()
    {
        return this.idColumn3;
    }

    public String getOverwriteChoice()
    {
        return overwriteChoice == null ? null : overwriteChoice.toString();
    }

    public OverwriteChoice getOverwriteChoiceEnum()
    {
        return overwriteChoice;
    }

    public void setOverwriteChoice(String choice)
    {
        overwriteChoice = OverwriteChoice.valueOf(choice);
    }

    public Map<Integer, String> getKeyOptions(boolean allowBlank)
    {
        return new UploadSamplesHelper(this).getIdFieldOptions(allowBlank);
    }


    public ExpSampleSetImpl getSampleSet()
    {
        if (name == null)
            return null;
        return ExperimentServiceImpl.get().getSampleSet(getContainer(), name);
    }
}
