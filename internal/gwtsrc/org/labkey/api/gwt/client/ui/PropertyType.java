package org.labkey.api.gwt.client.ui;

import java.util.HashMap;
import java.util.Map;

/**
* Created by IntelliJ IDEA.
* User: matthewb
* Date: Apr 22, 2010
* Time: 11:33:56 AM
* To change this template use File | Settings | File Templates.
*/
public enum PropertyType
{
    xsdString("http://www.w3.org/2001/XMLSchema#string", "Text (String)", "string"),
    expMultiLine("http://www.w3.org/2001/XMLSchema#multiLine", "Multi-Line Text", "multiline"),
    xsdBoolean("http://www.w3.org/2001/XMLSchema#boolean", "Boolean", "boolean"),
    xsdInt("http://www.w3.org/2001/XMLSchema#int", "Integer", "int"),
    xsdDouble("http://www.w3.org/2001/XMLSchema#double", "Number (Double)", "double"),
    xsdDateTime("http://www.w3.org/2001/XMLSchema#dateTime", "DateTime", "datetime"),
    expFileLink("http://cpas.fhcrc.org/exp/xml#fileLink", "File", "file"),
    expAttachment("http://www.labkey.org/exp/xml#attachment", "Attachment", "attachment");

    private final String _uri, _display, _short;
    PropertyType(String uri, String display, String shortName)
    {
        this._uri = uri;
        this._display = display;
        this._short = shortName;
    }


    @Override
    public String toString()
    {
        return _uri;
    }

    public String getDisplay()
    {
        return _display;
    }

    public String getShortName()
    {
        return _short;
    }

    public static PropertyType fromName(String type)
    {
        PropertyType t = synonyms.get(type);
        if (null == t)
            t = synonyms.get(type.toLowerCase());
        return t;
    }


    private static Map<String,PropertyType> synonyms = new HashMap<String,PropertyType>();
    private static void _put(PropertyType t)
    {
        synonyms.put(t.toString(), t);
        synonyms.put(t.toString().toLowerCase(), t);
        synonyms.put(t.getShortName(), t);
    }
    static
    {
        for (PropertyType t : PropertyType.values())
            _put(t);
        synonyms.put("text", xsdString);
        synonyms.put("integer", xsdInt);
        synonyms.put("real", xsdDouble);
        synonyms.put("float", xsdDouble);
        synonyms.put("date", xsdDateTime);
        synonyms.put("xsd:string", xsdString);
        synonyms.put("xsd:boolean", xsdBoolean);
        synonyms.put("xsd:int", xsdInt);
        synonyms.put("xsd:double", xsdDouble);
        synonyms.put("xsd:datetime", xsdDateTime);
    }
}
