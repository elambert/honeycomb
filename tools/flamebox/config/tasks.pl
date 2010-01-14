#!/usr/bin/perl -w
#
# This is the url to post the results to.
#
 
#
# $Id: tasks.pl 11944 2008-03-27 21:06:10Z jk142663 $
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

use Getopt::Long;

our $posturl = 'http://hc-flamebox.sfbay.sun.com/fbox-cgi/postresult.cgi';
#
# Email address to send errors and warnings to
# Currently set to a NetAdmin alias, open for subscription 
#
# You can also specify individual email addresses in each task
# to have warning/error mails will go to those and not to hc-flamebox.
#

our $flamebox_email = 'hc-flamebox@sun.com';

our $cluster = '';
our $clients = '';
our $nodes = '';
our $srcroot = '';
our $isourl = '';
our $link = '';

our $iso_image = "honeycombdvd.iso";
our $upgrade_jar = "st5800-upgrade.jar";

parse_tasks_args();

# cluster info
if (!defined($ENV{'CLUSTER'})) {
    if ($cluster eq '') {
        print "\n*** Missing required arguments --cluster <cluster_name>\n";
        tasks_usage();
	exit 1;
    }
    $ENV{'CLUSTER'} = $cluster;
    $ENV{'sp'} = $cluster."-cheat";
    $ENV{'admin'} = $cluster."-admin";
    $ENV{'data'} = $cluster."-data";
} 

# list of test clients
if (!defined($ENV{'CLIENTS'})) {
    if ($clients eq '') {
        print "\n*** Missing required argument --clients <list of clients>\n";
        tasks_usage();
	exit 1;
    }
    $ENV{'CLIENTS'} = $clients;
}
our @clients = split (/,/, $ENV{'CLIENTS'});

# Number of nodes in the cluster
if (!defined($ENV{'NODES'})) {
    if ($nodes eq '') {
        print "\n*** Missing required argument --nodes <node_num>\n";
        tasks_usage();
	exit 1;
    }
    $ENV{'NODES'} = $nodes;
}

# absolute path of workspace
if (!defined($ENV{'SRCROOT'})) {
    if ($srcroot eq '') {
        $srcroot = "/export/home/build/svn/trunk";
    }
    $ENV{'SRCROOT'} = $srcroot;
}

# http location of the url
if (!defined($ENV{'ISOURL'})) {
    if ($isourl eq '') {
        $isourl = "http://10.7.224.9/~build/".$iso_image;
    }
    $ENV{'ISOURL'} = $isourl;
}

# absolute path of iso image on hc-flamebox
if (!defined($ENV{'LINK'})) {
    if ($link eq '') {
        $link = "/export/home/build/public_html";
    }
    $ENV{'LINK'} = $link;
}

our $hctestjar = "$R{'te'}{'srcroot'}/build/hctest/dist/lib/honeycomb-hctest.jar";
our $emulator_tar = "/tmp/emulator.tar";

our $repository = "/usr/local/svnrepos/honeycomb"; 
our $branch = "trunk";
our $module = "honeycomb";

our $PATH = "/bin:/sbin:/usr/bin:/usr/sbin:/usr/lib/java/bin:/opt/test/bin:".
        "/usr/local/bin:/opt/bin:/usr/i686-pc-linux-gnu/gcc-bin/3.2:".
        "/usr/local/pgsql/bin/";

our $SSH = "/usr/bin/ssh";
our $SCP = "/bin/scp";
our $SSH_OPTIONS = "-o StrictHostKeyChecking=no -q -i $R{'te'}{'srcroot'}/hctest/etc/ssh/id_dsa";
our $sshcluster = "$SSH $SSH_OPTIONS"; 

our $DO_SERVERS_16 = "/opt/honeycomb/bin/do-servers-16.sh";
our $COPY_SERVERS_16 = "/opt/honeycomb/bin/copy-to-servers-16.sh";
our $DO_SERVERS_8 = "/opt/honeycomb/bin/do-servers-8.sh";
our $COPY_SERVERS_8 = "/opt/honeycomb/bin/copy-to-servers-8.sh";

our $DO_SERVERS = $DO_SERVERS_8;
our $COPY_SERVERS = $COPY_SERVERS_8;
if ($ENV{'NODES'} eq "16") {
    $DO_SERVERS = $DO_SERVERS_16;
    $COPY_SERVERS = $COPY_SERVERS_16;
}


#
# Resource Hash Table for tests. for eg. clients, clusters, ws, iso.
#
# te == test environment
#
$RESOURCES{'te'} = {
    'cluster' => $ENV{'CLUSTER'}, 
    'sp' => $ENV{'sp'}, 
    'admin' => $ENV{'admin'}, 
    'data' => $ENV{'data'},
    'nodes' => $ENV{'NODES'},
    'clients' => \@clients, 
    'srcroot' => $ENV{'SRCROOT'},
    'isourl' => $ENV{'ISOURL'}, 
    'link' => $ENV{'LINK'},  
    'DO_SERVERS' => $DO_SERVERS,
    'COPY_SERVERS' => $COPY_SERVERS,    
};

                  
#
# Alias for shorter referencing
#
*R = \%RESOURCES;


##############################################################################
#
# Define each task below.
#

$tasks{"HelloWorld"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [
                  "rm $R{'te'}{'link'}/$$iso_image",
                  "ln -sf $R{'te'}{'srcroot'}/build/pkgdir/st5800_1.1-\*.iso $R{'te'}{'link'}/$$iso_image", 
                ] 
};

$tasks{"SvnUpdate"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: Updating $R{'te'}{'srcroot'}.",
                  "cd $R{'te'}{'srcroot'}",
                  "/opt/csw/bin/svn update", ]
};

