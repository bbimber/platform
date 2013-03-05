/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresScript("/study/DataViewUtil.js");

Ext4.define('LABKEY.ext4.UsersCombo', {

    extend  : 'Ext.form.field.ComboBox',
    alias   : 'widget.lk-userscombo',

    constructor : function(config){

        Ext4.applyIf(config, {

            fieldLabel  : 'Users',
/*
            typeAhead   : true,
            typeAheadDelay : 75,
*/
            editable    : false,
            forceSelection : true, // user must pick from list
            store       : LABKEY.study.DataViewUtil.getUsersStore(),
            queryMode   : 'local',
            displayField : 'DisplayName',
            valueField  : 'UserId'
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.getStore().on('load', function() {
            if (this.initialValue)
                this.setValue(this.initialValue);
        }, this);

        this.callParent();
    },

    setValue : function(value) {
        this.initialValue = value;
        this.callParent([value]);
    }
});

