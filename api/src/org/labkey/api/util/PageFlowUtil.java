/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.settings.TemplateResourceHandler;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebTheme;
import org.labkey.api.view.WebThemeManager;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.WebUtils;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;


public class PageFlowUtil
{
    public enum TransformFormat
    {
        html,
        xml
    }

    private static Logger _log = Logger.getLogger(PageFlowUtil.class);
    private static final String _newline = System.getProperty("line.separator");
                                                                           
    private static final Object[] NO_ARGS = new Object[ 0 ];

    private static final Pattern urlPattern = Pattern.compile(".*((http|https|ftp|mailto)://\\S+).*");
    private static final Pattern urlPatternStart = Pattern.compile("((http|https|ftp|mailto)://\\S+).*");

    /**
     * Default parser class.
     */
    protected static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";

    String encodeURLs(String input)
    {
        Matcher m = urlPattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        int start;
        int end = 0;
        while (m.find())
        {
            start = m.start();
            String href = m.group(1);
            if (href.endsWith(".")) href = href.substring(0, href.length() - 1);
            sb.append(input.substring(end, start));
            sb.append("<a href=\"").append(href).append("\">").append(href).append("</a>");
            end = m.end();
        }
        sb.append(input.substring(end));
        return sb.toString();
    }


    static public final String NONPRINTING_ALTCHAR = "~";
    static final String nonPrinting;

    static
    {
        StringBuffer sb = new StringBuffer();
        for (char c = 1 ; c < ' ' ; c++)
        {
            if (" \t\r\n".indexOf('c') == -1)
                sb.append(c);
        }
        nonPrinting = sb.toString();
    }

    static public String filterXML(String s)
    {
        return filter(s,false,false);
    }

    static public HString filter(HString s)
    {
        if (null == s)
            return HString.EMPTY;
        
        return new HString(filter(s.getSource()), false);
    }


    static public HString filter(HStringBuilder s)
    {
        if (null == s)
            return HString.EMPTY;

        return new HString(filter(s.getSource()), false);
    }

    
    static public String filter(String s, boolean encodeSpace, boolean encodeLinks)
    {
        if (null == s || 0 == s.length())
            return "";

        int len = s.length();
        StringBuilder sb = new StringBuilder(2 * len);
        boolean newline = false;

        for (int i=0 ; i < len; ++i)
        {
            char c = s.charAt(i);

            if (!Character.isWhitespace(c))
                newline = false;
            else if ('\r' == c || '\n' == c)
                newline = true;

            switch (c)
            {
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#039;");    // works for xml and html
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '\n':
                    if (encodeSpace)
                        sb.append("<br>\n");
                    else
                        sb.append(c);
                    break;
                case '\r':
                    break;
                case '\t':
                    if (!encodeSpace)
                        sb.append(c);
                    else if (newline)
                        sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                    else
                        sb.append("&nbsp; &nbsp; ");
                    break;
                case ' ':
                    if (encodeSpace && newline)
                        sb.append("&nbsp;");
                    else
                        sb.append(' ');
                    break;
                case 'f':
                case 'h':
                case 'm':
                    if (encodeLinks)
                    {
                        String sub = s.substring(i);
                        if ((c == 'f' || c == 'h' || c == 'm') && StringUtilsLabKey.startsWithURL(sub))
                        {
                            Matcher m = urlPatternStart.matcher(sub);
                            if (m.find())
                            {
                                String href = m.group(1);
                                if (href.endsWith(".")) href = href.substring(0, href.length() - 1);
                                sb.append("<a href=\"").append(href).append("\">").append(href).append("</a>");
                                i += href.length() - 1;
                                break;
                            }
                        }
                    }
                    sb.append(c);
                    break;
                default:
                    if (c >= ' ')
                        sb.append(c);
                    else
                    {
                        if (c == 0x08) // backspace (e.g. xtandem output)
                            break;
                        sb.append(NONPRINTING_ALTCHAR);
                    }
                    break;
            }
        }

        return sb.toString();
    }


    public static String filter(Object o)
    {
        return filter(o == null ? null : o.toString());
    }

    /**
     * HTML encode a string
     */
    public static String filter(String s)
    {
        return filter(s, false, false);
    }


    static public String filter(String s, boolean translateWhiteSpace)
    {
        return filter(s, translateWhiteSpace, false);
    }


    /**
     * put quotes around a JavaScript string, and HTML encode that.
     */
    public static String filterQuote(Object value)
    {
        if (value == null)
            return "null";
        String ret = PageFlowUtil.filter("\"" + PageFlowUtil.groovyString(value.toString()) + "\"");
        ret = ret.replace("&#039;", "\\&#039;");
        return ret;
    }

    /**
     * Creates a JavaScript string literal of an HTML escaped value.
     *
     * Ext, for example, will use the 'id' config parameter as an attribute value in an XTemplate.
     * The string value is inserted directly into the dom and so should be HTML encoded.
     *
     * @param s String to escaped
     * @return The JavaScript string literal of the HTML escaped value.
     */
    // For example, given the string: "\"'>--></script><script type=\"text/javascript\">alert(\"8(\")</script>"
    // the method will return: "'&quot;&#039;&gt;--&gt;&lt;/script&gt;&lt;script type=&quot;text/javascript&quot;&gt;alert(&quot;8(&quot;)&lt;/script&gt;'"
    public static String qh(String s)
    {
        return PageFlowUtil.jsString(PageFlowUtil.filter(s));
    }

    static public String jsString(CharSequence cs)
    {
        if (cs == null)
            return "''";

        String s;
        if (cs instanceof HString)
            s = ((HString)cs).getSource();
        else
            s = cs.toString();

        // UNDONE: what behavior do we want for tainted strings? IllegalArgumentException()?
        if (cs instanceof Taintable && ((Taintable)cs).isTainted())
        {
            if (s.toLowerCase().contains("<script"))
                return "''";
        }
        return jsString(s);
    }