$tasks{"MakeAll"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: Performing `make all` in $R{'te'}{'srcroot'}.",
                  "cd $R{'te'}{'srcroot'}/build",
                  "make all", 
                  "head -1 $R{'te'}{'srcroot'}/build/pkgdir/SUNWhcserver/root/opt/honeycomb/version",
                ]
};

$tasks{"MakeCheckCommit"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: Performing `make check_commit` in $R{'te'}{'srcroot'}.",
                  "cd $R{'te'}{'srcroot'}/build",
                  "make check_commit", ]
};

$tasks{"AntDvd"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: Performing `sudo ant dvd` in $R{'te'}{'srcroot'}/platform/.",
                  "head -1 $R{'te'}{'srcroot'}/build/pkgdir/SUNWhcserver/root/opt/honeycomb/version",
                  "cd $R{'te'}{'srcroot'}/platform/",
                  "sudo ant dvd", 
                  "if [ -f $R{'te'}{'link'}/$iso_image ]; then \
                      rm $R{'te'}{'link'}/$iso_image; \
                  fi",
                  "ln -sf $R{'te'}{'srcroot'}/build/pkgdir/st5800_1.1-\*.iso $R{'te'}{'link'}/$iso_image",
                  "if [ -f $R{'te'}{'link'}/$upgrade_jar ]; then \
                      rm $R{'te'}{'link'}/$upgrade_jar; \
                  fi",
                  "cp $R{'te'}{'srcroot'}/build/pkgdir/$upgrade_jar $R{'te'}{'link'}/",
                ]
};


$tasks{"Upgrade"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Upgrading $R{'te'}{'cluster'}",
                  "chmod 600 $R{'te'}{'srcroot'}/hctest/etc/ssh/id_dsa",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "$SSH $R{'te'}{'admin'} -l admin $SSH_OPTIONS upgrade -F $R{'te'}{'isourl'}",
                  "sleep 2700",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",  
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep 'HAFaultTolerant'",                    
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                ] 
};

$tasks{"UpgradeCheck"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: validating upgrade",
		  "echo *** Cluster:     $R{'te'}{'sp'}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS '/opt/honeycomb/bin/upgrade_checker.pl'" ]
};

$tasks{"Reboot"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ 
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "echo INFO: Run 'reboot' on $R{'te'}{'cluster'}",
                  "chmod 600 $R{'te'}{'srcroot'}/hctest/etc/ssh/id_dsa",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS '/opt/test/bin/reboottest.sh -s $R{'te'}{'sp'} -a $R{'te'}{'admin'} -i 1; '" 
                ]
};

$tasks{"RebootAll"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ 
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "echo INFO: Run 'reboot --all' on $R{'te'}{'cluster'}",
                  "chmod 600 $R{'te'}{'srcroot'}/hctest/etc/ssh/id_dsa",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestReboot -ctx nodes=$ENV{'NODES'} -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{"Wipe"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Wiping $R{'te'}{'cluster'}",
                  "chmod 600 $R{'te'}{'srcroot'}/hctest/etc/ssh/id_dsa",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestWipe -ctx nodes=$ENV{'NODES'} -ctx cluster=$R{'te'}{'cluster'}\""
                ] 
};

$tasks{"ResetSchema"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: Resetting schema on $R{'te'}{'cluster'}",
                  "chmod 600 $R{'te'}{'srcroot'}/hctest/etc/ssh/id_dsa",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "cat $R{'te'}{'srcroot'}/server/src/config/metadata_config_merged.xml | 
                  ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS mdconfig -a",
                ]
};

$tasks{"MakeCleanAll"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: Performing `sudo make cleanall` in $R{'te'}{'srcroot'}.",
                  "cd $R{'te'}{'srcroot'}/build",
                  "sudo make cleanall", ]
};

$tasks{"UpdateClients"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: Doing the client install",
	          "echo *** Source: $R{'te'}{'srcroot'}", 
		  "echo *** Client: @{$R{'te'}{'clients'}}",
                  "cd $R{'te'}{'srcroot'}/build",
                  "for cl in @{$R{'te'}{'clients'}}; do make client=\$cl install_client; done",
                  "for cl in @{$R{'te'}{'clients'}}; do make client=\$cl install_perf; done",
                  "for cl in @{$R{'te'}{'clients'}}; do ssh \$cl -l root $SSH_OPTIONS \"cat /opt/test/bin/load/emi-load/ENV | sed -e \'s/^DATAVIP=.*/DATAVIP=$R{'te'}{'data'}/\' > /tmp/ENV\"; done",
                  "for cl in @{$R{'te'}{'clients'}}; do ssh \$cl -l root $SSH_OPTIONS \'mv /tmp/ENV /opt/test/bin/load/emi-load/ENV\'; done",
		  "for cl in @{$R{'te'}{'clients'}}; do ssh \$cl -l root $SSH_OPTIONS \'mkdir -p /mnt/test/emi-stresslogs/'; done",
		  "for cl in @{$R{'te'}{'clients'}}; do ssh \$cl -l root $SSH_OPTIONS \'/opt/test/bin/load/emi-load/clean-stress-logs.sh'; done",
                ]
};

$tasks{"InstallWhitebox"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: Installing Whitebox packages on cheat and cluster nodes",
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
		  "scp -r $SSH_OPTIONS $R{'te'}{'srcroot'}/build/pkgdir/SUNWhcwb* root\@$R{'te'}{'sp'}:/export/",
		  "scp -r $SSH_OPTIONS $R{'te'}{'srcroot'}/build/hctest/dist/lib/honeycomb-hctest.jar root\@$R{'te'}{'sp'}:/opt/test/lib",
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS '/bin/yes | pkgadd -d /export SUNWhcwbsp'",
		 ]
};

