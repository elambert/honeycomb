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

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.Properties;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import java.io.IOException;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.client.AdminClient;
// TODO: Move strings to localization db

import com.sun.honeycomb.adm.client.ServiceTagsUpdater;
import java.util.logging.Logger;
import java.util.logging.Level;

public class CommandUpgrade extends ShellCommand 
implements ExitCodes {

    private int DEFAULT_HTTP_PORT=80;
    // variable indicating if this is a DVD upgrade
    private boolean spdvd = false;
    // variable indicating whether the jar needs to be downloaded
    private boolean downloadjar = false;

    // bitwise operators - must be powers of 2. Correspond to status
    // returned from the methods called by CommandUpgrade
    private static final int STATUS_OK = 1;
    private static final int STATUS_ERROR = 2;
    private static final int STATUS_EXIT = 4;
    private static final int STATUS_REBOOTED = 8;

    private static final Logger logger = 
	Logger.getLogger(CommandUpgrade.class.getName());
    
    public CommandUpgrade (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);

        // hidden option for automated upgrade tests
	addForceOption();
	addCellIdOption(true);
    }

    public int main (Properties env, String[] argv) 
        throws MgmtException, PermissionException, ConnectException {
        if(!getApi().loggedIn())
            throw new PermissionException();

	int retCode = handleStandardOptions(argv, false);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}
	
	String[] unparsedArgs = getRemainingArgs();
        if (0 == unparsedArgs.length) {
	    System.out.println("Insufficient arguments.");
	    usage ();
	    return EX_USAGE;
        }
	
	if (unparsedArgs.length > 2) {
	    System.out.println("Unknown argument: " + unparsedArgs[2]);
	    usage ();
	    return EX_USAGE;
	}
        
        if ((1 == unparsedArgs.length) && 
            (unparsedArgs[0].equalsIgnoreCase("download")) ) {
            System.out.println("URL required.");
	    usage ();
	    return EX_USAGE;
        }
        
        if ((2 == unparsedArgs.length) &&
            (!unparsedArgs[0].equalsIgnoreCase("download")) ) {
	    System.out.println("Incorrect arguments.");
	    usage ();
	    return EX_USAGE;
	}
	
        HCCell cell = getApi().getCell(cellId);
        String src = null;

	String qualifier = "NONE";
	boolean downloadOnly = false;

        //
        // This is miserable, and should be cleaned up.
        //
        if (unparsedArgs.length == 1) {
	    src = unparsedArgs[0];
	}else if(unparsedArgs.length == 2) {
	    qualifier = unparsedArgs[0];
	    src = unparsedArgs[1];
	    if(qualifier.equalsIgnoreCase("download")) {
		downloadOnly = true;
	    }   
	}

	return upgrade(src, downloadOnly);
    }

    /*
     * Primary upgrade method which interacts with HCUpgradeAdapter
     * and the loaded upgrade jar to perform upgrade.
     * @param src - the String indicating the source of the upgrade image
     * @param downloadOnly - boolean indicating if this is a download only upgrade
     * @return int the status code
     */
    private int upgrade(String src, boolean downloadOnly) 
        throws ConnectException, MgmtException,PermissionException {
	int exitres = -1;
	BufferedWriter out
	    = new BufferedWriter(new OutputStreamWriter(System.out));
	int type = 0;

	spdvd = false; // dvd iso is mounted from sp, not downloaded
	downloadjar = false; // jar needs to be downloaded

	boolean success = false;

        try {
	    /* Log and set variables appropriately based on type of upgrade */
	    if(src.equalsIgnoreCase("dvd")) {
		/* For DVD upgrades, make sure the user has placed the
		 * DVD in the drive - stop upgrade if not */
		if (!isForceEnabled() 
		    && !promptForConfirm 
		    ("Is the DVD in the service node drive? [y/N]: ", 
		     'N')){
		    logger.log(Level.INFO,"UPGRADE: Upgrade exited - no DVD in drive");
		    out.write("Please try this command again with the DVD " +
			      "in the service node drive");
                    return EX_USERABORT;
                } 
		logger.log(Level.INFO,"UPGRADE: DVD upgrade");
		spdvd = true;
		type = CliConstants.UPGRADE_SPDVD;
	    } else if(src.equalsIgnoreCase("downloaded")) {
		logger.log(Level.INFO,
			   "UPGRADE: Upgrading using previously downloaded image");
		out.write("Upgrading using previously downloaded image...");
		out.newLine();
		out.flush();
		type = CliConstants.UPGRADE_DOWNLOADED;
	    } else {
		if (downloadOnly) {
		    logger.log(Level.INFO,"UPGRADE: HTTP image download only");
		    type = CliConstants.UPGRADE_DOWNLOAD_ONLY;
		}
		else {
		    logger.log(Level.INFO,"UPGRADE: HTTP upgrade");
		    type = CliConstants.UPGRADE_HTTP;
		}
		downloadjar = true;
	    }
	    
	    /* Call getJar to get the new upgrade jar from wherever it
	     * resides and place it in an expected location */
	    int res = getJar(src);
	    if (res != EX_OK) {
		out.write("Exiting upgrade due to errors getting new upgrade code");
		out.newLine();
		out.flush();
		return res;
	    }
	    
        } catch (Exception ioe) {
	    logger.log(Level.SEVERE, 
		       "UPGRADE: Threw an exception during upgrade: ", ioe);
            System.out.println("Got internal error during upgrade, exiting.");
            return EX_TEMPFAIL;
        }

        try {
	    logger.log(Level.INFO,"UPGRADE: Starting upgrade");
	    out.write("Starting upgrade...");
	    out.newLine();out.flush();
	    
	    /* Run the status check before doing anything */
	    exitres = getApi().statusCheck(cellId);
	    if ((checkRetVal(exitres, "It is not safe to upgrade the system.\n"
			    + getLocalString("cli.reboot.reasonPrompt")) 
		 & STATUS_OK) != STATUS_OK) {
		out.write("Exiting upgrade due to cluster state.");
		out.newLine();out.flush();
		return EX_IOERR;
	    }
	} catch (MgmtException me) {
	    logger.log (Level.SEVERE, "UPGRADE: Could not access upgrader: ", me);
	    System.out.println("Exiting upgrade due to errors.");
	    return EX_TEMPFAIL;
	} catch (IOException ie) {
	    logger.log (Level.SEVERE, "UPGRADE: Upgrade got IOException: ",ie);
            System.out.println("Got error: " + ie);
            ie.printStackTrace();
            return EX_IOERR;
        }
	try {
	    /* Initialize upgrader - sets up all the pre-upgrade stuff */
	    exitres = getApi().initializeUpgrader(cellId);
	    // Pass in null since we don't expect to get UPGRADE_ERROR_CONTINUE
	    if ((checkRetVal(exitres, null) & STATUS_OK) != STATUS_OK) {
		logger.log (Level.SEVERE, "UPGRADE: Failed to initialize upgrader: " 
			    + exitres + ". Exiting upgrade");
		out.write("Failed to initialize upgrader: " + exitres
			  + ". Exiting upgrade");
		out.newLine();out.flush();
		return EX_IOERR;
	    }
	
	    String question, method;
	    /* If downloadOnly, no need to ask user questions */
	    if (!downloadOnly) {
		/* If force is enabled, assume all answers are yes */
		if (!isForceEnabled()) {
		    /* User interactive section of upgrade */
		    while ((question = getApi().getNextQuestion(cellId)) 
			   != null) {
			/* All questions default to the 'Y' answer. */
			boolean response = promptForConfirm 
			    (question + " [Y/n]: ", 'Y');
			exitres = getApi().invokeNextMethod(cellId, response);
			
			if ((checkRetVal(exitres,null) & STATUS_OK) != 
			    STATUS_OK) {
			    logger.log(Level.WARNING, 
				       "UPGRADE: Failed to invoke next method: " 
				       + exitres);
			    out.write("Failed to invoke next method. Exiting upgrade.");
			    out.newLine();out.flush();
			    return EX_TEMPFAIL;
			}				
		    } 						
		} else {
		    /* if force set, then call setForceOptions */
		    exitres = getApi().setForceOptions(cellId);
		    if ((checkRetVal(exitres, null) & STATUS_OK) != 
			STATUS_OK) {
			out.write("Failed to set force options. Exiting upgrade.");
			out.newLine();out.flush();
			return EX_TEMPFAIL;
		    }
		}
	    }

	    /* Only need to download the iso if we're in a download mode -
	       either upgrade <src> or upgrade download */
	    if (downloadjar) {
		exitres = getApi().httpFetch(cellId, src);
		if ((checkRetVal(exitres, null) & STATUS_OK)!= STATUS_OK) {
		    logger.log (Level.SEVERE, "UPGRADE: Failed to fetch the new image with http "
			       + src);
		    out.write("Failed to fetch the new image with http. Exiting upgrade.");
		    out.newLine();out.flush();
		    return EX_TEMPFAIL;
		} 
		if (downloadOnly) {
		    /* if this is a downloadOnly, we're done */
		    logger.log(Level.INFO,
			       "UPGRADE: Upgrade download only : completed download");
		    out.write("Completed download.");
		    success = true;
		    return EX_OK;
		}
	    }
	    /* Mount the iso so we can read information from within it */
	    exitres = getApi().mountIso(cellId, spdvd);
	    if ((checkRetVal(exitres, null) & STATUS_OK) != STATUS_OK) {
		logger.log (Level.SEVERE, "UPGRADE: Exiting upgrade due to unmountable iso");
		out.write("Exiting upgrade due to unmountable iso");
		out.newLine();out.flush();
		return EX_TEMPFAIL;
	    }
	    if (!isForceEnabled()) {
		/* if force is enabled, assume yes, otherwise ask if
		 * user wants to continue */
		if ((question = getApi().getConfirmQuestion(cellId)) != null) {
		    if (!promptForConfirm(question + " [Y/n]: ", 'Y')) {
			logger.log(Level.WARNING,
				   "UPGRADE: Exiting upgrade due to user input: " +
				   question);
			return EX_USERABORT;
		    }
		}
	    }
	    /* Call method that does the bulk of the upgrade */
	    exitres = getApi().startUpgrade(cellId,spdvd);
	    if((checkRetVal(exitres, null) & STATUS_OK) != STATUS_OK) {
		logger.log (Level.SEVERE, "UPGRADE: Upgrade failed: " 
			   + exitres);
		out.write("Upgrade failed: " +
			  exitres);
		out.newLine();out.flush();
		return EX_IOERR;
	    } else {
		success = true;

                // An upgrade changes the software running on the system.
                // As a result the service tag registry needs to be updated
                // to reflect the software change since the version field
                // of the software running is stored within the service
                // tag entry in the service tag registry on the master cell
                serviceTagsRegistryUpdate();

	    }
        } catch (IOException ioe) {
	    logger.log (Level.SEVERE, "UPGRADE: Upgrade got IOException: ",ioe);
            System.out.println("Got error: " + ioe);
            ioe.printStackTrace();
	    return EX_IOERR;
        } catch (Exception e) {
	    logger.log (Level.SEVERE, "UPGRADE: Upgrade got Exception: ",e);
            System.out.println("Got error: " + e);
            e.printStackTrace();
	    return EX_TEMPFAIL;
	} catch (Throwable t) {
	    logger.log (Level.SEVERE, "UPGRADE: Caught throwable: ", t);
	    System.out.println("Got error: " + t);
	    t.printStackTrace();
	    return EX_TEMPFAIL;
	} finally {
	    /* Call the final step for upgrade that cleans everything
	     * up and does the reboot - pass in success to tell it if
	     * the upgrade was successful and the type of upgrade it
	     * was */
	    logger.log (Level.INFO, "UPGRADE: Invoking final upgrade step.");
	    exitres = getApi().finishUpgrade(cellId,type,success);
	    int checkedval = checkRetVal(exitres, null);
	    if ((checkedval & STATUS_OK) != STATUS_OK) {
		logger.log (Level.WARNING, "UPGRADE: Final upgrade step had problems.");
		System.out.println("Errors in final upgrade step.");
		return EX_TEMPFAIL;
	    } else if ((checkedval & STATUS_REBOOTED) == STATUS_REBOOTED) {
		System.out.println("Exiting; cell is rebooting.");
		System.exit(0);
	    }
	}
	return EX_OK;
    }
    
    /*
     * Private method to get the jar from the source and place it in a
     * known location to be loaded as part of upgrade.
     * @param src - String indicating the location of the jar
     * @return int the status code of the operation
     * @throws MgmtException
     * @throws PermissionException
     * @throws ConnectException
     */
    private int getJar(String src)
	throws MgmtException, PermissionException, ConnectException {

	int exitres = EX_TEMPFAIL;

	if (downloadjar) {
	    /* If this is a download, download the jar into the
	     * expected location */
	    logger.log (Level.INFO, "UPGRADE: Upgrade downloading new jar");
	    exitres=getApi().downloadJar(cellId,src);
	    if ((checkRetVal(exitres,null) & STATUS_OK) != STATUS_OK) {
		logger.log(Level.WARNING,
			   "UPGRADE: Exiting upgrade due to download errors");
		return EX_TEMPFAIL;
	    }
	} else {
	    /* If this is a downloaded or DVD upgrade, mount the image
	     * and copy the jar out of it into the expected
	     * location. */

	    /* Mount and unmount the image here to keep it logically
	       distinct from what is done from within the upgrade jar */
	    logger.log(Level.INFO,"UPGRADE: Upgrade mounting DVD image");
	    exitres=getApi().mountIso(cellId,spdvd);
	    if ((checkRetVal(exitres, null) & STATUS_OK) != STATUS_OK) {
		logger.log(Level.WARNING,
			   "UPGRADE: Exiting upgrade due to unmountable iso");
		return EX_TEMPFAIL;
	    }
	    exitres=getApi().copyJar(cellId);
	    if ((checkRetVal(exitres, null) & STATUS_OK) != STATUS_OK) {
		logger.log(Level.WARNING,
			   "UPGRADE: Exiting upgrade due to copy error");
		return EX_TEMPFAIL;
	    }
	    exitres=getApi().uMountIso(cellId,spdvd);
	    if ((checkRetVal(exitres, null) & STATUS_OK) != STATUS_OK) {
		logger.log(Level.WARNING,
			   "UPGRADE: Failed to unmount iso, error code: " + exitres);
		System.out.println("Failed to unmount iso, error code: " 
				   + exitres);
		return EX_TEMPFAIL;
	    }
	}
	return EX_OK;
    }

    /*
     * Private method to handle the return values of the methods
     * called by CommandUpgrade.  At the moment, we don't do anything
     * different with errors vs ok state, so return STATUS_OK for now
     * with "error but ok to continue" return values.
     * @param retval - return value to be handled
     * @param prompt - the question to be asked the user if this is a
     * ERROR_CONTINUE
     * @return int the status (defined above as STATUS_OK, etc)
     */
    private int checkRetVal(int retval, String prompt) {
	// At the moment 
    
	int value = 0;
	if ((retval & CliConstants.UPGRADE_REBOOTED) == 
	    /* UPGRADE_REBOOTED indicates that the system has been
	     * rebooted. Used only by finishUpgrade currently */
	    CliConstants.UPGRADE_REBOOTED) {
	    value |= STATUS_REBOOTED;
	} 
	if ((retval & CliConstants.UPGRADE_SUCCESS) == 
	    CliConstants.UPGRADE_SUCCESS) {
	    value |= STATUS_OK;
	} 
	if ((retval & CliConstants.UPGRADE_ERROR_CONTINUE) == 
	    /* ERROR_CONTINUE means that there's a problem but upgrade
	     * can continue, and to ask the user if we should
	     * continue. */
	    CliConstants.UPGRADE_ERROR_CONTINUE) {
	    if (prompt == null) {
		prompt = "Do you want to continue?";
		logger.log (Level.WARNING, "UPGRADE: Unexpected return value: " 
			   + retval + ", using default prompt.");
	    }
	    MessageFormat mf = new MessageFormat(prompt);
	    Object [] args = { "upgrade" };
	    if (!isForceEnabled() && 
		! promptForConfirm(mf.format(args), 'N')) {
		// If user says do not continue, then exit 
		logger.log (Level.WARNING, "UPGRADE: Bad return value: " + retval  
			   + ". Upgrade exiting due to user input");
		value |= STATUS_EXIT;
	    } else if (isForceEnabled()) {
		// If force is enabled, assume "no" and stop upgrade
		logger.log (Level.WARNING, "UPGRADE: Bad return value: " + retval
			   + ". Upgrade exiting (force enabled)");
		value |= STATUS_EXIT;
	    } else {
		// If user says yes, then keep going. return STATUS_OK (see above)
		logger.log (Level.WARNING, "UPGRADE: Bad return value: " + retval
			   + ". Upgrade continuing due to user input.");
		value |= STATUS_OK;
	    }
	} 
	if ((retval & CliConstants.UPGRADE_ERROR) == 
	    // See above. ERROR means a problem, but upgrade can
	    // proceed and we don't need to ask the user.  We return
	    // STATUS_OK because it's treated the same way in the
	    // calling methods for now.
	    CliConstants.UPGRADE_ERROR) {
	    value |= STATUS_OK;
	} 
	if ((retval & CliConstants.UPGRADE_FAILURE) == 
	    // Failure means exit.
	    CliConstants.UPGRADE_FAILURE) {
	    value |= STATUS_EXIT;
	}
	return value;
    }

    /**
     * The software on the system has changed.  This means the service tag
     * registry needs to be rebuilt with new instanceURNs so that if
     * the box is reregistered the IBIS system will recognise there's been
     * a change and update all the data at Sun.
     * <P>
     * On a single cell system we update the service tag registry unless
     * there's a validation error.
     * <P>
     * On a multi-cell hive we clear the service tag registry and instruct
     * the operator that they must perform the 'servicetags --refresh'
     * operation when all cells in the hive have been upgraded.
     * We do this since there's no way for us to know when all the cells
     * in the hive will be in sync.
     * @throws com.sun.honeycomb.adm.cli.PermissionException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     */
    public void serviceTagsRegistryUpdate() 
        throws PermissionException, ConnectException {
        try {
            if (getApi().getNumCells() == 1) {
                ServiceTagsUpdater.validateAndUpdateServiceTags(getApi());
            } else {
                ServiceTagsUpdater.clearServiceTagsRegistry(getApi(), null);
                StringBuffer buf = new StringBuffer();
                buf.append("CAUTION: The software version of the hive has changed as a result of the\n");
                buf.append("\tupgrade.  The service tag registry needs to be rebuilt.  Since this\n");
                buf.append("\tis a multi-cell hive this is a manual process.  Once all cells have\n");
                buf.append("\tbeen upgraded run the cli command, 'servicetags --refresh'.");
                System.out.println(buf.toString());
                buf = new StringBuffer();
                buf.append("Upgrade on multi-cell hive requires operator to ");
                buf.append("manually run the cli command 'servicetags --refresh'");
                buf.append("when all cells have been upgraded.");
                logger.log(Level.INFO, "UPGRADE: " + buf.toString());
            }
        }
        catch (MgmtException me) {
            // We don't want a MgmtException to stop upgrade 
            System.out.println(me.toString());
        }
    }
}
