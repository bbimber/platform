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

package org.labkey.api.util.emailTemplate;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.NotFoundException;

import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 15, 2007
 */
public class EmailTemplateService
{
    static Logger _log = Logger.getLogger(EmailTemplateService.class);

    private static final String EMAIL_TEMPLATE_PROPERTIES_MAP_NAME = "emailTemplateProperties";
    private static final String MESSAGE_SUBJECT_PART = "subject";
    private static final String MESSAGE_BODY_PART = "body";
    private static final String EMAIL_TEMPLATE_DELIM = "/";

    private static final EmailTemplateService instance = new EmailTemplateService();
    private final Set<Class<? extends EmailTemplate>> _templates = new LinkedHashSet<Class<? extends EmailTemplate>>();

    public static EmailTemplateService get()
    {
        return instance;
    }
    private EmailTemplateService(){}

    public void registerTemplate(Class<? extends EmailTemplate> templateClass)
    {
        synchronized(_templates)
        {
            if (_templates.contains(templateClass))
                throw new IllegalStateException("Template : " + templateClass.getName() + " has previously been registered.");

            if (!EmailTemplate.class.isAssignableFrom(templateClass))
                throw new IllegalArgumentException("The specified class: " + templateClass + " is not an instance of EmailTemplate");

            _templates.add(templateClass);
        }
    }

    /** Looks only at site-level and default templates */
    public <T extends EmailTemplate> T getEmailTemplate(Class<T> templateClass)
    {
        return getEmailTemplate(templateClass, ContainerManager.getRoot());
    }

    /** Looks at folder-level, site-level and default templates */
    public <T extends EmailTemplate> T getEmailTemplate(Class<T> templateClass, Container c)
    {
        return (T)_getEmailTemplates(c).get(templateClass);
    }

    public List<EmailTemplate> getEditableEmailTemplates(Container c)
    {
        List<EmailTemplate> templates = new ArrayList<EmailTemplate>(_getEmailTemplates(c).values());

        if (!c.isRoot())
        {
            Iterator<EmailTemplate> i = templates.iterator();
            while (i.hasNext())
            {
                if (!i.next().getEditableScopes().isEditableIn(c))
                {
                    i.remove();
                }
            }
        }

        Collections.sort(templates, new Comparator<EmailTemplate>(){

            public int compare(EmailTemplate o1, EmailTemplate o2)
            {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return 1;
                if (o2 == null) return -1;

                int ret = o1.getPriority() - o2.getPriority();
                if (0 == ret && null != o1.getName() && null != o2.getName())
                    ret = o1.getName().compareToIgnoreCase(o2.getName());
                return ret;
            }
        });

        return templates;
    }

    private Map<String, String> getProperties(Container c, boolean writable)
    {
        if (writable)
            return PropertyManager.getWritableProperties(c, EMAIL_TEMPLATE_PROPERTIES_MAP_NAME, true);
        else
            return PropertyManager.getProperties(c, EMAIL_TEMPLATE_PROPERTIES_MAP_NAME);
    }

    private Map<Class<? extends EmailTemplate>, EmailTemplate> _getEmailTemplates(Container c)
    {
        Map<Class<? extends EmailTemplate>, EmailTemplate> templates = new HashMap<Class<? extends EmailTemplate>, EmailTemplate>();
        // Populate map in override sequence, so that the most specific override will be used

        // First, the default templates
        for (Class<? extends EmailTemplate> et : _templates)
        {
            templates.put(et, createTemplate(et));
        }

        // Second, the site-wide templates stored in the database
        addTemplates(templates, ContainerManager.getRoot());

        // Finally, the folder-scoped templates stored in the database
        addTemplates(templates, c);

        return templates;
    }