$tasks{"JavaRegression"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Running the java regression test suite",
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "cd $R{'te'}{'srcroot'}/build",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export " .
                      "PATH=$PATH ; /opt/test/bin/runtest " .
                      "-ctx cluster=$R{'te'}{'cluster'}:explore:include=regression\""
		 ]
};

$tasks{"UnitTest"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Running Unit tests",
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
                  "head -1 $R{'te'}{'srcroot'}/build/pkgdir/SUNWhcserver/root/opt/honeycomb/version",
                  "cd $R{'te'}{'srcroot'}/build/unit_tests/dist",
                  "./run_tests.sh 2>&1 | awk '{print \"UT: \",\$0}'",
		 ]
};

$tasks{"OAUnitTest"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "wilson.ross\@sun.com", 
    warning_email => "wilson.ross\@sun.com",
    commands => [ "echo INFO: Running OA unit tests",
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
                  "head -1 $R{'te'}{'srcroot'}/build/pkgdir/SUNWhcserver/root/opt/honeycomb/version",
                  "cd $R{'te'}{'srcroot'}/build/unit_tests/deleteTest/dist",
                  "./runAll.sh 2>&1 | awk '{print \"OA_UT: \",\$0}'",
		 ]
};

$tasks{"SmokePerfRegression"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com",
    commands => [ "echo INFO: Running the performance regression test suite",
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "cd $R{'te'}{'srcroot'}/build",
                  "make client=${$R{'te'}{'clients'}}[0] install_perf",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS 'mkdir -p /mnt/test/perf; cd /opt/performance; PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/lib/java/bin:/opt/test/bin:/usr/local/bin:/opt/bin:/usr/i686-pc-linux-gnu/gcc-bin/3.2:/usr/local/pgsql/bin/ ./regression_test.sh -c $R{'te'}{'cluster'} -t ${$R{'te'}{'clients'}}[0] -l /mnt/test/perf" 
                ]
};

$tasks{"FragmentSingleTests"} = {  
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "daria.mehra\@sun.com",
    warning_email => "daria.mehra\@sun.com",
    commands => [ "echo INFO: Fragment Single Tests",
	          "echo INFO: Test completes in ~2 hours",
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Cheat:       $R{'te'}{'sp'}", 
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.FragmentLevelTests -ctx cluster=$R{'te'}{'cluster'}:deletesingle:startinfilesize=0:endingfilesize=1073741826\"",
		 ]
};

$tasks{"FragmentDoubleTests"} = {  
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "daria.mehra\@sun.com",
    warning_email => "daria.mehra\@sun.com",
    commands => [ "echo INFO: Fragment Double Tests",
	          "echo INFO: Test completes in ~11-12 hours",
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Cheat:       $R{'te'}{'sp'}", 
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.FragmentLevelTests -ctx cluster=$R{'te'}{'cluster'}:deletedouble:startinfilesize=0:endingfilesize=1073741826\"",
		 ]
};

$tasks{"HealingBasicTests"} = {  
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "daria.mehra\@sun.com",
    warning_email => "daria.mehra\@sun.com",
    commands => [ "echo INFO: Basic Healing Tests",
	          "echo INFO: Test completes in ~2 hours",
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Cheat:       $R{'te'}{'sp'}", 
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.HealingBasic -ctx allfilesizes:cluster=$R{'te'}{'cluster'}\"",
		 ]
};

$tasks{"TestEmulator"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Deploying Emulator",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "head -1 $R{'te'}{'srcroot'}/build/pkgdir/SUNWhcserver/root/opt/honeycomb/version",
                  "rm -f ${emulator_tar}", 
		  "tar cf ${emulator_tar} -C $R{'te'}{'srcroot'}/build/emulator dist",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS  'mkdir -p /mnt/test/flamebox-emulator'",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS 'rm -rf /mnt/test/flamebox-emulator/*'",
                  "scp -r $SSH_OPTIONS ${emulator_tar} root\@${$R{'te'}{'clients'}}[0]:/mnt/test/flamebox-emulator",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS 'tar -xf ".
                      "/mnt/test/flamebox-emulator/emulator.tar -C /mnt/test/flamebox-emulator'",
                  "scp -r $SSH_OPTIONS $R{'te'}{'srcroot'}/server/src/config/metadata_config_merged.xml ".
                      "root\@${$R{'te'}{'clients'}}[0]:/mnt/test/flamebox-emulator/dist/config",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS ".
                      "'PATH=/usr/lib/java/bin:\$PATH;/mnt/test/flamebox-emulator/".
                      "dist/bin/metadata_merge_config.sh /mnt/test/flamebox-emulator/".
                      "dist/config/metadata_config_merged.xml'",
                  "ssh ${$R{'te'}{'clients'}}[0] -f -l root $SSH_OPTIONS ".
                      "'PATH=/usr/lib/java/bin:\$PATH;/mnt/test/flamebox-emulator/".
                      "dist/bin/start.sh &'",
                  "sleep 10",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS ".
                      "'export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/lib/java:".
                      "/usr/lib/java/bin:/usr/local/bin:/opt/bin:".
                      "/usr/i686-pc-linux-gnu/gcc-bin/3.2:/usr/local/pgsql/bin/; ".
                      "/opt/test/bin/runtest -ctx include=emulator:dataVIP=localhost".
                      ":adminVIP=localhost:spIP=localhost:explore:nocluster'",
                  "(cd /tmp; LD_LIBRARY_PATH=$R{'te'}{'srcroot'}/build/client_c/".
                      "build_sol_10_x86/curl/dist/lib:$R{'te'}{'srcroot'}/build/client_c".
                      "/build_sol_10_x86/honeycomb/dist; export LD_LIBRARY_PATH; ".
                      "$R{'te'}{'srcroot'}/build/client_c/build_sol_10_x86/test/dist/testhcclient ".
                      "${$R{'te'}{'clients'}}[0] 8080 0 $R{'te'}{'srcroot'}/build/".
                      "client_c/build_sol_10_x86/test/dist/testhcclient)",
                  "ssh ${$R{'te'}{'clients'}}[0] -f -l root $SSH_OPTIONS ".
                      "'pkill -f emulator'"]
};

