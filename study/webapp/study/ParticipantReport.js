/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);

Ext4.define('LABKEY.ext4.ParticipantReport', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            layout : 'border',
            frame  : false,
            border : false,
            editable : false,
            allowCustomize : false
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.customMode = false;
        this.items = [];

        this.previewPanel = Ext4.create('Ext.panel.Panel', {
            bodyPadding : 20,
            autoScroll  : true,
            border : false, frame : false,
            html   : '<span style="width: 400px; display: block; margin-left: auto; margin-right: auto">' +
                    ((!this.reportId && !this.allowCustomize) ? 'Unable to initialize report. Please provide a Report Identifier.' : 'Preview Area') +
                    '</span>'
        });

        this.exportForm = Ext4.create('Ext.form.Panel', {
            border : false, frame : false,
            standardSubmit  : true,
            items           : [
                {xtype : 'hidden', name : 'htmlFragment'},
                {xtype : 'hidden', name : 'X-LABKEY-CSRF'}
            ]
        });

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            border   : false, frame : false,
            layout   : 'fit',
            disabled : this.isNew(),
            region   : 'center',
            tbar     :  [{
                text    : 'Export',
                menu    : [{
                    text    : 'To Excel',
                    handler : function(){this.exportToXls();},
                    scope   : this}]
            }],
            items    : [this.previewPanel, this.exportForm]
        });
        this.items.push(this.centerPanel);

        if (this.allowCustomize) {
            this.saveButton = Ext4.create('Ext.button.Button', {
                text    : 'Save',
                disabled: this.isNew(),
                handler : function() {
                    var form = this.northPanel.getComponent('selectionForm').getForm();

                    if (form.isValid()) {
                        var data = this.getCurrentReportConfig();
                        this.saveReport(data);
                    }
                    else {
                        var msg = 'Please enter all the required information.';

                        if (!this.reportName.getValue()) {
                            msg = 'Report name must be specified.';
                        }
                        Ext4.Msg.show({
                             title: "Error",
                             msg: msg,
                             buttons: Ext4.MessageBox.OK,
                             icon: Ext4.MessageBox.ERROR
                        });
                    }
                },
                scope   : this
            });

            this.saveAsButton = Ext4.create('Ext.button.Button', {
                text    : 'Save As',
                hidden  : this.isNew(),
                handler : function() {
                    this.onSaveAs();
                },
                scope   : this
            });

            this.northPanel = Ext4.create('Ext.panel.Panel', {
                bodyPadding : 20,
                hidden   : true,
                preventHeader : true,
                frame : false,
                region   : 'north',
                layout   : 'hbox',
                buttons  : [{
                    text    : 'Cancel',
                    handler : function() {
                        this.customize();
                        // revert back to the last config (if any)
                        if (this.storedTemplateConfig)
                            this.loadSavedConfig(this.storedTemplateConfig);
                    },
                    scope   : this
                }, this.saveButton, this.saveAsButton
                ]
            });
            this.items.push(this.northPanel);
        }
        this.initNorthPanel();

        // customization
        this.on('enableCustomMode',  this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);

        // call generateTemplateConfig() to use this task
        this.generateTask = new Ext4.util.DelayedTask(function(){

            var measures = this.getMeasures();
            var sorts = this.getSorts();

            if (measures.length > 0) {
                this.previewPanel.getEl().mask('loading data...');
                Ext4.Ajax.request({
                    url     : LABKEY.ActionURL.buildURL('visualization', 'getData.api'),
                    method  : 'POST',
                    jsonData: {
                        measures : measures,
                        sorts : sorts
                    },
                    success : function(response){
                        this.previewPanel.getEl().unmask();
                        this.renderData(Ext4.decode(response.responseText));
                    },
                    failure : function(response){
                        Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                    },
                    scope : this
                });
            }
            else {
                this.previewPanel.update('');
            }

        }, this);

        if (this.reportId) {
            this.onDisableCustomMode();            
            this.loadReport(this.reportId);
        }
        else if (this.allowCustomize) {
            this.customize();
        }

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);

        this.callParent([arguments]);
    },

    initNorthPanel : function() {

        if (this.allowCustomize) {
            var formItems = [];

            this.reportName = Ext4.create('Ext.form.field.Text', {
                fieldLabel : 'Report Name',
                allowBlank : false,
                readOnly   : !this.isNew(),
                listeners : {
                    change : function() {this.markDirty(true);},
                    scope : this
                }
            });

            this.reportDescription = Ext4.create('Ext.form.field.TextArea', {
                fieldLabel : 'Report Description',
                listeners : {
                    change : function() {this.markDirty(true);},
                    scope : this
                }
            });

            this.reportPermission = Ext4.create('Ext.form.RadioGroup', {
                xtype      : 'radiogroup',
                width      : 300,
                fieldLabel : 'Viewable By',
                items      : [
                    {boxLabel : 'All readers',  width : 100, name : 'public', checked : true, inputValue : true},
                    {boxLabel : 'Only me',   width : 100, name : 'public', inputValue : false}]
            });
            formItems.push(this.reportName, this.reportDescription, this.reportPermission);

            this.formPanel = Ext4.create('Ext.form.Panel', {
                bodyPadding : 20,
                itemId      : 'selectionForm',
                flex        : 1,
                items       : formItems,
                border      : false, frame : false,
                fieldDefaults : {
                    anchor  : '100%',
                    maxWidth : 650,
                    labelWidth : 150,
                    labelSeparator : ''
                }
            });

            var model = Ext4.define('LABKEY.query.Measures', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'id'},
                    {name : 'name'},
                    {name : 'label'},
                    {name : 'description'},
                    {name : 'isUserDefined',    type : 'boolean'},
                    {name : 'queryName'},
                    {name : 'schemaName'},
                    {name : 'type'}
                ],
                proxy : {
                    type : 'memory',
                    reader : {
                        type : 'json',
                        root : 'measures'
                    }
                }
            });

            this.pageFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Measures' });
            this.gridFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Measures' });

            // TODO: figure out infos

