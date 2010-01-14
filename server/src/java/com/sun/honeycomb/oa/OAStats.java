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



package com.sun.honeycomb.oa;

import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertComponent.AlertProperty;
import com.sun.honeycomb.alert.AlertException;

import java.util.LinkedList;
import java.util.Iterator;
import java.util.logging.Logger;

/**
    Get stats on basic OA ops for alerts. Can be added to alert tree
    of any OA client that is a ManagedService and thus has alerts in
    its proxy. 
*/

public class OAStats implements AlertComponent {

    private transient static final Logger logger =
        Logger.getLogger(OAStats.class.getName());

    static private final String CREATE = "create";
    static private final String CLOSE  = "close";
    static private final String RENAME = "rename";

    private int creates = 0;
    private long create_time = 0;
    private int closes = 0;
    private long close_time = 0;
    private int renames = 0;
    long rename_time = 0;

    /**
     *  scan OA RunnableCode objects for latest stats
     */
    public OAStats() {

        //
        //  creates
        //
        LinkedList ll = CreatorThreads.getAllCode();
        Iterator it = ll.iterator();
        while (it.hasNext()) {
            FragmentFileSet.FragmentCreator fc = 
                                   (FragmentFileSet.FragmentCreator) it.next();
            creates += fc.getCreates();
            create_time += fc.getCreateTime();
        }

        //
        //  closes
        //
        ll = WriteAndCloseThreads.getAllCode();
        it = ll.iterator();
        while (it.hasNext()) {
            FragmentFileSet.FragmentFooterWriterAndCloser ffwac =
                      (FragmentFileSet.FragmentFooterWriterAndCloser) it.next();
            closes += ffwac.getCloses();
            close_time += ffwac.getCloseTime();
        }

        //
        // renames
        //
        ll = RenamerThreads.getAllCode();
        it = ll.iterator();
        while (it.hasNext()) {
            FragmentFileSet.FragmentRenamer fr = 
                       (FragmentFileSet.FragmentRenamer) it.next();
            renames += fr.getRenames();
            rename_time += fr.getRenameTime();
        }
    }

    public int getNbChildren() {
        return 3;
    }

    public AlertProperty getPropertyChild(int index)
        throws AlertException {

        AlertProperty prop;

        switch(index) {
        case 0:
            prop = new AlertProperty(CREATE, AlertType.STRING);
            break;
        case 1:
            prop = new AlertProperty(CLOSE, AlertType.STRING);
            break;
        case 2:
            prop = new AlertProperty(RENAME, AlertType.STRING);
            break;
        default:
            throw new AlertException("index " + index + " out of bound");
        }
        return prop;
    }

    public String getPropertyValueString(String property)
        throws AlertException {

        if (property.equals(CREATE)) {
            return "" + creates + " " + create_time;
        } else if (property.equals(CLOSE)) {
            return "" + closes + " " + close_time;
        } else if (property.equals(RENAME)) {
            return "" + renames + " " + rename_time;
        } else {
            throw new AlertException("property " + property +
                                         " does not exist");
        }
    }

    public AlertComponent getPropertyValueComponent(String property)
        throws AlertException {

        throw new AlertException("getPropertyValueComponent not implemented");
    }
    public boolean getPropertyValueBoolean(String property)
       throws AlertException {
        throw new AlertException("Not implemented");
    }

    public int getPropertyValueInt(String property) 
        throws AlertException {
        throw new AlertException("Not implemented");
    }

    public long getPropertyValueLong(String property)
        throws AlertException {
        throw new AlertException("Not implemented");
    }

    public float getPropertyValueFloat(String property)
        throws AlertException {
        throw new AlertException("Not implemented");
    }

    public double getPropertyValueDouble(String property)
        throws AlertException {
        throw new AlertException("Not implemented");
    }
}
