/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.files;

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.json.JSONArray;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.data.xml.ActionLink;
import org.labkey.data.xml.ActionOptions;
import org.labkey.data.xml.PipelineOptionsDocument;
import org.labkey.data.xml.TbarBtnOptions;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Feb 2, 2010
 * Time: 9:35:36 AM
 */
public class FilesAdminOptions
{
    private boolean _importDataEnabled = true;
    private Container _container;
    private Map<String, PipelineActionConfig> _pipelineConfig = new HashMap<String, PipelineActionConfig>();
    private fileConfig _fileConfig = fileConfig.useDefault;
    private Map<String, FilesTbarBtnOption> _tbarConfig = new HashMap<String, FilesTbarBtnOption>();
    private static Comparator TBAR_BTN_COMPARATOR = new TbarButtonComparator();

    public enum fileConfig {
        useDefault,
        useCustom,
        useParent,
    }

    public enum configProps {
        actions,
        fileConfig,
        importDataEnabled,
        tbarActions,
        inheritedFileConfig,
    }

    public FilesAdminOptions(Container c, String xml)
    {
        _container = c;
        if (xml != null)
            _init(xml);
    }

    public FilesAdminOptions(Container c)
    {
        this(c, null);
    }

    private void _init(String xml)
    {
        try {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();

            PipelineOptionsDocument doc = PipelineOptionsDocument.Factory.parse(xml, options);
            XmlBeansUtil.validateXmlDocument(doc);

            PipelineOptionsDocument.PipelineOptions pipeOptions = doc.getPipelineOptions();
            if (pipeOptions != null)
            {
                _importDataEnabled = pipeOptions.getImportEnabled();

                if (pipeOptions.getFilePropertiesConfig() != null)
                    _fileConfig = fileConfig.valueOf(pipeOptions.getFilePropertiesConfig());

                ActionOptions actionOptions = pipeOptions.getActionConfig();
                if (actionOptions != null)
                {
                    for (ActionOptions.DisplayOption o : actionOptions.getDisplayOptionArray())
                    {
                        PipelineActionConfig pa = new PipelineActionConfig(o.getId(), o.getState(), o.getLabel());

                        ActionLink links = o.getLinks();
                        if (links != null)
                        {
                            List<PipelineActionConfig> actionLinks = new ArrayList<PipelineActionConfig>();

                            for (ActionLink.DisplayOption lo : links.getDisplayOptionArray())
                                actionLinks.add(new PipelineActionConfig(lo.getId(), lo.getState(), lo.getLabel()));

                            pa.setLinks(actionLinks);
                        }
                        _pipelineConfig.put(pa.getId(), pa);
                    }
                }

                TbarBtnOptions btnOptions = pipeOptions.getTbarConfig();
                if (btnOptions != null)
                {
                    for (TbarBtnOptions.TbarBtnOption o : btnOptions.getTbarBtnOptionArray())
                    {
                        _tbarConfig.put(o.getId(), new FilesTbarBtnOption(o.getId(), o.getPosition(), o.getHideText(), o.getHideIcon()));
                    }
                }
            }
        }
        catch (XmlValidationException e)
        {
            throw new RuntimeException(e);
        }
        catch (XmlException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean isImportDataEnabled()
    {
        return _importDataEnabled;
    }

    public void setImportDataEnabled(boolean importDataEnabled)
    {
        _importDataEnabled = importDataEnabled;
    }

    public List<PipelineActionConfig> getPipelineConfig()
    {
        return new ArrayList<PipelineActionConfig>(_pipelineConfig.values());
    }

    public void setPipelineConfig(List<PipelineActionConfig> pipelineConfig)
    {
        _pipelineConfig.clear();
        for (PipelineActionConfig config : pipelineConfig)
            _pipelineConfig.put(config.getId(), config);
    }

    public void addDefaultPipelineConfig(PipelineActionConfig config)
    {
        if (!_pipelineConfig.containsKey(config.getId()))
            _pipelineConfig.put(config.getId(), config);
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public fileConfig getFileConfig()
    {
        return _fileConfig;
    }

    public void setFileConfig(fileConfig fileConfig)
    {
        _fileConfig = fileConfig;
    }

    public List<FilesTbarBtnOption> getTbarConfig()
    {
        List<FilesTbarBtnOption> configs = new ArrayList(_tbarConfig.values());
        Collections.sort(configs, TBAR_BTN_COMPARATOR);

        return configs;
    }

    public void setTbarConfig(List<FilesTbarBtnOption> tbarConfig)
    {
        _tbarConfig.clear();
        for (FilesTbarBtnOption o : tbarConfig)
            _tbarConfig.put(o.getId(), o);
    }

    public fileConfig getInheritedFileConfig()
    {
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        Container container = getContainer();
        while (container != container.getProject())
        {
            container = container.getParent();
            FilesAdminOptions options = svc.getAdminOptions(container);

            if (options.getFileConfig() != FilesAdminOptions.fileConfig.useParent)
                return options.getFileConfig();
        }
        FilesAdminOptions.fileConfig cfg = svc.getAdminOptions(container).getFileConfig();
        return cfg != FilesAdminOptions.fileConfig.useParent ? cfg : FilesAdminOptions.fileConfig.useDefault;
    }

    public String serialize()
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            PipelineOptionsDocument doc = PipelineOptionsDocument.Factory.newInstance();
            PipelineOptionsDocument.PipelineOptions pipelineOptions = doc.addNewPipelineOptions();

            pipelineOptions.setImportEnabled(isImportDataEnabled());
            pipelineOptions.setFilePropertiesConfig(_fileConfig.name());
            
            if (!_pipelineConfig.isEmpty())
            {
                ActionOptions actionOptions = pipelineOptions.addNewActionConfig();

                for (PipelineActionConfig ac : _pipelineConfig.values())
                {
                    ActionOptions.DisplayOption displayOption = actionOptions.addNewDisplayOption();

                    displayOption.setId(ac.getId());
                    displayOption.setState(ac.getState().name());
                    displayOption.setLabel(ac.getLabel());

                    ActionLink link = displayOption.addNewLinks();
                    for (PipelineActionConfig lac : ac.getLinks())
                    {
                        ActionLink.DisplayOption linkOption = link.addNewDisplayOption();

                        linkOption.setId(lac.getId());
                        linkOption.setState(lac.getState().name());
                        linkOption.setLabel(lac.getLabel());
                    }
                }
            }

            if (!_tbarConfig.isEmpty())
            {
                TbarBtnOptions tbarOptions = pipelineOptions.addNewTbarConfig();

                for (FilesTbarBtnOption o : _tbarConfig.values())
                {
                    TbarBtnOptions.TbarBtnOption tbarOption = tbarOptions.addNewTbarBtnOption();

                    tbarOption.setId(o.getId());
                    tbarOption.setPosition(o.getPosition());
                    tbarOption.setHideText(o.isHideText());
                    tbarOption.setHideIcon(o.isHideIcon());
                }
            }
            XmlBeansUtil.validateXmlDocument(doc);
            doc.save(output, XmlBeansUtil.getDefaultSaveOptions());

            return output.toString();
        }
        catch (Exception e)
        {
            // This is likely a code problem -- propagate it up so we log to mothership
            throw new RuntimeException(e);
        }
        finally
        {
            IOUtils.closeQuietly(output);
        }
    }

    public void updateFromJSON(Map<String, Object> props)
    {
        if (props.containsKey(configProps.actions.name()))
        {
            Object actions = props.get(configProps.actions.name());
            if (actions instanceof JSONArray)
            {
                JSONArray jarray = (JSONArray)actions;

                for (int i=0; i < jarray.length(); i++)
                {
                    PipelineActionConfig config = PipelineActionConfig.fromJSON(jarray.getJSONObject(i));
                    if (config != null)
                        _pipelineConfig.put(config.getId(), config);
                }
            }
        }
        if (props.containsKey(configProps.importDataEnabled.name()))
            setImportDataEnabled((Boolean)props.get(configProps.importDataEnabled.name()));

        if (props.containsKey(configProps.fileConfig.name()))
            setFileConfig(fileConfig.valueOf((String)props.get(configProps.fileConfig.name())));

        if (props.containsKey(configProps.tbarActions.name()))
        {
            Object actions = props.get(configProps.tbarActions.name());
            if (actions instanceof JSONArray)
            {
                JSONArray jarray = (JSONArray)actions;
                _tbarConfig.clear();

                for (int i=0; i < jarray.length(); i++)
                {
                    FilesTbarBtnOption o = FilesTbarBtnOption.fromJSON(jarray.getJSONObject(i));
                    if (o != null)
                        _tbarConfig.put(o.getId(), o);
                }
            }
        }
    }

    public static FilesAdminOptions fromJSON(Container c, Map<String,Object> props)
    {
        FilesAdminOptions options = new FilesAdminOptions(c);

        options.updateFromJSON(props);
        return options;
    }

    public Map<String, Object> toJSON()
    {
        Map<String, Object> props = new HashMap<String, Object>();

        if (!_pipelineConfig.isEmpty())
        {
            JSONArray actions = new JSONArray();

            for (PipelineActionConfig config : getPipelineConfig())
            {
                actions.put(config.toJSON());
            }
            props.put(configProps.actions.name(), actions);
        }
        props.put(configProps.importDataEnabled.name(), isImportDataEnabled());
        props.put(configProps.fileConfig.name(), _fileConfig.name());
        props.put(configProps.inheritedFileConfig.name(), getInheritedFileConfig().name());

        if (!_tbarConfig.isEmpty())
        {
            JSONArray tbarOptions = new JSONArray();

            for (FilesTbarBtnOption o : getTbarConfig())
            {
                tbarOptions.put(o.toJSON());
            }
            props.put(configProps.tbarActions.name(), tbarOptions);
        }
        return props;
    }

    private static class TbarButtonComparator implements Comparator<FilesTbarBtnOption>
    {
        @Override
        public int compare(FilesTbarBtnOption o1, FilesTbarBtnOption o2)
        {
            return o1.getPosition() - o2.getPosition();
        }

        @Override
        public boolean equals(Object obj)
        {
            return this.equals(obj);
        }
    }
}
