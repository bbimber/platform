-- VISITMAP
EXEC sp_rename 'study.visitmap.isrequired', 'required', 'COLUMN';
go


-- VISIT

-- ALTER TABLE study.visit ADD name NVARCHAR(200);
-- go
-- UPDATE study.visit SET name=COALESCE(label,cast(rowid as NVARCHAR(20)));
-- UPDATE study.visit SET name=rowid
-- WHERE 1 < (SELECT COUNT(*) FROM study.visit V where V.container=study.visit.container and V.name=study.visit.name)
-- go
--
-- ALTER TABLE study.visit ALTER COLUMN name VARCHAR(200) NOT NULL
-- go
-- ALTER TABLE study.visit ADD CONSTRAINT UQ_VisitName UNIQUE (container, name);
-- go


-- DATASET

ALTER TABLE study.dataset ADD name VARCHAR(200);
go
UPDATE study.dataset SET name=COALESCE(label,cast(datasetid as NVARCHAR(20)));
UPDATE study.dataset SET name=datasetid
WHERE 1 < (SELECT COUNT(*) FROM study.dataset D where D.container=study.dataset.container and D.name=study.dataset.name)
go

ALTER TABLE study.dataset ALTER COLUMN name VARCHAR(200) NOT NULL
go
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
go

ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetName
go
ALTER TABLE study.dataset ALTER COLUMN name NVARCHAR(200) NOT NULL
go
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
go
ALTER TABLE study.SampleRequestEvent ADD
    CONSTRAINT PK_SampleRequestEvent PRIMARY KEY (RowId);

DROP VIEW study.SpecimenSummary
DROP VIEW study.SpecimenDetail
GO

ALTER TABLE study.Site ADD
    IsClinic Bit
GO

ALTER TABLE study.Specimen ADD
    OriginatingLocationId INT,
    CONSTRAINT FK_SpecimenOrigin_Site FOREIGN KEY (OriginatingLocationId) REFERENCES study.Site(RowId)
GO

CREATE INDEX IX_Specimen_OriginatingLocationId ON study.Specimen(OriginatingLocationId);
GO

CREATE VIEW study.SpecimenDetail AS
      SELECT SpecimenInfo.*,
        -- eliminate nulls in my left-outer-join fields:
        (CASE Locked WHEN 1 THEN 1 ELSE 0 END) AS LockedInRequest,
        (CASE AtRepository WHEN 1 THEN (CASE Locked WHEN 1 THEN 0 ELSE 1 END) ELSE 0 END) As Available
         FROM
            (
                SELECT
                    Specimen.Container, Specimen.RowId, SpecimenNumber, GlobalUniqueId, Ptid,
                    VisitDescription, VisitValue, Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId,
                    DerivativeTypeId, Site.Label AS SiteName,Site.LdmsLabCode AS SiteLdmsCode,
                    (CASE IsRepository WHEN 1 THEN 1 ELSE 0 END) AS AtRepository,
                    DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
                    OriginatingLocationId
                FROM
                    (study.Specimen AS Specimen LEFT OUTER JOIN study.SpecimenEvent AS Event ON (
                        Specimen.RowId = Event.SpecimenId AND Specimen.Container = Event.Container
                        AND Event.ShipDate IS NULL
                        AND (Event.ShipBatchNumber IS NULL OR Event.ShipBatchNumber = 0)
                        AND (Event.ShipFlag IS NULL OR Event.ShipFlag = 0))
                    ) LEFT OUTER JOIN study.Site AS Site ON
                        (Site.RowId = Event.LabId AND Site.Container = Event.Container)
        ) SpecimenInfo LEFT OUTER JOIN (
            SELECT *, 1 AS Locked
            FROM study.LockedSpecimens
        ) LockedSpecimens ON (SpecimenInfo.RowId = LockedSpecimens.RowId)
GO

CREATE VIEW study.SpecimenSummary AS
    SELECT Container, SpecimenNumber, Ptid, VisitDescription, VisitValue, SUM(Volume) AS TotalVolume,
        SUM(CASE Available WHEN 1 THEN Volume ELSE 0 END) As AvailableVolume,
        VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, DrawTimestamp, SalReceiptDate,
        ClassId, ProtocolNumber, SubAdditiveDerivative, OriginatingLocationId,
        COUNT(GlobalUniqueId) As VialCount,
        SUM(CASE LockedInRequest WHEN 1 THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN 1 THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN 1 THEN 1 ELSE 0 END) AS AvailableCount
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
        OriginatingLocationId
GO

ALTER TABLE study.StudyDesign ADD StudyEntityId entityid
go

ALTER TABLE study.Dataset ADD Description NTEXT NULL
go

ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData
go
ALTER TABLE study.StudyData ADD CONSTRAINT UQ_StudyData UNIQUE CLUSTERED
	(
		Container,
		DatasetId,
		SequenceNum,
		ParticipantId,
		_key
	)
go


ALTER TABLE study.StudyDesign
    ADD Active BIT
go

UPDATE study.StudyDesign SET Active=1 WHERE StudyEntityId IS NOT NULL
UPDATE study.StudyDesign SET Active=0 WHERE StudyEntityId IS NULL
go

ALTER TABLE study.StudyDesign
  ADD CONSTRAINT DF_Active DEFAULT 0 FOR Active
go

ALTER TABLE study.StudyDesign
    DROP COLUMN StudyEntityId
go

ALTER TABLE study.StudyDesign
    ADD SourceContainer ENTITYID
go

UPDATE study.StudyDesign SET SourceContainer = Container
go
