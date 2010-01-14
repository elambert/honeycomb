package com.sun.dtf.util;

public class DTFThread extends Thread {

    private boolean _running = true;
  
    public boolean running() { return _running; } 
    public synchronized boolean shutdown() { 
        if (_running) {
            _running = false;
            return true;
        } else {
            return false;
        }
    }
}