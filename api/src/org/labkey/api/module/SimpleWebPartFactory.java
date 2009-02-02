/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.Portal;
import org.labkey.data.xml.webpart.WebpartDocument;
import org.labkey.data.xml.webpart.WebpartType;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.SchemaProperty;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;
import java.util.HashMap;

/*
* User: Dave
* Date: Jan 26, 2009
* Time: 11:16:03 AM
*/

/**
 * A factory for web parts defined as simple XML files in a module
 */
public class SimpleWebPartFactory extends BaseWebPartFactory
{
    public static final String FILE_EXTENSION = ".webpart.xml";

    //can be used to select all webpart files in a directory
    public static final FilenameFilter webPartFileFilter = new FilenameFilter(){
        public boolean accept(File dir, String name)
        {
            return name.toLowerCase().endsWith(FILE_EXTENSION);
        }
    };

    private File _webPartFile;
    private long _lastModified = 0;
    private Module _module;
    private WebpartType _webPartDef = null;
    private Exception _loadException = null;

    public SimpleWebPartFactory(Module module, File webPartFile)
    {
        super(getNameFromFile(webPartFile));
        _module = module;
        loadDefinition(webPartFile);
        _webPartFile = webPartFile;
        _lastModified = webPartFile.lastModified();
    }

    protected static String getNameFromFile(File webPartFile)
    {
        String name = webPartFile.getName();
        return name.substring(0, name.length() - FILE_EXTENSION.length());
    }

    protected void loadDefinition(File webPartFile)
    {
        Logger log = Logger.getLogger(SimpleWebPartFactory.class);
        try
        {
            _loadException = null;
            XmlOptions xmlOptions = new XmlOptions();

            Map<String,String> namespaceMap = new HashMap<String,String>();
            namespaceMap.put("", "http://labkey.org/data/xml/webpart");
            xmlOptions.setLoadSubstituteNamespaces(namespaceMap);
            
            WebpartDocument doc = WebpartDocument.Factory.parse(webPartFile, xmlOptions);
            if(null == doc || null == doc.getWebpart())
                throw new Exception("Webpart definition file " + webPartFile.getAbsolutePath() + " does not contain a root 'webpart' element!");

            _webPartDef = doc.getWebpart();
        }
        catch(Exception e)
        {
            _loadException = e;
            log.error(e);
        }
    }

    @Override
    public String getName()
    {
        return null != _webPartDef && null != _webPartDef.getTitle() ? _webPartDef.getTitle() : super.getName();
    }

    public String getViewName()
    {
        return null != _webPartDef && null != _webPartDef.getView() && null != _webPartDef.getView().getName() ?
                _webPartDef.getView().getName() : null;
    }

    public Module getModule()
    {
        return _module;
    }

    public boolean isStale()
    {
        return _webPartFile.lastModified() != _lastModified;
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        if(isStale())
            loadDefinition(_webPartFile);

        if(null != _loadException)
            throw _loadException;

        if(null == getViewName())
            throw new Exception("No view name specified for the module web part defined in " + _webPartFile.getAbsolutePath());

        return SimpleAction.getModuleHtmlView(getModule(), getViewName());
    }
}