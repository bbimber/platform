/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.3
 * @license Copyright (c) 2008 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

Ext.namespace("LABKEY", "LABKEY.ext");

/**
 * Constructs a new LabKey Store using the supplied configuration.
 * @class LabKey extension to the <a href="http://extjs.com/deploy/dev/docs/?class=Ext.data.Store">Ext.data.Store</a> class,
 * which can retrieve data from a LabKey server, track changes, and update the server upon demand. This is most typically
 * used with data-bound user interface widgets, such as the LABKEY.ext.Grid. 
 * @constructor
 * @augments Ext.data.Store
 * @param config Configuration properties.
 * @param {String} config.schemaName The LabKey schema to query.
 * @param {String} config.queryName The query name within the schema to fetch.
 * @param {String} [config.viewName] A saved custom view of the specified query to use if desired.
 * @param {String} [config.columns] A comma-delimeted list of column names to fetch from the specified query. Note
 *  that the names may refer to columns in related tables using the form 'column/column/column' (e.g., 'RelatedPeptide/TrimmedPeptide').
 * @param {String} [config.sort] A base sort specification in the form of '[-]column,[-]column' ('-' is used for descending sort).
 * @param {Array} [config.filterArray] An array of LABKEY.Filter.FilterDefinition objects to use as the base filters.
 * @example &lt;script type="text/javascript"&gt;
	LABKEY.requiresClientAPI();
&lt;/script&gt;
&lt;script type="text/javascript"&gt;
    var _grid;
    Ext.onReady(function(){

        //initialize the Ext QuickTips support
        //which allows quick tips to be shown
        Ext.QuickTips.init();

        _store = new LABKEY.ext.Store({
            schemaName: 'lists',
            queryName: 'Kitchen Sink'
        });

        _grid = new LABKEY.ext.EditorGridPanel({
            store: new LABKEY.ext.Store({
                schemaName: 'lists',
                queryName: 'People'
            }),
            renderTo: 'grid',
            width: 800,
            autoHeight: true,
            title: 'Example',
            editable: true
        });
    });
&lt;/script&gt;
&lt;div id='grid'/&gt;
 */
