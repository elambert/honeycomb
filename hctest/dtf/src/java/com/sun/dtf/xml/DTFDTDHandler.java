package com.sun.dtf.xml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.actions.Action;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;


public class DTFDTDHandler extends DTDHandler {

    public DTFDTDHandler(InputStream dtdIS) throws DTFException {
        super(dtdIS);
    }

    private static DTFDTDHandler _instance = null;
    
    public synchronized static DTFDTDHandler getInstance() throws DTFException {
        if (_instance == null) { 
            Config config = Action.getConfig();
            String dtdFilename = config.getProperty(DTFProperties.DTF_DTD_FILENAME);
            try {
                _instance = new DTFDTDHandler(new FileInputStream(dtdFilename));
            } catch (FileNotFoundException e) {
                throw new DTFException("Unable to find dtd [" + dtdFilename + "]",e);
            }
        }
        
        return _instance;
    }
}
