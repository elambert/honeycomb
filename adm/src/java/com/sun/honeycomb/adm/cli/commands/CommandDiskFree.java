
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

import java.util.Properties;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.PrecisionFormatter;
import com.sun.honeycomb.adm.cli.PrintfFormat;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.client.Statistics;
import com.sun.honeycomb.adm.client.ClientUtils;
import com.sun.honeycomb.adm.client.SiloInfo;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.admin.mgmt.client.HCDisks;

/**
 * Command-line app that reports logical and physical disk space
 */
public class CommandDiskFree extends ShellCommand 
implements ExitCodes {




    private final Option _optHuman;
    private final Option _optRaw;

    private PrintfFormat formatter 
            = new PrintfFormat("Total: %9s; Avail: %9s; Used: %9s; Usage: %s\n");

    private PrintfFormat cellLabelFormatter 
            = new PrintfFormat("\nCell %3s:\n");

    private PrintfFormat multiCellFormatter 
            = new PrintfFormat("Total: %11s; Avail: %11s; Used: %11s; Usage: %s\n");

    private String allCellsLabel = "All Cells:\n";

    private PrintfFormat cellUnreachableFormatter
            = new PrintfFormat("WARNING: Cell %1s is unreachable\n");
    
//    private PrintfFormat allCellsFormatter 
//            = new PrintfFormat("Total: %11s; Avail: %11s; Used: %11s; Usage: %s\n");
    
    public CommandDiskFree (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
        _optHuman = addOption (OPTION_BOOLEAN, 
                               getLocalString("cli.df.human_readable_char").charAt(0), 
                               getLocalString("cli.df.human_readable_name"));
        _optRaw   = addOption (OPTION_BOOLEAN, 
                               getLocalString("cli.df.physical_char").charAt(0), 
                               getLocalString("cli.df.physical_name"));
	addCellIdOption(false);
    }

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        boolean doHuman = false;
        boolean doRaw   = false;
        boolean allCells = false; // true if command to be executed on all cells

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

	doHuman = getOptionValueBoolean (_optHuman);
	doRaw   = getOptionValueBoolean (_optRaw);  

        /*
         * Check if cell id required
         */
        if (cellId == NO_CELL_SELECTED) {
            if (SiloInfo.getInstance().getCellCount() == 1) {
                cellId = SiloInfo.getInstance().getUniqueCellId();
                cell = getApi().getCell(cellId);
            } else { // multicell - all cells 
                if (doRaw) {
                    System.out.println(getLocalString(
                            "cli.cellid_required"));
                    return EX_USAGE;
                }
                // Command will be executed on all cells
                allCells = true;
            }
        }
        
        // Totals for all cells in mutlicell
        Statistics allCellsTotal = new Statistics();
        Statistics allCellsFree = new Statistics();
        Statistics allCellsUsed = new Statistics();
        
        if (allCells) {
            System.out.println(getDiskFreeAllCells(getCellId(),doRaw, doHuman));
        } else {
            // if not human readable, make the block size clear
            if (!doHuman) { 
                System.out.println(getLocalString("cli.df.block_size"));
            }

            System.out.println(getDiskFree(getCellId(), doRaw, doHuman,
                    formatter, allCellsTotal, allCellsFree, allCellsUsed));
        }

        return EX_OK;
    }
    
    /**
     *  Retrieves the disk usage information for a single cell
     *  @param cellId    the cell id of the cell
     *  @param raw       true if display raw, physical disk information
     *  @param human     true if display numbers in user-friendly units
     *  @param displayFormatter the PrintfFormat to use to display disk info
     *  @param allCellsTotal  stores total disk size for all cells
     *  @param allCellsFree   stores total disk free size for all cells
     *  @param allCellsUsed   stores total disk usage for all cells
     *
     *  @returns String  the formatted output of disk size and usage information
     *                   for the cell.
     */
    public String getDiskFree(byte cellId, boolean raw, boolean human,
            PrintfFormat displayFormatter,
            Statistics allCellsTotal, Statistics allCellsFree,
            Statistics allCellsUsed) 
        throws MgmtException, ConnectException {
	
	String total;
	String free;
	String used;
        StringBuffer dfStringBuffer = new StringBuffer();

        HCDisks diskObj = getApi().getDisks(cell.getCellId());      
        HCDisk[] disks = (HCDisk [])diskObj.getDisksList().
            toArray(new HCDisk[diskObj.getDisksList().size()]);
        

        Statistics clstr_totl = new Statistics();
        Statistics clstr_free = new Statistics();
        Statistics clstr_used = new Statistics();

        for (int i = 0; i < disks.length; i++) {
            // only count enabled disks 
            if (disks[i] == null) {
                continue;
            }
            if ( (disks[i].getStatus().intValue() != 1)) {
                continue;
            }
            // Disk values are in MB
            long disk_totl = disks[i].getTotalCapacity();
            long disk_free = disks[i].getTotalCapacity() - 
                    disks[i].getUsedCapacity();
            long disk_used = disks[i].getUsedCapacity();

            clstr_totl.add (disk_totl);
            clstr_free.add (disk_free);
            clstr_used.add (disk_used);

            if (raw) {
                dfStringBuffer.append(disks[i].getFruName()).append(": ");
                if (human) {
                    total = ClientUtils.reduceMBStorageUnit(disk_totl);
                    free = ClientUtils.reduceMBStorageUnit(disk_free);
                    used = ClientUtils.reduceMBStorageUnit(disk_used);
                } 
                else {
                    // TODO: Post 1.1
                    // Use Precission formatter to format longs.
                    // This will add "," to output.  Don't do
                    // now since would require reformatting output
                    // Not enough room given 80 char limit
                    total = Long.toString(disk_totl * ClientUtils.KBYTE);
                    free = Long.toString(disk_free * ClientUtils.KBYTE);
                    used = Long.toString(disk_used * ClientUtils.KBYTE);
                }


                double usage = (disk_totl > 0) ?
                        ((double)disk_used / (double) disk_totl) * 100 :
                        0.0;


                dfStringBuffer.append(
                    displayFormatter.sprintf(
                        new String[] {
                            total,
                            free,
                            used,
                            ClientUtils.getPercentage(usage) }));
            }
        }   // end for
        
        if (!raw) {
            if (human) {
                    total = ClientUtils.reduceMBStorageUnit(clstr_totl.total());
                    free = ClientUtils.reduceMBStorageUnit(clstr_free.total());
                    used = ClientUtils.reduceMBStorageUnit(clstr_used.total());
            }
            else {
		    // TODO: Post 1.1
		    // Use Precission formatter to format longs.
		    // This will add "," to output.  Don't do
		    // now since would require reformatting output
		    // Not enough room given 80 char limit
                    total = Long.toString(clstr_totl.total() * 
                            ClientUtils.KBYTE);
                    free = Long.toString(clstr_free.total() * 
                            ClientUtils.KBYTE);
                    used = Long.toString(clstr_used.total() * 
                            ClientUtils.KBYTE);
            }
            double usage = (clstr_totl.total() > 0) ? 
                ((double)clstr_used.total() / (double)clstr_totl.total()) * 100
                : 0.0;
                        
	    dfStringBuffer.append(
		displayFormatter.sprintf(
		    new String[] {
			total,
			free,
			used,
			ClientUtils.getPercentage(usage) }));
        }
        
        allCellsTotal.add(clstr_totl.total());
        allCellsFree.add(clstr_free.total());
        allCellsUsed.add(clstr_used.total());
        
        return dfStringBuffer.toString();

    }
    
    /**
     *  Gets the disk usage information for all cells in a multicell. Also
     *  calculates and outputs a total for all cells.
     *  Can not call this routine if raw output has been selected.
     *  @param cellId    the cell id of the cell
     *  @param raw       true if display raw, physical disk information
     *  @param human     true if display numbers in user-friendly units
     *
     *  @returns String  the formatted output of disk size and usage information
     *                   for the all the cells.
     */
    public String getDiskFreeAllCells(byte cellId, boolean raw, boolean human) 
        throws MgmtException, ConnectException {
	
	String total;
	String free;
	String used;
        double usage = 0.0;
        boolean cellAlive = false;
        StringBuffer cellErrors = new StringBuffer();
        
        HCCell[] cells = null;
        if (cellId != NO_CELL_SELECTED) {
            // Should not get here but handle just in case
            cells = new HCCell[1];
            cells[0] = cell;
        } else {
            // Note: This command can take a while if a cell is down
            System.out.println(getLocalString("cli.df.contact_cells") + 
                        "\n");

            cells = getApi().getCells(true);
        }

        // Totals for all cells in mutlicell
        Statistics allCellsTotal = new Statistics();
        Statistics allCellsFree = new Statistics();
        Statistics allCellsUsed = new Statistics();

        StringBuffer dfStringBuffer = new StringBuffer();
         
        for (int curCell = 0; curCell < cells.length; curCell++) {
            cell = cells[curCell];
            cellId = cell.getCellId();
            cellAlive = true;
            dfStringBuffer.append(cellLabelFormatter.sprintf(
                        new String[] {
                            Byte.toString(cellId)}));       
            
            if (!cell.isIsAlive()) {
                cellAlive = false;
                cellErrors.append(this.cellUnreachableFormatter.sprintf(
                        new String[] { Byte.toString(cellId)}));
                dfStringBuffer.append("Unavailable\n");
            }  else {

                dfStringBuffer.append(getDiskFree(cellId, raw, human,
                        multiCellFormatter, allCellsTotal, allCellsFree, 
                        allCellsUsed));  
            }
                        
        }   // end for all cells
        
        // Write out the total multicell line. This appears before the other
        // cell detail
        if (cells.length > 1) {
            if (human) {
                total = ClientUtils.reduceMBStorageUnit(
                        allCellsTotal.total());
                free = ClientUtils.reduceMBStorageUnit(
                        allCellsFree.total());
                used = ClientUtils.reduceMBStorageUnit(
                        allCellsUsed.total());
            }
            else {
                // TODO: Post 1.1
                // Use Precission formatter to format longs.
                // This will add "," to output.  Don't do
                // now since would require reformatting output
                // Not enough room given 80 char limit
                total = Long.toString(allCellsTotal.total() * 
                        ClientUtils.KBYTE);
                free = Long.toString(allCellsFree.total() * 
                        ClientUtils.KBYTE);
                used = Long.toString(allCellsUsed.total() * 
                        ClientUtils.KBYTE);
            }
            usage = (allCellsTotal.total() > 0) ?
                    ((double)allCellsUsed.total() / 
                        (double)allCellsTotal.total()) * 100 :
                     0.0;
            
            dfStringBuffer.insert(0,
                multiCellFormatter.sprintf(
                    new String[] {
                        total,
                        free,
                        used,
                        ClientUtils.getPercentage(usage) }));
            dfStringBuffer.insert(0, allCellsLabel);
            
            if (!human) { 
                dfStringBuffer.insert(0, getLocalString("cli.df.block_size") + 
                        "\n");
            }
            
            if (cellErrors.length() > 0) {
                cellErrors.append("\n");
                dfStringBuffer.insert(0, cellErrors.toString());
            }
        }
        return (dfStringBuffer.toString());

    }

    /**
     * Given a number of 1K blocks, returns a "human readable" unit (e.g.,
     * 1K, 234M, 2G, 16T, 3P)
     */
    public static final int KBYTE = 1024;


}
