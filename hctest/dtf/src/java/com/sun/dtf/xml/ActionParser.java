package com.sun.dtf.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.xmlrpc.parser.TypeParser;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.function.Function;
import com.sun.dtf.actions.reference.RefWrapper;
import com.sun.dtf.actions.reference.Referencable;
import com.sun.dtf.actions.util.CDATA;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.references.References;


public class ActionParser implements TypeParser {
    
    private static DTFLogger _logger = DTFLogger.getLogger(ActionParser.class);

    private Action _root = null;
    private Action _current = null;
    
    private Stack _stack = new Stack();
    private Stack _charStack = new Stack();
  
    private boolean processRefs = true;
    private boolean processFuncs = true;
    
    private static ArrayList _pkgs = null;
    static { 
        init();
    }
    
    public ActionParser() { 
        this(true,true);
    }
    
    public ActionParser(boolean processRefs, boolean processFuncs) { 
        this.processRefs = processRefs;
        this.processFuncs = processFuncs; 
    }
   
    private Locator _locator = null;

    public Object getResult() {
        return _root;
    }
    
    public Locator getLocator() { return _locator; } 
      
    public void characters(char[] ch, int start, int length)
            throws SAXException {
       ((StringBuffer)_charStack.peek()).append(ch,start,length);
    }

    public void endDocument() throws SAXException { }

    public void endElement(String uri, String localName, String name)
            throws SAXException {
        
        if (name.equals("value")) 
            return;
       
        StringBuffer buff = 
              (_charStack.size() != 0 ? (StringBuffer) _charStack.pop() : null);
        Action action = (_stack.size() != 0 ? (Action) _stack.pop() : null);
        
        if (buff != null && action instanceof CDATA)
            ((CDATA) action).setCDATA(buff.toString());
    }

    public void endPrefixMapping(String prefix) throws SAXException { }
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException { }
    public void processingInstruction(String target, String data) throws SAXException { }
    public void setDocumentLocator(Locator locator) { _locator = locator; }
    public void skippedEntity(String name) throws SAXException { }
    public void startDocument() throws SAXException { }
 
    /*
     * Cache available packages once for reference.
     */
    private static void init() { 
        Package pkg = Action.class.getPackage();
       _pkgs = getSubPackages(pkg.getName());
    }
    
    private Action getAction(String name) throws SAXException { 
        // 1st look up class based on given class name.
        Class actionClass = null;
        String actionPkg = Action.class.getPackage().getName();
        
        try {
            actionClass = Class.forName(name);
        } catch (ClassNotFoundException e) {
            // 2nd look up this class under com.sun.dtf.actions package
            if (name.indexOf(".") == -1) { 
                try {
                    actionClass = Class.forName(actionPkg + "." + name);
                } catch (ClassNotFoundException e1) { 
                    name = capitalize(name);
                    // 3rd look up this class under com.sun.dtf.actions.* package
                    for(int i = 0; i < _pkgs.size(); i++) { 
                        String pkgName = (String)_pkgs.get(i); 
                        try {
                            actionClass = Class.forName(pkgName + "." + name);
                            break;
                        } catch (ClassNotFoundException e3) { }
                    }
                }
            } 
        }
       
        if (actionClass == null) 
            throw new SAXException("Class not found [" + name + "] under " +
                                   actionPkg);
                
        try {
            Object obj = actionClass.newInstance();
            return (Action)obj;
        } catch (InstantiationException e) {
            throw new SAXException("InstantiationException error.",e);
        } catch (IllegalAccessException e) {
            throw new SAXException("IllegalAccessException error.",e);
        }
    }

    private static ArrayList getSubPackages(String packageName) {
        ArrayList packages = new ArrayList();

        String[] cp = System.getProperty("java.class.path").split(
                "" + File.pathSeparatorChar);

        for (int i = 0; i < cp.length; i++) {
            String jarName = cp[i];
            packageName = packageName.replaceAll("\\.", "/");

            if (jarName.endsWith(".jar")) {
                try {
                    JarInputStream jarFile = new JarInputStream(
                            new FileInputStream(jarName));
                    JarEntry jarEntry;

                    while (jarFile != null) {
                        jarEntry = jarFile.getNextJarEntry();

                        if (jarEntry == null)
                            break;

                        if (jarEntry.getName().startsWith(packageName)
                                && jarEntry.isDirectory()) {
                            String name = jarEntry.getName().replaceAll("/",
                                    "\\.");
                            name = name.substring(0, name.length() - 1);
                            packages.add(name);
                        }
                    }
                } catch (IOException ignore) { }
            }
        }

        return packages;
    }

