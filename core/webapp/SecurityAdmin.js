/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var $ = Ext.get;
var $h = Ext.util.Format.htmlEncode;
var $dom = Ext.DomHelper;
var $url = LABKEY.ActionURL.buildURL;
var S = LABKEY.Security;

function Array_remove(array,item)
{
    var i = array.indexOf(item);
    if (i != -1)
        array.splice(i,1);
}


var SecurityCacheStore = Ext.extend(LABKEY.ext.Store,{
    constructor : function(config)
    {
        SecurityCacheStore.superclass.constructor.call(this,config);
        this.addEvents("ready");
        this.remoteSort = false;
        
        // consider the component 'ready' after first successful load
        this.on("load", function()
        {
            if (this.ready)
                return;
            this.ready = true;
            this.fireEvent("ready");
        }, this);
    },

    onReady : function(fn, scope)
    {
        if (this.ready)
        {
            fn.call(scope);
        }
        else
        {
            this.on("ready",fn,scope);
        }
    },

    onMetaChange : function(meta, rtype, o)
    {
        SecurityCacheStore.superclass.onMetaChange.apply(this, arguments);
    },

    removeById : function(id)
    {
        var record = this.getById(id);
        if (record)
            this.remove(record);
    },
    
    ready : false
});



/*
 * SecurityCache consolidates a lot of the background data requests into one place, with one
 * onReady() event
 *
 * Ext.data.Store is overkill, but it has handy events rigged already
 */
