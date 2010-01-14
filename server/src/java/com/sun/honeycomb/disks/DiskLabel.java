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



package com.sun.honeycomb.disks;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.logging.*;
import java.util.zip.Adler32;
import java.text.MessageFormat;
import java.util.ResourceBundle;

// These are needed for the unit tests
import com.sun.honeycomb.common.Getopt;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.sysdep.DiskOps;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.ExtLevel;

public class DiskLabel implements Serializable {

    private static Logger logger = Logger.getLogger(DiskLabel.class.getName());

    public  static final int BLOCK_SIZE   = 512;
    public  static final int LABEL_OFFSET = BLOCK_SIZE*62;
    public  static final int LABEL_LENGTH = BLOCK_SIZE;
    private static final long LABEL_MAGIC  = 0x0F0BAF0BAF0BAF0BL;
    private static final byte LABEL_VERSION = 4;

    // The various versions for various features
    private static final byte LABEL_VERSION_DISABLEDISK  = 2;
    private static final byte LABEL_VERSION_NODEID       = 3;
    private static final byte LABEL_VERSION_INCARNATIONS = 4;

    // The incarnation of all disks on the system
    private static long sysIncarnation = -1;

    // A map from disk Id -> DiskLabel
    private static Map diskLabels = null;

    // All the disks in the system
    private static List paths = null;

    // Local node's ID
    private static short localCellId = -1;
    private static short localSiloId = -1;
    private static short localNodeId = -1;

    private int     cellId;
    private short   siloId;
    private short   nodeId;

    private short   diskIndex;
    private String  device;
    private boolean disabled;
    private long    incarnation;

    // Label should be written after disk replacement finishes
    private boolean delayedWrite;

    // This is the mapping from disk device name to slot number.
    private static Map slotMap = null;
    private static final int DISK_SLOT_SIZE = 6;
    static {
        slotMap = new HashMap();

        // Each string here (disk name) is of length DISK_SLOT_SIZE
        slotMap.put("c0t0d0", new Short((short)0));
        slotMap.put("c0t1d0", new Short((short)1));
        slotMap.put("c1t0d0", new Short((short)2));
        slotMap.put("c1t1d0", new Short((short)3));

        // WARNING! The disk_replacement script also has this info.
        // If the sequence changes, change it there too.
    }

    private DiskLabel() {
        this(-1,	// cellId
            (short)-1,	// siloId
            (short)-1,	// nodeId
            (short)-1,	// diskIndex
            "",		// device
            false,	// disabled
            -1L,	// incarnation
            false,	// dirty
            false);	// delayedWrite
    }


    // Method to return label string for the disklabel command.
    // If this is modified please vist CommandDiskLabel.java and updateDiskLabel() below.
    // Sample output:
    //  [0,0,108,3;1] /dev/rdsk/c1t1d0p0 (enabled)
    // Gathers specific members for the cli only.

    public String diskLabelCliString() {
        StringBuffer sb = new StringBuffer("[");
        sb.append(Long.toHexString(cellId)).append(',');
        sb.append(siloId).append(',');
        sb.append(nodeId).append(',');
        sb.append(diskIndex).append(';');
        sb.append(incarnation);
        sb.append("] ").append(device);
        if (disabled)
            sb.append(" (disabled)");
        else 
            sb.append(" (enabled)");
        return sb.toString();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("[");

        // Identify foreign values with a prefixed asterisk

        if (cellId != localCellId)
            sb.append('*');
        sb.append(Long.toHexString(cellId)).append(',');

        if (siloId != localSiloId)
            sb.append('*');
        sb.append(siloId).append(',');

        if (nodeId != localNodeId)
            sb.append('*');
        sb.append(nodeId).append(',');

        if (diskIndex != getSlotIndex(device))
            sb.append('*');
        sb.append(diskIndex).append(';');

        sb.append(incarnation);

        sb.append("] ").append(device);
        if (isForeign())
            sb.append(" (foreign)");
        else if (disabled)
            sb.append(" (disabled)");

        return sb.toString();

    }

