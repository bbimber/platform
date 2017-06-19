/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.api.security.impersonation;

import org.labkey.api.view.UnauthorizedException;

/**
 * Thrown to indicate that someone attempted to impersonate in an invalid way - such as a project admin trying
 * to access another project while impersonating a user.
 * User: adam
 * Date: 5/6/12
 */
public class UnauthorizedImpersonationException extends UnauthorizedException
{
    private final ImpersonationContextFactory _factory;

    UnauthorizedImpersonationException(String message, ImpersonationContextFactory factory)
    {
        super(message);
        _factory = factory;
    }

    public ImpersonationContextFactory getFactory()
    {
        return _factory;
    }
}