    public static String capitalize(String string) {
        string = string.toLowerCase();
        String firstLetter = string.substring(0, 1).toUpperCase();
        return firstLetter + string.substring(1);
    }
    
    public void startElement(String uri, 
                             String localName, 
                             String name, 
                             Attributes atts) throws SAXException {
        
        if (name.equals("value")) 
            return;
       
        String classname = atts.getValue("class"); 
       
        if (classname == null)
            classname = name;
        
        _charStack.push(new StringBuffer());
        _current = getAction(classname);
        
        _current.setLine(getLocator().getLineNumber());
        _current.setColumn(getLocator().getColumnNumber());

        Action.getState().setAction(_current);
       
        for (int i = 0; i < atts.getLength(); i++) { 
            String attrName = atts.getQName(i);
            String attrValue = atts.getValue(i);
            Method[] method = _current.getClass().getMethods();

            for (int j = 0; j < method.length; j++) {
                // TODO: later i could implement another way of looking up 
                //       other methods based on types ? 
                // First method to be found will have to do...
                if (method[j].getName().equalsIgnoreCase("set" + attrName)) {
                    Method setMethod = method[j];
                    Class classType = setMethod.getParameterTypes()[0];
                    Object[] args = new Object[1];

                    try {
                        if (classType.equals(String.class)) {
                            args[0] = attrValue;
                        } else if (classType.equals(Integer.TYPE)) {
                            args[0] = new Integer(attrValue);
                        } else if (classType.equals(Boolean.TYPE)) {
                            args[0] = new Boolean(attrValue);
                        } else  if (classType.equals(Long.TYPE)) {
                            args[0] = new Long(attrValue);
                        } else if (classType.equals(Double.TYPE)) {
                            args[0] = new Double(attrValue);
                        } else if (classType.equals(Float.TYPE)) {
                            args[0] = new Float(attrValue);
                        } else if (classType.equals(Short.TYPE)) {
                            args[0] = new Short(attrValue);
                        } 
                    } catch (NumberFormatException e) {
                        throw new SAXException(
                                "Failed to set attrib: " + attrName
                                        + " with value: " + attrValue
                                        + " of type: " + classType, e);
                    }
                    
                    if (args[0] == null) {
                        throw new SAXException("setMethod for class "
                                + _current.getClass()
                                + " unsupported argument type: "
                                + classType);
                    }

                    try {
                        setMethod.invoke(_current, args);
                        break;
                    } catch (IllegalArgumentException e) {
                        throw new SAXException("Error exeuting setter.", e);
                    } catch (IllegalAccessException e) {
                        throw new SAXException("Error exeuting setter.", e);
                    } catch (InvocationTargetException e) {
                        throw new SAXException("Error exeuting setter.", e);
                    }
                }
            }
        }
     
        boolean referencable = false;
        // Reference magic!
        if (_current instanceof Referencable && processRefs) {
            Referencable ref = (Referencable)_current;
            References refs = Action.getState().getReferences();
            try {
                if (ref.isReferencable()) { 
                    referencable = true;
                    if  (!refs.hasReference(ref.getId())) {
                        String id = ref.getId();
                        // refid must be nulled out so this code doesn't do 
                        // so funny business on component side.
                        ref.setId(null);
                        refs.addReference(id, ref);
                    } else 
                        _logger.warn("Not overwriting reference for [" + ref.getId() + "]");
                } else if (ref.isReference()) {
                    _current = new RefWrapper(ref);
                }
            } catch (ParseException e) {
                throw new SAXException("Unable to add reference.",e);
            }
        } else if (_current instanceof Function && processFuncs) { 
            /*
             * Functions are added to the functions lookup mechanism and are 
             * only executed if they're called from a call tag.
             */
            Function function = (Function) _current;
            try {
                Action.getState().getFunctions().addFunction(function.getName(), function);
            } catch (ParseException e) {
                throw new SAXException("Error adding function.",e);
            }
        }
        
        if (_root == null) {
            _root = _current;
            _stack.push(_root);
        } else {
            /*
             * Function are not added to the execution tree.
             * 
             */
            if (!(_current instanceof Function || referencable)) { 
                ((Action)_stack.peek()).addAction(_current);
            }
            
            _stack.push(_current);
        }
    }

    public void startPrefixMapping(String prefix, String uri)
            throws SAXException { }
}
