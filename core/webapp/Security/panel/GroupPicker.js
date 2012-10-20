Ext4.define('Security.panel.GroupPicker', {

    extend: 'Ext.panel.Panel',

    alias: 'widget.labkey-grouppicker',
//
//    requires : [
//        'Security.store.SecurityCache'
//    ],

    projectId : null,
    view : null,
//    cls : 'x-combo-list',
//    selectedClass : 'x-combo-selected',

    selectedGroup : null,

    initComponent : function()
    {
        Ext4.apply(this, {
            border: false,
            defaults: {
                border: false
            }
        });

        this.callParent(arguments);

        this.addEvents('select');

        // This should only fire once
        var fired = false;
        this.cache.onReady(function(){

            if (!fired)
            {
                fired = true;
                this.cache.principalsStore.on('datachanged', this.onDataChanged, this);
                this.cache.membershipStore.on('datachanged', this.onDataChanged, this);

                this.updateStore();
                this.add(this.getGrid());
            }

        }, this);
    },

    extraClass : function(values)
    {
        var c = 'pGroup';
        if (values.Type == 'u')
            c = 'pUser';
        else if (values.Type == 's')
            c = 'pSite';
        return c;
    },

    getGrid : function() {

        if (this.grid)
            return this.grid;

        if (!this.store)
            throw "data source not available for group selection grid.";

        this.grid = Ext4.create('Ext.grid.Panel', {
            store          : this.store,
            deferredRender : true,
            emptyText      : this.emptyText||'No groups defined',
            reserveScrollOffset : true,
            forceFit : true,
            columns: [{
                header: 'Group',
                width: 220,
                dataIndex: 'name',
                tpl:'<div class="{extraClass}" style="cursor:pointer;">{name}</div>'
            },{
                header: '<span ext:qtip="Direct group memberships for the group">Member Groups</span>',
                width: 120,
                dataIndex: 'countGroups',
                align:'right'
            },{
                header: '<span ext:qtip="Direct user memberships for the group">Member Users</span>',
                width: 115,
                dataIndex: 'countUsers',
                align:'right'
            },
            {
                header: '<span ext:qtip="Recursive count of unique users in the group and all member groups">Total Users</span>',
                width: 100,
                dataIndex: 'countTotalUsers',
                align:'right'
            }],
            listeners: {
                select: this.onViewClick,
                scope: this
            },
            scope : this
        });

        return this.grid;
    },

    onDataChanged : function()
    {
        this.updateStore();
    },

    updateStore : function()
    {
        var principalsStore = Security.store.SecurityCache.filterPrincipalsStore(this.cache.principalsStore, (this.projectId ? 'project' : 'site'), this.projectId, {});
        var data = [], countAllMembers, countMemberGroups, countTotalUsers;
        principalsStore.each(function(r){

            countAllMembers   = this.cache.getMembersOf(r.data.UserId).length;
            countMemberGroups = this.cache.getMemberGroups(r.data.UserId).length;
            countTotalUsers   = this.cache.getEffectiveUsers(r.data.UserId).length;

            data.push([
                r.data.Name,
                (countAllMembers-countMemberGroups),
                countMemberGroups,
                countTotalUsers,
                r.data.Type,
                this.extraClass(r.data),
                r.data.UserId
            ]);
        }, this);

        if(!this.store)
        {
            this.store = Ext4.create('Ext.data.ArrayStore', {
                fields   : ['name','countUsers','countGroups','countTotalUsers','type','extraClass','id'],
                data     : data,
                autoLoad : true
            });
        }
        else
        {
            this.store.removeAll();
            this.store.add(data);
        }
    },

    onViewClick : function(model,record,i,e)
    {
        var group = this.cache.getPrincipal(record.data.id);
        if (group)
        {
            this.selectedGroup = group;
            this.fireEvent('select', this, group);
        }
    },

    onRender : function(ct,position)
    {
        this.callParent(arguments);
        this.cache.onReady(this.onDataChanged,this);
        this.addClass('groupPicker');
    }
});
