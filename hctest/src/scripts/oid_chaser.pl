#!/usr/bin/perl -w
#
# $Id: oid_chaser.pl 11962 2008-04-08 17:47:53Z dm155201 $
#
# Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
# Use is subject to license terms.
#

#
# Given an OID received by the client, ie external MD OID, and the cluster,
# report all there is to know about this object's state on the cluster:
# - internal MD and data OIDs,
# - location of MD and data fragments on disks,
# - (optional) checksum fragments on disks (may be useful when chasing corruption bugs),
# - layout at store time (use print_layout.sh to get current layout),
# - (optoinal) logs of store, recovery and other events for this object (if --logs),
# - system cache record,
# - hadb records (XXX not done).
#
# Script runs from a test client, expects to find ConvertOid in /opt/test/bin.
# Script ssh's to the SP, expects to find do_servers scripts in /opt/honeycomb/bin.
#
# Usage: ./oid_chaser.pl -o <external_oid> -s <SP> -d <dataVIP> -c <num_nodes> [--logs] [--fragchecksum]
#
# TODO:
# 1. Support multichunk objects
# 2. Get HADB entries.
#

use strict;

use Getopt::Long;

my ($EXTOID, $SP, $DATAVIP, $NODECOUNT, $DOLOGS, $DOCHECKSUM) = (0, 0, 0, 16, 0, 0);

GetOptions("oid=s" => \$EXTOID,
           "sp=s" => \$SP,
           "datavip=s" => \$DATAVIP,
           "count:i" => \$NODECOUNT,
           "logs!" => \$DOLOGS,
           "fragchecksum!" => \$DOCHECKSUM);

die "Usage: ./oid_chaser.pl -o <external oid> -s <sp> -d <datavip> -c <nodecount> [--logs] [--fragchecksum] \n"
    unless $EXTOID && $SP && $DATAVIP && $NODECOUNT; 

my $CONVERTOID = "/opt/test/bin/ConvertOid"; # on client
my $CHEAT = "ssh -o StrictHostKeyChecking=no root\@${SP}";
my $DOSERVERS = "/opt/honeycomb/bin/do-servers-${NODECOUNT}.sh"; # on cheat
my $SYSRECORD_MD = 0; 

### main ### 

my $mdoid = convert_ext2int_oid($EXTOID);
my $dataoid = lookup_data_oid_syscache($EXTOID);
if (!$dataoid) {
    $SYSRECORD_MD = 0;
    print "WARN: Failed syscache lookup for OID $EXTOID - will search logs \n";
    $dataoid = lookup_data_oid_log($mdoid);
}
$dataoid or die "Failed to lookup data OID from MD OID $mdoid \n";

print "\nInternal MD OID: $mdoid \n";
print "\nInternal data OID: $dataoid \n";

if ($DOLOGS && $SYSRECORD_MD) {
    print "\nSystem record for oid $EXTOID:\n".$SYSRECORD_MD;
}

print "\n";
my $mdcount = lookup_fragments($mdoid);
print "\n";
my $datacount = lookup_fragments($dataoid);

my $rc = ($mdcount < 7 || $datacount < 7) ? -1 : 0;

exit $rc unless $DOLOGS;

my $oidlogs = get_oid_logs($dataoid, $mdoid);
$oidlogs or die "No logs found for OID $EXTOID \n";

print_storelayout($mdoid, $dataoid, $oidlogs);

print "\nLogs for OID $EXTOID:\n\n";
print $oidlogs."\n"; 

exit $rc;

### end main ###

sub convert_ext2int_oid {
    my ($extoid) = @_;
    my $intoid; 
    my $out = `$CONVERTOID $extoid`;
    # Note, this assumes cell ID 0, but it doesn't matter
    # We will not use cell id - specific bits
    $_ = $out;
    if(/Internal:\s(\S+)/) {
        $intoid = $1;
        chomp $intoid;
    }
    (defined $intoid) or die 
        "Failed to convert to internal OID from external OID $extoid \n";
    return $intoid;
}