var SecurityCache = Ext.extend(Ext.util.Observable,{

    groupAdministrators : -1,
    groupUsers : -2,
    groupGuests : -3,
    groupDevelopers : -4,

    rootContainer : null,
    
    principalsStore : null,

    membershipStore : null,

    containersReady : false,
    projectsReady : false,
    ContainerRecord : Ext.data.Record.create(['id','name','path','sortOrder', {name:'project', type:'boolean'}]),
    containersStore : null,

    rolesReady : false,
    roles : null,
    roleMap : {},

    resourcesReady : false,
    resources : null,
    resourceMap : {},
    
    /*
     * config just takes a project path/id and folder path/id
     *
     * fires "ready" when all the initially requested objects are loaded, subsequent loads
     * are handled with regular callbacks
     */
    constructor : function(config)
    {
        SecurityCache.superclass.constructor.call(this,config);
        this.addEvents(['ready']);

        this.projectId = config.project || config.folder || LABKEY.container.id;
        this.folderId = config.folder || config.project || LABKEY.container.id;

        this.principalsStore = new SecurityCacheStore(
        {
            id:'UserId',
            schemaName:'core',
            queryName:'Principals'
        });
        this.principalsStore.on("loadexception", this._onLoadException, this);
        this.principalsStore.on("metachange", function(store,meta)
        {
            meta.fields.push({name:'sortOrder',type:'string',mvEnabled:false});
            var rtype = Ext.data.Record.create(meta.fields,'UserId');
            store.fields = rtype.prototype.fields;
            store.recordType = store.reader.recordType = rtype;
        }, this);

        this.membershipStore = new SecurityCacheStore(
        {
            schemaName:'core',
            queryName:'Members'
        });
        this.membershipStore.on("loadexception", this._onLoadException, this);

        this.containersStore = new Ext.data.Store({id:'id'}, this.ContainerRecord);

        S.getContainers({containerPath:this.projectId, includeSubfolders:true, successCallback:this._loadContainersResponse, errorCallback:this._errorCallback, scope:this});
        S.getContainers({containerPath:'/', includeSubfolders:true, depth:1, successCallback:this._loadProjectsResponse, errorCallback:this._errorCallback, scope:this});

        S.getRoles({containerPath:this.folderId, successCallback:this._loadRolesResponse, errorCallback:this._errorCallback, scope:this});
        this.principalsStore.load();
        this.principalsStore.onReady(this.Principals_onReady, this);
        S.getSecurableResources({successCallback:this._loadResourcesResponse, errorCallback:this._errorCallback, scope:this});

        // not required for onReady
        this.membershipStore.load();
    },

    _onLoadException : function(proxy, result, response, e)
    {
        console.log(e.message);   
    },

    getRole : function(id)
    {
        return this.roleMap[id];
    },


    getPrincipal : function(id)
    {
        var record = this.principalsStore.getById(id);
        if (record)
            return record.data;
        return null;
    },

    // type = 'project', 'site', 'both'
    getGroups : function(type)
    {
        var groups = [];
        if (!type) type = 'both';
        this.principalsStore.each(function(record){
            var data = record.data;
            if (data.TYPE == 'u')
                return;
            if ('both' == type || 'site' == type && !data.Container || 'project' == type && data.Container)
                groups.push(data);
        });
        return groups;
    },


    // direct (and explicit) group membership for this principal
    getGroupsFor : function(principal)
    {
        if (!this.mapPrincipalToGroups)
            this._computeMembershipMaps();
        var ids = this.mapPrincipalToGroups[principal] || [];
        var groups = [];
        for (var i=0 ; i<ids.length ; i++)
        {
            var id = ids[i];
            var record = this.principalsStore.getById(id);
            if (!record)
                continue;
            var group = record.data;
            groups.push(group);
        }
        return groups;
    },

    // recursive membership, return array of ids
    getEffectiveGroups : function(principal)
    {
        if (principal == this.groupGuests || principal == 0) // guest
            return [0, this.groupGuests];
        var set = {};
        set[this.groupGuests] = true;
        set[this.groupUsers] = true;
        this._collectGroups([principal], set);
        var ret = [];
        for (var id  in set)
            ret.push(id);
        ret.sort();
        return ret;
    },


    // recursive group memberships
    _collectGroups : function(groups, set)
    {
        if (!groups)
            return;
        for (var g=0 ; g<groups.length ; g++)
        {
            var id = groups[g];
            if (set[id])
                continue;
            set[id] = true;
            this._collectGroups(this.mapPrincipalToGroups[id], set);
        }
    },


    // non-recursive, return objects
    getMembersOf : function(principal)
    {
        if (principal == this.groupUsers)
            return [];
        if (principal == this.groupGuests)
            return [{UserId:0, Name:'Guest'}];
        if (!this.mapPrincipalToGroups)
            this._computeMembershipMaps();
        var users = [];
        var ids = this.mapGroupToMembers[principal] || [];
        for (var i=0 ; i<ids.length ; i++)
        {
            var record = this.principalsStore.getById(ids[i]);
            if (record)
                users.push(record.data);
        }
        return users;
    },

    
    // recursive, returns objects
    getEffectiveMembers : function(principal, users, set)
    {
        users = users || [];
        set = set || {};
        if (principal == this.groupUsers || principal == this.groupGuests)
            return users;
        if (!this.mapPrincipalToGroups)
            this._computeMembershipMaps();
        var ids = this.mapGroupToMembers[principal] || [];
        for (var i=0 ; i<ids.length ; i++)
        {
            var id = ids[i];
            var record = this.principalsStore.getById(id);
            if (!record)
                continue;
            var user = record.data;
            if (set[user.UserId])
                continue;
            set[user.UserId] = true;
            if (user.Type == 'u')
                users.push(user);
            else
                this.getEffectiveMembers(user.UserId, users, set);
        }
        return users;
    },

    createGroup : function(project, name, callback)
    {
        var me = this;
        S.createGroup({containerPath:project, groupName:name, successCallback:function(obj,response,options)
        {
            var group = {UserId:obj.id, Name:obj.name, Container:project, Type:'g'};
            var st = me.principalsStore;
            var record = new st.reader.recordType(group,group.UserId);
            me._applyPrincipalsSortOrder(record);
            st.add(record);
            st.applySort();
        }});
    },

    deleteGroup : function(groupid, callback)
    {
        var group = this.getPrincipal(groupid);
        if (group && group.Type == 'g')
        {
            var me = this;
            S.deleteGroup({groupId:groupid, containerPath:(group.Container||'/'), successCallback:function()
            {
                me.principalsStore.removeById(groupid);
                callback.call();
            }});
        }
    },

    addMembership : function(groupid,userid,callback)
    {
        var group = this.getPrincipal(groupid);
        if (group && (group.Type == 'g' || group.Type == 'r'))
        {
            var success = function()
            {
                this._addMembership(groupid,userid);
                callback.call();
            };
            S.addGroupMembers({groupId:groupid, principalIds:[userid], containerPath:(group.Container||'/'), successCallback:success, scope:this});
        }
    },

    removeMembership : function(groupid,userid,callback)
    {
        var group = this.getPrincipal(groupid);
        if (group && (group.Type == 'g' || group.Type == 'r'))
        {
            var success = function()
            {
                this._removeMembership(groupid,userid);
                callback.call();
            };
            // find the container for the group
            S.removeGroupMembers({groupId:groupid, principalIds:[userid], containerPath:(group.Container||'/'), successCallback:success, scope:this});
        }
    },


    // NOT recursive
    mapGroupToMembers : null,
    mapPrincipalToGroups : null,

    _addMembership : function(groupid,userid)
    {
        if (this.mapGroupToMembers)
        {
            if (!this.mapGroupToMembers[groupid])
                this.mapGroupToMembers[groupid] = [userid];
            else if (-1 == this.mapGroupToMembers[groupid].indexOf(userid))
                this.mapGroupToMembers[groupid].push(userid);

            if (!this.mapPrincipalToGroups[userid])
                this.mapPrincipalToGroups[userid] = [groupid];
            else if (-1 == this.mapPrincipalToGroups[userid].indexOf(groupid))
                this.mapPrincipalToGroups[userid].push(groupid);
        }
        var r = this.principalsStore.getById(groupid);
        if (r)
            this.principalsStore.fireEvent("update", this.principalsStore, r, Ext.data.Record.EDIT);
        r = this.principalsStore.getById(userid);
        if (r)
            this.principalsStore.fireEvent("update", this.principalsStore, r, Ext.data.Record.EDIT);
    },

    _removeMembership : function(groupid,userid)
    {
        if (this.mapGroupToMembers)
        {
            if (this.mapGroupToMembers[groupid])
                Array_remove(this.mapGroupToMembers[groupid],userid);
            if (this.mapPrincipalToGroups[userid])
                Array_remove(this.mapPrincipalToGroups[userid],groupid);
        }
        var r = this.principalsStore.getById(groupid);
        if (r)
            this.principalsStore.fireEvent("update", this.principalsStore, r, Ext.data.Record.EDIT);
        r = this.principalsStore.getById(userid);
        if (r)
            this.principalsStore.fireEvent("update", this.principalsStore, r, Ext.data.Record.EDIT);
    },

    _computeMembershipMaps : function()
    {
        var groups = {};
        var members = {};
        
        this.membershipStore.each(function(item){
            var uid = item.data.UserId;
            var gid = item.data.GroupId;
            groups[uid] ? groups[uid].push(gid) : groups[uid]=[gid];
            members[gid] ? members[gid].push(uid) : members[gid]=[uid];
        });

        this.mapGroupToMembers = members;
        this.mapPrincipalToGroups = groups;
    },

    getResource : function(id)
    {
        return this.resourceMap[id];
    },

    ready : false,
    
    checkReady : function()
    {
        if (this.principalsStore.ready &&
            this.containersReady &&
            this.projectsReady &&
            this.rolesReady &&
            this.resourcesReady)
        {
            this.ready = true;
            this.fireEvent("ready");
        }
    },

    onReady : function(fn, scope)
    {
        if (this.ready)
            fn.call(scope);
        else
            this.on("ready", fn, scope);
    },

    Principals_onReady : function()
    {
        // add a sortOrder field to each principal
        this.principalsStore.data.each(this._applyPrincipalsSortOrder);
        this.principalsStore.sort('sortOrder');
        this.checkReady();
    },

    _applyPrincipalsSortOrder : function(item)
    {
        var data = item.data;
        var major = data.Type == 'u' ? '3' : data.Container ? '2' : '1';
        var minor = data.Name.toLowerCase();
        data.sortOrder = major + "." + minor;
    },

    _errorCallback: function(e,r)
    {
        console.error(e + " " + r);
    },

    _loadResourcesResponse : function(r)
    {
        this.resources = r.resources;
        this._mapResources([r.resources],this.resourceMap);
        this.resourcesReady = true;
        this.checkReady();
    },

    _mapResources : function(list,map)
    {
        if (!list || list.length==0)
            return map;
        for (var i=0; i<list.length ; i++)
        {
            var r = list[i];
            map[r.id] = r;
            this._mapResources(r.children, map);
        }
        return map;
    },

    _loadRolesResponse : function(resp)
    {
        this.roles = resp;
        for (var r=0 ; r<this.roles.length ; r++)
        {
            var role = this.roles[r];
            this.roleMap[role.uniqueName] = role;
            if (!role.excludedPrincipals)
                role.excludedPrincipals = [];
            role.accept = function(id)
            {
                if (typeof id == 'object')
                    id = id.Userid;
                //return -1 == role.excludedPrincipals.indexOf(id);
                for (var i=0 ; i<this.excludedPrincipals.length ; i++)
                    if (id == this.excludedPrincipals[i])
                        return false;
                return true;
            };
        }
        this.rolesReady = true;
        this.checkReady();
    },

    _makeRecord : function(c)
    {
        c.project = c.path != '/' && 0 == c.path.lastIndexOf('/');
        return new this.ContainerRecord(c,c.id);
    },
            
    _loadContainersResponse :function(r)
    {
        var map = this._mapContainers(null, [r], {});
        var records = [];
        for (var id in map)
            records.push(this._makeRecord(map[id]));
        this.containersStore.add(records);
        this.containersReady = true;
        this.checkReady();
    },

    _loadProjectsResponse : function(r)
    {
        var map = this._mapContainers(null, [r], {});
        var records = [];
        for (var id in map)
        {
            var c = map[id];
            if (c.path=='/')
                this.rootContainer = c;
            records.push(this._makeRecord(c));
        }
        this.containersStore.add(records);
        this.projectsReady = true;
        this.checkReady();
    },

    _mapContainers : function(parent, list, map)
    {
        if (!list || list.length == 0)
            return map;
        for (var i=0; i<list.length ; i++)
        {
            var c = list[i];
            c.parent = parent;
            map[c.id] = c;
            this._mapContainers(c, c.children, map);
        }
        return map;                                                                                 
    }
});


