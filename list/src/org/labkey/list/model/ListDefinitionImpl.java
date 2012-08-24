/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.list.model;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.Pump;
import org.labkey.api.etl.WrapperDataIterator;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.VirtualFile;
import org.labkey.list.client.ListEditorService;
import org.labkey.list.view.ListController;
import org.labkey.list.view.ListImportHelper;
import org.labkey.list.view.ListItemAttachmentParent;
import org.springframework.web.servlet.mvc.Controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.GUID.makeGUID;

public class ListDefinitionImpl implements ListDefinition
{
    protected static final String NAMESPACE_PREFIX = "List";

    static public ListDefinitionImpl of(ListDef def)
    {
        if (def == null)
            return null;
        return new ListDefinitionImpl(def);
    }

    boolean _new;
    ListDef _defOld;
    ListDef _def;
    Domain _domain;

    public ListDefinitionImpl(ListDef def)
    {
        _def = def;
    }

    public ListDefinitionImpl(Container container, String name)
    {
        _new = true;
        _def = new ListDef();
        _def.setContainer(container.getId());
        _def.setName(name);
        String guid = makeGUID();
        _def.setEntityId(guid);
        Lsid lsid = ListDomainType.generateDomainURI(name, container, guid);
        _domain = PropertyService.get().createDomain(container, lsid.toString(), name);
    }

    public int getListId()
    {
        return _def.getRowId();
    }

