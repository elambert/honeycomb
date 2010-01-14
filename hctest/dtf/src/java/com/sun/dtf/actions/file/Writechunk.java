package com.sun.dtf.actions.file;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.dtf.exception.DTFException;
import com.sun.dtf.util.ByteArrayUtil;

public class Writechunk extends Returnfile {

    private boolean append = false;
    private byte[] bytes = null;
    private int length = 0;
    
    public void execute() throws DTFException {
        OutputStream os = getStorageFactory().getOutputStream(getUri(), append);

        if (getAppend() == false)
            getLogger().info("Writing file [" + getUri() + "]");
       
        try {
            if (bytes != null)
                os.write(bytes, 0, getLength());
        } catch (IOException e) {
            throw new DTFException("Unable to write to file.",e);
        } finally { 
            try {
                if (os != null) 
                    os.close();
            } catch (IOException e) {
                throw new DTFException("Error closing file.",e);
            }
        }
    }
   
    public boolean getAppend() { return append; } 
    public void setAppend(boolean append) { this.append = append; } 
    
    public String getBytes() {
        return ByteArrayUtil.byteArrayToHexString(bytes);
    }
    
    public void setBytes(String hex) { 
        this.bytes = ByteArrayUtil.hexToByteArray(hex);
    }
    public void bytes(byte[] bytes) { this.bytes = bytes; } 
    
    public void setLength(int length) { this.length = length; } 
    public int getLength() { return length; } 
}
