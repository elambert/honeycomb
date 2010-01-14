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

import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.config.CliConfigProperties;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.adm.client.MultiCellOp;
import com.sun.honeycomb.adm.client.MultiCellRunner;
import com.sun.honeycomb.adm.client.MultiCellServiceTagUpdater;
import com.sun.honeycomb.adm.client.MultiCellUtils;
import com.sun.honeycomb.adm.client.ServiceTagsUpdater;
import com.sun.honeycomb.adm.client.ServiceTagsUtils;
import com.sun.honeycomb.adm.client.SiloInfo;
import com.sun.honeycomb.adm.common.Validate;
import com.sun.honeycomb.admin.mgmt.client.Fetcher;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCServiceTagCellData;
import com.sun.honeycomb.admin.mgmt.client.HCServiceTags;
import com.sun.honeycomb.common.AdminResourcesConstants;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.mgmt.common.MgmtException;

        
import com.sun.honeycomb.util.ExtLevel;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * This class represents the servicetag command and the actions that
 * the command invokes.
 * <P>
 * Service Tags is a new strategy within Sun that exists to provide the 
 * customer with an easy way to identify their products/equipment to Sun.   
 * To register equipment with Sun, the customer logs into their My Sun 
 * account and downloads and runs the service tag registration client.  
 * The client uses the service tag discovery mechanism to discover service 
 * tag enabled products/equipment. 
 * <P>
 * The information needed to generate the service tag information is
 * not available on the 5800.  This tool provides the means for manufacturing
 * and customer service to enter the service tag information and generate
 * the required service tag data entries to make support the service tag
 * registration client.
 */
public class CommandServiceTags extends ShellCommand {

    private final Option _optMarketingNum;
    private final Option _optProductNum;
    private final Option _optSerialNum;
    private final Option _optEnable;
    private final Option _optDisable;
    private final Option _optRefresh;
    private final Option _optValidate;
    private final Option _optList;
    private String marketingNum = null;
    private String productNum = null;
    private String serialNum = null;
    private HashMap serialNumMap = null;
    private HashMap knownPartNumbers = null;

    /** Creates a new instance of CommandServiceTag */
    public CommandServiceTags(String name, String[] aliases, Boolean isHidden) {
        super(name, aliases, isHidden);
        addCellIdOption(false);
        addForceOption();
        _optMarketingNum = addOption(OPTION_STRING, 'M', "marketing_num");
        _optProductNum = addOption(OPTION_STRING, 'P', "product_num");
        _optSerialNum = addOption(OPTION_STRING, 'S', "product_serial_num");
        _optEnable = addOption(OPTION_BOOLEAN, 'E', "enable");
        _optDisable = addOption(OPTION_BOOLEAN, 'D', "disable");
        _optRefresh = addOption(OPTION_BOOLEAN, 'r', "refresh");
        _optValidate = addOption(OPTION_BOOLEAN, 'V', "validate");
        _optList = addOption(OPTION_BOOLEAN, 'l', "list");
    }

