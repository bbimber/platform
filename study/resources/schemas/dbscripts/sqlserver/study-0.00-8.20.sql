/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

/* study-0.00-2.30.sql */

/* study-0.00-2.00.sql */

/* study-0.00-1.30.sql */

-- Tables used for Study module

IF NOT EXISTS (SELECT * FROM sysusers WHERE name ='study')
    EXEC sp_addapprole 'study', 'password'
GO

IF NOT EXISTS (SELECT * FROM systypes WHERE name ='LSIDtype')
    EXEC sp_addtype 'LSIDtype', 'nvarchar(300)'
GO


CREATE TABLE study.Study
(
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL
    CONSTRAINT PK_Study PRIMARY KEY (Container)
)
GO


CREATE TABLE study.Site
(
    EntityId ENTITYID NOT NULL DEFAULT NEWID(),
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    RowId INT IDENTITY(1,1),
    ScharpId INT,
    LdmsLabCode INT,
    LabwareLabCode NVARCHAR(20),
    LabUploadCode NVARCHAR(2),
    IsSal Bit,
    IsRepository Bit,
    IsEndpoint Bit,

    CONSTRAINT PK_Site PRIMARY KEY (RowId)
)
GO


CREATE TABLE study.Visit
(
    VisitId INT NOT NULL,
    Label NVARCHAR(200) NULL,
    TypeCode CHAR(1) NULL,
    ShowByDefault BIT NOT NULL DEFAULT 1,
    DisplayOrder INT NOT NULL DEFAULT 0,
    Container ENTITYID NOT NULL

    CONSTRAINT PK_Visit PRIMARY KEY CLUSTERED (Container,VisitId)
)
GO


CREATE TABLE study.VisitMap
(
    Container ENTITYID NOT NULL,
    VisitId INT NOT NULL,    -- FK
    DataSetId INT NOT NULL,    -- FK
    IsRequired BIT NOT NULL DEFAULT 1

    CONSTRAINT PK_VisitMap PRIMARY KEY CLUSTERED (Container,VisitId,DataSetId)
)
GO


CREATE TABLE study.DataSet -- AKA CRF or Assay
(
    Container ENTITYID NOT NULL,
    DataSetId INT NOT NULL,
    TypeURI NVARCHAR(200) NULL,
    Label NVARCHAR(200) NULL,
    ShowByDefault BIT NOT NULL DEFAULT 1,
    DisplayOrder INT NOT NULL DEFAULT 0,
    Category NVARCHAR(200) NULL

    CONSTRAINT PK_DataSet PRIMARY KEY CLUSTERED (Container,DataSetId)
)
GO


-- ParticipantId is not a sequence, we assume these are externally defined
CREATE TABLE study.Participant
(
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(16) NOT NULL,

    CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
)
GO

CREATE TABLE study.SampleRequestStatus
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label NVARCHAR(100),

    CONSTRAINT PK_SampleRequestStatus PRIMARY KEY (RowId)
)
GO


CREATE TABLE study.SampleRequestActor
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label NVARCHAR(100),
    PerSite Bit NOT NULL DEFAULT 0,

    CONSTRAINT PK_SampleRequestActor PRIMARY KEY (RowId)
)


CREATE TABLE study.SampleRequest
(
    -- standard fields
    _ts TIMESTAMP,
    RowId INT IDENTITY(1,1),
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Container ENTITYID NOT NULL,

    StatusId INT NOT NULL,
    Comments NTEXT,
    DestinationSiteId INT NULL,
    Hidden Bit NOT NULL DEFAULT 0,

    CONSTRAINT PK_SampleRequest PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequest_SampleRequestStatus FOREIGN KEY (StatusId) REFERENCES study.SampleRequestStatus(RowId),
    CONSTRAINT FK_SampleRequest_Site FOREIGN KEY (DestinationSiteId) REFERENCES study.Site(RowId)
)
GO

