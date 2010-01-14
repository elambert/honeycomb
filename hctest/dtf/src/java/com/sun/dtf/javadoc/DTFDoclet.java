package com.sun.dtf.javadoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import com.sun.dtf.xml.DTDHandler;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;
import com.wutka.dtd.DTDAny;
import com.wutka.dtd.DTDCardinal;
import com.wutka.dtd.DTDChoice;
import com.wutka.dtd.DTDElement;
import com.wutka.dtd.DTDEmpty;
import com.wutka.dtd.DTDItem;
import com.wutka.dtd.DTDMixed;
import com.wutka.dtd.DTDName;
import com.wutka.dtd.DTDPCData;
import com.wutka.dtd.DTDSequence;

/**
 * This class generates the DTF XML documentation directly from the Java source
 * code of the available tags. It also uses the DTF DTD to construct additional
 * information about each of the tags and its usage.
 * 
 * @author Rodney Gomes
 */
public class DTFDoclet {
    
    private static final String TAGS_DIRECOTRY      = "tags";
    
    public static final String DTF_TAG              = "dtf.tag";
    public static final String DTF_TAG_DESC         = "dtf.tag.desc";

    public static final String DTF_AUTHOR           = "dtf.author";
    public static final String DTF_SINCE            = "dtf.since";
    
    public static final String DTF_EVENT            = "dtf.event";
    public static final String DTF_EVENT_ATTR       = "dtf.event.attr";
    public static final String DTF_EVENT_ATTR_DESC  = "dtf.event.attr.desc";

    public static final String DTF_ATTR             = "dtf.attr";
    public static final String DTF_ATTR_DESC        = "dtf.attr.desc";

    private static final String DTF_TAG_EXAMPLE     = "dtf.tag.example";

    public static final String DTF_LINK             = "dtf.link";
    
