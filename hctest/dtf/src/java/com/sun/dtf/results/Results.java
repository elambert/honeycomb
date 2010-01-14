package com.sun.dtf.results;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;

import com.sun.dtf.DTFConstants;
import com.sun.dtf.DTFProperties;
import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.ResultsException;
import com.sun.dtf.exception.StorageException;


public class Results {
    private ResultsBase _results = null;
    private Results _parent = null;
    
    private Properties _properties = null;

    private boolean _testsuiteRecorded = false;
    
    private Appender _appender = null;
    private String _logfile = null;
    
    public Results(ResultsBase results) throws ResultsException { 
        _results = results; 
        _properties = new Properties();

        /*
         * FileResults usually want to save the location of the test logs for 
         * future processing, while other ConsoleResults could care less :)
         */
        if (_results instanceof FileResults) { 
            FileResults res = (FileResults)results;
            URI uri = res.getURI();
            
            Layout layout = new PatternLayout("%-5p %d{dd/MM/yyyy HH:MM:ss} - %m%n");
            String output = "script-" +  
                            Action.getConfig().getProperty(DTFConstants.SCRIPT_ID) +
                            ".out";
            try { 
                URI loguri =  new URI(uri.getScheme(), 
                                      uri.getHost(),
                                      /*
                                       * necessary because on windows platform
                                       * the File.separatorChar becomes a funky
                                       * character
                                       */
                                      "/" + output,
                                      null);
                
                _logfile = Action.getStorageFactory().getPath(loguri);
                _appender = new FileAppender(layout,_logfile);
                
                Action.getLogger().addAppender(_appender);
            } catch (URISyntaxException e) {
                throw new ResultsException("Unable to add appender to logger.",e);
            } catch (StorageException e) {
                throw new ResultsException("Unable to add appender to logger.",e);
            } catch (IOException e) {
                throw new ResultsException("Unable to add appender to logger.",e);
            }
            
            recordProperty(DTFProperties.DTF_TESTCASE_LOG, _logfile);
        }
    }
    
    public void recordProperty(String key, String value) { 
        _properties.setProperty(key, value);
    }
    
    public boolean isTestSuiteRecorded() { return _testsuiteRecorded; }
    public void setTestSuiteRecorded(boolean value) { _testsuiteRecorded = value; }
    
    public void recordResult(Result result) throws ResultsException {
        
        if (_results == null) 
            return;
        
        if (result.isTestSuite()) _testsuiteRecorded = true;
            
        result.setProperties(_properties);
        _properties = new Properties();
        
        synchronized(_results) { 
            _results.recordResult(result);
        }
            
        if (_parent != null) 
            _parent.recordResult(result);
        
    }
    
    public void setParent(Results results) { _parent = results; } 
    public Results getParent() { return _parent; } 
    
    public void start() throws ResultsException { _results.start(); } 
    public void stop() throws ResultsException { 
        if (_appender != null) { 
            Action.getLogger().removeAppender(_appender);
            _appender.close();
        }
        
        _results.stop(); 
    } 
}
