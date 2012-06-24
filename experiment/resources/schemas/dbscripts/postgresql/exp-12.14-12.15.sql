/*
 * Copyright (c) 2012 LabKey Corporation
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

-- Merge the "meta data only" and "entire list data" settings, migrating them to a single boolean (EntireListIndex) plus
-- a setting denoting what to index (EntireListIndexSetting = meta data only (0), item data only (1), or both (2))

ALTER TABLE exp.List ADD EntireListIndexSetting INT NOT NULL DEFAULT 0;  -- Meta data only, the default

UPDATE exp.List SET EntireListIndexSetting = 1 WHERE MetaDataIndex = FALSE AND EntireListIndex = TRUE; -- Item data only
UPDATE exp.List SET EntireListIndexSetting = 2 WHERE MetaDataIndex = TRUE AND EntireListIndex = TRUE;  -- Meta data and item data

UPDATE exp.List SET EntireListIndex = TRUE WHERE MetaDataIndex = TRUE;   -- Turn on EntireListIndex if meta data was being indexed

ALTER TABLE exp.List DROP MetaDataIndex;
