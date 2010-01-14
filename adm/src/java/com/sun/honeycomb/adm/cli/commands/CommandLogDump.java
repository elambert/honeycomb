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

import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.adm.common.Validate;
import com.sun.honeycomb.admin.mgmt.client.HCSiloProps;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.admin.mgmt.client.HCExpProps;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.util.HashMap;

import java.util.Properties;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is responsible for running the CLI logdump command.  It reads in
 * the user input and validates proxy server, proxy port, geo and phone number.
 * The contact user name and email address isn't checked.  If -z/--set is not 
 * specified, the command will run directly.  For example:
 *       logdump -g AMERICAS -s 100.2.34.5 -p 8080
 * The user is encouraged to run this command interactively by simply running:
 *       logdump -z
 * S/he will be asked to input all of the explorer configuration parameters.
 * This command executes the explorer script, which Sun Service uses to collect
 * system information for trouble-shooting purposes.  For the st5800, explorer 
 * kicks off the extractor and log_scraper tools and sends back log data to Sun.
 *
 */
public class CommandLogDump extends ShellCommand 
implements ExitCodes {
    
    private final Option optSet;
    private final Option optProxyServer;
    private final Option optProxyPort;
    private final Option optGeoLocation;
    private final Option optContactName;
    private final Option optContactPhone;
    private final Option optContactEmail;
    
    private AdminClient api = null;
    
    private static final Logger logger = 
                            Logger.getLogger(CommandLogDump.class.getName()); 
    private static final String EMPTY_STR = "";
    private final String PROP_PROXY = 
                            getLocalString("cli.logdump.proxyServer_name");
    private final String PROP_PORT = 
                            getLocalString("cli.logdump.proxyPort_name");
    private final String PROP_CONTACT = 
                            getLocalString("cli.logdump.contact_name");
    private final String PROP_GEO = getLocalString("cli.logdump.geo_name");
    private final String PROP_PHONE = getLocalString("cli.logdump.number_name");
    private final String PROP_EMAIL = getLocalString("cli.logdump.email_name");
    
    public CommandLogDump(String name, String[] aliases, Boolean isHidden) {
        super(name, aliases, isHidden);
        addForceOption();
        optSet = addOption (OPTION_BOOLEAN,
                getLocalString("cli.logdump.set_char").charAt(0), 
                getLocalString("cli.logdump.set_name"));
        optProxyServer = addOption (OPTION_STRING,
                getLocalString("cli.logdump.proxyServer_char").charAt(0), 
                getLocalString("cli.logdump.proxyServer_name"));
        optProxyPort = addOption (OPTION_INTEGER,
                getLocalString("cli.logdump.proxyPort_char").charAt(0), 
                getLocalString("cli.logdump.proxyPort_name"));
        optGeoLocation = addOption (OPTION_STRING,
                getLocalString("cli.logdump.geo_char").charAt(0), 
                getLocalString("cli.logdump.geo_name"));
        optContactName = addOption (OPTION_STRING,
                getLocalString("cli.logdump.contact_char").charAt(0), 
                getLocalString("cli.logdump.contact_name"));
        optContactPhone = addOption (OPTION_STRING,
                getLocalString("cli.logdump.number_char").charAt(0), 
                getLocalString("cli.logdump.number_name"));
        optContactEmail = addOption (OPTION_STRING,
                getLocalString("cli.logdump.email_char").charAt(0), 
                getLocalString("cli.logdump.email_name"));
     
    }
    public int main (Properties env, String[] argv)
        throws MgmtException, PermissionException, ConnectException {

        HashMap mArgs = null;
        boolean interactive = false;
        int retCode = EX_UNAVAILABLE;
        
        retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}
        String server = getOptionValueString(optProxyServer);
        Integer port = getOptionValueInteger(optProxyPort);
        String geo = getOptionValueString(optGeoLocation);
        String name = getOptionValueString(optContactName);
        String phone = getOptionValueString(optContactPhone);
        String email = getOptionValueString(optContactEmail);
        
	interactive = getOptionValueBoolean(optSet);
        String[] unparsedArgs = getRemainingArgs();
        if (unparsedArgs.length > 0) {
	    System.out.println("Unknown argument: " + unparsedArgs[0]);
	    usage();
	    return EX_USAGE;
	}
        // nothing is specified....only "logdump" so return usage
        if (!interactive && server == null && port == 0 && geo == null && 
                name == null && phone == null && email == null) {
            // warn that this command should be run only at the request of service
            System.out.println(generateMsgBox(ShellCommand.SUN_SERVICE_COMMAND_LOG_REQUEST_ONLY));
            usage();
            return EX_USAGE;            
        }
        // warn that this command should be run only at the request of service
        System.out.println(generateMsgBox(ShellCommand.SUN_SERVICE_COMMAND_LOG_REQUEST_ONLY));
        api = getApi();
	HCExpProps props = api.getExpProps();
	HCExpProps newProps = new HCExpProps();
        
        // gathers config params and sets their values in new HCExpProps object
        if (interactive) {
            try {
                // gather information from user
                configure(props, newProps);
            } catch (IOException io) {
                System.out.println("Logdump aborted.");
                return EX_TEMPFAIL;
            }
        } else {
            // check args passed in directly and populate new HCExpProps obj
            mArgs = buildPropMap(server, port, geo, name, phone, email);
            if (populateProps(mArgs, props, newProps) != EX_OK) {
                System.out.println(getLocalString("cli.logdump.args.err"));
                return EX_TEMPFAIL;
            }
        }
        // regardless of whether params are set interactively or via passed in
        // options, need to check to see if values have changed - if at least
        // one param has changed then update cluster config properties file
        if (isExpUpdateNeeded(props, newProps)) {
            api.setExpProps(newProps);
            System.out.println(getLocalString("cli.logdump.config.updated"));
        } else {
            System.out.println(getLocalString(
                                        "cli.logdump.config.no.update.needed"));
        }
        // ask user if s/he wants to proceed and actually perform logdump
        if (isForceEnabled() == false) {
            if (!promptForConfirm(getLocalString("cli.logdump.proceed"), 'N')) {
                return EX_USERABORT;
            }
        }
        // collect log information
        return logDump();
    }
    /**
     * Collects log information for the hive
     * @return int exit value from dumping the log
     */
    private int logDump() 
        throws MgmtException, PermissionException, ConnectException {
        int result = EX_UNAVAILABLE;
        BufferedWriter out
                    = new BufferedWriter(new OutputStreamWriter(System.out));
        try {
            logger.log(Level.INFO,"Initiating log extraction...");
            out.write(getLocalString("cli.logdump.started"));
            out.newLine();
            out.flush();
            if((result = getApi().dumpLog()) != 0) {
                logger.log(Level.SEVERE,"logDump FAILED: " + result);
                System.out.println(getLocalString("cli.logdump.failed"));
                return EX_IOERR;
            }
        } catch (IOException ioe) {
            logger.log(Level.SEVERE,"IOException during logDump: ", ioe);
            try {
                out.write("IOError while extracting log information");
                out.newLine();
                out.flush();
            } catch (IOException ignore) {}
            
            System.out.println(getLocalString("cli.logdump.failed"));
            return EX_IOERR;
        }
        return EX_OK;
    }
    /**
     * Populates a map with properties entered by the user via the command line
     * @param server proxy server needed to connect to internet
     * @param port proxy server port needed to connect to internet
     * @param geo geographic region where the ST5800 resides
     * @param name contact person's name
     * @param phone contact person's phone
     * @param email contact person's email
     * @return HashMap mapping of arguments entered by user to a key
     *                 describing its type (e.g. ("HTTP Proxy Port", 8080))
     */
    private HashMap buildPropMap(String server, Integer port, String geo, 
                                    String name, String phone, String email) {
        HashMap propMap = new HashMap();
        // build up a values map
        if (server != null) {
            propMap.put(PROP_PROXY, server);
        } 
        if (port != null) {
            propMap.put(PROP_PORT, port);
        }
        if (geo != null) {
            propMap.put(PROP_GEO, geo);
        }
        if (name != null) {
            String fullName = EMPTY_STR;
            String[] names = name.split(",");
            for (int idx = names.length - 1; idx >= 0 ; idx--) {
                fullName = fullName + names[idx].trim() + " ";
            }
            propMap.put(PROP_CONTACT, fullName);
        }
        if (phone != null) {
            propMap.put(PROP_PHONE, phone);
        }
        if (email != null) {
            propMap.put(PROP_EMAIL, email);
        }
        return propMap;
    }
    /**
     * Checks for which configuration parameters were entered by the user via
     * the command line and if present, validates their values and inserts 
     * it into the new HCExpProps object.  For those configuration parameters
     * not entered, the old values are retrieved and kept the same for the new
     * HCExpProps object.
     * @param mArgs Map of the user specified command line parameters
     * @param op HCExpProps object containing the new explorer parameters
     * @param np HCExpProps object containing the current explorer parameters
     * @return int status of inserting property values into the map.
     */
    private int populateProps(HashMap mProps, HCExpProps op, HCExpProps np) {
        if (!mProps.containsKey(PROP_GEO)) {
            // no geographic region specified
            System.out.println("Geographic location required.");
            System.out.println(getLocalString("cli.logdump.geo.selections"));
            return EX_CONFIG;
        } else {
            String geo = (String) mProps.get(PROP_GEO);
            String validGeo = validateGeo(geo);
            if (validGeo == "AMERICAS" || validGeo == "EMEA" || validGeo == "APAC") {
                np.setGeoLocale(validGeo);
            } else {
                // invalid geo region specified on the command line
                System.out.println(validGeo);
                System.out.println(getLocalString("cli.logdump.geo.selections"));
                return EX_CONFIG;
            }   
        }

        String proxyServer = mProps.containsKey(PROP_PROXY) ? 
                       (String) mProps.get(PROP_PROXY) : EMPTY_STR;
        if (proxyServer.length() > 0 && 
                        Validate.isValidIpAddress(proxyServer) == false) {
            if (Validate.isValidHostname(proxyServer) == false) {
                System.out.println("Invalid proxy server specified.");
                return EX_CONFIG;
            }
            if (Validate.isValidHostnameStick(proxyServer) == false) {
                System.out.println("Failed to lookup the specified " + 
                                                        "proxy server.");
                if (!isDnsEnabled()) {
                    System.out.println(getLocalString("cli.logdump.dns.err"));
                }
                return EX_CONFIG;
            }
        }
        np.setProxyServer(proxyServer);
        String proxyPort = mProps.containsKey(PROP_PORT) ? 
                  ((Integer)mProps.get(PROP_PORT)).toString() : "8080";
        if (isValidPort(proxyPort) == false) {
            return EX_CONFIG;
        }
        np.setProxyPort(new BigInteger(proxyPort));
        
        String phone = mProps.containsKey(PROP_PHONE) ? 
                                        (String)mProps.get(PROP_PHONE) : "0";
        if (isValidPhone(phone) == false) {
            return EX_CONFIG;
        }
        np.setContactPhone(new BigInteger(phone));
        String contactName = mProps.containsKey(PROP_CONTACT) ? 
                   (String) mProps.get(PROP_CONTACT) : EMPTY_STR;
        np.setContactName(contactName);
        String contactEmail = mProps.containsKey(PROP_EMAIL) ? 
                   (String) mProps.get(PROP_EMAIL) : EMPTY_STR;
        np.setContactEmail(contactEmail);   
        return EX_OK;
    }
    /**
     * Determine whether we need to update the explorer configuration
     * @param o HCExpProps object containing current values
     * @param n HCExpProps object containing new values
     * @return boolean true if the current explorer config must be saved,
     * false indicates no changes to the settings has occurred and no save
     * is necessary.
     */
    private boolean isExpUpdateNeeded(HCExpProps o, HCExpProps n) {
	return (isExpUpdateNeeded(o.getProxyServer(), n.getProxyServer())
	    || isExpUpdateNeeded(
                o.getProxyPort().toString(), n.getProxyPort().toString()))
	    || isExpUpdateNeeded(o.getContactName(), n.getContactName())
	    || isExpUpdateNeeded(o.getContactEmail(), n.getContactEmail())
	    || isExpUpdateNeeded(
                o.getContactPhone().toString(), n.getContactPhone().toString());
    }
    /**
     * Compares property values
     * @param oldValue current property value
     * @param newValue new property value
     * @return boolean true if the explorer parameter needs updating, otherwise
     *         false.
     */
    private boolean isExpUpdateNeeded(String oldValue, String newValue) {
	if (oldValue == null && newValue != null)
	    return true;
	return (oldValue.equals(newValue) == false);    
    }
    
    /**
     * Perform basic validation of port value entered by user.
     * @param port https proxy port
     * @return true port check passes, false otherwise
     */
    public boolean isValidPort(String port) {
	if (port == null) {
	    System.out.println("No proxy port specified.");
	    return false;
	}
	try {
	    int iValue = Integer.valueOf(port).intValue();
	    if (iValue < 0) {
		System.out.println(
                            "Invalid proxy port specified, must be positive.");
	    }
	} catch (NumberFormatException nfe) {
	    System.out.println("Invalid proxy port specified.");
	    return false;
	}
	return true;
    }
    /**
     * Perform validation of phone number value entered by user.
     * @param num phone number consisting of all digits
     * @return true number check passes, false otherwise
     */
    public boolean isValidPhone(String num) {
	if (num == null) {
	    System.out.println("No phone number specified.");
	    return false;
	}
	try {
            // if phone number doesn't contain all digits, then its not
            // a valid number  (i.e. digit = [0-9] = \d)
            Pattern p = Pattern.compile("\\d+");
            Matcher m = p.matcher(num);
            if (!m.matches()) {
                System.out.println(
                   "Invalid phone number specified, must contain only digits.");
                return false;
            }
	    Long obj = new Long(num);
            long lValue = obj.longValue();
            if (lValue < 0) {
		System.out.println("Invalid phone number specified, " + 
                                            "must consist of positive digits.");
                return false;
	    }
	} catch (NumberFormatException nfe) {
	    System.out.println("Invalid phone number specified.");
	    return false;
	}
	return true;
    }
    /**
     * Perform validation of the geographic location value.
     * @param geo geographic region specified via number selected or actual
     *            value of AMERICAS, EMEA or APAC.
     * @return String valid geographic region values (AMERICAS, EMEA, or APAC)
     *                specified, otherwise the error msg
     */
    public String validateGeo(String geo) {
        String location = null;
	if (geo == null || geo.length() == 0) {
	    return "No geographic location specified.";
        }
        if (geo.equalsIgnoreCase("AMERICAS") || geo.equals("1")) {
            location = "AMERICAS";    
        } else if (geo.equalsIgnoreCase("EMEA") || geo.equals("2")) {
            location = "EMEA";
        } else if (geo.equalsIgnoreCase("APAC") || geo.equals("3")) {
            location = "APAC";
        } else {
            location = "Invalid geographic region specified.";
        }
        return location;
    }
    /**
     * Check and see if DNS is enabled
     * 
     * @return boolean true if DNS is enabled for the hive, false otherwise
     */
    public boolean isDnsEnabled() {
        boolean enabled = false;
        HCSiloProps siloProps = null;
        try {
            siloProps = api.getSiloProps();
            enabled = "y".equals(siloProps.getDns());
        } catch (Exception e) {
            // do not fail if exception occurs for DNS - it will be set to false
            // so let user find out why hostname doesn't resolve - just log info
            logger.log(Level.SEVERE, "Failed to retrieve DNS information", e);
        }
        return enabled;
    }
    /**
     * Configure the params that will be used to run the explorer tool in order
     * to send output back to Sun Service.  This method is called when the 
     * explorer configuration parameters are being set interactively.
     * @param props the current settings
     * @param newProps the new settings
     * @throws IOException, PermissionException, MgmtException, ConnectException
     */
    private void configure(HCExpProps props, HCExpProps newProps)
      throws IOException, PermissionException, MgmtException, ConnectException {
	boolean notDone = true;
        boolean dnsEnabled = isDnsEnabled();
        String validatedGeo = null;
        String currentGeo = props.getGeoLocale();
        System.out.println(getLocalString("cli.logdump.geo.info"));
	do {
            String newGeo = promptWithDefault(
                                    getLocalString("cli.logdump.geoLocale"),
                                                        currentGeo, true);
            validatedGeo = validateGeo(newGeo);
            if (validatedGeo == "AMERICAS" || 
                            validatedGeo == "EMEA" ||
                                    validatedGeo == "APAC") {
                newProps.setGeoLocale(validatedGeo); 
                notDone = false;
            } else {
                // invalid geo region
                System.out.println(validatedGeo);
                System.out.println(getLocalString("cli.logdump.geo.info"));
                continue;
            }            
	} while (notDone);
        boolean proxyNotDone = true;
        do {
            if (promptForConfirm(
                            getLocalString("cli.logdump.use_proxy"), 'N')) {
                // Ask for proxy server input
                String server = promptWithDefault(
                    getLocalString("cli.logdump.proxyServer"),
                    props.getProxyServer(), true);

                if (Validate.isValidIpAddress(server) == false) {
                    if (Validate.isValidHostname(server) == false) {
                        System.out.println("Invalid proxy server specified.");
                        continue;
                    }
                    if (Validate.isValidHostnameStick(server) == false) {
                        System.out.println("Failed to lookup the specified " +
                                "proxy server.");
                        if (!dnsEnabled) {
                            System.out.println(getLocalString(
                                                        "cli.logdump.dns.err"));
                        }
                        continue;
                    }
                }
                newProps.setProxyServer(server);
                String port = promptWithDefault(
                    getLocalString("cli.logdump.proxyPort"), 
                    props.getProxyPort().toString(), true);
                if (isValidPort(port) == false)
                    continue;
                newProps.setProxyPort(new BigInteger(port));
            } else {
                // Set to defaults -- not needed
                newProps.setProxyServer(EMPTY_STR);
                newProps.setProxyPort(BigInteger.valueOf(8080));
            }
            proxyNotDone = false;
        } while (proxyNotDone);

        // Ask for contact info
        String name = promptWithDefault(
                        getLocalString("cli.logdump.contact"),
                                props.getContactName(), true);
        newProps.setContactName(name);
        String email = promptWithDefault(
                        getLocalString("cli.logdump.email"),
                                props.getContactEmail(), true);
        newProps.setContactEmail(email);
        boolean phoneNotDone = true;
        String currentPhone = props.getContactPhone().toString();
        do {
            currentPhone = currentPhone.equals("0") ? EMPTY_STR : currentPhone;
            String phone = promptWithDefault(
                            getLocalString("cli.logdump.number"),
                            currentPhone, true);
            if (isValidPhone(phone) == false) {
                // preserve previously entered values
                props.setContactName(name);
                props.setContactEmail(email);
                continue;
            }  
            newProps.setContactPhone(new BigInteger(phone));
            phoneNotDone = false;
        } while (phoneNotDone);
    }
    /**
     * Override the method that generates the message box in order to 
     * create one to handle multiple line messages. <B> Note a line of text
     * should be no more than 80 characters in length.<\B>  Place the line feed
     * character, "\n", in the appropriate place within the string passed into
     * this method.  This method can handle single lines of text as well.
     * @param message the message string to generate a box around
     * @return String the resulting message box string
     */
    public static String generateMsgBox(String message) {
        assert(message != null);
        String[] msgLines = message.split("\\n");
        String line = null;
        
        // create the line of stars for top and bottom of box
        StringBuffer sbStars = new StringBuffer("****");
        int maxLineLength = 0;
        for (int idx = 0; idx < msgLines.length; idx++) {
            line = msgLines[idx].trim();
            int len = line.length();
            if (len > maxLineLength) {
                maxLineLength = len;
            }
        }
        for (int i=0; i < maxLineLength; i++)
            sbStars.append("*");
        sbStars.append("\n");
        
        // add lines of text
        StringBuffer sbBox = new StringBuffer(sbStars);
        for (int idx = 0; idx < msgLines.length; idx++) {
            StringBuffer sbLine = new StringBuffer("* ");
            line = msgLines[idx].trim();
            sbLine.append(line);
            // computes space required to align the last star on the line
            int amtSpaces = maxLineLength - sbLine.length();
            for (int i = 0; i < amtSpaces + 2; i++) {
                sbLine.append(" ");
            }
            sbLine.append(" *\n");
            sbBox.append(sbLine);         
        }
        // add the line of stars for the bottom of the box
        sbBox.append(sbStars);
        return sbBox.toString();
    }
}