function _link(text,href)
{
    var html = ["[",
        {tag:'a', href:(href||'#'), children:$h(text)},
        "]"];
    return $dom.markup(html);
}
function $open(href)
{
    window.open(href+'&_print=1','_blank','width=500,height=500');    
}
function _open(text,href)
{
    var html = ["[",
        {tag:'a', href:'#', onclick:$h("$open('" + href + "')"), children:$h(text)},
        "]"];
    return $dom.markup(html);
}


/* config: cache, user or userId, policy, canEdit */

var UserInfoPopup = Ext.extend(Ext.Window,{
    constructor : function(config)
    {
        if (!config.user && config.userId)
            config.user = config.cache.getPrincipal(config.userId);

         //this.panel = new Ext.Panel({html:'hey'});
        config = Ext.apply({},config,{
            title:config.user.Name + ' Information',
            closeable : true,
            extraCls : 'extContainer',
            closeAction : 'close',
            constrain : true,
            minWidth:200,
            width:400,
            height:300,
            autoScroll:true,
            minHeight:200
        });
        UserInfoPopup.superclass.constructor.call(this, config);
    },

    addPrincipalComboBox : null,

    onRender : function(ct, where)
    {
        UserInfoPopup.superclass.onRender.call(this, ct, where);
        this._redraw();
    },

    onDestroy : function()
    {
        UserInfoPopup.superclass.onDestroy.call(this);
        if (this.addPrincipalComboBox)
            this.addPrincipalComboBox.destroy();
    },

    _redraw : function()
    {
        this.body.update('',false);

        var isGroup = this.user.Type == 'g' || this.user.Type == 'r';
        
        var html = ["<table width=100%><tr><td align=left valign=middle><h3 style='display:inline'>" + (isGroup?'Group ':'User ') + this.user.Name + "</h3></td><td align=right valign=middle>"];

        // links
        if (isGroup)
        {

            if (this.user.UserId == SecurityCache.prototype.groupUsers || this.user.UserId == SecurityCache.prototype.groupGuests)
                this.canEdit = false;
            if (this.canEdit)
            {
                html.push(_link('manage group...', $url('security','group',this.user.Container||'/',{id:this.user.UserId})));
                html.push('&nbsp;');
            }
            html.push(_open('permissions', $url('security','groupPermission',this.cache.projectId,{id:this.user.UserId})));
        }
        else
        {
            html.push(_open('permissions', $url('user','userAccess',this.cache.projectId,{userId:this.user.UserId})));
        }
        html.push("</td></tr></table><p/>");

        // groups
        var groups = this.cache.getGroupsFor(this.userId);
        if (groups.length)
        {
            html.push('<b>member of</b><ul style="list-style-type:none;">');
            for (var g=0 ; g<groups.length ; g++)
            {
                var group = groups[g];
                html.push("<li>" + group.Name + "</li>");
                // UNDONE: also render inherited groups (indented)?
            }
            html.push("</ul>\n");
        }

        // permissions
        if (this.policy)
        {
            var ids = this.cache.getEffectiveGroups(this.userId);
            var roles = this.policy.getEffectiveRolesForIds(ids);
            var allRoles = this.cache.roles;
            html.push('<b>effective roles</b><ul style="list-style-type:none;">');
            for (var r=0 ; r<allRoles.length ; r++)
            {
                var role = allRoles[r];
                if (roles[role.uniqueName])
                    html.push("<li>" + ((role && role.name) ? role.name : role.uniqueName) + "</li>");
            }
            html.push("</ul>\n");
        }

        var i, user;
        var id = Ext.id();
        var principalWrapper = null;
        var removeWrapper;
        var deleteGroup;
        
        // users
        if (isGroup)
        {
            var users = this.cache.getMembersOf(this.userId);
            html.push("<b>users</b>");

            if (this.userId == SecurityCache.prototype.groupUsers)
            {
                html.push("<p/>Site Users represents all signed-in users.");
            }
            else if (this.userId == SecurityCache.prototype.groupUsers)
            {
                html.push("Guest");
            }
            else
            {
                html.push("<table>");
                if (this.canEdit)
                {
                    principalWrapper = '$p$' + id;
                    html.push("<tr><td colspan=3 id=" + principalWrapper + "></td></tr>");
                    if (users.length == 0 && this.userId > 0)
                    {
                        deleteGroup = '$delete$' + id;
                        html.push('<tr><td colspan=3><a id="' + deleteGroup + '" class="labkey-button" href="#"" ><span>Delete Empty Group</span></a></td></tr>');
                    }
                }                
                for (i=0 ; i<users.length ; i++)
                {
                    user = users[i];
                    html.push("<tr><td width=100>" + $h(user.Name) + "</td>");
                    if (this.canEdit)
                    {
                        removeWrapper = '$remove$' + id + user.UserId;
                        html.push("<td>[<a href=# id=" + removeWrapper + ">remove</a>]</td><td>");
                    }
                    html.push(_open('permissions', $url('user','userAccess',this.cache.projectId,{userId:user.UserId})));
                    html.push("</td></tr>");
                }
                html.push("</table>");
            }
        }

        var m = $dom.markup(html);
        this.body.update(m, false);

        // render a principals drop down
        if (isGroup && this.canEdit)
        {
            if (deleteGroup)
                Ext.fly(deleteGroup).dom.onclick = this.DeleteGroup_onClick.createDelegate(this);
            this.addPrincipalComboBox = new PrincipalComboBox({cache:this.cache, usersOnly:true});
            this.addPrincipalComboBox.on("select",this.Combo_onSelect,this);
            this.addPrincipalComboBox.render(principalWrapper);
            for (i=0 ; i<users.length ; i++)
            {
                user = users[i];
                removeWrapper = '$remove$' + id + user.UserId;
                Ext.fly(removeWrapper).dom.onclick = this.RemoveMember_onClick.createDelegate(this,[user.UserId]);
            }
        }
    },

    DeleteGroup_onClick : function()
    {
        var groupid = this.user.UserId;
        this.cache.deleteGroup(groupid, this.close.createDelegate(this));
    },

    RemoveMember_onClick : function(userid)
    {
        var groupid = this.user.UserId;
        this.cache.removeMembership(groupid,userid,this._redraw.createDelegate(this));
    },

    Combo_onSelect : function(combo,record,index)
    {
        if (record)
        {
            var groupid = this.user.UserId;
            var userid = record.data.UserId;
            this.cache.addMembership(groupid,userid,this._redraw.createDelegate(this));
        }
        combo.selectText();
    }
});






