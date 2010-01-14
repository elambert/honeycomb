package com.sun.dtf.actions.honeycomb.cluster;

import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag snapshot
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The snapshot tag is used to manage snapshots on the cluster 
 *               through the DTF framework. It can be used to create safe copies
 *               of all the current data on the cluster and also manages the 
 *               copying of the config so that you can really return your cluster
 *               back to the same state it was before some critical task such as
 *               recovery or disaster recovery has taken place.
 *               
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *      <snapshot name="mysnapshot" type="save" mode="move"/>
 * </component>
 *
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *      <snapshot name="mysnapshot" type="delete"/>
 * </component>
 *
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *      <snapshot name="mysnapshot" type="restore" mode="copy" node="1"/>
 * </component>
 */
public class Snapshot extends NodeAction {

    /**
     * @dtf.attr mode
     * @dtf.attr.desc one of two possible modes:
     *           
     *           <b>Snapshot Modes</b>
     *           <table border="1">
     *               <tr>
     *                   <th>Mode</th> 
     *                   <th>Description</th> 
     *               </tr>
     *               <tr>
     *                   <td>copy</td> 
     *                   <td>Copies the data and config to/from the .snapshots 
     *                       directory on each disk.</td>
     *               </tr>
     *               <tr>
     *                   <td>move</td> 
     *                   <td>Moves the data and config to/from the .snapshots 
     *                       directory on each disk.</td>
     *               </tr>
     *          </table>
     */
    private String mode = null;

    /**
     * @dtf.attr name
     * @dtf.attr.desc The name of the snapshot being handled.
     */
    private String name = null;

    /**
     * @dtf.attr type 
     * @dtf.attr.desc
     * 
     *           <b>Snapshot Type</b>
     *           <table border="1">
     *               <tr>
     *                   <th>Type</th> 
     *                   <th>Description</th> 
     *               </tr>
     *               <tr>
     *                   <td>save</td>
     *                   <td>Save the specified snapshot.</td>
     *               </tr>
     *               <tr>
     *                   <td>restore</td> 
     *                   <td>Restore the specified snapshot.</td>
     *               </tr>
     *               <tr>
     *                   <td>delete</td> 
     *                   <td>delete the specified snapshot.</td>
     *               </tr>
     *               <tr>
     *                   <td>deletedata</td> 
     *                   <td>deletes the data on the cluster, this only means that
     *                       all fragments on the cluster will be delete. None
     *                       of the snapshots will be deleted nor will the config
     *                       of the cluster be changed in anyway.
     *               </tr>
     *          </table>
     */
    private String type = null;

    /**
     * @dtf.attr disk
     * @dtf.attr.desc Identifies a single disk to do the current operation on 
     *                this is only useful for healing tests. 
     */
    private String disk = null;
    
    public Snapshot() { 
        setPreConditionChecing();
    }
    
    public void execute(NodeInterface node) throws DTFException {
        node.snapshot(getType(), getName(), getMode(), getDisk());
    }
    
    public void preCondition(NodeInterface node) throws DTFException {
        node.snapshotPreCondition(getType(), getName(), getMode(), getDisk());
    }
    
    public String getMode() throws ParseException { return replaceProperties(mode); }
    public void setMode(String mode) { this.mode = mode; }

    public String getName() throws ParseException { return replaceProperties(name); }
    public void setName(String name) { this.name = name; }

    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }

    public String getDisk() throws ParseException { return replaceProperties(disk); }
    public void setDisk(String disk) { this.disk = disk; }
}
