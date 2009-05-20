/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.data.RenderContext;
import org.labkey.api.data.DataRegion;
import org.labkey.api.security.ACL;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.MemTracker;
import org.springframework.web.servlet.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

public abstract class DisplayElement implements View, Cloneable
{
    private Class<? extends Permission> _displayPermission = ReadPermission.class;
    private boolean _visible = true;
    private int _displayModes = DataRegion.MODE_ALL;
    protected StringExpressionFactory.StringExpression _caption = null;
    protected boolean _locked = false;

    public DisplayElement()
    {
        assert MemTracker.put(this);
    }

    public String getContentType()
    {
        return "text/html";
    }

    public Class<? extends Permission> getDisplayPermission()
    {
        return _displayPermission;
    }

    public void setDisplayPermission(int displayPermission)
    {
        checkLocked();
        _displayPermission = ACL.translatePermission(displayPermission);
    }

    public void setDisplayPermission(Class<? extends Permission> perm)
    {
        checkLocked();
        _displayPermission = perm;
    }

    public boolean isVisible(Map ctx)
    {
        return _visible;
    }


    public void setVisible(boolean visible)
    {
        checkLocked();
        _visible = visible;
    }

    public boolean shouldRender(RenderContext ctx)
    {
        return isVisible(ctx) && (null == _displayPermission || ctx.getViewContext().getContainer().hasPermission(ctx.getViewContext().getUser(), _displayPermission));
    }

    public String getOutput(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            render(ctx, writer);
        }
        catch (IOException e)
        {
            writer.write(e.getMessage());
        }

        return writer.toString();
    }


    public String getCaption(RenderContext ctx)
    {
        return null == _caption ? null : _caption.eval(ctx);
    }


    public String getCaption()
    {
        return null == _caption ? null : _caption.getSource();
    }


    public void setCaption(String caption)
    {
        checkLocked();
        _caption = StringExpressionFactory.create(caption);
        assert _caption.toString().equals(caption);
    }


    public String getCaptionExpr()
    {
        return _caption == null ? null : _caption.toString();
    }


    public int getDisplayModes()
    {
        return _displayModes;
    }

    public void setDisplayModes(int displayModes)
    {
        checkLocked();
        _displayModes = displayModes;
    }

    /**
     * convert org.springframework.web.servlet.View.render(Map) to DisplayElement.render(ModelBean)
     */
    public final void render(Map map, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        assert map instanceof RenderContext;
        render((RenderContext)map,  request, response);
    }

    
    /**
     * org.springframework.framework.web.View
     */
    public void render(RenderContext ctx, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        render(ctx, response.getWriter());
    }


    // This is not WebPartView.renderView() */
    public void renderView(Map model, Writer out) throws IOException
    {
        RenderContext ctx;
        if (model instanceof RenderContext)
            ctx = (RenderContext) model;
        else if (model instanceof ViewContext)
            ctx = new RenderContext((ViewContext)model);
        else
            throw new IllegalStateException();

        render(ctx, out);
    }


    public abstract void render(RenderContext ctx, Writer out) throws IOException;


    public void lock()
    {
        _locked = true;
    }

    protected void checkLocked()
    {
        if (_locked)
            throw new IllegalStateException("Object has already been locked");
    }
}