    /**
     * @param env the Properties
     * @param argv the command line arguments to process
     * @throws MgmtException
     * @throws PermissionException
     * @throws ConnectException
     */
    public int main(Properties env, String[] argv)
            throws MgmtException, PermissionException, ConnectException {

        boolean doForce = false;
        boolean noOptionsSpecified = true;

        AdminClient api = getApi();
        
        int retCode = handleStandardOptions(argv, true);
        if (retCode != ExitCodes.EX_CONTINUE) {
            return retCode;
        }

        String[] unparsedArgs = getRemainingArgs();
        if (unparsedArgs.length > 0) {
            System.out.println("Unknown argument: " + unparsedArgs[0]);
            usage();
            return ExitCodes.EX_USAGE;
        }
        
        HCServiceTags serviceTags = null;
        if (getOptionValueBoolean(_optDisable)) {
            noOptionsSpecified = false;
            if (serviceTags == null)
                serviceTags = ServiceTagsUtils.getServiceTags(api);
            retCode = disableService(api, serviceTags);
            if (retCode != ExitCodes.EX_OK) {
                return retCode;
            } 
        }
        if (getOptionValueBoolean(_optEnable)) {
            noOptionsSpecified = false;
            if (serviceTags == null)
                serviceTags = ServiceTagsUtils.getServiceTags(getApi());
            return enableService(api, serviceTags);
        }
        
        String newMarketingNum = getOptionValueString(_optMarketingNum);
        String newProductNum = getOptionValueString(_optProductNum);
        String newSerialNum = getOptionValueString(_optSerialNum);
        if (newMarketingNum != null 
                || newProductNum != null 
                || newSerialNum != null) {
            noOptionsSpecified = false;
            retCode = processServiceTagCellValues(
                    newMarketingNum, newProductNum, newSerialNum);
            if (retCode != ExitCodes.EX_CONTINUE) {
                return retCode;
            }
        }

        serialNumMap = null;        // Set during the validation process
        boolean validate = getOptionValueBoolean(_optValidate);
        boolean refresh = getOptionValueBoolean(_optRefresh);
        if (refresh || validate) {
            noOptionsSpecified = false;
            // Validate the silo_xml settings on the master cell
            serviceTags = ServiceTagsUtils.getServiceTags(getApi());
            retCode = validateServiceTagInfo(serviceTags);
            if (retCode != ExitCodes.EX_CONTINUE) {
                if (refresh) {
                    System.out.println("Refresh operation could not be performed."
                            + " Configuration is not valid.");
                }
                return retCode;
            }
            System.out.println("Successfully validated configuration.  No problems found.");
            if (refresh) {
		if (api.loggedIn() == false)
              	    throw new PermissionException();
                String[] params = { Long.toString(api.getLoggedInSessionId()) };
		api.extLog(ExtLevel.EXT_INFO,
                    AdminResourcesConstants.MSG_KEY_SERVICE_TAG_REFRESH_REGISTRY,
                    params, "refreshServiceTagRegistry");

                ServiceTagsUpdater.resetInstanceURNs(serialNumMap);
                retCode = ServiceTagsUpdater.updateAllServiceTagData(
                        getApi(), serviceTags, true);

                if (retCode == ExitCodes.EX_OK) {
                    System.out.println("Successfully updated service tag registry.");
                    serviceTags.setRegistryStatus(
                        BigInteger.valueOf(
                            CliConstants.SERVICE_TAG_REGISTRY_ENTRIES_FOUND));
                } else {
                    return retCode;
                }
            }
        }

        if (noOptionsSpecified || getOptionValueBoolean(_optList)) {
            // Don't refetch it if we already have it
            if (serviceTags == null)
                serviceTags = ServiceTagsUtils.getServiceTags(getApi());
            displayAll(serviceTags);
        }
        return ExitCodes.EX_OK;
    }
    
    /**
     * Usage help method.  Overides default to include part # output in help
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public void usage() 
    throws MgmtException {
        super.usage();
        outputPartNumbers();
    }
    
    /**
     * Enable the service tags service.  
     * @param api handle to the admin client obj
     * @param service handle to the object on which the disable
     * call can be made
     * @return int the return code.  Caller should exit if any value
     * other than 0 is returned.
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     * @throws com.sun.honeycomb.adm.cli.PermissionException
     */
    public int enableService(AdminClient api, HCServiceTags service)
            throws MgmtException, ConnectException, PermissionException {

        if (api.loggedIn() == false)
            throw new PermissionException();
     
        if (service.getStatus().equals(BigInteger.ONE)) {
            System.out.println("Service tag service is already enabled.");
            return ExitCodes.EX_TEMPFAIL;
        }
        String[] params = { Long.toString(api.getLoggedInSessionId()) };
        api.extLog(ExtLevel.EXT_INFO,
            AdminResourcesConstants.MSG_KEY_SERVICE_TAG_ENABLE_REGISTRY,
            params, "enableService");
                
        // If the service says it's disabled always allow it to be enabled.
        int retCode = service.enable(BigInteger.ZERO).intValue();
        if (retCode == ExitCodes.EX_OK) {
            System.out.println("Successfully enabled service tag service.");
            System.out.println(
                "A refresh of the service tags is now need, execute the cli command\n"
                + "'servicetags --refresh' to refresh the service tag registry.");
        } else {
            System.out.println("Failed to enabled service tag service.");
        }
        return retCode;
    }
    
