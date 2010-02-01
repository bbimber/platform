/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.api.query;

/*
* User: Dave
* Date: Jun 10, 2008
* Time: 10:40:29 AM
*/

public class SimpleValidationError implements ValidationError
{
    private String _message;
    
    public SimpleValidationError(String message)
    {
        assert null != message: "Null message passed to SimpleValidationError!";
        _message = message;
    }

    public String getMessage()
    {
        return _message;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleValidationError that = (SimpleValidationError) o;

        if (_message != null ? !_message.equals(that._message) : that._message != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        return _message != null ? _message.hashCode() : 0;
    }
}