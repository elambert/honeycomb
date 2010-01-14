/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.emd.config;

import java.util.HashMap;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.SAXException;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Iterator;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.ByteArrayInputStream;
import java.util.logging.Logger;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;

public class RootNamespace
    extends Namespace {

    private static final Logger LOG = Logger.getLogger(
            RootNamespace.class.getName());
    
    public static final File templateFile = new File(
            "/opt/honeycomb/share/metadata_config_template.xml");
    private static final File factoryFile = new File(
            "/opt/honeycomb/share/metadata_config_factory.xml");
    public static final File factoryPreUpgradeFile = new File(
            "/config/metadata_config_factory_pre_upgrade.xml");
    public static final File legacyFactoryFile = new File(
            "/opt/honeycomb/share/metadata_config_factory_legacy_101.xml");
    public static final File userConfig = new File(
            "/config/metadata_config.xml");
    public static final File userPreUpgradeConfig = new File(
            "/config/metadata_config_pre_upgrade.xml");

    private static final String emulatorFactoryFile = 
            "config/metadata_config_factory.xml";
    private static final String emulatorLegacyFactoryFile = 
            "config/metadata_config_factory_legacy_101.xml";
    public static final String emulatorFactoryPreUpgradeFile = 
            "config/metadata_config_factory_pre_upgrade.xml";
    public static final String emulatorUserConfig = 
            "config/metadata_config.xml";
    public static final String emulatorUserPreUpgradeConfig = 
            "config/metadata_config_pre_upgrade.xml";
    
    private static RootNamespace instance = null;
    private static File usedUserConfig = null;
    private ArrayList fsViews;
    private Map tables;
    private long lastModified;

    private static File getUsedConfigFile(File sysFile, 
            String emulatorFileName) {
        // Check if we run in the emulator
        String emulatorRoot = System.getProperty("emulator.root");
        if (emulatorRoot == null)
            return sysFile;
        return new File(emulatorRoot+"/"+emulatorFileName);
    }

    public static File getUserConfigFile() {
        return getUsedConfigFile(userConfig,
                                 emulatorUserConfig);
    }

    public static File getUserPreUpgradeConfigFile() {
        return getUsedConfigFile(userPreUpgradeConfig,
                                 emulatorUserPreUpgradeConfig);
    }

    public static File getFactoryConfigFile() {
        return getUsedConfigFile(factoryFile,
                                 emulatorFactoryFile);
    }

    public static File getLegacyFactoryConfigFile() {
        return getUsedConfigFile(legacyFactoryFile,
                                 emulatorLegacyFactoryFile);
    }

    public  static File getFactoryPreUpgradeConfigFile() {
        return getUsedConfigFile(factoryPreUpgradeFile,
                                 emulatorFactoryPreUpgradeFile);
    }

    // When we do this for real we have to save it using the config mechanism!
    public static void savePreUpgradeConfig() {
        LOG.info("Saving the pre-upgrade factory config file to "+
                 getFactoryPreUpgradeConfigFile());
        
        getFactoryConfigFile().renameTo(getFactoryPreUpgradeConfigFile());

        if (usedUserConfig == null) {
            usedUserConfig = getUserConfigFile();
        }

        if (usedUserConfig.exists()) {
            LOG.info("Saving the pre-upgrade user config file to "+
                 getUserPreUpgradeConfigFile());
            usedUserConfig.renameTo(getUserPreUpgradeConfigFile());
        }
    }

    public static void removePreUpgradeConfig() {
        LOG.info("Removing the pre-upgrade config files");
        getFactoryPreUpgradeConfigFile().delete();
        getUserPreUpgradeConfigFile().delete();
    }

    public static boolean preUpgradeConfigExists() {
        boolean v = getFactoryPreUpgradeConfigFile().exists();
        if (v) 
            LOG.info("Found pre-upgrade database schema file -- database upgrade in progress");
        return v;
    }

    public synchronized static RootNamespace getInstance(String config) 
        throws EMDConfigException {
        instance = loadConfigFromString(config, false);
        return instance;
    }

    public synchronized static RootNamespace getNewInstance()
        throws EMDConfigException {
        return initNewInstance();
    }
        
    public synchronized static RootNamespace getInstance() 
        throws EMDConfigException {
        if ((instance == null) || (!instance.stillValid())) {
            if (instance != null) {
                LOG.info("The metadata configuration has changed. Reloading ...");
            }
            instance = initNewInstance();
        }
        return(instance);
    }

    private static RootNamespace initNewInstance()
        throws EMDConfigException {
        if (usedUserConfig == null) {
            usedUserConfig = getUserConfigFile();
        }
        RootNamespace newInstance = new RootNamespace();
        newInstance.loadConfigFiles(getFactoryConfigFile(),usedUserConfig);
        return newInstance;
    }


    public synchronized static RootNamespace getPreUpgradeInstance() 
        throws EMDConfigException {
        File factoryConfig = getFactoryPreUpgradeConfigFile();

        if (factoryConfig.exists()) {
            //This code path is used when there is an old schema 
            // to replace with a new schema.  Both the user config
            // and the factory config must exist in "old" versions.
            LOG.info("found pre-upgrade config file "+factoryConfig);
            RootNamespace myInstance = new RootNamespace();
            File userConfig = getUserPreUpgradeConfigFile();
            myInstance.loadConfigFiles(factoryConfig,userConfig);
            return(myInstance);
        } 

        File legacyConfig = getLegacyFactoryConfigFile();
        if (legacyConfig.exists()) {
            LOG.info("found legacy config file "+legacyConfig);
            //This code path is used when we are updating from a legacy
            // Honeycomb to a recent Honeycomb.   We have a copy of the
            // legacy (1.0.1) factory schema in /opt/honeycomb/share.
            // The user schema is the same.
            RootNamespace myInstance = new RootNamespace();
            File userConfig = getUserConfigFile(); 
            myInstance.loadConfigFiles(legacyConfig,userConfig);
            return(myInstance);
        } 
        return null;
    }

    public static RootNamespace loadConfigFromString(String config, 
                                                     boolean factoryDefault) 
        throws EMDConfigException {
        RootNamespace myInstance = new RootNamespace();
        InputStream stream = null;
        try {
            stream = new ByteArrayInputStream(config.getBytes("UTF-8"));
            myInstance.readConfig(stream, factoryDefault);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } finally {
            if (stream != null)
                try { stream.close(); } catch (IOException e) {}
        }
        return(myInstance);
    }

    private void loadConfigFiles(File factoryConfig, File userConfig)
        throws EMDConfigException {

        loadConfigFile(factoryConfig, true);

        if ( userConfig.exists() ) {
            loadConfigFile(userConfig, false);
        }
    }

    public void loadConfigFile(File confFilename, 
                               boolean factoryDefault) 
        throws EMDConfigException {
        InputStream stream = null;
        try {
            stream = new FileInputStream(confFilename);
            readConfig(stream, factoryDefault);
        } catch (FileNotFoundException e) {
            throw new EMDConfigException(e);
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (IOException e) {}
                stream = null;
            }
        }
    }


    public RootNamespace() {
        super();
        if (usedUserConfig != null) {
            if (usedUserConfig.exists()) {
                lastModified = usedUserConfig.lastModified();
            } else {
                lastModified = 0;
            }
        }
        fsViews = new ArrayList();
        tables = new HashMap();
    }
    
    private boolean stillValid() {
        assert(usedUserConfig != null);

        if (!usedUserConfig.exists()) {
            if (lastModified == 0) {
                return (true);
            } else {
                // user config just got wiped
                return (false);
            }
        }

        if (usedUserConfig.lastModified() > lastModified) {
            return(false);
        }
        return(true);
    }

    public void readConfig(InputStream stream,
                           boolean factoryDefault) 
        throws EMDConfigException {
        
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            SAXParser parser = parserFactory.newSAXParser();
            XMLConfigHandler handler = 
                new XMLConfigHandler(this, factoryDefault);
            parser.parse(stream, handler);
            AssignMissingTables(factoryDefault);
        } catch (Throwable e) {
            throw new EMDConfigException(e);
        }
    }

    private void readConfig()
        throws EMDConfigException {
        InputStream stream = null;
        String emulatorRoot = null;
        String confFilename;

        // Check if we run in the emulator
        emulatorRoot = System.getProperty("emulator.root");
        if (emulatorRoot == null)
            confFilename = factoryFile.getAbsolutePath();
        else
            confFilename = emulatorRoot + "/" + emulatorFactoryFile;

        try {
            stream = new FileInputStream(confFilename);
            readConfig(stream, true);
        } catch (FileNotFoundException e) {
            throw new EMDConfigException(e);
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (IOException e) {}
                stream = null;
            }
        }

        if (usedUserConfig.exists() ) {
            try {
                stream = new FileInputStream(usedUserConfig);
                readConfig(stream, false);
            } catch (FileNotFoundException e) {
                throw new EMDConfigException(e);
            } finally {
                if (stream != null) {
                    try { stream.close(); } catch (IOException e) {}
                    stream = null;
                }
            }
        }
    }

    public FsView getFsView(String name) {
        for (int i = 0; i < fsViews.size(); i++) {
            FsView view = (FsView)fsViews.get(i);
            if (view.getName().equals(name)) {
                return view;
            }
        }
        return null;
    }


    public void addFsView(FsView fsView) 
        throws EMDConfigException {
        fsViews.add(fsView);
    }

    public int getNbViews() {
        return(fsViews==null ? 0 : fsViews.size());
    }

    public FsView[] getViews() {
        FsView[] result = new FsView[fsViews.size()];
        fsViews.toArray(result);
        return(result);
    }

    /**
     * Add a table to the current namespace.
     * @param table the table to be added.
     * @throws EMDConfigException if failed.
     */
    public void addTable(Table table) throws EMDConfigException {
        table.validateTable();
        tables.put(table.getName().toUpperCase(), table);
    }
    
    public Table addTable( String name, boolean factoryDefault) 
        throws EMDConfigException {
        return addTable(name,factoryDefault,false);
    }

    public Table addTable( String name, 
                           boolean factoryDefault, 
                           boolean autoGenerated) 
        throws EMDConfigException {

        Table table = new Table(name, factoryDefault, autoGenerated);
        String tableKey = name.toUpperCase();

        if (tables.get(tableKey) != null) {
            throw new EMDConfigException("Table name ["+
                                         table.getName()+
                                         "] must be unique ignoring case.");
        } // if
        
        tables.put(tableKey, table);
        return table;
    } // addTable
    
    public void removeTable(String name) {
        String tableKey = name.toUpperCase();
        tables.remove(tableKey);
    }

    public int getNbTables() {
        return(tables.size());
    }

    public Table getTable(String tableName) {
	return (Table)tables.get(tableName.toUpperCase());
    }

    public Table[] getTables() {
        Table[] result = new Table[tables.size()];
        tables.values().toArray(result);
        return(result);
    }

    public Map getTablesMap() {
        return tables;
    }

    /**
       Assign tables for the fields that are not mentioned in a
       <table> declaration the config file.  The current rule is that
       each non-assigned field gets its own table.  This rule might
       change in the future.  <br> This method is called during config
       parsing, and the config parsing knows whether the config file
       being parsed is part of the factory default or not.  <br>
       (This functionality belongs with the RootNamespace class, even
       though the implementation must iterate through all the children
       namespaces.)
    */
    public void AssignMissingTables(boolean factory) 
        throws EMDConfigException {

        ArrayList fields = new ArrayList();

        getFields(fields,true);  // get all fields in all namespaces
	    
        for (int i = 0; i < fields.size(); i++) {
            Field field = (Field) fields.get(i);
            String fieldName = field.getQualifiedName();
            if (fieldName.equals("system.object_id")) 
                continue;       // No table needed!
            if (! field.isQueryable())
                continue;       // No table needed
            if (field.getTableColumn() == null) {
                String tableName = fieldName.replaceAll("\\.","_");
                // set isAutoGenerated() for this new table
                Table table = addTable(tableName, factory, true);
                table.addColumn(field);
            }
        }
    }

    public void export(Writer out,
                       boolean includeFactoryDefault) 
        throws IOException {

        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n"+
                                "<!-- DO NOT EDIT-->\n\n"+
                                "<metadataConfig>\n"+
                                "  <schema>\n");

        exportDependants(out, "    ", includeFactoryDefault);
        out.write("  </schema>\n");
        out.write("\n  <fsViews>\n");

        for (int i=0; i<fsViews.size(); i++) {
            FsView fsView = (FsView)fsViews.get(i);
            fsView.export(out, "    ", includeFactoryDefault);
        }

        out.write("  </fsViews>\n");

        out.write("\n  <tables>\n");
        Iterator ite = tables.values().iterator();
        while(ite.hasNext()) {
            Table table = (Table) ite.next();
            table.export(out, "    ", 
                         includeFactoryDefault);
        }
        out.write("  </tables>\n");

        out.write("\n</metadataConfig>\n");
    }

    public Object validate(String name,
                           Object value)
        throws EMDConfigException {

        Field field = resolveField(name);
        if (field == null) {
            throw new EMDConfigException("Field ["+name+"] does not exist");
        }
        return field.validate(value);
    }

    public void validate(Map map)
        throws EMDConfigException {
        String key;
        Object value, newValue;

        Map newValues = new HashMap();

        Iterator keys = map.keySet().iterator();
        while (keys.hasNext()) {
            key = (String)keys.next();
            value = map.get(key);
            if ((newValue = validate(key, value)) != null) {
                // We'll be replacing the value in the Map
                keys.remove();
                newValues.put(key, newValue);
            }
        }

        keys = newValues.keySet().iterator();
        while (keys.hasNext()) {
            key = (String)keys.next();
            map.put(key, newValues.get(key));
        }
    }

    public void validateSchema() 
        throws EMDConfigException {

        Iterator ite = tables.values().iterator();
        while(ite.hasNext()) {
            Table table = (Table) ite.next();
            table.validateTable();
        }
    }


    public static void main(String[] arg) {

        try {
            RootNamespace rootNamespace = RootNamespace.getInstance();
            Writer out = new BufferedWriter(new OutputStreamWriter(System.out,"UTF-8"));
            rootNamespace.export(out, true);

            RootNamespace preNamespace = getPreUpgradeInstance();
            if (preNamespace != null) {
                preNamespace.export(out,true);
            }
            out.flush();
            out.close();

        } catch (EMDConfigException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