    // This is used just for formatted output according to the format:
    //
    // CellId: {0} SiloId {2} NodeId: {3} disk Index: {4} Enabled: {5}
    // Incarnation: {6} device:{7}
    //
    // Used by disklabel cli to print the formatted label.
    // 
    public Object formatDiskLabel() {
        Object args = new Object[] {
                          Integer.toString(this.cellId()),
                          Integer.toString(this.siloId()),
                          Integer.toString(this.nodeId()),
                          Integer.toString(this.diskIndex()),
                          Boolean.toString(this.isDisabled()),
                          Long.toString(this.incarnation()),
                          this.getDevice(),
        };
        return args;
    }

    // Update a diskLabel from a string. Used by disklabel command to 
    // set new label parameters.
    // Sample label format recieved here:
    //  [0,0,106,3;1] /dev/rdsk/c1t1d0p0 (enabled)
    public void updateDiskLabel(String labelString, boolean commit) throws IOException {

        logger.info("updateDiskLabel: New Label = " + labelString);
        StringTokenizer st = new StringTokenizer (labelString, ",[];() ");
        String cellId = st.nextToken().trim();
        String siloId = st.nextToken().trim();
        String nodeId = st.nextToken().trim();
        String diskIndex = st.nextToken().trim();
        String incarnation = st.nextToken().trim();
        String device = st.nextToken().trim();
        String diskState = st.nextToken().trim();

        // Move them to the label
        this.cellId = Integer.parseInt(cellId);
        this.siloId = Short.parseShort(siloId);
        this.nodeId = Short.parseShort(nodeId);
        this.diskIndex = Short.parseShort(diskIndex);
        this.device = device;
        this.incarnation = Long.parseLong(incarnation);
        if (diskState.equalsIgnoreCase("disabled")) {
            this.disabled = true;
        } else {
            this.disabled = false;
        }

        if (commit) {
            this.writeLabel();
        } 
    }

    public void writeLabel() throws IOException {
        if (logger.isLoggable(Level.INFO))
            logger.info("Writing out label " + toString());

        writeLabel(device, toByteBuffer());

        if (cellId < 0 || siloId < 0 || nodeId <= 0)
            throw new RuntimeException("Un-initialized!");

        if (logger.isLoggable(Level.INFO))
            logger.info("Disklabel " + this + " written to disk.");
    }

    /** Readonly access to cellId */
    public int cellId() { return cellId; }

    /** Readonly access to siloId */
    public short siloId() { return siloId; }

    /** Readonly access to nodeId */
    public short nodeId() { return nodeId; }

    /** Readonly access to diskIndex */
    public short diskIndex() { return diskIndex; }

    /** Readonly access to incarnation */
    public long incarnation() { return incarnation; }

    /** Readonly access to device name */
    public String getDevice() { return device; }

    /** Readonly access to disabled state */
    public boolean isDisabled() { return disabled; }

    /** Readonly access to system incarnation number */
    public static long getSysIncarnation() { return sysIncarnation; }

    /** Mark the disk as disabled */
    public void setDisabled(boolean v) { disabled = v; }

    // The following setter methods are only for testing purpose by the
    // 'disklabel' command.

    /** set the cell Id */
    public void setCellId(Integer c) { cellId = c; }

    /** set the silo Id */
    public void setSiloId(Short s) { siloId = s; }

    /** set the node Id */
    public void setNodeId(Short n) { nodeId = n; }

    /** set the Disk Index */
    public void setDiskIndex(Short d ) { diskIndex = d; }

    /** Set the incarnation **/
    public void setLabelIncarnation(long incarnation) {
        this.incarnation = incarnation;
    }

    /** set device name */
    public void setDevice(String device) { this.device = device; }

    /** Readonly access to delayedWrite */
    public boolean isDelayedWrite() { return delayedWrite; }

