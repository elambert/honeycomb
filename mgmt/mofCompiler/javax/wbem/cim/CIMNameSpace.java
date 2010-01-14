/*
 *EXHIBIT A - Sun Industry Standards Source License
 *
 *"The contents of this file are subject to the Sun Industry
 *Standards Source License Version 1.2 (the "License");
 *You may not use this file except in compliance with the
 *License. You may obtain a copy of the 
 *License at http://wbemservices.sourceforge.net/license.html
 *
 *Software distributed under the License is distributed on
 *an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either
 *express or implied. See the License for the specific
 *language governing rights and limitations under the License.
 *
 *The Original Code is WBEM Services.
 *
 *The Initial Developer of the Original Code is:
 *Sun Microsystems, Inc.
 *
 *Portions created by: Sun Microsystems, Inc.
 *are Copyright (c) 2001 Sun Microsystems, Inc.
 *
 *All Rights Reserved.
 *
 *Contributor(s): WBEM Solutions, Inc.
 */

package javax.wbem.cim;


import java.io.Serializable;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import javax.wbem.client.ClientProperties;

/**
 * Creates and instantiates a CIM namespace, an object that defines a scope 
 * within which object keys must be unique.  This class is used to refer to
 * an implementation that provides a domain in which class instances are
 * unique. The CIM namespace is a logical grouping of CIM 
 * classes and CIM instances that represent managed objects
 * in a particular environment. 
 *
 *
 * A CIM object name consists of two parts:  namespace + model path.
 * The CIMNameSpace class instantiates the namespace portion of the CIM object
 * name. 
 *
 * For more information regarding the description and rules for the model path 
 * and namespace, refer to the CIM specification at http://www.dmtf.org/
 *            
 *
 * @author	Sun Microsystems, Inc.
 * @since	WBEM 1.0
 */
public class CIMNameSpace implements Serializable {

    final static long serialVersionUID = 200;

    private final static String DEFAULT_HOST = "localhost";
    final static String LOOPBACK_ADDRESS = "127.0.0.1";

    // Can be either a hostName or IP addr
    private String host      = DEFAULT_HOST;
    private String nameSpace = "/root/cimv2";
    private URI    hostURI   = null;

    private static String localIP = null;

    protected static String validateNameSpace(String s) {
    	if ((s == null) || (s.length() == 0)) {
                return "";
        }
        s = s.replace('\\', '/');
        s = s.toLowerCase();

        // remove duplicate '/'s
        int len = s.length();
        StringBuffer sb = new StringBuffer(len);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            sb.append(c);
            if (c == '/') {
                int nextChar = i + 1;
                while((nextChar < len) && (s.charAt(nextChar) == '/')) {
                    i = nextChar;
                    nextChar++;
                }
            }
        }

        // Strip any trailing '/'s
        int lastChar = sb.length() - 1;
        if(lastChar > 0 && sb.charAt(lastChar) == '/') {
            sb.deleteCharAt(lastChar);
        }
	
        return sb.toString();
    }

    /**
     * Constructor creates and instantiates a default CIM namespace
     * name. The default CIM namespace name is "//./root/cimv2"
     *
     */
    public CIMNameSpace() {
	this(null);
    }

    /**
     * Constructor for a CIM namespace, pointing to a specified host 
     * or URI. For example, specifying the string "myhost" creates
     * a CIM namespace with host name "myhost" and the default 
     * namepspace name /root/cimv2 is used. If he path portion of the
     * URI exists it will be used as the namespace.
     *
     * @param uri The string for the host name. <code>h</code> can be a 
     * 		host name, ip address, or a URI of the form:
     *		        "http://myhost:8080/cimom"
     *
     * @exception 	IllegalArgumentException If the String can not 
     *			be used to create a valid URI (e.g. new URI(uri)). 
     *
     */
