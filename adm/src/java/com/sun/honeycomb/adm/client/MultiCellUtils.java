/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.honeycomb.adm.client;

import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.mgmt.client.ClientData;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.lang.reflect.Constructor;

/**
 *
 */
public class MultiCellUtils {

    /**
     * This class creates an instance of <code>clazz</code>
     * for each cell in the hive passed in via <code>cells</code>
     * object
     * @param clazz The class to instantiate.  Must extends MutliCellOp
     * and have a contructor with a single argument of same type passed in
     * as <code>parameter</code> such that
     * <code>new Class(ClientData parameter[i])</code> will instantiate properly
     * @params parameter the array of arguments to pass to the constructor of
     * <code>clazz</code>.  One argument is passed to each instance of the 
     * constructor.
     * <P>
     * The arguments are assumed to have been fetch by 
     * <code>Fetcher.fetch${METHOD}</code>.
     * @return MultiCellOp[] array of instantiated MultiCellOp classes
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     */
    public static MultiCellOp [] allocateMultiCellOp(
            Class clazz, ClientData[] parameter) 
    throws MgmtException, ConnectException {

        if (parameter == null || parameter.length == 0)
            throw new IllegalArgumentException(
                    "parameter argument can not be empty or null.");
        
        MultiCellOp [] res = new MultiCellOp[parameter.length];
        Class [] signature = { parameter[0].getClass() };
        try {
            Constructor ctor = clazz.getConstructor(signature);
            for (int i = 0; i < parameter.length; i++) {
                Object [] params = { parameter[i] };
                MultiCellOp curOp = (MultiCellOp) ctor.newInstance(params);
                res[i] = curOp;
            }
        } catch (Exception ignore) {
            //
            // That should not happen but better to be cautious.
            // (this not a MgmtException but we hope this will never
            //  be returned)
            //
            throw new MgmtException("Failed to start the operation on "+
                "the multi-cell hive.");
        }
        return res;
    }

}