CREATE TABLE study.SampleRequestRequirement
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    ActorId INT NOT NULL,
    SiteId INT NULL,
    Description NVARCHAR(300),
    Complete Bit NOT NULL DEFAULT 0,

    CONSTRAINT PK_SampleRequestRequirement PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestRequirement_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestRequirement_SampleRequestActor FOREIGN KEY (ActorId) REFERENCES study.SampleRequestActor(RowId),
    CONSTRAINT FK_SampleRequestRequirement_Site FOREIGN KEY (SiteId) REFERENCES study.Site(RowId)
)
GO


CREATE TABLE study.SampleRequestEvent
(
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    Comments NTEXT,
    EntryType NVARCHAR(64),
    RequirementId INT NULL,

    CONSTRAINT FK_SampleRequestEvent_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId),
)
GO


CREATE TABLE study.Report
(
    ReportId INT IDENTITY NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ContainerId ENTITYID NOT NULL,
    Label NVARCHAR(100) NOT NULL,
    Params NVARCHAR(512) NULL,
    ReportType NVARCHAR(100) NOT NULL,
    Scope INT NOT NULL,

    CONSTRAINT PK_Report PRIMARY KEY (ReportId),
    CONSTRAINT UQ_Report UNIQUE (ContainerId, Label)
)
GO


CREATE TABLE study.StudyData
(
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitId INT NULL,
    DatasetId INT NOT NULL,
    LSID VARCHAR(200) NOT NULL,

    CONSTRAINT PK_ParticipantDataset PRIMARY KEY (LSID),
    CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, VisitId, ParticipantId)
)
GO


CREATE TABLE study.SpecimenAdditive (
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsAdditiveCode NVARCHAR(3),
    LabwareAdditiveCode NVARCHAR(20),
    Additive NVARCHAR(100),

    CONSTRAINT PK_Additives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container)
)
GO

CREATE TABLE study.SpecimenDerivative (
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsDerivativeCode NVARCHAR(3),
    LabwareDerivativeCode NVARCHAR(20),
    Derivative NVARCHAR(100),

    CONSTRAINT PK_Derivatives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container)
)
GO

CREATE TABLE study.SpecimenPrimaryType (
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    PrimaryTypeLdmsCode NVARCHAR(5),
    PrimaryTypeLabwareCode NVARCHAR(5),
    PrimaryType NVARCHAR(100),

    CONSTRAINT PK_PrimaryTypes PRIMARY KEY (RowId),
    CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container)
)
GO

CREATE TABLE study.Specimen (
    RowId INT NOT NULL, -- FK exp.Material
    Container ENTITYID NOT NULL,
    RecordSource NVARCHAR(10),
    GlobalUniqueId NVARCHAR(20) NOT NULL,
    Ptid INT,
    DrawTimestamp DATETIME,
    SalReceiptDate DATETIME,
    SpecimenNumber NVARCHAR(50),
    ClassId NVARCHAR(4),
    VisitValue FLOAT,
    ProtocolNumber NVARCHAR(10),
    VisitDescription NVARCHAR(3),
    OtherSpecimenId NVARCHAR(20),
    Volume FLOAT,
    VolumeUnits NVARCHAR(3),
    ExpectedTimeValue FLOAT,
    ExpectedTimeUnit NVARCHAR(15),
    GroupProtocol INT,
    SubAdditiveDerivative NVARCHAR(20),
    PrimaryTypeId INT,
    DerivativeTypeId INT,
    AdditiveTypeId INT,
    SpecimenCondition NVARCHAR(3),
    SampleNumber INT,
    XSampleOrigin NVARCHAR(20),
    ExternalLocation NVARCHAR(20),
    UpdateTimestamp DATETIME,

    CONSTRAINT PK_Specimens PRIMARY KEY (RowId),
    CONSTRAINT UQ_Specimens_GlobalId UNIQUE (GlobalUniqueId, Container),
    CONSTRAINT FK_Specimens_Additives FOREIGN KEY (AdditiveTypeId) REFERENCES study.SpecimenAdditive(RowId),
    CONSTRAINT FK_Specimens_Derivatives FOREIGN KEY (DerivativeTypeId) REFERENCES study.SpecimenDerivative(RowId),
    CONSTRAINT FK_Specimens_PrimaryTypes FOREIGN KEY (PrimaryTypeId) REFERENCES study.SpecimenPrimaryType(RowId)
)
GO

