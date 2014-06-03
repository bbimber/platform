/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

function createMoveIssueWindow(issueId) {
    Ext4.onReady(function()
    {
        var cmp = Ext4.create('Issues.window.MoveIssue', {issueIds: [issueId]});
        cmp.show();
    });
}

Ext4.define('Issues.window.MoveIssue', {
    extend: 'Ext.window.Window',
    modal: true,
    border: false,
    width: 500,
    layout: 'fit',
    closeAction: 'destroy',
    title: 'Move Issue', // TODO: use entity noun here

    initComponent : function() {
        this.buttons = ['->', {
            text: 'Move',
            scope: this,
            handler: this.handleMoveIssue
        },{
            text: 'Cancel',
            scope: this,
            handler: this.handleCancel
        }];

        this.items = [this.getPanel()];

        this.callParent();
        this.on('show', function() {
            this.moveCombo.focus(false, 500);
        }, this)
    },

    getPanel: function(){
        var divContainer = Ext4.create('Ext.container.Container', {
            html: "<div>Select a container from the list below and click the 'Move' button</div>",
            margin: '0 0 15 0'
        });

        this.moveCombo = Ext4.create('Ext.form.field.ComboBox', {
            store: this.getUserStore(),
            name: 'move',
            allowBlank: false,
            valueField: 'containerId',
            displayField: 'containerPath',
            fieldLabel: 'Container',
            triggerAction: 'all',
            labelWidth: 65,
            queryMode: 'local',
            typeAhead: true,
            forceSelection: true,
            tpl: Ext4.create('Ext.XTemplate',
                    '<tpl for=".">',
                    '<div class="x4-boundlist-item">{containerPath:htmlEncode}</div>',
                    '</tpl>')
        });

        return {
            xtype: 'panel',
            padding: 10,
            border: false,
            layout: 'form',
            items: [divContainer, this.moveCombo]
        };
    },

    getUserStore: function(){
        // define data models
        if (!Ext4.ModelManager.isRegistered('Issues.model.Containers')) {
            Ext4.define('Issues.model.Containers', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'containerId', type: 'string'},
                    {name: 'containerPath', type: 'string'}
                ]
            });
        }

        return Ext4.create('Ext.data.Store', {
            model: 'Issues.model.Containers',
            autoLoad: true,
            proxy: {
                type: 'ajax',
                url: LABKEY.ActionURL.buildURL('issues', 'getMoveDestinations', LABKEY.container.path),
                reader: {
                    type: 'json',
                    root: 'containers'
                }
            }
        });
    },

    handleMoveIssue: function(){
        if (!this.moveCombo.isValid())
            return;

        var containerId = this.moveCombo.getValue();
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('issues', 'move'),
            method: 'POST',
            params: {
                issueIds: this.issueIds,
                containerId: containerId,
                returnUrl: window.location
            },
            scope: this,
            success: function(response){
                location = location;
            },
            failure: function(response){
                var jsonResp = LABKEY.Utils.decode(response.responseText);
                if (jsonResp && jsonResp.errors)
                {
                    var errorHTML = jsonResp.errors[0].message;
                    Ext4.Msg.alert('Error', errorHTML);
                }
            }
        });
    },

    handleCancel: function(){
        this.close();
    }
});
