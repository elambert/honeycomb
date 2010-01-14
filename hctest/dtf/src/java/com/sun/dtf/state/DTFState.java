package com.sun.dtf.state;

import java.util.Hashtable;

import com.sun.dtf.actions.Action;
import com.sun.dtf.comm.Comm;
import com.sun.dtf.components.Components;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.functions.Functions;
import com.sun.dtf.query.Cursors;
import com.sun.dtf.recorder.Recorder;
import com.sun.dtf.references.References;
import com.sun.dtf.results.Results;
import com.sun.dtf.storage.StorageFactory;


/*
 * TODO: Cleanup of this code and it's logic is needed, probably will benefit 
 *       from using ThreadLocal
 */
public class DTFState {
    
    private boolean deleted = false;
    
    private Config config = null;
    private StorageFactory storage = null;
    private Comm comm = null;
    private Components components = null;
   
    private Recorder recorder = null;
    private Cursors cursors = null;

    private Hashtable context = null;
   
    /*
     * TODO: temporary solution... I need to clean up all of this state setting
     *       and management code.
     */
    private Hashtable globalContext = null;
    
    private Action current = null;
    
    private Results results = null;

    private boolean replace = true;
    
    private References references = null;
    private Functions functions = null;
    
    public DTFState(Config config, StorageFactory storage) throws DTFException {
        this.config = config;
        this.storage = storage;
        this.context = new Hashtable();
        this.globalContext = new Hashtable();
        this.cursors = new Cursors();
        this.references = new References();
        this.functions = new Functions();
    }
    
    public DTFState(Config config, 
                    StorageFactory storage, 
                    Comm comm,
                    Components components,
                    Recorder recorder,
                    Cursors cursors,
                    References references,
                    Results results,
                    Functions functions,
                    Hashtable contexts,
                    Hashtable globalContexts) {
        this.config = config;
        this.storage = storage;
        this.comm = comm;
        this.components = components;
        this.recorder = recorder;
        this.context = contexts;
        this.globalContext = globalContexts;
        this.cursors = cursors;
        this.references = references;
        this.functions = functions;
        this.results = results;
    }
    
    public Config getConfig() { return config; }
    public void setConfig(Config config) { this.config = config; }

    public StorageFactory getStorage() { return storage; }
    public void setStorage(StorageFactory storage) { this.storage = storage; }

    public Comm getComm() { return comm; }
    public void setComm(Comm comm) { this.comm = comm; }
    
    public void disableReplace() { replace = false; }
    public void enableReplace() { replace = true; }
    public boolean replace() { return replace; }
    
    public void registerContext(String key, Object value) { context.put(key, value); }
    public void unRegisterContext(String key) { context.remove(key); }
    public Object getContext(String key) { return context.get(key); }

    public void registerGlobalContext(String key, Object value) { globalContext.put(key, value); }
    public void unRegisterGlobalContext(String key) { globalContext.remove(key); }
    public Object getGlobalContext(String key) { return globalContext.get(key); }
    public void resetGlobalContext() { globalContext = new Hashtable(); }
    
    public void setAction(Action action) { current = action; }
    public Action getAction() { return current; }
    
    public Object clone() throws CloneNotSupportedException {
        throw new Error("Method not implemented, use duplicate().");
    }
    
    public DTFState duplicate() {
        DTFState state = new DTFState((Config)config.clone(),
                                      storage,
                                      comm,
                                      components,
                                      recorder,
                                      cursors,
                                      references,
                                      results,
                                      functions,
                                      new Hashtable(),
                                      globalContext);
        return state;
    }

    public Components getComponents() { return components; }
    public void setComponents(Components components) { this.components = components; }

    public Recorder getRecorder() { return recorder; }
    public void setRecorder(Recorder recorder) {  this.recorder = recorder;  }

    public Results getResults() { return results; }
    public void setResults(Results results) {  this.results = results;  }

    public Cursors getCursors() { return cursors; }
    public void setCurors(Cursors cursors) { this.cursors = cursors; }
    
    public References getReferences() { return references; }
    public void setReferences(References references) { this.references = references; }
    
    public Functions getFunctions() { return functions; } 
    public void setFunctions(Functions functions) { this.functions = functions; } 
    
    public boolean isDeleted() { return deleted; } 
    public void setDeleted() { deleted = true; } 
}
