/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.pipeline.file;

import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipelineSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisTaskPipelineSpec</code> is used with Spring
 * configuration to specify settings necessary for creating a
 * <code>FileAnalysisTaskPipeline</code>.
 */
public class FileAnalysisTaskPipelineSettings extends TaskPipelineSettings
{
    /**
     * Name to use in the <code>TaskId</code> for clone and configure.
     */
    private String _cloneName;

    /**
     * Text shown in the user interface for initiating the pipeline.
     */
    private String _description;

    /**
     * Name to be used by protocol factory for this pipeline.  This name
     * is used to create the analysis parent directory in the data directory
     * <p>
     * e.g. &lt;data&gt;/xtandem/protocol1
     * <p>
     * It is also used to create the protocols store directory for this type
     * under the system directory.
     */
    private String _protocolFactoryName;

    /**
     * Extensions used as filters for files to show in the user interface
     * when initiating the pipeline.
     */
    private List<String> _initialInputExts;

    /**
     * Maps the extension for a specific input/output file type to the list of
     * extensions for types from which it was derrived.
     * <p>
     * e.g. <pre>
     * .mzXML => (.pep.xml, .features.tsv)
     * </pre>
     * <p>
     * In the example above, a file of the current primary input type,
     * .features.tsv, was derived by running a file analysis on a .pep.xml
     * file, which was in turn derived by running a file analysis on a
     * .mzXML file.
     * <p>
     * To find the .mzXML file in question, this chain must be walked
     * in reverse.
     */
    private Map<String, List<String>> _fileExtHierarchy;

    /**
     * Support for generating XAR files for the <code>FileAnalysisTaskPipeline</code>
     */
    private FileAnalysisXarGeneratorSupport _xarGeneratorSupport;

    public FileAnalysisTaskPipelineSettings(String name)
    {
        super(FileAnalysisTaskPipeline.class, name);
    }

    public TaskId getCloneId()
    {
        return new TaskId(FileAnalysisTaskPipeline.class, _cloneName);
    }

    public String getCloneName()
    {
        return _cloneName;
    }

    public void setCloneName(String cloneName)
    {
        _cloneName = cloneName;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getProtocolFactoryName()
    {
        return _protocolFactoryName;
    }

    public void setProtocolFactoryName(String protocolFactoryName)
    {
        _protocolFactoryName = protocolFactoryName;
    }

    public List<String> getInitialInputExts()
    {
        return _initialInputExts;
    }

    public void setInitialInputExts(List<String> initialInputExts)
    {
        _initialInputExts = initialInputExts;
    }

    public void setInitialInputExt(String ext)
    {
        _initialInputExts = new ArrayList<String>();
        _initialInputExts.add(ext);
    }

    public Map<String, List<String>> getFileExtHierarchy()
    {
        return _fileExtHierarchy;
    }

    public void setFileExtHierarchy(Map<String, List<String>> fileExtHierarchy)
    {
        _fileExtHierarchy = fileExtHierarchy;
    }

    public FileAnalysisXarGeneratorSupport getXarGeneratorSupport()
    {
        return _xarGeneratorSupport;
    }

    public void setXarGeneratorSupport(FileAnalysisXarGeneratorSupport support)
    {
        _xarGeneratorSupport = support;
    }
}
