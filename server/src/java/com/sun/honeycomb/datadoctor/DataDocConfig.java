/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.datadoctor;
import com.sun.honeycomb.cm.node_mgr.NodeMgr;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.common.InternalException;
import java.util.logging.Logger;
import java.util.Properties;
import java.beans.PropertyChangeEvent;
import com.sun.honeycomb.config.ClusterProperties;


/** Encapsulates all external info needed by DataDoctor subsystem. */
public class DataDocConfig {

  private static DataDocConfig instance = null;   // handle to singleton
  private ClusterProperties config;
  private int localNodeId;                        // from NodeManager

  // current cycle goals, indexed on taskId
  long[] cycleGoals;
  //long sloshCycleGoal;

  // configured number of parity fragments
  private static final String HC_PARITY_FRAGS   = "honeycomb.layout.parityfrags";
  int parityFrags;

  // some cycle goals have special meaning
  public static final long CG_FULL_SPEED = 600;
  public static final long CG_DONT_RUN = 0;

  // default value for cycle goal
  static final long GOAL_DEFAULT = 86400;  // 24 hrs
                                                 
  // logger has same name as this class
  private static final Logger LOG =
    Logger.getLogger(DataDocConfig.class.getName());

  /** Return the singleton, or create if doesn't exist. */
  static synchronized DataDocConfig getInstance() {

    if (instance == null) {
      instance = new DataDocConfig();
    }
    return instance;

  }

  /* ACCESSORS */
  int localNodeId() { return localNodeId; }
  long cycleGoal(int taskId) { return cycleGoals[taskId]; }
  //long sloshCycleGoal() { return sloshCycleGoal; }

  /**********************************************************************
   * If any DataDoc config values changed, re-read and return true
   **/
  boolean processPropChange(PropertyChangeEvent event) {

    boolean ddPropChange = false;
    String prop = event.getPropertyName();

    for (int i=0; i < cycleGoals.length; i++) {
      String cycleGoalProp = TaskList.cycleGoalProp(i);
      if (prop.equals(cycleGoalProp)) {
        long oldVal = cycleGoals[i];
        cycleGoals[i] = config.getPropertyAsLong(cycleGoalProp);
        LOG.info("Detected cycle goal change, "+cycleGoalProp+
                 "="+cycleGoals[i]+" (was "+oldVal+")");
        ddPropChange = true;
      }
    } 

    return ddPropChange;
  }

  /**********************************************************************
   ** Get Node Manager proxy for local node, cannot return null.
   **/
  static NodeMgrService.Proxy getNodeMgrProxy() {

    // get node manager proxy
    NodeMgrService.Proxy proxy = null;
    proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

    // if local NodeMgr unavailable, throw exception
    if (proxy == null) {
      throw new InternalException("local NodeMgr proxy is null");
    }
    return proxy;
  }


  /**********************************************************************
   * For unit testing, set instead of reading from Config service
   *
   * XXX use inheritence, that is what it is for.
   **/
  static synchronized void utInitConfig() {

    // overwrite if already exists
    instance = new DataDocConfig();
  }

  /**********************************************************************
   * Print values of private attributes read from cluster config.
   **/
  public String toString() {

    StringBuffer sb = new StringBuffer();
    sb.append("localNodeId="+localNodeId+"  ");
    for (int i=0; i < cycleGoals.length; i++) {
      sb.append(TaskList.cycleGoalProp(i)+"="+cycleGoals[i]+"  ");
    } 
    //sb.append(TaskList.sloshCycleProp()+"="+sloshCycleGoal);

    return sb.toString();
  }

  /**********************************************************************
   * Read cluster config to get values used by DataDoctor.
   **/
  private DataDocConfig() {
    cycleGoals = new long[TaskList.numTasks()];
    getClusterConfig();
    localNodeId = getNodeMgrProxy().nodeId();
  }

  /**********************************************************************
   * Create config based on given values, used for unit testing.
   **/
  private DataDocConfig(long[] configCycleGoals, int nodeId) {
    int numTasks = TaskList.numTasks();
    if (configCycleGoals.length != numTasks) {
      throw new IllegalArgumentException("given "+
                                         configCycleGoals.length+" cycleGoals, expected "+numTasks);
    }

    cycleGoals = new long[numTasks];
    for (int i=0; i < cycleGoals.length; i++) {
      cycleGoals[i] = configCycleGoals[i];
    } 
    localNodeId = nodeId;
  }

  /**********************************************************************
   * Read cluster config from Cell Manager
   **/
  private void getClusterConfig() {

      // read cycle goals from config file
      config = ClusterProperties.getInstance();
      for (int i=0; i < cycleGoals.length; i++) {
          cycleGoals[i] = readCycleGoal(TaskList.cycleGoalProp(i));
      } 
      //sloshCycleGoal = readCycleGoal(TaskList.sloshCycleProp());
      
      // get config values used to check unhealed failure count
      String s = config.getProperty(HC_PARITY_FRAGS);
      if (s == null) {
          throw new Error("cannot find property "+HC_PARITY_FRAGS);
      }
      parityFrags = Integer.parseInt(s);
  }
    
  /**********************************************************************
   * read and return the value associate with the give property
   **/
  private long readCycleGoal(String cycleGoalProp) { 

    long cycleGoal;
    if (!config.isDefined(cycleGoalProp)) {

      LOG.severe(cycleGoalProp+" undefined in your config "+ 
                 "file, using default: "+GOAL_DEFAULT+" seconds");
      cycleGoal = GOAL_DEFAULT;
    } else {
      cycleGoal = config.getPropertyAsLong(cycleGoalProp);
    }

    // error check the value
    if (cycleGoal < 0 && 
        cycleGoal != CG_FULL_SPEED && 
        cycleGoal != CG_DONT_RUN) {
            
      LOG.severe(cycleGoalProp+" value of "+cycleGoal+" is "+
                 "invalid, using default: "+GOAL_DEFAULT+" seconds");
      cycleGoal = GOAL_DEFAULT;
    }

    return cycleGoal;
  }

}
