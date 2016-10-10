Ext4.define('LABKEY.VaccineDesign.TreatmentSchedulePanel', {
    extend : 'Ext.panel.Panel',

    border : false,

    bodyStyle : 'background-color: transparent;',

    width: 1400,

    disableEdit : true,

    dirty : false,

    returnURL : null,

    initComponent : function()
    {
        this.items = [this.getTreatmentsGrid()];

        this.callParent();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getTreatmentsGrid : function()
    {
        if (!this.treatmentsGrid)
        {
            this.treatmentsGrid = Ext4.create('LABKEY.VaccineDesign.TreatmentsGrid', {
                disableEdit: this.disableEdit,
                productRoles: this.productRoles
            });

            this.treatmentsGrid.on('dirtychange', this.enableSaveButton, this);
            this.treatmentsGrid.on('celledited', this.enableSaveButton, this);

            // Note: since we need the data from the treatment grid, don't add this.getTreatmentScheduleGrid() until the treatment grid store has loaded
            this.treatmentsGrid.on('loadcomplete', this.onTreatmentGridLoadComplete, this, {single: true});
        }

        return this.treatmentsGrid;
    },

    onTreatmentGridLoadComplete : function()
    {
        this.add(this.getTreatmentScheduleGrid());
        this.add(this.getButtonBar());

        // since a treatment label change needs to be reflected in the treatment schedule grid, force a refresh there
        this.getTreatmentsGrid().on('celledited', function(view, fieldName, value){
            if (fieldName == 'Label')
                this.getTreatmentScheduleGrid().refresh();
        }, this);

        // removing a treatment row needs to also remove any visit mappings for that treatment
        this.getTreatmentsGrid().on('beforerowdeleted', function(grid, record){
            this.getTreatmentScheduleGrid().removeTreatmentUsages(record.get('RowId'));
        }, this);
    },

    getTreatmentScheduleGrid : function()
    {
        if (!this.treatmentScheduleGrid)
        {
            this.treatmentScheduleGrid = Ext4.create('LABKEY.VaccineDesign.TreatmentScheduleGrid', {
                padding: '20px 0',
                disableEdit: this.disableEdit,
                subjectNoun: this.subjectNoun,
                visitNoun: this.visitNoun
            });

            this.treatmentScheduleGrid.on('dirtychange', this.enableSaveButton, this);
            this.treatmentScheduleGrid.on('celledited', this.enableSaveButton, this);
        }

        return this.treatmentScheduleGrid;
    },

    getButtonBar : function()
    {
        if (!this.buttonBar)
        {
            this.buttonBar = Ext4.create('Ext.toolbar.Toolbar', {
                dock: 'bottom',
                ui: 'footer',
                padding: 0,
                style : 'background-color: transparent;',
                defaults: {width: 75},
                items: [this.getSaveButton(), this.getCancelButton()]
            });
        }

        return this.buttonBar;
    },

    getSaveButton : function()
    {
        if (!this.saveButton)
        {
            this.saveButton = Ext4.create('Ext.button.Button', {
                text: 'Save',
                disabled: true,
                hidden: this.disableEdit,
                handler: this.saveTreatmentSchedule,
                scope: this
            });
        }

        return this.saveButton;
    },

    enableSaveButton : function()
    {
        this.setDirty(true);
        this.getSaveButton().enable();
    },

    getCancelButton : function()
    {
        if (!this.cancelButton)
        {
            this.cancelButton = Ext4.create('Ext.button.Button', {
                margin: this.disableEdit ? 0 : '0 0 0 10px',
                text: this.disableEdit ? 'Done' : 'Cancel',
                handler: this.goToReturnURL,
                scope: this
            });
        }

        return this.cancelButton;
    },

    saveTreatmentSchedule : function()
    {
        this.getEl().mask('Saving...');

        var treatments = [], cohorts = [],
            index = 0, errorMsg = [];

        Ext4.each(this.getTreatmentsGrid().getStore().getRange(), function(record)
        {
            var recData = Ext4.clone(record.data);
            index++;

            // drop any empty immunogen or adjuvant or challenge rows that were just added
            recData['Products'] = [];
            Ext4.each(recData['Immunogen'], function(immunogen)
            {
                if (Ext4.isDefined(immunogen['RowId']) || LABKEY.VaccineDesign.Utils.objectHasData(immunogen))
                    recData['Products'].push(immunogen);
            }, this);
            Ext4.each(recData['Adjuvant'], function(adjuvant)
            {
                if (Ext4.isDefined(adjuvant['RowId']) || LABKEY.VaccineDesign.Utils.objectHasData(adjuvant))
                    recData['Products'].push(adjuvant);
            }, this);
            Ext4.each(recData['Challenge'], function(challenge)
            {
                if (Ext4.isDefined(challenge['RowId']) || LABKEY.VaccineDesign.Utils.objectHasData(challenge))
                    recData['Products'].push(challenge);
            }, this);

            // drop any empty treatment rows that were just added
            var hasData = recData['Label'] != '' || recData['Description'] != '' || recData['Products'].length > 0;
            if (Ext4.isDefined(recData['RowId']) || hasData)
            {
                var treatmentLabel = recData['Label'] != '' ? '\'' + recData['Label'] + '\'' : index;

                // validation: treatment must have at least one immunogen or adjuvant, no duplicate immunogens/adjuvants for a treatment
                var treatmentProductIds = Ext4.Array.clean(Ext4.Array.pluck(recData['Products'], 'ProductId'));
                if (recData['Products'].length == 0)
                    errorMsg.push('Treatment ' + treatmentLabel + ' must have at least one immunogen or adjuvant defined.');
                else if (treatmentProductIds.length != Ext4.Array.unique(treatmentProductIds).length)
                    errorMsg.push('Treatment ' + treatmentLabel + ' contains a duplicate immunogen or adjuvant.');
                else
                    treatments.push(recData);
            }
        }, this);

        if (errorMsg.length > 0)
        {
            this.onFailure(errorMsg.join('<br/>'));
            return;
        }

        Ext4.each(this.getTreatmentScheduleGrid().getStore().getRange(), function(record)
        {
            var recData = Ext4.clone(record.data);

            // drop any empty cohort rows that were just added
            var hasData = recData['Label'] != '' || recData['SubjectCount'] != '' || recData['VisitMap'].length > 0;
            if (Ext4.isDefined(recData['RowId']) || hasData)
            {
                var countVal = Number(recData['SubjectCount']);
                if (isNaN(countVal) || countVal < 0)
                    errorMsg.push('Cohort ' + this.subjectNoun.toLowerCase() + ' count values must be a positive integer: ' + recData['SubjectCount'] + '.');
                else
                    cohorts.push(recData);
            }
        }, this);

        if (errorMsg.length > 0)
        {
            this.onFailure(errorMsg.join('<br/>'));
            return;
        }

        LABKEY.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-design', 'updateTreatmentSchedule.api'),
            method  : 'POST',
            jsonData: {
                treatments: treatments,
                cohorts: cohorts
            },
            scope: this,
            success: function(response)
            {
                var resp = Ext4.decode(response.responseText);
                if (resp.success)
                    this.goToReturnURL();
                else
                    this.onFailure();
            },
            failure: function(response)
            {
                var resp = Ext4.decode(response.responseText);
                if (resp.errors)
                    this.onFailure(Ext4.Array.pluck(resp.errors, 'message').join('<br/>'));
                else
                    this.onFailure(resp.exception);
            }
        });
    },

    goToReturnURL : function()
    {
        this.setDirty(false);
        window.location = this.returnURL;
    },

    onFailure : function(text)
    {
        Ext4.Msg.show({
            title: 'Error',
            msg: text || 'Unknown error occurred.',
            icon: Ext4.Msg.ERROR,
            buttons: Ext4.Msg.OK
        });

        this.getEl().unmask();
    },

    setDirty : function(dirty)
    {
        this.dirty = dirty;
        LABKEY.Utils.signalWebDriverTest("treatmentScheduleDirty", dirty);
    },

    isDirty : function()
    {
        return this.dirty;
    },

    beforeUnload : function()
    {
        if (!this.disableEdit && this.isDirty())
            return 'Please save your changes.';
    }
});

