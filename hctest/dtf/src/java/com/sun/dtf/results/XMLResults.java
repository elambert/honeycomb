package com.sun.dtf.results;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;


import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.ResultsException;
import com.sun.dtf.exception.StorageException;
import com.sun.dtf.util.TimeUtil;


public class XMLResults extends ResultsBase implements FileResults {

    private static String PRETTY_PRINTING_XSL = "xsl/pretty_printing.xsl";
    
    private DOMImplementation _di = null;
    private Document _document = null;
    private Element _root = null;

    private URI _uri = null;
    
    public XMLResults(URI uri) { _uri = uri; }
    public URI getURI() { return _uri; }

    public void start() throws ResultsException {
        // Creation of an XML document
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        try {
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new ResultsException("DocumentBuilder error.",e);
        }
        _di = db.getDOMImplementation();
    }
    
    public void stop() throws ResultsException {
        OutputStream os;
        try {
            os = Action.getStorageFactory().getOutputStream(_uri);
        } catch (StorageException e) {
            throw new ResultsException("Error acess results storage.",e);
        }
       
        // output of the XML document
        DOMSource ds = new DOMSource(_document);
        TransformerFactory tf = TransformerFactory.newInstance();
        StreamResult sr = new StreamResult(os);
        try {
            InputStream is = ClassLoader.getSystemResourceAsStream(PRETTY_PRINTING_XSL);
            
            if (is == null) 
                throw new ResultsException("Unable to find resource: " + 
                                           PRETTY_PRINTING_XSL);
            
            Source source = new StreamSource(is);
            Transformer trans = tf.newTransformer(source);
            trans.transform(ds, sr);     
        } catch (TransformerException e) { 
            throw new ResultsException("Transformer error.",e);
        }
    }
    
    public void recordResult(Result result) throws ResultsException { 
        recordResult(result,_root);
    }
   
    private void populateSuite(Result result, Element suite) throws ResultsException { 
        suite.setAttribute("name", result.getName());
        suite.setAttribute("tests", ""+result.getTotalTests());
        
        try { 
            suite.setAttribute("start", TimeUtil.dateStampToDateStamp(result.getStart()));
            suite.setAttribute("stop", TimeUtil.dateStampToDateStamp(result.getStop()));
        } catch (ParseException e) { 
            throw new ResultsException("Error handling date.",e);
        }
         
        suite.setAttribute("time", ""+result.getDurationInSeconds());
        suite.setAttribute("passed", ""+result.getNumPassed());
        suite.setAttribute("failed", ""+result.getNumFailed());
        suite.setAttribute("skipped", ""+result.getNumSkipped());
    }
    
    private void recordResult(Result result, Element root) throws ResultsException {
        Element suite = null;
        
        if (root == null) { 
            _document = _di.createDocument(null, "testsuite", null);
            _root = _document.getDocumentElement();
            suite = _root;
            root = _root;
            
            populateSuite(result, suite);
        } else if (result.isTestSuite()) { 
            suite = _document.createElement("testsuite");
            root.appendChild(suite);
            populateSuite(result, suite);
        }

        if (result.isTestSuite()) { 
            processProperties(result, suite);
            
            if (result.isFailResult()) { 
               Element failure = _document.createElement("failed");
               if (result.getOutput() != null)
                   failure.appendChild(_document.createCDATASection(result.getOutput()));
               suite.appendChild(failure);
            }
            
            if (result.isPassResult()) { 
               Element pass = _document.createElement("passed");
               if (result.getOutput() != null)
                   pass.appendChild(_document.createCDATASection(result.getOutput()));
               suite.appendChild(pass);
            }
        
            if (result.isSkipResult()) { 
               Element skip = _document.createElement("skipped");
               if (result.getOutput() != null)
                   skip.appendChild(_document.createCDATASection(result.getOutput()));
               suite.appendChild(skip);
            }
            
            Iterator results = result.getResults().iterator();
            while (results.hasNext()) { 
                recordResult((Result)results.next(),suite);
            }
        } else if (result.isTestCase()) { 
            /*
             * Test case output should look like this:
             * 
             * <testcase name="testAdd" 
             *           time="0.018"/>
             */
            Element testcase = _document.createElement("testcase");
            testcase.setAttribute("name", result.getName());
            
            testcase.setAttribute("start", ""+result.getStart());
            testcase.setAttribute("stop", ""+result.getStop());
            
            processProperties(result, testcase);
            
            testcase.setAttribute("time",""+result.getDurationInSeconds());
           
            if (result.isFailResult()) { 
               Element failure = _document.createElement("failed");
               if (result.getOutput() != null)
                   failure.appendChild(_document.createCDATASection(result.getOutput()));
               testcase.appendChild(failure);
            }
            
            if (result.isPassResult()) { 
               Element pass = _document.createElement("passed");
               if (result.getOutput() != null)
                   pass.appendChild(_document.createCDATASection(result.getOutput()));
               testcase.appendChild(pass);
            }
        
            if (result.isSkipResult()) { 
               Element skip = _document.createElement("skipped");
               if (result.getOutput() != null)
                   skip.appendChild(_document.createCDATASection(result.getOutput()));
               testcase.appendChild(skip);
            }
            
            root.appendChild(testcase);
        }
    }
    
    private void processProperties(Result result, Element element) { 
        /*
         * Properties
         */
        Enumeration props = result.getProperties().keys();
        
        while (props.hasMoreElements()) { 
            Element prop = _document.createElement("property");
            String key = (String)props.nextElement();
            prop.setAttribute("name", key);
            prop.setAttribute("value", result.getProperties().getProperty(key));
            element.appendChild(prop);
        }
    }
}