/*
            var pageGrid = Ext4.create('Ext.grid.Panel', {
                title   : 'Page Fields',
                store   : this.pageFieldStore,
                //flex    : 1.2,
                columns : [
                    { header : 'Columns', dataIndex : 'name', flex : 1},
                    {
                        xtype : 'actioncolumn',
                        width : 40,
                        align : 'center',
                        sortable : false,
                        items : [{
                            icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_40 + '/resources/themes/images/access/qtip/close.gif',
                            tooltip : 'Delete'
                        }],
                        listeners : {
                            click : function(col, grid, idx) {
                                this.pageFieldStore.removeAt(idx);
                                this.generateTemplateConfig();
                            },
                            scope : this
                        },
                        scope : this
                    }
                ],
                //height      : 200,
                viewConfig  : {
                    emptyText : 'Defaults to ' + this.subjectColumn,
                    plugins   : [{
                        ddGroup : 'ColumnSelection',
                        ptype   : 'gridviewdragdrop',
                        dragText: 'Drag and drop to reorder'
                    }],
                    listeners : {
                        drop : function(node, data, model, pos) {
                            this.generateTemplateConfig();
                        },
                        scope: this
                    },
                    scope : this
                }
            });

            var pageFieldsPanel = Ext4.create('Ext.panel.Panel', {
                height      : 200,
                layout      : 'fit',
                border      : false, frame : false,
                flex        : 1.2,
                items       : [pageGrid],
                fbar        : [{
                    type: 'button',
                    text:'Add Field',
                    handler: function() {
                        var callback = function(recs){
                            var rawData = []
                            for (var i=0; i < recs.length; i++) {
                                rawData.push(Ext4.clone(recs[i].data));
                            }
                            this.pageFieldStore.loadRawData({measures : rawData}, true);
                            this.generateTemplateConfig();
                        };
                        this.selectMeasures(callback, this);
                    }, scope: this}]

            });
*/

            var fieldGrid = Ext4.create('Ext.grid.Panel', {
                store   : this.gridFieldStore,
                columns : [
                    { header : 'Report Measures', dataIndex : 'label', flex : 1},
                    {
                        xtype : 'actioncolumn',
                        width : 40,
                        align : 'center',
                        sortable : false,
                        items : [{
                            icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_40 + '/resources/themes/images/access/qtip/close.gif',
                            tooltip : 'Delete'
                        }],
                        listeners : {
                            click : function(col, grid, idx) {
                                this.gridFieldStore.removeAt(idx);
                                this.generateTemplateConfig();
                            },
                            scope : this
                        },
                        scope : this
                    }
                ],
                viewConfig  : {
                    plugins   : [{
                        ddGroup  : 'ColumnSelection',
                        ptype    : 'gridviewdragdrop',
                        dragText : 'Drag and drop to reorder',
                        copy : true
                    }],
                    listeners : {
                        drop : function() {
                            this.generateTemplateConfig();
                        },
                        drag : function() {
                            console.log('dragging');
                        },
                        scope: this
                    },
                    scope : this
                }
            });

            this.measuresHandler = function() {
                var callback = function(recs){
                    var rawData = []
                    for (var i=0; i < recs.length; i++) {
                        rawData.push(Ext4.clone(recs[i].data));
                    }
                    this.enableUI(true);    // enable the UI if it is currently disabled
                    this.gridFieldStore.loadRawData({measures : rawData}, true);
                    this.generateTemplateConfig();
                };
                this.selectMeasures(callback, this);
            };

            this.designerPanel = Ext4.create('Ext.panel.Panel', {
                height      : 200,
                layout      : 'fit',
                border      : false, frame : false,
                flex        : 0.8,
                minButtonWidth : 150,
                buttonAlign : 'left',
                items       : [fieldGrid],
                fbar        : [{
                    xtype: 'button',
                    text:'Choose Measures',
                    handler: this.measuresHandler,
                    scope: this
                }]
            });