var CloseButton = Ext.extend(Ext.Button,{

    template : new Ext.Template(
                    '<table border="0" cellpadding="0" cellspacing="0" class="x-btn-wrap" style="display:inline-table;"><tbody><tr>',
                    '<td class="x-btn-left"><i>&#160;</i></td><td class="x-btn-center"><em unselectable="on"><button class="x-btn-text" type="{1}">{0}</button></em></td><td class="x-btn-center"><i class="pclose">&#160;</i></td><td class="x-btn-right"><i>&#160;</i></td>',
                    "</tr></tbody></table>"),

    initComponent : function()
    {
        CloseButton.superclass.initComponent.call(this);
        this.addEvents(['close']);
    },

    onRender : function(ct, position)
    {
        CloseButton.superclass.onRender.call(this, ct, position);
        // find the close element
        var close = this.el.child('I[class=pclose]');
        if (close)
        {
            close.on("click",this.onClose,this);
            if (this.tooltip)
            {
                if (typeof this.closeTooltip == 'object')
                {
                    Ext.QuickTips.register(Ext.apply({target: close}, this.closeTooltip));
                }
                else
                {
                    close.dom[this.tooltipType] = this.closeTooltip;
                }
            }
        }
    },

    stoppedEvent : null,

    onClose : function(event)
    {
        if (this.disabled)
            return;
        // can't seem to actually stop mousedown events, but we can disable the button
        //        event.stopEvent();
        this.stoppedEvent = event;
        this.fireEvent("close", this, event);
    },

    onClick : function(event)
    {
        if (!this.stoppedEvent || event.type != 'click')
            CloseButton.superclass.onClick.call(this, event);
        this.stoppedEvent = null;
    }
});




var ButtonGroup = function()
{
    this.buttons = [];
};

Ext.apply(ButtonGroup.prototype, {

    buttons : null,

    add : function(btn)
    {
        this.buttons.push(btn);
        btn.on("mouseover", this.over, this);
        btn.on("mouseout", this.out, this);
    },

    over : function()
    {
        for (var i=0 ; i<this.buttons.length ; i++)
        {
            var btn = this.buttons[i];
            btn.el.addClass("x-btn-over");
        }
    },

    out : function()
    {
        for (var i=0 ; i<this.buttons.length ; i++)
        {
            var btn = this.buttons[i];
            btn.el.removeClass("x-btn-over");
        }
    }
});




