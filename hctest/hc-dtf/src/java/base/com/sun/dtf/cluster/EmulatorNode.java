package com.sun.dtf.cluster;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.dtf.cluster.ssh.SSHCommand;
import com.sun.dtf.exception.DTFException;

public class EmulatorNode implements NodeInterface {
    
    public String getId() { return "emulator.node"; }

    public void chmod(String perm, String file) throws DTFException {
    }

    public void collectPerfLog(String whereTo, boolean append)
            throws DTFException {
        try {
            new File(whereTo).createNewFile();
        } catch (IOException e) {
            throw new DTFException("Unable to create fake log file.",e);
        }
    }

    public String hashFile(String remotefile) throws DTFException {
        return null;
    }

    public void collectFrags(String drive, String whereTo) throws DTFException {
        
    }
    
    public boolean dirExists(String dir) throws DTFException {
        return false;
    }

    public void mkdir(String dir) throws DTFException {
    }

    public boolean packageInstalled(String packageName) throws DTFException {
        return true;
    }

    public void pkillHoneycomb() throws DTFException {
    }

    public void rebootOS() throws DTFException {
    }

    public void rmdir(String dir) throws DTFException {
    }

    public void setDevMode() throws DTFException {
    }

    public void snapshot(String type, String name, String mode, String disk)
            throws DTFException {
    }

    public void snapshotPreCondition(String type, String name, String mode, String disk)
            throws DTFException {
    }

    public void startHoneycomb() throws DTFException {
    }

    public void startPerformanceMonitor(int sampleRate) throws DTFException {
    }

    public void stopPerformanceMonitor() throws DTFException {
    }

    public void unSetDevMode() throws DTFException {
    }

    public SSHCommand executeCommand(String cmd) throws DTFException {
        return null;
    }

    public void scpFrom(String remotefile, OutputStream out)
            throws DTFException {
    }

    public void scpTo(InputStream in, String remotefile) throws DTFException {
    }

}