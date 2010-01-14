package com.sun.dtf.actions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.actions.reference.RefWrapper;
import com.sun.dtf.actions.util.CDATA;
import com.sun.dtf.comm.Comm;
import com.sun.dtf.components.Components;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.RecorderException;
import com.sun.dtf.exception.ResultsException;
import com.sun.dtf.functions.Functions;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.logger.RemoteLogger;
import com.sun.dtf.query.Cursor;
import com.sun.dtf.recorder.Recorder;
import com.sun.dtf.recorder.RecorderBase;
import com.sun.dtf.results.Results;
import com.sun.dtf.results.ResultsBase;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;
import com.sun.dtf.storage.StorageFactory;
import com.sun.dtf.util.StringUtil;
import com.sun.dtf.xml.ActionParser;
import com.sun.dtf.xml.DTFDTDHandler;

abstract public class Action {

    private static DTFLogger _logger = DTFLogger.getLogger(Action.class);
  
    private ArrayList _list = null;
    
    private int line = -1;
    private int column = -1;
   
    public Action() { 
        _list = new ArrayList(5); 
    }
    
    private int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    
    private int getColumn() { return column; }
    public void setColumn(int column) { this.column = column; }
    
    public void addAction(Action action) { 
        _list.add(action); 
    }
    
    public void addActions(List actions) {
        for (int i = 0; i < actions.size(); i++)
            addAction((Action) actions.get(i));
    }

    public boolean hasChildren() { return _list.size() != 0; }
    public ArrayList children() {
        return (ArrayList)_list.clone();
    }
    
    public void clearChildren() { 
        _list.clear();
    }

    protected Action getAction(int index) { 
       Action action = (Action)_list.get(index); 
       
       if (action instanceof RefWrapper) { 
           try {
            action = ((RefWrapper)action).lookupReference();
           } catch (ParseException e) {
               throw new RuntimeException("This shouldn't happen.",e);
           }
       }
       
       return action;
    }
    
    /**
     * DTF's internal instance of function.
     * 
     * @param type
     * @return boolean
     */
    public boolean anInstanceOf(Class type) { 
        return type.isInstance(this);
    }
    
    public ArrayList findActions(Class classType) {
        ArrayList result = new ArrayList();
        for (int i = 0; i < _list.size(); i++) {
            if (((Action)_list.get(i)).anInstanceOf(classType))
                result.add(getAction(i));
        }
        return result;
    }

    public Action findFirstAction(Class classType) { 
        for (int i = 0; i < _list.size(); i++) {
            if (((Action)_list.get(i)).anInstanceOf(classType))
                return getAction(i);
        }
        return null;
    }
    
    public void executeChildren() throws DTFException {
        for (int i = 0; i < _list.size(); i++) {
            getState().setAction((Action) _list.get(i));
            getAction(i).execute();
        }
    }
    
    protected void executeChildrenWithoutStateChange() throws DTFException {
        for (int i = 0; i < _list.size(); i++) {
            getAction(i).execute();
        }
    }
   
    public void executeChildren(Class classType) throws DTFException {
        for (int i = 0; i < _list.size(); i++) {
            if (((Action)_list.get(i)).anInstanceOf(classType)) {
                getState().setAction((Action) _list.get(i));
                getAction(i).execute();
            }
        }
    }
  
    public static DTFState getState() { return ActionState.getInstance().getState(); }
    public static Config getConfig() { return getState().getConfig(); }
    public static Comm getComm() { return getState().getComm(); }
    public static StorageFactory getStorageFactory() { return getState().getStorage(); }
    public static Components getComponents() { return getState().getComponents(); }
    public static Recorder getRecorder() { return getState().getRecorder(); }
    public static Results getResults() { return getState().getResults(); }
    public static Functions getFunctions() { return getState().getFunctions(); } 

    public static void pushRecorder(RecorderBase recorder, String event) throws RecorderException { 
        Recorder rec = new Recorder(recorder,event);
        rec.start();
        rec.setParent(getState().getRecorder());
        getState().setRecorder(rec);
    }
    
