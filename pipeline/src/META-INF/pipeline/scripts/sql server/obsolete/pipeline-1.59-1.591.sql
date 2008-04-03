DELETE FROM pipeline.StatusFiles
    WHERE Container NOT IN (SELECT EntityId from core.Containers)
Go

ALTER TABLE pipeline.StatusFiles
    ADD CONSTRAINT FK_StatusFiles_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
Go    
