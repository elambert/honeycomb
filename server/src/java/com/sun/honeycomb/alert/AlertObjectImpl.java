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



package com.sun.honeycomb.alert;

public class AlertObjectImpl implements AlertApi.AlertObject, java.io.Serializable {

    private String path;
    private Object value;
    private int type;

    public AlertObjectImpl(String p, Object v, int t) {
        path = p;
        value = v;
        type = t;
    }

    public String getPropertyName() {
        return path;
    }

    public int getPropertyType() {
        return type;
    }

    public boolean getPropertyValueBoolean()
        throws AlertException {

        if (type != AlertType.BOOLEAN) {
            throw (new AlertException("Property " + path + 
                                      "is not a boolean"));
        }
        return ((Boolean) value).booleanValue();
    }

    public int getPropertyValueInt()
        throws AlertException {

        if (type != AlertType.INT) {
            throw (new AlertException("Property " + path + 
                                      "is not an integer"));
        }
        return ((Integer) value).intValue();
    }

    public long getPropertyValueLong()
        throws AlertException {

        if (type != AlertType.LONG) {
            throw (new AlertException("Property " + path + 
                                      "is not a long"));
        }
        return ((Long) value).longValue();
    }

    public float getPropertyValueFloat()
        throws AlertException {

        if (type != AlertType.FLOAT) {
            throw (new AlertException("Property " + path + 
                                      "is not a float"));
        }
        return ((Float) value).floatValue();
    }

    public double getPropertyValueDouble()
        throws AlertException {

        if (type != AlertType.DOUBLE) {
            throw (new AlertException("Property " + path + 
                                      "is not a double"));
        }
        return ((Double) value).doubleValue();
    }

    public String getPropertyValueString()
        throws AlertException {

        if (type != AlertType.STRING) {
            throw (new AlertException("Property " + path + 
                                      "is not a string"));
        }
        return ((String) value);
    }

    // Undocumented
    public Object getPropertyValue() {
        return value;
    }
    
    private String getTypeStr() {
	switch (type) {
	    case AlertType.COMPOSITE:
		return "AlertType.COMPOSITE";
	    case AlertType.INT:
		return "AlertType.INT";
	    case AlertType.LONG:
		return "AlertType.LONG";
	    case AlertType.FLOAT:
		return "AlertType.FLOAT";
	    case AlertType.DOUBLE:
		return "AlertType.DOUBLE";
 	    case AlertType.STRING:
		return "AlertType.STRING";
 	    case AlertType.BOOLEAN:
		return "AlertType.BOOLEAN";
	    default:
		break;
	}
	return "AlertType.????";
    }
    
    public String toString() {
	StringBuffer buf = new StringBuffer();
	buf.append(path).append(",");
	buf.append(getTypeStr()).append(",");
	if (value != null)
	    buf.append(value.toString());
	else
	    buf.append("<null>");
	return buf.toString();
    }
}
