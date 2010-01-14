package com.sun.dtf.cluster.config;

import java.io.IOException;
import java.io.InputStream;

import java.util.HashMap;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.dtf.exceptions.MCConfigException;

public class MulticellConfig {
   
    private HashMap _cells = null;
    private LinkedList _cellIds = null;
   
    private class Cell { 
        public String _adminvip = null;
        public String _datavip = null;
        public String _spvip = null;
        
        public Cell(String adminvip, String datavip, String spvip) { 
            _adminvip = adminvip;
            _datavip = datavip;
            _spvip = spvip;
        }

        public String getAdminVIP() { return _adminvip; }
        public String getDataVIP() { return _datavip; }
        public String getSpVIP() { return _spvip; }
    }
    
    public MulticellConfig(InputStream is) throws MCConfigException { 
        _cells = new HashMap();
        _cellIds = new LinkedList();
       
        /*
         * XXX: Hackish parsing of the silo_info.xml for the time being 
         *      since multicell related config libraries have some sort of 
         *      dependency on jcontract.
         */
        DocumentBuilder docBuilder;
        try {
            docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = docBuilder.parse(is);
            NodeList list = document.getElementsByTagName("Cell");
          
            for (int i = 0; i < list.getLength(); i++) { 
                Node node = list.item(i);
                NamedNodeMap attributes = node.getAttributes();
                
                String cellID = attributes.getNamedItem("id").getNodeValue();
                String dataVIP = attributes.getNamedItem("data-vip").getNodeValue();
                String adminVIP = attributes.getNamedItem("admin-vip").getNodeValue();
                String spVIP = attributes.getNamedItem("sp-vip").getNodeValue();
               
                _cells.put(cellID, new Cell(adminVIP, dataVIP, spVIP));
                _cellIds.add(cellID);
            }
        } catch (ParserConfigurationException e) {
            throw new MCConfigException("Unable to parse silo config.",e);
        } catch (FactoryConfigurationError e) {
            throw new MCConfigException("Unable to parse silo config.",e);
        } catch (SAXException e) {
            throw new MCConfigException("Unable to parse silo config.",e);
        } catch (IOException e) {
            throw new MCConfigException("Unable to parse silo config.",e);
        }
    }
    
    public int getNumCells() { return _cells.size(); } 
    
    public String getCellId(int index) { return (String)_cellIds.get(index); } 
    
    public String getDataVIP(String cellID) { return ((Cell)_cells.get(cellID)).getDataVIP(); }  
    public String getAdminVIP(String cellID) { return ((Cell)_cells.get(cellID)).getAdminVIP(); }  
    public String getSpVIP(String cellID) { return ((Cell)_cells.get(cellID)).getSpVIP(); }  
}
