package com.sun.dtf.plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Enumeration;
import java.util.Hashtable;


import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.UnsupportedFeatureException;
import com.sun.dtf.init.JarFileFilter;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.xml.DTDHandler;
import com.wutka.dtd.DTDElement;
import com.wutka.dtd.DTDEntity;

/**
 * Class used by build.xml to add plug-ins to existing DTF framework. This class
 * is responsible for merging DTDs and any other content that is necessary to 
 * make a plug-in part of the DTF framework.
 * 
 * @author Rodney Gomes
 *
 */
public class PluginMerger {
    
    private static DTFLogger _logger = DTFLogger.getLogger(PluginMerger.class);
    
    public static void printUsage() { 
        _logger.info(" PluginMerger ");
        _logger.info("**************");
        _logger.info(" PluginMerger plugin_directory dtf_dtd_location build_location");
    }
   
    public static void main(String[] args) {
        
        if (args.length != 3) { 
            printUsage();
            return;
        }
        
        String pluginLocation = args[0];
        String dtfFilename = args[1];
        String buildLocation = args[2];
        
        File lib = new File(pluginLocation + File.separatorChar + "lib");
        DTDHandler dtfDTD = null; 
        try {
            dtfDTD = new DTDHandler(new FileInputStream(dtfFilename));
        } catch (DTFException e) {
            _logger.error("Unable to load dtd: [" + dtfFilename + "].",e);
            return;
        } catch (FileNotFoundException e) {
            _logger.error("Unable to load dtd: [" + dtfFilename + "].",e);
            return;
        }
        
        Hashtable dtfEntities = dtfDTD.getEntities();
        Hashtable dtfElements = dtfDTD.getElements();
      
        // Get all jar files in the directories lib directory.
        File[] jars = lib.listFiles(new JarFileFilter());
        _logger.info("Looking for jars in: " + lib.getAbsolutePath());
        
        //* Get the jar file and figure out if there is a DTD to be merged.
        for(int i = 0; i < jars.length; i++) { 
            // Check for the DTDFile property value from the Manifest
            File pluginJar = jars[i].getAbsoluteFile();
            String dtdFilename = JarUtil.getDTDPropertyValue(pluginJar, DTDConstants.DTD_FILE_PROPERTY); 
                 
            if (dtdFilename != null) { 
                InputStream dtdIS = JarUtil.getDTDInputStream(pluginJar);
                DTDHandler dtd = null;
                
                try {
                    dtd = new DTDHandler(dtdIS);
                } catch (DTFException e) {
                    _logger.error("Unable to load dtd: [" + dtdFilename + "].",e);
                    return;
                }

                // Merge the entities with the same name and use the | operator
                // to insert the entity values together.
                Hashtable entities = dtd.getEntities();
                Enumeration enumeration = entities.keys();
                
                while (enumeration.hasMoreElements()) { 
                    String entityName = (String) enumeration.nextElement();
                    DTDEntity entity = (DTDEntity) entities.get(entityName);
                   
                    if (_logger.isDebugEnabled())
                        _logger.debug("Found entity: " + entityName + 
                                      " = [" + entity.getValue() + "]");
             
                    if (dtfEntities.containsKey(entityName)) { 
                        DTDEntity dtfEntity = (DTDEntity)dtfEntities.get(entityName); 
                        String value = dtfEntity.getValue();
                        dtfEntity.setValue(value + "|" + entity.getValue());
                        _logger.info("Merging entites: " + entityName + " = " + 
                                      dtfEntity.getValue());
                    } else { 
                        _logger.info("Adding new entity: " + entityName + " = " + 
                                     entity.getValue());
                        dtfEntities.put(entityName, entity);
                    }
                }
                
                Hashtable elements = dtd.getElements();
                enumeration = elements.keys();
                
                while (enumeration.hasMoreElements()) { 
                    String elementName = (String) enumeration.nextElement();
                    DTDElement element = (DTDElement) elements.get(elementName);
                   
                    if (_logger.isDebugEnabled())
                        _logger.debug("Found Element: " + elementName + 
                                      " = [" + element + "]");

                    if (dtfElements.containsKey(elementName)) { 
                        // Can't have this if there are colliding element names
                        // then we can't merge this plugin.
                        _logger.error("Unable to merge two dtds with conflicting elements: [" + elementName + "");
                        return;
                    } 
                }
                
                try {
                    File dtdFile = new File(buildLocation + File.separatorChar + 
                                            "dtf.dtd");
                    
                    File bkFile =  File.createTempFile("dtd", "bk");
                    bkFile.deleteOnExit();
                    copy(dtdFile,bkFile,false);
                  
                    InputStream pluginIS = JarUtil.getDTDInputStream(pluginJar);
                    File dtdTmp = File.createTempFile("dtd-plugin","tmp");
                    dtdTmp.deleteOnExit();
                    File dtdTmp2 = File.createTempFile("dtd-plugin","tmp");
                    dtdTmp2.deleteOnExit();
                    
                    OutputStream dtdtemp = new FileOutputStream(dtdTmp);
                    _logger.info("Copying plugin dtd contents to " + dtdFile);
                    copy(pluginIS,dtdtemp);
                   
                    filterLines(dtdTmp, dtdTmp2, "<!ENTITY.*",false);
                  
                    // Write the Merged Entities
                    FileOutputStream fos = new FileOutputStream(dtdFile); 
                    dtfDTD.writeTo(fos);
                    fos.close();
                    
                    // filter out all the entity lines!
                    _logger.info("Merging with DTD file: " + dtdFile);
                    filterLines(bkFile, dtdFile, "<!ENTITY.*",true);
                   
                    // Attach the Elements from the plugin DTD
                    copy(dtdTmp2,dtdFile,true);
                } catch (UnsupportedFeatureException e) {
                    _logger.error("Error writing out new DTD.",e);
                    return;
                } catch (DTFException e) {
                    _logger.error("Error writing out new DTD.",e);
                    return;
                } catch (FileNotFoundException e) {
                    _logger.error("Error writing out new DTD.",e);
                    return;
                } catch (IOException e) {
                    _logger.error("Error writing out new DTD.",e);
                    return;
                }
            }
        }
    }
    