$tasks{"OverniteLoadTest"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Running Overnite EMI Stress test from @{$R{'te'}{'clients'}} ", 
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Clients:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "echo do store", 
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/start_io.sh -o STR \
                       -s $R{'te'}{'cluster'} -c \$cl -t 28800 -i $R{'te'}{'srcroot'}; \
                   done",
                   "sleep 28800", 
                   "for cl in @{$R{'te'}{'clients'}} ; do \
                       $SSH $SSH_OPTIONS -l root \$cl rm -fR /mnt/test/emi-stresslogs/$R{'te'}{'cluster'}; \
                  done",  
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       $SSH $SSH_OPTIONS -l root \$cl mkdir -p /mnt/test/emi-stresslogs/$R{'te'}{'cluster'}; \ 
                       $SCP $SSH_OPTIONS /tmp/$R{'te'}{'cluster'}/stores.\$cl root\@\$cl:/mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/; \
                  done", 
                  "echo do retrieve and query", 
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/start_io.sh -o RTV \
                       -s $R{'te'}{'cluster'} -c \$cl -i $R{'te'}{'srcroot'} \
                       -f /mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/stores.\$cl; \
                   done",
                  "echo query all stored oids", 
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/start_io.sh -o QRY \
                       -s $R{'te'}{'cluster'} -c \$cl -i $R{'te'}{'srcroot'} \
                       -f /mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/stores.\$cl; \
                  done",
                  "sleep 3600", 
                  "echo Verify that stores, retrieves and queries are OK",
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       grep FAIL /tmp/$R{'te'}{'cluster'}/stores.\$cl | tail -10 ; \   
                       grep FAIL /tmp/$R{'te'}{'cluster'}/retrieves.\$cl | tail -10 ; \   
                       grep FAIL /tmp/$R{'te'}{'cluster'}/queries.\$cl  | tail -10 ; \
                   done", 
                  "echo Killing Java process on @{$R{'te'}{'clients'}} ",
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                      $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/stop_io.sh \$cl $R{'te'}{'srcroot'} ; \ 
                   done",
                   "echo Save all log files to dir /mnt/test/emi-stresslogs/$R{'te'}{'cluster'} in each client",
                   "for cl in @{$R{'te'}{'clients'}} ; do \
                       $SCP $SSH_OPTIONS /tmp/$R{'te'}{'cluster'}/*.\$cl root\@\$cl:/mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/ ; \
                       $SCP $SSH_OPTIONS /tmp/$R{'te'}{'cluster'}/*.\$cl.err root\@\$cl:/mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/ ; \
                   done",               
                  "echo parse the emi stress test log files on @{$R{'te'}{'clients'}} ",
                  "if [ -f /tmp/$R{'te'}{'cluster'}_OverniteLoad_analyze_perf.out ]; then \
                      rm /tmp/$R{'te'}{'cluster'}_OverniteLoad_analyze_perf.out; \
                  fi",
                  "touch /tmp/$R{'te'}{'cluster'}_OverniteLoad_analyze_perf.out",
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                      ssh \$cl -f -l root $SSH_OPTIONS /opt/test/bin/load/emi-load/analyze-perf.sh \
                      -f $R{'te'}{'cluster'}/stores.\$cl \
                      >> /tmp/$R{'te'}{'cluster'}_OverniteLoad_analyze_perf.out; \
                      sleep 120; \
                      ssh \$cl -f -l root $SSH_OPTIONS /opt/test/bin/load/emi-load/analyze-perf.sh \
                      -f $R{'te'}{'cluster'}/retrieves.\$cl \
                      >> /tmp/$R{'te'}{'cluster'}_OverniteLoad_analyze_perf.out; \
                      sleep 120; \
                      ssh \$cl -f -l root $SSH_OPTIONS /opt/test/bin/load/emi-load/analyze-perf.sh \
                      -f $R{'te'}{'cluster'}/queries.\$cl \
                      >> /tmp/$R{'te'}{'cluster'}_OverniteLoad_analyze_perf.out; \
                      sleep 120; \
                  done",
                  "cat /tmp/$R{'te'}{'cluster'}_OverniteLoad_analyze_perf.out",
                  "rm -fR /tmp/$R{'te'}{'cluster'}_OverniteLoad ",
                  "mv /tmp/$R{'te'}{'cluster'} /tmp/$R{'te'}{'cluster'}_OverniteLoad ",                 
                 ]
};