var ButtonsDragDrop = Ext.extend(Ext.dd.DragZone,
{
    ddGroup : "ButtonsDD",

    constructor : function(container, config)
    {
        this.container = container;
        this.node = container.el.dom;
        //this.view = grid.getView();
        ButtonsDragDrop.superclass.constructor.call(this, this.node, config);
        this.scroll = false;
        this.ddel = document.createElement('div');
    },

    getDragData : function(e)
    {
        // is target a button in my container?
        var table = Ext.fly(e.getTarget()).findParentNode('table');
        if (!table || !table.id) return;
        var btn = this.container.getComponent(table.id);
        if (!btn)
            return false;
        if (!('groupId' in btn) || !btn.roleId)
            return false;
        return btn;
    },

    onInitDrag : function(e)
    {
        var data = this.dragData;
        this.ddel.innerHTML = data.text;
        this.proxy.update(this.ddel);
        this.proxy.setStatus(this.proxy.dropAllowed);
    },

    afterRepair : function()
    {
        this.dragging = false;
    },

    getRepairXY : function(e, data)
    {
        return false;
    },

    onEndDrag : function(data, e)
    {
    },

    onValidDrop : function(dd, e, id)
    {
        this.hideProxy();
    },

    beforeInvalidDrop : function(e, id)
    {
    }
});



function filterPrincipalsStore(store, type, container, excludedPrincipals)
{
    if (!excludedPrincipals)
        excludedPrincipals = {};
    var data = [];
    var records = store.getRange();
    for (var i=0 ; i<records.length ; i++)
    {
        var d = records[i].data;
        if (excludedPrincipals[d.UserId])
            continue;
        switch (type)
        {
        case 'users': if (d.Type != 'u') continue; break;
        case 'groups': if (d.Type != 'g' && d.Type != 'r') continue; break;
        case 'project' : if (d.Type != 'g' || d.Container != container) continue; break;
        case 'site' : if ((d.Type != 'g' && d.Type != 'r') || d.Container) continue; break;
        }
        data.push(d);
    }
    store = new Ext.data.Store({data:data, reader:new Ext.data.JsonReader({id:'UserId'},store.reader.recordType)});
    return store;
}



var PrincipalComboBox = Ext.extend(Ext.form.ComboBox,{

    excludedPrincipals : null,

    constructor : function(config)
    {
        var i;
        
        var a = config.excludedPrincipals || [];
        a.push(SecurityCache.prototype.groupDevelopers);
        a.push(SecurityCache.prototype.groupAdmin);
        delete config.excludedPrincipals;
        this.excludedPrincipals = {};
        for (i=0 ; i<a.length ; i++)
            this.excludedPrincipals[a[i]] = true;
        
        config = Ext.apply({}, config, {
            store : config.cache.principalsStore,
            mode : 'local',
            minListWidth : 200,
            style:{display:'inline'},
            triggerAction : 'all',
            forceSelection : true,
            typeAhead : true,
            displayField : 'Name',
            emptyText : config.groupsOnly ? 'Add group...' : config.usersOnly ? 'Add user...' : 'Add user or group...'
        });
        PrincipalComboBox.superclass.constructor.call(this, config);
    },

    tpl : new Ext.XTemplate('<tpl for="."><div class="x-combo-list-item {[this.extraClass(values)]}">{Name}</div></tpl>',
    {
        extraClass : function(values)
        {
            var c = 'pGroup';
            if (values.Type == 'u')
                c = 'pUser';
            else if (!values.Container)
                c = 'pSite';
            return c;
        }
    }),

    onDataChanged : function(store)
    {
        // UNDONE: reload combo
    },

    groupsOnly : false,
    usersOnly : false,
    unfilteredStore : null,

    bindStore : function(store, initial)
    {
        if (this.unfilteredStore)
            this.unfilteredStore.unwatch("datachanged", this.onDataChanged, this);

        // UNDONE: ComboBox does not lend itself to filtering, but this is expensive!
        // UNDONE: would be nice to share a filtered DataView like object across the PrincipalComboBoxes
        // CONSIDER only store UserId and lookup record in XTemplate
        if (store)
        {
            this.unfilteredStore = store;
            this.unfilteredStore.on("datachanged", this.onDataChanged, this);
            type = this.groupsOnly ? 'groups' : this.usersOnly ? 'users' : null;
            store = filterPrincipalsStore(store, type, null, this.excludedPrincipals);
        }
        PrincipalComboBox.superclass.bindStore.call(this,store,initial);
    },

    initComponent : function()
    {
        PrincipalComboBox.superclass.initComponent.call(this);
    },

    onRender : function(ct,position)
    {
        PrincipalComboBox.superclass.onRender.call(this,ct,position);
        this.el.parent().addStyles({display:'inline'});
    }
});


var GroupPicker = Ext.extend(Ext.Panel,{
    constructor : function(config)
    {
        config = Ext.apply({}, config, {
            cls : 'x-combo-list',
            displayField : 'Name',
            borders : false
        });
        GroupPicker.superclass.constructor.call(this,config);
        this.tpl.cache = this.cache;
    },

    projectId : null,
    view : null,
    cls : 'x-combo-list',
    selectedClass : 'x-combo-selected',

    tpl : new Ext.XTemplate('<tpl for="."><div class="x-combo-list-item {[this.extraClass(values)]}" style="cursor:pointer;"><table cellspacing=0 cellpadding=0 width=100%><tr><td align=left>{Name}</td><td align=right>{[this.count(values)]}</td></tr></table></div></tpl>',
    {
        extraClass : function(values)
        {
            var c = 'pGroup';
            if (values.Type == 'u')
                c = 'pUser';
            else if (values.Type == 's')
                c = 'pSite';
            return c;
        },
        count : function(values)
        {
            var m = this.cache.getMembersOf(values.UserId);
            return m.length;
        }
    }),
    
    initComponent : function()
    {
        GroupPicker.superclass.initComponent.call(this);

        this.addEvents("select");

        this.cache.principalsStore.on("datachanged",this.onDataChanged, this);
        this.cache.principalsStore.on("add",this.onDataChanged, this);
        this.cache.principalsStore.on("remove",this.onDataChanged, this);
        this.cache.principalsStore.on("update",this.onDataUpdated, this);

        this.view = new Ext.DataView({
            tpl: this.tpl,
            singleSelect: true,
            selectedClass: this.selectedClass,
            borders : false,
            itemSelector: this.itemSelector || '.' + this.cls + '-item'
        });
        this.view.on('click', this.onViewClick, this);
    },

    selectedGroup : null,

    onDataUpdated : function(s, record, type)
    {
        if (this.store && this.view)
            this.view.refresh(this.body);
    },

    onDataChanged : function()
    {
        this.store = filterPrincipalsStore(this.cache.principalsStore, (this.projectId ? 'project' : 'site'), this.projectId, {});
        if (this.view)
        {
            this.view.setStore(this.store);
            if (this.selectedGroup)
            {
                var index = this.store.indexOfId(this.selectedGroup.UserId);
                if (index >= 0)
                    this.view.select(index);
            }
        }
    },

    onViewClick : function(view,index,item,e)
    {
        var record = this.store.getAt(index);
        var group = record.data;
        this.selectedGroup = group;
        this.fireEvent('select', this, group);
    },
    
    onRender : function(ct,position)
    {   
        GroupPicker.superclass.onRender.call(this,ct,position);
        this.view.render(this.body);
        if (this.cache.principalsStore.ready)
            this.onDataChanged();
    }
});