/*
            this.designerPanel = Ext4.create('Ext.panel.Panel', {
                //height : 250,
                width  : 500,
                layout: 'fit',
                border : false, frame : false,
                //region : 'center',
                //layout : 'vbox',
                defaults : {
                    style : 'padding-right: 20px'
                },
                //flex   : 4,
                items  : [gridFieldsPanel],
                scope : this
            });
*/

            if (this.isNew())
                this.northPanel.add({
                    xtype   : 'panel',
                    flex    : 1,
                    layout  : {
                        type : 'vbox',
                        align: 'center',
                        pack : 'center'
                    },
                    height  : 200,
                    border  : false, frame : false,
                    items   : [
                        {
                            xtype : 'displayfield', width: 300, value : 'To get started, choose some Measures:'
                        },{
                            xtype   : 'button',
                            text    :'Choose Measures',
                            handler : this.measuresHandler,
                            scope   : this
                        }
                    ]
                });
            else
                this.northPanel.add(this.formPanel, this.designerPanel);

            this.northPanel.show(); // might be hidden
        }
    },

    loadReport : function(reportId) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-reports', 'getParticipantReport.api'),
            method  : 'GET',
            params  : {reportId : reportId},
            success : function(response){
                var o = Ext4.decode(response.responseText);

                this.reportName.setReadOnly(true);
                this.saveAsButton.setVisible(true);
                this.loadSavedConfig(o.reportConfig);
            },
            failure : function(response){
                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
            },
            scope : this
        });
    },

    loadSavedConfig : function(config) {

        this.allowCustomize = config.editable;
        
        if (this.reportName)
            this.reportName.setValue(config.name);

        if (this.reportDescription && config.description != null)
            this.reportDescription.setValue(config.description);

        if (this.reportPermission)
            this.reportPermission.setValue({public : config.public});

        if (this.gridFieldStore) {

            var rawData = []
            for (var i=0; i < config.measures.length; i++) {
                rawData.push(Ext4.clone(config.measures[i].measure));
            }
            this.gridFieldStore.loadRawData({measures : rawData});
            this.generateTemplateConfig();
        }
        this.markDirty(false);
    },

    renderData : function(qr) {

        var config = {
            pageFields : [],
            pageBreakInfo : [],
            gridFields : [],
            rowBreakInfo : [],
            reportTemplate : SIMPLE_PAGE_TEMPLATE
        };

        if (this.pageFieldStore.getCount() > 0) {

            for (var i=0; i < this.pageFieldStore.getCount(); i++) {
                var mappedColName = qr.measureToColumn[this.pageFieldStore.getAt(i).data.name];
                if (mappedColName) {
                    if (i==0)
                        config.pageBreakInfo.push({name : mappedColName, rowspan: false});
                    config.pageFields.push(mappedColName);
                }
            }
        }
        else {
            // try to look for a participant ID measure to use automatically
            if (qr.measureToColumn[this.subjectColumn]) {
                config.pageBreakInfo.push({name : qr.measureToColumn[this.subjectColumn], rowspan: false});
                config.pageFields.push(qr.measureToColumn[this.subjectColumn]);
            }
        }

        // as long as there is page break info then we can render the report
        if (config.pageBreakInfo.length > 0) {

            if (qr.measureToColumn[this.subjectVisitColumn + '/Visit/Label'])
                config.gridFields.push(qr.measureToColumn[this.subjectVisitColumn + '/Visit/Label']);
            
            for (i=0; i < this.gridFieldStore.getCount(); i++) {
                var mappedColName = qr.measureToColumn[this.gridFieldStore.getAt(i).data.name];
                if (mappedColName)
                    config.gridFields.push(mappedColName);
            }

            // finally fix up the column names so that they don't display the long made-up names, the label
            // for the corresponding measure is probably the friendliest
            var columnToMeasure = {};

            for (var m in qr.measureToColumn) {
                if (qr.measureToColumn.hasOwnProperty(m)) {
                    columnToMeasure[qr.measureToColumn[m]] = m;
                }
            }

            for (i=0; i < qr.metaData.fields.length; i++) {
                var field = qr.metaData.fields[i];

                var rec = this.gridFieldStore.findRecord('name', columnToMeasure[field.name]);
                if (rec)
                    field.shortCaption = rec.data.label;
                else if (columnToMeasure[field.name])
                    field.shortCaption = columnToMeasure[field.name];
            }

            config.renderTo = this.previewEl || this.previewPanel.getEl().id + '-body';

            Ext.get(config.renderTo).update('');
            this.templateReport = Ext4.create('LABKEY.TemplateReport', config);
            this.templateReport.loadData(qr);
        }
    },

    isCustomizable : function() {
        return this.allowCustomize;
    },

    // private
    _inCustomMode : function() {
        return this.customMode;
    },

    customize : function() {

        if (!this.isCustomizable())
            return false;

        this.fireEvent((this._inCustomMode() ? 'disableCustomMode' : 'enableCustomMode'), this);
    },

    onEnableCustomMode : function() {

        // stash away the current config
        if (this.reportId) {
            this.storedTemplateConfig = this.getCurrentReportConfig();
        }

        // if the north panel hasn't been fully populated, initialize the dataset store, else
        // just show the panel
        this.northPanel.show();
        this.customMode = true;
    },

    onDisableCustomMode : function() {

        this.customMode = false;

        if (this.northPanel)
            this.northPanel.hide();

//        this.setHeight(this.templateReport.getHeight());
//        this.setWidth(this.templateReport.getWidth());  This screws up the north panel if opened again
    },

    generateTemplateConfig : function() {
        this.markDirty(true);
        this.generateTask.delay(500);
    },

    // get the grid fields in a form that the visualization getData api can understand
    //
    getMeasures : function() {

        var measures = [];
        for (var i=0; i < this.gridFieldStore.getCount(); i++) {
            measures.push({measure : this.gridFieldStore.getAt(i).data, time : 'visit'});
        }
        return measures;
    },

    getSorts : function() {
        var sorts = [];
        var firstMeasure = this.gridFieldStore.getAt(0).data;

        sorts.push({name : this.subjectColumn, queryName : firstMeasure.queryName,  schemaName : firstMeasure.schemaName});
        sorts.push({name : this.subjectVisitColumn + '/VisitDate', queryName : firstMeasure.queryName,  schemaName : firstMeasure.schemaName});

        return sorts;
    },

    getCurrentReportConfig : function() {

        return {
            name        : this.reportName.getValue(),
            reportId    : this.reportId,
            description : this.reportDescription.getValue(),
            public      : this.reportPermission.getValue().public,
            schemaName  : 'study',
            measures    : this.getMeasures()
        };
    },

    saveReport : function(data) {

        console.log('Saving Report Configuration.');

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-reports', 'saveParticipantReport.api'),
            method  : 'POST',
            jsonData: data,
            success : function(resp){
                Ext4.Msg.alert('Success', 'Report : ' + data.name + ' saved successfully', function(){
                    var o = Ext4.decode(resp.responseText);

                    // Modify Title (hack: hardcode the webpart id since this is really not a webpart, just
                    // using a webpart frame, will need to start passing in the real id if this ever
                    // becomes a true webpart
                    var titleEl = Ext.query('th[class=labkey-wp-title-left]:first', 'webpart_-1');
                    if (titleEl && (titleEl.length >= 1))
                    {
                        titleEl[0].innerHTML = LABKEY.Utils.encodeHtml(data.name);
                    }

                    this.reportId = o.reportId;
                    this.loadReport(this.reportId);
                    //this.reportName.setReadOnly(true);
                    //this.saveAsButton.setVisible(true);
                    this.customize();

                }, this);
            },
            failure : function(resp){
                Ext4.Msg.alert('Failure', Ext4.decode(resp.responseText).exception);
            },
            scope : this
        });
    },

    exportToXls : function() {

        var markup = this.templateReport.getMarkup();
        if (markup)
        {
            this.exportForm.getForm().setValues({htmlFragment : markup, 'X-LABKEY-CSRF' : LABKEY.CSRF});
            this.exportForm.submit({
                scope: this,
                url    : LABKEY.ActionURL.buildURL('experiment', 'convertHtmlToExcel'),
                failure: function(response, opts){
                    Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                }
            });
        }
    },

    // show the select measures dialog
    selectMeasures : function(handler, scope) {
        if (!this.measuresDialog) {
            this.measuresDialog = new LABKEY.vis.MeasuresDialog({
                multiSelect : true,
                closeAction :'hide'
            });
        }
        this.measuresDialog.addListener('measuresSelected', function(recs){handler.call(scope || this, recs);}, this, {single : true});
        this.measuresDialog.show();
    },

    isNew : function() {
        return !this.reportId;
    },

    enableUI : function(enable) {

        if (enable && !this.formPanel.rendered) {

            this.northPanel.removeAll();
            this.northPanel.add(this.formPanel, this.designerPanel);
            this.centerPanel.enable();
            this.saveButton.enable();
        }
    },

    onSaveAs : function() {
        var formItems = [];

        formItems.push(Ext4.create('Ext.form.field.Text', {name : 'name', fieldLabel : 'Report Name', allowBlank : false}));
        formItems.push(Ext4.create('Ext.form.field.TextArea', {name : 'description', fieldLabel : 'Report Description'}));

        var permissions = Ext4.create('Ext.form.RadioGroup', {
            xtype      : 'radiogroup',
            width      : 300,
            fieldLabel : 'Viewable By',
            items      : [
                {boxLabel : 'All readers',  width : 100, name : 'public', checked : true, inputValue : true},
                {boxLabel : 'Only me',   width : 100, name : 'public', inputValue : false}]
        });
        formItems.push(permissions);

        var saveAsWindow = Ext4.create('Ext.window.Window', {
            width  : 500,
            height : 300,
            layout : 'fit',
            draggable : false,
            modal  : true,
            title  : 'Save As',
            defaults: {
                border: false, frame: false
            },
            bodyPadding : 20,
            items  : [{
                xtype : 'form',
                fieldDefaults : {
                    anchor  : '100%',
                    maxWidth : 650,
                    labelWidth : 150,
                    labelSeparator : ''
                },
                items       : formItems,
                buttonAlign : 'left',
                buttons     : [{
                    text : 'Save',
                    formBind: true,
                    handler : function(btn) {
                        var form = btn.up('form').getForm();

                        if (form.isValid()) {
                            var data = this.getCurrentReportConfig();
                            var values = form.getValues();

                            data.name = values.name;
                            data.description = values.description;
                            data.public = values.public;

                            this.saveReport(data);
                        }
                        else {
                            Ext4.Msg.show({
                                 title: "Error",
                                 msg: 'Report name must be specified.',
                                 buttons: Ext4.MessageBox.OK,
                                 icon: Ext4.MessageBox.ERROR
                            });
                        }
                        saveAsWindow.close();
                    },
                    scope   : this
                }]
            }],
            scope : this
        });

        saveAsWindow.show();
    },

    markDirty : function(dirty) {
        this.dirty = dirty;
    },

    beforeUnload : function() {
        if (this.dirty) {
            return 'please save your changes';
        }
    }
});
