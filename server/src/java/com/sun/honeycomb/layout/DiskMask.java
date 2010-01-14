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



package com.sun.honeycomb.layout;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertException;

import com.sun.honeycomb.common.InternalException;

import java.util.BitSet;
import java.util.ArrayList;

/**
 * Indicates which disks are currently available in the cluster.
 */
public class DiskMask implements AlertComponent, Codable, Cloneable {

    /*
     * Disk is mounted and available for all honeycomb purposes.
     */
    private BitSet enabledDisks = null;

    /*
     * Disk can bet set to unavailble here for adminstrative purposes, 
     * both other tasks/features in honeycomb stack. By setting this disk
     * to unavailable we will no longer use it in layout until it has
     * been set back to available.
     */
    private BitSet availableDisks = null;

    public DiskMask() {
        nodes = LayoutConfig.NODES_PER_CELL;
        disks = LayoutConfig.DISKS_PER_NODE;

        enabledDisks = new BitSet(size());
        availableDisks = new BitSet(size());

        enabledDisks.set(0, size(), false);   // default to off
        availableDisks.set(0, size(), false); // default to off
    }

    /** ignore bits added for word-alignment */
    public int size() {
        return nodes * disks;
    }

    public int enabledDisks() {
        if (enabledDisks != null) {
            return enabledDisks.cardinality();
        } else {
            return 0;
        }
    }

    public boolean equals(Object obj) { 
        if (obj instanceof DiskMask) { 
            DiskMask other = (DiskMask)obj;
            if (enabledDisks.equals(other.enabledDisks) &&
                availableDisks.equals(other.availableDisks)) 
                return true;
        }

        return false;
    }

    public Object clone() {
        DiskMask cloneMask = new DiskMask();
        cloneMask.or(this);
        return cloneMask;
    }


    /*
     * Accessors for online/offline status
     */
    public boolean get(int disk) { 
        return (enabledDisks.get(disk) && availableDisks.get(disk));
    }

    public void setAvailable(int disk) { setAvailable(disk,true); }
   
    public void setAvailable(int disk, boolean value) { 
        availableDisks.set(disk, value); 
    }

    public void setAvailable(int start, int end, boolean value) { 
        availableDisks.set(start, end, value); 
    }

    public void setEnabled(int nodeId, int diskId) { 
        setEnabled(diskIdToIndex(new DiskId(nodeId, diskId)));
    }

    public void setEnabled(int disk) { setEnabled(disk, true); }
    
    public void setEnabled(int disk, boolean value) { 
        enabledDisks.set(disk, value); 
    }

    public void setEnabled(int start, int end, boolean value) { 
        enabledDisks.set(start, end, value); 
    }

    public boolean isAvailable(int disk) { 
        return availableDisks.get(disk); 
    }

    private void set(int disk) { set(disk,true); }

    private void set(int disk, boolean value) { 
        setAvailable(disk, value);
        setEnabled(disk, value);
    }

    private void set(int start, int end, boolean value) { 
        setAvailable(start, end, value);
        setEnabled(start, end, value);
    }

    public boolean isEmpty() { return enabledDisks.isEmpty(); }

    public void clear() { 
        enabledDisks.clear();
        availableDisks.clear();
    }

    public void flip(int disk) { 
        enabledDisks.flip(disk);
        availableDisks.flip(disk);
    }

    public void flip(int start, int end) { 
        enabledDisks.flip(start, end);
        availableDisks.flip(start, end);
    }

    public int length() { return enabledDisks.length(); }  

    public DiskMask and(DiskMask d) { 
        this.enabledDisks.and(d.enabledDisks);
        this.availableDisks.and(d.availableDisks);
        return this;
    }

    public DiskMask andNot(DiskMask d) { 
        this.enabledDisks.andNot(d.enabledDisks);
        this.availableDisks.andNot(d.availableDisks);
        return this;
    }

    public DiskMask or(DiskMask d) { 
        this.enabledDisks.or(d.enabledDisks);
        this.availableDisks.or(d.availableDisks);
        return this;
    }