$tasks{"WeekendLoadTest"} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Running Weekend EMI Stress test from @{$R{'te'}{'clients'}} ", 
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Clients:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "echo do store", 
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/start_io.sh -o STR \
                       -s $R{'te'}{'cluster'} -c \$cl -t 28800 -i $R{'te'}{'srcroot'}; \
                   done",
                   "sleep 172800", 
                   "for cl in @{$R{'te'}{'clients'}} ; do \
                       $SSH $SSH_OPTIONS -l root \$cl rm -fR /mnt/test/emi-stresslogs/$R{'te'}{'cluster'}; \
                  done",  
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       $SSH $SSH_OPTIONS -l root \$cl mkdir -p /mnt/test/emi-stresslogs/$R{'te'}{'cluster'}; \ 
                       $SCP $SSH_OPTIONS /tmp/$R{'te'}{'cluster'}/stores.\$cl root\@\$cl:/mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/; \
                  done", 
                  "echo do retrieve and query", 
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/start_io.sh -o RTV \
                       -s $R{'te'}{'cluster'} -c \$cl -i $R{'te'}{'srcroot'} \
                       -f /mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/stores.\$cl; \
                   done",
                  "echo query all stored oids", 
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/start_io.sh -o QRY \
                       -s $R{'te'}{'cluster'} -c \$cl -i $R{'te'}{'srcroot'} \
                       -f /mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/stores.\$cl; \
                  done",
                  "sleep 3600", 
                  "echo Verify that stores, retrieves and queries are OK",
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                       grep FAIL /tmp/$R{'te'}{'cluster'}/stores.\$cl | tail -10 ; \   
                       grep FAIL /tmp/$R{'te'}{'cluster'}/retrieves.\$cl | tail -10 ; \   
                       grep FAIL /tmp/$R{'te'}{'cluster'}/queries.\$cl  | tail -10 ; \
                   done", 
                  "echo Killing Java process on @{$R{'te'}{'clients'}} ",
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                      $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/stop_io.sh \$cl $R{'te'}{'srcroot'} ; \ 
                   done",
                   "echo Save all log files to dir /mnt/test/emi-stresslogs/$R{'te'}{'cluster'} in each client",
                   "for cl in @{$R{'te'}{'clients'}} ; do \
                       $SCP $SSH_OPTIONS /tmp/$R{'te'}{'cluster'}/*.\$cl root\@\$cl:/mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/ ; \
                       $SCP $SSH_OPTIONS /tmp/$R{'te'}{'cluster'}/*.\$cl.err root\@\$cl:/mnt/test/emi-stresslogs/$R{'te'}{'cluster'}/ ; \
                   done",               
                  "echo parse the emi stress test log files on @{$R{'te'}{'clients'}} ",
                  "if [ -f /tmp/$R{'te'}{'cluster'}_WeekendLoad_analyze_perf.out ]; then \
                      rm /tmp/$R{'te'}{'cluster'}_WeekendLoad_analyze_perf.out; \
                  fi",
                  "touch /tmp/$R{'te'}{'cluster'}_WeekendLoad_analyze_perf.out",
                  "for cl in @{$R{'te'}{'clients'}} ; do \
                      ssh \$cl -f -l root $SSH_OPTIONS /opt/test/bin/load/emi-load/analyze-perf.sh \
                      -f $R{'te'}{'cluster'}/stores.\$cl \
                      >> /tmp/$R{'te'}{'cluster'}_WeekendLoad_analyze_perf.out; \
                      sleep 120; \
                      ssh \$cl -f -l root $SSH_OPTIONS /opt/test/bin/load/emi-load/analyze-perf.sh \
                      -f $R{'te'}{'cluster'}/retrieves.\$cl \
                      >> /tmp/$R{'te'}{'cluster'}_WeekendLoad_analyze_perf.out; \
                      sleep 120; \
                      ssh \$cl -f -l root $SSH_OPTIONS /opt/test/bin/load/emi-load/analyze-perf.sh \
                      -f $R{'te'}{'cluster'}/queries.\$cl \
                      >> /tmp/$R{'te'}{'cluster'}_WeekendLoad_analyze_perf.out; \
                      sleep 120; \
                  done",
                  "cat /tmp/$R{'te'}{'cluster'}_WeekendLoad_analyze_perf.out",
                  "rm -fR /tmp/$R{'te'}{'cluster'}_WeekendLoad",
                  "mv /tmp/$R{'te'}{'cluster'} /tmp/$R{'te'}{'cluster'}_WeekendLoad",
                 ]
};

$tasks{'SetupDevMode'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: pre condition required for CM tests", 
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
		  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS $R{'te'}{'COPY_SERVERS'} /export/SUNWhcwbcluster/ /export",
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS \"$R{'te'}{'DO_SERVERS'} \'/bin/yes | pkgadd -d /export SUNWhcwbcluster\'\"",
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS \"$R{'te'}{'DO_SERVERS'} touch /config/nohoneycomb /config/noreboot\"",
                  "echo UnClean Honeycomb Shutdown",
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS \"$R{'te'}{'DO_SERVERS'} \'pgrep java; if [ \$? -eq 0 ]; then pkill java; else echo 0; fi\'\"",
                  "sleep 600",
            ]
};

$tasks{'UnsetDevMode'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    commands => [ "echo INFO: post condition required for CM tests", 
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS \"$R{'te'}{'DO_SERVERS'} rm -f /config/nohoneycomb /config/noreboot\"",
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS \"$R{'te'}{'DO_SERVERS'} reboot; if [ \$? -ne 0 ]; then echo 0 ; fi\"",
                  "sleep 1800",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
		 ]
};
		 
$tasks{'CMTests'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "rodney.gomes\@Sun.COM",
    warning_email => "rodney.gomes\@Sun.COM",
    commands => [ "echo INFO: Running CM tests", 
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
		  "ssh $SSH_OPTIONS -l root ${$R{'te'}{'clients'}}[0] \"export PATH=$PATH ;/opt/test/bin/run_cmm_tests.sh -c $R{'te'}{'cluster'} -n $ENV{'NODES'} -i 1 -m FULL_HC\"",
                ]
};

