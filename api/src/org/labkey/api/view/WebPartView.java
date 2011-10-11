/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.ErrorRenderer;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.PageFlowUtil.filter;


public abstract class WebPartView<ModelBean> extends HttpView<ModelBean>
{
    private Throwable _prepareException = null;
    private boolean _isPrepared = false;
    private boolean _isEmbedded = false;
    private boolean _showTitle  = true;
    private boolean _isWebpart  = true;
    private String _helpPopup;
    private FrameType _frame = FrameType.PORTAL;
    private int _webPartRowId = -1;
    private NavTree _navMenu = null;
    private List<NavTree> _customMenus = null;
    private String _defaultLocation;
    private NavTree _custom;

    public static enum FrameType
    {
        /** with portal widgets */
        PORTAL,
        /** title w/ hr */
        TITLE,
        DIALOG,
        /** just <div class=""> */
        DIV,
        LEFT_NAVIGATION,
        /** clean */
        NONE
    }

    public WebPartView()
    {
        super();
        setFrame(FrameType.PORTAL);
    }

    public WebPartView(String title)
    {
        this();
        addObject("title", title);
    }

    public WebPartView(ModelBean model)
    {
        super(model);
        setFrame(FrameType.PORTAL);
    }

    public WebPartView(String title, HttpView contents)
    {
        this();
        addObject("title", title);
        setBody(contents);
    }


    public void setFrame(FrameType type)
    {
        _frame = type;
    }

    public FrameType getFrame()
    {
        return _frame;
    }

    public void setBodyClass(String className)
    {
        addObject("className", className);
    }

    public String getBodyClass()
    {
        return StringUtils.defaultString((String) getViewContext().get("className"), "");
    }

    /* Only used by FrameType.PORTAL */
    public void setFrameClass(String className)
    {
        addObject("frameClass", className);
    }

    /* Only used by FrameType.PORTAL */
    public String getFrameClass()
    {
        return StringUtils.defaultString((String) getViewContext().get("frameClass"), "");
    }

    public void setTitle(CharSequence title)
    {
        if (title instanceof HString)
            title = ((HString)title).getSource();
        addObject("title", title == null ? "" : title.toString());
    }

    public String getTitle()
    {
        Object ret = getViewContext().get("title");

        // If a webpart provides a title override use it
        Portal.WebPart wp = Portal.getPart(getWebPartRowId());
        if (wp != null)
        {
            Map<String, String> props = wp.getPropertyMap();
            if (props.containsKey("webpart.title"))
                return props.get("webpart.title");
        }

        if (ret != null && !(ret instanceof String))
        {
            return ret.toString();
        }
        return (String) ret;
    }

    /** Use ActionURL version instead */
    @Deprecated
    public void setTitleHref(String href)
    {
        addObject("href", href);
    }

    public void setTitlePopupHelp(String title, String body)
    {
        _helpPopup = PageFlowUtil.helpPopup(title, body);
    }

    public void setTitleHref(ActionURL href)
    {
        addObject("href", href.getLocalURIString());
    }


    public String getTitleHref()
    {
        return (String) getViewContext().get("href");
    }

    public int getWebPartRowId()
    {
        return _webPartRowId;
    }

    public void setWebPartRowId(int webPartRowId)
    {
        _webPartRowId = webPartRowId;
    }

    public NavTree getPortalLinks()
    {
        NavTree navTree = (NavTree) getViewContext().get("customizeLinks");
        if (null == navTree)
        {
            navTree = new NavTree();
            addObject("customizeLinks", navTree);
        }

        return navTree;
    }

    public void setPortalLinks(NavTree navTree)
    {
        addObject("customizeLinks", navTree);
    }

    public void setCustomize(NavTree tree)
    {
       _custom = tree;
    }

    public NavTree getCustomize()
    {
        return _custom;
    }

    public void enableExpandCollapse(String rootId, boolean collapsed)
    {
        addObject("collapsed", Boolean.valueOf(collapsed));
        addObject("rootId", rootId);
    }

    public void setNavMenu(NavTree navMenu)
    {
        _navMenu = navMenu;
    }

    /** Override to declare your own NavTree menu implementation that will be used by the
     * WebPartView upon rendering your webpart. NOTE: The top level key of your tree
     * may be overridden.
     * @return
     * */
    public NavTree getNavMenu()
    {
        return _navMenu;
    }

    public void addCustomMenu(NavTree tree)
    {
        if (_customMenus == null)
            _customMenus = new ArrayList<NavTree>();
        _customMenus.add(tree);
    }

    public List<NavTree> getCustomMenus()
    {
        return _customMenus;
    }