    /**
     * Disable the service tag service running on remote node
     * @param api handle to the admin client obj
     * @param service handle to the object on which the disable
     * call can be made
     * @return int the return code.  Caller should exit if any value
     * other than 0 is returned.
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     * @throws com.sun.honeycomb.adm.cli.PermissionException
     */
    public int disableService(AdminClient api, HCServiceTags service)
            throws MgmtException, ConnectException, PermissionException {

        if (api.loggedIn() == false)
            throw new PermissionException();
 
        if (service.getStatus().equals(BigInteger.ZERO)) {
            System.out.println("Service tag service is already disabled.");
            return ExitCodes.EX_TEMPFAIL;
        }
        if (service.getData().size() > 1) {
            // This is not a single cell system.
            System.out.println("This is a multi-cell hive. Disable operation "
                + "is only allowed on single cell systems.");
            return ExitCodes.EX_TEMPFAIL;
        }
        
        String[] params = { Long.toString(api.getLoggedInSessionId()) };
        api.extLog(ExtLevel.EXT_INFO,
            AdminResourcesConstants.MSG_KEY_SERVICE_TAG_ENABLE_REGISTRY,
            params, "enableService");
        
        int retCode = service.disable(BigInteger.ZERO).intValue();
        if (retCode == ExitCodes.EX_OK)
            System.out.println("Successfully disabled service tag service.");
        else {
            if (retCode == CliConstants.SERVICE_TAG_REGISTRY_DELETE_FAILURE) {
                System.out.println("Successfully disabled service tag service.");
                System.out.println("Failed to delete service tag registration file.");
            } else {
                System.out.println("Failed to disabled service tag service.");
            }
            return ExitCodes.EX_TEMPFAIL;
        }
        return retCode;
    }

    private static final String NO_CELL_ID_ERR_MSG =
            "This is a multi-cell hive, the cell ID must be specified.\n"
	    + "The option --cellid is required on a multi-cell hive when using the options,\n"
	    + "-M/--marketing_num, -P/--product_num, "
            + "and -S/--product_serial_num.";

    
    public int processServiceTagCellValues(
            String newMarketingNum,
            String newProductNum,
            String newSerialNum)
            throws MgmtException, ConnectException, PermissionException {
        
        // Make sure the -cellid option was used or we are running on
        // a single cell cluster
        int retCode = handleOptionalCellId(NO_CELL_ID_ERR_MSG);
        if (retCode != ExitCodes.EX_CONTINUE)
            return retCode;
        
        // Retrieve the Service Tag Data for the cell we are processing
        HCServiceTagCellData entry = getServiceTagCellData();
        boolean updateNeeded = false;

        // We don't know the order of the args due to the design of the
        // cli so we just check values based on an arbitrary order we choose
        if (newMarketingNum != null) {
            if (Validate.isValidMarketingNumber(newMarketingNum) == false) {
                System.out.println("Invalid marketing number of '" + newMarketingNum 
                        + "' specified.   Value must be in the\n" 
                        + "form XX-XXXX-XXXXXXX where X has a value of 0-9 or A-Z.\n");
                outputPartNumbers();
                return ExitCodes.EX_TEMPFAIL;
            }
            if (newMarketingNum.equals(entry.getMarketingNumber())) {
                System.out.println(
                        "Marketing number specified matches existing, ignoring new setting.");
            } else {
                updateNeeded = true;
                entry.setMarketingNumber(newMarketingNum);
            }
        }
        if (newProductNum != null) {
            if (Validate.isValidTopLevelAssemblyPartNumber(newProductNum) == false) {
                System.out.println("Invalid product number of '" + newProductNum 
                        + "' specified.   Value must be in the\n" 
                        + "form 594-XXXX-XX where X has a value of 0-9.\n");
                outputPartNumbers();
                return ExitCodes.EX_TEMPFAIL;
            }
            if (newProductNum.equals(entry.getProductNumber())) {
                System.out.println(
                        "Product number specified matches existing, ignoring new setting.");
            } else {
                updateNeeded = true;
                entry.setProductNumber(newProductNum);
            }
        }
        if (newSerialNum != null) {
            if (Validate.isValidTopLevelAssemblySerialNumber(newSerialNum) == false) {
                System.out.println("Invalid product serial number of '" + newSerialNum 
                    + "' specified.  Values must\nbe in the form YYWWMMUUUU where:\n\n" 
                    + "\tYY = Year of manufacture\n" 
                    + "\tWW = Week in year where WW is any value 01-52\n" 
                    + "\tMM = Manufactring plant code where M may be any value A-Z\n" 
                    + "\tUUUU = Unique manufacturing # where U may be any value 0-9");
                return ExitCodes.EX_TEMPFAIL;
            }
            String serialNum = entry.getProductSerialNumber();
            if (newSerialNum.equals(serialNum)) {
                System.out.println(
                    "Serial number specified matches existing, ignoring new setting.");
            } else {
                if (isEmpty(serialNum) == false) {
                    // We've got a serial number change.  Warn the user that there
                    // about to change the serial number associated with the cell.
                    System.out.println(
                        "CAUTION: The product serial number associated with CELL " 
                        + getCellId() + " is being changed\n" 
                        + "from '" + serialNum + "' to '" 
                        + newSerialNum + "'.");
		    if (isForceEnabled() == false) {
                        if (!promptForConfirm("Continue? [y/N]: ", 'N')) {
                            return ExitCodes.EX_USERABORT;
                        }
                    }
                }
                updateNeeded = true;
                entry.setProductSerialNumber(newSerialNum);
            }
        }
        StringBuffer buf = new StringBuffer();
        if (isEmpty(entry.getMarketingNumber())) {
            buf.append("Marketing number is not set.\n");
        }
        if (isEmpty(entry.getProductNumber())) {
            buf.append("Product number is not set.\n");
        }
        if (isEmpty(entry.getProductSerialNumber())) {
            buf.append("Product serial number is not set.\n");
        }
        if (buf.length() != 0) {
            buf.append("This command requires that the product number, ");
            buf.append("product serial number, and\nmarketing number be ");
            buf.append("set in a single command sequence if not previously set.\n");
            buf.append("For example:\n");
            buf.append("\tservicetags -M XT-5832-20AMCBZ -P 594-4516-02 -S 0801BB0001");
            System.out.println(buf.toString());
            return ExitCodes.EX_TEMPFAIL;
        }
        // All the validation of the values has occurred. 
        // Initiate an update action which will cause the silo_info.xml
        // service tag entry for this cell to be updated
        if (updateNeeded == false) {
            System.out.println("No changes made to service tag data for cell.");
            return ExitCodes.EX_CONTINUE;
        }
        retCode = validateAgainstKnowNumbers(entry, isEmpty(newProductNum) == false);
        if (retCode!= ExitCodes.EX_CONTINUE)
            return retCode;
        updateServiceTagData(entry);
        return ExitCodes.EX_CONTINUE;
    }
    
