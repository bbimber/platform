package org.labkey.study.designer;

import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Feb 13, 2007
 * Time: 9:59:12 AM
 */
public class StudyDesignInfo
{
    private int studyId;
    private int createdBy;
    private Date created;
    private int modifiedBy;
    private Date modified;
    private Container container;
    private int publicRevision;
    private int draftRevision;
    private String label;
    private Container sourceContainer;
    private boolean active;

    public int getStudyId()
    {
        return studyId;
    }

    public void setStudyId(int studyId)
    {
        this.studyId = studyId;
    }

    public int getCreatedBy()
    {
        return createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        this.createdBy = createdBy;
    }

    public Date getCreated()
    {
        return created;
    }

    public void setCreated(Date created)
    {
        this.created = created;
    }

    public int getModifiedBy()
    {
        return modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        this.modifiedBy = modifiedBy;
    }

    public Date getModified()
    {
        return modified;
    }

    public void setModified(Date modified)
    {
        this.modified = modified;
    }

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
    {
        this.container = container;
        if (null == sourceContainer)
            this.sourceContainer = container;
    }

    public int getPublicRevision()
    {
        return publicRevision;
    }

    public void setPublicRevision(int publicRevision)
    {
        this.publicRevision = publicRevision;
    }

    public int getDraftRevision()
    {
        return draftRevision;
    }

    public void setDraftRevision(int draftRevision)
    {
        this.draftRevision = draftRevision;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public Lsid getLsid()
    {
        return new Lsid("study-design", String.valueOf(getStudyId()));
    }

    public Container getSourceContainer() {
        return sourceContainer;
    }

    public void setSourceContainer(Container sourceContainer) {
        this.sourceContainer = sourceContainer;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }
}
