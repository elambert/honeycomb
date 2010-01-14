package com.sun.dtf.actions.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import com.sun.dtf.actions.util.CDATA;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag cat
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Cat tag is used to output information to files that would 
 *               usually be logged to the test case output.
 *               
 *              
 * @dtf.tag.example
 * <cat uri="storage://OUTPUT/testoutput">
 *      This output will be visible at storage://OUTPUT/testoutput
 *      Os: ${os.name}
 *      Arch: ${os.arch}
 *      dtf.test.property1 = ${dtf.test.property1}
 *      dtf.test.property2 = ${dtf.test.property2}
 * </cat>
 */
public class Cat extends CDATA {
    
    /**
     * @dtf.attr uri
     * @dtf.attr.desc The URI defining where to output the enclosed message.
     */
    private String uri = null;
   
    /**
     * @dtf.attr append
     * @dtf.attr.desc Attribute will specify if we want to append to the 
     *                destination or just replace it with the new contents.
     */
    private String append = null;
   
    /**
     * @dtf.attr trim
     * @dtf.attr.desc This attribute allows us to trim white spaces around the
     *                message we're trying to cat.
     */
    private String trim = null;
   
    public void execute() throws DTFException {
        String message = getCDATA();
        
        if (message == null || message.trim().length() == 0) { 
            // if no CDATA is specified then cat the file being pointed to by
            // the uri.
            InputStream is = getStorageFactory().getInputStream(getUri());
           
            byte[] buff = new byte[1024*64];
            try { 
                int read = 0;
                while ((read = is.read(buff)) >= 0) { 
                    getLogger().info(new String(buff,0,read));
                }
            } catch (IOException e) { 
                throw new DTFException("Unable to write to file [" + getUri() + "]",e);
            } finally { 
                try {
                    is.close();
                } catch (IOException ignore) { }
            }
        } else { 
            OutputStream os = getStorageFactory().getOutputStream(getUri(),getAppend());
            StringBuffer result = new StringBuffer();
           
            if (getTrim()) {
                String[] lines = message.split("\n");
                
                for (int i = 0; i < lines.length; i++) { 
                    String aux = lines[i].replaceAll("^[ \t]+","");
                    if (i < lines.length-1)
                        result.append(aux.replaceAll("[ \t]$","") + "\n");
                    else 
                        result.append(aux.replaceAll("[ \t]$",""));
                }
            }
             
            try { 
                os.write(result.toString().getBytes());
            } catch (IOException e) { 
                throw new DTFException("Unable to write to file [" + getUri() + "]",e);
            } finally { 
                try {
                    os.close();
                } catch (IOException ignore) { }
            }
        }
    }

    public void setUri(String uri) { this.uri = uri; }
    public URI getUri() throws ParseException { return parseURI(uri); }
    
    public void setAppend(String append) { this.append = append; } 
    public boolean getAppend() throws ParseException { return toBoolean("append", append); }

    public void setTrim(String trim) { this.trim = trim; } 
    public boolean getTrim() throws ParseException { return toBoolean("trim", trim); }
}
