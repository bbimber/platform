package org.labkey.pipeline.api;

import org.labkey.api.data.Entity;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.io.*;

/**
 * @author B. MacLean
 */
public class PipelineStatusFileImpl extends Entity implements Serializable, PipelineStatusFile
{
    protected int _rowId;
    protected String _job;
    protected String _jobParent;
    protected String _jobStore;
    protected String _provider;
    protected String _status;
    protected String _info;
    protected String _dataUrl;
    protected String _description;
    protected String _filePath;
    protected String _email;
    private boolean _hadError;

    private int MAX_STATUS_LENGTH = 100;
    private int MAX_INFO_LEN = 1024;
    private int MAX_FILEPATH_LENGTH = 1024;
    private int MAX_DATAURL_LENGTH = 1024;
    private int MAX_DESCRIPTION_LENGTH = 255;
    private int MAX_EMAIL_LENGTH = 255;

    public PipelineStatusFileImpl()
    {
    }

    public PipelineStatusFileImpl(PipelineJob job, String status, String info)
    {
        assert(job.getStatusFile() != null) : "Must have a status file to set status.";

        setJob(job.getJobGUID());
        setJobParent(job.getParentGUID());
        setProvider(job.getProvider());
        setEmail(job.getInfo().getUserEmail());
        setDescription(job.getDescription());
        setFilePath(job.getStatusFile().getAbsolutePath());
        setStatus(status);
        setInfo(info);
        if (PipelineJob.COMPLETE_STATUS.equals(status))
        {
            // Make sure the Enterprise Pipeline recognizes this as a completed
            // job, even if did not have a TaskPipeline.
            job.setActiveTaskId(null);
            job.setActiveTaskStatus(PipelineJob.TaskStatus.complete);

            ActionURL urlData = job.getStatusHref();
            if (urlData != null)
                setDataUrl(urlData.getLocalURIString());
        }

        try
        {
            synchDiskStatus();
        }
        catch (IOException eio)
        {
            setStatus(PipelineJob.ERROR_STATUS);
            setInfo("type=disk; attempting " + status + (info == null ? "" : " - " + info) + "; " +
                    eio.getMessage());
        }

        if (PipelineJob.ERROR_STATUS.equals(getStatus()))
            job.setErrors(job.getErrors() + 1);
    }

    public void beforeUpdate(User user, Entity cur)
    {
        super.beforeUpdate(user, cur);

        PipelineStatusFileImpl curSF = (PipelineStatusFileImpl) cur;

        // Preserve original values across updates, if not explicitly changed.
        if (_jobStore == null || _jobStore.length() == 0)
            _jobStore = curSF._jobStore;
        if (_email == null || _email.length() == 0)
            _email = curSF._email;
        if (_provider == null || _provider.length() == 0)
            _provider = curSF._provider;
        if (_description == null || _description.length() == 0)
            _description = curSF._description;
        if (_job == null || _job.length() == 0)
            _job = curSF._job;
        if (_dataUrl == null || _dataUrl.length() == 0)
            _dataUrl = curSF._dataUrl;
        // _hadError?
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        this._rowId = rowId;
    }

    public String getJob()
    {
        return _job;
    }

    public void setJob(String job)
    {
        this._job = job;
    }

    public String getJobParent()
    {
        return _jobParent;
    }

    public void setJobParent(String jobParent)
    {
        _jobParent = jobParent;
    }

    public String getJobStore()
    {
        return _jobStore;
    }

    public void setJobStore(String jobStore)
    {
        _jobStore = jobStore;
    }

    public String getProvider()
    {
        return _provider;
    }

    public void setProvider(String provider)
    {
        this._provider = provider;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        if (status != null && status.length() > MAX_STATUS_LENGTH)
            status = status.substring(0, MAX_STATUS_LENGTH);
        this._status = status;
        if (PipelineJob.ERROR_STATUS.equalsIgnoreCase(status))
            _hadError = true;
    }

    public String getInfo()
    {
        return _info;
    }

    public void setInfo(String info)
    {
        if (info != null && info.length() > MAX_INFO_LEN)
            info = info.substring(0, MAX_INFO_LEN);
        this._info = info;
    }

    public String getFilePath()
    {
        return _filePath;
    }

    public void setFilePath(String filePath)
    {
        if (filePath != null && filePath.length() > MAX_FILEPATH_LENGTH)
            filePath = filePath.substring(0, MAX_FILEPATH_LENGTH);
        this._filePath = PipelineJobService.statusPathOf(filePath);
    }

    public String getDataUrl()
    {
        return _dataUrl;
    }

    public void setDataUrl(String dataUrl)
    {
        if (dataUrl != null && dataUrl.length() > MAX_DATAURL_LENGTH)
            dataUrl = dataUrl.substring(0, MAX_DATAURL_LENGTH);
        this._dataUrl = dataUrl;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH)
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        this._description = description;
    }

    public String getEmail()
    {
        return _email;
    }

    public void setEmail(String email)
    {
        if (email != null && email.length() > MAX_EMAIL_LENGTH)
            email = email.substring(0, MAX_EMAIL_LENGTH);
        this._email = email;
    }

    public boolean isHadError()
    {
        return _hadError;
    }

    public void setHadError(boolean hadError)
    {
        this._hadError = hadError;
    }

    public void synchDiskStatus() throws IOException
    {
        // If the disk file is not a .status file, then do nothing.
        if (!_filePath.endsWith(".status"))
            return;

        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter(new FileWriter(_filePath));
            writer.write(_status);
            if (_info != null && _info.length() > 0)
            {
                writer.write("->");
                writer.write(_info);
            }
        }
        finally
        {
            if (writer != null)
                writer.close();
        }
    }
}
