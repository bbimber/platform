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

package org.labkey.wiki;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.security.User;
import org.labkey.api.view.*;
import org.labkey.api.wiki.MacroProvider;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.util.HString;
import org.labkey.wiki.model.RadeoxMacroProxy;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.wiki.model.WikiView;
import org.labkey.wiki.renderer.HtmlRenderer;
import org.labkey.wiki.renderer.PlainTextRenderer;
import org.labkey.wiki.renderer.RadeoxRenderer;
import org.radeox.macro.MacroRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * User: Mark Igra
 * Date: Jun 12, 2006
 * Time: 3:19:54 PM
 */
public class ServiceImpl implements WikiService
{
    public static WikiRendererType DEFAULT_WIKI_RENDERER_TYPE = WikiRendererType.HTML;
    public static WikiRendererType DEFAULT_MESSAGE_RENDERER_TYPE = WikiRendererType.TEXT_WITH_LINKS;

    private Map<String, MacroProvider> providers = new HashMap<String, MacroProvider>();

    public String getHtml(Container c, String name, boolean forceRefresh)
    {
        if (null == c || null == name)
            return null;

        try
        {
            Wiki wiki = WikiManager.getWiki(c, new HString(name), forceRefresh);
            if (null == wiki)
                return null;
            WikiVersion version = WikiManager.getLatestVersion(wiki, forceRefresh);
            return version.getHtml(c, wiki);
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    public void insertWiki(User user, Container c, String name, String body, WikiRendererType renderType, String title)
    {
        Wiki wiki = new Wiki(c, new HString(name));
        WikiVersion wikiversion = new WikiVersion();
        wikiversion.setTitle(new HString(title));

        wikiversion.setBody(body);

        if (renderType == null)
            renderType = getDefaultWikiRendererType();

        wikiversion.setRendererTypeEnum(renderType);

        try
        {
            WikiManager.insertWiki(user, c, wiki, wikiversion, null);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (AttachmentService.DuplicateFilenameException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void registerMacroProvider(String name, MacroProvider provider)
    {
        providers.put(name, provider);
        MacroRepository repository = MacroRepository.getInstance();
        repository.put(name, new RadeoxMacroProxy(name, provider));

    }

    //Package
    MacroProvider getMacroProvider(String name)
    {
        return providers.get(name);
    }

    public WebPartView getView(Container c, String name, boolean refresh, boolean contentOnly)
    {
        try
        {
            if (contentOnly)
            {
                String html = getHtml(c, name, refresh);
                return null == html ? null : new HtmlView(html);
            }
            Wiki wiki = WikiManager.getWiki(c, new HString(name), refresh);
            if (null == wiki)
                return null;
            WikiVersion version = WikiManager.getLatestVersion(wiki, refresh);
            WikiView view = new WikiView(wiki, version, true);
            return view;
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    public WikiRendererType getDefaultWikiRendererType()
    {
        return DEFAULT_WIKI_RENDERER_TYPE;
    }

    public WikiRendererType getDefaultMessageRendererType()
    {
        return DEFAULT_MESSAGE_RENDERER_TYPE;
    }
    
    public WikiRenderer getRenderer(WikiRendererType rendererType)
    {
        return getRenderer(rendererType, null, null, null, null);
    }

    public WikiRenderer getRenderer(WikiRendererType rendererType, String attachPrefix, Attachment[] attachments)
    {
        return getRenderer(rendererType, null, attachPrefix, null, attachments);
    }

    public WikiRenderer getRenderer(WikiRendererType rendererType, String hrefPrefix,
                                    String attachPrefix, Map<HString, WikiRenderer.WikiLinkable> pages,
                                    Attachment[] attachments)
    {
        WikiRenderer renderer;
        switch (rendererType)
        {
            case RADEOX:
                renderer = new RadeoxRenderer(hrefPrefix, attachPrefix, pages, attachments);
                break;
            case HTML:
                renderer = new HtmlRenderer(hrefPrefix, attachPrefix, pages, attachments);
                break;
            case TEXT_WITH_LINKS:
                renderer = new PlainTextRenderer();
                break;
            default:
                renderer = new RadeoxRenderer(null, attachPrefix, null, attachments);
        }
        return renderer;
    }


    public java.util.List<String> getNames(Container c)
    {
        try
        {
            List<HString> l = WikiManager.getWikiNameList(c);
            ArrayList<String> ret = new ArrayList<String>();
            for (HString h : l)
                ret.add(h.getSource());
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

}
