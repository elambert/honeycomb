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


import com.sun.honeycomb.adm.cli.editline.Editline;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.Shell;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.common.Utils;
import com.sun.honeycomb.adm.common.Validate;
import com.sun.honeycomb.admin.mgmt.client.HCSetupCell;
import com.sun.honeycomb.admin.mgmt.client.HCSiloProps;

import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ConnectException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.EOFException;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

//
// FIXME - we'll want to remove this dependancy
//
import com.sun.honeycomb.time.Time;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
public abstract class ValueSetter extends ShellCommand
    implements ExitCodes {

    protected HashMap<String, String> _pendingChanges;
    
    /**
     * Hashmap of possible NETMASKS with key /xx
     */
    private static HashMap netmaskMap;
    
    
    protected final int MAX_RETRIES = 3;
    
    public static final String ADMIN_IP_KEY = "admin_ip";
    public static final String DATA_IP_KEY = "data_ip";
    public static final String SP_IP_KEY = "service_node_ip";
    public static final String SUBNET_KEY = "subnet";
    public static final String GATEWAY_KEY = "gateway";
    public static final String NTP_SERVER_KEY = "ntp_server";
    public static final String SMTP_SERVER_KEY = "smtp_server";
    public static final String SMTP_SERVER_PORT_KEY = "smtp_port";
    public static final String AUTH_CLIENT_KEY = "authorized_clients";
    public static final String EXT_LOGGER_KEY = "external_logger";
    public static final String DNS_ENABLE_KEY = "dns";
    public static final String DOMAIN_NAME_KEY = "domain_name";
    public static final String DNS_SEARCH_KEY = "dns_search";
    public static final String PRIMARY_DNS_SERVER_KEY = "primary_dns_server";
    public static final String SECONDARY_DNS_SERVER_KEY = "secondary_dns_server";
    
    // The method suffix is registered as an alias for an option.  These
    // prefixes are used by commit
    public static final String ADMIN_IP_METHOD_SUFFIX = null;
    
    
    public ValueSetter (String name, String[] aliases, 
			Boolean isHidden, String[][] options) {
        super (name, aliases, isHidden);
        _pendingChanges = new HashMap<String, String>();
        for(int i = 0; i < options.length; i++) {
            addStringOption(options[i][3].charAt(0),options[i][1]);
        }
    }

    protected boolean areKeyValues(String options[][]) {
        boolean isopt = false;
        if (null == _values)
            return false;
        Iterator iter = _values.keySet().iterator();

        while (iter.hasNext()) {
            String key = (String) (iter.next());

            if (isKeyOption(key,options)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isKeyOption(String option,String options[][]) {
        boolean isopt = false;

        for(int i = 0; i < options.length;i++) {
            if (option.equals(options[i][1])) {
                isopt=true;
            }
        }
        return isopt;
    }

    /**
     * Note - this command uses the HCSiloProps directly,
     * bypassing AdminClientImpl. Login checks should take
     * place at the AdminClientImpl level.
     */
    protected void commitChange(Method method, Object target, 
				String argument) 
        throws MgmtException,ConnectException, PermissionException {
        if (!getApi().loggedIn()) 
            throw new PermissionException();

        Object[] arguments = new String[1];
        arguments[0]=(Object)argument;

        try {
            method.invoke(target,arguments);
        } catch (Exception ile) {
	    Shell.logger.log(Level.SEVERE, "CLI: Internal error", ile);
            throw new MgmtException("Failed to invoke method " +
                                    method.toString());
        }

    }

    protected void commitChanges(Object target, 
                                 String[][] options) 
        throws MgmtException,ConnectException, PermissionException {


        Method [] methods = target.getClass().getMethods();
        for(int i = 0; i < options.length; i++) {
            String localString = options[i][1];


            if (options[i].length == 5 && options[i][4].equals("csv")) {

                for (int j = 0; j < methods.length; j++) {
                    if (methods[j].toString().contains("get"+options[i][2])) {
                        String newVal=(String)_pendingChanges.get(localString);
                        if (null !=newVal) {
                            commitChange(methods[j],target, newVal);
                            _pendingChanges.remove(localString);
                        }
                    }
                }
            } else {
                for (int j = 0; j < methods.length; j++) {
                    if (methods[j].toString().contains("set"+options[i][2])) {
                        String newVal=(String)_pendingChanges.get(localString);
                        if (null !=newVal) {
                            commitChange(methods[j],target, newVal);
                            _pendingChanges.remove(localString);

                        }
                    }
                }
            }
        }

        //
        // FIXME - dynamic switch configuration goes here
        //

    }



    protected String[] getStatus(Object target,String[][] options) 
    throws MgmtException{
        String[] output = new String[options.length];
        if (null == target) {
            throw new MgmtException(
		"Internal error - valuesetter status target is null.");
        }
        Method [] methods = target.getClass().getMethods();
        for(int i = 0; i < options.length; i++) {
            String localString = options[i][1];
            //
            // Iterate over target to find method get+options[2]
            //

            for (int j = 0; j < methods.length; j++) {

                if (methods[j].toString().contains(".get"
		    + options[i][2] + "(")) {
                    try {
                        Object retval=methods[j].invoke(target);
                        if (retval instanceof String) {
                            output[i]=(String)retval;
                        } else if (retval instanceof ArrayList) {
                            output[i] = getCSVFromList((List<String>)retval);
                        }

                    } catch (IllegalAccessException ile) {
                        throw new MgmtException("failed to invoke method " +
                                                methods[j].toString() +
                                                " " + ile);

                    } catch (IllegalArgumentException ila) {
                        throw new MgmtException("failed to invoke method " +
                                                methods[j].toString() +
                                                " " + ila);

                    } catch (InvocationTargetException ite) {
                        Throwable th = ite.getTargetException();
                        String err = "failed to invoke method " +
                            methods[j].toString() +
                            " " + ite;
                        err = err + "Message : " + th.getMessage();
                        throw new MgmtException(err + "Stack trace : "
			    + th.getStackTrace());

                    } catch (ExceptionInInitializerError eie) {
                        throw new MgmtException("failed to invoke method " +
                                                methods[j].toString() +
                                                " " + eie);

                    } catch (Exception exc) {
                        throw new MgmtException("failed to invoke method " +
                                                methods[j].toString() +
                                                " " + exc);

                    }
                }
            }
        }

        return output;
    }

    protected void printNetInfo(Object target, String[][] options)
    throws MgmtException {
        String[] output = getStatus(target, options);
        for (int i = 0; i < options.length; i++) {
	    System.out.println(options[i][0] + " = " + output[i]);
	    if (options[i][1].equals("dns")) {
		if (output[i].equals("n")) {
		    // If DNS is set to no don't output any of the
		    // DNS settings.  Since the DNS settings are
		    // at the end and are not followed by any subsequent
		    // settings we can safetly exit this loop.
		    return;
		}
	    }
        }

    }


    /**
     * Set the values associated with the command <code>target</code>
     * @param target
     * @param options the options to process
     * @return boolean true if the processing of all options was successful,
     * false otherwise.
     */
    protected boolean setValuesInteractively(Object target, 
                                             String[][] options, 
                                             boolean multipleValuesAllowed)
        throws MgmtException {

        final int MAX_DNS_RETRY_PROMPT = 2;
        Editline editline = Editline.create("hcsh");
        String line = null;
	boolean dnsConfigured = true;
        int dnsRetryPrompt = 0;
	int retryPrompts = 0;


        String[] changedOptions = new String[options.length];
        String[] curStatus = getStatus(target, options);

        System.out.println("Enter new value, or hit <enter> to leave the " +
          "value unchanged:");
        if (multipleValuesAllowed) {
            System.out.println("[multiple values need to be comma separated]\n");
        }

	// Loop through all the options.  If dns is not configured
	// then break out of the options as the last options in
	// the list are all DNS.
        for (int i = 0; i < options.length && dnsConfigured; i++) {
	    StringBuffer prompt = new StringBuffer(options[i][0]);

            if (options[i][1].equals(DNS_ENABLE_KEY)) {
		prompt.append(" [y or n]");
	    }
            prompt.append(" [" + curStatus[i] + "]: ");
            try {
                line = editline.readline(prompt.toString(), isInteractive());
            } catch (EOFException e) {
                line = null;
            } catch (IOException e) {
                line = null;
            }

	    // Make sure there is a value (default or user entered)
	    // for all values.  If not don't allow user to move
	    // to the next option.
	    if (line == null
		&& (curStatus[i] == null || curStatus[i].length() == 0)) {
		// Null values are not allowed
		if (retryPrompts == MAX_RETRIES) {
		    System.out.println("No value entered, aborting ...");
		    return false;
		}

		System.out.println("A value must be specified for "
		    + options[i][0] + ".");
		i--;
		retryPrompts++;
		continue;
	    }

            if (options[i][1].equals(DNS_ENABLE_KEY)) {
		if (null == line) {
		    line = curStatus[i];
		}
                if (!line.equals("y") && !line.equals("n")) {
                    System.out.println("Invalid value of " + line
			+ " specified.  Enter y or n.");
                    dnsRetryPrompt++;
                    if (dnsRetryPrompt >= MAX_DNS_RETRY_PROMPT) {
                        System.out.println("Skip to next setting, value is " +
                          "unchanged.");
                        line = curStatus[i];
                    } else {
                        i--;
                        continue;
                    }
                }
		dnsConfigured = line.equals("y");
            }

            if (line != null && line.length() > 0) {
                // normalize all of spaces, because split ("\\s+") doesn't do
                // the right thing...
                line = line.trim();
                line = line.replaceAll("\\s+", " ");

		// Check to make sure that value has been changed.
		// If it hasn't then no update is needed and the
		// value should be set to null.
		if (line.equals(curStatus[i])) {
		    changedOptions[i] = null;
		} else {
		    changedOptions[i] = line;
		}
            } else {
                changedOptions[i] = null;
            }
	    retryPrompts = 0;
        }

	// Clear the hashtable of all pending changes
	_pendingChanges.clear();

        boolean changed = false;
	System.out.println();
	for (int i = 0; i < changedOptions.length; i++) {
            if (changedOptions[i] != null) {
                changed = true;
                System.out.print("Old " + options[i][0] + " [");

                System.out.print(curStatus[i]);

                System.out.println("] "+
                                   "new: " +
                                   changedOptions[i]);

                _pendingChanges.put(options[i][1],
                                    changedOptions[i]);
            }

        }
        return changed;
    }

    /**
     * Generic prompt [y/n]
     * @param prompt the desired prompt.  This will be
     * followed by a [y/n] option choice for the user?
     */
    protected boolean continueOperation(String prompt)
    {
	String line;
        Editline editline = Editline.create("hcsh");
	
	StringBuffer buf = new StringBuffer(prompt);
	buf.append(" [y/n]? ");
	for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                line = editline.readline(buf.toString(), isInteractive());
            } catch (EOFException e) {
                line = null;
            } catch (IOException e) {
                line = null;
            }
	    if (line != null)
		line.toLowerCase();
	    if ("y".equals(line))
		return true;
	    else if ("n".equals(line))
		return false;
	    if (line == null)
		System.out.print("No value specified.");
	    else
		System.out.println("Invalid value of " + line
		    + " was specified.");
	    System.out.println("Enter y or n.");
	}
	System.out.println("Aborting operation.");
	return false;
    }

    /**
     * @return boolean true if DNS is being disabled in the
     * current settings for the command. false otherwise
     */
    private boolean isDnsBeingDisabled() {
	String dns_setting = (String)_pendingChanges.get(DNS_ENABLE_KEY);
	return "n".equals(dns_setting);
    }

    /**
     *
     * @return boolean true if current dns setting is enabled ('y'), false
     * otherwise
     */
    private boolean isDnsEnabled(HCSiloProps siloProps) {
	return "y".equals(siloProps.getDns());
    }

    /**
     * Check to see with DNS is disabled via a setting issued by the
     * user or if the setting has not been changed by the user
     * the current box settings.
     * @return true if DNS box setting is disabled, false otherwise
     */
    private boolean isDnsSettingDisabled(HCSiloProps siloProps) {
	String dns_setting = (String)_pendingChanges.get(DNS_ENABLE_KEY);
	if (dns_setting == null)
	    dns_setting = siloProps.getDns();
	return "n".equals(dns_setting);
    }
    
    
    private static final Pattern IP_LOOSE_FORM_PATTERN = 
            Pattern.compile( "^([0-9]+\\.){2,}+[0-9]+$");
    
    /**
     * Check to see whether this looks like a network style [0-9] dotted address
     * @param ip the string to check
     * @return boolean true if network style dotted address
     */
    public static boolean isNetworkStyleAddress(String ip) {
	Matcher m = IP_LOOSE_FORM_PATTERN.matcher(ip);
	return m.matches();
    }
    
    /**
     * Determine whether the specified hostname is valid.  The <code>strick</code>
     * flag determine the type of validty check that is made.  If hostname is
     * an IP Address the IP Address will be check to ensure it's valid. 
     *
     * @param hostname the hostname to validate
     * @param strict if true will only return true for a hostname only if
     * the hostname can be looked up which implies DNS must be up and
     * running on the system.  If false the hostname will be checked against
     * the hostname pattern to qualify that it appears to be a valid syntax.
     */
    public static boolean validateHostname(String label, String hostname) {
	if (isNetworkStyleAddress(hostname)) {
	    if (Validate.isValidIpAddress(hostname) == false) {
		System.out.println("Invalid " + label + " of '" + hostname + "' specified.");
		return false;
	    }
            return true;
	}
	if (Validate.isValidHostname(hostname) == false) {
            System.out.println("Invalid " + label + " of '" + hostname + "' specified.");
	    return false;
        }
        return true;
    }

    protected boolean validateSwitches(byte cellId)
        throws MgmtException,ConnectException {
        if(!getApi().getSwitchesState(cellId)) {
            System.out.println("ABORTING - one switch is down on cell: " + cellId+
                               " Can't configure until both switches are online.");
            return false;
            
        } else {
            return true;
        }
    }


    /**
     * Validates the authorized clients selection.  If no change has been made
     * to the client selection the current selection is validated against
     * the other proposed changes to ensure it's still valid.
     *
     * @return boolean true if authorized clients selection is valid,
     * false otherwise
     */
    protected boolean verifyAuthorizedClients(HCSiloProps siloProps)
    throws MgmtException, ConnectException {
        String authClients = (String)_pendingChanges.get(AUTH_CLIENT_KEY);
        if (authClients == null) {
	    if (isDnsBeingDisabled() == false)
		return true;
	    authClients = siloProps.getAuthorizedClients();
	}

	if ("all".equals(authClients)) {
	    return true;
	}

	
	StringTokenizer st =
	    new StringTokenizer(authClients, " ,");
	int size = st.countTokens();
	int max_auth_clients = getApi().getMaxNumAuthClients(siloProps);
        if (size > max_auth_clients) {
           System.out.println("Too many authorized clients specified. Maximum number\n"
                              + "of authorized clients allowed is " + max_auth_clients + ".");
           return false;
        }
	
	boolean dnsSettingDisabled = isDnsSettingDisabled(siloProps);
	while (st.hasMoreTokens()) {
	    String client = st.nextToken().trim();
	    if (client.indexOf('/') != -1) {
		// There's a '/' in the element so this must be a netmask
		int errorCode = Validate.validNetmask(client);
		if (errorCode != 0) {
		    outputNetmaskValidationError(errorCode, client);
		    return false;
		}
	    } else if (isNetworkStyleAddress(client)) {
		if (Validate.isValidIpAddress(client) == false) {
		    System.out.println("Invalid authorizied client of '" + client + "' specified.");
		    return false;
		}
		// No further validation required
		continue;
	    } else {
		if (validateHostname("authorized client", client) == false)
                    return false;
		if (dnsSettingDisabled) {
		    // If the current DNS setting for the box is enabled
		    // then attempt to convert the setting for the user
		    // automatically
		    if (isDnsEnabled(siloProps)) {
			String ipAddress = Utils.getIpAddress(client);
			if (ipAddress != null) {
			    // Replace the current hostname for ntpServer
			    // with it's IP address.
			    try {
				authClients = authClients.replaceFirst(client, ipAddress);
				System.out.println(
				    "Converting authorized client " + client
				    + " to " + ipAddress + ".");
				_pendingChanges.put(AUTH_CLIENT_KEY,
				    authClients);
				continue;
			    }
			    catch (PatternSyntaxException ignore) {}
			}
			System.out.println("The authorized client hostname, " + client  
			    + ", must\nbe specified as an IP address before DNS can be disabled.");
			return false;
		    } else {
			System.out.println(
                            "An IP address must be specified for the authorized client hostname\n'"
                            + client + "' if DNS is disabled.");
                        return false;
		    }
		}
	    }
	}
        return true;
    }

    /**
     * Outputs error string for the given <code>errorCode</code> that 
     * was returned when Validate.validNetmask() is called.
     * @param errorCode the err code returned by Validate.validNetmask
     * @param netmask the netmask passed to Validate.validNetmask
     */
    private void outputNetmaskValidationError(int errorCode, String netmask) {
	switch (errorCode) {
	    case 1:
		System.out.println("No mask bit specified for '" + netmask + "'.");
		break;
	    case 2:
		System.out.println("Invalid IP address specified for netmask, " 
		    + netmask + ".");
		break;
	    case 3:
		System.out.println("Invalid mask bit specified for '" 
		    + netmask + "'. Not a number.");
		break;
	    case 4:
		System.out.println("Invalid mask bit specified for '" 
		    + netmask + "'.\nMask bit out of range.  Must be between 1 and 32.");
		break;
	    case 5:
		System.out.println("Invalid IP/mask combination.  Validation is "
		    + "strict to avoid unintended settings.");
		System.out.println("For example, 192.37.54.80/24 will fail "
		    + "validation and must be specified either");
		System.out.println("as 192.37.1.80/28 or changed to be 192.37.1.0/24.");
		break;
	    default:
		break;
	}
    }
    
    
    /**
     * Verify the DNS Search Domain setting
     * @return boolean true if valid, false otherwise
     */
    protected boolean verifyDNSSearchDomains() {
	String str = _pendingChanges.get(DNS_SEARCH_KEY);
	if (str == null)
	    return true;

        StringTokenizer st = new StringTokenizer(str, " ,");
        while (st.hasMoreTokens()) {
	    String domain = st.nextToken().trim();
	    if (Validate.isValidHostname(domain) == false) {
		System.out.println("Invalid DNS search domain of '"
		    + domain + "' specified.");
		return false;
	    }
	}
	return true;
    }
    
    /**
     * Verify the DNS Domain Name setting
     * @return boolean true if valid, false otherwise
     */
    protected boolean verifyDNSDomainName() {
	String str = _pendingChanges.get(DOMAIN_NAME_KEY);
	if (str == null)
	    return true;
	
	if (str != null && Validate.isValidDomainName(str) == false) {
	    System.out.println("Invalid domain name specified.");
	    return false;
	}
	return true;
    }
    
    /**
     * Verify the DNS setting
     * @return boolean true if valid, false otherwise
     */
    protected boolean verifyDns() {
	String dns = (String)_pendingChanges.get(DNS_ENABLE_KEY);
        if (null == dns) {
            return true;
        }

	// The only allowed values are "y" or "n"
	if (dns.equals("y") || dns.equals("n"))
	    return true;
	
	System.out.println("Invalid value for DNS (should be y or n): "+
			   (String)_pendingChanges.get(DNS_ENABLE_KEY));
	return false;
    }
    
    /**
     * Perform some final validation of the DNS settings.
     * <ul>
     * <li>Verify that all values are set if DNS is enabled.</li>
     * <li>Verify the user is not trying to change a DNS setting
     *  while DNS is disabled.</li>
     * </ul>
     * Outputs error message if ether condition is detected.
     * @param siloProps current settings
     * @return boolean true if all values are set, false otherwise
     */
    protected boolean verifyDNSSettings(HCSiloProps siloProps) 
    throws ConnectException, MgmtException {
        String dns = (String)_pendingChanges.get(DNS_ENABLE_KEY);
	if (dns != null && dns.toLowerCase().equals("y")) {
	    // Check to make sure the rest of the DNS values are
	    // set.  This is necessary in case the hivecfg --dns option was
	    // used instead of hivecfg --set
	    if (((_pendingChanges.get(DOMAIN_NAME_KEY) == null)
		    && (siloProps.getDomainName() == null
		    || siloProps.getDomainName().length()==0))
		|| ((_pendingChanges.get(DNS_SEARCH_KEY) == null)
		    && (siloProps.getDnsSearch() == null
		    || siloProps.getDnsSearch().length() == 0))
		|| ((_pendingChanges.get(PRIMARY_DNS_SERVER_KEY) == null)
		    && (siloProps.getPrimaryDnsServer() == null
		    || siloProps.getPrimaryDnsServer().length() == 0))
		|| ((_pendingChanges.get(SECONDARY_DNS_SERVER_KEY) == null)
		    && (siloProps.getSecondaryDnsServer() == null
		    || siloProps.getSecondaryDnsServer().length() == 0))) {
		System.out.println(
		    "All DNS values must be set if DNS is enabled.");
		return false;
	    }
	} else {
	    
	    
	    boolean changes = ((_pendingChanges.get(DOMAIN_NAME_KEY) != null)
		|| (_pendingChanges.get(DNS_SEARCH_KEY) != null)
		|| (_pendingChanges.get(PRIMARY_DNS_SERVER_KEY) != null)
		|| (_pendingChanges.get(SECONDARY_DNS_SERVER_KEY) != null));
	    if (dns != null && dns.equals("n")) {
		// 
		// At this point we've verified all the settings passed by
		// the user via the flags.  However, now we need to
		// check for all the fields that can have hostnames that haven't
		// already been validated.
		if (checkForDNSHostnameConversion(siloProps) == false)
		    return false;
		if (changes) {
		    System.out.println(
			"DNS configuration changes where accepted, but will not be visible while\n"
			+ "DNS is disabled.");
		}
	    } else if (isDnsSettingDisabled(siloProps)) {
		if (changes) {
		    System.out.println("DNS values can not be changed while DNS is disabled.");
		    return false;
		}
	    }
	}
        return true;
    }
    
    private boolean checkForDNSHostnameConversion(HCSiloProps siloProps)
    throws ConnectException, MgmtException {
	// TODO x.x: The hostname DNS conversion code that converts hostnames
	// to ip's when DNS goes to n should probably move out
	// of the validation routines so the UI and this routine can use
	// it.  
	
	// Check all the fields that can have hostnames that we
	// haven't already processed.  If there is a pendingChange
	// value that field has been processed already.
	if (_pendingChanges.get(AUTH_CLIENT_KEY) == null)
	    if (verifyAuthorizedClients(siloProps) == false)
		return false;
	if (_pendingChanges.get(EXT_LOGGER_KEY) == null)
	    if (verifyExternalLogger(siloProps) == false)
		return false;
	if (_pendingChanges.get(NTP_SERVER_KEY) == null)
	    if (verifyNtp(siloProps) == false)
		return false;
	if (_pendingChanges.get(SMTP_SERVER_KEY) == null)
	    if (verifySmtp(siloProps) == false)
		return false;
	return true;
    }

    /**
     * Validates the external logger selection.  If no change has been made to
     * the logger selection the current selection is validated against
     * the other proposed changes to ensure it's still valid.  For example if
     * DNS is being disabled and the extern logger selection is a hostname the
     * verify will fail because the hostname lookup will fail without DNS.
     *
     * @return boolean true if external logger selection is valid,
     * false otherwise
     */
    protected boolean verifyExternalLogger(HCSiloProps siloProps) {

        String externLogger = (String)_pendingChanges.get(EXT_LOGGER_KEY);
        if (externLogger == null) {
	    if (isDnsBeingDisabled() == false)
		return true;
	    externLogger = siloProps.getExtLogger();
	}
	
	boolean dsnSettingDisabled = isDnsSettingDisabled(siloProps);
	if (isNetworkStyleAddress(externLogger)) {
	    if (Validate.isValidIpAddress(externLogger) == false) {
		System.out.println("Invalid external logger of '" + externLogger + "' specified.");
		return false;
	    }
	} else {
	    if (validateHostname("external logger", externLogger) == false)
		return false;
	    if (dsnSettingDisabled) {
		// If the current DNS setting for the box is enabled
		// then attempt to convert the setting for the user
		// automatically
		if (isDnsEnabled(siloProps)) {
		    String ipAddress = Utils.getIpAddress(externLogger);
		    if (ipAddress != null) {
			// Replace the current hostname for ntpServer
			// with it's IP address.
			System.out.println("Converting external logger "
			    + externLogger + " to " + ipAddress + ".");
			_pendingChanges.put("external_logger", ipAddress);
			return true;
		    }
		    System.out.println("The external logger, " + externLogger  
			+ ", must be specified\nas an IP address before DNS can be disabled.");
		    return false;
		} else {
		    System.out.println(
		        "An IP address must be specified for the external logger\n'" 
			+ externLogger + "' if DNS is disabled.");
		    return false;
		}
	    }
	}
	return true;
    }
    
    private boolean performSystemValidation = false;
    
    /**
     * @return boolean true indicates that to perform any cluster level validation
     * with the system as necessary.  false indicates, skip this validation.
     */
    public boolean validateWithSystem() {
	return performSystemValidation;
    }
    
    /**
     * Set the flag to indicate whether cluster level validation with
     * the system is necessary.  NTP is currently the only system level
     * validation that is available.
     * @boolean validate true perform validation, false skip validation
     */
    public void setValidateWithSystem(boolean validate) {
	performSystemValidation = validate;
    }

    /**
     * Validates the ntp server selection.  If no change has been made to
     * the ntp selection the current selection is validated against
     * the other proposed changes to ensure it's still valid.
     *
     * @param siloProps the current setttings
     * @return boolean true if ntp sever selection is valid, false otherwise
     */
    protected boolean verifyNtp(HCSiloProps siloProps)
    throws MgmtException, ConnectException {
	boolean systemValidationNecessary = true;
        String newNtpServers = (String)_pendingChanges.get(NTP_SERVER_KEY);
        if (null == newNtpServers) {
	    if (isDnsBeingDisabled() == false)
		return true;
	    systemValidationNecessary = false;
	    newNtpServers = siloProps.getNtpServer();
	}

	boolean dnsSettingDisabled = isDnsSettingDisabled(siloProps);
	boolean isDnsEnabled = isDnsEnabled(siloProps);
        StringTokenizer st = new StringTokenizer(newNtpServers, " ,");
	HashMap<String, String> unique = new HashMap<String, String>();
        while (st.hasMoreTokens()) {
	    String ntpServer = st.nextToken().trim();
	    if (isNetworkStyleAddress(ntpServer)) {
		if (Validate.isValidIpAddress(ntpServer) == false) {
		    System.out.println("Invalid NTP server of '" + ntpServer + "' specified.");
		    return false;
		}
		// No further validation required
		continue;
	    } else {
		if (validateHostname("NTP server", ntpServer) == false)
                    return false;
		if (dnsSettingDisabled) {
		    // If DNS is enabled on the box then convert the hostname
		    // to an ip address automatically
		    if (isDnsEnabled) {
			String ipAddress = Utils.getIpAddress(ntpServer);
		        if (ipAddress != null) {
			    // Replace the current hostname for ntpServer
			    // with it's IP address.
			    try {
			        newNtpServers = newNtpServers.replaceFirst(ntpServer, ipAddress);
			        System.out.println("Converting NTP server " + ntpServer
				    + " to " + ipAddress + ".");
			        _pendingChanges.put(NTP_SERVER_KEY, newNtpServers);
			        continue;
			    }
			    catch (PatternSyntaxException ignore) {}
		        }
		        System.out.println("The NTP server, " + ntpServer  
			    + ", must be specified\nas an IP address before DNS can be disabled.");
		        return false;
		    } else {
		        System.out.println(
		            "An IP address must be specified for the NTP server '" 
			    + ntpServer + "'\nif DNS is disabled.");
		        return false;
		    }
		}
            }
	}
	if (systemValidationNecessary && validateWithSystem())
	    return validateNTPServerWithSystem();
	return true;
    }
    
    
    /**
     * Validate the NTP Server settings with the system.  The system will
     * ensure that the specified NTP servers are validate
     * @return boolean true if ntp sever passed the system ntp
     * validation check with no errors or a warning, false otherwise
     */
    protected boolean validateNTPServerWithSystem()
    throws MgmtException, ConnectException {
	String newNtpServers = (String)_pendingChanges.get(NTP_SERVER_KEY);
	if (newNtpServers == null)
	    return true;
	
        StringTokenizer st = new StringTokenizer(newNtpServers, " ,");
	HashMap unique = new HashMap();
        while (st.hasMoreTokens()) {
	    String ntpServer = st.nextToken().trim();
	    // Convert all entries to IP Addresses (if possible) and
	    // add to the unique list so that we can check later that
	    // we have 3 unique NTP Servers.  If DNS is enabled on
	    // the system but the system hasn't been rebooted we can't
	    // convert the name to an ip address so it will be possible
	    // for the user to only have 2 unique NTP Servers.
	    //
	    // The check for 3 NTP servers has been removed for 
	    // the 1.1 release
	    //
	    // For example:
	    //
	    // Given the defined NTP Servers and a system where DNS isn't
	    // enabled
	    //
	    // 129.1.1.1 ntpserver1
	    // 129.1.1.2 ntpserver2
	    // 129.1.1.3 ntpserver3
	    //
	    // if the user enters
	    //
	    // hivecfg --ntp_server ntpserver1, 129.1.1.1, 129.1.1.2
	    //
	    // Only 2 unique servers were entered.  But we can't tell this
	    // since we can't lookup the ip address for ntpserver1.
	    //
	    String ipAddress = Utils.getIpAddress(ntpServer);
	    if (ipAddress != null)
		unique.put(ipAddress, ntpServer);
	    else {
		unique.put(ntpServer, ntpServer);
	    }
	}
	
	// If response time becomes an issue it may make sense to only
	// make this call for those ntpServers that where changed.
	String[] servers = 
	    (String[])unique.keySet().toArray(new String[unique.size()]);
	for (int i=0; i < servers.length; i++) {
	    //
	    // If DNS isn't enabled verifyNTPConfig won't succeed.
	    // If a hostname can not be converted to an ipaddress we know
	    // verifyNtpServer call won't succeed.
	    //
	    if (Validate.isValidIpAddress(servers[i]) == false) {
		System.out.println("DNS is not currently running on hive, validation of \nNTP Server '" 
		    + servers[i] + "' is not possible.");
	    }
	    else {
		int retcode = 0;
		String userEnteredName = (String)unique.get(servers[i]);
		try {
		    System.out.print("Validating NTP server " + userEnteredName + "...");
		    retcode = getApi().verifyNtpServers(servers[i]);
		}
		finally {
		    System.out.println();
		}
		if (isNTPServerSystemCheckOk(retcode, userEnteredName) == false)
		    return false;
		    
	    }
	}
        return true;
    }

    /*
     * Output the error string associated with the return code from
     * getApi().verifyNtpServers().  Determine whether this is an
     * error or a warning scenario.  An error code means we want
     * to exit the command and warning means we continue.
     * @param retCode the retcode from verifyNtpServers(String ntpServers)
     * @param server the server the error code refers to
     * @return boolean true no error or warning, continue, false error
     * condition abort any validation
     */
    private boolean isNTPServerSystemCheckOk(int retCode, String server) {

        /**
         * verifyNTPServerConfig on the master node executes
         * ntp commands from the master node to validate the
         * sancity of each ntp server
         */

        switch (retCode) {
          case Time.SERVER_OK:
            return true;

          case Time.SWITCH_NOT_PINGABLE:
            System.out.println("Unable to verify NTP server, ' "
		+ server + "'. \nCannot ping the primary switch.");
	    return false;

          case Time.SWITCH_UPLINK_NOT_CONNECTED:
            System.out.println("Unable to verify NTP server, ' "
		+ server + "'. \nSwitch uplink is not connected.");
	    return true;

          case Time.SERVER_LOCALLY_SYNCED:
            System.out.println("NTP server " + server
		+ " is synced to its own hardware clock.");
	    return true;

          case Time.SERVER_NOT_RUNNING:
            System.out.println("NTP is not running on server " + server + ".");
	    return false;

          case Time.SERVER_NOT_SYNCED:
            System.out.println("NTP server " + server + " has offset greater than 5 "
		+ "seconds\nDo not trust this NTP server.");
	    return false;

          case Time.SERVER_NOT_TRUSTED:
            System.out.println("NTP server " + server
		+ " has offset greater than 5 seconds.");
	    return false;

          case Time.SERVER_NOT_VERIFIED:
            System.out.println("Unable to verify NTP server, ' "
		+ server + "'.");
	    return false;

          default:
            System.out.println("Internal error - unknown retcode: " + retCode);
	    return false;

        }

    }

    /**
     * Validates the smtp server selection.  If no change has been made to
     * the smtp selection the current selection is validated against
     * the other proposed changes to ensure it's still valid.  For example if
     * DNS is being disabled and the smtp server selection is a hostname the
     * verify will fail because the hostname lookup will fail without DNS.
     *
     * @param siloProps the current setttings
     * @return boolean true if smtp sever selection is valid, false otherwise
     */
    protected boolean verifySmtp(HCSiloProps siloProps) {
        //
        // bit of a hack - duplicates hardcoded strings in
        // commandHiveConfig.
        //
        String smtpServer = (String)_pendingChanges.get(SMTP_SERVER_KEY);
        if (smtpServer == null) {
	    if (isDnsBeingDisabled() == false)
		return true;
	}

	if (smtpServer == null) {
	    smtpServer = siloProps.getSmtpServer();
	}
	
	if (isNetworkStyleAddress(smtpServer)) {
	    if (Validate.isValidIpAddress(smtpServer) == false) {
		System.out.println("Invalid SMTP server of '" + smtpServer + "' specified.");
		return false;
	    }
	} else {
	    if (validateHostname("SMTP server", smtpServer) == false) 
                return false;
	    if (isDnsSettingDisabled(siloProps)) {
		// If the current DNS setting for the box is enabled
		// then attempt to convert the setting for the user
		// automatically
		if (isDnsEnabled(siloProps)) {
		    String ipAddress = Utils.getIpAddress(smtpServer);
		    if (ipAddress != null) {
			// Replace the current hostname for SMTP Server
			// with it's IP address.
			System.out.println("Converting SMTP server " + smtpServer
			    + " to " + ipAddress + ".");
			_pendingChanges.put(SMTP_SERVER_KEY, ipAddress);
			return true;
		    }
		    System.out.println("The SMP server, " + smtpServer  
			+ ", must be specified\nas an IP address before DNS can be disabled.");
		    return false;
	    } else {
		    System.out.println(
		        "An IP address must be specified for the SMTP server\n'" + smtpServer 
			+ "' if DNS is disabled.");
		    return false;
		}
	    }
	}
	return true;
    }
   
    /**
     * Perform a simple verification of the SMTP Port to ensure it's a
     * number and the number is a positive value
     * @return boolean true if valid, false otherwise
     */ 
    public boolean verifySmtpPort() {
	String value = (String)_pendingChanges.get(SMTP_SERVER_PORT_KEY);
	if (value != null) {
	    try {
		int iValue = Integer.valueOf(value).intValue();
		if (iValue < 0) {
		    System.out.println("Invalid SMTP Port specified, must be positive.");
		    return false;
		}
	    }
	    catch (NumberFormatException nfe) {
		System.out.println("Invalid SMTP Port specified.");
		return false;
	    }
	}
	return true;
    }
    
    /**
     * @param key the _pendingChanges lookup key to retrieve the IP address
     * that needs checking
     * @param label - a non capitalized label that represents the name of
     * the field the key refers to that will be outputed in the error message
     * if the IP validation check fails.
     */
    private boolean verifyIP(String key, String label) {
	String ip = (String)_pendingChanges.get(key);
        if (ip != null) {
	    if (Validate.isValidIpAddress(ip) == false) {
		System.out.println("Invalid " + label + " of '" + ip + "' specified.");
		return false;
	    }
	}
	return true;
    }
 
    
    /**
     * Verify the value indicated by key
     * @param key the lookup key for the value contained in _pendingChanges
     * the needs to be validated.
     * @return true if verification of value for the value associated with
     * <code>key</code> passed, false otherwise
     */
    protected boolean verifySimpleValue(String key)
    throws ConnectException, MgmtException {
	if (ADMIN_IP_KEY.equals(key)) {
	    return verifyIP(ADMIN_IP_KEY, "admin IP address");
	}
	if (DATA_IP_KEY.equals(key)) {
	    return verifyIP(DATA_IP_KEY, "data IP address");
	}
	if (SP_IP_KEY.equals(key)) {
	    return verifyIP(SP_IP_KEY, "service processor IP address");
	}
 	if (SUBNET_KEY.equals(key)) {
	    return verifyIP(SUBNET_KEY, "subnet address");
	}
	if (GATEWAY_KEY.equals(key)) {
	    return verifyIP(GATEWAY_KEY, "gateway address");
	}
	if (SMTP_SERVER_PORT_KEY.equals(key)) {
	    return verifySmtpPort();
	}
	if (DNS_ENABLE_KEY.equals(key)) {
	    return verifyDns();
	}
	if (DOMAIN_NAME_KEY.equals(key)) {
	    return verifyDNSDomainName();
	}
	if (DNS_SEARCH_KEY.equals(key)) {
	    return verifyDNSSearchDomains();
	}
	if (PRIMARY_DNS_SERVER_KEY.equals(key)) {
	    return verifyIP(PRIMARY_DNS_SERVER_KEY, "primary DNS server");
	}
	if (SECONDARY_DNS_SERVER_KEY.equals(key)) {
	    return verifyIP(SECONDARY_DNS_SERVER_KEY, "secondary DNS server");
	}
	throw new RuntimeException("No verification routine for " + key);
    }
    
    /**
     * Verify the value indicated by key
     * @param key the lookup key for the value contained in _pendingChanges
     * the needs to be validated.
     * @param siloProps the object that describes the current settings of
     * the box.  This setting may not be null if Authorized Client, NTP Server,
     * SMTP Server, or External logger needs to be verified.
     * @return true if verification of value for the value associated with
     * <code>key</code> passed, false otherwise
     */
    protected boolean verifyValue(String key, HCSiloProps siloProps)
    throws ConnectException, MgmtException {
	if (NTP_SERVER_KEY.equals(key)) {
	    return verifyNtp(siloProps);
	}
	if (SMTP_SERVER_KEY.equals(key)) {
	    return verifySmtp(siloProps);
	}
	if (AUTH_CLIENT_KEY.equals(key)) {
	    return verifyAuthorizedClients(siloProps);
	}
	if (EXT_LOGGER_KEY.equals(key)) {
	    return verifyExternalLogger(siloProps);
	}
	return verifySimpleValue(key);
    }
    
    
    
    
    
}
