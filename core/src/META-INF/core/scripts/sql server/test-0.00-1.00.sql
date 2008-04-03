/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

EXEC sp_addapprole 'test', 'password'
GO

CREATE TABLE test.TestTable
	(
	_ts TIMESTAMP,
	EntityId ENTITYID DEFAULT NEWID(),
	RowId INT IDENTITY(1,1),
	CreatedBy USERID,
	Created DATETIME,

	Container ENTITYID,			--container/path
	Text NVARCHAR(195),		--filename
	
	IntNull INT NULL,
	IntNotNull INT NOT NULL,
	DatetimeNull DATETIME NULL,
	DatetimeNotNull DATETIME NOT NULL,
	RealNull REAL NULL,
	BitNull Bit NULL,
	BitNotNull Bit NOT NULL,

	CONSTRAINT PK_TestTable PRIMARY KEY (RowId)
	)
GO
