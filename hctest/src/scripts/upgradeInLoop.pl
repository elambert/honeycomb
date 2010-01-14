#!/usr/bin/perl
#
# $Id: upgradeInLoop.pl 11285 2007-07-31 17:45:53Z sarahg $
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

#
# Script to upgrade Honeycomb in a loop
#
# Consult the usage message for required arguments.
#
# Some notes:
# 
# 1. This script assumes that authentication to root on the cheat node
# is configured without passwd from the client.  Further, it assumes
# you've already answered 'yes' to this question:
#
# The authenticity of host 'dev310-cheat (10.7.224.200)' can't be established.
# RSA key fingerprint is 6b:6b:1a:7b:7a:13:94:dc:f9:0b:e0:dd:9d:80:f0:6d.
# Are you sure you want to continue connecting (yes/no)? yes
#
#
#
# 2. There is a possible issue encountered where upgrade checker failed
# but test did not detect this and exit.  Manually review test output
# until that issue is corrected.  Flamebox can be set to detect 
# the 'Failed' string below.  Code from upgrade checker seems to exit
# non-zero in both cases, so it is a bit mysterious:
#
# ...
# Verify md5sum for node ramdisk. Usually takes 10-60 mins.
# ================================================================================
#
# INFO: DONE with checksumming ramdisks for node hcb101 slice 0
# ...
# INFO: DONE with checksumming ramdisks for node hcb115 slice 0
# mount: I/O error
# mount: Cannot mount /dev/dsk/c0t1d0s0
# Failed to run ... /usr/bin/ssh -q -l root -o StrictHostKeyChecking=no
# hcb115 mount /boot/images/1
#
#
# *** Beginning iteration 1 ***
# ssh root@dev308-cheat ...
# ...
#
#
#
# However, it is the case that some errors are indeed detected:
#
# cl5 test # ./upgradeInLoop.pl http://... ...  ...
# ...
# ERROR: Expected version 1.1-11226, switch version string
# /export/home/mgoff/svn/tcp-fix/build/../build/pkgdir/overlay-11018.tar
# 2007-07-17 14:41:36 PDT hc-dev.sfbay.sun.com
#     
#   backup switch overlay version 11018
#   Print Switch CLI version & compare with version on both
#   switches..........[FAIL]
# ...
#
# ssh root@dev310-cheat upgrade_checker.pl command failed
# cl5 test # 
#


sub usage {
    print "Usage:\n";
    print "NAME\n";
    print "    $0 - upgrade in a loop\n";
    print "\n";
    print "SYNOPSIS\n";
    print "    $0  http_url  service_node  [num_iterations]\n\n";
    print "DESCRIPTION\n";
    print "    Run upgrade in a loop with the given build URL\n";
    print "    via the given service node for the given number of iterations.\n";
    print "    num_iterations defaults to 20 if not specified.\n";
    print "\n";
    print "EXAMPLE\n";
    print "    $0  http://10.7.228.10/~hcbuild/repository/releases/1.1/1.1-70/AUTOBUILT/pkgdir/st5800_1.1-70.iso ";
    print " dev308-cheat  50\n";
    print "\n";
    exit 1;
} # usage #

# This is the default number of iterations if the number isn't
# given on the command line
$ITERATIONS = 20;

# Handle Arguments
if ((!defined $ARGV[0]) || (!defined $ARGV[1]))  {
    print "Error: missing arguments\n";
    usage();
}

$UPGRADE_URL = $ARGV[0];
$CHEATVIP = $ARGV[1];

print "Using url $UPGRADE_URL\n";
print "Using service node $CHEATVIP\n";

# iterations is optional argument
if (defined $ARGV[2]) {
    $ITERATIONS = $ARGV[2];
    print "Doing $ITERATIONS iterations\n";
} else {
    print "Using $ITERATIONS as default number of iterations\n";
}


$INTERNAL_ADMIN_IP = "10.123.45.200";
$SSH_ADMIN = "ssh root\@$CHEATVIP ssh -o StrictHostKeyChecking=no admin\@$INTERNAL_ADMIN_IP";
$LOOPCOUNT = 1;
$MAX_HADB_WAIT = 3 * 60 * 60; # unit is seconds, this is 3 hours

$| = 1;                 # don't buffer STDOUT
&WaitHAF;
printf("*** Running baseline upgrade_checker before first upgrade. ***\n");
printf("ssh root\@$CHEATVIP upgrade_checker.pl\n");
!system "ssh root\@$CHEATVIP upgrade_checker.pl" or die
       "ssh root\@$CHEATVIP upgrade_checker.pl command failed\n";

while ($LOOPCOUNT <= $ITERATIONS)
{
       printf("\n\n\n*** Beginning iteration $LOOPCOUNT ***\n");
       printf("$SSH_ADMIN upgrade --force $UPGRADE_URL\n");
       system "$SSH_ADMIN upgrade --force $UPGRADE_URL";
       &WaitHAF;
       printf("ssh root\@$CHEATVIP upgrade_checker.pl\n");
       !system "ssh root\@$CHEATVIP upgrade_checker.pl" or die
               "ssh root\@$CHEATVIP upgrade_checker.pl command failed\n";
       $LOOPCOUNT++;
}

printf("Test Passed.  Ran $ITERATIONS iterations\n");

#
# Wait for cluster to become online and HAFaultTolerant
# "Data services Online, Query Engine Status: HAFaultTolerant"
#
sub WaitHAF
{
       $HAFaultTolerant = 0;
       $giveuptime = time() + $MAX_HADB_WAIT;

       printf("Checking for HADB in HAFaultTolerant state\n");
       printf("Will wait $MAX_HADB_WAIT secs for HADB to reach this state\n");

       while ( $HAFaultTolerant == 0 )
       {
               # check if we've exceeded our time limit
               $curtime = time();
               print "Current time: $curtime (";
               print scalar localtime($curtime), ")";
               print " Give up time: $giveuptime (";
               print scalar localtime($giveuptime), ")\n";

               if ($curtime > $giveuptime) {
                       printf("Test Failed: HADB didn't reach ");
                       printf("HAFaultTolerant within ");
                       printf("$MAX_HADB_WAIT seconds\n");
                       exit 1;
               }

               if (open (__HAF__,"$SSH_ADMIN hadb status -F |") )
               {
                       while ( <__HAF__> )
                       {
                               @haf = split;
                               if (/HAFaultTolerant/)
                               {
                                       $HAFaultTolerant = 1;
                               }
                       }
               } else {
                       printf("ssh command failed \n");
                   exit 1;
               }
               if ( $HAFaultTolerant == 0 )
               {
                       printf("Waiting for HAFaultTolerant...\n");
                       sleep 60;
               }
       }

       print "HADB has reached the desired state\n";
} 