    /** Corrupt the label so it no longer looks valid */
    public void smash() {
        ByteBuffer buf = ByteBuffer.allocate(LABEL_LENGTH);
        buf.clear();

        // debug
        logger.log(Level.INFO, "smashing label on " + device);

        try {
            writeLabel(device, buf);
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                       "Couldn't label disk " + toString(), e);
        }
    }

    /** Test whether the label belongs to the node specied */
    public boolean isOnNode(int cellId, short siloId, short nodeId, long inc) {
        if (inc > 0 && this.incarnation != inc)
            return false;

        return cellId() == cellId && siloId() == siloId &&
            nodeId() == nodeId;
    }

    // If the disk is not from this node or the label's diskIndex
    // does not match the slot, this is a Foreign disk.
    public boolean isForeign() {
        if (!isOnNode(localCellId, localSiloId, localNodeId, -1))
            return true;

        return diskIndex() != getSlotIndex(getDevice());
    }

    /**
     * Get the active disks on the system
     *
     * @return Map from disk device name to disk label
     */
    static synchronized Map probeDisks(int cellId, short siloId, short nodeId,
                                       List diskPaths, boolean eraseAll)
            throws IOException {

        paths = diskPaths;
        sysIncarnation = -1;
        localCellId = (short) cellId;
        localSiloId = siloId;
        localNodeId = nodeId;

        diskLabels = new HashMap(); // this is returned

        if (logger.isLoggable(Level.INFO))
            logger.info("Probed disks: " + paths);

        if (eraseAll) {
            // Wiping all disks. Reset incarnation number
            sysIncarnation = 1;

            // No need to check labels etc.; just write labels out
            // (this will result in disk_replacement not running) and
            // add the label to diskLabels.
            for (Iterator p = paths.iterator(); p.hasNext(); ) {
                String path = (String) p.next();

                DiskLabel label = assignLabel(cellId, siloId, nodeId, path,
                                              false); // Immediate write
                diskLabels.put(label.device, label);
            }
        }
        else {
            for (Iterator i = paths.iterator(); i.hasNext(); )
                // checkAndGetLabel inserts labels into diskLabels also
                checkAndGetLabel((String) i.next(), nodeId);

            // If no disk had a label with an incarnation number, start
            // the count
            if (sysIncarnation <= 0)
                sysIncarnation = 1;

            // Ensure that the label incarnation numbers
            // are up to date and create label objects
            // for new disks.  In the case of new disks,
            // the labels will be committed to disk after
            // replacement processing is done.
            for (Iterator i = paths.iterator(); i.hasNext(); )
                labelDisk((String)i.next(), nodeId);
        }

        return diskLabels;
    }

    /**
     * Read a disk's label and return it after doing consistency
     * checks and adding it to the list of known labels. 
     *
     * Note that any valid label is returned -- this is not only for
     * disks that actually belong on this node. The intent is that we
     * always know (and remember) everything about the disk, even if
     * we're not going to use it. This way the CLI always presents
     * useful information that the admin can use to put the disks in
     * their correct places.
     * 
     * If there is no valid label on the disk, return null.
     */
    public static DiskLabel checkAndGetLabel(String device, short nodeId) {
        if (logger.isLoggable(Level.INFO))
            logger.info("Trying to get label from " + device);

        // Device have a good label?
        DiskLabel label = null;
        try {
            if ((label = DiskLabel.readValidateLabel(device, nodeId)) == null)
                return null;
        } catch(IOException ioe) {
            logger.log(Level.WARNING, "Bad label on " + device, ioe);
            return null;
        }

        if (sysIncarnation <= 0)
            sysIncarnation = label.incarnation();

        // Here, sysIncarnation could still be 0. This can happen
        // if the first k disks all have old disklabels, which get
        // promoted, and their incarnation is set to 0 -- since we
        // haven't yet found a disk with the v3 disklabel, we
        // don't know what the sysIncarnation is. Later, when we
        // label unlabelled disks, we'll fix any unset incarnation
        // numbers also.

        // Does this disk belong to this node?
        if (!label.isOnNode(localCellId, localSiloId, localNodeId,
                            sysIncarnation)) {
            logger.severe("Found " + label + " -- expected " +
                          "[" + Long.toHexString(localCellId) + ":" +
                          localSiloId + ":" + localNodeId + ":x;" +
                          sysIncarnation + "], skipping");
        }
        else if (label.isForeign()) {
            logger.severe("Disk " + label + " is in the wrong slot " +
                          getSlotIndex(device) + " (" + device + ")");
        }
        else if (logger.isLoggable(Level.INFO))
            logger.info("Good disk " + label);

        diskLabels.put(label.device, label);
        return label;
    }

    /**
     * Write the disk label information to the disk.
     */
    public void commitLabel() {
        if (logger.isLoggable(Level.INFO))
            logger.info("Commiting label for " + device);

        delayedWrite = false;
        try {
            writeLabel();        // Write it out
        } catch(IOException ioe) {
            if (logger.isLoggable(Level.INFO))
                logger.log(Level.INFO, device, ioe);
        }
    }

    /**
     * Label unlabelled disk.
     */
    static private void labelDisk(String device, short nodeId) {
        if (logger.isLoggable(Level.INFO))
            logger.info("Labelling " + device);

        // We don't want to write a label now for a disk w/out
        // a label, since the presence of a label will prevent
        // the disk_replacement script from properly setting up
        // a new disk.  We will create a label object, but will
        // commit the label to the disk after replacement processing
        // is done.

        DiskLabel label = null;
        try {
            label = DiskLabel.readValidateLabel(device, nodeId);
        } catch(IOException ioe) {
            logger.log(Level.FINE, "Problem reading " + device, ioe);
        }

        if (label != null) {
            // We have a label, bring the sysIncarnation up to date.
            if (label.incarnation() <= 0)
                label.setIncarnation(sysIncarnation);
        } else {
            // Create the label, but write it out later.
            label = DiskLabel.assignLabel(localCellId, localSiloId,
                localNodeId, device, true /* delayedWrite */);
            diskLabels.put(label.device, label);
        }
    }

    /**
     * A new disk has just been detected; it is labelled here.
     */
    public static void newDisk(int cellId, short siloId,
                               short nodeId, String device) {
        // Check if there's a pre-existing label on the disk
        // TODO XXX

        if (logger.isLoggable(Level.INFO))
            logger.info("New disk detected: " + device);
        ResourceBundle rs = BundleAccess.getInstance().getBundle();
        String str = rs.getString("info.disk.label.newdisk");
        Object [] args = {new String(device)};
        logger.log(ExtLevel.EXT_INFO, MessageFormat.format(str, args));


        DiskLabel.incrSysIncarnation();
        DiskLabel label =
            DiskLabel.assignLabel(cellId, siloId, nodeId, device,
                false /* delayedWrite */);
        diskLabels.put(label.device, label);
        paths.add(label.device);
    }

    /**
     * Clean up after a disk has been removed.
     */
    public static void deleteDisk(String device) {
        DiskLabel label = (DiskLabel) diskLabels.get(device);

        if (logger.isLoggable(Level.INFO))
            logger.info("Deleting disk " + device);

        if (label == null) {
            logger.log(Level.WARNING, "should have found label for " +
                device);
            return;
        }

        diskLabels.remove(device);
        paths.remove(device);
    }

    /**
     * Increment the incarnation number on all disks
     */
    public static long incrSysIncarnation() {
        sysIncarnation++;
        for (Iterator i = paths.iterator(); i.hasNext(); ) {
            String device = (String) i.next();
            DiskLabel label = (DiskLabel) diskLabels.get(device);
            if (label == null)
                logger.severe("No label in map for disk " + device);
            else
                label.setIncarnation(sysIncarnation);
        }

        return sysIncarnation;
    }

    /**
     * Translate a disk device path to slot number. Disk paths are
     * assumed to be of the form "/dev/dsk/c1t0d0p0" (or
     * "/dev/rdsk/...)". The slot numbering is:
     *    c0t0d0 c0t1d0 c1t0d0 c1t1d0
     */
    public static short getSlotIndex(String dev) {
        String device = dev.replaceAll("^/dev/r?dsk/", "");
        Object slot = slotMap.get(device.substring(0, DISK_SLOT_SIZE));
        if (slot instanceof Short)
            return ((Short)slot).shortValue();

        throw new InternalException("Bad value in slotMap <" + slot + "> for "
                                    + device);
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // Private methods

    /**
     * Bump up the incarnation number 
     */
    private void setIncarnation(long incarnation) {
        this.incarnation = incarnation;
        try {
            writeLabel();
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                       "Couldn't label disk " + toString(), e);
        }
    }

    /**
     * Assign a disk Id and write a new label to the disk with it
     */
    private static DiskLabel assignLabel(int cellId, short siloId,
        short nodeId, String device, boolean writeLabelLater) {

        DiskLabel label = (DiskLabel) diskLabels.get(device);
        if (label != null) {
            throw new RuntimeException("Disk " + device +
                                       " already has a label!");
        }

        // The diskIndex is the slot the disk is plugged into.

        label = new DiskLabel(cellId, siloId, nodeId, getSlotIndex(device),
            device,
            false /* isDisabled */, 
            sysIncarnation,
            true /* isDirty */, writeLabelLater);

        String msg = (writeLabelLater ?
            " created but not written to disk" :
            " written to disk");

        if (logger.isLoggable(Level.INFO))
            logger.info("Disklabel " + label + msg);

        return label;
    }

    /** Private constructor used internally, sets default non-deferred write */
    private DiskLabel(int cellId, short silo, short node, short diskIndex,
        String device, boolean isDisabled, long incarnation, boolean isDirty) {
        this(cellId, silo, node, diskIndex, device, isDisabled, incarnation,
            isDirty, /* Default: don't defer the write */ false);
    }

    /** Private constructor used internally */
    private DiskLabel(int cellId, short silo, short node, short diskIndex,
                String device, boolean isDisabled, long incarnation, 
        boolean isDirty, boolean writeLabelLater) {

        this.cellId  = cellId;
        this.siloId = silo;
        this.nodeId = node;
        this.diskIndex = diskIndex;
        this.device = device;
        this.incarnation = incarnation;
        this.disabled = isDisabled;
        this.delayedWrite = writeLabelLater;

        if (isDirty && !writeLabelLater) {
            try {
                writeLabel();        // Write it out
            } catch(IOException ioe) {
                if (logger.isLoggable(Level.INFO))
                    logger.log(Level.INFO, device, ioe);
            }
        }
    }

    /**
     * Read a disklabel and check, promoting to latest version if
     * necessary. Arguments are only used if promoting.
     */
    public static DiskLabel readValidateLabel(String device, short myId)
            throws IOException {
        return getLabel(device, myId, true);
    }

    /**
     * Read a disklabel and check, promoting to latest version if
     * necessary. Arguments are only used if promoting.
     */
    private static DiskLabel getLabel(String device, short myId,
                                      boolean update)
            throws IOException {
        if (logger.isLoggable(Level.INFO))
            logger.info("Reading label from " + myId + ":" + device);

        ByteBuffer buf = readLabel(device);

        // Is there an invalid or nonexistent DiskLabel?
        if (buf == null || !isValid(device, buf)) {
            if (logger.isLoggable(Level.INFO))
                logger.info("No label for " + device);
            return null;
        }

        boolean isDirty = false;
        int version = buf.getInt();
        if (version != LABEL_VERSION) {
            if (version > 0 && version <= LABEL_VERSION) {
                // We can promote it
                isDirty = update;
                if (logger.isLoggable(Level.INFO))
                    logger.info(device + ": promoting disk label from v" +
                                version);
            }
            else {
                if (logger.isLoggable(Level.WARNING))
                    logger.warning(device + ": disk label version mismatch: " +
                                   version);
                return null;
            }
        }
        else
            if (logger.isLoggable(Level.FINE))
                logger.fine(device + ": disk label version OK");

        int cellId = buf.getInt();
        short siloId = buf.getShort();

        short nodeId = -1;
        if (version >= LABEL_VERSION_NODEID)
            nodeId = buf.getShort();
        if (nodeId <= 0) {
            logger.warning("Disk " + device + " has nodeId 0; setting to " +
                           myId);
            nodeId = myId;
            isDirty = update;
        }

        short diskIndex = buf.getShort();

        boolean isDisabled = false;
        if (version >= LABEL_VERSION_DISABLEDISK)
            isDisabled = (buf.getShort() == 1);
        else
            isDirty = update;

        if (logger.isLoggable(Level.FINE))
            logger.fine(device + " is " +
                        (isDisabled? "" : "not ") + "disabled");

        long incarnation = 0;
        if (version >= LABEL_VERSION_INCARNATIONS)
            incarnation = buf.getLong();
        else
            isDirty = update;

        short slotNum = getSlotIndex(device);
        if (slotNum != diskIndex)
            logger.warning("Disk in slot " + slotNum + " (" + device +
                           ") has label from slot " + diskIndex);

        return new DiskLabel(cellId, siloId, nodeId, diskIndex, device,
            isDisabled, incarnation, isDirty);
    }

    public static boolean hasValidLabel(String device) {
        ByteBuffer buf = null;
        try {
            buf = readLabel(device);
        } catch (IOException e) {
        }

        if (buf == null)
            return false;

        return isValid(device, buf);
    }

    /** Validate a label */
    private static boolean isValid(String device, ByteBuffer buf) {
        if (isZero(buf.duplicate())) {
            if (logger.isLoggable(Level.INFO))
                logger.info(device + ": no disk label");
            return false;
        }

        String errmsg = device + ": invalid disk label";

        long checksum = buf.getLong(LABEL_LENGTH - 8);
        if (checksum != checksum(buf.duplicate())) {
            if (logger.isLoggable(Level.INFO))
                logger.info(errmsg + " checksum");
            return false;
        }

        long magic = buf.getLong();
        if (magic != LABEL_MAGIC) {
            if (logger.isLoggable(Level.INFO))
                logger.info(errmsg + " magic " + Long.toHexString(magic));
            return false;
        }

        return true;
    }

    /** Read a disklabel */
    private static ByteBuffer readLabel(String device) throws IOException {
        if (logger.isLoggable(Level.INFO))
            logger.info("Trying to read label from " + device);

        ByteBuffer buf = ByteBuffer.allocate(LABEL_LENGTH);

        FileChannel channel =
            new RandomAccessFile(device, "r").getChannel();
        if (channel.read(buf, LABEL_OFFSET) != LABEL_LENGTH) {
            channel.close();
            throw new IOException("Can't read disk label from " + device);
        }
        channel.close();

        buf.flip();
        return buf;
    }

    /** Construct a ByteBuffer from attributes */
    private ByteBuffer toByteBuffer() {
        ByteBuffer buf = ByteBuffer.allocate(LABEL_LENGTH);
        buf.clear();
        buf.putLong(LABEL_MAGIC);
        buf.putInt(LABEL_VERSION);
        buf.putInt(cellId());
        buf.putShort(siloId());
        buf.putShort(nodeId());
        buf.putShort(diskIndex());
        buf.putShort(isDisabled()? (short)1 : (short)0);
        buf.putLong(incarnation());

        // padding
        while(buf.remaining() > 0) {
            buf.put((byte)0);
        }
        buf.flip();

        // put checksum
        buf.putLong(LABEL_LENGTH-8, checksum(buf.duplicate()));
        return buf;
    }

    /** Write a disklabel */
    private static void writeLabel(String device, ByteBuffer data)
        throws IOException {
        FileChannel channel =
            new RandomAccessFile(device, "rw").getChannel();
        if (channel.write(data, LABEL_OFFSET) != LABEL_LENGTH) {
            throw new IOException("Can't write disk label to " + device);
        }
        channel.close();
    }

    // The 'disklabel' command is the only consumer.
    public static DiskLabel getDiskLabel(DiskLabel diskLabel) {
        if(diskLabel == null) {
	    diskLabel = new DiskLabel();
	    logger.warning("DiskLabel does not exist, creating a new default label");
	}
        return diskLabel;
    } 

    // Serialization
    
    public static void serialize(DiskLabel diskLabel, DataOutput output) 
            throws IOException {

	if(diskLabel == null) {
	    diskLabel = new DiskLabel();
	    logger.warning("DiskLabel reference null - using default");
	}

        if (diskLabel.nodeId <= 0 && logger.isLoggable(Level.FINE))
            logger.fine("DiskLabel bogus: " + diskLabel);

        output.writeInt(diskLabel.cellId());
        output.writeShort(diskLabel.siloId());
        output.writeShort(diskLabel.nodeId());
        output.writeShort(diskLabel.diskIndex());
        output.writeLong(diskLabel.incarnation());
        output.writeBoolean(diskLabel.isDisabled());
        output.writeUTF(diskLabel.getDevice());
    }

    public static DiskLabel deserialize(DataInput input) throws IOException {
        int cellId = input.readInt();
        short siloId = input.readShort();
        short nodeId = input.readShort();
        short diskIndex = input.readShort();
        long incarnation = input.readLong();
        boolean disabled = input.readBoolean();
        String device = input.readUTF();

        if (nodeId <= 0 && logger.isLoggable(Level.FINE))
            logger.fine("DiskLabel for \"" + device + "\": nodeId=" + nodeId);

        return new DiskLabel(cellId, siloId, nodeId, diskIndex, device,
            disabled, incarnation, false /* isDirty */);
    }

    private static long checksum(ByteBuffer buf) {
        byte[] data = new byte[LABEL_LENGTH - 8];
        buf.get(data, 0, LABEL_LENGTH - 8);
        Adler32 sum = new Adler32();
        sum.update(data);
        return sum.getValue();
    }

    private static boolean isZero(ByteBuffer buf) {
        for (int i = 0; i < LABEL_LENGTH; i++) {
            if (buf.get(i) != 0) {
                return false;
            }
        }
        return true;
    }

    ////////////////////////////////////////////////////////////////////////
    // For unit-tests and node-verification service only: all the
    // simulator start and support stuff is here

    // Modes of operation for main()
    private static final int UNKNOWN = 0;
    private static final int SIMULATOR = 1;
    private static final int PRINTLABELS = 2;

    private static Date now = null;
    private static PrintStream simLog = System.out;
    private static int diskType = HardwareProfile.DISKS_SATA;

    private static String inputScriptDir = ".";
    private static int verbosity = 0;
    private static String outFileName = null;
    private static String[] diskPaths = null;

    public static void main(String[] args) {
        int rc = 0;
        try {
            int mode = parseArgs(args);
            switch (mode) {
            case SIMULATOR:
                if (setupSimulator())
                    runSimulator();
                break;

            case PRINTLABELS:
                printLabels(localCellId, localSiloId, localNodeId, diskPaths);
                break;

            default:
                System.err.println("Unknown mode " + mode);
                System.exit(1);
            }
        }
        catch (Exception e) {
            e.fillInStackTrace();
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace(System.err);
            rc = 2;
        }
        finally {
            if (simLog != System.out)
                simLog.close();
        }

        System.exit(rc);
    }

    private static void usage() {
        System.out.println("Usage: DiskLabel.main { -S | -L } " +
                           "[-t <disk-type>] [-o outfile] [-d script-dir]");
        System.out.println("    -c cellID -n nodeId -s siloID [-v] disks ...");
    }

    private static int parseArgs(String[] args) {
        int rc = 0;
        int mode = UNKNOWN;

        now = new Date(System.currentTimeMillis());

        Getopt opts = new Getopt(args, "SLo:d:t:vc:s:n:");
        while (opts.hasMore()) {
            Getopt.Option option = opts.next();

            // Non-switch options must come last
            if (option.noSwitch() == true) {
                if (diskPaths == null) {
                    opts.back();
                    diskPaths = opts.remaining();
                }
                break;
            }

            switch (option.name()) {

            case 'L':
                if (mode != UNKNOWN && mode != PRINTLABELS) {
                    System.err.println("Can't use both -L and " +
                                       modeName(mode));
                    System.exit(1);
                }
                mode = PRINTLABELS;
                break;

            case 'S':
                if (mode != UNKNOWN && mode != SIMULATOR) {
                    System.err.println("Can't use both -S and " +
                                       modeName(mode));
                    System.exit(1);
                }
                mode = SIMULATOR;
                break;

            case 't':
                try {
                    diskType = Integer.parseInt(option.value());
                }
                catch (NumberFormatException e) {
                    System.err.println("Not numeric: \"" + option.value() +
                                       "\"");
                }
                break;

            case 'o':
                outFileName = option.value();
                break;

            case 'd':
                inputScriptDir = option.value();
                break;

            case 'c':
                try {
                    localCellId = Short.parseShort(option.value());
                }
                catch (NumberFormatException e) {
                    System.err.println("Not numeric: \"" + option.value() +
                                       "\"");
                }
            case 's':
                try {
                    localSiloId = Short.parseShort(option.value());
                }
                catch (NumberFormatException e) {
                    System.err.println("Not numeric: \"" + option.value() +
                                       "\"");
                }
            case 'n':
                try {
                    localNodeId = Short.parseShort(option.value());
                }
                catch (NumberFormatException e) {
                    System.err.println("Not numeric: \"" + option.value() +
                                       "\"");
                }

            case 'v':
                verbosity++;
                break;

            default:
                System.err.println("Ignoring unrecognized option: \"" +
                    option.value() + "\"");
                break;
            }
        }

        if (mode == UNKNOWN) {
            System.err.println("What do you want me to do?");
            usage();
            System.exit(1);
        }

        if (localNodeId < 0)
            System.err.println("Error: node ID is unspecified");
        if (localCellId < 0)
            System.err.println("Error: cell ID is unspecified");
        if (localSiloId < 0)
            System.err.println("Error: silo ID is unspecified");
        if (localCellId < 0 || localSiloId < 0 || localNodeId < 0) {
            usage();
            System.exit(1);
        }

        return mode;
    }

    private static void printLabels(short cellId, short siloId, short nodeId,
                                    String[] diskPaths) {
        if (diskPaths == null)
            return;

        for (int i = 0; i < diskPaths.length; i++) {
            try {
                DiskLabel label = getLabel(diskPaths[i], nodeId, false);
                if (label == null)
                    System.out.println("- " + diskPaths[i]);
                else
                    System.out.println(label.toString());
            }
            catch (IOException e) {
                System.err.println(e.toString());
            }
            catch (Exception e) {
                e.fillInStackTrace();
                e.printStackTrace(System.err);
            }
        }
    }

    private static boolean setupSimulator() {
        if (outFileName != null && !outFileName.equals("-"))
            try {
                simLog = new PrintStream(new FileOutputStream(outFileName));
                outFileName = "\"" + outFileName + "\"";
            }
            catch (FileNotFoundException e) {
                System.err.println("Couldn't find \"" + outFileName + "\"!");
                return false;
            }
        else
            outFileName = "<stdout>";

        // We're ready!

        System.out.println("Started at " + now.toString() +
                           ", trace is in " + outFileName);
        simLog.println(now.toString());

        Exec.simulatorMode(simLog, inputScriptDir);

        return true;
    }

    private static void runSimulator() throws IOException {

        int i;
        Iterator l;
        List paths = DiskOps.getDiskOps().getDiskPaths(diskType);
        Map disks = DiskLabel.probeDisks(0, (short)0, (short)101, paths,
                                         false);

        for (i = 0, l = disks.keySet().iterator(); l.hasNext(); i++) {
            String path = (String) l.next();
            DiskLabel label = (DiskLabel) disks.get(path);
            System.out.println(label);
        }

        // Adding a disk: create a temp file to use as a disk
        File f = File.createTempFile("HC-new-disk-test", ".raw");
        f.deleteOnExit();
        OutputStream fd = new BufferedOutputStream(new FileOutputStream(f));
        Random gen = new Random(System.currentTimeMillis());
        byte[] garbage = new byte[1024*8192]; // 8MB
        gen.nextBytes(garbage);
        fd.write(garbage);
        fd.close();

        DiskLabel.newDisk(0, (short)0, (short)101, f.getAbsolutePath());

        for (i = 0, l = disks.keySet().iterator(); l.hasNext(); i++) {
            String path = (String) l.next();
            DiskLabel label = (DiskLabel) disks.get(path);
            System.out.println(label);
        }
    }

    private static String modeName(int mode) {
        switch (mode) {
        case SIMULATOR:
            return "-S";
        case PRINTLABELS:
            return "-L";
        case UNKNOWN:
            return "";
        default:
            return "?";
        }
    }
}