    public void setShowTitle(boolean showTitle)
    {
        _showTitle = showTitle;
    }

    public boolean showTitle()
    {
        return _showTitle;
    }

    public boolean isCollapsible()
    {
        return true;
    }

    public void setLocation(String location)
    {
        _defaultLocation = location;
    }

    public String getLocation()
    {
        return _defaultLocation;
    }

    /** @return Override to declare if a view is or is not a Web Part. Defaults to true. */
    public boolean isWebPart()
    {
        return _isWebpart;
    }

    public void setIsWebPart(boolean isWebPart)
    {
        _isWebpart = isWebPart;
    }
    
    @Override
    protected final void renderInternal(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        prepare(model);
        if (!isVisible())
            return;

        Throwable exceptionToRender = _prepareException;
        String errorMessage = null;

        this.doStartTag(getViewContext(), response.getWriter());

        if (exceptionToRender == null)
        {
            try
            {
                renderView(getModelBean(), request, response);
            }
            catch (RedirectException x)
            {
                Logger.getLogger(WebPartView.class).warn("Shouldn't throw redirect during renderView()", x);
                throw x;
            }
            catch (UnauthorizedException x)
            {
                Logger.getLogger(WebPartView.class).warn("Shouldn't throw unauthorized during renderView()", x);
                errorMessage = ExceptionUtil.getUnauthorizedMessage(getViewContext());
            }
            catch (Throwable t)
            {
                exceptionToRender = ExceptionUtil.unwrapException(t);
                if (ExceptionUtil.isIgnorable(exceptionToRender))
                {
                    exceptionToRender = null;
                }
                else
                {
                    Logger log = Logger.getLogger(WebPartView.class);
                    log.error("renderView() exception in " + getClass().getName() + " while responding to " + getViewContext().getActionURL().getLocalURIString(), exceptionToRender);
                    log.error("View creation stacktrace:" + ExceptionUtil.renderStackTrace(_creationStackTrace));
                }
            }
        }

        //if we received an exception during prepare or render, we'll display it here
        if (exceptionToRender != null || errorMessage != null)
        {
            if (errorMessage != null)
            {
                response.getWriter().write(errorMessage);
            }
            if (exceptionToRender != null)
            {
                renderException(exceptionToRender, request, response);
            }
        }

        this.doEndTag(getViewContext(), response.getWriter());
    }

    /**
     * Subclasses can override this method to handle how they respond
     * to different types of exceptions
     */
    protected void renderException(Throwable t, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        String message = "An unexpected error occurred";
        ErrorRenderer renderer = ExceptionUtil.getErrorRenderer(status, message, t, request, true, false);
        renderer.renderStart(response.getWriter());
        renderer.renderContent(response.getWriter(), request, null);
        renderer.renderEnd(response.getWriter());
    }


    @Override
    public boolean isVisible()
    {
        prepare(getModelBean());
        return super.isVisible();
    }


    protected final void prepare(ModelBean model)
    {
        if (_isPrepared)
            return;

        try
        {
            // HttpView.render() sets ActionURL(), but render may not have been called yet
            // UNDONE: MAB: clean up the propagation/initialization of viewURLHelper
            HttpView.initViewContext(getViewContext());
            this.prepareWebPart(model);
        }
        catch (Throwable t)
        {
            _prepareException = t;
        }
        finally
        {
            _isPrepared = true;
        }
    }


    protected void prepareWebPart(ModelBean model)
            throws ServletException
    {
    }


