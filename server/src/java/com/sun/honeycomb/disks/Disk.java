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

import java.io.Serializable;
import java.io.IOException;
import java.io.DataOutput;
import java.io.DataInput;

import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertException;

import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.StringUtil;

public final class Disk implements AlertComponent,
                                   Serializable, Comparable, Codable {
    // These are used in the status field
    public static final int DISABLED    = 0; // Unmounted, but my disk
    public static final int ENABLED     = 1; // In use
    public static final int MOUNTED     = 2; // Mounted, but not used (yet)
    public static final int OFFLINE     = 3; // Present, but unknown
    public static final int FOREIGN     = 4; // HC disk but not mine
    public static final int ABSENT      = 5; // No disk in slot
    private static final int LASTSTATUS = 5; // 

    // These are modes
    public static final int ABNORMAL  = 0;
    public static final int NORMAL    = 1;
    public static final int EVACUATE  = 2;
    public static final int CHECKING  = 3;
    private static final int LASTMODE = 3;

    private DiskId id = null;
    private String device = null;   // data partition device
    private String altdev = null;
    private String path = null;     // mount point
    private String nodeIpAddr = null;

    private int status = -1;
    private int mode = -1;

    private long diskSize = 0;
    private long availableSize = 0;
    private int badSectors = 0;
    private int pendingBadSectors = 0;
    private int temperature = 0;
    private boolean smartError = false;
    private String serialNo = "UNKNOWN";

    // derived values
    private long usedSize = 0;
    private double percentFree = 0.0;
    private double percentUsed = 0.0;

    // set by DiskMonitor
    private boolean usageCapReached = false;

    private long lastSnapshot = 0L;
    private long totalBytesRead = 0L;
    private long totalBytesWritten = 0L;
    private double readRate = 0.0;
    private double writeRate = 0.0;
    private double maxReadRate = 0.0;
    private double maxWriteRate = 0.0;

    /**
     * Public constructor taking a path for the disk. This is used
     * only for testing.
     */
    public Disk(String path) {this.path = path;}

    Disk() {}

    public Disk(int cellId, int siloId, int nodeId, int diskId,
                long incarnation,
                String device, String path,
                String nodeIpAddr,
                int status, int mode,
                long diskSize, long availableSize,
                int badSectors, int pendingBadSectors,
                int temperature, boolean smartError) {

        this(new DiskId(cellId, siloId, nodeId, diskId, incarnation),
             device, path, nodeIpAddr, status, mode,
             diskSize, availableSize, badSectors, pendingBadSectors,
             temperature, smartError);
    }

    public Disk(DiskId id,
         String device, String path,
         String nodeIpAddr,
         int status, int mode,
         long diskSize, long availableSize,
         int badSectors, int pendingBadSectors,
         int temperature, boolean smartError) {

        if (device == null)
            throw new InternalException("Null device");
        if (path == null)
            throw new InternalException("Null path");
        if (nodeIpAddr == null)
            throw new InternalException("Null node IP addr");

        this.id = id;
        this.device = device;
        this.path = path;
        this.nodeIpAddr = nodeIpAddr;
        this.status = status;
        this.mode = mode;

        this.diskSize = diskSize;
        this.availableSize = availableSize;
        this.usedSize = this.diskSize - this.availableSize;

        percentUsed = percentFree = 0.0;
        if (this.diskSize > 0) {
            percentUsed = this.usedSize / (double)this.diskSize;
            percentFree = this.availableSize / (double)this.diskSize;
        }
        this.badSectors = badSectors;
        this.pendingBadSectors = pendingBadSectors;
        this.temperature = temperature;
        this.smartError = smartError;
    }

    public static Disk getNullDisk(int cellId, int siloId, int nodeId,
                                   int diskIndex, long incarnation) {
        DiskId id = new DiskId(cellId, siloId, nodeId, -1, incarnation);
        return getNullDisk(id);
    }

    public static Disk getNullDisk(DiskId id) {
        return new Disk(id, "", "", "", 0, 0, 0, 0, 0, 0, 0, false);
    }

    public static Disk getNullDisk() {
        return getNullDisk(new DiskId(0,0));
    }

    public boolean isNullDisk() {
    return(device.equals("") ||
           path.equals("") ||
           nodeIpAddr.equals(""));
    }

    public static String getStatusString(int s) {
        switch (s) {
        case -1: return "?";
        case DISABLED: return "disabled";
        case ENABLED: return "enabled";
        case MOUNTED: return "mounted";
        case OFFLINE: return "offline";
        case FOREIGN: return "foreign";
        case ABSENT: return "absent";
        default: return "status" + s;
        }
    }

    public static String getModeString(int m) {
        switch (m) {
        case -1: return "?";
        case NORMAL: return "normal";
        case ABNORMAL: return "abnormal";
        case EVACUATE: return "evacuate";
        case CHECKING: return "checking";
        default: return "mode" + m;
        }
    }

    public DiskId getId() { return id; }

    // Convenience methods for components of DiskId
    public int cellId() { return getId().cellId(); }
    public int siloId() { return getId().siloId(); }
    public int nodeId() { return getId().nodeId(); }
    public int diskIndex() { return getId().diskIndex(); }
    public long incarnation() { return getId().incarnation(); }

    public String getDevice() { return device; } // data partition device
    public String getAltDevice() { return altdev; }
    public String getPath() { return path; }
    public String getSerialNo() { return serialNo; }
    public String getNodeIpAddr() { return nodeIpAddr; }

    public int getStatus() { return status; }
    public boolean isEnabled() { return status == ENABLED; }
    public boolean isForeign() { return status == FOREIGN; }

    /**
     * Check if disk is mounted and data directories created. 'true'
     * indicates that hadb and OA upgrader can start.
     */
    public boolean isMounted() {
        return (status == MOUNTED) || (status == ENABLED);
    }

    public int getMode() { return mode; }

    public long getDiskSize() { return diskSize; }
    public long getAvailableSize() { return availableSize; }
    public int getBadSectors() { return badSectors; }
    public int getPendingBadSectors() { return pendingBadSectors; }
    public int getTemperature() { return temperature; }
    public boolean error() { return smartError; }
    public long getUsedSize() { return usedSize; }
    public double getPercentUsed() { return percentUsed; }
    public double getPercentFree() { return percentFree; }
    public boolean getUsageCapReached() { return usageCapReached; }
    public double readRate() { return readRate; }
    public double writeRate() { return writeRate; }
    public double maxReadRate() { return maxReadRate; }
    public double maxWriteRate() { return maxWriteRate; }
    public long bytesRead() { return totalBytesRead; }
    public long bytesWritten() { return totalBytesWritten; }

    public void setAltDevice(String altdev) {
        this.altdev = altdev;
    }

    public void setAvailableSize(long size) {
        availableSize = size;
        usedSize = diskSize - availableSize;

        if (diskSize > 0) {
            percentUsed = usedSize / (double)diskSize;
            percentFree = availableSize / (double)diskSize;
        }
    }

    public void setUsageCapReached(boolean b) { usageCapReached = b; }

    public String getStatusString() {
        return getStatusString(getStatus());
    }

    public String getModeString() {
        return getModeString(getMode());
    }

    public void setStatus(int s)  {
        if (s < 0 || s > LASTSTATUS)
            throw new InternalException("Invalid status value " + s);
        status = s;
    }

    public void setMode(int m) {
        if (m < 0 || m > LASTMODE)
            throw new InternalException("Invalid mode value " + m);
        mode = m;
    }

    public void setStatus(String s) {
        if (s.equals("disabled"))
            status = DISABLED;
        else if (s.equals("enabled"))
            status = ENABLED;
        else if (s.equals("mounted"))
            status = MOUNTED;
        else if (s.equals("offline"))
            status = OFFLINE;
        else if (s.equals("foreign"))
            status = FOREIGN;
        else if (s.equals("absent"))
            status = ABSENT;
        else
            throw new InternalException("Invalid status \"" + s + "\"");
    }

    public void setMode(String m) {
        if (m.equals("normal"))
            mode = NORMAL;
        else if (m.equals("abnormal"))
            mode = ABNORMAL;
        else if (m.equals("evacuate"))
            mode = EVACUATE;
        else if (m.equals("checking"))
            mode = CHECKING;
        else
            throw new InternalException("Invalid mode \"" + m + "\"");
    }

    public void setSerialNo(String serial) {
        serialNo = serial;
    }

    public void updateIOStats(long snapshotTime,
                              long bytesRead, long bytesWritten) {
        long duration = snapshotTime - lastSnapshot;
        if (duration == 0)
            return;

        // The unit for snapshotTime is ns
        double interval = ((double)duration)/1000000000;

        readRate = bytesRead / interval;
        writeRate = bytesWritten / interval;

        if (readRate > maxReadRate)
            maxReadRate = readRate;
        if (writeRate > maxWriteRate)
            maxWriteRate = writeRate;

        lastSnapshot = snapshotTime;
        totalBytesRead += bytesRead;
        totalBytesWritten += bytesWritten;
    }

    public double meanReadRate() {
        return totalBytesRead / (double)lastSnapshot;
    }

    public double meanWriteRate() {
        return totalBytesWritten / (double)lastSnapshot;
    }

    public String toString() {
        // This is the output:
        // [0,0,101,0;1]{10.123.45.101:/dev/hde2 -> /devices/pci@0,0/pci108e,5348@8/disk@1,0:e "Disk serial Num." (/data/0) 238282/238310 MB; R:W 61436.5B/s:12341.3B/s, 14.8GB:3GB (max 66.554MB/s:41.291MB/s); enabled,normal,no-errors(0,0) 42C}

        StringBuffer buf = new StringBuffer();

        if (getId() != null)
            buf.append(getId().toString());

        buf.append("{");
        buf.append(getNodeIpAddr()).append(":").append(device);
        if (altdev != null && !altdev.equals(device))
            buf.append(" -> ").append(altdev);
        buf.append(' ').append(StringUtil.image(getSerialNo()));
        buf.append(" (").append(getPath()).append(") ");
        buf.append(getAvailableSize()).append("MB/").append(getDiskSize());
        buf.append("MB; R:W ");

        buf.append(bytesToString(meanReadRate())).append("/s").append(":");
        buf.append(bytesToString(meanWriteRate())).append("/s, ");
        buf.append(bytesToString(bytesRead())).append(":");
        buf.append(bytesToString(bytesWritten())).append(" (max ");
        buf.append(bytesToString(maxReadRate())).append("/s").append(":");
        buf.append(bytesToString(maxWriteRate())).append("/s); ");

        buf.append(getStatusString()).append(",").append(getModeString());
        buf.append(",");
        if (!smartError) buf.append("no-");
        buf.append("errors(").append(getBadSectors()).append(",");
        buf.append(getPendingBadSectors()).append(") ");
        buf.append(getTemperature()).append("C}");
        return buf.toString();

    }

    /* Implementation of Comparable interface */

    public boolean equals(final Object obj) {
        if (!(obj instanceof Disk))
            return false;

        Disk other = (Disk) obj;
        return getId().equals(other.getId());
    }

    public int compareTo(final Object obj) {
        if (!(obj instanceof Disk)) {
            throw new ClassCastException("cannot compare");
        }
        Disk other = (Disk) obj;
        return getId().compareTo(other.getId());
    }

    public int hashCode() {
        return getId().hashCode() ^
            getDevice().hashCode() ^
            getPath().hashCode();
    }

    /* Implementation of Codable inteface */

    public void encode(Encoder encoder) {

        getId().encode(encoder);

        encoder.encodeString(device);
        encoder.encodeString(path);
        encoder.encodeString(nodeIpAddr);

        encoder.encodeString(getStatusString());
        encoder.encodeString(getModeString());
        encoder.encodeString(getSerialNo());

        encoder.encodeLong(diskSize);
        encoder.encodeLong(availableSize);
        encoder.encodeInt(badSectors);
        encoder.encodeInt(pendingBadSectors);
        encoder.encodeInt(temperature);
        encoder.encodeBoolean(smartError);
    }

    public void decode(Decoder decoder) {

        getId().decode(decoder);

        device = decoder.decodeString();
        path = decoder.decodeString();
        nodeIpAddr = decoder.decodeString();

        setStatus(decoder.decodeString());
        setMode(decoder.decodeString());
        setSerialNo(decoder.decodeString());

        diskSize = decoder.decodeLong();
        availableSize = decoder.decodeLong();
        badSectors = decoder.decodeInt();
        pendingBadSectors = decoder.decodeInt();
        temperature = decoder.decodeInt();
        smartError = decoder.decodeBoolean();
    }

    /* Implementation of Serializable inteface */

    public static void serialize(Disk disk, DataOutput output)
                    throws IOException {

        DiskId.serialize(disk.getId(), output);

        output.writeUTF(disk.getDevice());
        output.writeUTF(disk.getPath());
        output.writeUTF(disk.getNodeIpAddr());

        output.writeInt(disk.getStatus());
        output.writeInt(disk.getMode());

        output.writeLong(disk.getDiskSize());
        output.writeLong(disk.getAvailableSize());
        output.writeInt(disk.getBadSectors());
        output.writeInt(disk.getPendingBadSectors());
        output.writeInt(disk.getTemperature());
        output.writeBoolean(disk.error());

        output.writeUTF(disk.getSerialNo());
    }

    public static Disk deserialize(DataInput input) throws IOException {
        Disk d = new Disk(DiskId.deserialize(input),
                          input.readUTF(), input.readUTF(), input.readUTF(),
                          input.readInt(), input.readInt(),
                          input.readLong(), input.readLong(),
                          input.readInt(), input.readInt(),
                          input.readInt(), input.readBoolean());
        d.setSerialNo(input.readUTF());
        return d;
    }

    /**
     * Alert API.
     * At this point we limit to export the following:
     * - device
     * - diskSize
     * - usedSize
     * - percentUsed
     * - status
     * - mode
     * - diskid
     * - path
     * - FRU id (serial no.)
     */
    public int getNbChildren() {
        return 9;
    }

    public AlertProperty getPropertyChild(int index)
            throws AlertException {
        AlertProperty prop = null;

        switch(index) {
        case 0:
            prop = new AlertProperty("device", AlertType.STRING);
            break;

        case 1:
            prop = new AlertProperty("diskSize", AlertType.LONG);
            break;

        case 2:
            prop = new AlertProperty("diskUsed", AlertType.LONG);
            break;

        case 3:
            prop = new AlertProperty("percentUsed", AlertType.DOUBLE);
            break;

        case 4:
            prop = new AlertProperty("status", AlertType.INT);
            break;

        case 5:
            prop = new AlertProperty("mode", AlertType.INT);
            break;

        case 6:
            prop = new AlertProperty("diskId", AlertType.STRING);
            break;

        case 7:
            prop = new AlertProperty("path", AlertType.STRING);
            break;

        case 8:
            prop = new AlertProperty("serialNo", AlertType.STRING);
            break;

        default:
            throw new AlertException("index " + index + "does not exist");
        }
        return prop;
    }

    public double getPropertyValueDouble(String property)
            throws AlertException {
        if (property.equals("percentUsed")) {
            return percentUsed;
        } else {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
    }
    public String getPropertyValueString(String property)
            throws AlertException {
        if (property.equals("device")) {
            return device;
        } else if (property.equals("diskId")) {
            return id.toStringShort();
        } else if (property.equals("path")) {
            return path;
        } else if (property.equals("serialNo")) {
            return getSerialNo();
        } else {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
    }
    public long getPropertyValueLong(String property)
            throws AlertException {
        if (property.equals("diskSize")) {
            return diskSize;
        } else if  (property.equals("diskUsed")) {
            return usedSize;
        } else {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
    }
    public int getPropertyValueInt(String property) throws AlertException {
        if (property.equals("status")) {
            return status;
        } else if  (property.equals("mode")) {
            return mode;
        } else {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
    }

    /** default implementation. */
    public float getPropertyValueFloat(String property)
        throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public boolean getPropertyValueBoolean(String property)
        throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }

    public AlertComponent getPropertyValueComponent(String property)
        throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }

    private static String bytesToString(int bytes) {
        return bytesToString((double)bytes);
    }
    private static String bytesToString(long bytes) {
        return bytesToString((double)bytes);
    }
    private static String bytesToString(double bytes) {

        if (bytes < 1024)
            return (int)(0.5 + bytes) + "B";

        bytes = bytes/1024.0;
        if (bytes < 1024)
            return (int)(0.5 + bytes) + "KB";

        bytes = bytes/1024.0;
        if (bytes < 1024)
            return (int)(0.5 + bytes) + "MB";

        bytes = bytes/1024.0;
        if (bytes < 1024)
            return (int)(0.5 + bytes) + "GB";

        bytes = bytes/1024.0;
        if (bytes < 1024)
            return (int)(0.5 + bytes) + "TB";

        bytes = bytes/1024.0;
        return (int)(0.5 + bytes) + "PB";
    }
}
