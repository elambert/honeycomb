package com.sun.dtf.actions;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import com.sun.dtf.exception.ParseException;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.xml.ActionParser;


public class ActionFactory {

    protected static DTFLogger _logger = DTFLogger.getLogger(ActionFactory.class);

    public static Action parseAction(InputStream is) throws ParseException {
        return parseAction(is,true,true);
    }
    
    public static Action parseAction(InputStream is,
                                     boolean processFunctions,
                                     boolean processReferences) 
                  throws ParseException {
        
        ActionParser parser = new ActionParser(processReferences,
                                               processFunctions);
        XMLReader xr = newXMLReader();
        InputSource source = new InputSource(is);
        xr.setContentHandler(parser);
        
        MyErrorHandler errorHandler = new MyErrorHandler(parser);
        xr.setErrorHandler(errorHandler);
        
        try {
            xr.setFeature( "http://xml.org/sax/features/validation", true);
            xr.parse(source);
            return (Action) parser.getResult();
        } catch (IOException e) {
            throw new ParseException("Error parsing XML.",e);
        } catch (SAXException e) {
            throw new ParseException("Error parsing XML.",e);
        }
    }
    
    public static class MyErrorHandler implements ErrorHandler {
        
        private ActionParser _parser = null;

        public MyErrorHandler(ActionParser parser) { 
            _parser = parser;
        }
        
        public void error(SAXParseException exception) throws SAXException {
            Locator locator = _parser.getLocator();
            StringBuffer message = new StringBuffer();
            message.append("Error at line: " + locator.getLineNumber());
            message.append(" column: " + locator.getColumnNumber());
            message.append(" with " + exception.getMessage());
            throw new SAXException(message.toString());
        }

        public void fatalError(SAXParseException exception) throws SAXException {
            Locator locator = _parser.getLocator();
            StringBuffer message = new StringBuffer();
            message.append("Error at line: " + locator.getLineNumber());
            message.append(" column: " + locator.getColumnNumber());
            message.append(" with " + exception.getMessage());
            throw new SAXException(message.toString());
        }

        public void warning(SAXParseException exception) throws SAXException {
            _logger.warn("Warning while processing XML.",exception);
        } 
    }
    
    protected static XMLReader newXMLReader() throws ParseException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            return saxParser.getXMLReader();
        } catch (SAXException e) {
            throw new ParseException("Unable to get SAXParser.",e);
        } catch (ParserConfigurationException e) {
            throw new ParseException("Unable to get SAXParser.",e);
        }
    }
}