Ext4.define('LABKEY.VaccineDesign.TreatmentsGrid', {
    extend : 'LABKEY.VaccineDesign.BaseDataView',

    cls : 'study-vaccine-design vaccine-design-treatments',

    mainTitle : 'Treatments',

    width: 1400,

    studyDesignQueryNames : ['StudyDesignRoutes', 'Product', 'DoseAndRoute'],

    //Override
    getStore : function()
    {
        if (!this.store)
        {
            this.store = Ext4.create('Ext.data.Store', {
                storeId : 'TreatmentsGridStore',
                model : 'LABKEY.VaccineDesign.Treatment',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL("study-design", "getStudyTreatments", null, {splitByRole: true}),
                    reader: {
                        type: 'json',
                        root: 'treatments'
                    }
                },
                sorters: [{ property: 'RowId', direction: 'ASC' }],
                autoLoad: true
            });
        }

        return this.store;
    },

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            this.columnConfigs = [{
                label: 'Label',
                width: 200,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 185)
            },{
                label: 'Description',
                width: 200,
                dataIndex: 'Description',
                editorType: 'Ext.form.field.TextArea',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Description', 185, '95%')
            }];

            if (Ext4.isArray(this.productRoles)) {
                Ext4.each(this.productRoles, function(role){
                    var roleColumn = this.getProductRoleColumn(role);
                    this.columnConfigs.push(roleColumn);
                }, this);
            }
        }

        return this.columnConfigs;
    },

    getProductRoleColumn: function(roleName) {
        var column = {
            label: roleName + 's',
            width: 310,
            dataIndex: roleName,
            subgridConfig: {
                columns: [{
                    label: roleName,
                    width: 140,
                    dataIndex: 'ProductId',
                    required: true,
                    queryName: 'Product',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: this.getProductEditor(roleName)
                },{
                    label: 'Dose and Route',
                    width: 140,
                    dataIndex: 'DoseAndRoute',
                    queryName: 'DoseAndRoute',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: this.getDoseAndRouteEditorConfig()
                }]
            }
        };
        return column;
    },

    getProductEditor : function(roleName){

        var filter = LABKEY.Filter.create('Role', roleName),
            cfg = LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('ProductId', 125, 'Product', filter, 'Label', 'RowId');

        cfg.listeners = {
            scope: this,
            change : function(cmp, productId) {
                // clear out (if any) value for the dose and route field
                var record = this.getStore().getAt(cmp.storeIndex),
                    outerDataIndex = cmp.outerDataIndex,
                    subgridIndex = Number(cmp.subgridIndex),
                    selector = 'tr.row:nth(' + (this.getStore().indexOf(record)+1) + ') table.subgrid-' + outerDataIndex
                            + ' tr.subrow:nth(' + (subgridIndex+1) + ') td[data-index=DoseAndRoute] input';

                var inputField = this.getInputFieldFromSelector(selector);
                if (inputField != null)
                {
                    inputField.setValue('');
                    inputField.bindStore(this.getNewDoseAndRouteComboStore(productId));
                }
            }
        };
        return cfg;
    },

    getDoseAndRouteEditorConfig : function()
    {
        return {
            hideFieldLabel: true,
            name: 'DoseAndRoute',
            width: 125,
            forceSelection : false, // allow usage of inactive types
            editable : false,
            queryMode : 'local',
            displayField : 'Label',
            valueField : 'Label',
            store : null, // the store will be created and bound to this combo after render
            listeners : {
                scope: this,
                render : function(cmp) {
                    var record = this.getStore().getAt(cmp.storeIndex),
                        outerDataIndex = cmp.outerDataIndex,
                        subgridIndex = Number(cmp.subgridIndex),
                        productId = record.get(outerDataIndex)[subgridIndex]['ProductId'];

                    cmp.bindStore(this.getNewDoseAndRouteComboStore(productId));
                }
            }
        };
    },

    getNewDoseAndRouteComboStore : function(productId)
    {
        // need to create a new store each time since we need to add a [none] option and include any new treatment records
        var data = [];
        Ext4.each(Ext4.getStore('DoseAndRoute').getRange(), function(record)
        {
            if (record.get('ProductId') == null || record.get('ProductId') == productId)
                data.push(Ext4.clone(record.data));
        }, this);

        return Ext4.create('Ext.data.Store', {
            fields: ['RowId', 'Label'],
            data: data
        });
    },

    //Override
    getNewModelInstance : function()
    {
        return LABKEY.VaccineDesign.Treatment.create({
            RowId: Ext4.id() // need to generate an id so that the treatment schedule grid can use it
        });
    },

    //Override
    getDeleteConfirmationMsg : function()
    {
        return 'Are you sure you want to delete the selected treatment? '
            + 'Note: this will also delete any usages of this treatment record in the Treatment Schedule grid below.';
    },

    //Override
    updateSubgridRecordValue : function(record, outerDataIndex, subgridIndex, fieldName, newValue)
    {
        var preProductIds = Ext4.Array.pluck(record.get('Immunogen'), 'ProductId').concat(Ext4.Array.pluck(record.get('Adjuvant'), 'ProductId')).concat(Ext4.Array.pluck(record.get('Challenge'), 'ProductId'));

        this.callParent([record, outerDataIndex, subgridIndex, fieldName, newValue]);

        // auto populate the treatment label if the user has not already entered a value
        if (fieldName == 'ProductId')
            this.populateTreatmentLabel(record, preProductIds);
    },

    //Override
    removeSubgridRecord : function(target, record)
    {
        var preProductIds = Ext4.Array.pluck(record.get('Immunogen'), 'ProductId').concat(Ext4.Array.pluck(record.get('Adjuvant'), 'ProductId')).concat(Ext4.Array.pluck(record.get('Challenge'), 'ProductId'));
        this.callParent([target, record]);
        this.populateTreatmentLabel(record, preProductIds);
        this.refresh(true);
    },

    populateTreatmentLabel : function(record, preProductIds)
    {
        var currentLabel = record.get('Label');
        if (currentLabel == '' || currentLabel == this.getLabelFromProductIds(preProductIds))
        {
            var postProductIds = Ext4.Array.pluck(record.get('Immunogen'), 'ProductId').concat(Ext4.Array.pluck(record.get('Adjuvant'), 'ProductId')),
                updatedTreatmentLabel = this.getLabelFromProductIds(postProductIds);

            // need to update the input field value, which will intern update the record and fire teh celledited event
            var inputField = this.getInputFieldFromSelector('tr.row:nth(' + (this.getStore().indexOf(record)+1) + ') td.cell-value input');
            if (inputField != null)
            {
                inputField.setValue(updatedTreatmentLabel);
                record.set('Label', updatedTreatmentLabel);
            }
        }
    },

    getInputFieldFromSelector : function(selector)
    {
        var inputFieldEl = Ext4.DomQuery.selectNode(selector, this.getEl().dom);
        if (inputFieldEl != null)
            return Ext4.ComponentManager.get(inputFieldEl.id.replace('-inputEl', ''));

        return null;
    },

    getLabelFromProductIds : function(productIdsArr)
    {
        var labelArr = [];

        if (Ext4.isArray(productIdsArr))
        {
            Ext4.each(productIdsArr, function(productId){
                if (productId != undefined || productId != null)
                    labelArr.push(LABKEY.VaccineDesign.Utils.getLabelFromStore('Product', productId));
            });
        }

        return labelArr.join(' | ');
    }
});

