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


package com.sun.honeycomb.platform;

import com.sun.honeycomb.util.SysStat;
import com.sun.honeycomb.alert.*;
import com.sun.honeycomb.util.Exec;

import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.RuntimeException;

/**
 * Retrieves the BIOS information from the DMI table and inserts it
 * into the alert framework.
 */
public class BIOSInfo implements AlertComponent, java.io.Serializable {

    private static final Logger logger =
        Logger.getLogger (BIOSInfo.class.getName());

    protected String biosVersion;
    protected String releaseDate;
    protected String vendor;
    protected String uuid;
    
    public static final String PROPERTY_BIOS_VERSION = "BIOSVersion";
    public static final String PROPERTY_RELEASE_DATE = "ReleaseDate";
    public static final String PROPERTY_VENDOR = "Vendor";
    public static final String PROPERTY_UUID = "UUID";

    public BIOSInfo() {
        BufferedReader output = null;
        String cmd = "/opt/honeycomb/bin/dmidecode.pl -bC" +
            " /opt/honeycomb/bin/bios";
        try {
            output = Exec.execRead(cmd);
            biosVersion = output.readLine();
            releaseDate = output.readLine();
            vendor = output.readLine();
            uuid = output.readLine();
            logger.info("Adding node uuid " + uuid);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                output.close();
            } catch (IOException e) {}
        }

    }

    private static BIOSInfo theInfo = null;
    public static synchronized BIOSInfo getInfo() {

        if (theInfo == null) {
                theInfo = new com.sun.honeycomb.platform.BIOSInfo();
        }

        return theInfo;
    }

    /**
     * Alert API
     * We export:
     * - BIOSVersion
     * - ReleaseDate
     * - Vendor
     * - UUID
     */
    private static final AlertProperty[] alertProps = {
        new AlertProperty(PROPERTY_BIOS_VERSION, AlertType.STRING),
        new AlertProperty(PROPERTY_RELEASE_DATE, AlertType.STRING),
        new AlertProperty(PROPERTY_VENDOR, AlertType.STRING),
        new AlertProperty(PROPERTY_UUID, AlertType.STRING)
    };

    public int getNbChildren() {
        return alertProps.length;
    }

    public AlertProperty getPropertyChild(int index) throws AlertException {
        AlertProperty prop = null;
        try {
            return alertProps[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new AlertException("index " + index + " does not exist");
        }

    }

    public String getPropertyValueString(String prop) throws AlertException {
        if (prop.equals(PROPERTY_BIOS_VERSION)) {
            return biosVersion;
        } else if (prop.equals(PROPERTY_RELEASE_DATE)) {
            return releaseDate;
        } else if (prop.equals(PROPERTY_VENDOR)) {
            return vendor;
        } else if (prop.equals(PROPERTY_UUID)) {
            return uuid;
        } else {
            throw new AlertException("property " + prop + " does not exist");
        }
    }

    /* Default implementation. */
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

}
