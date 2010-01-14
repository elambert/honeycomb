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

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.Serializable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Date;

/**
 * This code is mysterious and ugly, and will be ripped out and hurled
 * away with great force.
 */
public final class DiskHealth implements Serializable {

    private static final Logger logger = 
        Logger.getLogger(DiskHealth.class.getName());

    private static final int ATTR_COUNT = 30;

    private static final int TEMPERATURE_ID = 194;
    private static final int PENDING_REALLOCATED_ID = 197;
    private static final int OFFLINE_UNCORRECTABLE_ID = 198;

    private static final int MAX_UNSIGNED_BYTE = 256;
    private static final int MAX_UNSIGNED_SHORT = 65536;

    private DiskId diskId = null;

    private int badSectors = 0;
    private int pendingBadSectors = 0;
    private int temperature = 0;

    private Date createdAt = null;

    private boolean someThresholdExceeded = false;
    
    private static int unsignedByte(int signed) {
        if (signed < 0) {
            return MAX_UNSIGNED_BYTE + signed;
        }
        return signed;
    }

    private static int unsignedShort(int signed) {
        if (signed < 0) {
            return MAX_UNSIGNED_SHORT + signed;
        }
        return signed;
    }

    private static int readByte(DataInput di) throws IOException {
        return unsignedByte(di.readByte());
    }

    private static int readShort(DataInput di) throws IOException {
        return unsignedShort(di.readByte() | (di.readByte() << 8));
    }

    private final static class ValueEntry {
        int id = 0;
        int flags = 0;
        int value = 0;
        int max = 0;
        byte[] reserved = new byte[7];

        ValueEntry(DataInputStream dis) throws IOException {
            id = readByte(dis);
            flags = readShort(dis);
            value = readByte(dis);
            max = readByte(dis);
            for (int i=0; i < reserved.length; i++) {
                reserved[i] = dis.readByte();
            }
        }

        public String toString() {
            return 
                "value id:" + id + 
                " flags:" + flags +
                " value:" + value +
                " max:" + max;
        }
    }

    private final static class ThresholdEntry {
        int id = 0;
        int threshold = 0;
        byte[] reserved = new byte[10];

        ThresholdEntry(DataInputStream dis) throws IOException {
            id = readByte(dis);
            threshold = readByte(dis);
            for (int i=0; i < reserved.length; i++) {
                reserved[i] = dis.readByte();
            }
        }

        public String toString() {
            return 
                "threshold id:" + id + 
                " threshold:" + threshold;
        }
    }

    private DataInputStream readHexInfo(String path) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        BufferedReader reader = null;
        try {
            FileInputStream fis = new FileInputStream(path);
            reader = new BufferedReader(new InputStreamReader(fis));
            int hex1 = 0;
            int hex2 = 0;
            for (String line = reader.readLine();
                 line != null;
                 line = reader.readLine()) {
                String[] hexString = line.split("\\s");
                for (int i=0; i < hexString.length; i++) {
                    String shex1 = hexString[i].substring(0, 2);
                    String shex2 = hexString[i].substring(2, 4);
                    try {
                        hex1 = Integer.parseInt(shex1, 16);
                        hex2 = Integer.parseInt(shex2, 16);
                    } catch (NumberFormatException nex) {
                        throw new IOException("Failed to parse SMART info: " +
                                              nex);
                    }
                    dos.writeByte(hex2);
                    dos.writeByte(hex1);
                }
            }
            dos.close();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignore) {}
            }
        }
        byte[] data = baos.toByteArray();
        return new DataInputStream(new ByteArrayInputStream(data));
    }

    public DiskHealth(String device, DiskId diskId) throws IOException {
        this.diskId = diskId;

        ValueEntry[] values = new ValueEntry[ATTR_COUNT];
        ThresholdEntry[] thresholds = new ThresholdEntry[ATTR_COUNT];

        String dev = device.split("/")[2];
        String svals = "/proc/ide/" + dev + "/smart_values";
        DataInputStream dis = null;
        try {
            dis = readHexInfo(svals);
            int revision = readShort(dis);
            for (int i=0; i < ATTR_COUNT; i++) {
                values[i] = new ValueEntry(dis);
            }
            byte offlineStatus = dis.readByte();
            byte reserved1 = dis.readByte();
            int offlineDuration = readShort(dis);
            byte reserved2 = dis.readByte();
            byte offlineCapability = dis.readByte();
            int smartCapability = readShort(dis);
            byte[] reserved3 = new byte[141];
            for (int j=0; j < reserved3.length; j++) {
                reserved3[j] = dis.readByte();
            }
            byte checksum = dis.readByte();
        }
        finally {
            if (dis != null) {
                try { dis.close(); } catch (IOException ignore) {}
            }
        }

        String sthr = "/proc/ide/" + dev + "/smart_thresholds";
        try {
            dis = readHexInfo(sthr);
            int revision = readShort(dis);
            for (int i=0; i < ATTR_COUNT; i++) {
                thresholds[i] = new ThresholdEntry(dis);
            }
            byte[] reserved  = new byte[149];
            for (int j=0; j < reserved.length; j++) {
                reserved[j] = dis.readByte();
            }
            byte checksum = dis.readByte();
        } finally {
            if (dis != null) {
                try { dis.close(); } catch (IOException ignore) {}
            }
        }

        for (int i=0; i < ATTR_COUNT; i++) {
            if (values[i].id == 0 || thresholds[i].id == 0 ||
                values[i].id != thresholds[i].id) {
                continue;
            }

            switch (values[i].id) {
            case TEMPERATURE_ID:
                temperature = unsignedByte(values[i].reserved[0]);
                break;

            case PENDING_REALLOCATED_ID:
                pendingBadSectors = unsignedByte(values[i].reserved[0]);
                break;
                
            case OFFLINE_UNCORRECTABLE_ID:
                badSectors = unsignedByte(values[i].reserved[0]);
                break;
            }

            /*
             * Prefailure condition -
             * If the value of the attribute flag is 1 and 
             * the attribute value is less than or equal to
             * its corresponding threshold, an imminent failure
             * is predicted with loss of data.
             */
            if ((values[i].flags & 1) != 0 &&
                values[i].value <= thresholds[i].threshold &&
                thresholds[i].threshold != 0xfe) {
                logger.warning("Imminent failure predicted for attribute " +
                            (int) values[i].id + " of disk " + device);
                someThresholdExceeded = true;
            }
        }
        
        createdAt = new Date();
    }

    public DiskId getId() { return diskId; }
    public int getBadSectors() { return badSectors; }
    public int getPendingBadSectors() { return pendingBadSectors; }
    public int getTemperature() { return temperature; }
    public Date getCreationTime() { return createdAt; }

    public String toString() {
        return "{ " + temperature + " " + badSectors + " " +
            pendingBadSectors + " " + createdAt + " }";
    }

    public boolean someThresholdExceeded() {
        return someThresholdExceeded;
    }
}
