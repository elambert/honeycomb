
package com.sun.honeycomb.admin.mgmt.server;

import com.sun.honeycomb.datadoctor.TaskList;
import org.w3c.dom.Document;
import java.math.BigInteger;
import java.util.List;

import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCDDTasksAdapter 
    implements HCDDTasksAdapterInterface {

    private MgmtServer           mgmtServer;
    public void loadHCDDTasks()         throws InstantiationException {
        mgmtServer = MgmtServer.getInstance();
    }





    /*
    * This is the list of accessors to the object
    */
    public BigInteger getNumTasks() throws MgmtException {

        return BigInteger.valueOf(TaskList.numTasks());
    }


    /*
     * This is the list of custom actions
     */
    public BigInteger getTaskCompletionTime(BigInteger task) throws MgmtException {
        return BigInteger.valueOf (System.currentTimeMillis()-
                                   ValuesRepository.getInstance().getTaskCompletionTimeDelta());
        
    }
    public BigInteger getTaskSlowestDisk(BigInteger task) throws MgmtException {
        return BigInteger.valueOf (System.currentTimeMillis());
    }
    public BigInteger getTaskFastestDisk(BigInteger task) throws MgmtException {
        return BigInteger.valueOf (System.currentTimeMillis());
    }
    public BigInteger getTaskAverageDisk(BigInteger task) throws MgmtException {
        return BigInteger.valueOf (System.currentTimeMillis());
    }
    public BigInteger getTaskNumFaults(BigInteger task) throws MgmtException {
        return BigInteger.valueOf (System.currentTimeMillis());
    }
    public BigInteger getTaskCompletionPercent(BigInteger task) throws MgmtException {
        return BigInteger.valueOf (System.currentTimeMillis());
    }
    public BigInteger getTaskErrorFreeStartTime(BigInteger task) throws MgmtException {
        return BigInteger.valueOf (System.currentTimeMillis());
    }
    public BigInteger getTaskOneCycleAgoTime(BigInteger task) throws MgmtException {
        return BigInteger.valueOf (System.currentTimeMillis());
    }

    public BigInteger getTaskId(String name) throws MgmtException {
        /*
        public static final String RM_DUPS_TASK = "RemoveDupFrags";
        public static final String RM_TEMP_TASK = "RemoveTempFrags";
        public static final String POP_SYS_TASK = "PopulateSysCache";
        public static final String POP_EXT_TASK = "PopulateExtCache";
        public static final String RECOVER_TASK = "RecoverLostFrags";
        public static final String SCANNER_TASK = "ScanFrags";
        */
        return BigInteger.valueOf(TaskList.taskId (name));

    }
    public String getTaskName(BigInteger id) throws MgmtException {
        /*
        public static final String RM_DUPS_TASK = "RemoveDupFrags";
        public static final String RM_TEMP_TASK = "RemoveTempFrags";
        public static final String POP_SYS_TASK = "PopulateSysCache";
        public static final String POP_EXT_TASK = "PopulateExtCache";
        public static final String RECOVER_TASK = "RecoverLostFrags";
        public static final String SCANNER_TASK = "ScanFrags";
        */
        return TaskList.taskLabel (id.intValue());

    }
}