CREATE TABLE study.SpecimenEvent (
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT,
    SpecimenId INT NOT NULL,
    LabId INT,
    UniqueSpecimenId NVARCHAR(20),
    ParentSpecimenId INT,
    Stored INT,
    StorageFlag INT,
    StorageDate DATETIME,
    ShipFlag INT,
    ShipBatchNumber INT,
    ShipDate DATETIME,
    ImportedBatchNumber INT,
    LabReceiptDate DATETIME,
    Comments NVARCHAR(30),

    CONSTRAINT PK_SpecimensEvents PRIMARY KEY (RowId),
    CONSTRAINT UQ_Specimens_ScharpId UNIQUE (ScharpId, Container),
    CONSTRAINT FK_SpecimensEvents_Specimens FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId),
    CONSTRAINT FK_Specimens_Site FOREIGN KEY (LabId) REFERENCES study.Site(RowId)
)
GO

CREATE TABLE study.SampleRequestSpecimen
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SampleRequestId INT NOT NULL,
    SpecimenId INT NOT NULL,

    CONSTRAINT PK_SampleRequestSpecimen PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestSpecimen_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestSpecimen_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId)
)
GO

/* study-1.30-1.40.sql */

ALTER TABLE study.StudyData ADD Created DATETIME NULL, Modified DATETIME NULL, VisitDate DATETIME NULL
GO

ALTER TABLE study.DataSet ADD EntityId ENTITYID
GO

ALTER TABLE study.Study ADD EntityId ENTITYID
GO

ALTER TABLE study.Report DROP COLUMN Created
GO

ALTER TABLE study.Report ADD Created DATETIME
GO

CREATE TABLE study.AssayRun
(
    RowId int IDENTITY (1, 1) NOT NULL,
    AssayType NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_AssayRun PRIMARY KEY (RowId)
)
GO

/* study-1.40-1.50.sql */

ALTER TABLE study.SampleRequestStatus ADD
    FinalState BIT NOT NULL DEFAULT 0,
    SpecimensLocked BIT NOT NULL DEFAULT 1
GO

ALTER TABLE study.Specimen
    ALTER COLUMN Ptid NVARCHAR(32)
GO

ALTER TABLE study.Participant ADD
    EnrollmentSiteId INT NULL,
    CurrentSiteId INT NULL,

    CONSTRAINT FK_EnrollmentSiteId_Site FOREIGN KEY (EnrollmentSiteId) REFERENCES study.Site (RowId),
    CONSTRAINT FK_CurrentSiteId_Site FOREIGN KEY (CurrentSiteId) REFERENCES study.Site (RowId)
GO

ALTER TABLE study.SpecimenEvent
    DROP CONSTRAINT UQ_Specimens_ScharpId
GO

/* study-1.50-1.60.sql */

CREATE TABLE study.UploadLog
(
    RowId INT IDENTITY NOT NULL,
    Container ENTITYID NOT NULL,
    Created DATETIME NOT NULL,
    CreatedBy USERID NOT NULL,
    Description TEXT,
    FilePath VARCHAR(512),
    DatasetId INT NOT NULL,
    Status VARCHAR(20),

    CONSTRAINT PK_UploadLog PRIMARY KEY (RowId),
    CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath)
)
GO

ALTER TABLE study.Report
    ADD ShowWithDataset INT NULL
GO

-- Change visit ids to type numeric:
ALTER TABLE study.Visit
    DROP CONSTRAINT PK_Visit
