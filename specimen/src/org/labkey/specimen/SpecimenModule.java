/*
 * Copyright (c) 2021 LabKey Corporation
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

package org.labkey.specimen;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.specimen.view.SpecimenWebPartFactory;
import org.labkey.api.specimen.SpecimensPage;
import org.labkey.api.specimen.importer.DefaultSpecimenImportStrategyFactory;
import org.labkey.api.specimen.model.AdditiveTypeDomainKind;
import org.labkey.api.specimen.model.DerivativeTypeDomainKind;
import org.labkey.api.specimen.model.PrimaryTypeDomainKind;
import org.labkey.api.specimen.model.SpecimenDomainKind;
import org.labkey.api.specimen.model.SpecimenEventDomainKind;
import org.labkey.api.specimen.model.SpecimenRequestEventType;
import org.labkey.api.specimen.model.VialDomainKind;
import org.labkey.api.specimen.view.SpecimenToolsWebPartFactory;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.specimen.security.roles.SpecimenCoordinatorRole;
import org.labkey.specimen.security.roles.SpecimenRequesterRole;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SpecimenModule extends CodeOnlyModule
{
    public static final String NAME = "Specimen";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return List.of(
            new SpecimenWebPartFactory(),
            new SpecimenToolsWebPartFactory()
        );
    }

    @Override
    protected void init()
    {
        addController(SpecimenController.NAME, SpecimenController.class);

        PropertyService.get().registerDomainKind(new AdditiveTypeDomainKind());
        PropertyService.get().registerDomainKind(new DerivativeTypeDomainKind());
        PropertyService.get().registerDomainKind(new PrimaryTypeDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenEventDomainKind());
        PropertyService.get().registerDomainKind(new VialDomainKind());

        // Register early so these roles are available to Java code at upgrade time
        RoleManager.registerRole(new SpecimenCoordinatorRole());
        RoleManager.registerRole(new SpecimenRequesterRole());

        AttachmentService.get().registerAttachmentType(SpecimenRequestEventType.get());
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new SpecimenContainerListener());

        StudyService.get().registerStudyTabProvider(tabs ->tabs.add(new SpecimensPage("Specimen Data")));

        SpecimenService.get().registerSpecimenImportStrategyFactory(new DefaultSpecimenImportStrategyFactory());

        AuditLogService.get().registerAuditType(new SpecimenCommentAuditProvider());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }
}