    private static final String FORMAT_PART_NUM_TABLE_HEADER = 
            "  %1$-11s  %2$-15s  %3$s\n";
    private static final String FORMAT_PART_NUM_TABLE_DATA = 
            "  %1$-11s  %2$-15s  %3$s\n";
    
    /**
     * Output a table with all know product numbers and there associated values
     */
    private void outputPartNumbers() {
        if (knownPartNumbers == null) {
            knownPartNumbers = buildPartNumberList();
        }
        Set keySet = knownPartNumbers.keySet();
	if (keySet.size() == 0)
	    return;
        String[] keys = (String[])keySet.toArray(new String[keySet.size()]);
        System.out.println("The list of known product/marketing numbers follows:\n");
        System.out.format(FORMAT_PART_NUM_TABLE_HEADER, 
                "Product #", "Marketing #", "Description");
        System.out.format(FORMAT_PART_NUM_TABLE_HEADER, 
                "-----------", "---------------","-------------------------------");
        for (int i=0; i < keys.length; i++) {
            PartNumberInfo part = 
                (PartNumberInfo)knownPartNumbers.get(keys[i]);
            System.out.format(FORMAT_PART_NUM_TABLE_DATA,
                emptyValueCheck(part.getProductNumber()), 
                emptyValueCheck(part.getMarketingNumber()),
                emptyValueCheck(part.getDescription()));
        }
    }
    