    public static boolean start(RootDoc root) throws Exception {
        ClassDoc[] classes = root.classes();
        HashMap packages = new HashMap();
        ArrayList packagenames = new ArrayList();
        String[][] options = root.options();
        
        String destination = null;
        String dtd = null;
        
        for(int i = 0; i < options.length; i++) { 
            if (options[i][0].equals("-d")) {
                destination = options[i][1];
            }

            if (options[i][0].equals("-dtfdtd")) {
                dtd = options[i][1];
            }
        }
        
        if (destination == null) {
            throw new IllegalArgumentException("-d option needs to be specified");
        }

        if (dtd == null) {
            throw new IllegalArgumentException("-dtfdtd option needs to be specified");
        }

        String tagdir = destination + File.separatorChar + TAGS_DIRECOTRY;
        new File(tagdir).mkdirs();
        PrintStream psIndex = createFile(destination + File.separatorChar + "index.html");
        psIndex.print("<html><head></head><body>");
       
        /*
         * 1st phase
         * 
         * Filter out non DTF related JavaDocs and also gather the list of 
         * packages that contain different DTF Actions. Create a TOC for the 
         * whole document.
         */
        for (int i = 0; i < classes.length; ++i) {
            ClassDoc classdoc = classes[i];
          
            /*
             * Identify which ones have DTF XML tags
             */
            if (classdoc.tags(DTF_TAG).length != 0) {
                String classname = classdoc.qualifiedName();
                String packagename = classname.substring(0, classname.lastIndexOf("."));
                
                if (!packages.containsKey(packagename)) {
                    packages.put(packagename, new ArrayList());
                    packagenames.add(packagename);
                }
                
                ((ArrayList)packages.get(packagename)).add(classdoc);
                               
            }
        }
        
        /*
         * Link to the root element of the DTF XML as a starting poit for the 
         * testcase writing.
         */
        Collections.sort(packagenames);
        Iterator iter = packagenames.iterator();
        
        psIndex.print("<h2>Packages</h2>");
        psIndex.print("<ul>");
        while (iter.hasNext()) {
            String pname = (String) iter.next();
            ArrayList tags = (ArrayList)packages.get(pname);
            psIndex.print("<li>" +  pname + "</li>");
            
            psIndex.print("<ul>");
            for (int i = 0; i < tags.size(); i++) { 
                ClassDoc classdoc = (ClassDoc)tags.get(i);
                String tagname = classdoc.name().toLowerCase();
            
                psIndex.print("<a href='" + TAGS_DIRECOTRY + "/" + 
                              tagname + ".html'>" + tagname + "</a> ");
            }
            psIndex.print("</ul>");
        }
        psIndex.print("</ul>");
       
        /*
         * 2nd phase 
         * 
         * Start putting together each of the package documentation files so 
         * that there is the index.html that references the other packag files
         * by name and each of those just have the DTF XML documentation 
         * available within them.
         */
        iter = packagenames.iterator();

        FileInputStream fis;
        try {
            fis = new FileInputStream(dtd);
        } catch (FileNotFoundException e) {
            throw new Exception("Error accessing DTD.",e);
        }
        DTDHandler dtfDTD = new DTDHandler(fis);
        
        while (iter.hasNext()) {
            String pname = (String) iter.next();
            ArrayList tags = (ArrayList)packages.get(pname);
            info("Package " + pname);
            for (int i = 0; i < tags.size(); i++) { 
                ClassDoc classdoc = (ClassDoc)tags.get(i);
                String tagname = classdoc.name().toLowerCase();
                PrintStream ps = createFile(tagdir + File.separatorChar + 
                                            tagname + ".html");
                
                info("Tag " + tagname);
                
                ps.print("<html><head></head><body>");
                ps.print("<a href='javascript:history.back()'>Back</a> ");
                ps.print("<a href='../index.html'>Top</a>");
                ps.print("<dt><h3>" + classdoc.name() + "</h3></dt>");
                
                /*
                 * Description
                 */
                Tag[] descriptions = classdoc.tags(DTF_TAG_DESC);
                if (descriptions.length != 0) { 
                    for (int d = 0; d < descriptions.length; d++) { 
                        ps.print("<dd><para align='justify'>" + 
                                 treatTag(descriptions[d]) + "</para></dd>");
                    }
                }
                
                /*
                 * Authors
                 */
                Tag[] authors = classdoc.tags(DTF_AUTHOR);
                if (authors.length != 0) { 
                    if (authors.length == 1)
                        ps.print("<br/><dt><b>Author </b></dt>");
                    else
                        ps.print("<br/><dt><b>Authors </b></dt>");
                   
                    ps.print("<dd>");
                    for (int a = 0; a < authors.length; a++) { 
                        if (a == authors.length - 1)
                            ps.print(authors[a].text());
                        else
                            ps.print(authors[a].text() + ", ");
                    }
                    ps.print("</dd>");
                }
                
                /*
                 * Events
                 */
                Tag[] events = classdoc.tags(DTF_EVENT);
                Tag[] eventsAttrs = classdoc.tags(DTF_EVENT_ATTR);
                Tag[] eventsDesc= classdoc.tags(DTF_EVENT_ATTR_DESC);
                
                if (eventsAttrs.length != eventsDesc.length) { 
                    // XXX: throw an exception
                }
               
                if (eventsAttrs.length != 0) {
                    HashMap eventMap = new HashMap();

                    /*
                     * Construct all the relations between events and their 
                     * attributes.
                     */
                    for (int a = 0; a < events.length; a++) { 
                        String[] eventNames = events[a].text().trim().split(" ");
                       
                        for (int e = 0; e < eventNames.length; e++) { 
                            if (!eventMap.containsKey(eventNames[e])) {
                                eventMap.put(eventNames[e], new HashMap());
                            }

                            String attrName = eventsAttrs[a].text();
                            String attrDesc = treatTag(eventsDesc[a]);
                           
                            ((HashMap)eventMap.get(eventNames[e])).put(attrName, attrDesc);
                        }
                    }
                    
                    ps.print("<br/><dt><b>Events</b></dt>");
                    Iterator eventIter = eventMap.keySet().iterator();
                    
                    String eventName = null;
                    while (eventIter.hasNext()) { 
                        eventName = (String) eventIter.next();
                        HashMap attribs = (HashMap) eventMap.get(eventName);
                        ps.print("<dd><table border=1>" + "<CAPTION><b>" + 
                                 eventName + "</b> Event</CAPTION>");
                        Iterator attribIter = attribs.keySet().iterator();
                        while (attribIter.hasNext()) { 
                            String name = (String) attribIter.next();
                            String desc = (String) attribs.get(name);
                            ps.print("<tr valign='top'><td width='120px'><b>" +
                                     name + "</b></td>");
                            ps.print("<td><p align='justify'>" + desc + "</p></td></tr>");
                        }
                        ps.print("</dd></table><br/>");
                    }
                }
                
                /*
                 * Attributes
                 */
                HashMap attrs = new HashMap();
                ClassDoc aux = classdoc;
                while (aux != null) { 
                    FieldDoc[] fieldDocs = aux.fields();
                    for (int f = 0; f < fieldDocs.length; f++) {
                        attrs.put(fieldDocs[f].name(), fieldDocs[f]);
                    }
                    aux = aux.superclass();
                }
                
                DTDElement element = (DTDElement) dtfDTD.getElements().get(tagname);
                if (element == null) {
                    throw new Exception("Unable to find element [" + tagname + "]");
                }

                Enumeration keys = element.attributes.keys();
                
                boolean someopt = false;
                StringBuffer opt = new StringBuffer();
                
                boolean somereq = false;
                StringBuffer req = new StringBuffer();
                
                opt.append("<br/><dt><b>Optional Attributes</b></dt>");
                opt.append("<dd><br/><table border=1>");

                req.append("<br/><dt><b>Required Attributes</b></dt>");
                req.append("<dd><br/><table border=1>");
                
                while (keys.hasMoreElements()) {
                    String aName = (String) keys.nextElement();
                    StringBuffer which = null;
                    
                    if (dtfDTD.isAttributeRequired(aName, element.getName())) {
                        which = req;
                        somereq = true;
                    } else {
                        which = opt;
                        someopt = true;
                    }
                   
                    which.append("<tr valign='top'>");
                    which.append("<td width='100px'><b>" + aName + "</b></td>");
                    
                    FieldDoc doc = (FieldDoc)attrs.get(aName);
                    if (doc != null) { 
                        Tag[] descs = doc.tags(DTF_ATTR_DESC);
                                
                        if (descs.length != 0) {
                            which.append("<td><p align='justify'>" + 
                                         treatTag(descs[0]) + "</p></td>");
                        }
                    }
                    which.append("</tr>");
                }

                if (someopt) { 
                    ps.print(opt + "</table></dd>");
                }

                if (somereq) { 
                    ps.print(req + "</table></dd>");
                }

                /*
                 * Child tags
                 */
                if (!(element.getContent() instanceof DTDEmpty)) { 
                    ps.print("<br/><dt><b>Child Tags</b><dt><br/><dd>");
                    ps.print(processChildren(element.getContent()));
                    ps.print("</dd>");
                }

                /*
                 * Examples
                 */
                Tag[] examples = classdoc.tags(DTF_TAG_EXAMPLE);
               
                if (examples.length != 0) { 
                    ps.print("<br/><dt><b>Usage Examples</b></dt>");
        
                    for (int e = 0; e < examples.length; e++) { 
                        Tag example = examples[e];
                        try { 
                            String text = example.text().trim();
                            
                            if (text.length() != 0) {
                                ps.print("<br/><dd><b>Example #" + (e+1) + "</b></dd>");
                                ps.print("<dd><pre>" + treatXML(text) + "</pre></dd>");
                            }
                        } catch (Exception exc) { 
                            throw new Exception("Error handling example #" + 
                                                (e+1) + " of tag " + tagname, exc);
                        }
                    }
                }
                
                ps.print("</body></html>");
                ps.close();
            }
        }
        psIndex.print("</ul></body></html>");
        psIndex.close();
      
        return true;
    }
    