    public void doStartTag(Map m, PrintWriter out)
    {
        ViewContext context = getViewContext();
        String contextPath = context.getContextPath();
        FrameType frameType = getFrame();

        String title = getTitle();
        String href = getTitleHref();

        // HACK for backwards compatibility
        if (null == title && frameType==FrameType.PORTAL)
        {
            frameType = FrameType.DIV;
            setFrame(frameType);
        }

        String className = getBodyClass();
        String frameClassName = getFrameClass();

        switch (frameType)
        {
            case NONE:
                break;

            case DIV:
                out.printf("<div class=\"%s\">", className);
                break;

            case TITLE:
                startTitleFrame(out, title, href, "100%", className);
                break;

            case DIALOG:
            {
                out.print("<table class=\"labkey-bordered\"><tr>");
                out.print("<th>");
                out.print(  PageFlowUtil.filter(title));
                out.print(  "<br><img src='" + getViewContext().getContextPath() + "/_.gif' height=1 width=600>");
                out.print("</th>");

                if (getViewContext().get("closeURL") != null)
                {
                    Object o = getViewContext().get("closeURL");
                    Object closeUrl = o instanceof ActionURL ? ((ActionURL)o).getLocalURIString() : String.valueOf(o);
                    out.print("<th>");
                    out.print("<a href=\"" + PageFlowUtil.filter(closeUrl) + "\">");
                    out.print("<img src=\"" + contextPath + "/_images/delete.gif\"></a>");
                    out.print("</th>");
                }

                out.print("</tr><tr><td colspan=2 class=\"" + className + "\">");
                break;
            }

            case PORTAL:
            {
                out.println("<!--webpart-->");
                out.println("<table name=\"webpart\" id=\"webpart_" + getWebPartRowId() + "\" class=\"labkey-wp " + frameClassName + "\">");

                Boolean collapsed = false;

                // Don't render webpart header if title is null or blank
                if (!StringUtils.isEmpty(title))
                {
                    out.println("<tr class=\"labkey-wp-header\">");
                    out.print("<th class=\"labkey-wp-title-left\" title=\"");
                    if (showTitle())
                        out.print(PageFlowUtil.filter(title));
                    out.print("\">");

                    collapsed = (Boolean) context.get("collapsed");

                    if (collapsed != null && isCollapsible())
                    {
                        String rootId = (String) context.get("rootId");
                        ActionURL expandCollapseUrl = null;
                        String expandCollapseGifId = "expandCollapse-" + rootId;
                        if (collapsed != null)
                        {
                            if (rootId == null)
                                throw new IllegalArgumentException("pathToHere or rootId not provided");
                            expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class). getExpandCollapseURL(getViewContext().getContainer(), "", rootId);
                        }

                        out.printf("<a href=\"%s\" onclick=\"return toggleLink(this, %s);\" id=\"%s\">",
                                filter(expandCollapseUrl.getLocalURIString()), "true", expandCollapseGifId);
                        String image = collapsed.booleanValue() ? "plus.gif" : "minus.gif";
                        out.printf("<img width=9 height=9 src=\"%s/_images/%s\"></a>", context.getContextPath(), image);

                        out.printf(" <a href=\"%s\" onclick=\"return toggleLink(document.getElementById(%s), %s);\">",
                                filter(expandCollapseUrl.getLocalURIString()), PageFlowUtil.jsString(expandCollapseGifId), "true");
                        if (showTitle())
                            out.print(PageFlowUtil.filter(title));
                        out.print("</a>");
                    }
                    else
                    {
                        if (null != href)
                            out.print("<a href=\"" + PageFlowUtil.filter(href) + "\">");
                        if (showTitle())
                            out.print(PageFlowUtil.filter(title));
                        if (null != href)
                            out.print("</a>");
                    }
                    if (_helpPopup != null)
                    {
                        out.print(_helpPopup);
                    }

                    out.print("</th>\n<th class=\"labkey-wp-title-right\">");
                    NavTree[] links = getPortalLinks().getChildren();
                    String sep = "";

                    // Add Custom link
                    NavTree nMenu = getNavMenu();
                    if (nMenu == null)
                        nMenu = new NavTree("More");  // // 11730 : Customize link missing on many webparts

                    if (getCustomize() != null)
                    {
                        NavTree customize = getCustomize();
                        customize.setKey("Customize");
                        nMenu.addChild(customize);
                    }
                    
                    if(getLocation() != null && getLocation().equals(WebPartFactory.LOCATION_RIGHT))
                    {
                        // Collapse all items into one drop-down
                        // Render the navigation menu
                        if (nMenu == null)
                            nMenu = new NavTree("More");

                        // Portal
                        if (links.length > 0)
                        {
                            NavTree portal = new NavTree("Layout");
                            for (NavTree link : links)
                                portal.addChild(link);
                            nMenu.addChild(portal);
                        }

                        if (nMenu.hasChildren())
                        {
                            out.print("&nbsp;");
                            renderMenu(nMenu, out, contextPath + "/_images/partmenu.png");
                        }
                    }
                    else if (!isWebPart())
                    {
                        // Purposely don't render custom links to avoid duplicates

                        // Render Navigation Links
                        if (nMenu.hasChildren())
                        {
                            out.print("<div class=\"labkey-wp-text-buttons\">");
                            for (NavTree link : nMenu.getChildren())
                            {
                                if (link.hasChildren())
                                {
                                    renderMenu(link, out);
                                }
                                out.print(sep);
                                String linkHref = link.second;
                                String linkText = link.first;

                                if (null != linkHref && 0 < linkHref.length())
                                {
                                    out.print("<a href=\"" + linkHref + "\">" + linkText + "</a>");                                
                                }
                                else if (link.getScript() != null)
                                {
                                    out.print("<a onClick=" + PageFlowUtil.jsString(link.getScript()) + ">" + linkText + "</a>");                                
                                }
                            }
                            out.print("</div>");
                        }
                    }
                    else
                    {
                        // Render specific parts first (e.g. wiki edit)
                        if (_customMenus != null)
                        {
                            Iterator itr = _customMenus.iterator();
                            NavTree current;
                            while (itr.hasNext())
                            {
                                current = (NavTree) itr.next();
                                out.print(sep);
                                if (current.hasChildren())
                                {                                    
                                    renderMenu(current, out, current.getImageSrc());
                                }
                                else
                                {
                                    String linkHref = current.second;
                                    String linkText = current.first;

                                    if (null != linkHref && 0 < linkHref.length())
                                    {
                                        out.print("<span class=\"labkey-wp-icon-button-active\">");
                                        out.print("<a href=\"" + PageFlowUtil.filter(linkHref) + "\">");
                                    }
                                    else
                                        out.print("<span class=\"labkey-wp-icon-button-inactive\">");
                                    
                                    if (null != current.getImageSrc())
                                    {
                                        if (current.getImageWidth() != 0 && current.getImageHeight() != 0)
                                            out.print("<img height=" + current.getImageHeight() + " width=" + current.getImageWidth() + " src=\"" + current.getImageSrc() + "\" title=\"" + PageFlowUtil.filter(linkText) + "\">");
                                        else
                                            out.print("<img src=\"" + current.getImageSrc() + "\" title=\"" + PageFlowUtil.filter(linkText) + "\">");
                                    }
                                    else
                                        out.print(PageFlowUtil.filter(linkText));
                                    if (null != linkHref && 0 < linkHref.length())
                                        out.print("</a>");
                                    out.print("</span>");
                                }
                            }
                        }
                        
                        if (nMenu != null && nMenu.hasChildren())
                        {
                            out.print(sep);
                            renderMenu(nMenu, out, contextPath + "/_images/partmenu.png");
                        }

                        for (NavTree link : links)
                        {
                            out.print(sep);
                            String linkHref = link.second;
                            String linkText = link.first;

                            if (null != linkHref && 0 < linkHref.length())
                            {
                                out.print("<span class=\"labkey-wp-icon-button-active\">");
                                out.print("<a href=\"" + PageFlowUtil.filter(linkHref) + "\">");
                            }
                            else
                                out.print("<span class=\"labkey-wp-icon-button-inactive\">");
                            
                            if (null != link.getImageSrc())
                            {
                                if (link.getImageWidth() != 0 && link.getImageHeight() != 0)
                                    out.print("<img height=" + link.getImageHeight() + " width=" + link.getImageWidth() + " src=\"" + link.getImageSrc() + "\" title=\"" + PageFlowUtil.filter(linkText) + "\">");
                                else
                                    out.print("<img src=\"" + link.getImageSrc() + "\" title=\"" + PageFlowUtil.filter(linkText) + "\">");
                            }
                            else
                                out.print(PageFlowUtil.filter(linkText));
                            if (null != linkHref && 0 < linkHref.length())
                                out.print("</a>");
                            out.print("</span>");
                        }
                        out.println("</th>");
                        out.println("</tr>");
                    }
                }


                out.print("<tr id=\"" + getContentId() + "\" ");
                if (collapsed != null && collapsed.booleanValue())
                    out.print("style=\"display: none\"");
                out.println(">");

                out.print("<td colspan=2 class=\"" + className + " labkey-wp-body\">");
                break;
            }
            case LEFT_NAVIGATION:
            {
                out.print("<!--leftnav-webpart--><table class=\"labkey-expandable-nav\">");

                Boolean collapsed = (Boolean) context.get("collapsed");
                if (title != null)
                {
                    out.print("<tr>");

                    String rootId = (String) context.get("rootId");
                    ActionURL expandCollapseUrl = null;
                    String expandCollapseGifId = "expandCollapse-" + rootId;

                    if (collapsed != null)
                    {
                        if (rootId == null)
                            throw new IllegalArgumentException("pathToHere or rootId not provided");
                        expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class). getExpandCollapseURL(getViewContext().getContainer(), "", rootId);
                    }

                    out.print("<td class=\"labkey-expand-collapse-area\"><div>");

                    if (collapsed != null)
                    {
                        out.printf("<a class=\"labkey-header\" href=\"%s\" onclick=\"return toggleLink(this, %s);\" id=\"%s\">",
                                filter(expandCollapseUrl.getLocalURIString()), "true", expandCollapseGifId);
                        String image = collapsed.booleanValue() ? "plus.gif" : "minus.gif";
                        out.printf("<img src=\"%s/_images/%s\"></a>", context.getContextPath(), image);
                    }
                    if (null != href)
                    {
                        // print title with user-specified link:
                        out.print("<a class=\"labkey-header\" href=\"" + PageFlowUtil.filter(href) + "\">");
                        out.print(PageFlowUtil.filter(title));
                        out.print("</a>");
                    }
                    else if (collapsed != null)
                    {
                        // print title with expand/collapse link:
                        out.printf("<a class=\"labkey-header\" href=\"%s\" onclick=\"return toggleLink(document.getElementById(%s), %s);\">",
                                filter(expandCollapseUrl.getLocalURIString()), PageFlowUtil.jsString(expandCollapseGifId), "true");
                        out.print(PageFlowUtil.filter(title));
                        out.print("</a>");
                    }
                    else
                    {
                        // print the title alone:
                        out.print(PageFlowUtil.filter(title));
                    }

                    out.print("</div></td></tr>\n"); // end of second <th>
                }
                out.print("<tr" + (collapsed != null && collapsed.booleanValue() ? " style=\"display:none\"" : "") + " class=\"" + className + "\">" +
                        "<td colspan=\"2\" class=\"labkey-expandable-nav-body\">");
                break;
            }
        }
    }

    private String getContentId()
    {
        return "WebPartView" + System.identityHashCode(this);
    }

    public static void startTitleFrame(Writer out, String title, String href, String width, String className)
    {
        try
        {
            out.write(
                    "<table " + (null!=width?"width=\"" + width + "\"" : "") + ">" +
                            "<tr>" +
                            "<td class=\"labkey-announcement-title\" align=left><span>");
            if (null != href)
                out.write("<a href=\"" + PageFlowUtil.filter(href) + "\">");
            out.write(PageFlowUtil.filter(title));
            if (null != href)
                out.write("</a>");
            out.write("</span></td></tr>");
            out.write("<tr><td class=\"labkey-title-area-line\"></td></tr>");
            out.write("<tr><td colspan=3 class=\"" + className + "\">");
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static void startTitleFrame(Writer out, String title)
    {
        startTitleFrame(out, title, null, null, null);
    }


    public static void endTitleFrame(Writer out)
    {
        try
        {
            out.write("</td></tr></table>");
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }


    public void doEndTag(Map context, PrintWriter out)
    {
        FrameType frameType = getFrame();

        switch (frameType)
        {
            case NONE:
                break;

            case DIV:
                out.print("</div>");
                break;

            case TITLE:
                endTitleFrame(out);
                break;

            case LEFT_NAVIGATION:
            case PORTAL:
                out.print("</td></tr></table><!--/webpart-->");
                break;

            case DIALOG:
                out.print("</td></tr></table>");
        }
    }

    protected void renderView(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        renderView(model, response.getWriter());
    }


    protected void renderView(ModelBean model, PrintWriter out) throws Exception
    {
        throw new IllegalStateException("override renderView");
    }

    public boolean isEmbedded()
    {
        return _isEmbedded;
    }

    public void setEmbedded(boolean embedded)
    {
        _isEmbedded = embedded;
    }

    protected static class WebPartCollapsible implements Collapsible
    {
        private boolean _collapsed;
        private String _id;

        public WebPartCollapsible(String id)
        {
            _id = id;
        }

        public void setCollapsed(boolean collapsed)
        {
            _collapsed = collapsed;
        }

        public boolean isCollapsed()
        {
            return _collapsed;
        }

        public Collapsible[] getChildren()
        {
            return new Collapsible[0];
        }

        public Collapsible findSubtree(String path)
        {
            return null;
        }

        public String getId()
        {
            return _id;
        }
    }

    private void renderMenu(NavTree menu, PrintWriter out)
    {
        try
        {
            menu.setKey(menu.getKey());
            PopupMenu more = new PopupMenu(menu, PopupMenu.Align.RIGHT, PopupMenu.ButtonStyle.TEXT);
            more.setOffset("-7");
            more.render(out);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void renderMenu(NavTree menu, PrintWriter out, String imageSrc)
    {
        try
        {
            menu.setKey("More");
            PopupMenu more = new PopupMenu(menu, PopupMenu.Align.RIGHT, PopupMenu.ButtonStyle.IMAGE);
            more.setImageSrc(imageSrc);
            more.setImageId("more-" + PageFlowUtil.filter(getTitle().toLowerCase()));
            out.print("<span class=\"labkey-wp-icon-button-active\">");
            more.render(out);
            out.print("</span>");
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