    /**
     * Valid the data entered against the list of known product numbers
     * We don't flag these as errors.  We only generate CAUTION warnings
     * @param entry the entry to validate
     * @param hasProductNumberChanged boolean flag that indicates whether
     * the user entered a new product number or not.
     */
    public int validateAgainstKnowNumbers(HCServiceTagCellData entry, 
            boolean hasProductNumberChanged) {
        if (knownPartNumbers == null) {
            knownPartNumbers = buildPartNumberList();
        }
        if (knownPartNumbers == null || knownPartNumbers.isEmpty()) {
            System.out.println(
                "Unable to validate product number, can't load list of valid product numbers.");
            return ExitCodes.EX_CONTINUE;
        }
        
        PartNumberInfo part = 
                (PartNumberInfo)knownPartNumbers.get(entry.getProductNumber());
        if (part == null) {
            System.out.print(
                "CAUTION: Unknown product number of '" 
                + entry.getProductNumber());
            if (hasProductNumberChanged == false) {
                 System.out.println("' configured for cell.");
            } else {
                System.out.println("' specified for cell.");
            }
            System.out.println(
                "Ensure that the product number specified is valid and in Sun's Manufacturing");
            System.out.println(
                "database. If the product number is not valid, when the customer attempts to");
            System.out.println("register this system the registration will fail.\n");
            
            outputPartNumbers();
	    System.out.println("\nThe settings you have specified for the cell are:");
            System.out.println();
            outputTable(entry);
            System.out.println();
            if (hasProductNumberChanged) {
                if (isForceEnabled() == false) {
                    if (promptForConfirm(
                            "Continue using specified product number? [y/N]:",'N') == false)
                        return ExitCodes.EX_TEMPFAIL;
                }
            } else {
                System.out.println("No further verficiation of settings is possible.");
                if (isForceEnabled() == false) {
                    if (promptForConfirm(
                            "Continue? [y/N]:",'N') == false)
                        return ExitCodes.EX_TEMPFAIL;
                }
            }
            System.out.println();
            return ExitCodes.EX_CONTINUE;
        }
        if (isEmpty(part.getMarketingNumber()) == false
                && entry.getMarketingNumber().equals(part.getMarketingNumber()) == false) {
            System.out.println("CAUTION: Expected a marketing number of '"
                + part.getMarketingNumber() + "' for the product number");
            System.out.println("'" + part.getProductNumber() 
                + "'.  Is the marketing number of '" + entry.getMarketingNumber() 
                + "' correct?");
            System.out.println();
            outputPartNumbers();
            System.out.println("\nThe settings you have specified for the cell are:");
            System.out.println();
            outputTable(entry);
	    System.out.println();
            if (isForceEnabled() == false) {
                if (promptForConfirm(
			"Continue using specified marketing number? [y/N]:",'N') == false)
                    return ExitCodes.EX_TEMPFAIL;
            }
            System.out.println();
        }
        return ExitCodes.EX_CONTINUE;
    }
    
    /**
     * Create a map of all the known part numbers and marketing numbers
     * We'll qualify the data entered by the user from this data
     * @return HashMap of part numbers
     */
    public HashMap<String,PartNumberInfo> buildPartNumberList() {
	HashMap map = new HashMap();
        String knownPartNumbers = CliConfigProperties.getInstance()
                .getProperty("servicetags.knownPartNumbers");
	if (knownPartNumbers == null || knownPartNumbers.length() == 0)
	    return map;
        String[] partNumbers = knownPartNumbers.split(",");
        ResourceBundle bundle = ResourceBundle.getBundle(
                AdminResourcesConstants.RESOURCE_BUNDLE_NAME, Locale.ENGLISH);
        for (int i=0; i < partNumbers.length; i++) {
            String partDesc = "";
            String marketingNumber = "";
            try {
                partDesc = bundle.getString(
                        AdminResourcesConstants.
                            PREFIX_KEY_SERVICE_TAG_PART_NUM_TO_PRODUCT_NAME 
                        + partNumbers[i]);
             }
            catch (Exception e) {
                e.printStackTrace();
                System.out.println("Lookup of product description for '" + partNumbers[i]
                        + "' failed.");
            }
            try {
                marketingNumber = bundle.getString(
                        AdminResourcesConstants.
                            PREFIX_KEY_SERVICE_TAG_PART_NUM_TO_MARKETING_NUMBER
                        + partNumbers[i]);
            }
            catch (Exception e) {
                e.printStackTrace();
                System.out.println("Lookup of marketing numbers for '" + partNumbers[i]
                        + "' failed.");
            }
            PartNumberInfo known = 
                    new PartNumberInfo(partNumbers[i], partDesc, marketingNumber);
            map.put(partNumbers[i], known);
        }
        return map;
    }
    
    
    /**
     * Validate the service tag data
     * @param tagData handle to the service tag data object
     * @return int the return code
     */
    private int validateServiceTagInfo(HCServiceTags tagData) {
        List<HCServiceTagCellData> list = tagData.getData();
        HCServiceTagCellData[] entries = 
            (HCServiceTagCellData[])list.toArray(
                new HCServiceTagCellData[list.size()]);
        
        // #1 - Verify that there are no null fields in any of the
        // service tag info fields.
        int retCode = checkForNullFields(entries);
        if (retCode != ExitCodes.EX_CONTINUE)
            return retCode;
        // #2 - Check to ensure that the service tag for all fields
        // that share the same serial # are the same
        return validateGroupings(entries);
    }
    
