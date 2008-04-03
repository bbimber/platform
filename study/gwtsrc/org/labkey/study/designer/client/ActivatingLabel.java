package org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.core.client.GWT;
import org.labkey.api.gwt.client.util.StringUtils;


/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 17, 2006
 * Time: 2:50:47 PM
 */
public class ActivatingLabel extends Composite implements SourcesFocusEvents, SourcesChangeEvents, ChangeListener, FocusListener, HasText
{
    FocusPanel fp = new FocusPanel();
    HTML l = new HTML();
    String emptyText = "Click to edit";
    Widget widget = null;
    FocusListenerCollection focusListeners = new FocusListenerCollection();
    ChangeListenerCollection changeListeners = new ChangeListenerCollection();


    public ActivatingLabel(Widget activated, String textWhenEmpty)
    {
        widget = activated;
        emptyText = textWhenEmpty;

        assert widget instanceof HasFocus;
        assert widget instanceof SourcesChangeEvents;
        assert widget instanceof HasText;

        initWidget(fp);
        fp.addFocusListener(this);
        ((HasFocus) widget).addFocusListener(this);
        ((SourcesChangeEvents) widget).addChangeListener(this);
        l.setWordWrap(true);
        updateLabelText(((HasText) widget).getText());
        fp.setWidget(l);
    }

    public ActivatingLabel()
    {
        this(new TextArea(), "Click to edit");
    }

    public Widget getWidget()
    {
        return widget;
    }
    
    public String getText()
    {
        return ((HasText) widget).getText();
    }

    public void setText(String text)
    {
        l.setHTML(StringUtils.filter(text, true));
        ((HasText) widget).setText(text);
        updateLabelText(text);
    }

    private void updateLabelText(String text)
    {
        text = StringUtils.trimToNull(text);
        if (null == text)
        {
            l.setText(emptyText);
            l.setStyleName("empty");
        }
        else
        {
            l.setHTML(StringUtils.filter(text, true));
            l.setStyleName("gwt-Label");
        }
    }

    public void addFocusListener(FocusListener listener)
    {
        focusListeners.add(listener);
    }

    public void removeFocusListener(FocusListener listener)
    {
        focusListeners.remove(listener);
    }

    public void addChangeListener(ChangeListener listener)
    {
       changeListeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener)
    {
        changeListeners.remove(listener);
    }

    public void onChange(Widget sender)
    {
        updateLabelText(((HasText) widget).getText());
        changeListeners.fireChange(this);
    }

    public void onFocus(Widget sender)
    {
        if (sender.equals(fp))
        {
            int labelHeight = l.getOffsetHeight();
            fp.setWidget(widget);
            if (widget.getOffsetHeight() < labelHeight && !(widget.getOffsetHeight() <= 0))
                widget.setHeight((labelHeight + 8) + "px");
            ((HasFocus) widget).setFocus(true);
        }
        focusListeners.fireFocus(this);
    }

    public void onLostFocus(Widget sender)
    {
        if (sender.equals(fp))
            return;

        //NOTE: On Firefox, onChange not necessarily fired on lost focus.
        //Double check here.
        String text = ((HasText) widget).getText();
        if (!text.equals(l.getText()))
        {
            changeListeners.fireChange(this);
            updateLabelText(((HasText) widget).getText());
        }
        fp.setWidget(l);
        focusListeners.fireLostFocus(this);
    }
}
