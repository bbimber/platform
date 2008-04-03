package org.labkey.core.query;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.audit.query.SimpleAuditColumnFactory;
import org.labkey.api.data.*;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.ViewContext;
import org.apache.commons.lang.ObjectUtils;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 16, 2007
 *
 * Event field documentation:
 *
 * createdBy - User who created the record
 * created - Timestamp
 * comment - record description
 * intKey1 - the user id of the principal being modified
 *
 */
public class UserAuditViewFactory extends SimpleAuditViewFactory
{
    private static final UserAuditViewFactory _instance = new UserAuditViewFactory();

    public static UserAuditViewFactory getInstance()
    {
        return _instance;
    }

    private UserAuditViewFactory(){}

    public String getEventType()
    {
        return UserManager.USER_AUDIT_EVENT;
    }

    public String getName()
    {
        return "User events";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        return createUserHistoryView(context);
    }

    public AuditLogQueryView createUserHistoryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter("EventType", UserManager.USER_AUDIT_EVENT);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        view.setShowCustomizeViewLinkInButtonBar(true);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("IntKey1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(TableInfo table)
    {
        ColumnInfo col = table.getColumn("IntKey1");
        if (col != null)
        {
            UserIdForeignKey.initColumn(col);
            col.setCaption("User");
            col.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        public String getName()
                        {
                            return "user";
                        }
                    };
                }
            });
        }
    }
}
