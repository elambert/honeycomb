#!/usr/bin/perl -w
#
# $Id: time_compliance_verifier.pl 10858 2007-05-19 03:03:41Z bberndt $
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

use strict;

# GLOBAL Variables
my $INTERNAL_ADMIN_VIP = "10.123.45.200";
my $ADMIN_VIP = undef; 
my $SP_IP = undef; 
my $DATA_IP = undef; 

my $SSH = "ssh";
my $SSH_OPTIONS = "-o StrictHostKeyChecking=no -l root -q";
my $SSH_MASTER_NODE = undef; 
my $SSH_NON_MASTER_NODE = undef; 

# NTP variables
my $NTPDATE = "/opt/honeycomb/sbin/ntpdate";
my $NTPQ = "/opt/honeycomb/sbin/ntpq";

# external ntp server
my $EXTERNAL_NTP_SERVER = "129.146.17.39";

# time offset variables
my $MASTER_NODE_TIME_OFFSET = undef;
my $NON_MASTER_NODE_TIME_OFFSET = undef;
my $SWITCH_TIME_OFFSET = undef;

# node info
my %NODES = ();

sub usage {
    print "NAME\n"; 
    print "    $0 - time verifier utility\n"; 
    print "\n";
    print "SYNOPSIS\n";
    print "    $0 <cluster>\n"; 
    print "\n";
    print "DESCRIPTION\n";
    print "    Verifier utility compares time on each node including the service processor\n";
    print "    with an external NTP server. If time offset is less than 1 Minute, then node\n";
    print "    is compliant otherwise not.\n";
    print "\n";
    print "USAGE\n"; 
    print "    $0 dev325\n"; 
    print "\n";
}

sub get_non_master_node_time_offset {
    my $port = shift @_;

    # compare time between master node and external NTP server 
    my $ntp_output = `$SSH -p $port $SSH_OPTIONS $ADMIN_VIP $NTPDATE -d -u $INTERNAL_ADMIN_VIP | grep offset 2>&1`;
    chomp $ntp_output;
   
    foreach (split (/\n/, $ntp_output)) {
        if (/^offset (\S*)/) {
            $NON_MASTER_NODE_TIME_OFFSET = $1;
            print "non master node time offset: $NON_MASTER_NODE_TIME_OFFSET\n"; 
        }
    }
    return $NON_MASTER_NODE_TIME_OFFSET;
}

# compare time between master node and external NTP server 
sub get_master_node_time_offset {
    # compare time between master node and external NTP server 
    my $ntp_output = `$SSH_MASTER_NODE $NTPDATE -d -u $EXTERNAL_NTP_SERVER | grep offset 2>&1`;
    chomp $ntp_output;
    
    foreach (split (/\n/, $ntp_output)) {
        if (/^offset (\S*)/) {
            $MASTER_NODE_TIME_OFFSET = $1;
            print "master node time offset: $MASTER_NODE_TIME_OFFSET\n"; 
        }
    }
    return $MASTER_NODE_TIME_OFFSET;
}

if (++$#ARGV < 1) {
    usage();
    exit 1;
}

$ADMIN_VIP = $ARGV[0] . "-admin";
$SP_IP = $ARGV[0] . "-cheat";
$DATA_IP = $ARGV[0] . "-data";

$SSH_MASTER_NODE = $SSH . " " . $SSH_OPTIONS . " " . $ADMIN_VIP;

my $NUM_NODES = `$SSH_MASTER_NODE grep honeycomb.cell.num_nodes /config/config.properties | awk -F= {'print \$2'}`;

print "no of nodes .. $NUM_NODES\n";

my $MASTER_NODE_HOSTNAME = `$SSH_MASTER_NODE hostname`;
chomp $MASTER_NODE_HOSTNAME;

print "master node .. $MASTER_NODE_HOSTNAME\n";

$NODES{$MASTER_NODE_HOSTNAME} = get_master_node_time_offset();

for (my $node = 1; $node <= $NUM_NODES; $node++) {
    my $nodeid = 100 + $node;
    my $hostname = "hcb" . $nodeid;
    my $port = 2000 + $node; 
    next if ($hostname =~ /$MASTER_NODE_HOSTNAME/);  
    $NODES{$hostname} = get_non_master_node_time_offset($port) + $NODES{$MASTER_NODE_HOSTNAME};
}

# Get Time Offset for the service Processor
$NODES{'hcb100'} = get_non_master_node_time_offset(2000) + $NODES{$MASTER_NODE_HOSTNAME};

my $HC_COMPLIANCE_STATUS = "time compliant"; 
foreach my $hostname (sort keys %NODES) {
    my $is_node_compliant = undef;
    if ($NODES{$hostname} <= 60) {
        $is_node_compliant = "yes";
    } else {
        $is_node_compliant = "no";
        $HC_COMPLIANCE_STATUS = "not time compliant";
    }
    print "node $hostname .. time offset $NODES{$hostname} .. is node time compliant $is_node_compliant\n";
}

# Check Master node time compliance
my $date = `ssh -l admin -o StrictHostKeyChecking=no -q $ADMIN_VIP date`;
chomp $date;

print "Master node Time Compliant .. $date\n";

if ($HC_COMPLIANCE_STATUS =~ /not time compliant/) {
    exit 1;
} else {
    exit 0;
}
