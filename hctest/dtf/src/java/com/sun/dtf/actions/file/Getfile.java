package com.sun.dtf.actions.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.sun.dtf.comm.rpc.ActionResult;
import com.sun.dtf.comm.rpc.Node;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

public class Getfile extends Returnfile {

    private String remotefile = null;
    
    private int offset = 0;
    private int chunkSize = 64*1024; // default chunk size of 64KB
    private boolean append = false;
   
    public void execute() throws DTFException {
        ActionResult ar = (ActionResult)getContext(Node.ACTION_RESULT_CONTEXT);
            
        if (!new File(getRemotefile()).exists()) 
            throw new DTFException("Remote file does not exist [" + 
                                   getRemotefile() + "]");
              
        RandomAccessFile raf = null;
        try { 
            raf = new RandomAccessFile(getRemotefile(),"rw");
            // If the length is less than 64Kb then use the length of the file
            // instead of the chunkSize
            int chunkLength = (int)(chunkSize < raf.length() ? chunkSize : raf.length());
            byte[] bytes = new byte[chunkLength];
            
            int read = -1;
            raf.seek(offset);
            if (raf.length() > offset) {
                read = raf.read(bytes);
            }
            
            // if the file is 0 bytes still create it 
            if (raf.length() == 0) { 
                Writechunk write = new Writechunk();
                write.setUri(uri);
                write.setAppend(false);
                write.setLength(0);
                ar.addAction(write);
            }
           
            if (read != -1) {
                Writechunk write = new Writechunk();
                write.setUri(uri);
                
                if (getAppend())  
                    write.setAppend(true);
                else
                    write.setAppend(offset != 0);
                
                write.setLength(read);
                write.bytes(bytes);
                // return Writefile result that will write out a certain amount
                // of data
                ar.addAction(write);
                Returnfile.genReturnFile(uri,offset+read,getRemotefile(),true);
            } 
        } catch (FileNotFoundException e) { 
            throw new DTFException("File not found.",e);
        } catch (IOException e) {
            throw new DTFException("Error reading file.",e);
        } finally { 
            try {
                if (raf != null) 
                    raf.close();
            } catch (IOException e) {
                throw new DTFException("Error closing input stream.",e);
            }
        }
    }
    
    public void setOffset(int offset) { this.offset = offset; } 
    public int getOffset() { return offset; } 

    public void setAppend(boolean append) { this.append = append; } 
    public boolean getAppend() { return append; } 

    public void setRemotefile(String remotefile) { this.remotefile = remotefile; } 
    public String getRemotefile() throws ParseException { 
        return replaceProperties(remotefile); 
    } 
}
