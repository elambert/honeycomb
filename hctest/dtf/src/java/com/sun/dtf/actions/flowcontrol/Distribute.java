package com.sun.dtf.actions.flowcontrol;

import com.sun.dtf.actions.Action;
import com.sun.dtf.distribution.DistWorkState;
import com.sun.dtf.distribution.Distribution;
import com.sun.dtf.distribution.DistributionFactory;
import com.sun.dtf.distribution.Worker;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.state.DTFState;
import com.sun.dtf.util.TimeUtil;

/**
 * @dtf.tag distribute
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The distribute tag allows you to efficiently distribute action
 *               execution over time. This tag can attempt to simulate execution
 *               distributions and record events when desired distributions were
 *               not acheived during the execution of underlying actions.
 *               
 *               MORE TO COME SOON.
 * 
 * @dtf.tag.example 
 */
public class Distribute extends Action {

    /**
     * @dtf.attr 
     * @dtf.attr.desc
     */
    private String workers = null;
    
    /**
     * @dtf.attr 
     * @dtf.attr.desc
     */
    private String property = null;
    
    /**
     * @dtf.attr 
     * @dtf.attr.desc
     */
    private String iterations = null;
    
    /**
     * @dtf.attr 
     * @dtf.attr.desc
     */
    private String timer = null;
    
    /**
     * @dtf.attr 
     * @dtf.attr.desc
     */
    private String func = null;
    
    /**
     * @dtf.attr 
     * @dtf.attr.desc
     */
    private String unit = null;
   
    /**
     * @dtf.attr 
     * @dtf.attr.desc
     */
    private String id = null;

    public Distribute() { }

    public void execute() throws DTFException {
        Action child = (Action) children().get(0);
        int workerCount = getWorkers(); 
        
        Worker[] workers =  new Worker[workerCount];
        DistributionFactory df = DistributionFactory.getInstance();
        Distribution dist = null;

        long interval = -1;
        
        if (getTimer() != null)
            interval = TimeUtil.parseTime("timer",getTimer());
        
        long unitWork = -1;

        if (getUnit() != null)
            unitWork = TimeUtil.parseTime("unit",getUnit());

        if (getFunc() != null)
            dist = df.getDistribution(getFunc());
        
        DistWorkState dstate = new DistWorkState(getIterations(),
                                                     interval,
                                                     unitWork,
                                                     workerCount,
                                                     getId(),
                                                     getProperty(),
                                                     dist);

        for (int i = 0 ; i < workerCount; i++)  {
            DTFState state = getState().duplicate();
            workers[i] = new Worker(child, dstate, state, i);
        }
        
        for (int i = 0 ; i < workerCount; i++)  {
            workers[i].start();
        }
        
        dstate.waitForFinish();
    }

    public String getProperty() throws ParseException { return replaceProperties(property); }
    public void setProperty(String property) { this.property = property; }

    public int getWorkers() throws ParseException { return toInt("workers", replaceProperties(workers)); }
    public void setWorkers(String workers) { this.workers = workers; }

    public String getTimer() throws ParseException { return replaceProperties(timer); }
    public void setTimer(String timer) { this.timer = timer; }
    
    public String getUnit() throws ParseException { return replaceProperties(unit); }
    public void setUnit(String unit) { this.unit = unit; }
    
    public long getIterations() throws ParseException { return toLong("iterations", replaceProperties(iterations),-1); }
    public void setIterations(String iterations) { this.iterations = iterations; }

    public String getFunc() throws ParseException { return replaceProperties(func); }
    public void setFunc(String func) { this.func = func; }

    public String getId() throws ParseException { return replaceProperties(id); }
    public void setId(String id) { this.id = id; }
}