    public static PrintStream createFile(String filename) throws Exception { 
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            throw new Exception("Unable to create destination folders.",e);
        }
        
        return new PrintStream(fos);
    }
    
    public static String treatTag(Tag tag) { 
        StringBuffer result = new StringBuffer();
        
        Tag[] tags = tag.inlineTags();
        
        for (int i = 0; i < tags.length; i++) { 
            Tag aux = tags[i];
            if (aux.name().equalsIgnoreCase("text")) { 
                result.append(aux.text());
            } else if (aux.name().equals("@"+DTF_LINK)) { 
                result.append("<a href='" + aux.text().trim().toLowerCase() + 
                              ".html'>" + aux.text() + "</a>");
            }
        }
        
        return result.toString();
    }

    public static String treatXML(String string) throws Exception {
        try { 
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new ByteArrayInputStream(string.getBytes()));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            serialize(doc,baos);
            return baos.toString().replaceAll("<","&lt;").replaceAll(">", "&gt;");
        } catch (Exception e) { 
            throw new Exception("Error processing XML node.",e);
        }
    }
    
    public static void serialize(Document doc, OutputStream out) throws Exception {
        TransformerFactory tfactory = TransformerFactory.newInstance();
        Transformer serializer;
        try {
            serializer = tfactory.newTransformer();
            serializer.setOutputProperty(OutputKeys.INDENT, "yes");
            serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "5");
            serializer.transform(new DOMSource(doc), new StreamResult(out));
        } catch (TransformerException e) {
            throw new Exception("Error processing XML node.",e);
        }
    }
    
    public static String createLink(String tagname) { 
        return "<a href='" + tagname + ".html'>" + tagname + "</a>"; 
    }
   
    public static String processChildren(DTDItem item) {
        StringBuffer result = new StringBuffer();
        
        if (item instanceof DTDName) {
            String tagname = ((DTDName)item).value;  
            result.append(createLink(tagname));
        } else if (item instanceof DTDAny) { 
            result.append("ANY");
        } else if (item instanceof DTDEmpty) {
            result.append("NONE");
        } else if (item instanceof DTDPCData) {
            result.append("TEXT");
        } else if (item instanceof DTDChoice) { 
            DTDChoice choice = (DTDChoice) item;
            DTDCardinal cardinal = choice.getCardinal();
            DTDItem[] items = choice.getItems();

            result.append("(");
            for (int i = 0; i < items.length; i++) {
                result.append(processChildren(items[i]) + " | ");
            }
            
            if (items.length != 0)
                result = result.replace(result.length() - " | ".length(),
                                        result.length(),
                                        "");
            
            result.append(")");
            
            if (cardinal.equals(DTDCardinal.ONEMANY))
                result.append("+");
            else if (cardinal.equals(DTDCardinal.ZEROMANY))
                result.append("*");
            else if (cardinal.equals(DTDCardinal.OPTIONAL))
                result.append("?");
        } else if (item instanceof DTDSequence) {
            DTDSequence sequence = (DTDSequence) item;
            DTDCardinal cardinal = sequence.getCardinal();
            DTDItem[] items = sequence.getItems();

            result.append("(");
            for (int i = 0; i < items.length; i++) {
                result.append(processChildren(items[i]) + " . ");
            }
            
            if (items.length != 0)
                result = result.replace(result.length() - " . ".length(),
                                        result.length(),
                                        "");
            
            result.append(")");

            if (cardinal.equals(DTDCardinal.ONEMANY))
                result.append("+");
            else if (cardinal.equals(DTDCardinal.ZEROMANY))
                result.append("*");
            else if (cardinal.equals(DTDCardinal.OPTIONAL))
                result.append("?");
        } else if (item instanceof DTDMixed) {
            DTDMixed mixed = (DTDMixed) item;
            DTDCardinal cardinal = mixed.getCardinal();
            DTDItem[] items = mixed.getItems();

            if (cardinal.equals(DTDCardinal.ONEMANY))
                result.append("+");
            else if (cardinal.equals(DTDCardinal.ZEROMANY))
                result.append("*");
            else if (cardinal.equals(DTDCardinal.OPTIONAL))
                result.append("?");
            
            for (int i = 0; i < items.length; i++)
                result.append(processChildren(items[i]));
        }

        return result.toString();
    }
    
    public static int optionLength(String option) {
        
        if (option.equals("-d"))
            return 2;
        
        if (option.equals("-dtfdtd")) 
            return 2;
        
        return 0;
    }

    public static boolean validOptions(String options[][],
                                       DocErrorReporter reporter) {
//        boolean foundTagOption = false;
//        for (int i = 0; i < options.length; i++) {
//            String[] opt = options[i];
//            if (opt[0].equals("-tag")) {
//                if (foundTagOption) {
//                    reporter.printError("Only one -d option allowed.");
//                    return false;
//                } else {
//                    foundTagOption = true;
//                }
//            }
//        }
//        if (!foundTagOption) {
//            reporter.printError("Usage: javadoc -d destination");
//        }
//        return foundTagOption;
        
        return true;
    }
    
    public static void info(String message) { 
        System.out.println(message);
    }
    
    public static void error(String message) { 
        System.err.println(message);
    }

    public static void warn(String message) { 
        System.out.println("WARN: " + message);
    }

    
}