    // public
    public boolean isOnline(int nodeId, int diskIndex) {
        return get(diskIdToIndex(new DiskId(nodeId, diskIndex)));
    }
    public boolean isOffline(int nodeId, int diskIndex) {
        return !get(diskIdToIndex(new DiskId(nodeId, diskIndex)));
    }
    public boolean isOnline(DiskId d) { return get(diskIdToIndex(d)); }
    public boolean isOffline(DiskId d) { return !get(diskIdToIndex(d)); }

    // list of all online disks 
    public DiskIdList onlineDiskIds() {
        DiskIdList diskIdList = new DiskIdList();
        for (int i=0; i < size(); i++) {
            if (get(i)) {
                diskIdList.add(indexToDiskId(i));
            }
        } 
        return diskIdList;
    }

    // list of all offline disks
    DiskIdList offlineDiskIds() {

        DiskIdList list = new DiskIdList();
        for (int i=0; i < size(); i++) {
            if (!get(i)) {
                list.add(indexToDiskId(i));
            }
        }
        return list;
    }

    // list of online disks on given node
    public DiskIdList localOnlineDiskIds(int nodeId) {
        DiskIdList diskIdList = new DiskIdList();
        for (int i=0; i < size(); i++) {
            DiskId d = indexToDiskId(i);
            if (get(i) && d.nodeId() == nodeId) {
                diskIdList.add(indexToDiskId(i));
            }
        } 
        return diskIdList;
    }

    // list of online disks that are NOT on the given node
    public DiskIdList remoteOnlineDiskIds(int localNodeId) {
        DiskIdList diskIdList = new DiskIdList();
        for (int i=0; i < size(); i++) {
            if (get(i)) {
                DiskId d = indexToDiskId(i);
                if (d.nodeId() != localNodeId) {
                    diskIdList.add(d);
                }
            }
        } 
        return diskIdList;
    }

    /** return all online disks in current disk mask, can be null */
    public Disk[] onlineDisks() {

        ArrayList diskList = new ArrayList();
        for (int i=0; i < length(); i++) {
            if (get(i)) {
                Disk d = DiskProxy.getDisk(indexToDiskId(i));
                diskList.add(d);
            }
        }

        if (diskList.isEmpty()) {
            return null;
        }

        Disk[] disks = new Disk[diskList.size()];
        disks = (Disk[]) diskList.toArray(disks);
        return disks;
    }

    /*
     * Utility function used by layout.
     *
     * If there is any available disk above the 32nd disk
     * in the available disk mask then we have an 8 node
     * DiskMask.
     */
    public boolean is8Node() { 
        for (int i = 32; i < 64; i++) { 
            if (availableDisks.get(i))
                return false;
        }
        return true;
    }

    /*
     * Methods to change online/offline status of disks.
     */

    // public access since Data Doctor uses masks
    
    /*
     * Sets disks online which includes setting available and 
     * enabled mask at the same time.
     */
    public void setOnline(DiskId d) { set(diskIdToIndex(d)); }

    public void setOffline(DiskId d) { set(diskIdToIndex(d), false); }
    public void setOnline(int nodeId, int diskIndex) {
        set(diskIdToIndex(new DiskId(nodeId, diskIndex)));
    }
    public void setOffline(int nodeId, int diskIndex) {
        set(diskIdToIndex(new DiskId(nodeId, diskIndex)), false);
    }
    public void setOnline(int nodeId) { 
        if (nodeId - LayoutConfig.BASE_NODE_ID >= nodes) {
            throw new IllegalArgumentException("nodeId "+nodeId+
                    " invalid, DiskMask only has "+nodes+" nodes");
        }
        for (int diskIndex=0; diskIndex < disks; diskIndex++) {
            setOnline(nodeId, diskIndex);
        } 
    }

    /*
     * Unit testing interfaces
     */

    /** set all bits online, for unit testing only! */
    void utAllOnline() {
        set(0, size(), true);
    }

    /** for unit testing, Honeycomb callers use getOnlineDisks */
    DiskIdList utOnlineDiskIds() {

        DiskIdList list = new DiskIdList();
        for (int i=0; i < length(); i++) {
            if (get(i)) {
                list.add(indexToDiskId(i));
            }
        }
        return list;
    }
    
    /** for unit testing */
    public DiskIdList utOfflineDiskIds() {

        DiskIdList list = new DiskIdList();
        for (int i=0; i < size(); i++) {
            if (!get(i)) {
                list.add(indexToDiskId(i));
            }
        }
        return list;
    }