GO
ALTER TABLE study.Visit
    ALTER COLUMN VisitId NUMERIC(15,4) NOT NULL
GO
ALTER TABLE study.Visit
    ADD CONSTRAINT PK_Visit PRIMARY KEY CLUSTERED (Container,VisitId)
GO


ALTER TABLE study.VisitMap
    DROP CONSTRAINT PK_VisitMap
GO
ALTER TABLE study.VisitMap
    ALTER COLUMN VisitId NUMERIC(15,4) NOT NULL
GO
ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY CLUSTERED (Container,VisitId,DataSetId)
GO


ALTER TABLE study.studydata
    DROP CONSTRAINT AK_ParticipantDataset
GO
ALTER TABLE study.studydata
    ALTER COLUMN VisitId NUMERIC(15,4) NULL
GO
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, VisitId, ParticipantId)
GO

ALTER TABLE study.Specimen
    ALTER COLUMN VisitValue NUMERIC(15,4)
GO

/* study-1.60-1.70.sql */

ALTER TABLE study.Visit ADD VisitDateDatasetId INT
GO
ALTER TABLE study.DataSet ADD VisitDatePropertyName NVARCHAR(200)
GO

CREATE TABLE study.Plate
(
    RowId INT IDENTITY(1,1),
    LSID NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(200) NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    Template BIT NOT NULL,
    DataFileId ENTITYID,
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    CONSTRAINT PK_Plate PRIMARY KEY (RowId)
)
GO

CREATE TABLE study.WellGroup
(
    RowId INT IDENTITY(1,1),
    PlateId INT NOT NULL,
    LSID NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(200) NULL,
    Template BIT NOT NULL,
    TypeName NVARCHAR(50) NOT NULL,
    CONSTRAINT PK_WellGroup PRIMARY KEY (RowId),
    CONSTRAINT FK_WellGroup_Plate FOREIGN KEY (PlateId) REFERENCES study.Plate(RowId)
)
GO

CREATE TABLE study.Well
(
    RowId INT IDENTITY(1,1),
    LSID NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Value FLOAT NULL,
    Dilution FLOAT NULL,
    PlateId INT NOT NULL,
    Row INT NOT NULL,
    Col INT NOT NULL,
    CONSTRAINT PK_Well PRIMARY KEY (RowId),
    CONSTRAINT FK_Well_Plate FOREIGN KEY (PlateId) REFERENCES study.Plate(RowId)
)
GO

--
-- refactor Visit, split VisitId into two different keys
--
-- VisitId --> VisitSequenceId
-- VisitId --> VisitRowId
--

-- in with the new
ALTER TABLE study.Visit ADD RowId INT IDENTITY(1,1) NOT NULL;
ALTER TABLE study.Visit DROP CONSTRAINT PK_Visit;
ALTER TABLE study.Visit ADD CONSTRAINT PK_Visit PRIMARY KEY (Container,RowId);
ALTER TABLE study.Visit ADD SequenceNumMin NUMERIC(15,4) NOT NULL DEFAULT 0;
ALTER TABLE study.Visit ADD SequenceNumMax NUMERIC(15,4) NOT NULL DEFAULT 0;
GO

--
-- fix up VisitMap
--

ALTER TABLE study.VisitMap DROP CONSTRAINT PK_VisitMap;
ALTER TABLE study.VisitMap ADD VisitRowId INT NOT NULL DEFAULT -1;
GO
ALTER TABLE study.VisitMap DROP COLUMN VisitId;
ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitRowId,DataSetId);
GO

--
-- fix up ParticipantVisit
--

CREATE TABLE study.ParticipantVisit
(
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitRowId int NULL,
    SequenceNum NUMERIC(15,4) NOT NULL,
    VisitDate DATETIME NULL
);
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);
GO

--
-- refactor StudyData
--

