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
 * Retrieves the CPU information from the DMI table and inserts it
 * into the alert framework.
 */
public class CPUInfo implements AlertComponent, java.io.Serializable {

    private static final Logger logger = 
        Logger.getLogger (CPUInfo.class.getName());

	protected String version;
	protected String maxSpeed;
	protected String curSpeed;
	private int children = 3;

    public CPUInfo() {
		BufferedReader output = null;
		String cmd = "/opt/honeycomb/bin/dmidecode.pl -bC" +
			" /opt/honeycomb/bin/cpu";

		try {
			output = Exec.execRead(cmd);
			version = output.readLine();
			maxSpeed = output.readLine();
			curSpeed = output.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				output.close();
			} catch (IOException e) {}
		}

    }

    private static CPUInfo theInfo = null;
    public static synchronized CPUInfo getInfo() {

        if (theInfo == null) {
                theInfo = new com.sun.honeycomb.platform.CPUInfo();
        }

        return theInfo;
    }

	public int getNbChildren() {
		return children;
	}

	public AlertProperty getPropertyChild(int index) throws AlertException {
		AlertProperty prop = null;
		String name;
		
		if (index == 0) {
			prop = new AlertProperty("Version", AlertType.STRING);
		} else if (index == 1) {
			prop = new AlertProperty("MaxSpeed", AlertType.STRING);
		} else if (index == 2) {
			prop = new AlertProperty("CurSpeed", AlertType.STRING);
		} else {
			throw new AlertException("index " + index + " does not exist");
		}

		return prop;
	}

	public String getPropertyValueString(String prop) throws AlertException {
		if (prop.equals("Version")) {
			return version;
		} else if (prop.equals("MaxSpeed")) {
			return maxSpeed;
		} else if (prop.equals("CurSpeed")) {
			return curSpeed;
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