LABKEY.ext.Store = Ext.extend(Ext.data.Store, {
    constructor: function(config) {

        Ext.apply(this, config);
        this.remoteSort = true;

        var baseParams = {schemaName: config.schemaName};
        baseParams['query.queryName'] = config.queryName;
        if (config.sort)
            baseParams['query.sort'] = config.sort;

        if (config.filterArray)
        {
            for (var i = 0; i < config.filterArray.length; i++)
            {
                var filter = config.filterArray[i];
                baseParams[filter.getURLParameterName()] = filter.getURLParameterValue();
            }
        }

        if(config.viewName)
            baseParams['query.viewName'] = config.viewName;

        if(config.columns)
            baseParams['query.columns'] = config.columns;

        LABKEY.ext.Store.superclass.constructor.call(this, {
            reader: new Ext.data.JsonReader(),
            proxy : new Ext.data.HttpProxy(new Ext.data.Connection({
                method: 'GET',
                url: LABKEY.ActionURL.buildURL("query", "selectRows", config.containerPath)
            })),
            baseParams: baseParams,
            listeners: {
                'load': {fn: this.onLoad, scope: this},
                'loadexception' : {fn: this.onLoadException, scope: this}
            }
        });

        //subscribe to the proxy's beforeload event so that we can map parameter names
        this.proxy.on("beforeload", this.onBeforeLoad, this);
    },

    /**
     * Adds a new record to the store based upon a raw data object.
     * @name addRecord
     * @function
     * @memberOf LABKEY.ext.Store
     * @param {Object} data The raw data object containing a properties for each field.
     * @param {integer} [index] The index at which to insert the record. If not supplied, the new
     * record will be added to the end of the store.
     * @returns {Ext.data.Record} The new Ext.data.Record object.
     */
    addRecord : function(data, index) {
        if(undefined == index)
            index = this.getCount();

        var fields = this.reader.meta.fields;

        //if no data was passed, create a new object with
        //all nulls for the field values
        if(!data)
            data = {};

        //set any non-specified field to null
        //some bound control (like the grid) need a property
        //defined for each field
        var field;
        for(var idx = 0; idx < fields.length; ++idx)
        {
            field = fields[idx];
            if(!data[field.name])
                data[field.name] = null;
        }

        var recordConstructor = Ext.data.Record.create(fields);
        var record = new recordConstructor(data);

        //add an isNew property so we know that this is a new record
        record.isNew = true;
        this.insert(index, record);
        return record;
    },

    /**
     * Deletes a set of records from the store as well as the server. This cannot be undone.
     * @name deleteRecords
     * @function
     * @memberOf LABKEY.ext.Store
     * @param {Array of Ext.data.Record objects} records The records to delete.
     */
    deleteRecords : function(records) {
        if(!records || records.length == 0)
            return;

        var deleteRowsKeys = [];
        var key;
        for(var idx = 0; idx < records.length; ++idx)
        {
            key = {};
            key[this.idName] = records[idx].id
            deleteRowsKeys[idx] = key;
        }

        //send the delete
        LABKEY.Query.deleteRows({
            schemaName: this.schemaName,
            queryName: this.queryName,
            containerPath: this.containerPath,
            rowDataArray: deleteRowsKeys,
            successCallback: this.getDeleteSuccessHandler(),
            action: "deleteRows" //hack for Query.js bug
        })
    },

    /**
     * Commits all changes made locally to the server. This method executes the updates asynchronously,
     * so it will return before the changes are fully made on the server. Records that are being saved
     * will have a property called 'saveOperationInProgress' set to true, and you can test if a Record
     * is currently being saved using the isUpdateInProgress method. Once the record has been updated
     * on the server, the existing record will be removed from the store and a new one will be added.
     * This is necessary as the key value may have changed. Subscribe to the "add" event to be notified
     * when the new records are added, the "remove" event to be notified when the old records are removed
     * or the "datachanged" event to be notified in general when records have been modified.
     * @name commitChanges
     * @function
     * @memberOf LABKEY.ext.Store
     */
    commitChanges : function() {
        var records = this.getModifiedRecords();
        if(!records || records.length == 0)
            return;

        //copy the modified row data into two arrays
        //one for rows to insert and one for rows to update
        //consider: maybe we should support a general saveRows
        //API that uses an metaData.isNew property to distinguish
        //between insert and update. That way we could do just one HTTP request.
        //In practice, the grid will commit row-by-row, so it won't matter
        var insertRecords = [];
        var updateRecords = [];
        var insertRowsData = [];
        var updateRowsData = [];
        var record;
        for(var idx = 0; idx < records.length; ++idx)
        {
            record = records[idx];

            //if we are already in the process of saving this record, just continue
            if(record.saveOperationInProgress)
                continue;

            if(!this.readyForSave(record))
                continue;

            if(record.isNew)
            {
                insertRecords[insertRecords.length] = record;
                insertRowsData[insertRowsData.length] = this.getRowData(record);
            }
            else
            {
                updateRecords[updateRecords.length] = record;
                updateRowsData[updateRowsData.length] = this.getRowData(record);
            }

            record.saveOperationInProgress = true;
        }

        //kick off the insert and update queries
        if(insertRowsData.length > 0)
            LABKEY.Query.insertRows({
                schemaName: this.schemaName,
                queryName: this.queryName, 
                containerPath: this.containerPath,
                successCallback: this.getSuccessHandler(insertRowsData, insertRecords),
                errorCallback: this.getErrorHandler(insertRowsData, insertRecords),
                rowDataArray: insertRowsData,
                action: "insertRows" //HACK: needed for a bug in Query.js
            });

        if(updateRowsData.length > 0)
            LABKEY.Query.updateRows({
                schemaName: this.schemaName,
                queryName: this.queryName,
                containerPath: this.containerPath,
                successCallback: this.getSuccessHandler(updateRowsData, updateRecords),
                errorCallback: this.getErrorHandler(updateRowsData, updateRecords),
                rowDataArray: updateRowsData,
                action: "updateRows"//HACK: needed for a bug in Query.js
            });
    },

    /**
     * Returns true if the given record is currently being updated on the server, false if not.
     * @param {Ext.data.Record} record The record.
     * @returns {boolea} true if the record is currently being updated, false if not.
     * @name isUpdateInProgress
     * @function
     * @memberOf LABKEY.ext.Store
     */
    isUpdateInProgress : function(record) {
        return record.saveOperationInProgress;
    },

    /**
     * Returns a LabKey.ext.Store filled with the lookup values for a given
     * column name if that column exists, and if it is a lookup column (i.e.,
     * lookup meta-data was supplied by the server).
     * @param columnName The column name
     * @param includeNullRecord Pass true to include a null record at the top
     * so that the user can set the column value back to null. Set the
     * lookupNullCaption property in this Store's config to override the default
     * caption of "[none]".
     */
    getLookupStore : function(columnName, includeNullRecord)
    {
        if(!this.lookupStores)
            this.lookupStores = {};

        var store = this.lookupStores[columnName];
        if(!store)
        {
            //find the column metadata
            var fieldMeta = this.findFieldMeta(columnName);
            if(!fieldMeta)
                return null;

            //create the lookup store and kick off a load
            var config = {
                schemaName: fieldMeta.lookup.schema,
                queryName: fieldMeta.lookup.table,
                containerPath: this.containerPath
            };
            if(includeNullRecord)
                config.nullRecord = {
                    displayColumn: fieldMeta.lookup.displayColumn,
                    nullCaption: this.lookupNullCaption || "[none]"
                }

            store = new LABKEY.ext.Store(config);
            this.lookupStores[columnName] = store;
            store.load();
        }
        return store;
    },

    /*-- Private Methods --*/

    findFieldMeta : function(columnName)
    {
        var fields = this.reader.meta.fields;
        for(var idx = 0; idx < fields.length; ++idx)
        {
            if(fields[idx].name == columnName)
                return fields[idx]
        }
        return null;
    },

    onBeforeLoad: function(proxy, options) {
        //the selectRows.api can't handle the 'sort' and 'dir' params
        //sent by Ext, so translate them into the expected form
        if(options.sort)
            options['query.sort'] = "DESC" == options.dir 
                    ? "-" + options.sort
                    : options.sort;

        //delete options.dir;
        //delete options.sort;
    },

    onLoad : function(store, records, options) {

        //remeber the name of the id column
        this.idName = this.reader.meta.id;

        if(this.nullRecord)
        {
            //create an extra record with a blank id column
            //and the null caption in the display column
            var data = {};
            data[this.reader.meta.id] = "";
            data[this.nullRecord.displayColumn] = this.nullCaption || "[none]";

            var recordConstructor = Ext.data.Record.create(this.reader.meta.fields);
            var record = new recordConstructor(data, -1);
            this.insert(0, record);
        }
    },

    onLoadException : function(proxy, options, response, error)
    {
        var loadError = {message: error};

        var ctype = response.getResponseHeader["Content-Type"];
        if(ctype.indexOf("application/json") >= 0)
        {
            var errorJson = Ext.util.JSON.decode(response.responseText);
            if(errorJson && errorJson.exception)
                loadError.message = errorJson.exception;
        }

        this.loadError = loadError;
    },

    getSuccessHandler : function(rowsData, records) {
        //save this in a closure for use by the returned function
        //CONSIDER: add scope property to insertRows and updateRows APIs, like Ext does
        var store = this;

        return function(results) {
            var idx = 0;
            var idCol = store.reader.jsonData.metaData.id;
            if(results.rows)
            {
                //because the row key might change as a result of an insert (auto-generated key)
                //or update (study dataset case), we need to create a new record based on the
                //response record, insert it where the old record was, and remove the old record
                //CONSIDER: can we dive into the private data of the store to fixup the key map?
                var resultRow;
                var sentRecord;
                var recordCreator = Ext.data.Record.create(store.reader.meta.fields);
                for(idx = 0; idx < results.rows.length; ++idx)
                {
                    resultRow = results.rows[idx];
                    sentRecord = records[idx]; //result rows come back in the same order as sent
                    sentRecord.commit();

                    var newRecord = new recordCreator(resultRow, resultRow[idCol]);
                    store.insert(store.indexOf(sentRecord), newRecord);
                    store.remove(sentRecord);
                }
            }
        }
    },

    getErrorHandler : function(rowsData, records) {
        return function(response) {
            for(var idx=0; idx < records.length; ++idx)
                delete records[idx].saveOperationInProgress;

            var msg = "Unable to save the data due to the following error:<br/>";
            msg += response.exception || "Unknown Error";
            alert(msg);
        }
    },

    getDeleteSuccessHandler : function() {
        var store = this;
        return function(results) {
            store.reload();
        }
    },

    getRowData : function(record) {
        //need to convert empty strings to null before posting
        //Ext components will typically set a cleared field to
        //empty string, but this messes-up Lists in LabKey 8.2 and earlier
        var data = {};
        Ext.apply(data, record.data);
        for(var field in data)
        {
            if(null != data[field] && data[field].toString().length == 0)
                data[field] = null;
        }
        return data;
    },

    readyForSave : function(record) {
        //this is kind of hacky, but it seems that checking
        //for required columns is the job of the store, not
        //the bound control. Since the required prop is in the
        //column model, go get that from the reader
        var colmodel = this.reader.jsonData.columnModel;
        if(!colmodel)
            return true;

        var col;
        for(var idx = 0; idx < colmodel.length; ++idx)
        {
            col = colmodel[idx];

            if(col.dataIndex != this.reader.meta.id && col.required && !record.data[col.dataIndex])
                return false;
        }

        return true;
    }

});
