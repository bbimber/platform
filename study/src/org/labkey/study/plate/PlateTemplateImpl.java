package org.labkey.study.plate;

import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.Position;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.data.Container;
import org.labkey.api.util.GUID;
import org.labkey.api.attachments.Attachment;

import java.util.*;
import java.io.File;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 1:13:45 PM
 */
public class PlateTemplateImpl extends PropertySetImpl implements PlateTemplate
{
    private String _name;
    private Integer _rowId;
    private int _rows;
    private int _columns;
    private int _createdBy;
    private long _created;
    private String _dataFileId;
    private String _type;

    private Map<WellGroup.Type, Map<String, WellGroupTemplateImpl>> _groups;

    public PlateTemplateImpl()
    {
        // no-param constructor for reflection
    }

    public PlateTemplateImpl(Container container, String name, String templateType)
    {
        this(container, name, templateType, 8, 12);
    }

    public PlateTemplateImpl(Container container, String name, String type, int rowCount, int colCount)
    {
        super(container);
        _name = name;
        _type = type;
        _rows = rowCount;
        _columns = colCount;
        _container = container;
        _dataFileId = GUID.makeGUID();
    }

    public WellGroupTemplate addWellGroup(String name, WellGroup.Type type, Position upperLeft, Position lowerRight)
    {
        int regionWidth = lowerRight.getColumn() - upperLeft.getColumn() + 1;
        int regionHeight = lowerRight.getRow() - upperLeft.getRow();
        List<Position> allPositions = new ArrayList<Position>(regionWidth * regionHeight);
        for (int col = upperLeft.getColumn(); col <= lowerRight.getColumn(); col++)
        {
            for (int row = upperLeft.getRow(); row <= lowerRight.getRow(); row++)
                allPositions.add(new PositionImpl(_container, row, col));
        }
        return addWellGroup(name, type, allPositions);
    }

    public WellGroupTemplate addWellGroup(String name, WellGroup.Type type, List<Position> positions)
    {
        return storeWellGroup(createWellGroup(name, type, positions));
    }

    protected WellGroupTemplateImpl createWellGroup(String name, WellGroup.Type type, List<Position> positions)
    {
        return new WellGroupTemplateImpl(this, name, type, positions);
    }

    public WellGroupTemplate addWellGroup(WellGroupTemplateImpl template)
    {
        return storeWellGroup(template);
    }

    protected WellGroupTemplate storeWellGroup(WellGroupTemplateImpl template)
    {
        if (_groups == null)
            _groups = new HashMap<WellGroup.Type, Map<String, WellGroupTemplateImpl>>();
        Map<String, WellGroupTemplateImpl> templatesByType = _groups.get(template.getType());
        if (templatesByType == null)
        {
            templatesByType = new LinkedHashMap<String, WellGroupTemplateImpl>();
            _groups.put(template.getType(), templatesByType);
        }
        templatesByType.put(template.getName(), template);
        assert (wellGroupsInOrder(templatesByType)) : "WellGroupTemplates are out of order.";
        return template;
    }

    private boolean wellGroupsInOrder(Map<String, WellGroupTemplateImpl> templates)
    {
        int row = -1;
        int col = -1;
        for (String name : templates.keySet())
        {
            WellGroupTemplateImpl template = templates.get(name);
            Position topLeft = template.getTopLeft();
            if (col > topLeft.getColumn())
                return false;
            if (col == topLeft.getColumn() && row > topLeft.getRow())
                return false;
            row = topLeft.getRow();
            col = topLeft.getColumn();
        }
        return true;
    }


    public List<? extends WellGroupTemplateImpl> getWellGroups(Position position)
    {
        List<WellGroupTemplateImpl> groups = new ArrayList<WellGroupTemplateImpl>();
        for (WellGroupTemplateImpl template : getWellGroupTemplates())
        {
            if (template.contains(position))
                groups.add(template);
        }
        return groups;
    }

    public List<? extends WellGroupTemplateImpl> getWellGroups()
    {
        return getWellGroupTemplates();
    }

    public WellGroupTemplateImpl getWellGroupTemplate(WellGroup.Type type, String name)
    {
        Map<String, WellGroupTemplateImpl> typedGroups = _groups.get(type);
        if (typedGroups == null)
            return null;
        return typedGroups.get(name);
    }

    public List<WellGroupTemplateImpl> getWellGroupTemplates()
    {
        List<WellGroupTemplateImpl> allGroupTemplates = new ArrayList<WellGroupTemplateImpl>();
        if (_groups != null)
        {
            for (Map<String, WellGroupTemplateImpl> typedGroupTemplates : _groups.values())
                allGroupTemplates.addAll(typedGroupTemplates.values());
        }
        return allGroupTemplates;
    }

    public int getColumns()
    {
        return _columns;
    }

    public String getName()
    {
        return _name;
    }

    public int getRows()
    {
        return _rows;
    }


    public PositionImpl getPosition(int row, int col)
    {
        return new PositionImpl(_container, row, col);
    }


    public boolean isTemplate()
    {
        return true;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public void setColumns(int columns)
    {
        _columns = columns;
    }

    public void setRows(int rows)
    {
        _rows = rows;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Date getCreated()
    {
        return new Date(_created);
    }

    public void setCreated(Date created)
    {
        _created = created.getTime();
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public String getDataFileId()
    {
        return _dataFileId;
    }

    public String getEntityId()
    {
        return _dataFileId;
    }

    public void setDataFileId(String dataFileId)
    {
        _dataFileId = dataFileId;
    }

    public String getContainerId()
    {
        return _container.getId();
    }

    public void setAttachments(Collection<Attachment> attachments)
    {
    }

    public int getWellGroupCount()
    {
        int size = 0;
        if (_groups != null)
        {
            for (Map.Entry<WellGroup.Type, Map<String, WellGroupTemplateImpl>> entry : _groups.entrySet())
                size += entry.getValue().size();
        }
        return size;
    }

    public int getWellGroupCount(WellGroup.Type type)
    {
        if (_groups != null)
        {
            Map<String, WellGroupTemplateImpl> typeMatch = _groups.get(type);
            if (typeMatch != null)
                return typeMatch.size();
        }
        return 0;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }
}