$tasks{'HadbTests'} = { 
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@sun.com",
    warning_email => "sameer.mehta\@sun.com",
    commands => [ "echo INFO: Running HADB State Machine tests",
                  "echo INFO: Test completes in ~7-8 hours",
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Clients:     @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "for cl in @{$R{'te'}{'clients'}}; do \ 
                       $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/start_io.sh -o STR \
                       -s $R{'te'}{'cluster'} -c \$cl -t -1 -i $R{'te'}{'srcroot'}; \
                   done",
                  "echo Assumes that Whitebox package is already installed",
                  "echo verify that all nodes are online",
                  "$SSH $SSH_OPTIONS -l admin $R{'te'}{'cluster'}-admin hwstat | ".
                      "grep NODE | grep -ic online | egrep '8|16'",
                  "echo Start 1 iteration of each HADB test scenarios",
                  "$SSH $SSH_OPTIONS -l root $R{'te'}{'sp'} /opt/test/bin/runhadbscenarios.sh",
                  "echo Killing Java process on @{$R{'te'}{'clients'}}",
                  "for cl in @{$R{'te'}{'clients'}}; do \
                      $R{'te'}{'srcroot'}/tools/flamebox/src/scripts/stop_io.sh \$cl $R{'te'}{'srcroot'} ; \
                   done",
                ]
};

$tasks{'CAPI'} = { 
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "wilson.ross\@sun.com",
    warning_email => "wilson.ross\@sun.com",
    commands => [ "echo INFO: Running CAPI tests",
                  "echo INFO: Test completes in 12 hours",
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "cat $R{'te'}{'srcroot'}/server/src/config/metadata_config_merged.xml | 
                      ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS mdconfig -a",
                  "$R{'te'}{'srcroot'}/build/hctest/dist/bin/capi_regression.sh $R{'te'}{'srcroot'} $R{'te'}{'data'}", 
                ]
};

$tasks{'CLITests'} = { 
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com",
    warning_email => "jolly.kundu\@sun.com",
    commands => [ "echo INFO: Running CLI tests",
                  "echo INFO: CLI tests take 7-8 hours to run",
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
                  "echo *** Client:      ${$R{'te'}{'client'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh $SSH_OPTIONS ${$R{'te'}{'clients'}}[0] -l root \"export EXTRA_JVM_ARGS=\"-Dtestdir=cli\" ; export PATH=$PATH ; /opt/test/bin/runtest -ctx explore:include=cli:cluster=$R{'te'}{'cluster'} -ctx cellcfgips=10.7.226.121,10.7.226.122,10.7.226.120 -ctx nodes=$ENV{'NODES'} 2>\&1\"",      
                ]
};

$tasks{'AlertTests'} = { 
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "Jolly.Kundu\@Sun.COM",
    warning_email => "Jolly.Kundu\@Sun.COM",
    commands => [ "echo INFO: Running Alert tests",
                  "echo INFO: Alert tests take 11-12 hours to run",
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
                  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS /opt/test/bin/run_alert_tests.pl -a $R{'te'}{'admin'} -c $R{'te'}{'sp'} -m $R{'te'}{'cluster'}", 
                ]
};

$tasks{'DataPathRegressionTests'} = { 
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "wilson.ross\@sun.com", 
    warning_email => "wilson.ross\@sun.com",
    commands => [ "echo INFO: Running Data Path Regression tests",
                  "echo INFO: Run time 2 hours", 
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "echo *** Cluster:     $R{'te'}{'cluster'}",
                  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "echo *** Clients:     @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "cat $R{'te'}{'srcroot'}/server/src/config/metadata_config_merged.xml | 
                      ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS mdconfig -a",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/datapath_regression.sh $R{'te'}{'cluster'}\"",
                ]
};

$tasks{'WipeInLoop'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Running 10 iterations of Wipe",
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestWipe -ctx nodes=$ENV{'NODES'} -ctx iterations=10 -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{'ShutdownInLoop'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Running 10 iterations of shutdown",
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS /opt/test/bin/shutdowninloop.sh -a $R{'te'}{'admin'} -i 10", 
                ] 
};

$tasks{'RebootAllInLoop'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "jolly.kundu\@sun.com", 
    warning_email => "jolly.kundu\@sun.com", 
    commands => [ "echo INFO: Running 10 iterations of rebootall", 
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cli.CLITestReboot -ctx nodes=$ENV{'NODES'} -ctx iterations=10 -ctx cluster=$R{'te'}{'cluster'}\"",
                ] 
};

$tasks{'BasicSloshingTest'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@sun.com",
    warning_email => "sameer.mehta\@sun.com", 
    commands => [ "echo INFO: Running Basic Sloshing Test",
                  "echo INFO: Run Time 4-5 hours", 
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  # is cluster ok?
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
		  # Setup
		  # Special iso is made only for hc-flamebox which includes
		  # whitebox package in the ramdisk itself. 
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS pkginfo -l SUNWhcwbsp",
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS \"$R{'te'}{'DO_SERVERS'} pkginfo -l SUNWhcwbcluster\"",
                   
                  # Run the test
                  "echo INFO: Running BasicSloshing Test Case",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.BasicSloshing -ctx cluster=$R{'te'}{'cluster'} -ctx setupcluster=true -ctx createdata=true\"",
                ] 
}; 

$tasks{'SloshingWithLoadTest'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@sun.com",
    warning_email => "sameer.mehta\@sun.com", 
    commands => [ "echo INFO: Running Sloshing with Load Test",
                  "echo INFO: Run Time 4-5 hours", 
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",

                  # is cluster ok?
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",

		  # Setup
		  # Special iso is made only for hc-flamebox which includes
		  # whitebox package in the ramdisk itself. 
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS pkginfo -l SUNWhcwbsp",
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS \"$R{'te'}{'DO_SERVERS'} pkginfo -l SUNWhcwbcluster\"",
                   
                  # Run the test
                  "echo INFO: Running SloshingWithLoad Test Case",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.SloshingWithLoad -ctx cluster=$R{'te'}{'cluster'}:clients=${$R{'te'}{'clients'}}[0],${$R{'te'}{'clients'}}[1] -ctx setupcluster=true -ctx createdata=true\"",
                ] 
}; 

