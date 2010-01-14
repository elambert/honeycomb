package com.sun.dtf.actions.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.sun.dtf.DTFNode;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.flowcontrol.Sequence;
import com.sun.dtf.comm.rpc.Node;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;
import com.sun.dtf.util.ThreadUtil;

/**
 * @dtf.tag fetchresult
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag iterates on a remote cursor and maintains a small 
 *               pre-load cache to reduce the number of times we need to 
 *               remotely update the cursor. 
 *               
 * @dtf.tag.example
 * <component id="AGENT${agent}">
 *      <loop property="thread" range="1..${threads}" type="parallel">
 *           <loop range="1..${iterations}" property="iter">
 *                <fetchresult cursor="rcursor" recycle="true"/>
 *                <event name="dtf.perf.echo"/>
 *           </loop>
 *      </loop>
 * </component>
 *               
 */
public class Fetchresult extends Action {
    
    public final static String FETCH_RESULT_CONTEXT = "dtf.fetchresult.ctx";
    public final static String FETCH_FETCHER_CONTEXT = "dtf.fetch.fetcher.ctx";

    /**
     * @dtf.attr cursor
     * @dtf.attr.desc The remote cursor on the DTFX side that will be used to 
     *                gather results from.
     */
    private String cursor = null;
    
    /**
     * @dtf.attr recycle
     * @dtf.attr.desc Specifies if we should restart the cursor once we've hit 
     *                the end of the currently available results. If this value
     *                is set to false then a NoMoreResultsException will be 
     *                thrown. 
     */
    private String recycle = null;
   
    /**
     * @dtf.attr fetchsize
     * @dtf.attr.desc The number of elements to prefetch each time. This is just
     *                the starting value and will adjusted during runtime to fit
     *                the needs of the current operations.
     */
    private String fetchsize = "25";
    
    public Fetchresult() { 
        
    }
   
    public class Fetcher extends Thread { 
        private boolean running = true;
        private DTFException exception = null;
        
        private String thread = null;
        private ArrayList events = null;
       
        public Fetcher(String thread, ArrayList events) { 
            this.thread = thread;
            this.events = events;
        }
       
        public void timeToStop() throws DTFException { 
            running = false;
            try {
                join();
            } catch (InterruptedException e) {
                throw new DTFException("Unable to wait for fetcher thread.",e);
            }
        }
        
        public ArrayList getEvents() { return events; } 
        
        public void run() {
            try {
                int fetchSize = getFetchsize();
                DTFState state = ActionState.getInstance().getState(thread);
               
                while (running) { 
                    /*
                     * XXX: hackish way of knowing that this fetcher should 
                     *      terminate
                     */
                    if (state.isDeleted() || events == null) 
                        break;
                    
                    int fetch = (fetchSize - events.size())*2;
                    
                    if (fetch > fetchSize) 
                        fetch = fetchSize;
                        
                    if (fetch > 0)
                        preFetchResults(thread, events, fetch);
                    else 
                        ThreadUtil.pause(10);

                    synchronized(events) { 
                        events.notify();
                    }
                }
            } catch (DTFException e) { 
                exception = e;
            } finally { 
                running = false;
            }
           
            getLogger().info("Done!");
        }
        
        public void checkForException() throws DTFException { 
            if (exception != null) 
                throw exception;
        }
    }
    
    private synchronized Fetcher setupFetcher() throws DTFException { 
        Fetcher fetcher  = (Fetcher) getContext(FETCH_FETCHER_CONTEXT);
       
        if (fetcher == null) {
            final String thread = Thread.currentThread().getName();
           
            ArrayList events = getEvents(thread,getCursor());
            preFetchResults(thread, events, getFetchsize());
            
            fetcher = new Fetcher(thread, events);
            fetcher.start();
            
            registerContext(FETCH_FETCHER_CONTEXT, fetcher);
        }
        
        return fetcher;
    }

    public void execute() throws DTFException {
        Fetcher fetcher = setupFetcher();
        ArrayList events = fetcher.getEvents();
        Config config = getConfig();
        HashMap map = null;
        
        synchronized (events) { 
            while (map == null) { 
                while (events.size() == 0) { 
                    
                    if (!fetcher.isAlive()) 
                        fetcher.checkForException(); 
                        
                    long start = System.currentTimeMillis();
                    synchronized(events) {
                        try {
                            events.wait(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    long stop = System.currentTimeMillis();
                    getLogger().info("Waited " + (stop-start) + "ms.");
                } 
               
                map = (HashMap) events.remove(0);
            }
        }
        
        Iterator keys = map.keySet().iterator();
        while (keys.hasNext()) { 
            String key = (String) keys.next();
            config.setProperty(key, (String)map.get(key));
        }
    }
    
    private ArrayList getEvents(String thread, String cursor) { 
        HashMap fetchresults = getFetchResults(thread);
        ArrayList events = (ArrayList) fetchresults.get(cursor);
            
        if (events == null) { 
            events = new ArrayList();
            fetchresults.put(cursor, events);
        }
          
        return events;
    }
    
    private HashMap getFetchResults(String thread) { 
        DTFState state = ActionState.getInstance().getState(thread);
        HashMap fetchresults = (HashMap) state.getContext(FETCH_RESULT_CONTEXT);
       
        if (fetchresults == null) { 
            fetchresults = new HashMap();
            state.registerContext(FETCH_RESULT_CONTEXT, fetchresults);
        }
        
        return fetchresults;
    }
    
    private void preFetchResults(String thread, ArrayList results, int prefetch) throws DTFException { 
        if (getLogger().isDebugEnabled())
            getLogger().debug("Fetching results from remote cursor [ " + 
                              getCursor() + "]");
       
        DTFState state = ActionState.getInstance().getState(thread);
        String threadId = (String) state.getGlobalContext(Node.REMOTE_THREAD_CONTEXT);
        Sequence seq = new Sequence();
        seq.setThreadID(threadId);
       
        GetResults get = new GetResults();
        get.setCursor(getCursor());
        get.setRecycle("" + getRecycle());
        get.setPreFetch("" + prefetch);
        seq.addAction(get);
        
        String ownerId = DTFNode.getOwner().getOwner();
        long start = System.currentTimeMillis();
        Action action = Action.getComm().sendAction(ownerId, seq);
        long stop = System.currentTimeMillis();

        ArrayList events = action.findActions(Event.class);
        getLogger().info("Time to preFetch " + events.size() + 
                         " results is " + (stop-start) + "ms.");
        
        for(int i = 0; i < events.size(); i++) { 
            HashMap map = new HashMap();
            Event event = (Event) events.get(i);
            ArrayList attributes = event.findActions(Attribute.class);
           
            for(int a = 0; a < attributes.size(); a++) { 
                Attribute attribute = (Attribute) attributes.get(a);
                map.put(attribute.getName(), attribute.getValue());
            }
          
            results.add(map);
        }
        
    }

    public String getCursor() throws ParseException { return replaceProperties(cursor); }
    public void setCursor(String cursor) { this.cursor = cursor; }

    public String getRecycle() throws ParseException { return replaceProperties(recycle); }
    public boolean isRecycle() throws ParseException { return toBoolean("recycle",getRecycle()); }
    public void setRecycle(String recycle) { this.recycle = recycle; }

    public int getFetchsize() throws ParseException { return toInt("fetchsize",fetchsize); }
    public void setFetchsize(String fetchsize) { this.fetchsize = fetchsize; }
}