    /**
     * Check the service tag data associated with each cell to
     * make sure it's not null.   If a null field is found.  Flag
     * it and terminate the validation process.
     * @param entries the entries to check for null fields
     * @return int status of the check.  The callee should exit if
     * any value other than ExitCodes.EX_CONTINUE is returned.
     */
    private int checkForNullFields(HCServiceTagCellData[] entries) {
        System.out.println("Verifying all fields are set...");
        List<HCServiceTagCellData> list = new ArrayList<HCServiceTagCellData>();
        for (int i=0; i < entries.length; i++) {
            if (isEmpty(entries[i].getMarketingNumber()) 
                    || isEmpty(entries[i].getProductNumber())
                    || isEmpty(entries[i].getProductSerialNumber())) {
                list.add(entries[i]);
            }
        }
        if (list.size() > 0) {
            // The service tag data associated with one or more cells
            // is not valid.  One or more of the fields in the service
            // tag record is not configured.
            System.out.println(
                    "\nUndefined values detected for the following cells:\n");
            outputTable(list);
            System.out.println();
            return ExitCodes.EX_DATAERR;
        }
        return ExitCodes.EX_CONTINUE;
    }
    
    private boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
    
    
    /**
     * Perform a validation of all the entries in the list.  All the data fields
     * in the list need to match.    
     * 
     * @param list the serial number list to validate.  All entries in the list
     * already share the same serial #s
     * @return int the return code
     */
    private int validateSerialNumberGrouping(List<HCServiceTagCellData> list) {
        if (list.size() == 1) {
            // The list passed in is based on serial numbers.  All the
            // items in the list will have the same serial number.
            // If there is only 1 item in the list then no validation
            // is required.
            return ExitCodes.EX_CONTINUE;
        }
        boolean productNumberMismatch = false;
        boolean marketingNumberMismatch = false;
        HCServiceTagCellData[] entries = ServiceTagsUtils.toArray(list);
        
        // Each list has a minimum of at least 1 entry
        HCServiceTagCellData baseEntry = entries[0];
        for (int i=1; i < entries.length; i++) {
            // A grouping is considered valid if the product number and
            // marketing numbers of all fields is the same.
            if (baseEntry.getProductNumber().equals(
                    entries[i].getProductNumber()) == false) {
                productNumberMismatch = true;
            }
            if (baseEntry.getMarketingNumber().equals(
                    entries[i].getMarketingNumber()) == false) {
                marketingNumberMismatch = true;
            }
        }
        if (marketingNumberMismatch || productNumberMismatch) {
            
            if (marketingNumberMismatch && productNumberMismatch) {
                System.out.println(
                        "Marketing number and product number mismatch detected.");
            } else {
                if (marketingNumberMismatch) {
                    System.out.println("Marketing number mismatch detected.");
                } else {
                    System.out.println("Product number mismatch detected.");
                }
            }
            System.out.println();
            outputTable(list);
            System.out.println();
            System.out.println(
                "All product numbers and marketing numbers must agree for all\n"
                + "cells that share the product serial number, '"
                + baseEntry.getProductSerialNumber() + "'\n");
            return ExitCodes.EX_DATAERR;
        }
        
        if (entries.length > 2) {
            // Should we try this as a caution instead of an error to allow
            // for a hardware change in the future?
            System.out.println("More than two cells detected with the same"
                + "product serial\nnumber, '"
                + baseEntry.getProductSerialNumber() + "'.");
            System.out.println();
            outputTable(list);
            System.out.println();
            return ExitCodes.EX_DATAERR;
        }
        return ExitCodes.EX_CONTINUE;
    }
    
