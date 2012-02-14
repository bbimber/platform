/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.announcements.model;

import org.labkey.announcements.AnnouncementsController;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.Container;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Mar 4, 2007
 * Time: 9:14:51 PM
 */
public abstract class EmailPrefsSelector
{
    // All project users' preferences plus anyone else who's signed up for notifications from this board.
    // Default option is set if this user has not indicated a preference.  Prefs with NONE have been removed.
    protected List<MessageConfigService.UserPreference> _emailPrefs;
    protected Container _c;

    protected EmailPrefsSelector(Container c)
    {
        initEmailPrefs(c);
        _c = c;
    }


    // Initialize list of email preferences: get all settings from the database, add the default values, and remove NONE.
    private void initEmailPrefs(Container c)
    {
        int defaultOption = AnnouncementManager.getDefaultEmailOption(c);
        MessageConfigService.UserPreference[] epArray = AnnouncementManager.getAnnouncementConfigProvider().getPreferences(c);
        _emailPrefs = new ArrayList<MessageConfigService.UserPreference>(epArray.length);

        for (MessageConfigService.UserPreference ep : epArray)
        {
            if (null == ep.getEmailOptionId())
                ep.setEmailOptionId(defaultOption);

            if (includeEmailPref(ep))
                _emailPrefs.add(ep);
        }
    }


    // Override this to filter out other prefs
    protected boolean includeEmailPref(MessageConfigService.UserPreference ep)
    {
        return AnnouncementManager.EMAIL_PREFERENCE_NONE != ep.getEmailOptionId() && AnnouncementManager.EMAIL_PREFERENCE_BROADCAST != ep.getEmailOptionId();
    }


    // All users with an email preference that was allowed by includeEmailPrefs() -- they have not been authorized!
    public Collection<User> getUsers()
    {
        Set<User> users = new HashSet<User>(_emailPrefs.size());

        for (MessageConfigService.UserPreference ep : _emailPrefs)
            users.add(ep.getUser());

        return users;
    }


    protected boolean shouldSend(AnnouncementModel ann, MessageConfigService.UserPreference ep)
    {
        int emailPreference = ep.getEmailOptionId() & AnnouncementManager.EMAIL_PREFERENCE_MASK;

        User user = ep.getUser();

        //if user is inactive, don't send email
        if (!user.isActive())
            return false;

        DiscussionService.Settings settings = AnnouncementsController.getSettings(_c);

        if (AnnouncementManager.EMAIL_PREFERENCE_MINE == emailPreference)
        {
            Set<User> authors = ann.getAuthors();

            if (!authors.contains(user))
                if (!settings.hasMemberList() || !ann.getMemberList().contains(user))
                    return false;
        }
        else
        {
            // Shouldn't be here if preference is NONE.
            assert AnnouncementManager.EMAIL_PREFERENCE_NONE != emailPreference;
        }

        Permissions perm = AnnouncementsController.getPermissions(_c, user, settings);

        return perm.allowRead(ann);
    }
}