ALTER TABLE study.StudyData ADD SequenceNum Numeric(15,4);
GO
ALTER TABLE study.StudyData DROP AK_ParticipantDataset;
ALTER TABLE study.StudyData DROP COLUMN VisitId;
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, SequenceNum, ParticipantId);
GO

-- out with the old
ALTER TABLE study.Visit DROP COLUMN VisitId;
GO

ALTER TABLE study.StudyData
    ADD SourceLSID VARCHAR(200) NULL
GO
ALTER TABLE study.DataSet ADD KeyPropertyName NVARCHAR(50) NULL         -- Property name in TypeURI
GO

ALTER TABLE study.StudyData ADD _key NVARCHAR(200) NULL                 -- assay key column, used only on INSERT for UQ index
GO

ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;
GO

ALTER TABLE study.StudyData
    ADD CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key);
GO

-- rename VisitDate -> _VisitDate to avoid some confusion

ALTER TABLE study.StudyData ADD _VisitDate DATETIME NULL
GO
ALTER TABLE study.StudyData DROP COLUMN VisitDate
GO

/* study-1.70-2.00.sql */

ALTER TABLE study.SampleRequestSpecimen ADD
    SpecimenGlobalUniqueId NVARCHAR(100)
GO

ALTER TABLE study.Specimen DROP COLUMN
    SpecimenCondition,
    SampleNumber,
    XSampleOrigin,
    ExternalLocation,
    UpdateTimestamp,
    OtherSpecimenId,
    ExpectedTimeUnit,
    RecordSource,
    GroupProtocol,
    ExpectedTimeValue
GO

ALTER TABLE study.SpecimenEvent ADD
    SpecimenCondition NVARCHAR(3),
    SampleNumber INT,
    XSampleOrigin NVARCHAR(20),
    ExternalLocation NVARCHAR(20),
    UpdateTimestamp DATETIME,
    OtherSpecimenId NVARCHAR(20),
    ExpectedTimeUnit NVARCHAR(15),
    ExpectedTimeValue FLOAT,
    GroupProtocol INT,
    RecordSource NVARCHAR(10)
GO

-- fix up study.SampleRequestSpecimen
ALTER TABLE study.SampleRequestSpecimen DROP CONSTRAINT fk_SampleRequestSpecimen_specimen;
ALTER TABLE study.SampleRequestSpecimen DROP COLUMN SpecimenId;
GO

ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Additives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Derivatives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_PrimaryTypes;
GO

ALTER TABLE study.SpecimenAdditive DROP CONSTRAINT UQ_Additives;
CREATE INDEX IX_SpecimenAdditive_ScharpId ON study.SpecimenAdditive(ScharpId);
ALTER TABLE study.SpecimenAdditive ADD CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container);
GO

ALTER TABLE study.SpecimenDerivative DROP CONSTRAINT UQ_Derivatives;
CREATE INDEX IX_SpecimenDerivative_ScharpId ON study.SpecimenDerivative(ScharpId);
ALTER TABLE study.SpecimenDerivative ADD CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container);
GO

ALTER TABLE study.SpecimenPrimaryType DROP CONSTRAINT UQ_PrimaryTypes;
CREATE INDEX IX_SpecimenPrimaryType_ScharpId ON study.SpecimenPrimaryType(ScharpId);
ALTER TABLE study.SpecimenPrimaryType ADD CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container);
GO

CREATE INDEX IX_SpecimenEvent_SpecimenId ON study.SpecimenEvent(SpecimenId);
CREATE INDEX IX_Specimen_PrimaryTypeId ON study.Specimen(PrimaryTypeId);
CREATE INDEX IX_Specimen_DerivativeTypeId ON study.Specimen(DerivativeTypeId);
CREATE INDEX IX_Specimen_AdditiveTypeId ON study.Specimen(AdditiveTypeId);
GO

