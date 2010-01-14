package com.sun.dtf.cluster;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.dtf.exception.DTFException;

public interface NodeInterface {
    
    public abstract String getId();

    /**
     * 
     * @param remotefile
     * @return
     * @throws DTFException
     */
    public abstract String hashFile(String remotefile) throws DTFException;

    /**
     * 
     * @param sampleRate
     * @return
     * @throws DTFException
     */
    public abstract void startPerformanceMonitor(int sampleRate)
            throws DTFException;

    /**
     * 
     * @param sampleRate
     * @return
     * @throws DTFException
     */
    public abstract void stopPerformanceMonitor() throws DTFException;

    /**
     * 
     * @param whereTo
     * @param append
     * @throws DTFException
     */
    public abstract void collectPerfLog(String whereTo, boolean append)
            throws DTFException;
    
    public void collectFrags(String drive, String whereTo) throws DTFException;

    public abstract void mkdir(String dir) throws DTFException;
    
    public abstract boolean dirExists(String dir) throws DTFException;

    public abstract void rmdir(String dir) throws DTFException;

    public abstract void chmod(String perm, String file) throws DTFException;

    public abstract void scpFrom(String remotefile, OutputStream out)
            throws DTFException;

    public abstract void scpTo(InputStream in, String remotefile)
            throws DTFException;

    /**
     * 
     * @param packageName
     * @return
     * @throws DTFException
     */
    public abstract boolean packageInstalled(String packageName)
            throws DTFException;

    public abstract void pkillHoneycomb() throws DTFException;

    public abstract void rebootOS() throws DTFException;

    public abstract void startHoneycomb() throws DTFException;

    public abstract void setDevMode() throws DTFException;

    public abstract void unSetDevMode() throws DTFException;

    public abstract void snapshot(String type,
                                  String name,
                                  String mode,
                                  String disk) 
                    throws DTFException;

    public abstract void snapshotPreCondition(String type,
                                              String name,
                                              String mode,
                                              String disk)
                    throws DTFException;

}