    public static void popRecorder() throws RecorderException {
        Recorder rec = getState().getRecorder(); 
        rec.stop();
        getState().setRecorder(rec.getParent());
    }

    public static void pushResults(ResultsBase results) throws ResultsException { 
        Results res = new Results(results);
        res.start();
        res.setParent(getState().getResults());
        getState().setResults(res);
    }
    
    public static void popResults() throws ResultsException {
        Results res = getState().getResults();
        res.stop();
        getState().setResults(res.getParent());
    }
    
    public static void addCursor(String name, Cursor cursor)  {
        getState().getCursors().addCursor(name, cursor);
    }
    
    public static Cursor retCursor(String name) { 
        return getState().getCursors().getCursor(name);
    }
    
    public static String getLocalID() { 
        return getConfig().getProperty(DTFProperties.DTF_NODE_NAME); 
    }
    
    public static String getXMLLocation() { 
        DTFState state = getState();
        
        if (state != null) { 
            Action action = Action.getState().getAction();
            if (action != null) { 
                return " at line: " + action.getLine() +
                       " column: " +  action.getColumn() + 
                       " of script: " + getConfig().getProperty(DTFProperties.DTF_XML_FILENAME); 
        
            } 
        } 
    
        return "";
    }
    
    public static void registerContext(String key, Object value) {
        getState().registerContext(key, value);
    }
    
    public static Object getContext(String key) {
        return getState().getContext(key);
    }
    
    public static void unRegisterContext(String key) { 
        getState().unRegisterContext(key);
    }
   
    public static void registerGlobalContext(String key, Object value) {
        getState().registerGlobalContext(key, value);
    }
    
    public static Object getGlobalContext(String key) {
        return getState().getGlobalContext(key);
    }
    
    public static void unRegisterGlobalContext(String key) { 
        getState().unRegisterGlobalContext(key);
    }
    
    protected int toInt(String property, String value) throws ParseException {
        try { 
            return new Integer(replaceProperties(value)).intValue();
        } catch (NumberFormatException e) { 
            throw new ParseException("Value of property: " + property + " is not an int.",e);
        }
    }

    protected int toInt(String property, String value, int defaultValue) 
              throws ParseException {
        try { 
            value = replaceProperties(value);
            
            if (value == null)
                return defaultValue;
            
            return new Integer(value).intValue();
        } catch (NumberFormatException e) { 
            throw new ParseException("Value of property: " + property + " is not an int.",e);
        }
    }

    protected double toDouble(String property, 
                              String value, 
                              double defaultValue) throws ParseException {
        try { 
            value = replaceProperties(value);
            
            if (value == null) 
                return defaultValue;
            
            return new Double(value).doubleValue();
        } catch (NumberFormatException e) { 
            throw new ParseException("Value of property: " + property + " is not a double.",e);
        }
    }
    
    protected double toDouble(String property, String value) throws ParseException {
        try { 
            return new Double(replaceProperties(value)).doubleValue();
        } catch (NumberFormatException e) { 
            throw new ParseException("Value of property: " + property + " is not a double.",e);
        }
    }
    
    protected boolean toBoolean(String property, String value) throws ParseException {
        try { 
            value = replaceProperties(value);
            return new Boolean(value).booleanValue();
        } catch (NumberFormatException e) { 
            throw new ParseException("Value of property: " + property + " is not a boolean.",e);
        }
    }

    protected long toLong(String property, 
                          String value, 
                          long defaultValue) throws ParseException {
        try { 
            value = replaceProperties(value);
            
            if (value == null) 
                return defaultValue;
            
            return new Long(value).longValue();
        } catch (NumberFormatException e) { 
            throw new ParseException("Value of property: " + property + " is not an long.",e);
        }
    }
    

    protected long toLong(String property, 
                          String value ) throws ParseException {
        try { 
            if (value == null) 
                throw new ParseException("Value of property: " + property + " is null.");

            value = replaceProperties(value);
            
            return new Long(value).longValue();
        } catch (NumberFormatException e) { 
            throw new ParseException("Value of property: " + property + " is not an long.",e);
        }
    }
    