CREATE TABLE study.StudyDesign
(
    -- standard fields
    _ts TIMESTAMP,
    StudyId INT IDENTITY(1,1),
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Container ENTITYID NOT NULL,
    PublicRevision INT NULL,
    DraftRevision INT NULL,
    Label NVARCHAR(200) NOT NULL,

    CONSTRAINT PK_StudyDesign PRIMARY KEY (StudyId),
    CONSTRAINT UQ_StudyDesign UNIQUE (Container,StudyId),
    CONSTRAINT UQ_StudyDesignLabel UNIQUE (Container, Label)
);


CREATE TABLE study.StudyDesignVersion
(
    -- standard fields
    _ts TIMESTAMP,
    RowId INT IDENTITY(1,1),
    StudyId INT NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    Container ENTITYID NOT NULL,
    Revision INT NOT NULL,
    Draft Bit NOT NULL DEFAULT 1,
    Label NVARCHAR(200) NOT NULL,
    Description NTEXT,
    XML NTEXT,

    CONSTRAINT PK_StudyDesignVersion PRIMARY KEY (StudyId,Revision),
    CONSTRAINT FK_StudyDesignVersion_StudyDesign FOREIGN KEY (StudyId) REFERENCES study.StudyDesign(StudyId),
    CONSTRAINT UQ_StudyDesignVersion UNIQUE (Container,Label,Revision)
);

ALTER TABLE study.Report DROP CONSTRAINT PK_Report
GO
ALTER TABLE study.Report ADD CONSTRAINT PK_Report PRIMARY KEY (ContainerId, ReportId)
GO

CREATE INDEX IX_AssayRun_Container ON study.AssayRun(Container);

CREATE INDEX IX_Plate_Container ON study.Plate(Container);

CREATE INDEX IX_SampleRequest_Container ON study.SampleRequest(Container);

CREATE INDEX IX_SampleRequest_StatusId ON study.SampleRequest(StatusId);

CREATE INDEX IX_SampleRequest_DestinationSiteId ON study.SampleRequest(DestinationSiteId);

CREATE INDEX IX_SampleRequestActor_Container ON study.SampleRequestActor(Container);

CREATE INDEX IX_SampleRequestEvent_Container ON study.SampleRequestEvent(Container);

CREATE INDEX IX_SampleRequestEvent_RequestId ON study.SampleRequestEvent(RequestId);

CREATE INDEX IX_SampleRequestRequirement_Container ON study.SampleRequestRequirement(Container);

CREATE INDEX IX_SampleRequestRequirement_RequestId ON study.SampleRequestRequirement(RequestId);

CREATE INDEX IX_SampleRequestRequirement_ActorId ON study.SampleRequestRequirement(ActorId);

CREATE INDEX IX_SampleRequestRequirement_SiteId ON study.SampleRequestRequirement(SiteId);

CREATE INDEX IX_SampleRequestSpecimen_Container ON study.SampleRequestSpecimen(Container);

CREATE INDEX IX_SampleRequestSpecimen_SampleRequestId ON study.SampleRequestSpecimen(SampleRequestId);

CREATE INDEX IX_SampleRequestSpecimen_SpecimenGlobalUniqueId ON study.SampleRequestSpecimen(SpecimenGlobalUniqueId);

CREATE INDEX IX_SampleRequestStatus_Container ON study.SampleRequestStatus(Container);

CREATE INDEX IX_Specimen_Container ON study.Specimen(Container);

CREATE INDEX IX_Specimen_GlobalUniqueId ON study.Specimen(GlobalUniqueId);

CREATE INDEX IX_SpecimenAdditive_Container ON study.SpecimenAdditive(Container);

CREATE INDEX IX_SpecimenDerivative_Container ON study.SpecimenDerivative(Container);

CREATE INDEX IX_SpecimenPrimaryType_Container ON study.SpecimenPrimaryType(Container);

CREATE INDEX IX_SpecimenEvent_Container ON study.SpecimenEvent(Container);

