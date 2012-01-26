/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.core.admin.writer;

import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.PagesDocument;

import java.util.List;
import java.util.Map;


/**
 * User: cnathe
 * Date: Jan 17, 2012
 */
public class PageWriterFactory implements FolderWriterFactory
{
    private static final String FILENAME = "pages.xml";

    @Override
    public FolderWriter create()
    {
        return new PageWriter();
    }

    public class PageWriter implements FolderWriter
    {
        public String getSelectionText()
        {
            return "Webpart properties and layout";
        }

        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            FolderDocument.Folder folderXml = ctx.getXml();
            FolderDocument.Folder.Pages folderPagesXML = folderXml.addNewPages();
            folderPagesXML.setFile(PageWriterFactory.FILENAME);

            PagesDocument pagesDocXML = PagesDocument.Factory.newInstance();
            PagesDocument.Pages pagesXML = pagesDocXML.addNewPages();

            List<WebPart> tabs = Portal.getParts(ctx.getContainer(), FolderTab.FOLDER_TAB_PAGE_ID);
            if (tabs.size() == 0)
            {
                // if there are no tabs, try getting webparts for the default page ID
                PagesDocument.Pages.Page pageXml = pagesXML.addNewPage();
                pageXml.setName(Portal.DEFAULT_PORTAL_PAGE_ID);
                addWebPartsToPage(ctx, pageXml, Portal.getParts(ctx.getContainer(), Portal.DEFAULT_PORTAL_PAGE_ID));
            }
            else
            {
                for (WebPart tab : tabs)
                {
                    PagesDocument.Pages.Page pageXml = pagesXML.addNewPage();
                    pageXml.setIndex(tab.getIndex());
                    pageXml.setName(tab.getName());

                    // for the study folder type(s), the Overview tab has a pageId of portal.default
                    String pageId = tab.getName().equals("Overview") ? Portal.DEFAULT_PORTAL_PAGE_ID : tab.getName();
                    addWebPartsToPage(ctx, pageXml, Portal.getParts(ctx.getContainer(), pageId));
                }
            }

            root.saveXmlBean(FILENAME, pagesDocXML);
        }

        public void addWebPartsToPage(ImportContext ctx, PagesDocument.Pages.Page pageXml, List<WebPart> webpartsInPage)
        {
            for (WebPart webPart : webpartsInPage)
            {
                PagesDocument.Pages.Page.Webpart webpartXml = pageXml.addNewWebpart();
                webpartXml.setName(webPart.getName());
                webpartXml.setIndex(webPart.getIndex());
                webpartXml.setLocation(webPart.getLocation());
                webpartXml.setPermanent(webPart.isPermanent());

                if (webPart.getPropertyMap().size() > 0)
                {
                    WebPartFactory factory = Portal.getPortalPart(webPart.getName());
                    Map<String, String> props = factory.serializePropertyMap(ctx, webPart.getPropertyMap());

                    PagesDocument.Pages.Page.Webpart.Properties propertiesXml = webpartXml.addNewProperties();
                    for (Map.Entry<String, String> prop : props.entrySet())
                    {
                        PagesDocument.Pages.Page.Webpart.Properties.Property propertyXml = propertiesXml.addNewProperty();
                        propertyXml.setKey(prop.getKey());
                        propertyXml.setValue(prop.getValue());
                    }
                }
            }
        }
    }
}