    /**
     * Validate service tag data based on the serial # groupings.  A common
     * product serial number indicates the cells that are located within
     * the same top level assembly component # (rack)
     * @param entries the service tag data that data that needs to be checked
     * @return int the status code.  A value of ExitCodes.EX_CONTINUE indicates
     * the the configuration is valid.  Any other value indicates and error
     * and the callee should terminate and not process any other actions.
     */
    private int validateGroupings(HCServiceTagCellData[] entries) {
        serialNumMap = ServiceTagsUtils.createSerialNumGroupings(entries);
        if (entries.length == 1) {
            // Since the HCServiceTagAdapter always returns 1 entry per cell
            // we've got a single cell system and there are no groupings to
            // verify
            return ExitCodes.EX_CONTINUE;
        }
        System.out.println("Verifying all product serial number groups are correct...");
        Set keySet = serialNumMap.keySet();
        String keys[] = (String[])keySet.toArray(new String[keySet.size()]);
        for (int i=0; i < keys.length; i++) {
            List<HCServiceTagCellData> grouping = 
                    (List<HCServiceTagCellData>)serialNumMap.get(keys[i]);
            int retCode = validateSerialNumberGrouping(grouping);
            if (retCode != ExitCodes.EX_CONTINUE) {
                return retCode;
            }
        }
        // Everything so far passed.  Now determine whether the service
        // tag registry needs to be rebuilt by rechecking the groupings
        // to see if all the instanceURN values are valid
        if (ServiceTagsUtils.validateInstanceURNs(serialNumMap) == false)
            System.out.println("Service tag registry needs to be refreshed.");
        
        return ExitCodes.EX_CONTINUE;
    }
    
    /**
     * Retrieve and display all know service tag data for all the cells in
     * the hive.
     */
    private void displayAll(HCServiceTags service) 
    throws MgmtException, PermissionException, ConnectException {
        // Todo: Add the current state of the service
        String status = getFieldValue(service.getStatus(),
                CliConstants.SERVICE_TAG_SERVICE_STATUS_STR);
        
        System.out.println("Service Tag Service Status: " + status);
                
        status = getFieldValue(service.getRegistryStatus(),
                CliConstants.SERVICE_TAG_REGISTRY_STATUS_STR);
        System.out.println("Service Tag Registration File Status: " + status);
        System.out.println();
        outputTable(service.getData());
    }
    
