/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.apache.commons.beanutils.ConversionException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;
import org.labkey.api.study.assay.*;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.util.GUID;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.security.permissions.ReadPermission;
import org.apache.commons.beanutils.ConvertUtils;

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.net.BindException;

/**
 * User: brittp
* Date: Jul 11, 2007
* Time: 2:52:54 PM
*/
public class AssayRunUploadForm<ProviderType extends AssayProvider> extends ProtocolIdForm implements AssayRunUploadContext
{
    protected Map<DomainProperty, String> _uploadSetProperties = null;
    protected Map<DomainProperty, String> _runProperties = null;
    private String _comments;
    private String _name;
    private String _dataCollectorName;
    private boolean _multiRunUpload;
    private String _uploadStep;
    private String _targetStudy;
    private boolean _resetDefaultValues;
    private Map<String, File> _uploadedData;
    private boolean _successfulUploadComplete;
    private String _uploadAttemptID = GUID.makeGUID();
    private Map<DomainProperty, File> _additionalFiles;
    private Integer _batchId;
    protected BindException _errors;
    private List<AssayDataCollector> _collectors;
    private TransformResult _transformResult = DefaultTransformResult.createEmptyResult();

    public DomainProperty[] getRunDataProperties()
    {
        AssayProvider provider = AssayService.get().getProvider(getProtocol());
        Domain domain = provider.getResultsDomain(getProtocol());
        return domain.getProperties();
    }

    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        if (_runProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            Domain domain = provider.getRunDomain(getProtocol());
            DomainProperty[] properties = domain.getProperties();
            _runProperties = getPropertyMapFromRequest(Arrays.asList(properties));
        }
        return Collections.unmodifiableMap(_runProperties);
    }

    /** @return property descriptor to value */
    public Map<DomainProperty, String> getBatchProperties()
    {
        if (_uploadSetProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            Domain domain = provider.getBatchDomain(getProtocol());
            DomainProperty[] properties = domain.getProperties();
            _uploadSetProperties = getPropertyMapFromRequest(Arrays.asList(properties));
        }
        return Collections.unmodifiableMap(_uploadSetProperties);
    }

    protected Map<DomainProperty, String> getPropertyMapFromRequest(List<DomainProperty> columns)
    {
        Map<DomainProperty, String> properties = new LinkedHashMap<DomainProperty, String>();
        Map<DomainProperty, File> additionalFiles = getAdditionalPostedFiles(columns);
        for (DomainProperty pd : columns)
        {
            String propName = UploadWizardAction.getInputName(pd);
            String value = getRequest().getParameter(propName);
            if (pd.getPropertyDescriptor().getPropertyType() == PropertyType.BOOLEAN &&
                    (value == null || value.length() == 0))
                value = Boolean.FALSE.toString();

            if (additionalFiles.containsKey(pd))
                properties.put(pd, additionalFiles.get(pd).getPath());
            else
                properties.put(pd, value);
        }
        return properties;
    }

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        _comments = comments;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDataCollectorName()
    {
        return _dataCollectorName;
    }

    public void setDataCollectorName(String dataCollectorName)
    {
        _dataCollectorName = dataCollectorName;
    }

    /** Used after the data has been successfully loaded - we don't need to offer to use it again */ 
    public void clearUploadedData()
    {
        _uploadedData = null;
        _runProperties = null;
    }

    private List<AssayDataCollector> getDataCollectors()
    {
        if (_collectors == null)
            _collectors = getProvider().getDataCollectors(Collections.<String, File>emptyMap(), this);
        return _collectors;
    }

    public AssayDataCollector getSelectedDataCollector()
    {
        List<AssayDataCollector> collectors = getDataCollectors();
        for (AssayDataCollector collector : collectors)
        {
            if (collector.getShortName().equals(_dataCollectorName))
            {
                return collector;
            }
        }
        if (collectors.size() == 1)
        {
            return collectors.get(0);
        }
        return null;
    }

    @NotNull
    public Map<String, File> getUploadedData() throws ExperimentException
    {
        if (_uploadedData == null)
        {
            AssayDataCollector collector = getSelectedDataCollector();
            if (collector != null)
            {
                try
                {
                    _uploadedData = collector.createData(this);
                }
                catch (IOException e)
                {
                    throw new ExperimentException(e);
                }
                return _uploadedData;
            }
            return Collections.emptyMap();
        }
        return _uploadedData;
    }

    public Map<DomainProperty, File> getAdditionalPostedFiles(List<DomainProperty> pds)
    {
        if (_additionalFiles == null)
        {
            Map<String, DomainProperty> fileParameters = new HashMap<String, DomainProperty>();
            for (DomainProperty pd : pds)
            {
                if (pd.getPropertyDescriptor().getPropertyType() == PropertyType.FILE_LINK)
                    fileParameters.put(UploadWizardAction.getInputName(pd), pd);
            }

            if (!fileParameters.isEmpty())
            {
                AssayFileWriter writer = new AssayFileWriter();
                try
                {
                    Map<String, File> postedFiles = writer.savePostedFiles(this, fileParameters.keySet());
                    _additionalFiles = new HashMap<DomainProperty, File>();
                    for (Map.Entry<String, File> entry : postedFiles.entrySet())
                        _additionalFiles.put(fileParameters.get(entry.getKey()), entry.getValue());
                }
                catch (ExperimentException e)
                {
                    throw new RuntimeException(e);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
            else
                _additionalFiles = Collections.emptyMap();
        }
        return _additionalFiles;
    }

    @NotNull
    public ProviderType getProvider()
    {
        return (ProviderType)super.getProvider();
    }

    public ActionURL getActionURL()
    {
        return getViewContext().getActionURL();
    }

    public boolean isMultiRunUpload()
    {
        return _multiRunUpload;
    }

    public void setMultiRunUpload(boolean multiRunUpload)
    {
        _multiRunUpload = multiRunUpload;
    }

    public String getUploadStep()
    {
        return _uploadStep;
    }

    public void setUploadStep(String step)
    {
        if (step == null)
        {
            _uploadStep = UploadWizardAction.BatchStepHandler.NAME;
        }
        else
        {
            _uploadStep = step;
        }
    }

    public String getTargetStudy()
    {
        return _targetStudy;
    }

    public void setTargetStudy(String targetStudy)
    {
        _targetStudy = targetStudy;
    }

    public String getBatchPropertyValue(PropertyDescriptor key, String value)
    {
        if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(key.getName()))
        {
            if (value == null || "".equals(value))
            {
                return "[None]";
            }
            Set<Study> targets = AssayPublishService.get().getValidPublishTargets(getUser(), ReadPermission.class);
            Container container = ContainerManager.getForId(value);
            Study study = StudyService.get().getStudy(container);
            if (study != null && targets.contains(study))
            {
                return container.getPath() + " (" + study.getLabel() + ")";
            }
        }
        else if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(key.getName()))
        {
            return AbstractAssayProvider.findType(value, getProvider().getParticipantVisitResolverTypes()).getDescription();
        }

        if (key.getLookupQuery() != null)
        {
            PdLookupForeignKey lookupKey = new PdLookupForeignKey(getUser(), key);
            TableInfo lookupTable = lookupKey.getLookupTableInfo();
            if (lookupTable != null)
            {
                List<ColumnInfo> pks = lookupTable.getPkColumns();
                if (pks.size() == 1)
                {
                    ColumnInfo pk = pks.get(0);
                    try
                    {
                        Object filterValue = ConvertUtils.convert(value, pk.getJavaClass());
                        SimpleFilter filter = new SimpleFilter(pk.getName(), filterValue);
                        try
                        {
                            Set<String> cols = new HashSet<String>();
                            cols.add(lookupTable.getTitleColumn());
                            cols.add(pks.get(0).getName());
                            Map[] maps =  Table.selectForDisplay(lookupTable, cols, filter, null, Map.class);
                            if (maps.length > 0)
                            {
                                Object title = maps[0].get(lookupTable.getTitleColumn());
                                if (title != null)
                                    value = title.toString();
                            }
                        }
                        catch (SQLException e)
                        {
                            throw new RuntimeSQLException(e);
                        }
                    }
                    catch (ConversionException e)
                    {
                        // Use the raw value as the display value
                    }
                }
            }
        }
        return value;
    }

    public boolean isResetDefaultValues()
    {
        return _resetDefaultValues;
    }

    public void setResetDefaultValues(boolean resetDefaultValues)
    {
        _resetDefaultValues = resetDefaultValues;
    }

    public boolean isSuccessfulUploadComplete()
    {
        return _successfulUploadComplete;
    }

    public void setSuccessfulUploadComplete(boolean successfulUploadComplete)
    {
        _successfulUploadComplete = successfulUploadComplete;
    }
    
    public String getUploadAttemptID()
    {
        return _uploadAttemptID;
    }

    public void setUploadAttemptID(String uploadAttemptID)
    {
        _uploadAttemptID = uploadAttemptID;
    }

    public void resetUploadAttemptID()
    {
        _uploadAttemptID = GUID.makeGUID();
    }

    public Integer getBatchId()
    {
        return _batchId;
    }

    public void setBatchId(Integer batchId)
    {
        _batchId = batchId;
    }


    public void clearDefaultValues(Domain domain) throws ExperimentException
    {
        DefaultValueService.get().clearDefaultValues(getContainer(), domain, getUser());
    }

    public void saveDefaultBatchValues() throws ExperimentException
    {
        Map<DomainProperty, Object> objectMap = new HashMap<DomainProperty, Object>(getBatchProperties());
        DefaultValueService.get().setDefaultValues(getContainer(), objectMap, getUser());
    }

    public void saveDefaultRunValues() throws ExperimentException
    {
        Map<DomainProperty, Object> objectMap = new HashMap<DomainProperty, Object>(getRunProperties());
        DefaultValueService.get().setDefaultValues(getContainer(), objectMap, getUser());
    }

    public void saveDefaultValues(Map<DomainProperty, String> values, String scope) throws ExperimentException
    {
        if (values.isEmpty())
            return;

        Map<DomainProperty, Object> objectMap = new HashMap<DomainProperty, Object>(values);
        DefaultValueService.get().setDefaultValues(getContainer(), objectMap, getUser(), scope);
    }

    public Map<DomainProperty, Object> getDefaultValues(Domain domain, String scope) throws ExperimentException
    {
        if (isResetDefaultValues())
            clearDefaultValues(domain);
        return DefaultValueService.get().getDefaultValues(getContainer(), domain, getUser(), scope);
    }

    public Map<DomainProperty, Object> getDefaultValues(Domain domain) throws ExperimentException
    {
        return getDefaultValues(domain, null);
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }

    public TransformResult getTransformResult()
    {
        return _transformResult;
    }

    public void setTransformResult(TransformResult transformResult)
    {
        _transformResult = transformResult;
    }
}