/* config
 *
 * cache (SecurityCache)
 * resourceId
 * isSiteAdmin
 * isProjectAdministrator
 * saveButton (true/false)
 */                 
var PolicyEditor = Ext.extend(Ext.Panel, {

    constructor : function(config)
    {
        PolicyEditor.superclass.constructor.call(this,config);
        this.roleTemplate.compile();
        if (this.resourceId)
            this.setResource(this.resourceId);
        this.cache.principalsStore.on("remove",this.Principals_onRemove,this);
    },

    Principals_onRemove : function(store,record,index)
    {
        if (this.policy)
        {
            var id = record.id;
            this.policy.clearRoleAssignments(id);
            this._redraw();
        }
    },

    initComponent : function()
    {
    },

    onRender : function(ct, position)
    {
        PolicyEditor.superclass.onRender.call(this, ct, position);
        this._redraw();
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);
    },

    // config
    resourceId : null,
    saveButton : true,      // overloaded
    isSiteAdmin : false,
    isProjectAdmin : false,

    // components, internal
    inheritedCheckbox : null,
    table : null,

    // internal, private
    inheritedOriginally : false,
    resource : null,
    policy : null,
    roles : null,
    inheritedPolicy : null,
    buttonGroups : {},
    

    doLayout : function()
    {
    },


    isDirty : function()
    {
        if (this.inheritedCheckbox && this.inheritedCheckbox.getValue() != this.inheritedOriginally)
            return true;
        return this.policy && this.policy.isDirty();
    },


    setResource : function(id)
    {
        this.cache.onReady(function(){
            this.resource = this.cache.getResource(id);
            S.getPolicy({resourceId:id, successCallback:this.setPolicy , scope:this});
        },this);
    },


    setInheritedPolicy : function(policy)
    {
        this.inheritedPolicy = policy;
        if (this.inheritedCheckbox && this.inheritedCheckbox.getValue())
            this._redraw();
        if (this.inheritedCheckbox)
            this.inheritedCheckbox.enable();
    },
    

    setPolicy : function(policy, roles)
    {
        this.inheritedOriginally = policy.isInherited();
        if (this.inheritedCheckbox)
            this.inheritedCheckbox.setValue(this.inheritedOriginally);

        if (policy.isInherited())
        {
            this.inheritedPolicy = policy;
            this.policy = policy.copy(this.resource.id);
            if (this.inheritedCheckbox)
                this.inheritedCheckbox.enable();
        }
        else
        {
            this.policy = policy;
            // we'd still like to get the inherited policy
            if (this.resource.parentId && this.resource.parentId != this.cache.rootContainer.id)
                S.getPolicy({resourceId:this.resource.parentId, containerPath:this.resource.parentId, successCallback:this.setInheritedPolicy, scope:this});
        }
        this.roles = [];
        for (var r=0 ; r<roles.length ; r++)
        {
            var role = this.cache.getRole(roles[r]);
            if (role)
                this.roles.push(role);
        }
        this._redraw();
    },


    getPolicy : function()
    {
        if (this.inheritedCheckbox.getValue())
            return null;
        var policy = this.policy.copy();
        if (policy.isEmpty())
            policy.addRoleAssignment(this.cache.groupGuests, this.policy.noPermissionsRole);
        return policy;
    },


    // documented as a container method, but it's not there
    removeAll : function()
    {
        if (this.items)
            Ext.destroy.apply(Ext, this.items.items);
        this.items = null;
    },

    removeAllButtons : function()
    {
        for (var g in this.buttonGroups)
        {
            var bg = this.buttonGroups[g];
            for (var b=0 ; b<bg.buttons.length ; b++)
            {
                var btn = bg.buttons[b];
                this.remove(btn);
            }
        }
        this.buttonGroups = {};
    },

    _eachItem : function(fn)
    {
        this.items.each(function(item){
            if (item == this.saveButton)
                return;
            if (item == this.inheritedCheckbox)
                return;
            item[fn]();
        },this);
    },

    disable : function()
    {
        this._eachItem('disable');
    },

    enable : function()
    {
        this._eachItem('enable');
    },

    roleTemplate : new Ext.Template(
            '<tr id="$tr${uniqueName}" class="permissionsTR">'+
            '<td><img height=50 width=1 src="' + Ext.BLANK_IMAGE_URL + '"></td><td height=50 width=300 valign=top class="roleTD"><div><h3 class="rn">{name}</h3><div class="rd">{description}</div></div></td>'+
            '<td class="groupsTD" width=100%><table><tr><td><img height=25 width=1 src="' + Ext.BLANK_IMAGE_URL + '"></td><td valign=top id="$buttons${uniqueName}"><span id="$br${uniqueName}"></span></td></tr><tr><td><img height=25 width=1 src="' + Ext.BLANK_IMAGE_URL + '"></td><td id="$combo${uniqueName}"></td></tr></table></td>'+
            '</tr>\n'),

    _redraw : function()
    {
        if (!this.rendered)
            return;
        if (!this.roles)
        {
            this.body.update("<i>Loading...</i>");
            return;
        }
        
        var r, role, ct;

        this.removeAllButtons();

        // CONSIDER: use FormPanel for outer layout
        if (!this.table)
        {
            var html = [];

            var label = "Inherit permissions from " + (this.parentName || 'parent') + "<br>";
            html.push('<table><tr><td id=checkboxTD></td><td>&nbsp;' + label + '</td></tr></table>');

            html.push(['<table cellspacing=0 style="border-collapse:collapse;"><tr><td></td><th><h3>Roles<br><img src="' +Ext.BLANK_IMAGE_URL + '" width=300 height=1></h3></th><th><h3>Groups</h3></th></tr>']);
            var spacerRow = ''; // '<tr class="spacerTR"><td><img src="' + Ext.BLANK_IMAGE_URL + '"></td></tr>';
            for (r=0 ; r<this.roles.length ; r++)
            {
                role = this.roles[r];
                if (r > 0) html.push(spacerRow);
                html.push(this.roleTemplate.applyTemplate(role));
            }
            html.push("</table>");
            var m = $dom.markup(html);
            this.body.update("",false);
            this.table = this.body.insertHtml('beforeend', m, true);

            this.inheritedCheckbox = new Ext.form.Checkbox({id:'inheritedCheckbox', style:{display:'inline'}, disabled:(!this.inheritedPolicy), checked:this.inheritedOriginally});
            this.inheritedCheckbox.render('checkboxTD');
            this.inheritedCheckbox.on("check", this.Inherited_onChange, this);
            this.add(this.inheritedCheckbox);

            if (this.saveButton) // check if caller want's to omit the button
            {
                this.saveButton = new Ext.Button({text:'Save', handler:this.SaveButton_onClick, scope:this});
                this.saveButton.render(this.body);
                this.add(this.saveButton);
            }
            
            for (r=0 ; r<this.roles.length ; r++)
            {
                // add role combo
                role = this.roles[r];
                var c = new PrincipalComboBox({cache:this.cache, id:('$add$'+role.uniqueName), roleId:role.uniqueName, excludedPrincipals:role.excludedPrincipals});
                c.on("select", this.Combo_onSelect, this);
                c.render(Ext.fly('$combo$' + role.uniqueName));
                this.add(c);

                // DropTarget
                new Ext.dd.DropTarget('$tr$' + role.uniqueName,
                {
                    editor : this,
                    role : role,
                    ddGroup  : 'ButtonsDD',
                    notifyEnter : function(dd, e, data)
                    {
                        this.el.stopFx();
                        if (data.roleId == this.role.uniqueName || !this.role.accept(data.groupId))
                        {
                            dd.proxy.setStatus(this.dropNotAllowed);
                            return this.dropNotAllowed;
                        }
                        // DOESN'T WORK RELIABLY... this.el.highlight("ffff9c",{duration:1});
                        dd.proxy.setStatus(this.dropAllowed);
                        return this.dropAllowed;
                    },
                    notifyOut : function(dd, e, data)
                    {
                        this.el.stopFx();
                    },
                    notifyDrop  : function(ddSource, e, data)
                    {
                        this.el.stopFx();
                        console.log('drop ' + (e.shiftKey?'SHIFT ':'') + data.text + ' ' + data.groupId + ' ' + data.roleId);
                        if (data.roleId == this.role.uniqueName || !this.role.accept(data.groupId))
                        {
                            // add for fail animation
                            this.editor.addRoleAssignment(data.groupId, data.roleId, ddSource.proxy.el);
                            return false;
                        }
                        else
                        {
                            this.editor.addRoleAssignment(data.groupId, this.role, ddSource.proxy.el);
                            if (!e.shiftKey)
                                this.editor.removeRoleAssignment(data.groupId, data.roleId);
                            return true;
                        }
                    }
                });
            }
            // DropSource (whole editor)
            new ButtonsDragDrop(this, {});
        }

        // render security policy
        var policy = this.inheritedCheckbox.getValue() ? this.inheritedPolicy : this.policy;
        if (policy)
        {
            // render the security policy buttons
            for (r=0 ; r<this.roles.length ; r++)
            {
                role = this.roles[r];
                var groups = policy.getAssignedPrincipals(role.uniqueName);
                for (var g=0 ; g<groups.length ; g++)
                {
                    var group = this.cache.getPrincipal(groups[g]);
                    if (!group) continue;
                    this.addButton(group,role,false);
                }
            }
            // make selenium testing easiers
            if (!$('policyRendered'))
                $dom.insertHtml('beforeend', document.body, '<input type=hidden id="policyRendered" value="1">');
        }
        if (this.inheritedCheckbox.getValue())
            this.disable();
        else
            this.enable();
    },


    // expects button to have roleId and groupId attribute
    Button_onClose : function(btn,event)
    {
        if (!this.inheritedCheckbox.getValue())
            this.removeRoleAssignment(btn.groupId, btn.roleId);
    },


    Button_onClick : function(btn,event)
    {
        var id = btn.groupId;
        var policy = this.inheritedCheckbox.getValue() ? this.inheritedPolicy : this.policy;
        // is this a user or a group
        var principal = this.cache.getPrincipal(id);
        // can edit?
        var canEdit = !principal.Container && this.isSiteAdmin || principal.Container && this.isProjectAdmin;
        var w = new UserInfoPopup({userId:id, cache:this.cache, policy:policy, modal:true, canEdit:canEdit});
        w.show();
    },


    // expects combo to have roleId attribute
    Combo_onSelect : function(combo,record,index)
    {
        if (record)
            this.addRoleAssignment(record.data, combo.roleId);

        combo.selectText();
        //combo.reset();
        //Ext.getBody().el.focus.defer(100);
        // reset(), and clearValue() seem to leave combo in bad state
        // however, calling selectText() allows you to start typing a new value right away
    },


    Inherited_onChange : function(checkbox)
    {
        var inh = this.inheritedCheckbox.getValue();
        if (inh && !this.inheritedPolicy)
        {
            // UNDONE: use blank if we don't know the inherited policy
            this.inheritedPolicy = this.policy.copy();
            this.inheritedPolicy.clearRoleAssignments();
        }
        if (!inh && !this.policy)
        {
            this.policy = this.inheritedPolicy.copy();
        }
        this._redraw();
    },

    
    addButton : function(group, role, animate, animEl)
    {
        if (typeof group != 'object')
            group = this.cache.getPrincipal(group);
        var groupName = group.Name;
        var groupId = group.UserId;
        var roleId = role; 
        if (typeof role == 'object')
            roleId = role.uniqueName;

        var style = 'pGroup';
        if (group.Type == 'u')
            style = 'pUser';
        else if (!group.Container)
            style = 'pSite';

        var btnId = roleId+'$'+groupId;
        var btnEl = Ext.fly(btnId);

        var br = Ext.get('$br$' + roleId);  // why doesn't Ext.fly() work?
        if (typeof animate == 'boolean' && animate)
        {
            if (!animEl)
                animEl = this.getComponent('$add$' + roleId).el;
            var body = Ext.getBody();
            var span = body.insertHtml("beforeend",'<span style:"position:absolute;">' + $h(groupName) + '<span>', true);
            span.setXY(animEl.getXY());
            var xy = btnEl ? btnEl.getXY() : br.getXY();
            span.shift({x:xy[0], y:xy[1], callback:function(){
                span.remove();
                this.addButton(group, role, false);
            }, scope:this});
            return;
        }
        
        if (btnEl)
        {
            btnEl.frame();
            return;
        }

        // really add the button
        var ct = Ext.fly(roleId);
        b = new CloseButton({text:groupName, id:btnId, groupId:groupId, roleId:roleId, closeTooltip:'Remove ' + groupName + ' from role'});
        b.addClass(style);
        b.on("close", this.Button_onClose, this);
        b.on("click", this.Button_onClick, this);
        b.render(ct, br);
        br.insertHtml("beforebegin"," ");

        if (typeof animate == 'string')
            b.el[animate]();

        this.add(b);
        if (!this.buttonGroups[groupId])
            this.buttonGroups[groupId] = new ButtonGroup();
        this.buttonGroups[groupId].add(b);
    },

    highlightGroup : function(groupId)
    {
        var btns = this.getButtonsForGroup(groupId);
        for (var i ; i<btns.length ; i++)
            btns[i].el.frame();
    },
    
    getButtonsForGroup : function(groupId)
    {
        var btns = [];
        this.items.each(function(item){
            if (item.buttonSelector && item.groupId == groupId) btns.push(item)
        });
        return btns;
    },

    removeButton : function(groupId, roleId, animate)
    {
        var button = this.getComponent(roleId+ '$' + groupId);
        if (!button)
            return;
        if (animate)
        {
            var combo = this.getComponent('$add$' + roleId);
            var xy = combo.el.getXY();
            var fx = {callback:this.removeButton.createDelegate(this,[groupId,roleId,false]), x:xy[0], y:xy[1], opacity:0};
            if (typeof animate == 'string')
                button.el[animate](fx);
            else
                button.el.shift(fx);
            return;
        }
        var button = this.getComponent(roleId+ '$' + groupId);
        if (button)
        {
            this.remove(button);
        }
    },
    
    addRoleAssignment : function(group, role, animEl)
    {
        var groupId = group;
        if (typeof group == "object")
            groupId = group.UserId;
        var roleId = role;
        if (typeof role == "object")
            roleId = role.uniqueName;
        this.policy.addRoleAssignment(groupId, roleId);

        this.addButton(group,role,true,animEl);
    },

    removeRoleAssignment : function(group, role)
    {
        var groupId = group;
        if (typeof group == "object")
            groupId = group.UserId;
        var roleId = role;
        if (typeof role == "object")
            roleId= role.uniqueName;
        this.policy.removeRoleAssignment(groupId,roleId);
        this.removeButton(groupId, roleId, true);
    },



    /*
     * SAVE
     */
    
    SaveButton_onClick : function(e)
    {
        this.save(false);
    },

    save : function(overwrite)
    {
        Ext.removeNode(document.getElementById('policyRendered')); // to aid selenium automation
        
        var policy = this.getPolicy();
        this.disable();
        if (!policy)
            S.deletePolicy({resourceId:this.resource.id, successCallback:this.saveSuccess, errorCallback:this.saveFail, scope:this});
        else
        {
            if (overwrite)
                policy.setModified(null);
            S.savePolicy({policy:policy, successCallback:this.saveSuccess, errorCallback:this.saveFail, scope:this});
        }
    },

    saveSuccess : function()
    {
        // reload policy
        S.getPolicy({resourceId:this.resource.id, successCallback:this.setPolicy, scope:this});
        // feedback
        var mb = Ext.MessageBox.show({title : 'Save', msg:'<div align=center><h3 style="color:green;">save successful</h3></div>', width:150, animEl:this.saveButton});
        var w = mb.getDialog();
        var save = w.el.getStyles();
        w.el.pause(1);
        w.el.fadeOut({callback:function(){mb.hide(); w.el.addStyles(save);}, scope:mb});
    },

    saveFail : function(json, response, options)
    {
        var optimisticFail = false;
        if (-1 != response.responseText.indexOf('OptimisticConflictException'))
            optimisticFail = true;
        if (-1 != json.exception.indexOf('has been altered by someone'))
            optimisticFail = true;

        if (optimisticFail)
        {
            // UNDONE: prompt for overwrite
            Ext.MessageBox.alert("Error", (json.exception || response.statusText || 'save failed'));
            S.getPolicy({resourceId:this.resource.id, successCallback:this.setPolicy, scope:this});
            return;
        }

        Ext.MessageBox.alert("Error", (json.exception || response.statusText || 'save failed'));
        this.enable();
    }
});
