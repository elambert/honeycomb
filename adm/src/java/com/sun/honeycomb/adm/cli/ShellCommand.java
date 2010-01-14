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


package com.sun.honeycomb.adm.cli;

import com.sun.honeycomb.adm.cli.parser.OptionParser;
import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.adm.client.SiloInfo;
import com.sun.honeycomb.adm.client.ClientUtils;
import com.sun.honeycomb.adm.client.AdminClientInternal;
import com.sun.honeycomb.adm.cli.config.CliBundleAccess;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.parser.OptionException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.FruNamePatterns;
import com.sun.honeycomb.mgmt.common.MgmtException;


import java.io.IOException;
import java.io.EOFException;
import java.lang.Thread;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Properties;

import java.util.regex.Matcher;



/**
 * <p>
 * Helper base class for implementors of {@link IShellCommand}. Defines some
 * handy and useful functions.
 * </p>
 *
 */
public abstract class ShellCommand extends OptionParser
    implements IShellCommand {
   
    protected String   _name     = null;
    protected String[] _aliases  = null;
    protected boolean  _isHidden = false;
    protected final byte NO_CELL_SELECTED = -1;
    protected byte cellId = NO_CELL_SELECTED;
    
    protected HCCell cell = null;
    private boolean isForceEnabled = false;
    private boolean isCellIdRequired = false;
    private boolean isAllCellsEnabled = false;
    private boolean isNodeOptionHidden = false;

    private Option _optCellId = null;
    private Option _optHelp  = null;
    private Option _optForce  = null;
    private Option _optNodeName = null;
    
    public static final String CELLID_KEY = "cellid";

    /**
     * The Node name string entered by the user via -n / --node option
     */
    private String sNodeName = null;

    /**
     * The node id corresponding to node name the user entered via
     * -n / --node option
     */
    private int nodeId = -1;
    
    /**
     * The node count for the current cell.  Only set when -n / --node
     * option is used.
     */
    private int nodeCount = -1;

    /**
     * Boolean flag to indicate whether or not the Sun Service warning
     * was been displayed or not.  
     */
    private boolean hasWarningBeenDisplayed = false;

    /*
     * Constants for hidden command Sun Customer Service only warning message
     */
    public static final String SUN_SERVICE_COMMAND_ONLY_WARNING = 
	"CAUTION: This command is intended for Sun Customer Service only.";
    
    /*
     * Constants for hidden option Sun Customer Service only warning message
     */
    public static final String SUN_SERVICE_OPTION_ONLY_WARNING =
        "CAUTION: The specified option is intended for Sun Customer Service only.";
    
    /*
     * Constants for visible command which should be run ONLY 
     * at the request of Sun Customer Service to send log info back to Sun
     */
    public static final String SUN_SERVICE_COMMAND_LOG_REQUEST_ONLY = 
	"CAUTION: This command is only intended to be run at the request of " +
            "\nSun Customer Service for sending log data back to Sun.";

     /*
      * Constants for proceed prompt
      */
    public static final String PROCEED_PROMPT = "Proceed? [y/N]: ";

    

    /**
     * Creates a new instance of a shell command
     *
     * @param name the name of the command
     * @param aliases[] list of aliases for the command
     * @param isHiddenCommand true if command is hidden, false otherwise
     */
    protected ShellCommand (String name, 
    	String[] aliases, 
	Boolean isHiddenCommand) 
    {
        super();
        cellId=-1;
        _name     = name;
        _aliases  = aliases;
        _isHidden = isHiddenCommand.booleanValue();
        _optHelp = addOption (OPTION_BOOLEAN, '?', "help");
	
	if (_isHidden) {
	    // If a command is hidden we automatically add the
	    // force option to ensure the y/n prompting of the
	    // Sun Service Option can be ignored if desired.
	    addForceOption();
	}
    }

    /**
     * Adds the -F/--force option to the list of available options.
     * This option is intended to be used to override Y/N prompting for
     * a command.   Use the isForceEnabled() to test whether the force option
     * has been passed the user once handleStandardOptions() has been
     * called.  The addForceOption() is automatically added to hidden commands
     */
    public void addForceOption() {
 	_optForce = addOption(OPTION_BOOLEAN, 'F', "force");
    }

    /**
     * Adds the -c/--cellid option to the list of available options.
     * If the <code>required</code> option is set to false.  The callee
     * should call handleOptionalCellId() prior to using an option that
     * requires that the cell id be set. 
     * @param required whether the parameter is required or not.
     */
    public void addCellIdOption(boolean required) {

	// TODO: We should really structure Options code to
	// support required arguments and such.  Too bad it
	// doesn't currently
	_optCellId = addOption(OPTION_BYTE, 'c', CELLID_KEY);
	isCellIdRequired = required;
    }


    /**
     * Adds the -n/--node option to the list of available options.
     * @param isHidden flag to indicate whether option is hidden or not.
     */
    public void addNodeOption(boolean isHidden) {
	_optNodeName = addOption(OPTION_STRING, 'n', "node");
	isNodeOptionHidden = isHidden;
    }

    
    /**
     * Set values back to there defaults
     */
    private void resetToDefaults() {
	hasWarningBeenDisplayed = false;
	cellId = -1;
	cell = null;
	nodeId = -1;
	nodeCount = -1;
	sNodeName = null;
    }

    // --- Start of IShellCommand Interface ---

    /**
     * @see IShellCommand#getName()
     */
    public String getName() {
        return _name;
    }
    
    /**
     * @see IShellCommand#getAliases()
     */
    public String[] getAliases() {
        return _aliases;
    }

    /**
     * @see IShellCommand#isHidden()
     */
    public boolean isHidden() {
        return _isHidden;
    }
    

    /**
     * Output the usage (ie. Help) for the command.
     * Hidden commands will output a message indicating the
     * command is meant for Sun Customer Service Only
     */
    public void usage() 
    throws MgmtException {
	if (isHidden() && hasWarningBeenDisplayed == false) {
	    System.out.println(
		generateMsgBox(SUN_SERVICE_COMMAND_ONLY_WARNING));
	    hasWarningBeenDisplayed = true;
	}
        System.out.print (getLocalString("cli.usage"));
        System.out.print (": ");
        System.out.print (   getName());
        System.out.print(" ");
        System.out.println(getUsageOptions());
        String help = getUsage();
	if (help != null && help.length() > 0) 
	    System.out.println(help);
    }

    public abstract int main (Properties env, String[] argv)
        throws MgmtException, PermissionException, ConnectException;


    // --- End of IShellCommand Interface ---

    /**
     * Valid the passed in cellId
     * @param cellId the id of the cell to validate
     * @return boolean true if cell is valid, false otherwise
     */
    protected boolean validateCellId(byte cellId) throws MgmtException {

        boolean result = false;

        try {
            result = SiloInfo.getInstance().isCellExist(cellId);
        } catch (ConnectException e) {
            exitConnectError(e);
        }

        return result;
    }

    /**
     * Handler for exiting cli due to a permission exception
     * @param e the permission exception
     */
    protected void exitPermissionException(PermissionException e) {
        if (e.getMessage() != null) {
            System.out.println(e.getMessage());
        } else {
            System.out.println("Failed to connect to the cell. " +
              "Permission error");
        }
        System.exit(1);
    }
    
    
    /**
     * Handler for exiting cli due to a connection exception
     * @param e the connection exception
     */
    protected void exitConnectError(ConnectException e) {
        System.out.println("Unable to connect to master cell in hive: " +
                           e.getMessage()+
                           ". Hive may not be online.");
        System.exit(1);
    }
            
    /**
     * @return id get the cellId that was set when handleStandardOptions() was called.
     * Will return -1 if value is not required and was not a passed in parameter by the user.
     */
    protected byte getCellId() {
        return cellId;
    }
    
    /**
     * @return cell the cell that was set when handleStandardOptions() was called.
     * May be null if cellid is not a required argument.
     */
    protected HCCell getCell() {
	return cell;
    }
    
    public boolean isMultiCell() 
    throws MgmtException, ConnectException, PermissionException {
	return (SiloInfo.getInstance().getCellCount() > 1);
    }
    

    /**
     * @return String the usage statement for the command
     */
    protected String getUsageOptions() 
    throws MgmtException {
	
	StringBuffer buf = new StringBuffer();
	StringBuffer key = new StringBuffer("cli.");
	key.append(getName()).append(".opts");
	
	// If the addCellIdOption() has been added
	// We want to see whether this is a single or multi-cell hive
	// -c <cellid> is typically required for multi-cell
	if (_optCellId != null && isCellIdRequired) {
	    try {
		if (SiloInfo.getInstance().getCellCount() != 1) {
		    // This is a multicellsystem.  See if usage statement
		    // has been overridden. At times we may want to
		    // override the usage system for multi-cell.  For example
		    // if the only argument the command supports is -c
		    // the saying "-c <cellid> [options]" wouldn't be correct
		    //
		    // We first check to see if custom form of the options is
		    // present.  cli.$command.opts.multicell.   If it is the
		    // lookup will return the string we want to output.  If
		    // it's not present in the resource bundle we'll get back
		    // the fall back message of "The string resource ...".
		    // In the later case we default to the standard multi-cell
		    // usage of "-c <cellid>"
		    StringBuffer multiCellKey = new StringBuffer(key);
		    multiCellKey.append(".multicell");
		    String str = getLocalString(multiCellKey.toString());
		    if (str != null && str.startsWith("The string resource") == false) {
			return str;
		    }
		    // usage statement was not overridden add the required
		    // cellid paramter to the usage statement
		    buf.append("-c <cellid> ");
		}
	    }
	    catch (ConnectException ce) {
		exitConnectError(ce);
	    } 

	}
	buf.append(getLocalString(key.toString()));
        return buf.toString();
    }

    /**
     * @return String the usage/help for the command
     */
    protected String getUsage() {
        return getLocalString ("cli." + getName() + ".usage");
    }
    

    /**
     * General method for handling and parsing the standard options.  
     *
     * @param argv the argument list entered by the user
     * @param noArgsAllowed.  boolean flag.  If true indicates that if
     * no arguments are passed by the user the usage message for the command
     * should be displayed.  If false no action is taken if no args passed.
     * @return int the return status.  If status is any value other than 
     * EX_CONTINUE he callee should exit with that status code.
     */
    protected int handleStandardOptions(String[] argv, boolean noArgsAllowed) 
    throws MgmtException 
    {

	resetToDefaults();

	if ((argv == null || argv.length == 0) && noArgsAllowed == false)
	{

	    usage();
	    return ExitCodes.EX_USAGE;
	}
	try 
	{
	    parse(argv);
	}
	catch (OptionException oe) {
	    System.out.println (oe.getMessage());

	    usage();
	    return ExitCodes.EX_USAGE;
	}
	

        if (getOptionValue (_optHelp) != null) {

            usage ();
	    return ExitCodes.EX_OK;
        }
	
	// Force option check must come before hidden command check
	if (_optForce != null) {
	    isForceEnabled = getOptionValueBoolean (_optForce);
	}

	// Hidden command check 
	// 
	// This must be the 2nd item checked.
	// We only allow the help option to be performed
	// without a prompt.  Since the help will indicate
	// that the command is intended for Sun Customer Service
        if (isHidden()) {
            if (isHiddenCommandProceed() == false) {

               return ExitCodes.EX_USERABORT;
	    }
        }
	
	int retCode = handleCellIdOption();
	if (retCode != ExitCodes.EX_CONTINUE)
	    return retCode;

	retCode = handleNodeOption();

	return retCode;
    }
    
    /**
     * When the specifying of the cell id is optional, ie
     * addCellOption(false).  This call should be called
     * before processing any options that require that the HCell object
     * be populated. This function will set the HCCell when running
     * on a single cell system.  If the system is a multi-cell it will
     * output the error message specified.
     * <P>
     * This method should be called after handleStandardOptions()
     * @param errorMsg the error message to output when --cellid is not
     * specified and the system is a multi-cell system where the cellid
     * can not be determined
     * @return int the status code, ExitCodes.EX_USAGE if an error message
     * passed is in output.  Otherwise ExitCode.EX_CONTINUE
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    protected int handleOptionalCellId(String errorMsg)
    throws MgmtException { 
        try {
             if (getCellId() == -1) {
                if (SiloInfo.getInstance().getCellCount() != 1) {
                    System.out.println(errorMsg);
                    return ExitCodes.EX_USAGE;
                }
             	cellId = SiloInfo.getInstance().getUniqueCellId();
             	cell = getApi().getCell(cellId);
	    }	
         }
         catch (ConnectException e) {
             exitConnectError(e);
         }
        return ExitCodes.EX_CONTINUE;
    }

    /**
     * Handle the processing of the cellid option.
     * If addOptionCellId required option was set to true it will ensure
     * that a cellid parameter was passed in if a multicell hive.
     * If single cell system set value for user if not specified.
     * @return int cli status.  Any value other ExitCodes.EX_CONTINUE indicates
     * processing of the cli command should terminate by callee
     * @throws MgmtException
     */
    protected int handleCellIdOption() 
    throws MgmtException {

	if (cellId != -1) {
	    // cellId is already set.  Skip
	    return ExitCodes.EX_CONTINUE;
	}
	if (_optCellId != null) {
	    try {

                cellId = getOptionValueByte(_optCellId);
           	if (cellId != -1) { 
		    if (SiloInfo.getInstance().isCellExist(cellId) == false) {
                    	System.out.println("Invalid cell ID specified - "
                            + cellId + " isn't a valid cell.");
		    	return ExitCodes.EX_DATAERR;
		    }
                } else if (isCellIdRequired && cellId == -1) {
            	    // If cellId is required and the user doesn't
            	    // specify use cell 0 if this is a single cell hive
            	    // otherwise generate an error
		    if (SiloInfo.getInstance().getCellCount() != 1) {
			System.out.println("This is a multi cell hive, the cell ID must be specified.");
			return ExitCodes.EX_USAGE;
		    }
		    cellId = SiloInfo.getInstance().getUniqueCellId();
		}
		if (cellId != -1)
		    cell = getApi().getCell(cellId);    
            }
	    catch (ConnectException e) {
            	exitConnectError(e);
            }

   	}
	return ExitCodes.EX_CONTINUE;	
    }

    /**
     * Handle the processing the node option.  Validate the node parameter
     * and output appropriate error message if not valid.
     * @return int cli status.  Any value other EX_OK indicates
     * processing of the cli command should terminate by callee
     * @throws MgmtException if cell id is not set
     * @throws ConnectionException
     */
    protected int handleNodeOption() 
    throws MgmtException {
	if (_optNodeName != null) {
            String sNodeName = getOptionValueString(_optNodeName);
            if (sNodeName != null) {
		if (isHidden() == false && isNodeOptionHidden == true) {
		    if (isHiddenOptionProceed() == false)
			return ExitCodes.EX_USERABORT;
		}
		return validateNodeName(sNodeName);
            }
        }
	return ExitCodes.EX_CONTINUE;
    }
    
    private final static String MSG_HWSTAT_FOR_NODES =
	"Use hwstat for a list of valid node names.";
    
    /**
     * Validate the passed in node name.  If the node name is valid 
     * calls to getNodeName(), getNodeId(), and getNodeCount() can
     * be made to retrieve node related information.
     * @param nodeName the node name to validate.  Generates
     * error messages to standard out if name is invalid.
     * @return int exit status if not EX_CONTINUE
     */
    protected int validateNodeName(String nodeName)
    throws MgmtException {
	assert(nodeName != null);
	Matcher nodeMatcher = FruNamePatterns.NODE_NAME_PATTERN.matcher(nodeName);
	if (nodeMatcher.matches() == false) { 
	    System.out.println("Invalid node name, " + nodeName 
		+ " specified. Must be in the form NODE-1xx.\n"
		+ MSG_HWSTAT_FOR_NODES); 
	    return ExitCodes.EX_DATAERR;
	}
	// It's a valid node pattern.  Make sure it falls within
	// the boundaries for the cell
	int nodeNum = Integer.parseInt(nodeMatcher.group(1));
	
	int nodeCount = 0;
	try {
	    nodeCount = getApi().getNumNodes(getCellId());
	}
	catch (ConnectException ce) {
	    exitConnectError(ce);
	}
	
	if (nodeNum == 0) {
	    System.out.println("Invalid node name, " + nodeName 
		+ " specified.\nValid nodes names start at NODE-101 "
		+ "and end at " + getNodeName(nodeCount) + ".\n"
		+ MSG_HWSTAT_FOR_NODES); 
	    return ExitCodes.EX_DATAERR;
	}
	if (nodeNum > nodeCount) {
	    System.out.println("Invalid node name, " + nodeName 
		+ " specified. Cell " + getCellId()
		+ " only has " + nodeCount + " nodes.\n"
		+ MSG_HWSTAT_FOR_NODES); 
	    return ExitCodes.EX_DATAERR;
	}
	this.nodeId = 100 + nodeNum;
	this.sNodeName = nodeName;
	this.nodeCount = nodeCount;
	return ExitCodes.EX_CONTINUE;
    }

    protected void exit() {
        Shell.getCurrentShell().terminate();
    }

    protected ShellCommandManager getManager() {
        return Shell.getCurrentShell().getShellCommandManager();
    }
    
    /**
     * An interactive shell is one with a terminal, e.g., there is a user
     * typing stuff at the other end
     */
    protected boolean isInteractive() {
	return Shell.getCurrentShell().isInteractive();
    }


    /**
     * Prompts the user, with command-line editing, completion, echo, etc
     * enabled.
     */
    protected String prompt (String text)
        throws IOException, EOFException {
        return Shell.getCurrentShell().prompt (text);
    }

    protected String promptNoEcho (String text)
	throws IOException, EOFException {
	return Shell.getCurrentShell().promptNoEcho (text);
    }

    protected String getLocalString(String l) {
        try {
            return CliBundleAccess.getInstance().getBundle().getString(l);
        } catch (MissingResourceException e) {

            String missingString=getLocalString("common.missingString");

            return missingString;
        }
        
    }



    /**
     * Return a handle to an API object which allows communication with the
     * admin server
     */
    protected AdminClient getApi() {
        return Shell.getCurrentShell().getApi();
    }

    protected AdminClientInternal getInternalApi() {
        return Shell.getCurrentShell().getInternalApi();
    }
    
    /**
     * Prompt for y/n confirmation
     * @param prompt the prompt string
     * @param defaultch the default character to accept when return pressed.
     * Valid values for defaultch are 'y','Y','n','N'
     * @return boolean true is user enter 'y'/'Y', false otherwise
     */
    protected boolean promptForConfirm(String prompt, char defaultch) {
        boolean response = false;
        boolean done     = false;
        char ch          = defaultch;
	
	assert("YynN".indexOf(defaultch) != -1);
        while (!done) {
            try {
                // discard any bytes hanging around on the stream
                System.in.skip (System.in.available());
		String input = Shell.getCurrentShell().prompt (prompt, false);

		if ((null != input) && (input.length() > 1)) {
		    continue;
		} else if (null != input) {
		    ch = input.charAt(0);
		}

                switch (ch) {
                    case 'Y':
                   case 'y':
                      response = true;
                      done = true;
                      break;
                   case 'n':
                   case 'N':
                    case '\n':
                        done = true;
                        break;
                    default:
                        continue;
                }
            }
            catch (IOException ioe) {
                done = true;
                break;
            }
        }

        return response;
    }

       
    public static  String getCSVFromList(List<String> myList) {
       return ClientUtils.getCSVFromList(myList);
    }

    public static  List<String> getListFromCSV(String myCsv) {
       return ClientUtils.getListFromCSV(myCsv);
    }
    
    public void printError(String errorMessage, Exception e) {
        System.out.println(errorMessage);
	Shell.outputException(e);
        
    }

    /**
     * Get the number that coresponds to the NODE-xxx string specifed 
     * by the user.  Values are between 101-116
     * @return int the node number that corresponds to the NODE-xxx string 
     * specified by the user via the -n / --node option.  May be null if 
     * option was not invoked by user or if addNodeOption() was
     * not added by the caller
     */
    public int getNodeId() {
	return nodeId;
    }

    /**
     * Get the NODE-xxx string specifed by the user when -n / --node option
     * was used.
     *
     * @return String the NODE-xxx string specified by the user
     * via the -n / --node option.  May be null if option
     * was not invoked by user.  Requires to use addNodeOption() and 
     * handleStandardOptions()
     */
    public String getNodeName() {
	return sNodeName;
    }
    
    /**
     * @param int the nodeId.
     * @return String the node name to use when outputing
     * the node id in the context of the cli
     */
    public String getNodeName(int nodeId) {
	if (nodeId < 100)
	    nodeId = nodeId + 100;
	return new StringBuffer("NODE-").append(nodeId).toString();
    }
    
    /**
     * Get the number of nodes associated with the current cell.
     *
     * @return int the number of nodes associated with the current cell.  Only valid
     * when -n / --node option invoked by user.  Requires to use addNodeOption() and 
     * handleStandardOptions()
     */
    public int getNodeCount() {
	return nodeCount;
    }
    
    /**
     * @return boolean true if -a / --all option has
     * been specified by the user.  This method is only valid
     * if called after handleStandardOptions.
     */
    public boolean isAllCellsEnabled()
    {
	return isAllCellsEnabled;
    }

    /**
     * @return boolean true if -F/--force option has
     * been specified by the user.  This method is only valid
     * if called after handleStandardOptions.
     */
    public boolean isForceEnabled()
    {
	return isForceEnabled;
    }
    

    /**
     * Standard method for Sun Service Only hidden commands.
     * If force option is not enabled informs user this is a Sun
     * Service only command and asks them whether they wish to procced.
     * Otherwise generates a message indicating this is a Sun Service
     * only command in the output. 
     * @return boolean true okay to continue, false otherwise
     */
    public boolean isHiddenCommandProceed() {
	hasWarningBeenDisplayed = true;
	System.out.println(generateMsgBox(SUN_SERVICE_COMMAND_ONLY_WARNING));
        if (isForceEnabled() == false) {
            return promptForConfirm(PROCEED_PROMPT,'N');
        }
        return true;
    }

    /**
     * Standard method for Sun Service Only hidden option.
     * If force option is not enabled informs user this is a Sun
     * Service only option and asks them whether they wish to procced.
     * Otherwise generates a message indicating this is a Sun Service
     * only command in the output.
     * @return boolean true okay to continue, false otherwise
     */
    public boolean isHiddenOptionProceed() {
	// A command that hidden options may get changed to hidden
	// Don't prompt if the hidden warning has already been issued
	if (hasWarningBeenDisplayed == false) {
	    hasWarningBeenDisplayed = true;
	    System.out.println(generateMsgBox(SUN_SERVICE_OPTION_ONLY_WARNING));
	    if (isForceEnabled() == false) {
		return promptForConfirm(PROCEED_PROMPT,'N');
	    }
	}
        return true;
    }

    
    protected String promptWithDefault (String text, String defaultAnswer, boolean enableEcho) 
        throws IOException {
        String def = "";
        String promptStr = "";
        boolean gotOne = false;
        if (defaultAnswer != null)
            def = defaultAnswer;
        
        if (defaultAnswer.length() > 0) {
            gotOne = true;
        }
        
        if (!enableEcho) {
            Shell.getCurrentShell().enableEcho(false);
            promptStr = text + ": ";
        } else {
            promptStr = text + " [" + def+ "]: ";
        }
        
        String userEntry = Shell.getCurrentShell().prompt (promptStr, false);
        if ((null != userEntry ) && (userEntry.length() > 0)) {
            gotOne = true;
        }

                
        while (!gotOne) {
            System.out.println(text + " is required.");
            userEntry = Shell.getCurrentShell().prompt (promptStr, false);
            if ((null != userEntry ) && (userEntry.length() > 0)) {
                gotOne = true;
            }
        }

        if (!enableEcho) {
            System.out.println("");
            Shell.getCurrentShell().enableEcho(true);
        }
        
        if (userEntry == null)
            return defaultAnswer;
        else
            return userEntry;
    }
   
   /**
    * Generate a message box
    * @param message the message string to generate a box around
    * @return String the resulting message box string
    */
   public static String generateMsgBox(String message)
    {
	assert(message != null);
	message = message.trim();
	int len = message.length();
	StringBuffer aLine = new StringBuffer("****");
	for (int i=0; i < len; i++)
	    aLine.append("*");
	aLine.append("\n");
	StringBuffer result = new StringBuffer(aLine);
	result.append("* ");
	result.append(message);
	result.append(" *\n");
	result.append(aLine);
	return result.toString();
    }
   
   
    /**
     * This routine should be executed when a command that reboots the
     * master or causes a master failover is executed.  Since the cli
     * currently doesn't keep the user from logging back in this routine
     * waits 2 minutes for the master to failover.  Ideally during this
     * time our process will get killed.  After 2 minutes force a logout
     * off the user.
     */
    public void doMasterLogoutWait()
    throws MgmtException, ConnectException, PermissionException{
	
	if(cellId == getApi().getMasterCellId()) { 
	    Shell.getCurrentShell().disableAutoLogout(); 
	    // Rebooting or failing over the master takes a little bit since
	    // the cli currently won't keep the user from logging back in wait
	    // two minute before exiting.  Ideally we want our process to
	    // get killed.  This will prevent the user from immediately logging
	    // back in
	    try {
		Thread.sleep(CliConstants.ONE_MINUTE * 2);   // 2 minutes
	    } catch (java.lang.InterruptedException ignore) {}
	    System.out.println("Forcing logout.");                             
	    System.exit(0);
	}
    }
}