CREATE INDEX IX_SpecimenEvent_LabId ON study.SpecimenEvent(LabId);

CREATE INDEX IX_Well_PlateId ON study.Well(PlateId);

CREATE INDEX IX_Well_Container ON study.Well(Container);

CREATE INDEX IX_WellGroup_PlateId ON study.WellGroup(PlateId);

CREATE INDEX IX_WellGroup_Container ON study.WellGroup(Container);

GO
/*
 * Fix for https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=3111
 */

/* study-2.00-2.10.sql */

-- VISITMAP
EXEC sp_rename 'study.visitmap.isrequired', 'required', 'COLUMN';
GO

-- DATASET

ALTER TABLE study.dataset ADD name VARCHAR(200);
GO

ALTER TABLE study.dataset ALTER COLUMN name VARCHAR(200) NOT NULL
GO
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
GO

ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetName
GO
ALTER TABLE study.dataset ALTER COLUMN name NVARCHAR(200) NOT NULL
GO
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
GO
ALTER TABLE study.SampleRequestEvent ADD
    CONSTRAINT PK_SampleRequestEvent PRIMARY KEY (RowId);

ALTER TABLE study.Site ADD
    IsClinic Bit
GO

ALTER TABLE study.Specimen ADD
    OriginatingLocationId INT,
    CONSTRAINT FK_SpecimenOrigin_Site FOREIGN KEY (OriginatingLocationId) REFERENCES study.Site(RowId)
GO

CREATE INDEX IX_Specimen_OriginatingLocationId ON study.Specimen(OriginatingLocationId);
GO

ALTER TABLE study.StudyDesign ADD StudyEntityId entityid
GO

ALTER TABLE study.Dataset ADD Description NTEXT NULL
GO

ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData
GO
ALTER TABLE study.StudyData ADD CONSTRAINT UQ_StudyData UNIQUE CLUSTERED
(
    Container,
    DatasetId,
    SequenceNum,
    ParticipantId,
    _key
)
GO


ALTER TABLE study.StudyDesign
    ADD Active BIT
GO

ALTER TABLE study.StudyDesign
    ADD CONSTRAINT DF_Active DEFAULT 0 FOR Active
GO

ALTER TABLE study.StudyDesign
    DROP COLUMN StudyEntityId
GO

ALTER TABLE study.StudyDesign
    ADD SourceContainer ENTITYID
GO

/* study-2.10-2.20.sql */

ALTER TABLE study.SampleRequest ADD
    EntityId ENTITYID NULL
GO

ALTER TABLE study.SampleRequestRequirement
    ADD OwnerEntityId ENTITYID NULL
GO

ALTER TABLE study.SampleRequestRequirement
    DROP CONSTRAINT FK_SampleRequestRequirement_SampleRequest;
GO

CREATE INDEX IX_SampleRequest_EntityId ON study.SampleRequest(EntityId);
CREATE INDEX IX_SampleRequestRequirement_OwnerEntityId ON study.SampleRequestRequirement(OwnerEntityId);
GO

DROP TABLE study.AssayRun
GO

ALTER TABLE study.Specimen
    ADD Requestable BIT

ALTER TABLE study.SpecimenEvent ADD
    freezer NVARCHAR(200),
    fr_level1 NVARCHAR(200),
    fr_level2 NVARCHAR(200),
    fr_container NVARCHAR(200),
    fr_position NVARCHAR(200)
GO

/* study-2.20-2.30.sql */

ALTER TABLE study.Study ADD
    DateBased BIT DEFAULT 0,
    StartDate DATETIME
GO

ALTER TABLE study.ParticipantVisit ADD
    Day INTEGER
GO

ALTER TABLE study.Participant ADD
    StartDate DATETIME
GO

ALTER TABLE study.Dataset
    ADD DemographicData BIT
GO

ALTER TABLE study.Dataset
    ADD CONSTRAINT DF_DemographicData_False
    DEFAULT 0 FOR DemographicData
