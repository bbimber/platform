CREATE VIEW exp.ExperimentRunMaterialOutputs AS
	SELECT exp.Material.LSID AS MaterialLSID, exp.ExperimentRun.LSID AS RunLSID, exp.ExperimentRun.Container AS Container
	FROM exp.Material
	JOIN exp.ExperimentRun ON exp.Material.RunId=exp.ExperimentRun.RowId
	WHERE SourceApplicationId IS NOT NULL
;
CREATE TABLE exp.ActiveMaterialSource (
	Container ENTITYID NOT NULL,
	MaterialSourceLSID LSIDtype NOT NULL,
	CONSTRAINT PK_ActiveMaterialSource PRIMARY KEY (Container),
	CONSTRAINT FK_ActiveMaterialSource_Container FOREIGN KEY (Container)
			REFERENCES core.Containers(EntityId),
	CONSTRAINT FK_ActiveMaterialSource_MaterialSourceLSID FOREIGN KEY (MaterialSourceLSID)
			REFERENCES exp.MaterialSource(LSID)
);

CREATE VIEW exp.MaterialSourceWithProject AS
    SELECT ms.RowId, ms.Name, ms.LSID, ms.MaterialLSIDPrefix, ms.URLPattern, ms.Description,
        ms.Created,	ms.CreatedBy, ms.Modified, ms.ModifiedBy, ms.Container , dd.Project
    FROM exp.MaterialSource ms
    LEFT OUTER JOIN exp.DomainDescriptor dd ON ms.lsid = dd.domainuri;

ALTER TABLE exp.DataInput
    ADD COLUMN PropertyId INT NULL;

ALTER TABLE exp.DataInput
    ADD CONSTRAINT FK_DataInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId);

ALTER TABLE exp.MaterialInput
    ADD COLUMN PropertyId INT NULL;

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT FK_MaterialInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId);

DROP INDEX exp.IDX_ObjectProperty_StringValue
;

DROP VIEW exp.ObjectPropertiesView
;

ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue TYPE VARCHAR(4000)
;

UPDATE exp.ObjectProperty SET StringValue=CAST(TextValue AS VARCHAR(4000)), TypeTag='s'
WHERE StringValue IS NULL AND (TextValue IS NOT NULL OR TypeTag='t')
;

ALTER TABLE exp.ObjectProperty DROP COLUMN TextValue
;

CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.ObjectId, O.Container, O.ObjectURI, O.OwnerObjectId,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId
	    JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
;

UPDATE exp.Material SET CpasType='Material' WHERE CpasType IS NULL;