//?? Reconcile this with parse()
    public CIMNameSpace(String uri) {
        
        if (uri == null) {            
	     try {
                 host = getLocalIPAddress();
	     } catch (Exception e) {		
		host = DEFAULT_HOST;
	     }
        } else {
            try {
                hostURI = new URI(uri);
                if (hostURI.getHost() != null) {
                    this.host = hostURI.getHost();
		} else {
		    this.host = uri;
		}
		try {
		    if (host.equalsIgnoreCase(DEFAULT_HOST)) {
			this.host = getLocalIPAddress();
		    } else {
			this.host = InetAddress.getByName(this.host).
			    getHostAddress();
		    }
		} catch (Exception e) {
		    //do nothing - just use the name...."
		}
		
		String path = hostURI.getPath();
		if ((path != null) && (!path.equals(""))) {
		    nameSpace = validateNameSpace(path);
		}
            } catch (URISyntaxException e) {
                if ((uri.indexOf('/') != -1) || (uri.indexOf('\\') != -1)) {
                    
                    throw new IllegalArgumentException(e.getClass().getName() + ": " + e.getMessage());
                }
                
                this.host = uri;
            }
        }
    }

    /**
     * Constructor creates and instantiates a CIM namespace with the
     * the specified host and CIM namespace name. For example, specifying 
     * host "myhost" and  namespace name "westcoast" creates a CIM 
     * namespace with the name "//myhost/westcoast".
     *
     *
     * @param host		the string representing the host name, ip 
     * 				address, or URL.
     *				
     * @param ns		the string representing the name of this 
     *				CIM namespace.
     *
     */
    public CIMNameSpace(String host, String ns) {
	this(host);
	nameSpace = validateNameSpace(ns);
    }

    /**
     * Gets the name of this CIM namespace
     *
     * @return nameSpace	The string representing the name 
     *				of this CIM namespace
     *
     */
    public String getNameSpace() {
	return nameSpace;
    }
    
    /**
     * Gets the host name of this CIM namespace
     *
     * @return the string representing the host name 
     *         that was used to create the CIMNameSpace
     */
    public String getHost() {
	return host;
    }

    /**
     * Gets the scheme of this CIM namespace
     *
     * @return the string representing the scheme 
     *         that was used to create the CIMNameSpace
     */
    public String getScheme() {
        if (hostURI != null) {
            return hostURI.getScheme();
        } else {
            return null;
        }
    }

    /**
     * Gets the port of this CIM namespace
     *
     * @return the string representing the port 
     *         that was used to create the CIMNameSpace
     */
    public String getPort() {
        if (hostURI != null) {
            return hostURI.getPort() != -1 ? 
                new Integer(hostURI.getPort()).toString() : null;
        }        
        return null;
    }
    
    /**
     * Gets the URI of this CIM namespace
     *
     * @return the URI representing this CIMNameSpace
     */
    public URI getURI() {
        return hostURI;
    }

    
    /**
     * Parses the name of a namespace by separating the host from the
     * namespace and replacing the backward slash (\) with a forward slash (/).
     * For example, if you pass the name of the default namespace,
     * \root\cimv2, this routine replaces this string with /root/cimv2.
     *
     * @param p		the string to parse
     * @deprecated use <code>new CIMNamespace(String)</code> instead
     */
    public void parse(String p) {
        String s = p.replace('\\', '/');
        if (s.startsWith("//")) {
            int index = s.indexOf("/", 3);
            host = s.substring(2, index);
        }

        nameSpace = s;
    }

    /**
     * Sets the name of this CIM namespace to the specified string
     *
     * @param ns		the string representing the name of this 
     *			CIM namespace
     */
    public void setNameSpace(String ns) {
	nameSpace = validateNameSpace(ns);
    }
    
    /**
     * Sets the host name of this CIM namespace to the specified string
     *
     * @param host  the string representing the host name of this 
     *			CIM namespace
     */
    public void setHost(String host) {
		this.host = host;
    }

    /**
     * Returns a String representation of the CIMNameSpace
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return string representation of this namespace
     */
    public String toString() {
        StringBuffer buffer = new StringBuffer(host.length() + nameSpace.length() + 3);

        final boolean haveHost = host != null && host.length() > 0;
        final boolean haveNameSpace = nameSpace != null && nameSpace.length() > 0;

        if(haveHost) {
            if (hostURI != null) {
                String scheme = hostURI.getScheme();
                if (scheme != null) {
                    buffer.append(scheme);
                    buffer.append(":");
                }
            }
        
            buffer.append("//");
            buffer.append(host);
            if (getPort() != null) {
                buffer.append(":" + getPort());
            }
            if (haveNameSpace && nameSpace.charAt(0) != '/') {
                buffer.append('/');
            }
        }

        if(haveNameSpace) {
            buffer.append(nameSpace);
        }

        return buffer.toString();
    }

    // Get the IP address of the local machine. 
    private String getLocalIPAddress() {
	// if we've already found local IP, return it
	if (localIP != null) {
	    return localIP;
	}
        try {
	    // set the preferIPv4 variable is not already set
	    if (System.getProperty("java.net.preferIPv4Stack") == null) {
		String preferIPv4 = 
		    ClientProperties.getProperty("java.net.preferIPv4Stack") 
		    == null ? "true" : 
		    ClientProperties.getProperty("java.net.preferIPv4Stack");
		System.setProperty("java.net.preferIPv4Stack", preferIPv4);
	    }
	    // get all network interfaces
	    Enumeration e = NetworkInterface.getNetworkInterfaces();
	    while (e.hasMoreElements()) {
		NetworkInterface ni = (NetworkInterface)e.nextElement();
		Enumeration e1 = ni.getInetAddresses();
		// get all InetAddresses for each NetworkInterface
		while (e1.hasMoreElements()) {
		    InetAddress inetAddress = (InetAddress)e1.nextElement();
		    String address = inetAddress.getHostAddress();
		    // if the address is not null, empty or the loopback 
		    // address, set static localIP variable and return it
		    if ((address != null) &&
			(address.trim().length() != 0) && 
			(!address.equalsIgnoreCase(LOOPBACK_ADDRESS)) ) {
			localIP = address;
			return address;
		    }	
		}
	    } 
	    
        } catch (Exception e) {
            // ignore, use loopback
        }
	// couldn't find valid local ip address.  Set static local ip address
	// to loopback address and return it.
	localIP = LOOPBACK_ADDRESS;
        return localIP;
    }

}