    private void addTemplates(Map<Class<? extends EmailTemplate>, EmailTemplate> templates, Container c)
    {
        Map<String, String> map = getProperties(c, false);
        for (Map.Entry<String, String> entry : map.entrySet())
        {
            final String key = entry.getKey();

            // Key format is "<TEMPLATE_CLASS_NAME>/<subject or body>"
            String[] parts = key.split(EMAIL_TEMPLATE_DELIM);

            if (parts.length == 2)
            {
                try
                {
                    String className = parts[0];
                    String partType = parts[1];
                    EmailTemplate et = templates.get(getTemplateClass(className));
                    if (et == null)
                    {
                        et = createTemplate(className);
                        templates.put(et.getClass(), et);
                    }
                    et.setContainer(c);

                    // Subject and bodies are stored as two separate key-value pairs in the map
                    if (MESSAGE_SUBJECT_PART.equals(partType))
                        et.setSubject(entry.getValue());
                    else
                        et.setBody(entry.getValue());
                }
                // do nothing, we don't necessarily care about stale template properties
                catch (Exception e)
                {
                    //_log.error("Unable to create a template for: " + parts[1], e);
                }
            }
        }
    }

    public Class<? extends EmailTemplate> getTemplateClass(String className)
    {
        try {
            Class c = Class.forName(className);
            if (EmailTemplate.class.isAssignableFrom(c))
            {
                return (Class<? extends EmailTemplate>)c;
            }
            throw new IllegalArgumentException("The specified class: " + c.getName() + " is not an instance of EmailTemplate");
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be found: " + className);
        }

    }

    public EmailTemplate createTemplate(String className)
    {
        return createTemplate(getTemplateClass(className));
    }

    public EmailTemplate createTemplate(Class<? extends EmailTemplate> templateClass)
    {
        try
        {
            return templateClass.newInstance();
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be created: " + templateClass.getName());
        }
    }

    public void saveEmailTemplate(EmailTemplate template, Container c)
    {
        if (!template.getEditableScopes().isEditableIn(c))
        {
            throw new NotFoundException("Cannot save template " + c + " in " + c);
        }
        Map<String, String> map = getProperties(c, true);

        final String className = template.getClass().getName();
        map.put(className + EMAIL_TEMPLATE_DELIM + MESSAGE_SUBJECT_PART,
                template.getSubject());
        map.put(className + EMAIL_TEMPLATE_DELIM + MESSAGE_BODY_PART,
                template.getBody());
        PropertyManager.saveProperties(map);
    }

    public void deleteEmailTemplate(EmailTemplate template, Container c)
    {
        Map<String, String> map = getProperties(c, true);

        final String className = template.getClass().getName();
        map.remove(className + EMAIL_TEMPLATE_DELIM + MESSAGE_SUBJECT_PART);
        map.remove(className + EMAIL_TEMPLATE_DELIM + MESSAGE_BODY_PART);
        PropertyManager.saveProperties(map);
    }

    public void upgradeTo102()
    {
        // We used to store templates as specially prefixed key/values in the user preferences properties map
        // They should now be stored in a separate property set, and we're changing from using % as the delimiter to ^
        Map<String, String> oldProperties = UserManager.getUserPreferences(true);
        Map<String, String> newProperties = getProperties(ContainerManager.getRoot(), true);

        // Iterate over a copy so we can modify the real map as we go 
        for (Map.Entry<String, String> entry : new HashMap<String, String>(oldProperties).entrySet())
        {
            String key = entry.getKey();
            if (key.startsWith("emailTemplateProperty"))
            {
                // Old key format is three parts - "emailTemplateProperty/<TEMPLATE_CLASS_NAME>/<subject or body>"
                String[] parts = key.split(EMAIL_TEMPLATE_DELIM);

                if (parts.length == 3)
                {
                    // Change the delimiter
                    String value = entry.getValue();
                    value = value.replace('%', '^');

                    // New key format is two parts - "<TEMPLATE_CLASS_NAME>/<subject or body>"
                    newProperties.put(parts[1] + EMAIL_TEMPLATE_DELIM + parts[2], value);
                }
                // Get rid of it in the old location
                oldProperties.remove(key);
            }
        }
        PropertyManager.saveProperties(oldProperties);
        PropertyManager.saveProperties(newProperties);
    }
}
