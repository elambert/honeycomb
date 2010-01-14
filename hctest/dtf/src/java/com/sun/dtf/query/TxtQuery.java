package com.sun.dtf.query;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.conditionals.And;
import com.sun.dtf.actions.conditionals.Condition;
import com.sun.dtf.actions.conditionals.Eq;
import com.sun.dtf.actions.conditionals.False;
import com.sun.dtf.actions.conditionals.Gt;
import com.sun.dtf.actions.conditionals.Lt;
import com.sun.dtf.actions.conditionals.Neq;
import com.sun.dtf.actions.conditionals.Or;
import com.sun.dtf.actions.conditionals.True;
import com.sun.dtf.actions.event.Field;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.QueryException;
import com.sun.dtf.exception.StorageException;


public class TxtQuery implements QueryIntf {
    
    private BufferedReader _br = null;
    private URI _uri = null;
    private String _event = null;
    private String _property = null;
    private Condition _constraints = null;
    private ArrayList _fields = null;
    private ArrayList _fieldNames = null;
    
    public void close() throws QueryException {
        try {
            _br.close();
        } catch (IOException e) {
            throw new QueryException("Error closing filehandle.",e);
        }
    }

    public HashMap next(boolean recycle) throws QueryException {
        return readNext(recycle);
    }
    
    private Condition process(Condition cond, Properties props) throws QueryException, ParseException { 
        Condition conditional = null;
        
        if (cond instanceof And) { 
            conditional = new And();
            ArrayList children = cond.children();
            for (int i = 0; i < cond.children().size(); i++) {
                conditional.addAction(process((Condition)children.get(i),props));
            }
        } else if (cond instanceof Or) { 
            conditional = new Or();
            ArrayList children = cond.children();
            for (int i = 0; i < cond.children().size(); i++) {
                conditional.addAction(process((Condition)children.get(i),props));
            }
        } else {  
            String field = null; 
            String value = null;
          
            if (props.containsKey(_event + "." + cond.getOp1())) { 
                field = cond.getOp1();
                value = cond.getOp2();
            } else if (props.containsKey(_event + "." + cond.getOp2())) { 
                field = cond.getOp2();
                value = cond.getOp1();
            } else  { 
                if (cond.getNullable()) {
                    /*
                     * None of the operators exist therefore if this is a nullable
                     * element then we can just have always true condition because
                     * the field is null logically :)
                     */
                    return new True();
                } else
                    return new False();
            }
            
            String fValue = props.getProperty(_event + "." + field);
            
            if (fValue == null) 
                throw new QueryException("Constraint field [" + field + 
                                         "] does not exist in results.");
            
            if (cond instanceof Eq) {
                conditional = new Eq();
                conditional.setOp1(fValue);
                conditional.setOp2(value);
            } else if (cond instanceof Neq) {
                conditional = new Neq();
                conditional.setOp1(fValue);
                conditional.setOp2(value);
            } else if (cond instanceof Lt)  {
                conditional = new Lt();
                conditional.setOp1(fValue);
                conditional.setOp2(value);
            } else if (cond instanceof Gt)  {
                conditional = new Gt();
                conditional.setOp1(fValue);
                conditional.setOp2(value);
            } 
            
            if (conditional == null)
                throw new QueryException("Uknown conditional: " + 
                        cond.getClass());
        }
        
        return conditional;
    }
    
    private synchronized HashMap readNext(boolean recycle) throws QueryException { 
        StringBuffer sb = new StringBuffer();
       
        try { 
            String line = _br.readLine();
            
            while (line != null) { 
                /*
                 * Read a block of properties since we know that TXTRecorder
                 * separates events by an empty line with a new line.
                 */
                sb = new StringBuffer();
                while ((line != null) && (!line.trim().equals(""))) {
                    sb.append(line + "\n");
                    line = _br.readLine();
                }
               
                ByteArrayInputStream bais = new ByteArrayInputStream(sb.toString().getBytes());
                Properties props = new Properties();
                props.load(bais);
               
                /*
                 * Skip events that are not of the type specified. The easiest
                 * way to do this is to look for the default start property that
                 * would exist for every event recorded with the DTF event 
                 * recording framework.
                 */
                if (props.getProperty(_event + ".stop") == null)  {
                    line = _br.readLine();
                    continue;
                }
               
                if (_constraints != null) { 
                    Condition cond = process(_constraints, props);
                    if (!cond.evaluate()) {
                        line = _br.readLine();
                        continue;
                    }
                }
                
                /*
                 * If we get here without having failed to meet the contraints
                 * then the current event is the one we should return.
                 */
                HashMap result = new HashMap();
                Iterator keys = props.keySet().iterator();
                while (keys.hasNext())  { 
                    String key = (String)keys.next();
                    String value = props.getProperty(key);
                    String keyString = key.toLowerCase().replaceFirst(_event + ".","");
                    
                    if (_fieldNames == null || _fieldNames.contains(keyString)) {
                        /*
                         * All results fields are store in the result attribute
                         */
                        result.put(_property + "." + keyString,value);
                    }
                }
                return result;
            }
            
            if (recycle) { 
                _br.close();
                open(_uri, _fields, _constraints, _event, _property);
                return next(false);
            }
            
            /*
             * If we got this far and have nothing to return then we failed to 
             * find an event that matches the requested query.
             */
            return null;
        } catch (IOException e) { 
            throw new QueryException("Error reading file.",e);
        } catch (ParseException e) {
            throw new QueryException("Parsing exception.",e);
        } catch (DTFException e) {
            throw new QueryException("Parsing exception.",e);
        }
    }

    public String getProperty() { return _property; }
    
    public void open(URI uri, 
                     ArrayList fields, 
                     Condition constraints,
                     String event, 
                     String property) throws QueryException {
        InputStream is;
       
        try {
            is = Action.getStorageFactory().getInputStream(uri);
        } catch (StorageException e) {
            throw new QueryException("Problem accessing storage.",e);
        }
        
        _br = new BufferedReader(new InputStreamReader(is));
       
        _uri = uri;
        _constraints = constraints;
        _fields = fields;
        _event = event;
        _property = property;
     
        if (_fields != null) {
            _fieldNames = new ArrayList();
            
            for(int i = 0 ; i < _fields.size(); i++) { 
                try {
                    _fieldNames.add(((Field)_fields.get(i)).getName().toLowerCase());
                } catch (ParseException e) {
                    throw new QueryException("Unable to get field name.",e);
                }
            }

            if (!_fieldNames.contains("start")) 
                _fieldNames.add("start");
            
            if (!_fieldNames.contains("stop")) 
                _fieldNames.add("stop");
        }
    }
}