    private String getFieldValue(BigInteger value, String[] sValues) {
        try {
            return sValues[value.intValue()];
        }
        catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    private static final String FORMAT_TABLE_HEADER = 
            "  %1$-4s  %2$-16s  %3$-11s  %4$-15s\n";
    private static final String FORMAT_TABLE_DATA = 
            "   %1$-3s  %2$-16s  %3$-11s  %4$-15s\n";
    
    
    /**
     * A comparator object used for sorting the data to be outputed.
     * Sort is by product serial number and then by cellid
     */
    public static Comparator SORT_COMPARATOR = new Comparator() {
        public int compare(Object o1, Object o2) {
            HCServiceTagCellData entry1 = (HCServiceTagCellData) o1;
            HCServiceTagCellData entry2 = (HCServiceTagCellData) o2;
            int result = entry1.getProductSerialNumber().compareTo( 
                    entry2.getProductSerialNumber());
            if (result != 0)
                return result;
            return Byte.valueOf(entry1.getCellId()).compareTo(
                    Byte.valueOf(entry2.getCellId()));
        }
        
        public int equals(Object o1, Object o2) {
            HCServiceTagCellData entry1 = (HCServiceTagCellData) o1;
            HCServiceTagCellData entry2 = (HCServiceTagCellData) o2;
            return (entry1.getProductSerialNumber().equals(
                    entry2.getProductSerialNumber())
                    && (entry1.getCellId() == entry2.getCellId())) ? 1 : 0;
        }
    };

    private void outputTable(HCServiceTagCellData entry) {
	List list = new ArrayList();
	list.add(entry);
	outputTable(list);
    }
    
    /**
     * Output the current service tag info associated with each cell
     * for the passed in list.
     * @param list The service tag info to output
     */
    public void outputTable(List<HCServiceTagCellData> list) {
        // Sort the list
        Collections.sort(list, SORT_COMPARATOR);
        HCServiceTagCellData[] entries = 
            (HCServiceTagCellData[])list.toArray(
                new HCServiceTagCellData[list.size()]);
        
        System.out.format(FORMAT_TABLE_HEADER, 
                "Cell", "Product Serial #", "Product #", "Marketing #");
        System.out.format(FORMAT_TABLE_HEADER, 
                "----", "----------------", "-----------", "---------------");
        for (int i=0; i < entries.length; i++) {
            System.out.format(FORMAT_TABLE_DATA,
                entries[i].getCellId(), 
                emptyValueCheck(entries[i].getProductSerialNumber()),
                emptyValueCheck(entries[i].getProductNumber()), 
                emptyValueCheck(entries[i].getMarketingNumber()));
        }
    }
    
    /**
     * Check to ensure that we don't output a empty string or null to
     * the screen.  In this case we want to output "-" instead
     * @param value the value to check for empty string or null
     * @return String the value to display
     */
    private String emptyValueCheck(String value) {
        if (value == null || value.length() == 0)
            return "-";
        return value;
    }
    
    /**
     * Fetch the data for a single cell
     * @return HCServiceTagCellData the service tag data for the cell
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     * @throws com.sun.honeycomb.adm.cli.PermissionException
     */
    private HCServiceTagCellData getServiceTagCellData()
    throws MgmtException, ConnectException, PermissionException {
        HCServiceTagCellData entry =
                Fetcher.fetchHCServiceTagCellData(SiloInfo.getInstance()
                    .getServerUrl(getCellId()));
        return entry;
    }
    
    
    /**
     * Update the service tag data configuration for the cell specified
     * in data.getCellId() for each cell in the cluster.   All the
     * cells in the hive must be updated so that there silo_info.xml
     * configuration information is keep up to date.
     *
     * @param data The service tag data to update
     * @return int the return status code, where 0 is success, any other
     * value represents a failure
     */
    private int updateServiceTagData(HCServiceTagCellData data) 
        throws MgmtException, ConnectException, PermissionException {

        if (getApi().loggedIn() == false) 
            throw new PermissionException();
        
        HCCell[] cells = getApi().getCells(false);
        System.out.print("Updating service tag data for cell " 
                + data.getCellId());
        String[] params = { 
                    Long.toString(getApi().getLoggedInSessionId()), 
                    Byte.toString(data.getCellId())
                };
        getApi().extLog(ExtLevel.EXT_INFO,
                AdminResourcesConstants.MSG_KEY_SERVICE_TAG_UPDATE_CELL,
                params, "updateServiceTagData");
        if (cells.length > 1)
            System.out.print(" on all cells");
        System.out.println(".");
        MultiCellOp [] tagUpdater = 
            MultiCellUtils.allocateMultiCellOp(
                MultiCellServiceTagUpdater.class, cells);
        MultiCellRunner runner = new MultiCellRunner(tagUpdater);
        runner.setCookie(data);
        runner.start();
        int retCode = runner.waitForResult();
        if (retCode == ExitCodes.EX_OK) {
            System.out.println("Successfully updated services tag data for cell.");
            if (cells.length > 1) {
                StringBuffer buf = new StringBuffer();
                System.out.println();
                System.out.println("CAUTION: Service Tag Registry is no longer up to date.  Once all service tag");
                System.out.println("data has been updated you must run 'servicetags --refresh' to cause an");
                System.out.println("update of the Service Tag Registry.");
            }
        } else {
            // Individual failures are done via the event thread.  
            // So don't do anything here
        }
        return retCode;
    }
    
    private class PartNumberInfo {
        
        String productNumber;
        String marketingNumber="";
        String description="";
        
        public PartNumberInfo(
                String productNumber, 
                String description, 
                String marketingNumber) {
            this.productNumber = productNumber;
            this.description = description;
            this.marketingNumber = marketingNumber;
        }
         
        public String getMarketingNumber() {
            return marketingNumber;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getProductNumber() {
            return productNumber;
        }
    }
}
