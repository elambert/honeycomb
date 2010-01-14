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


package com.sun.honeycomb.adm.cli.commands; 

import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.AdmException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.adm.client.ClientUtils;
import com.sun.honeycomb.adm.client.SiloInfo;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;
import com.sun.honeycomb.admin.mgmt.client.HCDDTasks;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.admin.mgmt.client.HCDisks;
import com.sun.honeycomb.admin.mgmt.client.HCNode;
import com.sun.honeycomb.admin.mgmt.client.HCNodes;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.util.logging.Logger;
import java.util.logging.Level;


import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.Date;

/**
 * Command-line tool that reports system status information
 */
public class CommandStatus extends ShellCommand 
implements ExitCodes {

    private static final Logger logger =
        Logger.getLogger(CommandStatus.class.getName());


    private final MessageFormat _dd_format;
    private final MessageFormat testDateMsgFormat;
    private final MessageFormat queryIntegrityFormat;

    private final Option _optIntvl;
    private final Option _optVerbose;
    private final Option _optRecovery;
    private final Option _optIgnore;

    private static int MAX_INTERVAL = 60 * 30;   // 30 minutes


    public CommandStatus (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);

        _optIntvl    = addOption (OPTION_INTEGER, 'i', "interval");
        _optVerbose  = addOption (OPTION_BOOLEAN, 'v', "verbose");
        _optRecovery = addOption (OPTION_BOOLEAN, 'r', "recovery-status");
        _optIgnore   = addOption (OPTION_BOOLEAN, 'I', "ignore");
        
	addCellIdOption(false);
	addForceOption();
                                 
        // [TaskName] [last]|[not] completed at [timestamp] 
        //    [diskMin]/[diskAvg]/[diskMax] minutes with [errorCount] faults 
        //    Current cycle at [curCycle]%

      _dd_format
            = new MessageFormat (
              "{0} {1,choice,-1#not completed since|0<last completed at}" +
              " {1,choice,-1#boot|0<{2,date,EEE MMM dd} {2,time,HH:mm:ss zzz} {2,date,yyyy}}\n"+
              "\t{3,number,integer}/{4,number,integer}/{5,number,integer} minutes\n"+
              "\tCurrent cycle at {7}%\n\t"+
              "{8,choice,0#Not currently running error free|0<Error free since {9,date,EEE MMM dd} {9,time,HH:mm:ss zzz} {9,date,yyyy}}\n\t"+
              "{10,choice,0#Current cycle still pending|"+
              "0<Current cycle {12}"+
              " began at {11,date,EEE MMM dd} {11,time,HH:mm:ss zzz} {11,date,yyyy}}\n");
      
      testDateMsgFormat
            = new MessageFormat (
            "{0} {1,choice,-1#not completed since|0<last completed at}" +
            " {1,choice,-1#boot|0<{2,date,EEE MMM dd} {2,time,HH:mm:ss zzz} {2,date,yyyy}}");

      queryIntegrityFormat
            = new MessageFormat(
            "{0} {1,choice,0#not established|0<established as of"+
            " {2,date,EEE MMM dd} {2,time,HH:mm:ss zzz} {2,date,yyyy}}");
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        int     intvl   = 0;
        boolean verbose = false;
        boolean ignore = false;
        boolean recovery = false;
        boolean allCells = false;
	
        int retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}
	
	String[] unparsedArgs = getRemainingArgs();
	if (unparsedArgs.length > 0) {
	    System.out.println("Unknown argument: " + unparsedArgs[0]);
	    usage ();
	    return EX_USAGE;
	}

	intvl   = getOptionValueInteger (_optIntvl);
	verbose = getOptionValueBoolean (_optVerbose);
	ignore = getOptionValueBoolean (_optIgnore);
	recovery = getOptionValueBoolean (_optRecovery);
	
	if (recovery || ignore) {
	    if (isHiddenOptionProceed() == false) {
		return EX_USERABORT;
	    }
	}

	if(intvl < 0) {
            System.out.println(
		"Invalid interval of '" + intvl 
		+ "' specified. Value must be positive.");
	    return EX_USAGE;
	}
	if (intvl > MAX_INTERVAL) {
	    System.out.println("Specified interval value is too large.  Maximum allowed\n" +
		"interval is " + MAX_INTERVAL + " seconds.");
	    return EX_USAGE;
	}
	intvl = intvl * CliConstants.ONE_SECOND;
        
        /*
         * Check if cell id required
         */
        if (cellId == NO_CELL_SELECTED) {
            if (SiloInfo.getInstance().getCellCount() == 1) {
                cellId = SiloInfo.getInstance().getUniqueCellId();
                cell = getApi().getCell(cellId);
            } else { // multi cell
                if (verbose || recovery) {
                    System.out.println(getLocalString(
                            "cli.cellid_required"));
                    return EX_USAGE;
                }
                // Command will be executed on all cells
                allCells = true;
            }
        }
        
        try {
            if(allCells) {
                HCCell[] cells = getApi().getCells(true);
                for(int curCell=0; curCell < cells.length; curCell++) {
                    byte curCellId=cells[curCell].getCellId();            
                    if(cells[curCell].isIsAlive()){
                        retCode=allStatus(cells[curCell], intvl, verbose,
                                ignore, recovery);
                    } else {
                        Object[] args = {Byte.toString(curCellId)};
                        String msg = getLocalString(
                                "cli.df.cell_unreachable_warn");
                        if (msg == null) {
                            msg = "";
                        }
                        System.out.println(MessageFormat.format(msg, args));                    
                    }
                    if(retCode != EX_OK) 
                        return retCode;
                    
                    System.out.println("");

                }
            } else {
                return allStatus(getApi().getCell(cellId), intvl, verbose,
                        ignore,recovery);
            }
        } catch (AdmException e) {
            printError("Internal adm error - failing.",e);
        }

        return EX_OK;

    }

    private int allStatus(HCCell cell,
                          int     intvl,
                          boolean verbose,
                          boolean ignore,
                          boolean recovery) 
            throws AdmException, ConnectException,MgmtException {
        int retval;
        HCDDTasks tasks = getApi().getDdTasks(cell.getCellId());
        while (true) {
            if (verbose) {
                retval = hwstatus (cell,tasks);
            } else if (recovery) {
                retval = recoveryStatus(cell,tasks);
            } else {
                retval = status (cell,tasks,ignore);
            }

            if (intvl <= 0) {
                return retval;
            }

            try {
               Thread.sleep (intvl);
            } catch (InterruptedException ie) {
                return EX_OSERR;
            }
        }
    }

    private int status (HCCell cell,HCDDTasks tasks,boolean ignore) 
        throws MgmtException, ConnectException {

        AdminClient api = getApi();
        HCDisks disks = api.getDisks(cell.getCellId());
        HCNodes nodes = api.getNodes(cell.getCellId());
        double mbFree = api.getFreeDiskSpace(disks);
        int numDisks = api.getNumNodes(cell) * 4;
        int numAliveDisks = api.getNumAliveDisks(nodes);

        boolean quor = api.hasQuorum(cell);

        long queryIntegrityTime = api.getQueryIntegrityTime(cell);

        String queryStatus;

        if (ignore) {
            queryStatus = "Unknown";
        } else {
            queryStatus = getApi().getHadbStatus(cell.getCellId());
        }


        HCCellProps cellProps = getApi().getCellProps(cell);

        long scanFragsLast
            = tasks.getTaskCompletionTime(tasks.getTaskId("ScanFrags")).longValue();
        long lostFragsLast
            = tasks.getTaskCompletionTime(tasks.getTaskId("RecoverLostFrags")).longValue();
        long popExtLast
            = tasks.getTaskCompletionTime(tasks.getTaskId("PopulateExtCache")).longValue();

        String backupState;
        try{
            backupState = api.getBackupStatus(cell.getCellId());
        }
        catch (Exception e){backupState = e.toString();}

        System.out.println("Cell "+cell.getCellId()+
                           ": "+ 
                           (quor ? "Online" : "Offline")+
                           ". Estimated Free Space: "+
                           ClientUtils.reduceMBStorageUnit(mbFree));
        //
        // We're not reporting disks on down nodes as unhealed failures. That's wrong.
        //
        //
        // Fixme - export number of disks per node, don't hardcode this!
        //


        System.out.println(Integer.toString(api.getNumAliveNodes(nodes))+
                           " nodes online, "+
                           Integer.toString(numAliveDisks)+" disks online.");

        System.out.println("Data VIP "+cellProps.getDataVIP()+", Admin VIP "+cellProps.getAdminVIP());

        System.out.println("Data services "+(api.getProtocolStatus(nodes) ? "Online" : "Offline" )+
                           ", Query Engine Status: "+queryStatus);


        Object[] args = {
            "Data Integrity check",
            new Long(scanFragsLast),
            new Date (scanFragsLast)
        };
        System.out.println (testDateMsgFormat.format (args));

        args =  new Object[] {
            "Data Reliability check",
            new Long(lostFragsLast),
            new Date (lostFragsLast)
        };
        System.out.println (testDateMsgFormat.format (args));

//         args = new Object[] {
//             "Query Integrity check",
//             new Long(popExtLast),
//             new Date (popExtLast)
//         };
//         System.out.println (testDateMsgFormat.format (args));

        args = new Object[] { "Query Integrity",
                 new Long(queryIntegrityTime),
                 new Date(queryIntegrityTime)
        };
        System.out.println (queryIntegrityFormat.format (args));

        args = new Object[] { backupState };
        System.out.println (CommandNDMPStatus.backupMsgFormat.format (args));
        
        return EX_OK;
    }


    /**
     *
     */
    private int hwstatus (HCCell cell,HCDDTasks tasks) 
        throws ConnectException, MgmtException {

        HCNode[] nodes = null;
        int numNodes;
        
        try {
            HCNodes nodesObj = getApi().getNodes(cell.getCellId());
            nodes = (HCNode [])nodesObj.getNodesList().
                toArray(new HCNode[nodesObj.getNodesList().size()]);
            numNodes = getApi().getNumNodes(cell);           
        } catch (MgmtException e) {
            System.out.println (e.getMessage());
            return EX_TEMPFAIL;
        }

        if (nodes == null) {
            System.out.println("No nodes available.");
            return EX_UNAVAILABLE;
        }

        for (int i = 0; i < numNodes; i++) {
            System.out.println("NODE-"+nodes[i].getNodeId()+"\t\t["+getNodeState (nodes[i])+"]");


            if (nodes[i].isIsAlive()) {
                HCDisk[] disks = null;
                try {
                    disks = getApi().getDisksOnNode 
                        (cell.getCellId(),nodes[i].getNodeId().intValue());

                } catch (MgmtException ce) {
                    System.out.println (ce.getMessage());
                }

                if (disks == null) {
                    //System.out.println ("cell unavailable");
                    //return EX_UNAVAILABLE;
                    continue; // just skip it if we have no disk proxy
                              // because it's possible the node has joined
                              // the ring but no services are started
                }

                for (int j = 0; j < disks.length; j++) {

                    if (disks[j] == null) {
                        continue; // skip missing disks
                    }

                    System.out.println("Disk "+disks[j].getDiskId()+"\t\t["+getDiskState (disks[j])+"]");
                }
            }
        }

        return EX_OK;
    }

    private int recoveryStatus (HCCell cell,HCDDTasks tasks) throws ConnectException {

        int curCellId=cell.getCellId();
        try {
            for (int i = 0; i < tasks.getNumTasks().intValue(); i++) {
                long compTime =
                    tasks.getTaskCompletionTime(BigInteger.valueOf(i)).longValue();
                long errorFreeTime =
                    tasks.getTaskErrorFreeStartTime(BigInteger.valueOf(i)).longValue();
                long oneCycleAgoTime =
                    tasks.getTaskOneCycleAgoTime(BigInteger.valueOf(i)).longValue();
                
                long errorFreeCycleTime = 
                    (oneCycleAgoTime <= 0 ? 0 :
                     (errorFreeTime > oneCycleAgoTime ? 0 : oneCycleAgoTime));
                Object[] args = {
                    tasks.getTaskName(BigInteger.valueOf(i)),
                    new Long(compTime),
                    new Date (compTime),
                    new Long (tasks.getTaskFastestDisk(
			BigInteger.valueOf(i)).longValue()/CliConstants.ONE_MINUTE),
                    new Long (tasks.getTaskAverageDisk(
			BigInteger.valueOf(i)).longValue()/CliConstants.ONE_MINUTE),
                    new Long (tasks.getTaskSlowestDisk(
			BigInteger.valueOf(i)).longValue()/CliConstants.ONE_MINUTE),
                    new Long (tasks.getTaskNumFaults(BigInteger.valueOf(i)).longValue()),
                    new Integer (tasks.getTaskCompletionPercent(BigInteger.valueOf(i)).intValue()),
                    new Long(errorFreeTime),
                    new Date(errorFreeTime),
                    new Long(oneCycleAgoTime),
                    new Date(oneCycleAgoTime),
                    (errorFreeCycleTime > 0 ? "(error-free)" : "(not error-free)")
                };
                System.out.println (_dd_format.format (args));
            }
        } catch (MgmtException ce) {
            
            System.out.println (ce.getMessage());
        }

        return EX_OK;
    }

    /**
     * Creates a String representging the state of this Node FRU
     */
    private String getNodeState (HCNode node) {
        if (node.isIsAlive()) {
            return getLocalString ("common.incluster");
        } else {
            return getLocalString ("common.off");
        }
    }

    /**
     * Creates a String repreenting the state of this Disk FRU
     */
    private String getDiskState (HCDisk disk) {
        //
        // see hcadm.mof for constant defs; fixme, should be able to be a constant
        // in generated HCNode file. fixme
        //
        if (disk.getStatus().intValue()==1) {
        return getLocalString ("common.incluster");
        } if (disk.getStatus().intValue()==0)  {
            return getLocalString ("common.off");
        } else {
            return "unknown";
        }
        
    }

}
