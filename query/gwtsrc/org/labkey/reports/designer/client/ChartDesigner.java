package org.labkey.reports.designer.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.ChartService;
import org.labkey.api.gwt.client.ui.ChartServiceAsync;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.reports.AbstractChartPanel;
import org.labkey.api.gwt.client.ui.reports.ChartAxisOptionsPanel;
import org.labkey.api.gwt.client.ui.reports.ChartMeasurementsPanel;
import org.labkey.api.gwt.client.ui.reports.ChartPreviewPanel;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 30, 2007
 */
public class ChartDesigner extends AbstractChartPanel implements EntryPoint
{
    private RootPanel _root = null;
    private Label _loading = null;
    private String _returnURL;


    private ChartServiceAsync _service;
    private GWTChart _chart;

    private FlexTable _buttons;

    public void onModuleLoad()
    {
        _root = RootPanel.get("org.labkey.reports.designer.ChartDesigner-Root");

        _returnURL = PropertyUtil.getServerProperty("returnURL");
        String isAdmin = PropertyUtil.getServerProperty("isAdmin");
        _isAdmin = isAdmin != null ? Boolean.valueOf(isAdmin).booleanValue() : false;
        String isGuest = PropertyUtil.getServerProperty("isGuest");
        _isGuest = isGuest != null ? Boolean.valueOf(isGuest).booleanValue() : false;

        _buttons = new FlexTable();
        _buttons.setWidget(0, 0, new CancelButton());
        if (!_isGuest)
            _buttons.setWidget(0, 1, new SubmitButton());

        _loading = new Label("Loading...");
        _root.add(_loading);

        _chart = getChart();
        _service = getService();

        showUI();
    }

    private void showUI()
    {
        if (_chart != null)
        {
            _root.remove(_loading);
            _root.add(_buttons);
            _root.add(createWidget());
        }
    }

    public Widget createWidget()
    {
        FlexTable panel = new FlexTable();
        int row = 0;

        panel.setWidget(row++, 0, new ChartMeasurementsPanel(_chart, _service).createWidget());
        panel.setWidget(row++, 0, new ChartAxisOptionsPanel(_chart, _service).createWidget());
        panel.setWidget(row++, 0, new ChartPreviewPanel(_chart, _service).createWidget());

        return panel;
    }

    public GWTChart getChart()
    {
        GWTChart chart = null;
        String reportId = PropertyUtil.getServerProperty("reportId");
        if (reportId == null)
        {
            chart = new GWTChart();

            chart.setReportType(PropertyUtil.getServerProperty("reportType"));
            chart.setQueryName(PropertyUtil.getServerProperty("queryName"));
            chart.setSchemaName(PropertyUtil.getServerProperty("schemaName"));
            chart.setViewName(PropertyUtil.getServerProperty("viewName"));
            chart.setChartType(PropertyUtil.getServerProperty("chartType"));
            chart.setHeight(Integer.parseInt(PropertyUtil.getServerProperty("height")));
            chart.setWidth(Integer.parseInt(PropertyUtil.getServerProperty("width")));
        }
        return chart;
    }

    protected boolean validate()
    {
        List errors = new ArrayList();

        _chart.validate(errors);

        if (!errors.isEmpty())
        {
            String s = "";
            for (int i=0 ; i<errors.size() ; i++)
                s += errors.get(i) + "\n";
            Window.alert(s);
            return false;
        }
        return true;
    }

    public ChartServiceAsync getService()
    {
        ChartServiceAsync service = (ChartServiceAsync) GWT.create(ChartService.class);
        ServiceUtil.configureEndpoint(service, "chartService");

        return service;
    }

    class SubmitButton extends ImageButton
    {
        SubmitButton()
        {
            super("Save");
        }

        public void onClick(Widget sender)
        {
            if (validate())
            {
                SaveDialog dlg = new SaveDialog();
                dlg.setPopupPosition(sender.getAbsoluteLeft() + 25, sender.getAbsoluteTop() + 25);
                dlg.show();
            }
        }
    }

    class CancelButton extends ImageButton
    {
        CancelButton()
        {
            super("Cancel");
        }

        public void onClick(Widget sender)
        {
            cancelForm();
        }
    }

    private void cancelForm()
    {
        if (null == _returnURL || _returnURL.length() == 0)
            back();
        else
            navigate(_returnURL);
    }


    public static native void navigate(String url) /*-{
      $wnd.location.href = url;
    }-*/;
    
    public static native void back() /*-{
        $wnd.history.back();
    }-*/;

    private class SaveDialog extends DialogBox
    {
        public SaveDialog()
        {
            super(false, true);
            createPanel();
        }

        private void createPanel()
        {
            FlexTable panel = new FlexTable();
            int row = 0;

            BoundTextBox name = new BoundTextBox("name", _chart.getReportName(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _chart.setReportName(((TextBox)widget).getText());
                }
            });
            panel.setWidget(row, 0, new HTML("Name"));
            panel.setWidget(row++, 1, name);

            BoundTextArea description = new BoundTextArea("description", "", new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _chart.setReportDescription(((TextArea)widget).getText());
                }
            });
            description.setCharacterWidth(60);
            description.setHeight("40px");

            panel.setWidget(row, 0, new HTML("Description"));
            panel.setWidget(row++, 1, description);

            if (_isAdmin)
            {
                BoundCheckBox share = new BoundCheckBox("Make this view available to all users.", false, new WidgetUpdatable()
                {
                    public void update(Widget widget)
                    {
                        _chart.setShared(((CheckBox)widget).isChecked());
                    }
                });
                panel.setWidget(row++, 1, share);
            }

            ImageButton save = new ImageButton("OK");
            save.addClickListener(new ClickListener()
            {
                public void onClick(Widget sender)
                {
                    if (_chart.getReportName() == null || _chart.getReportName().length() == 0)
                    {
                        Window.alert("Chart name cannot be blank");
                    }
                    else
                    {
                        getService().saveChart(_chart, new AsyncCallback()
                        {
                            public void onFailure(Throwable caught)
                            {
                                Window.alert(caught.getMessage());
                            }

                            public void onSuccess(Object result)
                            {
                                if (result instanceof String)
                                    _returnURL = (String)result;

                                cancelForm();
                            }
                        });
                    }
                }
            });

            ImageButton cancel = new ImageButton("Cancel");
            cancel.addClickListener(new ClickListener()
            {
                public void onClick(Widget sender)
                {
                    SaveDialog.this.hide();
                }
            });

            HorizontalPanel hp = new HorizontalPanel();
            hp.add(save);
            hp.add(new HTML("&nbsp;"));
            hp.add(cancel);
            panel.setWidget(row++, 1, hp);

            setText("Save Chart View");
            setWidget(panel);
        }
    }
}
