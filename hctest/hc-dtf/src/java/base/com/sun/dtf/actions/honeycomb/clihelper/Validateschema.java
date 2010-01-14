package com.sun.dtf.actions.honeycomb.clihelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.dtf.actions.honeycomb.cli.CLICommand;
import com.sun.dtf.cluster.HCEmulator;
import com.sun.dtf.cluster.cli.CLI;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.util.HCProperties;

/**
 * @dtf.tag validateschema
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag is used to validate that the current schema on the 
 *               honeycomb cluster has the specified schema and if it does not 
 *               then this tag will automatically apply the schema for you.
 *               
 *               All schemas referenced by this tag are found in the 
 *               dtf/lib/hc/config/ directory of your DTF build.
 *               
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <validateschema schema="${hc.metadata.schema}"/>
 * </component>
 */
public class Validateschema extends CLICommand  {

    public static final String SCHEMA_LOCATION  = "lib/hc/config";
    
    private static final String[] LIST_SCHEMA   = new String[] {"-l"};
    private static final String[] APPLY_SCHEMA  = new String[] {"-a"};

    /**
     * @dtf.attr schema
     * @dtf.attr.desc The schema name to be validated against the cluster. If 
     *                this schema is not present it will be applied using the 
     *                honeycomb CLI command "mdconfig". The available schemas
     *                can be found in dtf/lib/hc/config/ directory of your DTF 
     *                build.
     */
    private String schema = null;
    
    public void execute() throws DTFException {
        getLogger().info("Validating schema [" +  getSchema() + "]");
        getRemoteLogger().info("Validating schema [" + getSchema() + "]");
        CLI cli = getCLI();
        String[] lines = cli.mdconfig(LIST_SCHEMA, null);
    
        File fschema = new File(SCHEMA_LOCATION + File.separatorChar + getSchema());
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fschema);
        } catch (FileNotFoundException e) {
            throw new DTFException("Unable to find schema [" + getSchema() + "]",e);
        }
           
           
        DocumentBuilder docBuilder;
        Document document;
        try { 
            docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            document = docBuilder.parse(fis);
        } catch (FactoryConfigurationError e) { 
            throw new DTFException("Unable to parse schema [" + getSchema() + 
                                   "]",e);
        } catch (ParserConfigurationException e) {
            throw new DTFException("Unable to parse schema [" + getSchema() + 
                                   "]",e);
        } catch (SAXException e) {
            throw new DTFException("Unable to parse schema [" + getSchema() + 
                                   "]",e);
        } catch (IOException e) {
            throw new DTFException("Unable to parse schema [" + getSchema() + 
                                   "]",e);
        }
            
        NodeList namespaces = document.getElementsByTagName("namespace");
          
        boolean allFound = true;
        for (int i = 0; i < namespaces.getLength(); i++) { 
            Node namespace = namespaces.item(i);
            NamedNodeMap attributes = namespace.getAttributes();
            String name = attributes.getNamedItem("name").getNodeValue();
               
            boolean found = false;
            for (int l = 0; l < lines.length; l++) { 
                if (lines[l].indexOf(name) != -1) {
                    found = true;
                } 
            }
                
            if (!found)  {
                allFound = false;
                getLogger().warn("Missing namespace [" + name + "]");
            }
        }
            
        if (getConfig().getProperty(HCProperties.HC_CLUSTER_TYPE).equals(HCEmulator.HC_EMULATOR)) {
            getLogger().info("On the emulator you're responsible for checking your schema");
            getRemoteLogger().info("On the emulator you're responsible for checking your schema");
            return;
        }
                
            
        if (allFound){
            getLogger().info("Schema is loaded.");
            getRemoteLogger().info("Schema is loaded.");
        } else {
            getLogger().info("Schema is not loaded, applying desired schema.");
            getRemoteLogger().info("Schema is not loaded, apply desired schema.");
            try { 
                fis = new FileInputStream(fschema);
            } catch (FileNotFoundException e) { 
                throw new DTFException("Unable to find schema [" + 
                                       getSchema() + "]",e);
            }
              
            String[] result = cli.mdconfig(APPLY_SCHEMA, fis);
                
            for (int i = 0; i < result.length; i++) { 
                getLogger().info(result[i]);
            }
        }
    }

    public String getSchema() throws ParseException { return replaceProperties(schema); }
    public void setSchema(String schema) { this.schema = schema; }
    
}
