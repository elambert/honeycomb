#!/usr/bin/perl -w
#
# Copyright © 2008, Sun Microsystems, Inc.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
#   # Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#
#   # Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#
#   # Neither the name of Sun Microsystems, Inc. nor the names of its
# contributors may be used to endorse or promote products derived from
# this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
# TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
# PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
# OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.



#
# $Id: test_dns_setup.pl 10858 2007-05-19 03:03:41Z bberndt $

use FileHandle;
use Sys::Hostname;
use strict;

# change these if you need to run on a different cell
my $cluster_name = "hcb101"; #which ever node is the president
my $admin_cluster = "admin\@10.123.45.200"; 
my $local_domainname = "it.sfbay.sun.com";
my $local_dnssearch = "sun.com,central.sun.com";
my $local_primarydnsserver = "129.145.155.32";
my $local_secondarydnsserver = "129.145.155.42";
my $ping_hostname="hc-dev.sfbay.sun.com";

# vars
my $resolv_conf_handle = "/etc/resolv.conf";
my $config_properties_handle_cheat = "/tmp/config.properties";
my $domainname = "";
my $dnssearch = "";
my $primarydnsserver = "";
my $secondarydnsserver = "";
my $dns_enabled="0";

# function decl
sub verify_dns_enabled();
sub check_if_cheat();
sub enable_dns();
sub verify_ping_works();

# function Defn
sub check_if_cheat() {
     if (! -e "/config") {
        return "cheat";
     }
}

sub verify_dns_enabled() {
    if ( "cheat" eq check_if_cheat()) {
        open CONFIG_PROPERTIES,  "<  $config_properties_handle_cheat"  or die "can't open $config_properties_handle_cheat: $!";
    } else {
        die "Test from a cheat node.";
    }
    while (<CONFIG_PROPERTIES>) {
           if ( /honeycomb.cell.dns\s*=\s*(.*)/ ) {
               $dns_enabled = qq{$1};
               if ($dns_enabled eq "y")  {
                  close CONFIG_PROPERTIES;
                  return "enabled";
               } else {
                  close CONFIG_PROPERTIES;
                  return "disabled";
               }
           }
    } #while (<CONFIG_PROPERTIES>)
    close CONFIG_PROPERTIES;
    return "disabled";
}

#Enable or disable DNS

sub enable_dns() {
    my $choice = shift (@_);
    warn "Ensure that no one has logged in to admin cli shell\n";
    if ($choice eq "disable") {
      `ssh  -o StrictHostKeyChecking=no   $admin_cluster "netcfg --dns n"`;
      print "Disabled DNS\n";
    } elsif ($choice eq "enable") {
      `ssh  -o StrictHostKeyChecking=no   $admin_cluster "netcfg --dns y --domain_name $local_domainname --dns_search $local_dnssearch --primary_dns_server $local_primarydnsserver --secondary_dns_server $local_secondarydnsserver"`;
      print "Enabled DNS\n";
    } else {
        die "Invalid choice to enable_dns :: $choice";
    }
    print "Waiting for changes to take effect\n";
    sleep 100; # wait for the changes to take effect
}

sub verify_ping_works() {
    #If dns is enabled ping hc-dev.sfbay.sun.com from cheat and see if it works
    my $x=system("ping", "$ping_hostname");
    if ($x == 0) {
       print "ping to $ping_hostname is alive\n";
    } else {
       die "Failed to ping $ping_hostname:: $x\n";
    }

    # Then ssh to admin node and ping the hc-dev.sfbay.sun.com and see if it works
    my $y= system("ssh  -o StrictHostKeyChecking=no   $cluster_name ping $ping_hostname");
    if ($y == 0) {
       print "ping to $ping_hostname from admin node is alive\n";
    } else {
       die "Failed to ping $ping_hostname from admin node:: $x\n";
    }
}

sub verify_ping_fails() {
    my $y= system("ssh  -o StrictHostKeyChecking=no   $cluster_name ping $ping_hostname");
    if ($y != 0) {
        print "=>Verified Ping fails\n";
    } else {
        die "FAILED to disable DNS using netcfg..\n";
    }
}

sub print_config_file() {
    open CONFIG_PROPERTIES,  "<  $config_properties_handle_cheat"  or die "can't open $config_properties_handle_cheat: $!";
    while (<CONFIG_PROPERTIES>) {
        print $_;
    }
}

sub get_config_props_from_node() {
    my $y=`ping $admin_cluster`;
    my $status;
    if ($y =~ /unknown/) {
      return ; # not ready
    }

    if (($status=`scp hcb101:/config/config.properties /tmp`) ne "") {
       if (($status=`scp hcb102:/config/config.properties /tmp`) ne "") {
          if (($status=`scp hcb103:/config/config.properties /tmp`) ne "") {
             die "Unable to to reach hcb10{1,3}\n";
          }
       }
    }
}

#
# main()
# Run this script from cheat on a properly configured cluster and with
# quorum enabled. Check that using cmm_verifier command
#
warn "WARNING....\n";
warn "This Test will run succesfully only when all the properties\n";
warn "like smtp_server are configured as IP addr.\n\n";
    
# scp the config.properties from admin to /tmp
get_config_props_from_node();

# DYNAMIC DNS IS DISABLED SO THIS TEST IS NOT GOING TO WORK FOR NOW

return 0;

if (( "enabled" eq verify_dns_enabled())) {
    print "DNS is originally enabled\n";
    verify_ping_works();
    #Now disable DNS
    &enable_dns("disable");
    print_config_file();
    verify_ping_fails();
    &enable_dns("enable");
    print_config_file();
} else {
  # Enable the DNS on the cheat and see if it works
    print "DNS is originally disabled\n";
    &enable_dns("enable");
    print_config_file();
    verify_ping_works();
    &enable_dns("disable");
    print_config_file();
    verify_ping_fails();
}
print "THE DNS TEST SUCCEEDED\n";