    static public String jsString(String s)
    {
        if (s == null)
            return "''";

        StringBuilder js = new StringBuilder(s.length() + 10);
        js.append("'");
        int len = s.length();
        for (int i = 0 ; i<len ; i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '\\':
                    js.append("\\\\");
                    break;
                case '\n':
                    js.append("\\n");
                    break;
                case '\r':
                    js.append("\\r");
                    break;
                case '<':
                    js.append("\\x3C");
                    break;
                case '>':
                    js.append("\\x3E");
                    break;
                case '\'':
                    js.append("\\'");
                    break;
                case '\"':
                    js.append("\\\"");
                    break;
                default:
                    js.append(c);
                    break;
            }
        }
        js.append("'");
        return js.toString();
    }

    //used to output strings from Java in Groovy script.
    static public String groovyString(String s)
    {
        //replace single backslash
        s = s.replaceAll("\\\\", "\\\\\\\\");
        //replace double quote
        s = s.replaceAll("\"", "\\\\\"");
        return s;
    }

    @SuppressWarnings({"unchecked"})
    static Pair<String, String>[] _emptyPairArray = new Pair[0];   // Can't delare generic array

    public static Pair<String, String>[] fromQueryString(String query)
    {
        return fromQueryString(query, "UTF-8");
    }

    public static Pair<String, String>[] fromQueryString(String query, String encoding)
    {
        if (null == query || 0 == query.length())
            return _emptyPairArray;

        if (null == encoding)
            encoding = "UTF-8";

        List<Pair> parameters = new ArrayList<Pair>();
        if (query.startsWith("?"))
            query = query.substring(1);
        String[] terms = query.split("&");

        try
        {
            for (String term : terms)
            {
                if (0 == term.length())
                    continue;
                // NOTE: faster to decode all at once, just can't allow keys to have '=' char
                term = URLDecoder.decode(term, encoding);
                int ind = term.indexOf('=');
                if (ind == -1)
                    parameters.add(new Pair<String,String>(term.trim(), ""));
                else
                    parameters.add(new Pair<String,String>(term.substring(0, ind).trim(), term.substring(ind + 1).trim()));
            }
        }
        catch (UnsupportedEncodingException x)
        {
            throw new IllegalArgumentException(encoding, x);
        }

        //noinspection unchecked
        return parameters.toArray(new Pair[parameters.size()]);
    }


    public static Map<String, String> mapFromQueryString(String queryString)
    {
        Pair<String, String>[] pairs = fromQueryString(queryString);
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (Pair<String, String> p : pairs)
            m.put(p.getKey(), p.getValue());

        return m;
    }


    public static String toQueryString(Collection<? extends Map.Entry<?,?>> c)
    {
        return toQueryString(c, false);
    }

    
    public static String toQueryString(Collection<? extends Map.Entry<?,?>> c, boolean allowSubstSyntax)
    {
        if (null == c || c.isEmpty())
            return null;
        String strAnd = "";
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<?,?> entry : c)
        {
            sb.append(strAnd);
            Object key = entry.getKey();
            if (null == key)
                continue;
            Object v = entry.getValue();
            String value = v == null ? "" : String.valueOf(v);
            sb.append(encode(String.valueOf(key)));
            sb.append('=');
            if (allowSubstSyntax && value.length()>3 && value.startsWith("${") && value.endsWith("}"))
                sb.append(value);
            else
                sb.append(encode(value));
            strAnd = "&";
        }
        return sb.toString();
    }


    public static String toQueryString(PropertyValues pvs)
    {
        if (null == pvs || pvs.isEmpty())
            return null;
        String strAnd = "";
        StringBuffer sb = new StringBuffer();
        for (PropertyValue entry : pvs.getPropertyValues())
        {
            Object key = entry.getName();
            if (null == key)
                continue;
            String encKey = encode(String.valueOf(key));
            Object v = entry.getValue();
            if (v == null || v instanceof String || !v.getClass().isArray())
            {
                sb.append(strAnd);
                sb.append(encKey);
                sb.append('=');
                sb.append(encode(v==null?"":String.valueOf(v)));
                strAnd = "&";
            }
            else
            {
                Object[] a = (Object[])v;
                for (Object o : a)
                {
                    sb.append(strAnd);
                    sb.append(encKey);
                    sb.append('=');
                    sb.append(encode(o==null?"":String.valueOf(o)));
                    strAnd = "&";
                }
            }
        }
        return sb.toString();
    }


    public static <T> Map<T, T> map(T... args)
    {
        HashMap<T, T> m = new HashMap<T, T>();
        for (int i = 0; i < args.length; i += 2)
            m.put(args[i], args[i + 1]);
        return m;
    }


    public static <T> Set<T> set(T... args)
    {
        HashSet<T> s = new HashSet<T>();

        if (null != args)
            s.addAll(Arrays.asList(args));

        return s;
    }


    public static ArrayList pairs(Object... args)
    {
        ArrayList<Pair> list = new ArrayList<Pair>();
        for (int i = 0; i < args.length; i += 2)
            list.add(new Pair<Object,Object>(args[i], args[i + 1]));
        return list;
    }


    private static final Pattern pattern = Pattern.compile("\\+");


    /**
     * URL Encode string.
     * NOTE! this should be used on parts of a url, not an entire url
     */
    public static String encode(String s)
    {
        if (null == s)
            return "";
        try
        {
            return pattern.matcher(URLEncoder.encode(s, "UTF-8")).replaceAll("%20");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    public static String decode(String s)
    {
        try
        {
            return null==s ? "" : URLDecoder.decode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    /**
     * Encode path URL parts, preserving path separators.
     * @param path The raw path to encode.
     * @return An encoded version of the path parameter.
     */
    public static String encodePath(String path)
    {
        String[] parts = path.split("/");
        String ret = "";
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
                ret += "/";
            ret += encode(parts[i]);
        }
        return ret;
    }

    public static URI redirectToURI(HttpServletRequest request, String uri)
    {
        if (null == uri)
            uri = request.getContextPath() + "/";

        // Try redirecting to the URI stashed in the session
        try
        {
            return new URI(uri);
        }
        catch (Exception x)
        {
            // That didn't work, just redirect home
            try
            {
                return new URI(request.getContextPath());
            }
            catch (Exception y)
            {
                return null;
            }
        }
    }


    // Cookie helper function -- loops through Cookie array and returns matching value (or defaultValue if not found)
    public static String getCookieValue(Cookie[] cookies, String cookieName, String defaultValue)
    {
        if (null != cookies)
            for (Cookie cookie : cookies)
            {
                if (cookieName.equals(cookie.getName()))
                    return (cookie.getValue());
            }
        return (defaultValue);
    }


    /**
     * boolean controlling whether or not we compress {@link ObjectOutputStream}s when we render them in HTML forms.
     * 
     */
    static private final boolean COMPRESS_OBJECT_STREAMS = true;
    static public String encodeObject(Object o) throws IOException
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStream osCompressed;
        if (COMPRESS_OBJECT_STREAMS)
        {
            osCompressed = new DeflaterOutputStream(byteArrayOutputStream);
        }
        else
        {
            osCompressed = byteArrayOutputStream;
        }
        ObjectOutputStream oos = new ObjectOutputStream(osCompressed);
        oos.writeObject(o);
        oos.close();
        osCompressed.close();
        return new String(Base64.encodeBase64(byteArrayOutputStream.toByteArray(), true));
    }


    public static Object decodeObject(String s) throws IOException
    {
        s = StringUtils.trimToNull(s);
        if (null == s)
            return null;
        
        try
        {
            byte[] buf = Base64.decodeBase64(s.getBytes());
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buf);
            InputStream isCompressed;

            if (COMPRESS_OBJECT_STREAMS)
            {
                isCompressed = new InflaterInputStream(byteArrayInputStream);
            }
            else
            {
                isCompressed = byteArrayInputStream;
            }
            ObjectInputStream ois = new ObjectInputStream(isCompressed);
            Object obj = ois.readObject();
            return obj;
        }
        catch (ClassNotFoundException x)
        {
            throw new IOException(x.getMessage());
        }
    }

    
    public static byte[] gzip(String s)
    {
        try
        {
            return gzip(s.getBytes("UTF-8"));
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }


    public static byte[] gzip(byte[] in)
    {
        try
        {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            GZIPOutputStream zip = new GZIPOutputStream(buf);
            zip.write(in);
            zip.close();
            return buf.toByteArray();
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }


    public static int[] toInts(Collection<String> strings)
    {
        return toInts(strings.toArray(new String[strings.size()]));
    }


    public static int[] toInts(String[] strings)
    {
        int[] result = new int[strings.length];
        for (int i = 0; i < strings.length; i++)
        {
            result[i] = Integer.parseInt(strings[i]);
        }
        return result;
    }


    private static MimeMap _mimeMap = new MimeMap();

    public static String getContentTypeFor(String filename)
    {
        String contentType = _mimeMap.getContentTypeFor(filename);
        if (null == contentType)
        {
            contentType = "application/octet-stream";
        }
        return contentType;
    }

    private static void prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, String filename, boolean asAttachment)
    {
        String contentType = null==responseHeaders ? null : responseHeaders.get("Content-Type");
        if (null == contentType && null != filename)
            contentType = getContentTypeFor(filename);
        response.reset();
        response.setContentType(contentType);
        if (asAttachment)
        {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        }
        else
        {
            response.setHeader("Content-Disposition", "filename=\"" + filename + "\"");
        }
        for (Map.Entry<String, String> entry : responseHeaders.entrySet())
            response.setHeader(entry.getKey(), entry.getValue());
    }

    public static void streamFile(HttpServletResponse response, File file, boolean asAttachment) throws IOException
    {
        streamFile(response, Collections.<String, String>emptyMap(), file, asAttachment);
    }

    public static void streamFile(HttpServletResponse response, String fileName, boolean asAttachment) throws IOException
    {
        streamFile(response, Collections.<String, String>emptyMap(), new File(fileName), asAttachment);
    }


    // Read the file and stream it to the browser
    public static void streamFile(HttpServletResponse response, Map<String, String> responseHeaders, File file, boolean asAttachment) throws IOException
    {
        streamFile(response, responseHeaders, file.getName(), new FileInputStream(file), asAttachment);
    }


    // Read the file and stream it to the browser
    public static void streamFile(HttpServletResponse response, Map<String, String> responseHeaders, String name, InputStream is, boolean asAttachment) throws IOException
    {
        try
        {
            prepareResponseForFile(response, responseHeaders, name, asAttachment);
            ServletOutputStream out = response.getOutputStream();
            FileUtil.copyData(is, out);
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }


    public static void streamFileBytes(HttpServletResponse response, String filename, byte[] bytes, boolean asAttachment) throws IOException
    {
        prepareResponseForFile(response, Collections.<String, String>emptyMap(), filename, asAttachment);
        response.getOutputStream().write(bytes);
    }
    

    // Fetch the contents of a text file, and return it in a String.
    public static String getFileContentsAsString(File aFile)
    {
        StringBuilder contents = new StringBuilder();
        BufferedReader input = null;

        try
        {
            input = new BufferedReader(new FileReader(aFile));
            String line;
            while ((line = input.readLine()) != null)
            {
                contents.append(line);
                contents.append(_newline);
            }
        }
        catch (FileNotFoundException e)
        {
            _log.error(e);
            contents.append("File not found");
            contents.append(_newline);
        }
        catch (IOException e)
        {
            _log.error(e);
        }
        finally
        {
            IOUtils.closeQuietly(input);
        }
        return contents.toString();
    }


    public static class Content
    {
        public Content(String s)
        {
            this(s, null, System.currentTimeMillis());
        }

        public Content(String s, byte[] e, long m)
        {
            content = s;
            encoded = e;
            if (null == e && null != s)
                encoded = s.getBytes();
            modified = m;
        }

        public Content copy()
        {
            Content ret = new Content(content,encoded,modified);
            ret.dependencies = dependencies;
            ret.compressed = compressed;
            return ret;
        }

        public Object dependencies;
        public String content;
        public byte[] encoded;
        public byte[] compressed;
        public long modified;

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Content content1 = (Content) o;

            if (modified != content1.modified) return false;
            if (content != null ? !content.equals(content1.content) : content1.content != null) return false;
            if (dependencies != null ? !dependencies.equals(content1.dependencies) : content1.dependencies != null)
                return false;
            if (!Arrays.equals(encoded, content1.encoded)) return false;
            //if (!Arrays.equals(compressed, content1.compressed)) return false;
            return true;
        }

        @Override
        public int hashCode()
        {
            int result = dependencies != null ? dependencies.hashCode() : 0;
            result = 31 * result + (content != null ? content.hashCode() : 0);
            result = 31 * result + (encoded != null ? Arrays.hashCode(encoded) : 0);
            //result = 31 * result + (compressed != null ? Arrays.hashCode(compressed) : 0);
            result = 31 * result + (int) (modified ^ (modified >>> 32));
            return result;
        }
    }


    // Marker class for caching absense of content -- can't use a single marker object because of dependency handling.
    public static class NoContent extends Content
    {
        public NoContent(Object dependsOn)
        {
            super(null);
            dependencies = dependsOn;
        }
    }


    public static Content getViewContent(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        final StringWriter writer = new StringWriter();
        HttpServletResponse sresponse = new HttpServletResponseWrapper(response)
            {
                public PrintWriter getWriter()
                {
                    return new PrintWriter(writer);
                }
            };
        mv.getView().render(mv.getModel(), request, sresponse);
        String sheet = writer.toString();
        Content c = new Content(sheet);
        return c;
    }


	// UNDONE: Move to FileUtil
    // Fetch the contents of an input stream, and return in a String.
    public static String getStreamContentsAsString(InputStream is)
    {
		return getReaderContentsAsString(new BufferedReader(new InputStreamReader(is)));
    }


	public static String getReaderContentsAsString(BufferedReader reader)
	{
		StringBuilder contents = new StringBuilder();
		String line;
		try
		{
			while ((line = reader.readLine()) != null)
			{
				contents.append(line);
				contents.append(_newline);
			}
		}
		catch (IOException e)
		{
			_log.error("getStreamContentsAsString", e);
		}
		finally
		{
			IOUtils.closeQuietly(reader);
		}
		return contents.toString();
	}


    // Fetch the contents of an input stream, and return it in a list.
    public static List<String> getStreamContentsAsList(InputStream is) throws IOException
    {
        return getStreamContentsAsList(is, false);
    }


    // Fetch the contents of an input stream, and return it in a list, skipping comment lines is skipComments == true.
    public static List<String> getStreamContentsAsList(InputStream is, boolean skipComments) throws IOException
    {
        List<String> contents = new ArrayList<String>();
        BufferedReader input = new BufferedReader(new InputStreamReader(is));

        try
        {
            String line;
            while ((line = input.readLine()) != null)
                if (!skipComments || !line.startsWith("#"))
                    contents.add(line);
        }
        finally
        {
            IOUtils.closeQuietly(input);
        }

        return contents;
    }


    // Fetch the contents of a file, and return it in a list.
    public static List<String> getFileContentsAsList(File file) throws IOException
    {
        return getStreamContentsAsList(new FileInputStream(file));
    }


    public static boolean empty(String str)
    {
        return null == str || str.trim().length() == 0;
    }


    static Pattern patternPhone = Pattern.compile("((1[\\D]?)?\\(?(\\d\\d\\d)\\)?[\\D]*)?(\\d\\d\\d)[\\D]?(\\d\\d\\d\\d)");

    public static String formatPhoneNo(String s)
    {
        s = StringUtils.trimToNull(s);
        if (null == s)
            return "";
        Matcher m = patternPhone.matcher(s);
        if (!m.find())
            return s;
        //for (int i=0 ; i<=m.groupCount() ; i++) System.err.println(i + " " + m.group(i));
        StringBuffer sb = new StringBuffer(20);
        m.appendReplacement(sb, "");
        String area = m.group(3);
        String exch = m.group(4);
        String num = m.group(5);
        if (null != area && 0 < area.length())
            sb.append("(").append(area).append(") ");
        sb.append(exch).append("-").append(num);
        m.appendTail(sb);
        return sb.toString();
    }


    // Generates JavaScript that redirects to a new location when Enter is pressed.  Use this on pages that have
    // button links but don't submit a form.
    public static String generateRedirectOnEnter(ActionURL url)
    {
        return "\n<script type=\"text/javascript\">\n" +
                "document.onkeydown = keyListener;\n" +
                "function keyListener(e)" +
                "{\n" +
                "   if (!e)\n" +
                "   {\n" +
                "      //for IE\n" +
                "      e = window.event;\n" +
                "   }\n" +
                "   if (13 == e.keyCode)\n" +
                "   {\n" +
                "      document.location = \"" + PageFlowUtil.filter(url) + "\";\n" +
                "   }\n" +
                "}\n" +
                "</script>\n";
    }


    public static String generateBackButton()
    {
        return generateBackButton("Back");
    }

    public static String generateBackButton(String text)
    {
        return generateButton(text, "#", "window.history.back(); return false;");
    }

    /*
     * Renders a span wrapped in a link (<a>)
     * Consider: is there any way to name this method in such a way as to
     * make the order of parameters unambiguous?
     */
    public static String generateButton(String text, String href)
    {
        return generateButton(text, href, null);
    }

    public static String generateButton(String text, String href, String onClick)
    {
        return generateButton(text, href, onClick, "");
    }

    public static String generateButton(String text, String href, String onClick, String attributes)
    {
        char quote = getUsedQuoteSymbol(onClick); // we're modifying the javascript, so need to use whatever quoting the caller used

        String checkDisabled = "if (this.className.indexOf(" + quote + "labkey-disabled-button" + quote + ") != -1) return false; ";
        String script = wrapOnClick(onClick != null ? checkDisabled + onClick : checkDisabled);

        return "<a class=\"labkey-button\" href=\"" + filter(href) + "\"" +
                " onClick=" + script  +
                (attributes != null ? " " + attributes : "") +
                "><span>" + filter(text) + "</span></a>";
    }

    public static String generateButton(String text, URLHelper href)
    {
        return generateButton(text, href, null);
    }

    public static String generateButton(String text, URLHelper href, String onClick)
    {
        return generateButton(text, href.toString(), onClick);
    }

    /* Renders an input of type submit wrapped in a span */
    public static String generateSubmitButton(String text)
    {
        return generateSubmitButton(text, null);
    }

    public static String generateSubmitButton(String text, String onClickScript)
    {
        return generateSubmitButton(text, onClickScript, null);
    }

    public static String generateSubmitButton(String text, String onClick, String attributes)
    {
        return generateSubmitButton(text, onClick, attributes, true);
    }

    public static String generateSubmitButton(String text, String onClick, String attributes, boolean enabled)
    {
        return generateSubmitButton(text, onClick, attributes, enabled, false);
    }

    public static String generateSubmitButton(String text, String onClick, String attributes, boolean enabled, boolean disableOnClick)
    {
        String id = GUID.makeGUID();
        char quote = getUsedQuoteSymbol(onClick); // we're modifying the javascript, so need to use whatever quoting the caller used

        String checkDisabled = "if (this.className.indexOf(" + quote + "labkey-disabled-button" + quote + ") != -1) return false; ";
        String submitCode = "submitForm(document.getElementById(" + quote + id + quote + ").form); return false;";

        String onClickMethod;

        if (disableOnClick)
        {
            String replaceClass = "Ext.get(this).replaceClass(" + quote + "labkey-button" + quote + ", " + quote + "labkey-disabled-button" + quote + ");";
            onClick = onClick != null ? onClick + ";" + replaceClass : replaceClass;
        }

        if (onClick == null || "".equals(onClick))
            onClickMethod = checkDisabled + submitCode;
        else
            onClickMethod = checkDisabled + "this.form = document.getElementById(" + quote + id + quote + ").form; if (isTrueOrUndefined(function() {" + onClick + "}.call(this))) " +  submitCode;

        StringBuilder sb = new StringBuilder();

        sb.append("<input type=\"submit\" style=\"display: none;\" id=\"");
        sb.append(id);
        sb.append("\">");

        if (enabled)
            sb.append("<a class=\"labkey-button\"");
        else
            sb.append("<a class=\"labkey-disabled-button\"");

        sb.append(" href=\"#\"");

        sb.append(" onclick=").append(wrapOnClick(onClickMethod));

        if (attributes != null)
            sb.append(" ").append(" ").append(attributes);

        sb.append("><span>").append(filter(text)).append("</span></a>");

        return sb.toString();
    }

    /* Renders a span and a drop down arrow image wrapped in a link */
    public static String generateDropDownButton(String text, String href, String onClick, String attributes)
    {
        char quote = getUsedQuoteSymbol(onClick); // we're modifying the javascript, so need to use whatever quoting the caller used

        String checkDisabled = "if (this.className.indexOf(" + quote + "labkey-disabled-button" + quote + ") != -1) return false; ";
        String script = wrapOnClick(onClick != null ? checkDisabled + onClick : checkDisabled);

        return "<a class=\"labkey-menu-button\" href=\"" + filter(href) + "\"" +
                " onClick=" + script +
                (attributes != null ? " " + attributes : "") +
                "><span>" + filter(text) + "</span>&nbsp;<img src=\"" + HttpView.currentView().getViewContext().getContextPath() +
                "/_images/button_arrow.gif\" class=\"labkey-button-arrow\"></a>";
    }

    /* Renders a span and a drop down arrow image wrapped in a link */
    public static String generateDropDownButton(String text, String href, String onClick)
    {
        return generateDropDownButton(text, href, onClick, null);
    }

    /* Renders text and a drop down arrow image wrapped in a link not of type labkey-button */
    public static String generateDropDownTextLink(String text, String href, String onClick, boolean bold)
    {
        char quote = getUsedQuoteSymbol(onClick); // we're modifying the javascript, so need to use whatever quoting the caller used

        String checkDisabled = "if (this.className.indexOf(" + quote + "labkey-disabled-button" + quote + ") != -1) return false; ";
        String script = wrapOnClick(onClick != null ? checkDisabled + onClick : checkDisabled);

        return "<a class=\"labkey-header\" style=\"" + (bold ? "font-weight: bold;" : "") + "\" href=\"" + filter(href) + "\"" +
                " onClick=" + script +
                "><span>" + text + "</span>&nbsp;<img src=\"" + HttpView.currentView().getViewContext().getContextPath() +
                "/_images/text_link_arrow.gif\" style=\"position:relative; background-color:transparent; width:10px; height:auto; top:-1px; right:0;\"></a>";
    }

    /* Renders image and a drop down wrapped in an unstyled link */
    public static String generateDropDownImage(String text, String href, String onClick, String imageSrc, String imageId)
    {
        char quote = getUsedQuoteSymbol(onClick);

        String checkDisabled = "if (this.className.indexOf(" + quote + "labkey-disabled-button" + quote + ") != -1) return false; ";
        String script = wrapOnClick(onClick != null ? checkDisabled + onClick : checkDisabled);

        return "<a href=\"" + filter(href) +"\" onClick=" + script +"><img id=\"" + imageId + "\" title=\"" + text + "\"src=\"" + imageSrc + "\"/></a>";
    }

    /* Renders a lightly colored inactive button, or in other words, a disabled span wrapped in a link of type labkey-disabled-button */
    public static String generateDisabledButton(String text)
    {
        return "<a class=\"labkey-disabled-button\" disabled><span>" + filter(text) + "</span></a>";
    }

    /* Renders a lightly colored inactive button */
    public static String generateDisabledSubmitButton(String text, String onClick, String attributes)
    {
        return generateSubmitButton(text, onClick, attributes, false);
    }

    /* This function is used so that the onClick script can use either " or ' quote scheme inside of itself */
    public static String wrapOnClick(String onClick)
    {
        char quote = getUnusedQuoteSymbol(onClick);

        return quote + onClick + quote;
    }

    /**
     * If the provided text uses ", return '. If it uses ', return ".
     * This is useful to quote javascript.
     */
    public static char getUnusedQuoteSymbol(String text)
    {
        if (text == null || text.equals(""))
            return '"';

        int singleQuote = text.indexOf('\'');
        int doubleQuote = text.indexOf('"');
        if (doubleQuote == -1 || (singleQuote != -1 && singleQuote <= doubleQuote))
            return '"';
        return '\'';
    }

    public static char getUsedQuoteSymbol(String text)
    {
        char c = getUnusedQuoteSymbol(text);
        if (c == '"')
            return '\'';
        return '"';
    }

    public static String textLink(String text, String href, String id)
    {
        return textLink(text, href, null, id);
    }

    public static String textLink(String text, String href)
    {
        return textLink(text, href, null, null);
    }

    public static String textLink(String text, HString href, String onClickScript, String id)
    {
        return "<a class='labkey-text-link' href=\"" + filter(href) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + text + "<span class='css-arrow-right'></span></a>";
    }

    public static String textLink(String text, String href, String onClickScript, String id)
    {
        return "<a class='labkey-text-link' href=\"" + filter(href) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + text + "<span class='css-arrow-right'></span></a>";
    }

    public static String textLink(String text, String href, String onClickScript, String id, Map<String, String> properties)
    {
        String additions = "";
        for (Map.Entry<String, String> entry : properties.entrySet())
        {
            additions += entry.getKey() + "=\"" + entry.getValue() + "\" ";
        }

        return "<a class='labkey-text-link' " + additions + "href=\"" + filter(href) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + text + "<span class='css-arrow-right'></span></a>";
    }

    public static String textLink(String text, ActionURL url, String onClickScript, String id)
    {
        return "<a class='labkey-text-link' href=\"" + filter(url) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + text + "<span class='css-arrow-right'></span></a>";
    }

    public static String textLink(String text, ActionURL url)
    {
        return textLink(text, url.getLocalURIString(), null, null);
    }

    public static String textLink(String text, ActionURL url, String id)
    {
        return textLink(text, url.getLocalURIString(), null, id);
    }

    public static String helpPopup(String title, String helpText)
    {
        return helpPopup(title, helpText, false);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText)
    {
        return helpPopup(title, helpText, htmlHelpText, 0);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, int width)
    {
        String questionMarkHtml = "<span class=\"labkey-help-pop-up\">?</span>";
        return helpPopup(title, helpText, htmlHelpText, questionMarkHtml, width);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, 0, null);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, String onClickScript)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, 0, onClickScript);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, int width)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, width, null);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, int width, String onClickScript)
    {
        if (title == null && !htmlHelpText)
        {
            // use simple tooltip
            if (onClickScript == null)
                onClickScript = "return false";

            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" onClick=\"").append(onClickScript).append("\" title=\"");
            link.append(filter(helpText));
            link.append("\">").append(linkHtml).append("</a>");
            return link.toString();
        }
        else
        {
            StringBuilder showHelpDivArgs = new StringBuilder("this, ");
            showHelpDivArgs.append(filter(jsString(filter(title)), true)).append(", ");
            // The value of the javascript string literal is used to set the innerHTML of an element.  For this reason, if
            // it is text, we escape it to make it HTML.  Then, we have to escape it to turn it into a javascript string.
            // Finally, since this is script inside of an attribute, it must be HTML escaped again.
            showHelpDivArgs.append(filter(jsString(htmlHelpText ? helpText : filter(helpText, true))));
            if (width != 0)
                showHelpDivArgs.append(", ").append(filter(jsString(filter(String.valueOf(width) + "px"))));
            if (onClickScript == null)
            {
                onClickScript = "return showHelpDiv(" + showHelpDivArgs + ");";
            }
            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" onClick=\"");
            link.append(onClickScript);
            link.append("\" onMouseOut=\"return hideHelpDivDelay();\" onMouseOver=\"return showHelpDivDelay(");
            link.append(showHelpDivArgs).append(");\"");
            link.append(">").append(linkHtml).append("</a>");
            return link.toString();
        }
    }


    /**
     * helper for script validation
     */
    public static String convertHtmlToXml(String html, Collection<String> errors)
    {
        return tidy(html, true, errors);
    }


    static Pattern scriptPattern = Pattern.compile("(<script.*?>)(.*?)(</script>)", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);

    public static Document convertHtmlToDocument(final String html, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setDropEmptyParas(false); // radeox wikis use <p/> -- don't remove them
        tidy.setTrimEmptyElements(false); // keeps tidy from converting <p/> to <br><br>

        // TIDY does not property parse the contents of script tags!
        // see bug 5007
        // CONSIDER: fix jtidy see ParserImpl$ParseScript
        Map<String,String> scriptMap = new HashMap<String,String>();
        StringBuffer stripped = new StringBuffer(html.length());
        Matcher scriptMatcher = scriptPattern.matcher(html);
        int unique = html.hashCode();
        int count = 0;

        while (scriptMatcher.find())
        {
            count++;
            String key = "{{{" + unique + ":::" + count + "}}}";
            String match = scriptMatcher.group(2);
            scriptMap.put(key,match);
            scriptMatcher.appendReplacement(stripped, "$1" + key + "$3");
        }
        scriptMatcher.appendTail(stripped);

        StringWriter err = new StringWriter();
        try
        {
            // parse wants to use streams
            tidy.setErrout(new PrintWriter(err));
            Document doc = tidy.parseDOM(new ByteArrayInputStream(stripped.toString().getBytes("UTF-8")), null);

            // fix up scripts
            if (null != doc && null != doc.getDocumentElement())
            {
                NodeList nl = doc.getDocumentElement().getElementsByTagName("script");
                for (int i=0 ; i<nl.getLength() ; i++)
                {
                    Node script = nl.item(i);
                    NodeList childNodes = script.getChildNodes();
                    if (childNodes.getLength() != 1)
                        continue;
                    Node child = childNodes.item(0);
                    if (!(child instanceof CharacterData))
                        continue;
                    String contents = ((CharacterData)child).getData();
                    String replace = scriptMap.get(contents);
                    if (null == replace)
                        continue;
                    doc.createTextNode(replace);
                    script.removeChild(childNodes.item(0));
                    script.appendChild(doc.createTextNode(replace));
                }
            }

            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return doc;
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static String convertNodeToHtml(Node node) throws TransformerException, IOException
    {
        return convertNodeToString(node, TransformFormat.html);
    }

    public static String convertNodeToXml(Node node) throws TransformerException, IOException
    {
        return convertNodeToString(node, TransformFormat.xml);
    }

    public static String convertNodeToString(Node node, TransformFormat format) throws TransformerException, IOException
    {
        try
        {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.METHOD, format.toString());
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            t.transform(new DOMSource(node), new StreamResult(out));
            out.close();

            return new String(out.toByteArray(), "UTF-8").trim();
        }
        catch (TransformerFactoryConfigurationError e)
        {
            throw new RuntimeException("There was a problem creating the XML transformer factory." +
                    " If you specified a class name in the 'javax.xml.transform.TransformerFactory' system property," +
                    " please ensure that this class is included in the classpath for web application.", e);
        }
    }


    public static String tidy(final String html, boolean asXML, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        if (asXML)
            tidy.setXHTML(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8"); // utf8
        tidy.setOutputEncoding("UTF-8"); // utf8
        tidy.setDropEmptyParas(false); // allow <p/> in html wiki pages

        StringWriter err = new StringWriter();

        try
        {
            // parse wants to use streams
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tidy.setErrout(new PrintWriter(err));
            tidy.parse(new ByteArrayInputStream(html.getBytes("UTF-8")), out);
            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return new String(out.toByteArray(), "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static String tidyXML(final String xml, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        tidy.setXmlOut(true);
        tidy.setXmlTags(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8"); // utf8
        tidy.setOutputEncoding("UTF-8"); // utf8

        StringWriter err = new StringWriter();

        try
        {
            // parse want's to use streams
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tidy.setErrout(new PrintWriter(err));
            tidy.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")), out);
            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return new String(out.toByteArray(), "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    private static void parserSetFeature(XMLReader parser, String feature, boolean b)
    {
        try
        {
            parser.setFeature(feature, b);
        }
        catch (SAXNotSupportedException e)
        {
            _log.error("parserSetFeature", e);
        }
        catch (SAXNotRecognizedException e)
        {
            _log.error("parserSetFeature", e);
        }
    }


    public static String getStandardIncludes(Container c)
    {
        return getStandardIncludes(c, null);
    }

    // UNDONE: use a user-agent parsing library
    public static String getStandardIncludes(Container c, @Nullable String userAgent)
    {
        StringBuilder sb = getFaviconIncludes(c);
        sb.append(getLabkeyJS());
        sb.append(getStylesheetIncludes(c, userAgent, false));
        sb.append(getJavaScriptIncludes());
        return sb.toString();
    }


    public static StringBuilder getFaviconIncludes(Container c)
    {
        StringBuilder sb = new StringBuilder();

        ResourceURL faviconURL = TemplateResourceHandler.FAVICON.getURL(c);

        sb.append("    <link rel=\"shortcut icon\" href=\"");
        sb.append(PageFlowUtil.filter(faviconURL));
        sb.append("\" >\n");

        sb.append("    <link rel=\"icon\" href=\"");
        sb.append(PageFlowUtil.filter(faviconURL));
        sb.append("\" >\n");

        return sb;
    }


    public static String getStylesheetIncludes(Container c)
    {
        return getStylesheetIncludes(c, null, true);
    }


    /** @param forEmail whether this is intended for emailing, in which case we shouldn't include any JavaScript at all */
    public static String getStylesheetIncludes(Container c, @Nullable String userAgent, boolean forEmail)
    {
        boolean oldIE = null != userAgent && (-1 != userAgent.indexOf("MSIE 6") || -1 != userAgent.indexOf("MSIE 7"));
        boolean useLESS = null != HttpView.currentRequest().getParameter("less");
        boolean combinedCSS = false;
        WebTheme theme = WebThemeManager.getTheme(c);

        CoreUrls coreUrls = urlProvider(CoreUrls.class);
        StringBuilder sb = new StringBuilder();

        Formatter F = new Formatter(sb);
        String link = useLESS ? "<link href=\"%s\" type=\"text/x-less\" rel=\"stylesheet\">\n" : "<link href=\"%s\" type=\"text/css\" rel=\"stylesheet\">\n";
        
        // Combined CSS
        if (combinedCSS)
        {
            sb.append("<link href=\"").append(filter(coreUrls.getCombinedStylesheetURL(c))).append("\" type=\"text/css\" rel=\"stylesheet\" >\n");
        }
        else
        {
            F.format(link, AppProps.getInstance().getContextPath() + "/" + extJsRoot + "/resources/css/ext-all.css");

            F.format(link, PageFlowUtil.filter(new ResourceURL(theme.getStyleSheet(), ContainerManager.getRoot())));
            if (oldIE)
                F.format(link, PageFlowUtil.filter(new ResourceURL("stylesheetIE7.css", ContainerManager.getRoot())));

            ActionURL rootCustomStylesheetURL = coreUrls.getCustomStylesheetURL();

            if (!c.isRoot())
            {
                /* Add the themeStylesheet */
                if (coreUrls.getThemeStylesheetURL(c) != null)
                    F.format(link, PageFlowUtil.filter(coreUrls.getThemeStylesheetURL(c)));
                else
                {
                    /* In this case a themeStylesheet was not found in a subproject to default to the root */
                    if (coreUrls.getThemeStylesheetURL() != null)
                        F.format(link, PageFlowUtil.filter(coreUrls.getThemeStylesheetURL()));
                }
                ActionURL containerCustomStylesheetURL = coreUrls.getCustomStylesheetURL(c);

                /* Add the customStylesheet */
                if (null != containerCustomStylesheetURL)
                    F.format(link, PageFlowUtil.filter(containerCustomStylesheetURL));
                else
                {
                    if (null != rootCustomStylesheetURL)
                        F.format(link, PageFlowUtil.filter(rootCustomStylesheetURL));
                }
            }
            else
            {
                /* Add the root themeStylesheet */
                if (coreUrls.getThemeStylesheetURL() != null)
                    F.format(link, PageFlowUtil.filter(coreUrls.getThemeStylesheetURL()));

                /* Add the root customStylesheet */
                if (null != rootCustomStylesheetURL)
                    F.format(link, PageFlowUtil.filter(rootCustomStylesheetURL));
            }
        }

        ResourceURL printStyleURL = new ResourceURL("printStyle.css", ContainerManager.getRoot());
        sb.append("<link href=\"");
        sb.append(filter(printStyleURL));
        sb.append("\" type=\"text/css\" rel=\"stylesheet\" media=\"print\" >\n");

        if (forEmail)
        {
            // mark these stylesheets as included (in case someone else tries)
            sb.append("<script type=\"text/javascript\" language=\"javascript\">\n");
            sb.append("LABKEY.loadedScripts('" + extJsRoot + "/resources/css/ext-all.css','" + theme.getStyleSheet() + "','printStyle.css');\n");
            if (useLESS)
                sb.append("LABKEY.requiresScript('less-1.0.35.js',true);\n");
            sb.append("</script>\n");
        }

        return sb.toString();
    }

    static final String extJsRoot = "ext-3.2.2";
    static final String extDebug = extJsRoot + "/ext-all-debug.js";
    static final String extMin = extJsRoot + "/ext-all.js";
    static final String extBaseDebug = extJsRoot + "/adapter/ext/ext-base-debug.js";
    static final String extBase = extJsRoot + "/adapter/ext/ext-base.js";

    static String[] clientExploded = new String[]
    {
        "clientapi/ExtJsConfig.js",
        "clientapi/ActionURL.js",
        "clientapi/Ajax.js",
        "clientapi/Assay.js",
        "clientapi/Chart.js",
        "clientapi/DataRegion.js",
        "clientapi/Domain.js",
        "clientapi/Experiment.js",
        "clientapi/LongTextEditor.js",
        "clientapi/EditorGridPanel.js",
        "clientapi/Filter.js",
        "clientapi/GridView.js",
        "clientapi/NavTrail.js",
        "clientapi/Query.js",
        "clientapi/ExtendedJsonReader.js",
        "clientapi/Store.js",
        "clientapi/Utils.js",
        "clientapi/WebPart.js",
        "clientapi/QueryWebPart.js",
        "clientapi/Security.js",
        "clientapi/SecurityPolicy.js",
        "clientapi/Specimen.js",
        "clientapi/MultiRequest.js",
        "clientapi/HoverPopup.js",
        "clientapi/Form.js",
        "clientapi/PersistentToolTip.js",
        "clientapi/Message.js",
        "clientapi/FormPanel.js",
        "clientapi/Pipeline.js",
        "clientapi/Portal.js"
    };
    static String clientDebug = "clientapi/clientapi.js";
    static String clientMin = "clientapi/clientapi.min.js";

    public static String extJsRoot()
    {
        return extJsRoot;
    }

    /** scripts are the explicitly included scripts,
     * @param scripts   the scripts that should be explicitly included
     * @param included  the scripts that are implicitly included
     */
    public static void getJavaScriptPaths(List<String> scripts, Set<String> included)
    {
        boolean explodedExt = AppProps.getInstance().isDevMode() && false;
        boolean explodedClient = AppProps.getInstance().isDevMode();

        // LABKEY
        scripts.add("util.js");

        // EXT
        scripts.add(AppProps.getInstance().isDevMode() ? extBaseDebug : extBase);
        if (explodedExt)
        {
            String jsonString = getFileContentsAsString(new File(ModuleLoader.getServletContext().getRealPath("/" + extJsRoot + "/ext.jsb2")));
            JSONObject json = new JSONObject(jsonString);
            Map<String, JSONObject> packages = new HashMap<String, JSONObject>();
            for (JSONObject pkgObject : json.getJSONArray("pkgs").toJSONObjectArray())
            {
                packages.put(pkgObject.getString("file"), pkgObject);
            }
            JSONObject allPackage = packages.get("ext-all.js");
            JSONArray allPackageDeps = allPackage.getJSONArray("pkgDeps");
            for (int i = 0; i < allPackageDeps.length(); i++)
            {
                JSONObject dependency = packages.get(allPackageDeps.getString(i));
                for (JSONObject fileInclude : dependency.getJSONArray("fileIncludes").toJSONObjectArray())
                {
                    scripts.add(extJsRoot + "/" + fileInclude.getString("path") + fileInclude.getString("text"));
                }
            }
        }
        else
            scripts.add(AppProps.getInstance().isDevMode() ? extDebug : extMin);
        scripts.add(extJsRoot + "/ext-patches.js");
        included.add(extDebug);
        included.add(extMin);

        // CLIENT
        for (String e : clientExploded)
        {
            //included.add(e);
            if (explodedClient)
                scripts.add(e);
        }
        if (!explodedClient)
            scripts.add(AppProps.getInstance().isDevMode() ? clientDebug : clientMin);
        included.add(clientDebug);
        included.add(clientMin);

        included.addAll(scripts);
    }
    

    public static String getLabkeyJS()
    {
        String contextPath = AppProps.getInstance().getContextPath();
        String serverHash = getServerSessionHash();

        StringBuilder sb = new StringBuilder();
        sb.append("<script src=\"").append(contextPath).append("/labkey.js?").append(serverHash).append("\" type=\"text/javascript\" language=\"javascript\"></script>\n");
        sb.append("<script type=\"text/javascript\" language=\"javascript\">\n");
        sb.append("LABKEY.init(").append(jsInitObject()).append(");\n");
        sb.append("</script>\n");
        return sb.toString();
    }

    
    public static String getJavaScriptIncludes()
    {
        boolean combinedJS = true;

        String contextPath = AppProps.getInstance().getContextPath();
        String serverHash = getServerSessionHash();

        List<String> scripts = new ArrayList<String>();
        LinkedHashSet<String> includes = new LinkedHashSet<String>();
        getJavaScriptPaths(scripts, includes);

        StringBuilder sb = new StringBuilder();

        if (combinedJS && !AppProps.getInstance().isDevMode())
        {
            sb.append("<script src=\"").append(contextPath).append("/core/combinedJavascript.view?").append(serverHash).append("\" type=\"text/javascript\" language=\"javascript\"></script>\n");
        }
        else
        {
            for (String s : scripts)
                sb.append("<script src=\"").append(contextPath).append("/").append(filter(s)).append("?").append(serverHash).append("\" type=\"text/javascript\" language=\"javascript\"></script>\n");
        }
        sb.append("<script type=\"text/javascript\" language=\"javascript\">\nLABKEY.loadedScripts(");
        String comma = "";
        for (String s : includes)
        {
            sb.append(comma).append(jsString(s));
            comma = ",";
        }
        sb.append(");\n");
        sb.append("</script>\n");
        return sb.toString();
    }


    /** use this version if you don't care which errors are html parsing errors and which are safety warnings */
    public static String validateHtml(String html, Collection<String> errors, boolean scriptAsErrors)
    {
        return validateHtml(html, errors, scriptAsErrors ? null : errors);
    }


    /** validate an html fragment */
    public static String validateHtml(String html, Collection<String> errors, Collection<String> scriptWarnings)
    {
        if (errors.size() > 0 || (null != scriptWarnings && scriptWarnings.size() > 0))
            throw new IllegalArgumentException("empty errors collection expected");

        if (StringUtils.trimToEmpty(html).length() == 0)
            return "";

        // UNDONE: use convertHtmlToDocument() instead of tidy() to avoid double parsing
        String xml = tidy(html, true, errors);
        if (errors.size() > 0)
            return null;

        if (null != scriptWarnings)
        {
            try
            {
                XMLReader parser = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
                parserSetFeature(parser, "http://xml.org/sax/features/namespaces", false);
                parserSetFeature(parser, "http://xml.org/sax/features/namespace-prefixes", false);
                parserSetFeature(parser, "http://xml.org/sax/features/validation", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/dynamic", false);
                parserSetFeature(parser, "http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                parserSetFeature(parser, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/schema", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/schema-full-checking", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/dynamic", false);
                parserSetFeature(parser, "http://apache.org/xml/features/continue-after-fatal-error", false);

                parser.setContentHandler(new ValidateHandler(scriptWarnings));
                parser.parse(new InputSource(new StringReader(xml)));
            }
            catch (UnsupportedEncodingException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
            catch (IOException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
            catch (SAXException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
        }

        if (errors.size() > 0 || (null != scriptWarnings && scriptWarnings.size() > 0))
            return null;
        
        // let's return html not xhtml
        String tidy = tidy(html, false, errors);
        //FIX: 4528: old code searched for "<body>" but the body element can have attributes
        //and Word includes some when saving as HTML (even Filtered HTML).
        int beginOpenBodyIndex = tidy.indexOf("<body");
        int beginCloseBodyIndex = tidy.lastIndexOf("</body>");
        assert beginOpenBodyIndex != -1 && beginCloseBodyIndex != -1: "Tidied HTML did not include a body element!";
        int endOpenBodyIndex = tidy.indexOf('>', beginOpenBodyIndex);
        assert endOpenBodyIndex != -1 : "Could not find closing > of open body element!";

        tidy = tidy.substring(endOpenBodyIndex + 1, beginCloseBodyIndex).trim();
        return tidy;
    }



    static Integer serverHash = null;

    public static JSONObject jsInitObject()
    {
        AppProps props = AppProps.getInstance();
        String contextPath = props.getContextPath();
        JSONObject json = new JSONObject();
        json.put("contextPath", contextPath);
        json.put("imagePath", contextPath + "/_images");
        json.put("extJsRoot", extJsRoot);
        json.put("devMode", props.isDevMode());
        json.put("hash", getServerSessionHash());

        //TODO: these should be passed in by callers
        ViewContext context = HttpView.currentView().getViewContext();
        Container container = context.getContainer();
        User user = HttpView.currentView().getViewContext().getUser();

        JSONObject userProps = new JSONObject();

        userProps.put("id", user.getUserId());
        userProps.put("displayName", user.getDisplayName(user));
        userProps.put("email", user.getEmail());
        userProps.put("phone", user.getPhone());
        userProps.put("sessionid", getSessionId(context.getRequest()));

        userProps.put("canInsert", null != container && container.hasPermission(user, InsertPermission.class));
        userProps.put("canUpdate", null != container && container.hasPermission(user, UpdatePermission.class));
        userProps.put("canUpdateOwn", null != container && container.hasPermission(user, ACL.PERM_UPDATEOWN));
        userProps.put("canDelete", null != container && container.hasPermission(user, DeletePermission.class));
        userProps.put("canDeleteOwn", null != container && container.hasPermission(user, ACL.PERM_DELETEOWN));
        userProps.put("isAdmin", null != container && container.hasPermission(user, AdminPermission.class));
        userProps.put("isSystemAdmin", user.isAdministrator());
        userProps.put("isGuest", user.isGuest());
        json.put("user", userProps);

        if (null != container)
        {
            JSONObject containerProps = new JSONObject();

            containerProps.put("id", container.getId());
            containerProps.put("path", container.getPath());
            containerProps.put("name", container.getName());
            json.put("container", containerProps);
        }

        Container project = (null == container || container.isRoot()) ? null : container.getProject();
        if (null != project)
        {
            JSONObject projectProps = new JSONObject();

            projectProps.put("id", container.getId());
            projectProps.put("path", container.getPath());
            projectProps.put("name", container.getName());
            json.put("project", projectProps);
        }

        json.put("serverName", StringUtils.isNotEmpty(props.getServerName()) ? props.getServerName() : "Labkey Server");
        json.put("versionString", props.getLabkeyVersionString());
        if ("post".equalsIgnoreCase(context.getRequest().getMethod()))
        {
            json.put("postParameters", context.getRequest().getParameterMap());
        }
        json.put("CSRF", CSRFUtil.getExpectedToken(context.getRequest()));

        // Include a few server-generated GUIDs/UUIDs
        json.put("uuids", Arrays.asList(GUID.makeGUID(), GUID.makeGUID(), GUID.makeGUID()));

        return json;
    }

    public static String getServerSessionHash()
    {
        if (null == serverHash)
            serverHash = 0x7fffffff & AppProps.getInstance().getServerSessionGUID().hashCode();
        return Integer.toString(serverHash);
    }


    private static class ValidateHandler extends org.xml.sax.helpers.DefaultHandler
    {
        static HashSet<String> _illegalElements = new HashSet<String>();

        static
        {
            _illegalElements.add("link");
            _illegalElements.add("style");
            _illegalElements.add("script");
            _illegalElements.add("object");
            _illegalElements.add("applet");
            _illegalElements.add("form");
            _illegalElements.add("input");
            _illegalElements.add("button");
            _illegalElements.add("frame");
            _illegalElements.add("frameset");
            _illegalElements.add("iframe");
            _illegalElements.add("embed");
            _illegalElements.add("plaintext");
        }

        static HashSet<String> _illegalAttributes = new HashSet<String>();

        Collection<String> _errors;
        HashSet<String> _reported = new HashSet<String>();


        ValidateHandler(Collection<String> errors)
        {
            _errors = errors;
        }


        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
        {
            String e = qName.toLowerCase();
            if ((e.startsWith("?") || _illegalElements.contains(e)) && !_reported.contains(e))
            {
                _reported.add(e);
                _errors.add("Illegal element <" + qName + ">. For permissions to use this element, contact your system administrator.");
            }

            for (int i = 0; i < attributes.getLength(); i++)
            {
                String a = attributes.getQName(i).toLowerCase();
                String value = attributes.getValue(i).toLowerCase();

                if ((a.startsWith("on") || a.startsWith("behavior")) && !_reported.contains(a))
                {
                    _reported.add(a);
                    _errors.add("Illegal attribute '" + attributes.getQName(i) + "' on element <" + qName + ">.");
                }
                if ("href".equals(a))
                {
                    if (value.indexOf("script") != -1 && value.indexOf("script") < value.indexOf(":") && !_reported.contains("href"))
                    {
                        _reported.add("href");
                        _errors.add("Script is not allowed in 'href' attribute on element <" + qName + ">.");
                    }
                }
                if ("style".equals(a))
                {
                    if ((value.indexOf("behavior") != -1 || value.indexOf("url") != -1 || value.indexOf("expression") != -1) && !_reported.contains("style"))
                    {
                        _reported.add("style");
                        _errors.add("Style attribute cannot contain behaviors, expresssions, or urls. Error on element <" + qName + ">.");
                    }
                }
            }
        }

        @Override
        public void warning(SAXParseException e) throws SAXException
        {
        }

        @Override
        public void error(SAXParseException e) throws SAXException
        {
            _errors.add(e.getMessage());
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException
        {
            _errors.add(e.getMessage());
        }
    }


    //
    // TestCase
    //


    public static class TestCase extends Assert
    {
        @Test
        public void testPhone()
        {
            assertEquals(formatPhoneNo("5551212"), "555-1212");
            assertEquals(formatPhoneNo("2065551212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("12065551212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("206.555.1212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("1-206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1-206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1(206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1 (206)555.1212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("(206)-555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("work (206)555.1212"), "work (206) 555-1212");
            assertEquals(formatPhoneNo("206.555.1212 x0001"), "(206) 555-1212 x0001");
        }


        @Test
        public void testFilter()
        {
            assertEquals(filter("this is a test"), "this is a test");
            assertEquals(filter("<this is a test"), "&lt;this is a test");
            assertEquals(filter("this is a test<"), "this is a test&lt;");
            assertEquals(filter("'t'&his is a test\""), "&#039;t&#039;&amp;his is a test&quot;");
            assertEquals(filter("<>\"&"), "&lt;&gt;&quot;&amp;");
        }
    }


    public static void sendAjaxCompletions(HttpServletResponse response, List<AjaxCompletion> completions) throws IOException
    {
        response.setContentType("text/xml");
        response.setHeader("Cache-Control", "no-store");
        Writer writer = response.getWriter();
        writer.write("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n");
        writer.write("<completions>");
        for (AjaxCompletion completion : completions)
        {
            writer.write("<completion>\n");
            writer.write("    <display>" + filter(completion.getKey()) + "</display>");
            writer.write("    <insert>" + filter(completion.getValue()) + "</insert>");
            writer.write("</completion>\n");
        }
        writer.write("</completions>");
    }


    // Compares two objects even if they're null.
    public static boolean nullSafeEquals(Object o1, Object o2)
    {
        if (null == o1)
            return null == o2;

        return o1.equals(o2);
    }



    //
    //  From PFUtil
    //

    /**
     * Returns a specified <code>UrlProvider</code> interface implementation, for use
     * in writing URLs implemented in other modules.
     *
     * @param inter interface extending UrlProvider
     * @return an implementation of the interface.
     */
    static public <P extends UrlProvider> P urlProvider(Class<P> inter)
    {
        return ModuleLoader.getInstance().getUrlProvider(inter);
    }

    static private String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    static public <T> String strSelect(String selectName, Map<T,String> map, T current)
    {
        return strSelect(selectName, map.keySet(), map.values(), current);
    }

    static public String strSelect(String selectName, Collection<?> values, Collection<String> labels, Object current)
    {
        if (values.size() != labels.size())
            throw new IllegalArgumentException();
        StringBuilder ret = new StringBuilder();
        ret.append("<select name=\"");
        ret.append(h(selectName));
        ret.append("\">");
        boolean found = false;
        Iterator itValue;
        Iterator<String> itLabel;
        for (itValue  = values.iterator(), itLabel = labels.iterator();
             itValue.hasNext() && itLabel.hasNext();)
        {
            Object value = itValue.next();
            String label = itLabel.next();
            boolean selected = !found && ObjectUtils.equals(current, value);
            ret.append("\n<option value=\"");
            ret.append(h(value));
            ret.append("\"");
            if (selected)
            {
                ret.append(" SELECTED");
                found = true;
            }
            ret.append(">");
            ret.append(h(label));
            ret.append("</option>");
        }
        ret.append("</select>");
        return ret.toString();
    }

    static public void close(Closeable closeable)
    {
        if (closeable == null)
            return;
        try
        {
            closeable.close();
        }
        catch (IOException e)
        {
            _log.error("Error in close", e);
        }
    }

    static public String getResourceAsString(Class clazz, String resource)
    {
        InputStream is = null;
        try
        {
            is = clazz.getResourceAsStream(resource);
            if (is == null)
                return null;
            return PageFlowUtil.getStreamContentsAsString(is);
        }
        finally
        {
            close(is);
        }
    }

    static public String _gif()
    {
        return _gif(1, 1);
    }

    static public String _gif(int height, int width)
    {
        StringBuilder ret = new StringBuilder();
        ret.append("<img src=\"");
        ret.append(AppProps.getInstance().getContextPath());
        ret.append("/_.gif\" height=\"");
        ret.append(height);
        ret.append("\" width=\"");
        ret.append(width);
        ret.append("\">");
        return ret.toString();
    }

    static public String strCheckbox(String name, boolean checked)
    {
        return strCheckbox(name, null, checked);
    }
    
    static public String strCheckbox(String name, String value, boolean checked)
    {
        StringBuilder out = new StringBuilder();
        String htmlName = h(name);
        
        out.append("<input type=\"checkbox\" name=\"");
        out.append(htmlName);
        out.append("\"");
        if (null != value)
        {
            out.append(" value=\"");
            out.append(h(value));
            out.append("\"");
        }
        if (checked)
        {
            out.append(" checked");
        }
        out.append(">");
        out.append("<input type=\"hidden\" name=\"");
        out.append(SpringActionController.FIELD_MARKER);
        out.append(htmlName);
        out.append("\">");
        return out.toString();
    }


    /** CONSOLIDATE ALL .lastFilter handling **/

    public static void saveLastFilter()
    {
        ViewContext context = HttpView.getRootContext();
        saveLastFilter(context, context.getActionURL(), "");
    }


    // scope is not fully supported
    public static void saveLastFilter(ViewContext context, ActionURL url, String scope)
    {
        boolean lastFilter = ColumnInfo.booleanFromString(url.getParameter(scope + DataRegion.LAST_FILTER_PARAM));
        if (lastFilter)
            return;
        ActionURL clone = url.clone();
        clone.deleteParameter(scope + DataRegion.LAST_FILTER_PARAM);
        HttpSession session = context.getRequest().getSession(false);
        // We should already have a session at this point, but check anyway - see bug #7761
        if (session != null)
        {
            session.setAttribute(url.getPath() + "#" + scope + DataRegion.LAST_FILTER_PARAM, clone);
        }
    }

    public static ActionURL getLastFilter(ViewContext context, ActionURL url)
    {
        ActionURL ret = (ActionURL) context.getSession().getAttribute(url.getPath() + "#" + DataRegion.LAST_FILTER_PARAM);
        return ret != null ? ret.clone() : url.clone();
    }

    public static ActionURL addLastFilterParameter(ActionURL url)
    {
        return url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }

    
    public static ActionURL addLastFilterParameter(ActionURL url, String scope)
    {
        return url.addParameter(scope + DataRegion.LAST_FILTER_PARAM, "true");
    }

    public static String getSessionId(HttpServletRequest request)
    {
        return WebUtils.getSessionId(request);
    }

    /**
     * Stream the text back to the browser as a PNG
     */
    public static void streamTextAsImage(HttpServletResponse response, String text, int width, int height, Color textColor) throws IOException
    {
        Font font = new Font("SansSerif", Font.PLAIN, 12);

        BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = buffer.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(textColor);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        int fontHeight = metrics.getHeight();
        int spaceWidth = metrics.stringWidth(" ");

        int x = 5;
        int y = fontHeight + 5;

        StringTokenizer st = new StringTokenizer(text, " ");
        // Line wrap to fit
        while (st.hasMoreTokens())
        {
            String token = st.nextToken();
            int tokenWidth = metrics.stringWidth(token);
            if (x != 5 && tokenWidth + x > width)
            {
                x = 5;
                y += fontHeight;
            }
            g2.drawString(token, x, y);
            x += tokenWidth + spaceWidth;
        }

        response.setContentType("image/png");
        EncoderUtil.writeBufferedImage(buffer, ImageFormat.PNG, response.getOutputStream());
    }
}