# given external MD OID, lookup data OID via cluster's system cache
# data OID is returned in internal format
#
sub lookup_data_oid_syscache{
    my ($ext_md_oid) = @_;
    my $ext_data_oid = 0;

    my $url = "http://".$DATAVIP.":8080/".
        "query-select?metadata-type=system&where-clause=checkOID%20".
        $ext_md_oid."%20false&select-clause=XXXX"; 
    
    # example of a query url:
    # http://dev305-data.sfbay.sun.com:8080/query-select?metadata-type=system&where-clause=checkOID%200200050d09fd598de0d03911dc961800e0815970b300000acb0200000000%20false&select-clause=XXXX
    # in the url, %20 == space; select-clause=XXXX is bogus but required

    $SYSRECORD_MD = `curl -s \"${url}\"`;
    return 0 if ($? != 0);
    
    my @lines = split(/\n/, $SYSRECORD_MD);
    foreach my $line (@lines) {
        my (undef, $name, undef, $value) = split(/\"/, $line);
        # <attribute name="system.object_link" value="010001d58bf759d04611dcb2ed00e081595a03000005460100000000"/>
        next unless (defined $name and defined $value); # for malformed lines
        if ($name eq "system.object_link") {
            $ext_data_oid = $value;
        }
    }
    return 0 unless $ext_data_oid;
    
    return convert_ext2int_oid($ext_data_oid);
}

# given internal MD OID, lookup data OID in cheat's messages log
#
sub lookup_data_oid_log {
    my ($mdoid) = @_;
    my $dataoid = 0;
    my $logs = search_logs("OA stored.*$mdoid", "OA stored.*$mdoid");
    #
    # Jan 31 21:52:40 hcb108 java[21092]: [ID 702911 local0.info] 193 INFO [OAClient.renameFragments] (315.1) OA stored d5b7c24a-d046-11dc-b2ed-00e081595a03.1.1.29824.2.2.0.7425 size 569 layout MapId(7425): [111:3 116:0 109:1 114:2 102:3 105:0 104:1] in directory /74/25 with hash null and link d58bf759-d046-11dc-b2ed-00e081595a03.1.1.29824.2.1.0.1350
    #
    $logs or die "Failed to locate store log for OID $mdoid \n";
    my @words = split(/ /, $logs);
    $dataoid = $words[$#words];
    chomp $dataoid;
    return $dataoid;
}

# given internal OID, tell us which set of disks it was stored on 
#
sub print_storelayout {
    my ($oid1, $oid2, $logs) = @_;
    my $matched = 0;
    my $oid;
    for my $line (split(/\n/, $logs)) {
        if ($line =~ /OA stored $oid1|OA stored $oid2/) {
            $matched++;
            
        # Jan 31 21:52:39 hcb108 java[21092]: [ID 702911 local0.info] 985 INFO [OAClient.renameFragments] (314.1) OA stored d58bf759-d046-11dc-b2ed-00e081595a03.1.1.29824.2.1.0.1350 size 31 layout MapId(1350): [114:0 111:1 110:2 112:3 107:0 105:1 108:2] in directory /13/50 with hash null and link null
       # Jan 31 21:52:40 hcb108 java[21092]: [ID 702911 local0.info] 193 INFO [OAClient.renameFragments] (315.1) OA stored d5b7c24a-d046-11dc-b2ed-00e081595a03.1.1.29824.2.2.0.7425 size 569 layout MapId(7425): [111:3 116:0 109:1 114:2 102:3 105:0 104:1] in directory /74/25 with hash null and link d58bf759-d046-11dc-b2ed-00e081595a03.1.1.29824.2.1.0.1350
            
            my @chunks = split(/\[|\]/, $line);
            my $layout = $chunks[7];
            my $oid = ($line =~ /OA stored $oid1/) ? $oid1 : $oid2;
            print "\nLayout at store time for OID $oid was: $layout \n";
            last if ($matched == 2);
        }
    }
}

# Search logs on the cheat, starting with latest and going backwards,
# for the given regular expression. Stop searching when stopclause is found.
#
sub search_logs {
    my ($regex, $stopclause) = @_;
    my $logs = "";
    my $done = 0;

    # search non-zipped /var/adm/messages, .0, .1
    #
    foreach ("messages", "messages.0", "messages.1") {
        my $log = "/var/adm/".$_;
        my $cmd = "$CHEAT \'egrep \"${regex}\" $log\'";
        #print "Running: $cmd\n";
        my $foundlogs .= `$cmd`;
        $logs = $foundlogs . $logs; # prepend
        if ($foundlogs =~ /$stopclause/) {
            $done = 1;
            last;
        }
    }

    return $logs if ($done);

    # otherwise look in older messages.gz
    #
    my $lastlog = `$CHEAT ls -ltr /var/adm/messages*.gz |head -1`;
    my @tokens = split(/\./, $lastlog);
    my $lognum = $tokens[1];
    for (my $i = 2; $i <= $lognum; $i++) {
        my $log = "/var/adm/messages.$i.gz";
        my $cmd = "$CHEAT \'gzegrep \"${regex}\" $log\'";
        #print "Running: $cmd\n";
        my $foundlogs .= `$cmd`;
        $logs = $foundlogs . $logs; # prepend
        if ($foundlogs =~ /$stopclause/) {
            $done = 1;
            last;
        }
    }

    return $logs;
}

# Find and print log messages from the cheat involving given OIDs
#
sub get_oid_logs {
    my ($dataoid, $mdoid) = @_;
    my @oid1s = split(/\./, $dataoid);
    my @oid2s = split(/\./, $mdoid);
    my $uid1 = $oid1s[0];
    my $uid2 = $oid2s[0];
    my $logs = search_logs("${uid1}|${uid2}", "create.*$dataoid");
    #
    # stop searching when seeing the store-start message:
    # Feb  5 00:25:46 hcb109 java[22700]: [ID 702911 local0.info] 113 INFO [FragmentFileSet.create] (1506.1) OID [e4b20a07-d380-11dc-a1b3-00e081596edc.1.1.31459.2.1.0.5299] avg/max frag create (ms): -1/-1
    #
    return $logs;
}

# given internal OID (data or MD), locate the layout it was stored on
#
sub get_layout_dirs {
    my ($oid) = @_;
    my @tokens = split(/\./, $oid);
    my $layout = $tokens[$#tokens];
    while ($layout !~ /\d\d\d\d/) {
        $layout = "0".$layout;
    }
    my ($upper, $lower) = ($layout =~ /(\d\d)(\d\d)/);
    return $upper."/".$lower;
}

# given internal OID (data or MD), locate its fragments on disks
#
sub lookup_fragments {
    my ($oid) = @_;
    my $layout_dirs = get_layout_dirs($oid);
    my $cmd = "$CHEAT $DOSERVERS \"ls -l /data/?/${layout_dirs}/${oid}* 2>&1 |grep -v No\"";
    #print "Running: $cmd \n";
    my $ls = `$cmd`;
    my $count = 0;
    foreach my $line (split(/\n/, $ls)) {
        $count++ if ($line =~ /data/);
    }
    if ($count == 0) {
        print "Found NO ($count) fragments for OID $oid\n";
    } elsif ($count < 7) {
        print "Found fewer than 7 ($count) fragments for OID $oid. Listing:\n$ls";
    } elsif ($count == 7) {
        print "Found all ($count) fragments for OID $oid. Listing:\n$ls";
    } else {
        print "Found more than 7 ($count) fragments for OID $oid. Listing:\n$ls";
    }

    return $count unless $DOCHECKSUM;

    # Calculate checksums for each fragment on disk.
    # Author: Josh Dobies.
    # This can be useful in chasing corruption bugs, particularly when using
    # DupStore or similar tool to store the same object content twice, 
    # and then compare resulting fragments. 
    
    print "Fragment check sums:\n";
    my $node = 0;
    foreach my $line (split(/\n/, $ls)) {
        if ($line =~ /node.*:/) {
            $node++;
        }
        if ($line =~ /(\/data.*)$/) {
            my $frag_path = $1;
            my $hcbID = 100 + $node;
            my $sum = `$CHEAT ssh hcb${hcbID} sum $1`;
            print "hcb${hcbID}: $sum";
        }
    }

    return $count;
}