$tasks{'SloshFullCluster'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@sun.com",
    warning_email => "sameer.mehta\@sun.com", 
    commands => [ "echo INFO: Running Sloshing Test - fills an 8 node cluster to 78% and then expands it",
                  "echo INFO: Run Time 4-5 hours", 
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  # is cluster ok?
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
		  # Setup
		  # Special iso is made only for hc-flamebox which includes
		  # whitebox package in the ramdisk itself. 
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS pkginfo -l SUNWhcwbsp",
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS \"$R{'te'}{'DO_SERVERS'} pkginfo -l SUNWhcwbcluster\"",
                   
                  # Run the test
                  "echo INFO: Running SloshFullCluster Test Case",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.SloshFullCluster -ctx cluster=$R{'te'}{'cluster'}:setupcluster:createdata:deletedata:clients=${$R{'te'}{'clients'}}[0],${$R{'te'}{'clients'}}[1],${$R{'te'}{'clients'}}[2],${$R{'te'}{'clients'}}[3]\"",
                ] 
}; 

$tasks{'SloshFullClusterWithFailures'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@sun.com",
    warning_email => "sameer.mehta\@sun.com", 
    commands => [ "echo INFO: Running Sloshing Test while node and disk failures occur",
                  "echo INFO: Run Time 4-5 hours", 
	          "echo *** Source:      $R{'te'}{'srcroot'}", 
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  # is cluster ok?
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
		  # Setup
		  # Special iso is made only for hc-flamebox which includes
		  # whitebox package in the ramdisk itself. 
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS pkginfo -l SUNWhcwbsp",
                  "ssh $R{'te'}{'sp'} -l root $SSH_OPTIONS \"$R{'te'}{'DO_SERVERS'} pkginfo -l SUNWhcwbcluster\"",
                   
                  # Run the test
                  "echo INFO: Running SloshFullClusterWithFailures Test Case",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.SloshFullClusterWithFailures -ctx cluster=$R{'te'}{'cluster'}:setupcluster:createdata:deletedata:clients=${$R{'te'}{'clients'}}[0],${$R{'te'}{'clients'}}[1],${$R{'te'}{'clients'}}[2],${$R{'te'}{'clients'}}[3]\"",
                ] 
}; 

$tasks{'SdkRegressionTests'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "wilson.ross\@sun.com", 
    warning_email => "wilson.ross\@sun.com",
    commands => [ "echo INFO: Running SDK Regression tests",
                  "echo INFO: Run time 1-3 minutes", 
                  "echo INFO: Run against the emulator", 
                  "echo *** Source:    $R{'te'}{'srcroot'}",
                  "head -1 $R{'te'}{'srcroot'}/build/pkgdir/SUNWhcserver/root/opt/honeycomb/version",
                  "$R{'te'}{'srcroot'}/build/sdk/dist/emulator/bin/metadata_merge_config.sh $R{'te'}{'srcroot'}/build/sdk/dist/emulator/config/metadata_config_mp3demo.xml",
                  "$R{'te'}{'srcroot'}/build/sdk/dist/emulator/bin/start.sh \&", 
                  "$R{'te'}{'srcroot'}/build/hctest/dist/bin/sdk_regression.sh $R{'te'}{'srcroot'}",
                  "pkill -f emulator",
                ]
};

$tasks{'WebdavOfoto'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "wilson.ross\@Sun.COM", 
    warning_email => "wilson.ross\@Sun.COM", 
    commands => [ "echo INFO: Running Webdav Ofoto tests",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
                  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "echo *** Source:      $R{'te'}{'srcroot'}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",                  
                  "cat $R{'te'}{'srcroot'}/server/src/config/metadata_config_merged.xml | ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS mdconfig -a",
                  "$R{'te'}{'srcroot'}/hctest/src/scripts/launch_webdav_ofoto_reg.sh $R{'te'}{'srcroot'} $R{'te'}{'data'} $R{'te'}{'nodes'} ${$R{'te'}{'clients'}}[0] ${$R{'te'}{'clients'}}[1]",
                ]
};

$tasks{'NTPTests'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@sun.com",
    warning_email => "sameer.mehta\@sun.com",
    commands => [ "echo INFO: Run NTP functionality tests",
                  "echo INFO: Run time 6-7 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS /opt/test/bin/time_compliance_tests.sh $R{'te'}{'cluster'}",
                ]
};

$tasks{'NATTests'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@sun.com",
    warning_email => "sameer.mehta\@sun.com",
    commands => [ "echo INFO: Run NAT functionality tests",
                  "echo INFO: Run time 2-3 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS /opt/test/bin/nat_tests.sh $R{'te'}{'cluster'}",
                ]

};

