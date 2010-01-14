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



/**
    System property names used for setting test locale in runtest script.
*/
package com.sun.honeycomb.hctest.util;

public class HCLocale {

    // sfbay.sun.com
    public static final String PROPERTY_DOMAIN = "hctest.domain";

    public static final String PROPERTY_DBHOST = "hctest.audit_dbhost";

    public static final String PROPERTY_LOG_ARCHIVE_USER = 
						"hctest.log_archive_user";
    public static final String PROPERTY_LOG_ARCHIVE_HOST = 
						"hctest.log_archive_host";
    public static final String PROPERTY_LOG_ARCHIVE_PATH = 
						"hctest.log_archive_path";

    public static final String PROPERTY_DNS1 = "hctest.dns1";
    public static final String PROPERTY_DNS2 = "hctest.dns2";
    // sun.com
    public static final String PROPERTY_DOMAIN_NAME = "hctest.domain_name";
    // sfbay.sun.com
    public static final String PROPERTY_EXTRA_CLIENT_IP = 
						"hctest.extra_client_ip";
    public static final String PROPERTY_CLIENT_SUBNET = "hctest.client_subnet";
    public static final String PROPERTY_APC_SWITCH_CLUSTERS =
                                                "hctest.apc_switch_clusters";
    public static final String PROPERTY_RELEASES_URL = "hctest.releases_url";
    public static final String PROPERTY_RELEASES_ISO_PATH = 
						"hctest.releases_iso_path";

}