    private static Pattern pattern = Pattern.compile("\\$\\{([^}^$^{]*)\\}");
    public String replaceProperties(String string) throws ParseException {
        
        if (string == null) 
            return null;

        boolean hasMatch = true;
        while (hasMatch) { 
            hasMatch = false;
            Matcher match = pattern.matcher(string);
            
            if (match.find()) { 
                String group = match.group();
                String key = group.substring(2,group.length()-1);
               
                String value = getConfig().getInternalProperty(key);
                 
                if (value == null)
                    value = System.getProperty(key);
                
                if (value != null)  {
                    string = StringUtil.replace(string, group, value);
                    hasMatch = true;
                } else if (getState().replace()) 
                        throw new ParseException("Property [" + key + 
                                                 "] not found.");
            }
        }
        
        return string;
    }
    
    public static String getClassName(Class aClass) { 
        String classname = aClass.getName();
        return classname.substring(classname.lastIndexOf(".")+1,
                                   classname.length());
    }
 
    protected Hashtable getAttribs(Class actionClass) { 
        Hashtable attribs = null;
        Hashtable result = new Hashtable();
            
        try {
            attribs = DTFDTDHandler.getInstance().getAttributes(
                                                     getClassName(actionClass));
        } catch (DTFException e) {
            throw new RuntimeException(e);
        }
        Enumeration enumeration = attribs.keys();
      
        boolean oneappend = false;
        while (enumeration.hasMoreElements()) {
            String key = (String)enumeration.nextElement();
            Class[] args = new Class[0];
           

            try {
                Method getter = this.getClass().
                           getMethod("get" + ActionParser.capitalize(key),args);
                Object obj = getter.invoke(this, (Object[])args);
               
                if (obj != null)
                    result.put(key, obj);
                
                oneappend = true;
            } catch (SecurityException e) {
                _logger.warn("Error getting attribute: " + key + ".",e);
            } catch (NoSuchMethodException e) {
                _logger.warn("Error getting attribute: " + key + ".",e);
            } catch (IllegalArgumentException e) {
                _logger.warn("Error getting attribute: " + key + ".",e);
            } catch (IllegalAccessException e) {
                _logger.warn("Error getting attribute: " + key + ".",e);
            } catch (InvocationTargetException e) {
                _logger.warn("Error getting attribute: " + key + ".",e);
            }
        }
        
        return result;
    }
    
    private String TO_STRING_SEPERATOR = ",";
    public String toString() {
        StringBuffer result = new StringBuffer();
        Class actionClass = this.getClass();
        Hashtable attribs = getAttribs(actionClass);
        Enumeration enumeration = attribs.keys();

        result.append(getClassName(actionClass) + " {");
        boolean oneappend = false;
        while (enumeration.hasMoreElements()) {
            String key = (String)enumeration.nextElement();
            String value = attribs.get(key).toString();
            result.append(key + "=" + value + TO_STRING_SEPERATOR);
            oneappend = true;
        }
        
        if (this instanceof CDATA)
            try {
                result.append("CDATA" + "=" + ((CDATA)this).getCDATA() + 
                              TO_STRING_SEPERATOR);
            } catch (ParseException e) {
                _logger.warn("Error getting attribute: CDATA.",e);
            }
      
        if (oneappend)  {
            result.replace(result.length()-1,result.length(),"");
        }
        result.append("}");
        
        return result.toString();
    }
    
    public URI parseURI(String uri) throws ParseException { 
        if (uri == null) return null;
        
        try {
            return new URI(replaceProperties(uri));
        } catch (URISyntaxException e) {
            throw new ParseException("Bad URI syntax.",e);
        }
    }
    
    public static DTFLogger getLogger() { 
        String name = new Throwable().getStackTrace()[1].getClassName(); 
        return DTFLogger.getLogger(name);
    } 
    
    public static RemoteLogger getRemoteLogger() { 
        return RemoteLogger.getInstance();
    }
    
    /**
     * execute the behaviour of this action. 
     *
     */
    abstract public void execute() throws DTFException;
}
