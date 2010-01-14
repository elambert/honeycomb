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

/*
 * The various alert component in the system
 * are:
 * - root
 * - node
 * - <service> = layout, nodeMgr, metadata, objectArchive,
 *               diskMonitor, platform, switch
 * - <specific service instances>
 *
 */
public interface AlertComponent extends java.io.Serializable
{

    /*
     * Returns the number of direct children this
     * component has.
     * - input : none
     * - output : none
     */
    public int getNbChildren();


    /*
     * Returns the Property (its name and type)
     * - input : index of the property
     * - output : <name,type> of the property
     */
    public AlertProperty getPropertyChild(int index)
        throws AlertException;


    /*
     * Returns the value of the property
     * - input : name of the property (as knowm by this component)
     * - output : value of the property
     */
    public boolean getPropertyValueBoolean(String property)
        throws AlertException;
    public int getPropertyValueInt(String property)
        throws AlertException;
    public long getPropertyValueLong(String property)
        throws AlertException;
    public float getPropertyValueFloat(String property)
        throws AlertException;
    public double getPropertyValueDouble(String property)
        throws AlertException;
    public String getPropertyValueString(String property)
        throws AlertException;
    public AlertComponent getPropertyValueComponent(String property)
        throws AlertException;

    public class AlertProperty implements java.io.Serializable
    {
        protected String  name;
        protected int     type;
        
        
        public AlertProperty(AlertProperty p) {
            name = p.getName();
            type = p.getType();
        }
        
        public AlertProperty(String n, int t) {
            name = n;
            type = t;
        }
        public String getName() {
            return name;
        }
        
        public int getType() {
            return type;
        }
    }
}