    /** used by Layout service to create the disk mask */
    void setOnline(DiskIdList list) {
        for (int i=0; i < list.size(); i++) {
            setOnline((DiskId)list.get(i));
        }
    }
    void setOffline(DiskIdList list) {
        for (int i=0; i < list.size(); i++) {
            setOffline((DiskId)list.get(i));
        }
    }


    public String toString() {
        return toString (true);
    }

    public String toString(boolean pretty) {
        BitSet mask = (BitSet)enabledDisks.clone();
        mask.and(availableDisks);
        return getMask(mask,pretty);
    }

    private String getMask(BitSet bitset, boolean pretty) { 
        StringBuffer sb = new StringBuffer();
        boolean foundOne = false;

        if (pretty)
            sb.append("Online Disks: ");

        for (int i=0; i < LayoutConfig.NODES_PER_CELL; i++) {
            int n = LayoutConfig.BASE_NODE_ID + i;
            for (int d=0; d < LayoutConfig.DISKS_PER_NODE; d++) {
                if(bitset.get(diskIdToIndex(new DiskId(n,d)))) {
                    foundOne = true;
                    sb.append(n);
                    sb.append(":");
                    sb.append(d);
                    sb.append(" ");
                }
            }
        }

        if (!foundOne && pretty) {
            sb.append("<none>");
        }

        return sb.toString();
    }

    public String getEnabledMask() { 
        return getMask(enabledDisks, false);
    }

    public String getAvailableMask() { 
        return getMask(availableDisks, false);
    }

    public void setEnabledMask(String mask) throws InternalException { 
        enabledDisks = parseMask(mask);
    }

    public void setAvailableMask(String mask) throws InternalException { 
        availableDisks = parseMask(mask);
    }

    private BitSet parseMask(String mask) throws InternalException { 
        BitSet bitset = new BitSet(size());
        String[] disks = mask.split("\\s");
        for (int i = 0; i < disks.length; i++) {
            try {
                int nodeId = Integer.parseInt(disks[i].substring(0, 3));
                int diskId = Integer.parseInt(disks[i].substring(4));
                bitset.set(diskIdToIndex(new DiskId(nodeId, diskId)));
            } catch (Exception e) {
                throw new InternalException("unable to parse mask ("
                                   + mask + "): " + e.getMessage());
            }
        }
        return bitset;
    }

    /** translate between the (nodeId,diskIndex) and bitset index */
    private DiskId indexToDiskId(int index) {
        int nodeIndex = index / disks;
        int nodeId = LayoutConfig.BASE_NODE_ID + nodeIndex;
        return new DiskId(nodeId, index % disks);
    }
    private int diskIdToIndex(DiskId d) {
        int nodeId = d.nodeId();
        int nodeIndex = nodeId - LayoutConfig.BASE_NODE_ID;
        return (nodeIndex * disks) + d.diskIndex();
    }


    /* Codable API */

    public int encodedSizeBytes() {
        // bitset plus two ints, plus buffer
        return (size()/8 + 1) + (32 * 2) + 4; 
    }

    public void encode(Encoder encoder) {
        encoder.encodeInt(nodes);
        encoder.encodeInt(disks);
    }

    public void decode(Decoder decoder) {
        nodes = decoder.decodeInt();
        disks = decoder.decodeInt();
    }


    /*
     * Alert API
     */
    public int getNbChildren() {
        return 1;
    }

    public AlertProperty getPropertyChild(int index)  
            throws AlertException {
        AlertProperty prop = null;
        if (index == 0) {
            prop = new AlertProperty("currentMap", AlertType.STRING);
        }
        return prop;
    }
    public String getPropertyValueString(String property)  
            throws AlertException {
        if (property.equals("currentMap")) {
            return super.toString();
        } else {
        throw new AlertException("property " + property +
                                 " does not exist");
        }
    }
    public boolean getPropertyValueBoolean(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public int getPropertyValueInt(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public long getPropertyValueLong(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public float getPropertyValueFloat(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public double getPropertyValueDouble(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public AlertComponent getPropertyValueComponent(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }    

    private int nodes;
    private int disks;

}

