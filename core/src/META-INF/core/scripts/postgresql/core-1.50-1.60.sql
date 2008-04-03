/*
 * Copyright (c) 2003-2006 Fred Hutchinson Cancer Research Center
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
DROP VIEW core.Contacts;
DROP VIEW core.Users;

ALTER TABLE core.UsersData ALTER COLUMN Phone TYPE VARCHAR(64);
ALTER TABLE core.UsersData ALTER COLUMN Mobile TYPE VARCHAR(64);
ALTER TABLE core.UsersData ALTER COLUMN Pager TYPE VARCHAR(64);

CREATE VIEW core.Users AS
    SELECT Principals.Name AS Email, UsersData.*
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
    WHERE Type = 'u';

CREATE VIEW core.Contacts As
    SELECT Users.FirstName || ' ' || Users.LastName AS Name, Users.Email, Users.Phone, Users.UserId, Principals.OwnerId, Principals.Container, Principals.Name AS GroupName
    FROM core.Principals Principals
        INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
        INNER JOIN core.Users Users ON Members.UserId = Users.UserId;

CREATE OR REPLACE RULE Users_Update AS
	ON UPDATE TO core.Users DO INSTEAD
		UPDATE core.UsersData SET
			ModifiedBy = NEW.ModifiedBy,
			Modified = NEW.Modified,
			FirstName = NEW.FirstName,
			LastName = NEW.LastName,
			Phone = NEW.Phone,
			Mobile = NEW.Mobile,
			Pager = NEW.Pager,
			IM = NEW.IM,
			Description = NEW.Description,
			LastLogin = NEW.LastLogin
		WHERE UserId = NEW.UserId;