    private static void filterLines(File src, File dst, String regex, boolean append) throws DTFException { 
        try { 
            FileOutputStream fos = new FileOutputStream(dst,append);
            FileInputStream fis = new FileInputStream(src);
           
            Reader reader = Channels.newReader(fis.getChannel(), "UTF-8");
            BufferedReader br = new BufferedReader(reader);
            
            Writer writer = Channels.newWriter(fos.getChannel(), "UTF-8");
            BufferedWriter bw = new BufferedWriter(writer);
           
            String line = br.readLine();
            while (line != null) { 
                if (!line.matches(regex)) { 
                    bw.write(line + "\n");
                }
                line = br.readLine();
            }
            
            br.close();
            bw.close();
        } catch (FileNotFoundException e) { 
            throw new DTFException("Error filtering files.",e);  
        } catch (IOException e) {
            throw new DTFException("Error filtering files.",e);  
        }
    }
    
    private static void copy(File src, File dst, boolean append) throws DTFException { 
        try { 
            FileOutputStream fos = new FileOutputStream(dst,append);
            FileInputStream fis = new FileInputStream(src);
    
            fos.getChannel().transferFrom(fis.getChannel(),0,fis.getChannel().size());
            
            fos.close();
            fis.close();
        } catch (IOException e) { 
            throw new DTFException("Error copying files.",e);  
        }
    }
    
    private static void copy(InputStream is, OutputStream os) throws DTFException { 
        try { 
            ReadableByteChannel rbc = Channels.newChannel(is); 
            WritableByteChannel wbc = Channels.newChannel(os);
          
            ByteBuffer buff = ByteBuffer.allocateDirect(64*1024);
            buff.clear();
            while (rbc.read(buff) != -1) {
                buff.flip();
                wbc.write(buff);
                buff.compact();
            }
         
            // EOF will leave buffer in fill state
            buff.flip();

            // make sure the buffer is fully drained.
            while (buff.hasRemaining()) {
               wbc.write(buff);
            }
            
            rbc.close();
            wbc.close();
        } catch (IOException e) { 
            throw new DTFException("Error copying files.",e);  
        }
    }
}
