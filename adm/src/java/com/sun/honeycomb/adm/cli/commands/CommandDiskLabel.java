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

import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.editline.Editline;
import com.sun.honeycomb.adm.cli.editline.EditlineCompleter;
import java.math.BigInteger;
import java.lang.Byte;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Properties;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.io.EOFException;
import java.io.IOException;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.adm.cli.parser.OptionParser;
import com.sun.honeycomb.adm.cli.parser.OptionException;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.CliException;
import com.sun.honeycomb.adm.cli.FruCommand;
import com.sun.honeycomb.adm.client.*;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.adm.client.ClientUtils;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.admin.mgmt.client.HCNode;
import com.sun.honeycomb.admin.mgmt.client.HCFru;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.DiskHealth;
import com.sun.honeycomb.disks.DiskLabel;

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;

/* This class is the interface to display and modify DiskLabel parameters. 
 * It is a hidden cli and intended for disk service regression test automation
 * only. It can also be used for diagnostics */

public class CommandDiskLabel extends FruCommand 
    implements ExitCodes {

    private final MessageFormat _dl_formatLong 
            = new MessageFormat (getLocalString("cli.disklabel_long"));
    private final MessageFormat _dl_formatDiskLabel 
            = new MessageFormat (getLocalString("cli.disklabel"));

    private boolean allLabels = false;
    private final Option _optAllLabels;
    private final Option _optFruName;
    private final Option _optSiloId;
    private final Option _optDiskIndex;
    private final Option _optDlCellId;

    // flags to check what options were specified
    private Boolean opt_DlCellId = false;
    private Boolean opt_OnlyFruName = false;

    // DiskLabel values.
    private String fru_name = null;

    //disklabel parameters that can be set
    private int dlCellId = -1;
    private short   siloId = -1;
    private short   diskIndex = -1;
    private boolean only_fru_name = false;

    public CommandDiskLabel (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
        addCellIdOption(true);
        addForceOption();
        addNodeOption(false);

        _optDlCellId = addOption(OPTION_INTEGER, 'C', "dlCellId");
        _optSiloId = addOption(OPTION_SHORT, 's', "siloId");
        _optDiskIndex = addOption(OPTION_SHORT, 'd', "diskIndex");
        _optFruName = addOption(OPTION_STRING, 'f', "FRUID");
        _optAllLabels =  addOption(OPTION_BOOLEAN, 'a', "all");
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {

         // Temporary labels
        DiskLabel label = null;
        label = DiskLabel.getDiskLabel(null);

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

        allLabels = getOptionValueBoolean(_optAllLabels);
        if (getOptionValue(_optDlCellId) != null) {
            dlCellId = getOptionValueInteger (_optDlCellId);
            if (dlCellId < 0) {
                System.err.println("Invalid cellId");
                return EX_USAGE;
            }
            System.err.println("dlcell "+ dlCellId);
            label.setCellId(dlCellId);  
            opt_DlCellId = true;
        }

        fru_name = getOptionValueString(_optFruName);

        if (null != getOptionValue(_optSiloId)) {
            siloId = getOptionValueShort(_optSiloId);
            if (siloId < 0) {
                System.err.println("Invalid SiloId");
                return EX_USAGE;
            }
        }

        if (null != getOptionValue(_optDiskIndex)) {
            diskIndex = getOptionValueShort(_optDiskIndex);
            if (diskIndex > 3 ||  diskIndex < 0) {
                System.err.println("Invalid diskIndex, must be >= 0 and <=3");
                return EX_USAGE;
            }
        }

        // AllLabels 
        if (allLabels) {
            printAllLabels();
            return EX_OK;
        }

        // fru_name should be specified 
        if (null == fru_name || fru_name.length() == 0) {
            System.err.println (getLocalString("cli.disklabel.fru_name_needed"));
            return EX_USAGE;
        } else {
            retCode = validateDiskName(fru_name);
            if (retCode != EX_CONTINUE) {
                System.err.println ("Invalid FruName");
                return retCode;
            }
        }


        // only fru_name specified just dump the values
        if (fru_name != null && 
            null == getOptionValue(_optDlCellId) &&
            null == getOptionValue(_optSiloId) &&
            -1 == getNodeId() &&
            null == getOptionValue(_optDiskIndex)) {
            only_fru_name = true;
        } else {
            only_fru_name = false;
        }

        if (only_fru_name) {
            printOneLabel(fru_name);
            return EX_OK;
        }

        //Set the label.
        if (siloId != -1)
            label.setSiloId(siloId);
        if (dlCellId != -1) {
             label.setCellId(dlCellId);  
        }
        if (getNodeId() != -1)
            label.setNodeId((short)getNodeId());
        if (diskIndex != -1)
            label.setDiskIndex(diskIndex);

        try {
        // Here we set the label on disk
            if (-1 == setDiskLabel (fru_name, label)) {
                return EX_DATAERR;
            }
        } catch (CliException e) {
              System.err.println("Failed to set the disk label + e.getMessage()");
              return EX_USAGE;
        }
        return EX_OK;
    }

    //Print all the labels
    private void printAllLabels() 
        throws MgmtException, PermissionException, ConnectException {
        System.out.println (getLocalString("cli.disklabel.header"));
        Object[] frus = getApi().getFRUs(getCellId()).getFrusList().toArray();
        //
        //  Got through the fru's and print the labels for each disk
        //
        for (int i = 0; i < frus.length; i++) {
            HCFru fru = (HCFru) frus[i];
            if (!(fru instanceof HCDisk)) 
                continue;
            String labelString = null;
            // Load the disk for WS-management
            HCDisk disk;
            try {
                disk = getApi().getDisk(cellId, fru.getFruName());
            } catch (Exception exp) {
                System.err.println("Failed to fetch disk " + fru.getFruName());
                continue;
            }
            if (disk == null) {
                continue;
             }
            Object args = null;
            String msg = null;
            labelString = disk.getDiskLabel();
            args = new Object[] {
                disk.getFruName(),
                labelString
            };
             msg = _dl_formatLong.format (args);
             System.out.println (msg);
        }
    }

    // Populates the local DiskLabel object with values got from server.
    private void populateLabel(String labelString, DiskLabel label) {
        // Sample DiskLabel diskLabelCliString() output
        //  [0,0,108,3;1] /dev/rdsk/c1t1d0p0 (disabled)
        boolean toCommit = false;

        // Move them to the label
        try {
            label.updateDiskLabel(labelString, toCommit);
        } catch (IOException e) {
          System.err.println("Failed to populate label");
          return;
        }
    }
  
    // Get disk from fru
    private HCDisk getDiskFromFru(String fru_name)
        throws MgmtException, PermissionException, ConnectException {

        HCDisk disk;
        try {
            disk = getApi().getDisk(cellId, fru_name);
        } catch (Exception exp) {
            System.err.println("Failed to fetch disk " + fru_name);
            return null;
        }
        if (disk != null)
            return disk;
        //If the disk was not found bail
        System.out.println("Could not find" + fru_name);
        System.out.println(getLocalString("cli.disklabel.not_found"));
        return null;
    }

    // Update the label to be updated with the current values.
    // Used while writing out an updated label to server.
    private void refreshLabel(DiskLabel label, DiskLabel current) {
        if (dlCellId == -1)
            label.setCellId(current.cellId());
        if (siloId == -1)
            label.setSiloId(current.siloId());
        if (getNodeId() == -1)
            label.setNodeId(current.nodeId());
        if (diskIndex == -1)
            label.setDiskIndex(current.diskIndex());
        label.setLabelIncarnation(current.incarnation());
        label.setDisabled(current.isDisabled());
        label.setDevice(current.getDevice());
    }


    private void printOneLabel(String fru_name)
        throws MgmtException, PermissionException, ConnectException {
        HCDisk disk = getDiskFromFru(fru_name);
        String msg = null;
        if (disk == null) {
            return;
        }
        DiskLabel label = null;
        label = DiskLabel.getDiskLabel(null);
        String labelString = null;
        labelString = disk.getDiskLabel();
        populateLabel(labelString, label);
        Object args = label.formatDiskLabel();
        msg = _dl_formatDiskLabel.format (args);
        System.out.println (msg);
    }


    private int setDiskLabel (String fru_name, DiskLabel label) 
        throws CliException,ConnectException, PermissionException, MgmtException {
        DiskLabel old_label = null;
        old_label = DiskLabel.getDiskLabel(null);
        HCDisk disk = getDiskFromFru(fru_name);
        if (disk == null) {
            return -1;
        }
        String labelString = disk.getDiskLabel();
        //get the exisiting values in old_label.
        populateLabel(labelString, old_label);
        refreshLabel(label, old_label);
        try { 
            commitLabel(disk, label, old_label);
        } catch (CliException e) {
            return EX_IOERR;
        }
        return EX_OK;
    }
  
    // Commit the label
    private void commitLabel(HCDisk disk, DiskLabel new_label, DiskLabel old_label)
        throws MgmtException, CliException {
        Object args = null;
        String msg = null;
        String line = null;

        args = old_label.formatDiskLabel();
        msg = _dl_formatDiskLabel.format (args);
        System.out.println("---------------------------------------------------------");
        System.out.println("Current label");
        System.out.println("---------------------------------------------------------");
        System.out.println(msg);
        System.out.println("---------------------------------------------------------");
        System.out.println("New Label to commit");
        System.out.println("---------------------------------------------------------");
        args = new_label.formatDiskLabel();
        msg = _dl_formatDiskLabel.format (args);
        System.out.println(msg);

        if (isForceEnabled() == false && !promptForConfirm ("Warning: Not safe to modify disk label, continue? [y/N]: ", 'N')) {
            throw new CliException("Aborting...expected y/Y");
        } else {
            if (BigInteger.valueOf(0) != disk.applyDiskLabel(new_label.diskLabelCliString())) {
                throw new CliException("Failed to set the label: " + new_label.diskLabelCliString());
            }
        }
    }
}
