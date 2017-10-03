package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.study.Study;

public class ProtocolDocumentAttachmentParent implements AttachmentParent
{
    private final StudyImpl _study;

    public ProtocolDocumentAttachmentParent(@NotNull Study study)
    {
        _study = (StudyImpl)study;
    }

    @Override
    public String getEntityId()
    {
        return _study.getProtocolDocumentEntityId();
    }

    @Override
    public String getContainerId()
    {
        return _study.getContainer().getId();
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return ProtocolDocumentType.get();
    }
}