$tasks{'AuthClientTests'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@Sun.COM",
    warning_email => "sameer.mehta\@Sun.COM",
    commands => [ "echo INFO: Run Authorized Clients tests",
                  "echo INFO: Run time 10 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.AuthorizedClients -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{'DistributedLoadSpreadingTest'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@Sun.COM",
    warning_email => "sameer.mehta\@Sun.COM",
    commands => [ "echo INFO: Run Distributed Load Spreading tests",
                  "echo INFO: Run time 8 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.DistributedLoadSpreading -ctx cluster=$R{'te'}{'cluster'}:clients=${$R{'te'}{'clients'}}[0],${$R{'te'}{'clients'}}[1],${$R{'te'}{'clients'}}[2],${$R{'te'}{'clients'}}[3]\"",
                ]
};

$tasks{'BasicBackupTest'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "rodney.gomes\@Sun.COM",
    warning_email => "rodney.gomes\@Sun.COM",
    commands => [ "echo INFO: Run Basic Backup test",
                  "echo INFO: Run time 8 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.BackupBasic -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{'BackupBadOrderRestoreTest'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "rodney.gomes\@Sun.COM",
    warning_email => "rodney.gomes\@Sun.COM",
    commands => [ "echo INFO: Run Basic Backup test",
                  "echo INFO: Run time 8 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.BackupBadOrderRestore -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{'BackupRestoreRetryTest'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "rodney.gomes\@Sun.COM",
    warning_email => "rodney.gomes\@Sun.COM",
    commands => [ "echo INFO: Run Basic Backup test",
                  "echo INFO: Run time 8 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.BackupRestoreRetry -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{'BackupBasicPerfSmallOpsTest'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "rodney.gomes\@Sun.COM",
    warning_email => "rodney.gomes\@Sun.COM",
    commands => [ "echo INFO: Run Basic Backup test",
                  "echo INFO: Run time 8 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.BackupBasicPerfSmallOps -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{'BackupBasicPerfBigOpsTest'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "rodney.gomes\@Sun.COM",
    warning_email => "rodney.gomes\@Sun.COM",
    commands => [ "echo INFO: Run Basic Backup test",
                  "echo INFO: Run time 8 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.BackupBasicPerfBigOps -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{'BackupBasicLoadDuringRestoreTest'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "rodney.gomes\@Sun.COM",
    warning_email => "rodney.gomes\@Sun.COM",
    commands => [ "echo INFO: Run Basic Backup test",
                  "echo INFO: Run time 8 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.BackupBasicLoadDuringRestore -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{'SwitchFailoverTests'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@Sun.COM",
    warning_email => "sameer.mehta\@Sun.COM",
    commands => [ "echo INFO: Run Switch Failover test",
                  "echo INFO: Run time 20 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.SwitchFailover -ctx cluster=$R{'te'}{'cluster'}:clients=${$R{'te'}{'clients'}}[0],${$R{'te'}{'clients'}}[1],${$R{'te'}{'clients'}}[2],${$R{'te'}{'clients'}}[3]\"",
                ]
};


$tasks{'SwitchTwoSuite'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@Sun.COM",
    warning_email => "sameer.mehta\@Sun.COM",
    commands => [ "echo INFO: Run SwitchTwo Test Suite",
                  "echo INFO: Run time 20 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.SwitchTwoSuite -ctx exclude=02,04 -ctx cluster=$R{'te'}{'cluster'}:clients=${$R{'te'}{'clients'}}[0],${$R{'te'}{'clients'}}[1],${$R{'te'}{'clients'}}[2],${$R{'te'}{'clients'}}[3]\"",
                ]
};

$tasks{'DNSTestSuite'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@Sun.COM",
    warning_email => "sameer.mehta\@Sun.COM",
    commands => [ "echo INFO: Run DNS test",
                  "echo INFO: Run time 16 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      @{$R{'te'}{'clients'}}",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/runtest com.sun.honeycomb.hctest.cases.DNSTestSuite -ctx cluster=$R{'te'}{'cluster'}\"",
                ]
};

$tasks{'IpmiTests'} = {
    repository => $repository,
    module => $module,
    branch => $branch,
    group => "flamebox",
    dir => "/",
    error_email => "sameer.mehta\@Sun.COM",
    warning_email => "sameer.mehta\@Sun.COM",
    commands => [ "echo INFO: Run IPMI test",
                  "echo INFO: Run time 4 hours",
		  "echo *** Cluster:     $R{'te'}{'cluster'}",
		  "echo *** Client:      ${$R{'te'}{'clients'}}[0]",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS sysstat | egrep '8|16'",
                  "ssh $R{'te'}{'admin'} -l admin $SSH_OPTIONS version",
                  "ssh ${$R{'te'}{'clients'}}[0] -l root $SSH_OPTIONS \"export PATH=$PATH ; /opt/test/bin/run_ipmi_tests.sh -c $R{'te'}{'cluster'} -n $ENV{'NODES'}\"",
                ]
};


##############################################################################
#
# Subroutines.

sub tasks_usage
{
    my ($usage) = <<"__EOF__";
    
SYNOPSIS:
    flamebox-client.pl [task options] -- [flamebox options] task [task]
  e.g.,    
    flamebox-client.pl --cluster devxxx --clients cl1,cl2 --nodes 8 
                       --srcroot WS --isourl URL --link link
                       -- [flamebox options] task [task]

OPTIONS:

     --cluster          Name of your cluster (eg: --cluster dev312)
     --clients          Comma-separated list of client machine IPs or
                            hostnames (eg: --clients cl1,cl2)
     --nodes            Number of nodes in the cluster (e.g., --nodes 8)
     --srcroot          Absolute path of ws (optional), default is
                            set to /export/home/build/svn/trunk
     --isourl           URL of iso image (optional), default is set to
                            http://10.7.224.9/~build/honeycombdvd.iso
     --link             Absolute path of iso image and jar file on hc-flamebox (optional), 
                            default is set to /export/home/build/public_html
__EOF__
;

    print $usage;
    exit 0;
}

sub parse_tasks_args
{
    my %option_linkage = (
		       "cluster" => \$cluster,
		       "clients" => \$clients,
		       "nodes" => \$nodes,
                       "srcroot" => \$srcroot,
                       "isourl" => \$isourl,
                       "link" => \$link,
		       );
    
    if( !GetOptions (\%option_linkage,
                     "cluster=s",
		     "clients=s",
		     "nodes=s",
                     "srcroot=s",
                     "isourl=s",
		     "link=s",
		     )) {	
	print("Illegal options in \@ARGV: '@ARGV'\n");
	tasks_usage();
	exit 1 ;
    }
}
