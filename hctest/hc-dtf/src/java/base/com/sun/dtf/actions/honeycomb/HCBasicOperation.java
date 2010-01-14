package com.sun.dtf.actions.honeycomb;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.honeycomb.util.LoadSpreaderGamingSocketFactory;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.recorder.Event;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;

public abstract class HCBasicOperation extends Action {

    /**
     * @dtf.attr datavip
     * @dtf.attr.desc Identifies the datavip address which will be used when 
     *                trying to communicate with the honeycomb cluster.
     */
    private String datavip = null;
    
    /**
     * @dtf.attr port
     * @dtf.attr.desc An integer value that identifies the port to use when 
     *                trying to communicate with the honeycomb cluster.
     */
    private String port = null;

    /**
     * @dtf.attr node
     * @dtf.attr.desc An integer value that identifies which node in the ring
     *                to target with this current operation. 
     */
    private String node = null;
    
    /**
     * @dtf.attr continueOnFailure
     * @dtf.attr.desc This boolean attribute will define if the action being
     *                executed will throw an exception in case of a failure
     *                (onFailure=fail, default), or if onFailure we'll continue
     *                and just register this operation as having failed.
     */
    private String continueOnFailure = null;
   
    /**
     * @dtf.attr monitor
     * @dtf.attr.desc <p>
     *                This boolean property indicates if the current operation
     *                should use the monitoring data channels to collect 
     *                additional information about the operation such as: 
     *                first byte latency, commit latency and bandwidth usage.
     *                </p>
     */
    private String monitor = null;
    
    HCBasicOperation() { }
    
    public String getDatavip() throws ParseException { return replaceProperties(datavip); }
    public void setDatavip(String datavip) { this.datavip = datavip; }

    public int getPort() throws ParseException { return toInt("port",port); }
    public void setPort(String port) { this.port = port; }

    public int getNode() throws ParseException { return toInt("node",node,-1); }
    public void setNode(String node) { this.node = node; }

    public String getContinueonfailure() throws ParseException { 
        return replaceProperties(continueOnFailure); 
    }
    
    public boolean continueOnFailure() throws ParseException { 
        return toBoolean("continueOnFailure",getContinueonfailure());
    }
   
    public void setContinueonfailure(String continueOnFailure) { 
        this.continueOnFailure = continueOnFailure;
    }
    
    public String getMonitor() throws ParseException { 
        return replaceProperties(monitor); 
    }
    public boolean isMonitoring() throws ParseException { return toBoolean("monitor",getMonitor()); }  
    
    public void setMonitor(String monitor) { this.monitor = monitor; }
    
    public void processProps(ObjectIdentifier linkoid,
                             Event event, 
                             SystemRecord sr) throws ParseException { 
        event.addAttribute("oid", sr.getObjectIdentifier().toString(),true);
        event.addAttribute("link", linkoid.toString());
        event.addAttribute("size", sr.getSize());
        event.addAttribute("ctime", sr.getCreationTime());
        event.addAttribute("dtime", sr.getDeleteTime());
        event.addAttribute("hash", sr.getDataDigest());
    }
    
    /*
     * Default attributes are added.
     */
    public Event createHCOpEvent(String eventName) throws ParseException { 
        Event event = new Event(eventName);
        event.addAttribute("succeeded", "true");
        return event;
    }
   
    protected void processFailure(ObjectIdentifier linkoid,
                                 Event event, 
                                 Throwable t) throws ParseException {
        event.stop();
        event.addAttribute("oid", linkoid.toString());
        event.addAttribute("succeeded", "false");

        //TODO: need to save the stacktrace.
        event.addAttribute("exception", t.getMessage());
    }
    
    protected void connectionSetup() throws ParseException { 
        LoadSpreaderGamingSocketFactory.registerSocketFactory(getNode(), 
                                                              getDatavip(),
                                                              getPort());
    }
}