Ext4.define('LABKEY.VaccineDesign.TreatmentScheduleGrid', {
    extend : 'LABKEY.VaccineDesign.BaseDataViewAddVisit',

    cls : 'study-vaccine-design vaccine-design-cohorts',

    mainTitle : 'Treatment Schedule',

    width: 350,

    subjectNoun : 'Subject',

    visitNoun : 'Visit',

    getStore : function()
    {
        if (!this.store)
        {
            this.store = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.VaccineDesign.Cohort',
                sorters: [{ property: 'RowId', direction: 'ASC' }]
            });

            this.queryStudyTreatmentSchedule();
        }

        return this.store;
    },

    queryStudyTreatmentSchedule : function()
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('study-design', 'getStudyTreatmentSchedule', null, {splitByRole: true}),
            method: 'GET',
            scope: this,
            success: function (response)
            {
                var o = Ext4.decode(response.responseText);
                if (o.success)
                {
                    this.getVisitStore(o['visits']);
                    this.getStore().loadData(o['cohorts']);
                    this.getStore().fireEvent('load', this.getStore());
                }
            }
        });
    },

    getVisitStore : function(data)
    {
        if (!this.visitStore)
        {
            this.visitStore = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.VaccineDesign.Visit',
                data : data
            });
        }

        return this.visitStore;
    },

    getTreatmentsStore : function()
    {
        if (!this.treatmentsStore)
        {
            this.treatmentsStore = Ext4.getStore('TreatmentsGridStore');
        }

        return this.treatmentsStore;
    },

    getVisitColumnConfigs : function()
    {
        var visitConfigs = [];

        Ext4.each(this.getVisitStore().getRange(), function(visit)
        {
            if (visit.get('Included'))
            {
                visitConfigs.push({
                    label: visit.get('Label') || (this.visitNoun + visit.get('RowId')),
                    width: 150,
                    dataIndex: 'VisitMap',
                    dataIndexArrFilterProp: 'VisitId',
                    dataIndexArrFilterValue: visit.get('RowId'),
                    dataIndexArrValue: 'TreatmentId',
                    lookupStoreId: 'TreatmentsGridStore',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: this.getTreatmentComboBoxConfig
                });
            }
        }, this);

        if (visitConfigs.length == 0 && !this.disableEdit)
        {
            visitConfigs.push({
                label: 'No ' + this.visitNoun + 's Defined',
                displayValue: '',
                width: 160
            });
        }

        return visitConfigs;
    },

    getTreatmentComboBoxConfig : function()
    {
        return {
            hideFieldLabel: true,
            name: 'VisitMap',
            width: 135,
            forceSelection : false, // allow usage of inactive types
            editable : false,
            queryMode : 'local',
            displayField : 'Label',
            valueField : 'RowId',
            store : this.getNewTreatmentComboStore()
        };
    },

    getNewTreatmentComboStore : function()
    {
        // need to create a new store each time since we need to add a [none] option and include any new treatment records
        var data = [{RowId: null, Label: '[none]'}];
        Ext4.each(this.getTreatmentsStore().getRange(), function(record)
        {
            data.push(Ext4.clone(record.data));
        }, this);

        return Ext4.create('Ext.data.Store', {
            fields: ['RowId', 'Label'],
            data: data
        });
    },

    removeTreatmentUsages : function(treatmentId)
    {
        this.getStore().suspendEvents();
        Ext4.each(this.getStore().getRange(), function(record)
        {
            var newVisitMapArr = Ext4.Array.filter(record.get('VisitMap'), function(item){ return item.TreatmentId != treatmentId; });
            record.set('VisitMap', newVisitMapArr);
        }, this);
        this.getStore().resumeEvents();

        this.refresh(true);
    },

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            var columnConfigs = [{
                label: 'Group / Cohort',
                width: 200,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 185)
            },{
                label: this.subjectNoun + ' Count',
                width: 130,
                dataIndex: 'SubjectCount',
                editorType: 'Ext.form.field.Number',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignNumberConfig('SubjectCount', 115)
            }];

            var visitConfigs = this.getVisitColumnConfigs();

            // update the width based on the number of visit columns
            var width = 400 + (Math.max(2, visitConfigs.length) * 150);
            this.setWidth(width);

            // update the outer panel width if necessary
            var outerPanel = this.up('panel');
            if (outerPanel != null)
                outerPanel.setWidth(Math.max(width, 1400));

            this.columnConfigs = columnConfigs.concat(visitConfigs);
        }

        return this.columnConfigs;
    },

    //Override
    getNewModelInstance : function()
    {
        var newCohort = LABKEY.VaccineDesign.Cohort.create();
        newCohort.set('VisitMap', []);
        return newCohort;
    },

    //Override
    getDeleteConfirmationMsg : function()
    {
        return 'Are you sure you want to delete the selected group / cohort and its associated treatment / visit mapping records?';
    },

    //Override
    getCurrentCellValue : function(column, record, dataIndex, outerDataIndex, subgridIndex)
    {
        var value = this.callParent([column, record, dataIndex, outerDataIndex, subgridIndex]);

        if (Ext4.isArray(value))
        {
            var matchingIndex = LABKEY.VaccineDesign.Utils.getMatchingRowIndexFromArray(value, column.dataIndexArrFilterProp, column.dataIndexArrFilterValue);
            if (matchingIndex > -1)
                return value[matchingIndex][column.dataIndexArrValue];
            else
                return null;
        }

        return value;
    },

    //Override
    updateStoreRecordValue : function(record, column, newValue)
    {
        // special case for editing the value of one of the pivot visit columns
        if (column.dataIndex == 'VisitMap')
        {
            var visitMapArr = record.get(column.dataIndex),
                matchingIndex = LABKEY.VaccineDesign.Utils.getMatchingRowIndexFromArray(visitMapArr, column.dataIndexArrFilterProp, column.dataIndexArrFilterValue);

            if (matchingIndex > -1)
            {
                if (newValue != null)
                    visitMapArr[matchingIndex][column.dataIndexArrValue] = newValue;
                else
                    Ext4.Array.splice(visitMapArr, matchingIndex, 1);
            }
            else if (newValue != null)
            {
                visitMapArr.push({
                    CohortId: record.get('RowId'),
                    VisitId: column.dataIndexArrFilterValue,
                    TreatmentId: newValue
                });
            }

            this.fireEvent('celledited', this, 'VisitMap', visitMapArr);
        }
        else
        {
            this.callParent([record, column, newValue]);
        }
    }
});