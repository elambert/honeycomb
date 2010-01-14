package com.sun.dtf;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.sun.dtf.actions.protocol.Lock;
import com.sun.dtf.actions.util.ScriptUtil;
import com.sun.dtf.comm.Comm;
import com.sun.dtf.components.Components;
import com.sun.dtf.config.Config;
import com.sun.dtf.database.DBConnMgr;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.init.InitPlugins;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.query.DBQuery;
import com.sun.dtf.query.QueryFactory;
import com.sun.dtf.query.TxtQuery;
import com.sun.dtf.recorder.CSVRecorder;
import com.sun.dtf.recorder.ConsoleRecorder;
import com.sun.dtf.recorder.DBRecorder;
import com.sun.dtf.recorder.NullRecorder;
import com.sun.dtf.recorder.ObjectRecorder;
import com.sun.dtf.recorder.Recorder;
import com.sun.dtf.recorder.RecorderFactory;
import com.sun.dtf.recorder.TextRecorder;
import com.sun.dtf.results.Results;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;
import com.sun.dtf.storage.StorageFactory;
import com.sun.dtf.util.ThreadUtil;

public class DTFNode {

    // Logger
    protected static DTFLogger _logger = DTFLogger.getLogger(DTFNode.class);

    // Config 
    protected static Config _config = null;

    // Communications
    protected Comm _comm = null;
    
    // State
    protected DTFState _state = null;
    
    // Cached values at startup
    protected static String _type = null;
    
    protected static Lock _owner = null;
    
    private static boolean _running = true;
    
    public Config getConfig() { return _config; }
    public Comm getComm() { return _comm; } 
    public static String getType() { return _type; } 
    public static String getId() { return _owner.getId(); }
    
    public static Lock getOwner() { return _owner; } 
    public static void setOwner(Lock owner) { _owner = owner; } 
   
    private DTFNode() throws DTFException {
        // Read Configuration File
        _config = new Config();

        // Setup Logger
        DTFLogger.setConfig(_config);
        _type = _config.getProperty(DTFProperties.DTF_NODE_TYPE, "NONE");
        _logger.info("Starting " + _type + " component.");

        _state = new DTFState(_config, new StorageFactory());

        _state.setRecorder(new Recorder(new NullRecorder(false), null));
        _state.setComponents(new Components());

        ActionState.getInstance().setState(DTFConstants.MAIN_THREAD_NAME, _state);

        // init plugins
        InitPlugins.init();

        // Recorder Implemenations
        RecorderFactory.registerRecorder("console", ConsoleRecorder.class);
        RecorderFactory.registerRecorder("csv", CSVRecorder.class);
        RecorderFactory.registerRecorder("database", DBRecorder.class);
        RecorderFactory.registerRecorder("object", ObjectRecorder.class);
        RecorderFactory.registerRecorder("txt", TextRecorder.class);

        // Query Implemenations
        QueryFactory.registerQuery("database", DBQuery.class);
        QueryFactory.registerQuery("txt", TxtQuery.class);

        _state.setResults(new Results(null));

        // Setup Communications
        _comm = new Comm(_config);

        // Set COMM to default state
        _state.setComm(_comm);
    }
   
    public void run() throws DTFException {
        String xmlFile = _config.getProperty(DTFProperties.DTF_XML_FILENAME);
        DTFException failure = null;
        
        try {
            if (xmlFile != null) {
                FileInputStream fis = null;
                try { 
                    try { 
                        fis = new FileInputStream(xmlFile);
                    } catch (FileNotFoundException e) {
                        _logger.error("Unable to find file [" + xmlFile + "]");
                        throw new DTFException("Unable to load xml.",e);
                    }
                   
                    /*
                     * Record DTF properties once for this test.
                     */
                    String version = _state.getConfig().
                                         getProperty(DTFProperties.DTF_VERSION);
                    _state.getResults().
                              recordProperty(DTFProperties.DTF_VERSION,version);
                    
                    ScriptUtil.executeScript(fis,_state);
                } finally { 
                    try {
                        if (fis != null)
                            fis.close();
                    } catch (IOException e) {
                        if (_logger.isDebugEnabled())
                            _logger.debug("Failed to close xmlFile.",e);
                    }
                }
            } else {
                if (getType().equalsIgnoreCase("dtfx")) { 
                    _logger.error("DTFX supplied without " + 
                                  DTFProperties.DTF_XML_FILENAME);
                    throw new DTFException("DTFX supplied without " + 
                                           DTFProperties.DTF_XML_FILENAME);
                }
                
                while (_running && _comm.isUp()) {
                    // output important stats
                    ThreadUtil.pause(3000);
                }
            }
        } catch (DTFException e) { 
            failure = e;
        } finally { 
            _state.getCursors().close();
            DBConnMgr.getInstance().close();
            _comm.shutdown();
            _logger.info("Shutting down " + getType());
        }
      
        /*
         * Don't want the stack trace printed yet again, it's enough to print 
         * it once at the script level and then exit with a return code that
         * is different from 0
         */
        if (failure != null) {
            if (!failure.wasLogged())
                _logger.error("Failure during test execution.",failure);
            
            System.exit(-1);
        }
    }
    
    public static void stop() {
        _running = false;
    }
    
    public static void main(String[] args) throws DTFException {
        DTFNode node = null;
        node = new DTFNode();
        node.run();
    }
}
