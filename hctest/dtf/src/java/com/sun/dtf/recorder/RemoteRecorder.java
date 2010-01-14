package com.sun.dtf.recorder;

import java.util.Vector;

import com.sun.dtf.DTFNode;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.flowcontrol.Sequence;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.RecorderException;

public class RemoteRecorder extends RecorderBase {

    private Action action = null;
    private Vector _events = null;
   
    private Thread pusher = null;
    private DTFException exception = null;
    private boolean done = false;
    
    public RemoteRecorder(Action action,
                          boolean append,
                          String threadId) {
        super(append);
        _events = new Vector();
        this.action = action;
       
        final String id = threadId;
        pusher = new Thread() { 
            public void run() {
                while (!done || _events.size() > 0) { 
                    while (_events.size() > MAX || (done && _events.size() > 0)) { 
                        Sequence seq = new Sequence();
                        seq.setThreadID(id);
                    
                        /*
                         * Bigger than 250 is going to waste a lot of memory 
                         * plus take way longer than 100ms to send over the wire
                         */
                        int length = (_events.size() > 250 ? 250 : _events.size());
                        for (int i = 0; i < length; i++) {
                            seq.addAction((Event)_events.remove(0));
                        }
                            
                        try {
                            String ownerId = DTFNode.getOwner().getOwner();
                            Action.getComm().sendAction(ownerId, seq);
                        } catch (DTFException e) {
                            exception = e;
                        }
                    } 
                    
                    if (!done) { 
                        synchronized (this) {
                            try {
                                wait(10);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        };
        
        pusher.start();
    }
    
    private int MAX = 100;
    public void record(Event event) throws RecorderException {
        _events.add(event);
    }
    
    public void stop() throws RecorderException { 
        done = true;
        try {
            synchronized (pusher) {
                pusher.notify();
            }
            pusher.join();
        } catch (InterruptedException e) {
            throw new RecorderException("Unable to finish recording.",e);
        }
        
        if (exception != null) 
            throw new RecorderException("Error during recording.",exception);
        
        action.addActions(_events);
    }
    
    public void start() throws RecorderException { }
}