GO

ALTER TABLE study.Study ADD StudySecurity BIT DEFAULT 0
GO

/* study-2.30-8.10.sql */

/* study-2.30-2.31.sql */

ALTER TABLE study.Plate ADD Type NVARCHAR(200)
GO

/* study-2.31-2.32.sql */

CREATE TABLE study.Cohort
    (
    RowId INT IDENTITY(1,1),
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
    )
GO

ALTER TABLE study.Dataset ADD
    CohortId INT NULL,
    CONSTRAINT FK_Dataset_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)

CREATE INDEX IX_Dataset_CohortId ON study.Dataset(CohortId)
GO

ALTER TABLE study.Participant ADD
    CohortId INT NULL,
    CONSTRAINT FK_Participant_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Participant_CohortId ON study.Participant(CohortId);
CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
GO

CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid);
GO

ALTER TABLE study.Visit ADD
    CohortId INT NULL,
    CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)
GO

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);
GO

ALTER TABLE study.Study ADD
    ParticipantCohortDataSetId INT NULL,
    ParticipantCohortProperty NVARCHAR(200) NULL;
GO

/* study-2.32-2.33.sql */

ALTER TABLE study.Participant
    DROP CONSTRAINT PK_Participant
GO

DROP INDEX study.Participant.IX_Participant_ParticipantId
GO    

ALTER TABLE study.Participant
    ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL
GO

ALTER TABLE study.Participant
    ADD CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
GO    

CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId)
GO

/* study-2.33-2.34.sql */

ALTER TABLE study.SpecimenEvent
    ALTER COLUMN Comments NVARCHAR(200)
GO

/* study-8.10-8.20.sql */

/* study-8.10-8.11.sql */

ALTER TABLE study.ParticipantVisit
    DROP CONSTRAINT PK_ParticipantVisit
GO

ALTER TABLE study.ParticipantVisit
    ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL
GO

ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);
GO

/* study-8.11-8.12.sql */

CREATE TABLE study.ParticipantView
       (
       RowId INT IDENTITY(1,1),
       CreatedBy USERID,
       Created DATETIME,
       ModifiedBy USERID,
       Modified DATETIME,
       Container ENTITYID NOT NULL,
       Body TEXT,
       Active BIT NOT NULL,
       CONSTRAINT PK_ParticipantView PRIMARY KEY (RowId),
       CONSTRAINT FK_ParticipantView_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
       )

GO

/* study-8.12-8.13.sql */

ALTER TABLE study.specimen ADD CurrentLocation INT;
ALTER TABLE study.specimen ADD
    CONSTRAINT FK_CurrentLocation_Site FOREIGN KEY (CurrentLocation) references study.Site(RowId)
GO

CREATE INDEX IX_Specimen_CurrentLocation ON study.Specimen(CurrentLocation);
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue);
CREATE INDEX IX_Visit_SequenceNumMin ON study.Visit(SequenceNumMin);
CREATE INDEX IX_Visit_ContainerSeqNum ON study.Visit(Container, SequenceNumMin);
GO

/* study-8.13-8.14.sql */

ALTER TABLE study.Dataset ADD
    KeyPropertyManaged BIT DEFAULT 0
GO

/* study-8.14-8.15.sql */

ALTER TABLE study.Study ADD
    DatasetRowsEditable BIT DEFAULT 0
GO

/* study-8.15-8.16.sql */

UPDATE study.Study
SET DatasetRowsEditable = 0
WHERE
DatasetRowsEditable IS NULL
GO

UPDATE study.Dataset
SET KeyPropertyManaged = 0
WHERE
KeyPropertyManaged IS NULL
GO

ALTER TABLE
study.Study
ALTER COLUMN DatasetRowsEditable BIT NOT NULL
GO

ALTER TABLE
study.Dataset
ALTER COLUMN KeyPropertyManaged BIT NOT NULL
GO

/* study-8.16-8.17.sql */