    public String getEntityId()
    {
        return _def.getEntityId();
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_def.getContainerId());
    }

    public Domain getDomain()
    {
        if (_domain == null)
        {
            _domain = PropertyService.get().getDomain(_def.getDomainId());
        }
        return _domain;
    }

    public String getName()
    {
        return _def.getName();
    }

    public String getKeyName()
    {
        return _def.getKeyName();
    }

    public void setKeyName(String name)
    {
        if (_def.getTitleColumn() != null && _def.getTitleColumn().equals(getKeyName()))
        {
            edit().setTitleColumn(name);
        }
        edit().setKeyName(name);
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public KeyType getKeyType()
    {
        return KeyType.valueOf(_def.getKeyType());
    }

    public void setKeyType(KeyType type)
    {
        edit().setKeyType(type.toString());
    }

    public DiscussionSetting getDiscussionSetting()
    {
        return _def.getDiscussionSettingEnum();
    }

    public void setDiscussionSetting(DiscussionSetting discussionSetting)
    {
        edit().setDiscussionSettingEnum(discussionSetting);
    }

    public boolean getAllowDelete()
    {
        return _def.getAllowDelete();
    }

    public void setAllowDelete(boolean allowDelete)
    {
        edit().setAllowDelete(allowDelete);
    }

    public boolean getAllowUpload()
    {
        return _def.getAllowUpload();
    }

    public void setAllowUpload(boolean allowUpload)
    {
        edit().setAllowUpload(allowUpload);
    }

    public boolean getAllowExport()
    {
        return _def.getAllowExport();
    }

    public void setAllowExport(boolean allowExport)
    {
        edit().setAllowExport(allowExport);
    }

    @Override
    public boolean getEntireListIndex()
    {
        return _def.getEntireListIndex();
    }

    @Override
    public void setEntireListIndex(boolean eachItemIndex)
    {
        edit().setEntireListIndex(eachItemIndex);
    }

    @Override
    public IndexSetting getEntireListIndexSetting()
    {
        return _def.getEntireListIndexSettingEnum();
    }

    @Override
    public void setEntireListIndexSetting(IndexSetting setting)
    {
        edit().setEntireListIndexSettingEnum(setting);
    }

    @Override
    public TitleSetting getEntireListTitleSetting()
    {
        return _def.getEntireListTitleSettingEnum();
    }

    @Override
    public void setEntireListTitleSetting(TitleSetting setting)
    {
        edit().setEntireListTitleSettingEnum(setting);
    }

    @Override
    public String getEntireListTitleTemplate()
    {
        return _def.getEntireListTitleTemplate();
    }

    @Override
    public void setEntireListTitleTemplate(String template)
    {
        edit().setEntireListTitleTemplate(template);
    }

    @Override
    public BodySetting getEntireListBodySetting()
    {
        return _def.getEntireListBodySettingEnum();
    }

    @Override
    public void setEntireListBodySetting(BodySetting setting)
    {
        edit().setEntireListBodySettingEnum(setting);
    }

    @Override
    public String getEntireListBodyTemplate()
    {
        return _def.getEntireListBodyTemplate();
    }

    @Override
    public void setEntireListBodyTemplate(String template)
    {
        edit().setEntireListBodyTemplate(template);
    }

    @Override
    public boolean getEachItemIndex()
    {
        return _def.getEachItemIndex();
    }

    @Override
    public void setEachItemIndex(boolean eachItemIndex)
    {
        edit().setEachItemIndex(eachItemIndex);
    }

    @Override
    public TitleSetting getEachItemTitleSetting()
    {
        return _def.getEachItemTitleSettingEnum();
    }

    @Override
    public void setEachItemTitleSetting(TitleSetting setting)
    {
        edit().setEachItemTitleSettingEnum(setting);
    }

    @Override
    public String getEachItemTitleTemplate()
    {
        return _def.getEachItemTitleTemplate();
    }

    @Override
    public void setEachItemTitleTemplate(String template)
    {
        edit().setEachItemTitleTemplate(template);
    }

    @Override
    public BodySetting getEachItemBodySetting()
    {
        return _def.getEachItemBodySettingEnum();
    }

    @Override
    public void setEachItemBodySetting(BodySetting setting)
    {
        edit().setEachItemBodySettingEnum(setting);
    }

    @Override
    public String getEachItemBodyTemplate()
    {
        return _def.getEachItemBodyTemplate();
    }

    @Override
    public void setEachItemBodyTemplate(String template)
    {
        edit().setEachItemBodyTemplate(template);
    }

    public void save(User user) throws Exception
    {
        try
        {
            ExperimentService.get().ensureTransaction();

            if (_new)
            {
                _domain.save(user);
                _def.setDomainId(_domain.getTypeId());
                _def = ListManager.get().insert(user, _def);
                _new = false;
            }
            else
            {
                _def = ListManager.get().update(user, _def);
                _defOld = null;
                addAuditEvent(user, String.format("The definition of the list %s was modified", _def.getName()));
            }

            ExperimentService.get().commitTransaction();
        }
        catch (SQLException e) //issue 12162
        {
            processSqlException(e);
            throw e;
        }
        catch (RuntimeSQLException e)
        {
            processSqlException(e.getSQLException());
            throw e;
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
        ListManager.get().indexList(_def);
    }

    private void processSqlException(SQLException e) throws Exception
    {
        if (SqlDialect.isConstraintException(e))
        {
            //verify this is actually due to a duplicate name
            for (ListDef l : ListManager.get().getLists(getContainer()))
            {
                if (l.getName().equals(_def.getName()))
                {
                    throw new ListEditorService.ListImportException("The name '" + _def.getName() + "' is already in use.");
                }
            }

            throw Table.OptimisticConflictException.create(Table.ERROR_ROWVERSION);
        }
    }

    public ListItem createListItem()
    {
        return new ListItemImpl(this);
    }

    public ListItem getListItem(Object key)
    {
        // Convert key value to the proper type, since PostgreSQL 8.3 requires that key parameter types match their column types.
        Object typedKey = getKeyType().convertKey(key);

        return getListItem(new SimpleFilter("Key", typedKey));
    }

    public ListItem getListItemForEntityId(String entityId)
    {
        return getListItem(new SimpleFilter("EntityId", entityId));
    }

    private ListItem getListItem(SimpleFilter filter)
    {
        filter.addCondition("ListId", getListId());
        ListItm itm = new TableSelector(getIndexTable(), filter, null).getObject(ListItm.class);

        if (itm == null)
        {
            return null;
        }

        return new ListItemImpl(this, itm);
    }

    public void deleteListItems(User user, Collection keys) throws SQLException
    {
        try
        {
            ExperimentService.get().ensureTransaction();
            for (Object key : keys)
            {
                ListItem item = getListItem(key);
                if (item != null)
                {
                    item.delete(user, getContainer());
                }
            }
            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
    }

    public void delete(User user) throws SQLException, DomainNotFoundException
    {
        try
        {
            ExperimentService.get().ensureTransaction();

            //new approach
            SimpleFilter lstItemFilter = new SimpleFilter("ListId", getListId());

            //we make the assumption that big lists will have relatively few discussions and attachments
            //we find and delete these in batches of 1000, then delete the rows below
            int offset = 0;
            int step = 1000;

            while(1==1)
            {
                final List<String> entityIds = new ArrayList<String>();
                final List<AttachmentParent> attachmentParents = new ArrayList<AttachmentParent>();

                TableSelector ts = new TableSelector(getIndexTable(), Table.ALL_COLUMNS, lstItemFilter, null);
                ts.setRowCount(step);
                ts.setOffset(offset);

                ts.forEach(new Selector.ForEachBlock<ResultSet>() {
                    @Override
                    public void exec(ResultSet rs) throws SQLException
                    {
                        entityIds.add(rs.getString("EntityId"));
                        attachmentParents.add(new ListItemAttachmentParent(rs.getString("EntityId"), getContainer()));
                    }
                });

                String[] distinctIDs = entityIds.toArray(new String[entityIds.size()]);
                if (distinctIDs.length > 0)
                    DiscussionService.get().deleteDiscussions(getContainer(), user, distinctIDs);

                AttachmentParent[] distinctAttachments = attachmentParents.toArray(new AttachmentParent[attachmentParents.size()]);
                if (distinctAttachments.length > 0)
                    AttachmentService.get().deleteAttachments(distinctAttachments);

                if (entityIds.size() < step)
                {
                    break;
                }

                offset += step;
            }

            //delete all list items
            ListItm[] itms = new TableSelector(getIndexTable(), Table.ALL_COLUMNS, lstItemFilter, null).getArray(ListItm.class);
            Table.delete(getIndexTable(), lstItemFilter);

            Set<String> ids = new HashSet<String>();

            for (ListItm itm : itms)
            {
                if (itm.getObjectId() != null)
                {
                    OntologyObject object = OntologyManager.getOntologyObject(itm.getObjectId());
                    if (object != null)
                    {
                        ids.add(object.getObjectURI());
                    }
                }
            }
            OntologyManager.deleteOntologyObjects(getContainer(), ids.toArray(new String[ids.size()]));

            // Unindex all item docs and the entire list doc
            ListManager.get().deleteIndexedList(this);

            //then delete the list itself
            Table.delete(ListManager.get().getTinfoList(), getListId());
            Domain domain = getDomain();
            domain.delete(user);

            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
    }


    @Override
    public int insertListItems(User user, DataLoader loader, @NotNull BatchValidationException errors, @Nullable VirtualFile attachmentDir, @Nullable ListImportProgress progress) throws IOException
    {
        return insertListItemsETL(user, loader, errors, attachmentDir, progress);
    }


    public int insertListItemsOld(User user, DataLoader loader, @NotNull BatchValidationException errors, @Nullable VirtualFile attachmentDir, @Nullable ListImportProgress progress) throws IOException
    {
        Set<String> mvIndicatorColumnNames = new CaseInsensitiveHashSet();

        for (DomainProperty property : getDomain().getProperties())
            if (property.isMvEnabled())
                mvIndicatorColumnNames.add(property.getName() + MvColumn.MV_INDICATOR_SUFFIX);

        Map<String, DomainProperty> propertiesByName = getDomain().createImportMap(true);
        Map<String, DomainProperty> foundProperties = new CaseInsensitiveHashMap<DomainProperty>();
        ColumnDescriptor cdKey = null;
        DomainProperty dpKey = null;

        Object errorValue = new Object(){@Override public String toString(){return "~ERROR VALUE~";}};

        // We know the types, so don't infer them.  Instead, read the header line and create a ColumnDescriptor array
        // that tells the loader all the types.
        String[][] firstLine = loader.getFirstNLines(1);

        if (firstLine.length == 0)
        {
            errors.addRowError(new ValidationException("The header line cannot be blank"));
            return 0;
        }

        ArrayList<ColumnDescriptor> columnList = new ArrayList<ColumnDescriptor>(firstLine.length);

        for (String columnHeader : firstLine[0])
        {
            columnHeader = StringUtils.trimToEmpty(columnHeader);
            ColumnDescriptor descriptor;

            if (getKeyName().equalsIgnoreCase(columnHeader))
            {
                descriptor = new ColumnDescriptor(columnHeader, getKeyType().getPropertyType().getJavaType());
            }
            else
            {
                DomainProperty prop = propertiesByName.get(columnHeader);

                if (null == prop)
                    descriptor = new ColumnDescriptor(columnHeader, String.class); // We're going to ignore this column... just claim it's a string
                else
                    descriptor = new ColumnDescriptor(columnHeader, prop.getPropertyDescriptor().getPropertyType().getJavaType());
            }

            columnList.add(descriptor);
        }

        ColumnDescriptor[] columns = columnList.toArray((new ColumnDescriptor[columnList.size()]));
        loader.setColumns(columns);

        int colIdx = 0; //used to report the column number to the user for errors, 1-based
        for (ColumnDescriptor cd : columns)
        {
            colIdx++;
            String columnName = cd.name;
            DomainProperty property = propertiesByName.get(columnName);
            cd.errorValues = errorValue;

            boolean isKeyField = getKeyName().equalsIgnoreCase(cd.name) || null != property && getKeyName().equalsIgnoreCase(property.getName());

            if (property == null && !isKeyField)
            {
                errors.addRowError(new ValidationException(!"".equals(columnName) ? "The field '" + columnName + "' could not be matched to a field in this list." : "The import cannot have blank column headers (Column " + colIdx + ")"));
                continue;
            }

            if (isKeyField)
            {
                if (cdKey != null)
                {
                    errors.addRowError(new ValidationException("The field '" + getKeyName() + "' appears more than once."));
                }
                else
                {
                    cdKey = cd;
                    dpKey = property;
                }
            }
            else
            {
                // Special handling for MV indicators -- they don't have real property descriptors.
                if (mvIndicatorColumnNames.contains(columnName))
                {
                    cd.name = property.getPropertyURI();
                    cd.clazz = String.class;
                    cd.setMvIndicator(getContainer());
                }
                else
                {
                    cd.clazz = property.getPropertyDescriptor().getPropertyType().getJavaType();

                    if (foundProperties.containsKey(columnName))
                    {
                        errors.addRowError(new ValidationException("The field '" + property.getName() + "' appears more than once."));
                    }
                    if (foundProperties.containsValue(property) && !property.isMvEnabled())
                    {
                        errors.addRowError(new ValidationException("The fields '" + property.getName() + "' and '" + property.getPropertyDescriptor().getNonBlankCaption() + "' refer to the same property."));
                    }
                    foundProperties.put(columnName, property);
                    cd.name = property.getPropertyURI();
                    if (property.isMvEnabled())
                    {
                        cd.setMvEnabled(getContainer());
                    }
                }
            }
        }

        if (cdKey == null && getKeyType() != ListDefinition.KeyType.AutoIncrementInteger)
        {
            errors.addRowError(new ValidationException("There must be a field with the name '" + getKeyName() + "'"));
        }

        if (errors.hasErrors())
            return 0;

        List<Map<String, Object>> rows = loader.load();

        if (null != progress)
            progress.setTotalRows(rows.size());

        Set<Object> keyValues = new HashSet<Object>();
        Set<String> missingValues = new HashSet<String>();
        Set<String> wrongTypes = new HashSet<String>();
        Set<String> noUpload = new HashSet<String>();

		DomainProperty[] domainProperties = getDomain().getProperties();

        //this is the user-friendly row#, 1-based.
        // we could potentially start on 2 which would match excel's row# (assuming we have a header), but i dont know if that is a safe assumption
        int idx = 1;

        for (Map<String, Object> row : rows)
        {
            row = new CaseInsensitiveHashMap<Object>(row);
            for (DomainProperty domainProperty : domainProperties)
            {
                if (dpKey == domainProperty)
                    continue;
                Object o = row.get(domainProperty.getPropertyURI());
                boolean valueMissing;
                if (o == null)
                {
                    valueMissing = true;
                }
                else if (o instanceof MvFieldWrapper)
                {
                    MvFieldWrapper mvWrapper = (MvFieldWrapper)o;
                    if (mvWrapper.isEmpty())
                        valueMissing = true;
                    else
                    {
                        valueMissing = false;
                        if (!MvUtil.isValidMvIndicator(mvWrapper.getMvIndicator(), getContainer()))
                        {
                            String columnName = domainProperty.getName() + MvColumn.MV_INDICATOR_SUFFIX;
                            wrongTypes.add(columnName);
                            errors.addRowError(new ValidationException("Row " + idx + ": " + columnName + " must be a valid MV indicator."));
                        }
                    }
                }
                else
                {
                    valueMissing = o.toString().length() == 0;
                }
                
                if (domainProperty.isRequired() && valueMissing && !missingValues.contains(domainProperty.getName()))
                {
                    missingValues.add(domainProperty.getName());
                    errors.addRowError(new ValidationException("Row " + idx + ": The field \"" + domainProperty.getName() + "\" is required."));
                }
                else if (domainProperty.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT && null == attachmentDir && !valueMissing && !noUpload.contains(domainProperty.getName()))
                {
                    noUpload.add(domainProperty.getName());
                    errors.addRowError(new ValidationException("Row " + idx + ": " + "Can't upload to field " + domainProperty.getName() + " with type " + domainProperty.getType().getLabel() + "."));
                }
                else if (!valueMissing && o == errorValue && !wrongTypes.contains(domainProperty.getName()))
                {
                    wrongTypes.add(domainProperty.getName());
                    errors.addRowError(new ValidationException("Row " + idx + ": The field \"" + domainProperty.getName() + "\" must be of type " + domainProperty.getType().getLabel() + "."));
                }
            }

            if (cdKey != null)
            {
                Object key = row.get(cdKey.name);
                if (null == key)
                {
                    errors.addRowError(new ValidationException("Row " + idx + ": " + "Blank values are not allowed in field " + cdKey.name));
                    return 0;
                }
                else if (!getKeyType().isValidKey(key))
                {
                    // Ideally, we'd display the value we failed to convert and/or the row... but key.toString() is currently "~ERROR VALUE~".  See #10475.
                    // TODO: Fix this
                    errors.addRowError(new ValidationException("Row " + idx + ": " + "Could not convert values in key field \"" + cdKey.name + "\" to type " + getKeyType().getLabel()));
                    return 0;
                }
                else if (!keyValues.add(key))
                {
                    errors.addRowError(new ValidationException("Row " + idx + ": " + "The key field \"" + cdKey.name + "\" cannot have duplicate values.  The duplicate is: \"" + row.get(cdKey.name) + "\""));
                    return 0;
                }
            }

            idx++;
        }

        if (errors.hasErrors())
            return 0;

        Domain domain = getDomain();
        Map<String,DomainProperty> properties = foundProperties;
        try
        {
            ExperimentService.get().ensureTransaction();

            // There's a disconnect here between the PropertyService api and OntologyManager...
            ArrayList<DomainProperty> used = new ArrayList<DomainProperty>(properties.size());
            for (DomainProperty dp : domain.getProperties())
                if (properties.containsKey(dp.getPropertyURI()))
                    used.add(dp);
            ListImportHelper helper = new ListImportHelper(user, this, used.toArray(new DomainProperty[used.size()]), cdKey, attachmentDir, progress);

            // our map of properties can have duplicates due to MV indicator columns (different columns, same URI)
            Set<PropertyDescriptor> propSet = new HashSet<PropertyDescriptor>();
            for (DomainProperty domainProperty : properties.values())
            {
                propSet.add(domainProperty.getPropertyDescriptor());
            }

            PropertyDescriptor[] pds = propSet.toArray(new PropertyDescriptor[propSet.size()]);
            List<String> inserted = OntologyManager.insertTabDelimited(getContainer(), user, null, helper, pds, rows, true);
            addAuditEvent(user, "Bulk inserted " + inserted.size() + " rows to list.");

            ExperimentService.get().commitTransaction();

            return inserted.size();
        }
        catch (ValidationException ve)
        {
            errors.addRowError(ve);
        }
        catch (SQLException se)
        {
            errors.addRowError(new ValidationException(se.getMessage()));
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
        return 0;
    }


    public int insertListItemsETL(final User user, DataLoader loader, final BatchValidationException errors, @Nullable final VirtualFile attachmentDir, @Nullable ListImportProgress progress) throws IOException
    {
        ListQueryUpdateService lqus = (ListQueryUpdateService)getTable(user).getUpdateService();
        DataIteratorBuilder dib = lqus.createImportETL(user, getContainer(), loader, errors, true);

        try
        {
            ExperimentService.get().ensureTransaction();

            DataIterator insertIt = dib.getDataIterator(errors);
            DataIterator attach = _AttachmentImportDataIterator_wrap(insertIt, errors, user, attachmentDir);
            Pump p = new Pump(attach, errors);
            p.run();
            int inserted = p.getRowCount();

            if (!errors.hasErrors())
            {
                if (inserted > 0)
                    addAuditEvent(user, "Bulk inserted " + inserted + " rows to list.");
                ExperimentService.get().commitTransaction();
                return inserted;
            }
            return 0;
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
    }


    private static class _AttachmentUploadHelper
    {
        _AttachmentUploadHelper(int i, DomainProperty dp)
        {
            index=i;
            domainProperty = dp;
        }
        final int index;
        final DomainProperty domainProperty;
        final FileNameUniquifier uniquifier = new FileNameUniquifier();
    }


    private DataIterator _AttachmentImportDataIterator_wrap(DataIterator it, BatchValidationException errors, User user, VirtualFile attachmentDir)
    {
        // find attachment columns
        int entityIdIndex = 0;
        final ArrayList<_AttachmentUploadHelper> attachmentColumns = new ArrayList<_AttachmentUploadHelper>();
        for (int c=1 ; c<=it.getColumnCount() ; c++)
        {
            ColumnInfo col = it.getColumnInfo(c);
            if (StringUtils.equalsIgnoreCase("entityid",col.getName()))
                entityIdIndex = c;
            // Don't seem to have attachment information in the ColumnInfo 8(, so we need to lookup the DomainProperty
            // UNDONE: PropertyURI is not propagated, need to use name
            DomainProperty domainProperty = getDomain().getPropertyByName(col.getName());
            if (null==domainProperty || domainProperty.getPropertyDescriptor().getPropertyType() != PropertyType.ATTACHMENT)
                continue;
            attachmentColumns.add(new _AttachmentUploadHelper(c,domainProperty));
        }
        if (!attachmentColumns.isEmpty() && 0 != entityIdIndex)
            return new _AttachmentImportDataIterator(it, errors, user, attachmentDir, entityIdIndex, attachmentColumns);
        else
            return it;
    }


    class _AttachmentImportDataIterator extends WrapperDataIterator
    {
        final VirtualFile attachmentDir;
        final BatchValidationException errors;
        final int entityIdIndex;
        final ArrayList<_AttachmentUploadHelper> attachmentColumns;
        final User user;

        _AttachmentImportDataIterator(DataIterator insertIt, BatchValidationException errors,
                User user,
                VirtualFile attachmentDir,
                int entityIdIndex,
                ArrayList<_AttachmentUploadHelper> attachmentColumns)
        {
            super(insertIt);
            this.attachmentDir = attachmentDir;
            this.errors = errors;
            this.entityIdIndex = entityIdIndex;
            this.attachmentColumns = attachmentColumns;
            this.user = user;
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            try
            {
                boolean ret = super.next();
                if (!ret)
                    return false;
                ArrayList<AttachmentFile> attachmentFiles = null;
                for (_AttachmentUploadHelper p : attachmentColumns)
                {
                    Object attachmentValue = get(p.index);
                    String filename = null;
                    if (attachmentValue instanceof String)
                        filename = (String)attachmentValue;
                    else if (attachmentValue instanceof File)
                        filename = ((File)attachmentValue).getName();
                    if (null == filename)
                        continue;
                    if (null == attachmentDir)
                    {
                        errors.addRowError(new ValidationException("Row " + get(0) + ": " + "Can't upload to field " + p.domainProperty.getName() + " with type " + p.domainProperty.getType().getLabel() + "."));
                        return false;
                    }
                    InputStream aIS = attachmentDir.getDir(p.domainProperty.getName()).getInputStream(p.uniquifier.uniquify(filename));
                    AttachmentFile attachmentFile = new InputStreamAttachmentFile(aIS, filename);
                    attachmentFile.setFilename(filename);
                    if (null == attachmentFiles)
                        attachmentFiles = new ArrayList<AttachmentFile>();
                    attachmentFiles.add(attachmentFile);
                }
                if (null != attachmentFiles && !attachmentFiles.isEmpty())
                {
                    String entityId = String.valueOf(get(entityIdIndex));
                    AttachmentService.get().addAttachments(new ListItemAttachmentParent(entityId, getContainer()), attachmentFiles, user);
                }
                return ret;
            }
            catch (Exception x)
            {
                throw UnexpectedException.wrap(x);
            }
        }
    }


    private void addAuditEvent(User user, String comment)
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setComment(comment);

            Container c = getContainer();
            event.setContainerId(c.getId());
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
            event.setKey1(getDomain().getTypeURI());

            event.setEventType(ListManager.LIST_AUDIT_EVENT);
            event.setIntKey1(getListId());
            event.setKey3(getName());

            AuditLogService.get().addEvent(event);
        }
    }


    public int getRowCount()
    {
        return 0;
    }

    public String getDescription()
    {
        return _def.getDescription();
    }

    public String getTitleColumn()
    {
        return _def.getTitleColumn();
    }

    public void setTitleColumn(String titleColumn)
    {
        edit().setTitleColumn(titleColumn);
    }

    @Override
    public Date getModified()
    {
        return _def.getModified();
    }

    @Override
    public void setModified(Date modified)
    {
        edit().setModified(modified);
    }

    @Override
    public Date getLastIndexed()
    {
        return _def.getLastIndexed();
    }

    @Override
    public void setLastIndexed(Date modified)
    {
        edit().setLastIndexed(modified);
    }

    /** NOTE consider using ListSchema.getTable(), unless you have a good reason */
    public TableInfo getTable(User user)
    {
        ListTable ret = new ListTable(user, this);
        ret.afterConstruct();
        return ret;
    }

    public ActionURL urlShowDefinition()
    {
        return urlFor(ListController.EditListDefinitionAction.class);
    }

    public ActionURL urlShowData()
    {
        return urlFor(ListController.GridAction.class);
    }

    public ActionURL urlUpdate(@Nullable Object pk, @Nullable URLHelper returnUrl)
    {
        ActionURL url = urlFor(ListController.UpdateAction.class);

        // Can be null if caller will be filling in pk (e.g., grid edit column)
        if (null != pk)
            url.addParameter("pk", pk.toString());

        if (returnUrl != null)
            url.addParameter(ActionURL.Param.returnUrl, returnUrl.getLocalURIString());

        return url;
    }

    public ActionURL urlDetails(@Nullable Object pk)
    {
        ActionURL url = urlFor(ListController.DetailsAction.class);
        // Can be null if caller will be filling in pk (e.g., grid edit column)

        if (null != pk)
            url.addParameter("pk", pk.toString());

        return url;
    }

    public ActionURL urlShowHistory()
    {
        return urlFor(ListController.HistoryAction.class);
    }

    public ActionURL urlFor(Class<? extends Controller> actionClass)
    {
        ActionURL ret = new ActionURL(actionClass, getContainer());
        ret.addParameter("listId", Integer.toString(getListId()));
        return ret;
    }

    private ListDef edit()
    {
        if (_new)
        {
            return _def;
        }
        if (_defOld == null)
        {
            _defOld = _def;
            _def = _defOld.clone();
        }
        return _def;

    }

    public TableInfo getIndexTable()
    {
        switch (getKeyType())
        {
            case Integer:
            case AutoIncrementInteger:
                return OntologyManager.getTinfoIndexInteger();
            case Varchar:
                return OntologyManager.getTinfoIndexVarchar();
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public String toString()
    {
        return getName() + ", id: " + getListId();
    }

    public int compareTo(ListDefinition l)
    {
        return getName().compareToIgnoreCase(l.getName());
    }
}
