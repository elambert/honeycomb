#! /usr/bin/perl

#
# $Id: log_scraper.pl 11518 2007-09-20 05:19:31Z ks202890 $
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

use Time::localtime;
use Time::Local;
use Getopt::Std;

#
#
# Format of the Honeycomb log trace
#
# <week_day> <date> <hour:min:sec> <hostname> <prog:> <TRACE_LEVEL> <{Module.func]> <(.)> <msg>

# Hash table for input arguments.
my %opts = {};

my $init_date = 0;
my $end_date  = 4294967295;
my $date_today = `date "+%m-%d-%y:%H:%M:%S"`;
chomp $date_today;
my $cur_date = localtime;
my $all = "false";
my $stdout = \*STDOUT;
my $stderr = \*STDERR;
my $time_for_run = 0;

my $output_log_dir;
#verbose
my $verbosity = 0;

# Default level is 0. 
my $logging_level = -1;

# List of log files we scrub
my @log_files =();

# Number of latest lines per module to be printed, usually twice this get's dumped.
# This makes summary file like 3MB.
my $num_latest_msg_sw = 100;
my $num_latest_msg_info = 25;

# If a user specifies a set of modules for which he wants logs.
my @modules_of_interest = ();

my $output_base = "/var/adm";

my %host_file_handle = ();

my $start_host = "hcb101";
my $end_host = "hcb116";
# List of all types of messages the scraper looks for.
my @msg_type = ("local0.error", "local1.error","local0.warning", "local0.info",
                "local1.info", "user.notice", "user.info", "user.error",
                "user.warning", "kern.info", "kern.notice");

# The list of hard coded messages that are the top priority messages 
# we are interested the most. These are partial strings which will be
# matched with the actual log messages.
my @hot_message_list=("Failed",
                      "Abort",
                      "exception",
                      "not ok",
                      "unsuccess",
                      "corrupt",
                      "Failed to change the state of the system to corrupted",
                      "wipe",
                      "EXT_SEV",
                      "EXT_WARN",
                      #switch
                      "Failed to configure admin VIP",
                      "Couldn't talk to switch",
                      "Gratuitous sendarp failed",
                      "StatusManager.update failed",
                      "Switch failover",
                      "Both interfaces",
                      "clearing all rules",
                      "Switch rules not programmed properly",
                      "Switch programming",
                      "rebooted by root",
                      #Disk
                      "Couldn't initialize disks",
                      "Won't touch foreign disk",
                      "won't be checked/mounted",
                      "writing new partition table",
                      "absent on disk",
                      "Making new filesystem on",
                      "processOnline: no disk label after replacement processing",
                      "Wipe failed to enable disk",
                      "Refusing to enable Disk with label",
                      "Couldn't initialize",
                      "Couldn't online mirrors for disk",
                      "Couldn't offline mirrors for disk",
                      "Couldn't prepare disk",
                      "not our disk",
                      "CLUSTER WIPE",
                      "couldn't do offline processing on disk",
                      "No serial number for device",
                      "usage now above cap for disk",
                      "is in the wrong slot",
                      "New disk detected",
                      "REPLACEMENT DETECTION",
                      "newdisk",
                      "Error: cannot find",
                      "MDB OUTPUT",
                      "node_verification: some disks inconsistent",
                      "Detected kernel error on disk",
                      "sata error",
                      "failed to import handles for",
                      "Unable to backup/restore system cache for",
                      #HADB
                      "Failed to create the proper Metadata tables from the attributes table",
                      "Failed to start HADB",
                      "Timer expired; wipe and restart",
                      "Failed to reset Hadbm state! Continuing to wipe",
                      "Attempting to stop database",
                      "HADBM State:",
                      "Hadbm.logProgress",
                      "Forced HADB shutdown",
                      "Wipe and restart of node",
                      "SUNhadb path corrupt or missing",
                      "Database is non-operational",
                      "Database upgrade failed",
                      "Couldn't get JDBC URL",
                      "HADB has been non-operational for x seconds",
                      "Trying to recover database",
                      "Wiping and restarting MA on node",
                      "HADB-", # Hadb's internal logs
                      "NSUP Network Partition detected",
                      #done
                      #CM
                      "NODE HAS TOO MANY FAILURES",
                      "STOP TIMEOUT - some services still running",
                      "BAD VERSION - ATTEMPTING POWEROFF",
                      "Unsupported encoding type",
                      "heartbeat",
                      "NodeMgr detected lost of quorum",
                      "Waiting for quorum",
                      "Reached quorum, exit maintenance mode",
                      "Lost Quorum",
                      "SunOS",
                      "HONEYCOMB IS STARTING",
                      "STARTING MASTER SERVICES",
                      "STARTING API ACCESS SERVICES",
                      "STARTING DATA SERVICES",
                      "died",
                      "failed to get Multicell API",
                      "failed to push multicell init config",
                      "Unable to wipe system cache",
                      "Failure in system cache state machine",
                      "failed to populate System cache with file",
                      "couldn't configure data VIP",
                      "Couldn't run interface configuration script",
                      "DD rmi call failed",
                      );

# perf message hash has counters 
my %perf_messages =( "MEAS query",0,
                     "MEAS addmd",0,
                     "MEAS getschema",0,
                     "MEAS store_md",0,
                     "MEAS store_b",0,
                     "MEAS getmd",0,
                     "MEAS retrieve",0,
                     "MEAS delete",0,
                   );

# These should be ignored from the hot message list.
my @ignore_message_list = ( "addRowPreparedStatement",
                    "Failed to open frag",
                    "insert",
                    "Failed fast BOTROS read, trying normal:",
                    "RemoveTempFrags");


# The following are the array's which tells which are modules
# for which we print all the 'Info' level logs. Default is level 0, which
# uses the user specified modules on cli.

# Level 0 is unused as at default we dont want module wise output
# it is used if a user specifies specific  modules as command line.
@modules_for_level0 = ();

# Level 1 are the most important modules for which we need the Info messages.
# The Info messsages for these are useful for debugging.
@modules_for_level1 = (
                      "AncillaryServer",
                      "CfgUpdUtil.info",
                      "ClusterProperties.reload",
                      "ConfigurationAttributeSetImpl.setAttributesOnServer",
                      "DataDoctor.shutdown",
                      "DataDoctor.run",
                      "DiskLabel",
                      "DiskMonitor.checkUsage",
                      "DiskMonitor.disable",
                      "DiskMonitor.disksInit",
                      "DiskMonitor.ledCtl",
                      "DiskMonitor.makeDiskObj",
                      "DiskMonitor.newDisk",
                      "DiskMonitor.shutdown",
                      "DiskMonitor.unmountAll",
                      "DiskMonitor.checkExports",
                      "DiskOps.fsck",                      
                      "DiskOps.processReplacedDisks",
                      "FragmentFile.remove",
                      "GetHandler",
                      "HADBJdbc.isRunning",
                      "HAPlatform",
                      "HCUserRealm.setPassword",
                      "HadbService.\<init\>",
                      "HadbService.run",
                      "HadbService.shutdown",
                      "HadbService.startMA",
                      "HadbService.wipeAndRestartForAll",
                      "Hadbm.\<init\>",
                      "Hadbm.createDb",
                      "Hadbm.createDomain",
                      "Hadbm.getDb",
                      "Hadbm.getDomain",
                      "Hadbm.getDomainConfig",
                      "Hadbm.logProgress",
                      "Hadbm.runStateMachine",
                      "Hadbm.waitForHadb",
                      "Hadbm.wipeAndRestartAll",
                      "HcNfsDAAL",
                      "HttpClient.getHttp",
                      "JVMProcess",
                      "KernelListener",
                      "Kstat",
                      "LayoutService.\<init\>",
                      "LayoutService.logDiffs",
                      "LayoutService.manageDisks",
                      "LayoutService.run",
                      "LayoutService.shutdown",
                      "LobbyTask.processDiscovery",
                      "LobbyTask.processElection",
                      "LobbyTask.processUpdateOnMasterOnly",
                      "MDServer.\<init\>",
                      "MDServer.stopServer",
                      "MDService.shutdown",
                      "MainHandler",
                      "ManagementDomainImpl",
                      "MgmtServerBase",
                      "MultiCellBase",
                      "NfsManager",
                      "NodeMgr.main",
                      "NodeMgr.monitor",
                      "NodeMgr.shutdown",
                      "NodeMgr.stopJVMs",
                      "NodeMgr.stopServices",
                      "NodeMgr.update",
                      "NodeMgr.waitForMaster",
                      "NodeMonitor",
                      "OAServer",
                      "OAThreadPool",
                      "OAThreads",
                      "QuorumThread",
                      "Service.disable",
                      "Service.invoke",
                      "Service.monitor",
                      "Service.uncaughtException",
                      "SoftwareVersion.checkVersionMatch",
                      "SoftwareVersion.getConfigVersion",
                      "SoftwareVersion.getRunningVersion",
                      "SpreaderService",
                      "StatFS",
                      "SwitchPorts",
                      "SwitchRules",
                      "SwitchStatusManager",
                      "SysCache.startupSystemCache",
                      "SysStat",
                      "Time.syncRun",
                      "TimeManagedService",
                      "VIPManager",
                      "XMLParser"
                      );

# Level 2 are the second most important modules for which we need the Info messages.
@modules_for_level2 = (
                      "DAVServer", 
                      "DiskInitialize",
                      "DiskMonitor.logStats",
                      "DiskMonitor.run",
                      "EMDClient",
                      "GenericConenctor.RequestHandler-connectionException",
                      "HadbService.rmHadbDir",
                      "Hadbm.connectToSpecificMA",
                      "Hadbm.logTimeLeft",
                      "LinkedHashMapCache.init",
                      "LobbyTask.processConfigChange",
                      "LobbyTask.setOffice",
                      "LobbyTask.setStatusOnCommitAndSendNext",
                      "LobbyTask.work",
                      "MDServer.run",
                      "MDService.run",
                      "Mailbox.stateCheck",
                      "MasterService",
                      "Mboxd",
                      "MultiCellLibBase",
                      "MultiCellLogger",
                      "NDMPService",
                      "NodeMgr.run",
                      "Profiler",
                      "Service.shutdown",
                      "ServiceProcessor.isSPAlive",
                      "Switch.getSwitchValue",
                      "TaskLogger",
                      "ThreadPool",
                      "Upgrader",
                      );

# Level 3 -- More chatty messages.
@modules_for_level3 = (
                      "Exec.exec",
                      "Exec.execRead",
                      "FSCache.initialize",
                      "FragmentFileSet.open",
                      "FragmentFileSet.read",
                      "HCFile.addChildren",
                      "HCFile.refreshChildren",
                      "LayoutService.gracePeriod",
                      "MAConnectionFactory.getMAConnection",
                      "MultiPortSocketListener",
                      "NfsAccess",
                      "NodeMgr.getStoppableServices",
                      "OAClient.crawlForAndOpenFset",
                      "OAClient.findAndOpenFset",
                      "ReceiverTask",
                      "SenderTask",
                      "ServiceMailbox.monitor",
                      "SysCache.run",
                      "SysCache.updateInitState",
                      "SysCache.updateStateAndInit",
                      "Time.isNtpRunning",	
                      );

# Level 4 -- More More chatty messages.
@modules_for_level4 = (
                      "FlatByteBufferPool.purge",
                      "FragmentFileSet.create",
                      "Fragmenter.readAndDefragment", 
                      "NodeMgr.idle",
                      "OAClient.getConfig",
                      "OAClient.open",
                      "OAClient.recover",
                      "OAClient.renameFragments",
                      "ProcessingCenter",
                      "ProtocolBase",
                      "ProtocolProxy",
                      "ProtocolService",
                      "TaskListElem",
                      );

# Modules_table keeps the information about modules along with their
# various counts, statistics. this is just a template.
%modules_table=(
                header => { msg_sw => [],
                            msg_tmp_sw => [],
                            msg_info => [],
                            msg_tmp_info => [],
                            info_count => 0,
                            warning_count => 0,
                            severe_count => 0
                          }     
               );

# keep host specific counts. Just a template
%host_specific = ();
#my %host_specific = (
#                    "hcb101" => { info_count => 0,
#                                warning_count => 0,
#                                severe_count => 0,
#                                notice_count => 0
#                              }
#                 );

#
# Usage
#
sub usage
{
    print << "EOF";
  usage:
    -s <start_date ex: Apr:12:13:23:12 >
    -e <end_date ex:Apr:13:13:23:12 >
    -o <output_directory>
    -a <true|false>
    -l <log_level[0-4]>
    -n <num_msg_per_module>
    -d <num_of_days>
    -m <module_list, comma seperated>
    -v <true|false verbosity>
    -L <Absolute path to messages file>
EOF
     exit 1;
}

my $total_num_days = 0;
my $only_summaries = 1;

#
# Init
# - Parse arguments
# - Init hash table for log levels
#
sub init
{
    my $opt = "s:e:o:l:a:d:m:n:c:v:L:";
    getopts("$opt", \%opts) or usage();

    if (($opts{d} ne "") && 
        ($opts{s} ne "" || $opts{e} ne "")) {
        warn "Specify either the number of days or date range, not both\n",
        exit 1;
    } elsif ($opts{d} ne "") {
        $total_num_days = $opts{d};
        $end_date = time();
        $init_date = time - (86400 * $total_num_days);
    }

    if ($opts{s} ne "") {
        $init_date = convert_date(parse_arg_date($opts{s}));
    }

    if ($opts{e} ne "") {
        $end_date = convert_date(parse_arg_date($opts{e}));
    }
     
    # if both start and end date specified ensure start > end 
    if (($opts{s} ne "") && ($opts{e} ne "")) {
        if ($init_date > $end_date) {
           die "Start date $opts{s} is greater than end date $opts{e}";
        }
    }

    if ($opts{o} ne "") {
        $output_log_dir = "$opts{o}"."/"."$date_today";
        mkdir("$output_log_dir", 0777) || 
           die "failed to create dir $output_log_dir";
    } else {
        if ( ! -e "$output_base/scraper" ) {
           mkdir("$output_base/scraper/", 0777) || die "failed to create file $output_base";
        } else {
            print "Older logs at $output_base/scraper.old\n";
            `rm -rf  $output_base/scraper.old`;
            my $status = `mv $output_base/scraper $output_base/scraper.old`;
            if ($status != 0) {
               die "Failed to rename $output_base/scraper to $output_base/scraper.old";
            } 
            #clean up the older stuff.
            $status=`mkdir $output_base/scraper`;
            if ( $status != 0 ) {
               die "mkdir of $output_base/scraper Failed with $status\n";
            }
        }
        mkdir("$output_base/scraper/$date_today", 0777) || 
            die "failed to create dir $output_base/scraper/$date_today";
        $output_log_dir = "$output_base/scraper/$date_today";
    }

    if ($opts{l} ne "") {
        $logging_level = $opts{l};
        if ($logging_level > 4) {
           die "Logging level should be between 0-4\n";
        }
        if ($logging_level >= 0) {
            $only_summaries = 0
        }
    } else {
        $logging_level = 0;
    }

    if ($opts{L} ne "") {
        @log_files = split /\s+/,$opts{L};
        print "User supplied log files: @log_files\n";
        foreach (@log_files) {
           if (/\.gz/) {
               die "Please pass a valid message file, don't pass compressed gz files";
           }
        }
    } else {
        # We could pick up all the unzipped files for scraping but that 
        # will take a long while, so commenting that for now.
        # @log_files=`ls $output_base/messages* | grep -v gz | sort -r`;
        @log_files=("/var/adm/messages", "/var/adm/messages.0");
        print "default log files: @log_files\n";
    }

    foreach (@log_files) {
       $time_for_run += 10; # assume 10 mins for each file
    }

    if ($opts{a} eq "true") {
        $all = "true";
    } else {
        $all = "false";
    }
    if ($opts{m} ne "") {
       my @tmp_array = split/,/,$opts{m};
       for (@tmp_array) {
           s/^\s+|\s+$//g;
           push @modules_for_level0, $_;
       }
       LOGGER($stdout, "Specified modules for search as:  @modules_for_level0\n", 0);
    }

    if ($opts{n} ne "") {
        $num_latest_msg_info = $opts{n};
    }

    if ($opts{v} eq "true") {
        $verbosity = 1;
    } else {
        $verbosity = 0;
    }

    # Initialize the global counters
    for my $i (sort @msg_type) {
        ${total."$i"} = 0;
    }

    # Open all the output files if we need more than summaries.
    if (! $only_summaries ) {
        open_output_files();
    }
    open HOT_MESSAGE_FH, "> $output_log_dir/hot_message_file" ||
        die "Failed to open $output_log_dir/hot_message_file";

    LOGGER($stdout, "Done initializing...\n", 0);

    # Check if minimal space is available, if not bail early.
    check_space();
}

# Basically check if there is minimal space available in the 
# output directory. Atleast 2G available for default run of this tool.
# The tool ouput is expected to be upto 10M max.
# Higher Level of logging will produce more output and that should
# not exceed a gig worth of info, so 2G is good limit.
# For option 'ALL' we do a seperate check.
sub check_space() 
{
    LOGGER($stdout, "checking space needed...\n", 0);
    # check if free space is available to tar and gzip these.
    my $x=`df -b $output_log_dir | grep -vi avail`;
    my @y=split /\s+/,$x;
    my $total_space_avail=($y[1] * 1024);
    if ($total_space_avail < (2*( 2 ** 30))) {
       warn "Minimum of 2G free space needed in $output_log_dir\n";
       exit 1;
    }
    LOGGER($stdout, "got space needed...\n", 0);
}

# For now just close the open files.
sub finish 
{
    # close all the output files here.
    close HOT_MESSAGE_FH;
    if (! $only_summaries ) {
        close_output_files();
    }
    LOGGER($stdout, "done running. Log files at $output_log_dir.\n", 0);
}

#
# Parse argument date
#
# Ex: Apr:12:13:23:12 -> Apr 12 13:23:12
#
sub parse_arg_date
{
    if ($_[0] =~ /^(\w+):(\d+):(\d+):(\d+):(\d+)/) {
        return "$1 $2 $3:$4:$5";
    } else {
        die "Invalid date pattern specified: @_";
    }
}

my %month_to_index =("Jan", 0, "Feb", 1, "Mar", 2, "April", 3, "May", 4, "Jun", 5, "Jul", 6,
                     "Aug", 7, "Sep", 8, "Oct", 9, "Nov", 10, "Dec", 11);

# 
# Convert date to epoch
#
sub convert_date
{
    my $month;
    my $year;
    $_ = $_[0];
    if (/^\s*(\w+)\s+(\d+)\s+(\d+):(\d+):(\d+)/) {
        #
        # There is no 'year' in syslog.
        # If the month specified is the current month or before,
        # we assume this is the current year, otherwise this is for
        # for the year before, since we don't travel in the future yet...
        #
        $month = $month_to_index{$1};
        if ($month <= $cur_date->mon) {
            $year = $cur_date->year;
        } else {
            $year = $cur_date->year - 1;
        }
        my $epoch = timelocal($5, $4, $3, $2, $month, $year);
        return $epoch;
    } else {
        exit 1;
    }
}

# Logs to respective files at normal or debug message.
# Input param {output_handle, message, normal|debug}
sub LOGGER {
    my $handle = shift @_;
    my $message = shift @_;
    my $debug = shift @_;
    if (!defined($handle)) {
       #warn "Skipping..$message";
       return;
    }
    if ($debug == 0 ) {
        print $handle $message;
        return;
    }
    if ($debug == 1 && $verbosity == 1) {
       print $stdout $message || die "Failed to print:: @_";
    } else {
       return;
    }
}

# Hot message file handle
\*HOT_MESSAGE_FH;

# Assume 16 hosts.
sub open_output_files {
    my $i;
    my $j;
    for  $i ($start_host..$end_host) {
       for  $j (sort @msg_type) {
           $host_file_handle{$i}{$j} = *{$i.$j};
           open $host_file_handle{$i}{$j}, "> $output_log_dir/$i.$j" ||
               die "Failed to open output file $output_log_dir/$i.$j";
       }
    }
}

# close all the open files.
sub close_output_files {
    for my $i ($start_host..$end_host) {
       for my $j (sort @msg_type) {
           close $host_file_handle{$i}{$j};
       }
    }
    # delete empty files
    opendir(DIR, $output_log_dir) or die "can't opendir $dirname: $!";
    while (defined($file = readdir(DIR))) {
        if (-z "$output_log_dir/$file") {
           unlink("$output_log_dir/$file");
        }
    }
    closedir(DIR);
}

# Match the predetermined strings in the log messages and dump it to file
sub process_important_messages {
    my $m = shift @_;
    for (@ignore_message_list) {
        if ($m =~/$_/i) {
            return
        }
    }
    for (@hot_message_list) {
        # If the string is present in the line
        if ( $m =~ /$_/i ) {
            LOGGER($stdout, "process_important_messages: $m", 1); 
            LOGGER(HOT_MESSAGE_FH, $m, 0);
        }
    }
    process_performance_messages($m);
}

# count up the perf related messages for a summary.
sub process_performance_messages {
    my $m = shift @_;
    my $key ="";
    my $value = 0;
    while (($key,$value) = each (%perf_messages)) {
       if ($m =~ /$key/i) {
          $perf_messages{$key}++;
          return;
       }
    }
}

# Takes the line of log and loglevel and writes it to the corresponding
# logfile.
sub process_log_line {
    my ($loglevel, $line, $nodename, $module_fqn) = @_;
    my $logging_ctr = $logging_level;
    my $x;
    my $found_match=0;
    LOGGER($stdout, "process_log_line :$loglevel,  $nodename, $module_fqn\n", 1);
    LOGGER($stdout, "process_log_line : $init_date $end_date\n", 1);

    # All error,severe,warning messages are scrubbed.
    if ($loglevel =~ /(error|severe|warning)/i) {
        LOGGER($stdout, "process_log_line :Found a message $loglevel\n", 1);
        LOGGER($host_file_handle{$nodename}{$loglevel}, "$line", 0);
        return;
    }
    while ($logging_ctr >= 0) {
        foreach $x (@{modules_for_level."$logging_ctr"}) {
            if (uc($x) eq uc($module_fqn)) {
               $found_match=1;
               last;        
            } else {
                 # Break the module_fqn and check if the lower part
                 # matches an entry in the table.
                 # For example we accept all "DiskInitialize" 
                 # logs. So it should accept DiskInitialize$xxx.$yyyy messages.
                 # or DiskInitialize.run.
                 my $t=qq($module_fqn);
                 my @a = split /\$|\./, $t;
                 for my $tt (@a) {
                    if (uc($x) eq uc($a[0])) {
                       $found_match=1;
                       last;
                    }
                 }
            }
        }
        if ($found_match == 1) {
            LOGGER($stdout, "process_log_line:This line is logged as $loglevel\n", 1);
            LOGGER($host_file_handle{$nodename}{$loglevel}, "$line", 0);
            last;
        }
        $logging_ctr--;
    }
}

# Go through the log files which are uncompressed and scrape them for
# interesting messages.
sub process_log_files
{
    # Just match the timestamps to reject logs outside time stamp range needed.
    my $pattern0 = qw(^(\w+\s+\d+\s+\d+:\d+:\d+)\s+hcb.*);


    # Jun  8 23:01:00 hcb102 swap[1028]: [ID 545942 user.warning] libdiskmgt: sysevent thread for cache events failed to start
    my $pattern1 = qw(^(\w+\s+\d+\s+\d+:\d+:\d+)\s+(hcb\d+)\s+\w\[\d+\]\:\s\[ID\s+\d+\s+(.*?)\]);

    # Honeycomb messages like
    # Jun  8 19:20:38 hcb102 java: [ID 702911 local0.info] 810 INFO [MAConnectionFactory.getMAConnection] (2216.3)  at com.sun.hadb.adminapi.MAConnection.<init>(MAConnection.java:513)

    my $pattern2 = qw(^(\w+\s+\d+\s+\d+:\d+:\d+)\s+(hcb\d+).*?\[.*?\s+\d+\s+(.*?\..*?)\].*?\[(.*?)\](.*));


    # Jun 11 18:17:42 hcb101 root: [ID 702911 user.notice] Disk sata1/1 is in configured s
    my $pattern3 = qw(^(\w+\s+\d+\s+\d+:\d+:\d+)\s+(hcb\d+)\s+(.*?\w+)\:\s+\[ID\s+\d+\s+(.*?)\](.*?));

    foreach (@log_files) {
        LOGGER($stdout, "Processing file : $_ \n", 0);
        if (!open(FILE, "< $_")) {
           warn "Cannot open log file $_";
           next;
        }
        while (<FILE>) {
            my $line = $_;
            my $loglevel;
            my $module_fqn;
            my $message;
            my $nodename;
            my $x;
            my $i;
            # Skip lines out of the time range we want.
            if (/$pattern0/i) {
                my $epoch_time = convert_date($1);
                $_ = $line;
                next if (!($epoch_time >= $init_date && $epoch_time <= $end_date));
            }

            process_important_messages($line);

            # Some solaris messages are of form time host [deamon_name] [ID....]
            # So better to match it seperately.
            if (/$pattern1/i) {
                LOGGER($stdout, "pattern1 : $_ \n", 1);
                $nodename = $2;
                $loglevel = $4;
                ${total."$loglevel"}++;
                if (! exists $host_specific{$nodename}) {
                    $host_specific{$nodename}{$loglevel} = 0;
                    for  $x (sort @msg_type) {
                        $host_specific{$nodename}{$x} = 0;
                    }
                }
                $host_specific{$nodename}{$loglevel}++;
                # Don't write out for only summary case.
                if (! $only_summaries ) {
                    # print "Pattern1 $loglevel:: $nodename\n";
                    LOGGER($host_file_handle{$nodename}{$loglevel}, "$line", 0);
                }
                next;
            } elsif (/$pattern2/i || /$pattern3/i) { 
               LOGGER($stdout, "pattern2 : $_ \n", 1);
               if (/$pattern2/i) {
                   #$1=timestamp,$2=hostname,$3=loglevel,$4=module_fqn,$5=message
                   $nodename = $2;
                   $loglevel = $3;
                   $module_fqn = $4;
                   $message = $5;
               } elsif (/$pattern3/i) {
                   #$1=timestamp,$2=hostname,$3=module_fqn,$4=log_level,$5=message
                   $nodename = $2;
                   $loglevel = $4;
                   $module_fqn = $3;
                   $message = $5;
               }
               LOGGER($stdout, "process_log_file:$module_fqn :: $message\n", 1); 
               # Initialize all the counters here.
                if (! exists $modules_table{$module_fqn}) {
                    $modules_table{$module_fqn}{$loglevel} = 0;
                    for $x (sort @msg_type) {
                        $modules_table{$module_fqn}{$x} = 0;
                    }
                }
                if (! exists $host_specific{$nodename}) {
                    $host_specific{$nodename}{$loglevel} = 0;
                    for  $x (sort @msg_type) {
                        $host_specific{$nodename}{$x} = 0;
                    }
                }
                # Use msg_tmp to store messages and when it reaches count num_latest_msg_sw
                # copy it to msg and delete msg_tmp. Simple log rotation.
                if ($loglevel =~ /(error|severe|warning)/i) {
                    &log_rotate("msg_sw", "msg_tmp_sw", $num_latest_msg_sw, $module_fqn, $line);
                    LOGGER($stdout, "process_log_file:Rotate severe/warning messages\n", 1); 
                } else { # Other messages are treated as Info.
                    &log_rotate("msg_info", "msg_tmp_info", $num_latest_msg_info, $module_fqn, $line);
                    LOGGER($stdout, "process_log_file:Rotate non severe-warning messages\n", 1); 
                }

                # Dont process each line if we want only summaries.
                if (! $only_summaries ) {
                    LOGGER($stdout, "process_log_file:NON sumamry messages\n", 1); 
                    process_log_line($loglevel, $line, $nodename, $module_fqn);
                } 
                $modules_table{$module_fqn}{$loglevel}++;
                $host_specific{$nodename}{$loglevel}++;
                ${total."$loglevel"}++;
            } else {
                #skipped
                # Messages like daemon.*, SP come here
                # print $_;
                LOGGER($stdout, "Skipped $_", 1);
                next;
            }
            LOGGER($stdout, "Totals:  ${total.\"$loglevel\"} $loglevel\n", 1);
       }
       close FILE;
    }
}

# Append to the tmp message and copy it to the main msg array if full.
sub log_rotate
{
    my $type = shift @_;
    my $tmp_type = shift @_;
    my $num_latest_msg = shift @_;
    my $module_fqn = shift @_;
    my $line = shift @_;
    # Use msg_tmp to store messages and when it reaches count num_latest_msg_sw
    # copy it to msg and delete msg_tmp. Simple log rotation.
    push @{$modules_table{$module_fqn}{$tmp_type}}, "$line";
    my $msg_tmp_ref = \@{$modules_table{$module_fqn}{$tmp_type}};
    LOGGER($stdout, "***" .  @$msg_tmp_ref . " \n", 1);
    if (@$msg_tmp_ref == $num_latest_msg) {
        @{$modules_table{$module_fqn}{$type}} = 
        @{$modules_table{$module_fqn}{$tmp_type}};
        LOGGER($stdout, "Copying @{$modules_table{$module_fqn}{$type}} \n", 1);
        delete $modules_table{$module_fqn}{$tmp_type};
    }
}

# Go through the modules_table hash and print the summaries
# and last few messages from each module.
sub process_summaries 
{
    my $i;
    my $summary_file = "$output_log_dir/summary"; 
    open(SUMMARY, "> $summary_file") || die "failed to open Summary file";
    LOGGER(SUMMARY, "Log summary of files :", 0);
    chomp @log_files ; foreach my $y (@log_files) { chomp $y ; LOGGER(SUMMARY, "$y ", 0);}
    LOGGER(SUMMARY,  "\n", 0);
    LOGGER(SUMMARY,  "Message type \t count\n", 0);
    LOGGER(SUMMARY,  "===============================\n", 0);
    for $i (sort @msg_type) {
        if (${total."$i"} != 0) {
            LOGGER(SUMMARY, "$i:\t ${total.\"$i\"} \n", 0);
        }
    }
    LOGGER(SUMMARY, "\n",0);
    

    LOGGER(SUMMARY,  "====================================================================\n", 0);
    LOGGER(SUMMARY,  "The following messages lists the module name, type of message and count.\n", 0);
    LOGGER(SUMMARY,  "sw = Severe|Warning messages, Info = Non (severe|warning) message.\n", 0);
    LOGGER(SUMMARY,  "Please look at modules with high error count from summary_header file\n", 0);
    LOGGER(SUMMARY,  "====================================================================\n\n", 0);
    for my $mod (sort keys %modules_table) {
        if ($mod eq "header") {
            next;
        }
        for $i (sort @msg_type) {
            if ($modules_table{$mod}{$i} != 0) {
                LOGGER(SUMMARY, "[$mod] $i : $modules_table{$mod}{$i}\n", 0);
            }
        }
        LOGGER(SUMMARY, "\n", 0);
        # Print the severe/warning buffer
        &print_message_buffer("msg_sw", "msg_tmp_sw", SUMMARY, $mod);
        # Print the info buffer 
        &print_message_buffer("msg_info", "msg_tmp_info", SUMMARY, $mod);
        LOGGER(SUMMARY,  "===============================================================\n\n", 0);
    }
    close SUMMARY;
}

sub print_message_buffer
{
    my $type = shift @_;
    my $tmp_type = shift @_;
    my $fh = shift @_;
    my $mod = shift @_;
    if ($mod eq "") {
       return;
    }
    my $msg_ref = \@{$modules_table{$mod}{$type}};
    my $msg_tmp_ref = \@{$modules_table{$mod}{$tmp_type}};
    my $cnt_msg = @{$msg_ref};
    my $cnt_msg_tmp = @{$msg_tmp_ref};
    if ( $cnt_msg > 0 || $cnt_msg_tmp > 0 ) {
        LOGGER($fh, "Interesting few 50 - 100 log messages of $type for [$mod]\n",0);
        LOGGER($fh, "---------------------------------------------------------------------\n",0);
        LOGGER($stdout, "$mod tmp===> @{$modules_table{$mod}{$tmp_type}}\n",1);
        LOGGER($stdout, "notmp===> @{$modules_table{$mod}{$type}}\n\n",1);
        for my $message ( @$msg_ref, @$msg_tmp_ref) {
            LOGGER($fh, "$message", 0);
        }
        LOGGER($fh, "\n", 0);
    }
}

# Output that contains module wise summary so one can see which modules seems
# running with errors and then look at the info in the summary file.
sub process_summary_header
{
    my $summary_hdr_file = "$output_log_dir/summary_header"; 
    my $i;
    open(SUMMARY_HDR, "> $summary_hdr_file") ||
           die "failed to open Summary file $output_log_dir/summary_header";
    LOGGER(SUMMARY_HDR, "Summary output for files: ",0);
    chomp @log_files ; foreach my $y (@log_files) { chomp $y ; LOGGER(SUMMARY_HDR, "$y ",0);}
    LOGGER(SUMMARY_HDR, "\n",0);
    for $i (sort @msg_type) {
        # skip the msg_type with 0 counts.
        if (${total."$i"} != 0) {
            LOGGER(SUMMARY, "$i : ${total.\"$i\"} \n", 0);
        }
    }
    LOGGER(SUMMARY_HDR, "\n", 0);
    LOGGER(SUMMARY_HDR, "Host specific message counts :\n", 0);
    LOGGER(SUMMARY_HDR, "\n", 0);
    for my $host (sort keys %host_specific) {
       LOGGER(SUMMARY_HDR, "Host: $host\n", 0);
       for $i (sort @msg_type) {
           if ($host_specific{$host}{$i} != 0) {
               LOGGER(SUMMARY_HDR, "$i : $host_specific{$host}{$i}\n",0);
           }
       }
       LOGGER(SUMMARY_HDR, "\n", 0);
    }
    LOGGER(SUMMARY_HDR, "\n", 0);
    LOGGER(SUMMARY_HDR, "*********************************************************\n", 0);
    LOGGER(SUMMARY_HDR, "Module wise count of different message types\n", 0);
    LOGGER(SUMMARY_HDR, "Look for modules with high error|severe|warning counts\n", 0);
    LOGGER(SUMMARY_HDR, "Look up summary file for that module to get more information\n", 0);
    LOGGER(SUMMARY_HDR, "*********************************************************\n", 0);
    LOGGER(SUMMARY_HDR, "\n", 0);
    for my $mod (sort keys %modules_table) {
        if ($mod eq "header") {
            next;
        }
        for  $i (sort @msg_type) {
            if ($modules_table{$mod}{$i} != 0) {
                LOGGER(SUMMARY_HDR, "[$mod] $i : $modules_table{$mod}{$i}\n", 0);
            }
        }
        LOGGER(SUMMARY_HDR, "\n", 0);
    }
    LOGGER(SUMMARY_HDR, "\n", 0);

    # Print the perf summary
    LOGGER(SUMMARY_HDR, "*********************************************************\n", 0);
    LOGGER(SUMMARY_HDR, "Performance Statistics...\n", 0);
    LOGGER(SUMMARY_HDR, "*********************************************************\n", 0);
    my $key = "";
    my $value =0;
    while (($key, $value) = each %perf_messages) {
        LOGGER(SUMMARY_HDR, "$key => $value \n", 0);
    }
    LOGGER(SUMMARY_HDR, "*********************************************************\n", 0);

    close SUMMARY_HDR;
}

# Verify we have enough space (atleast 5G available in the destination directory or /var/adm/
# if not fail with an error.
sub process_all_files
{

    my $total_space_used =0;
    my $total_space_avail =0;
    my @x=`ls -al $output_base/messages*`; 
    foreach my $y (@x)  {
        my @p = split /\s+/, $y;
        $total_space_used +=$p[4];
    }
    LOGGER($stdout, "total space used for mesages: $total_space_used \n", 0);

    # check if free space is available to tar and gzip these.
    my $z=`df -b $output_log_dir | grep -vi avail`;
    my @y=split /\s+/,$z;
    $total_space_avail=($y[1] * 1024);
    # Enough space is available
    if ($total_space_avail > ($total_space_used + (1024 ^ 3))) {
        my $status =0;
        $status=`tar -cvf $output_log_dir/scraper.tar $output_base/messages*`;
        if ($status != 0) {
           die "Failed to tar up $output_base/messages* failed with : $status";
        }
        `gzip $output_log_dir/scraper.tar`;
        if ($status != 0) {
           die "Failed to zip $output_log_dir/scraper.tar failed with : $status";
        }
    } else {
         # We could try to grab the latest 10 files, for now die
         die "Not enough space to copy all the messages files";
    }
}


#MAIN:

init();

LOGGER($stderr, "Running with: start = $init_date; stop = $end_date; trace_level = $logging_level ",0);
LOGGER($stderr, "days = $total_num_days; log_output_directory = $output_log_dir verbosity = $verbosity \n",0);

LOGGER($stdout, "This will take atleast $time_for_run minutes to finish..\n", 0);

# Would just compress all the messages from the /var/adm and copy the one big file
# to the outout directory. Beware this could need 5GB space at least. We will
# do some calculation to find that. We should never have to do that in normal cases.
if ( $all  eq "true") {
    process_all_files();
    exit 0;
}
process_log_files();
process_summary_header();
process_summaries();
finish();

##############################################################################
# Documentation
##############################################################################
=pod

=head1 NAME

log_scraper - Filters a /var/adm/messages file and generates important information
              for service personel to toubleshoot.

=head1 SYNOPSIS
log_scraper
  usage:
    -s <start_date ex: Apr:12:13:23:12 >
    -e <end_date ex:Apr:13:13:23:12 >
    -o <output_directory>
    -a <true|false>
    -l <log_level[0-4]>
    -L <list of space seperated messages file>
    -n <num_msg_per_module>
    -d <num_of_days>
    -S <only_create_summary>
    -m <module_list, comma seperated>
    -v <verbosity>


Honeycomb generates a lot of debugging and informational messages to syslog and so the size 
of the logs is unwieldy from a serviceability perspective. Additionally a service engineer 
will find it daunting to wade through the huge logs for interesting information. The time 
required for this could run in to many hours. This tool is intended to help the service team 
to diagnose the system and get the system on it's feet quickly in around an hours time. 

It is also intended to document all the log messages seen in the scraper output, and additionally 
provide work around where possible. The current implementation of honeycomb is not clever about the 
logging and generates a lot of messages. For instance the logs at Info level could really indicate 
some issues in the system, where as a log at Severe level may not really mean a system wide issue. 
But in general the Severe and Warning's posted will be useful for the system debugging. The scraper 
should be smart about which levels of logging for the respective modules will be useful for troubleshooting. 
At the outset the scraper default output should provide the most important information from troubleshooting 
perspective. 

=head1 DESCRIPTION

OPTIONS
=======
All options need a value. There are no boolean options. Option -v needs a true/false specified.

-S : Option to  collect module wise summary and latest 'M' messages per module. 
The summary would print the number of messages of a particular logging level seen.
The option will also print node wise summaries. This helps the service engineer to 
get a over view of the system and so helps to look at interesting modules for triaging.

-n : Option to collect last 'N' days worth of scrubbed log at default log level.

-s + -e : Option to print scrubbed logs between a supplied start and end date

-l : Option to specify additional logging level so more messages are scrubbed. 
Probably used if more debugging information is needed. The level is 0-3, with 0 being the default. 

-o : Option to specify the output directory where the scrubbed logs will be collected.

-a : Option to collect 'all' the messages* files in /var/adm. This should check for the available 
space to collect logs, if enough space is not available try to collect as many log files as possible. 
No scrubbing is done for this option. The Sun engineers can run the scrub tool after they receive and 
uncompress all the messages files.

-L : Option to accept a list of messages file (no patterns). The scrubbing will happen only on these.

-v : Option to be verbose and chatty, not for service use. This is only to debug the tool.
      
The default will be to scrub the messages at log level 0 and create the summaries of top 2 of the 
unzipped log files in the /var/adm directory. The tool could be run on a service processor or on a 
node in case the service processor is unavailable.

Note on the output:
===================
The default run will create 3 files:

- hot_messages_file: This file will have predefined messages that have been identified as potential 
failure cases. A quick scan of this file will tell us if we see any bad things are happening in the 
cluster. This is the first file to be looked for immediate help.

- Summary_header: This file lists statistics about the different types of error messages in the 
whole cluster. After scanning this file one can determine where the potential failure lie.

- Summary: This file lists the summary modulewise. After looking at the above header file, one
can look at this file for troubles with specific modules. This file also has latest few messages
modulewise.

Non default runs with more levels will create additional per node files of the form
node.<error_type>. Some example files are hcb101.severe, hcb101.warning etc. We don't foresee
using non defaults in the normal circumstances.


Command reference:
==============
  usage:
    -s <start_date ex: Apr:12:13:23:12 >
    -e <end_date ex:Apr:13:13:23:12 >
    -o <output_directory>
    -a <true|false>
    -l <log_level[0-3]>
    -n <num_msg_per_module>
    -m <module_list, comma seperated>
    -d <num_of_days>
    -L <list of log files ssv with in quotes>

Start_date/End_date: The tool will scrape the log messages only between these dates. The format 
is mentioned in the usage message. Please the proper date as the tool will not validate it.

output_directory: This is where the scraped messages and the summary files will land. The caller of the 
tool will have the tar and gzip that directory. If this is not specified we store in /var/adm/scraper/<date>
directory, again the caller needs to tar-gzip it.

Log_level: This is the knob which can be turned for more details. The tool will automatically include modules 
at lesser logging_level.  Level 0 should fetch most of the interesting information. But higher levels can be used 
occasionally. When we need more information about a particular object or if we need all the messages from OAClient 
we need to set the level to 3.

num_msg_per_module : The summary file contains some stats at module level and that includes latest more or less 50 
messages. We could get more if we want.

num_of_days : Specify the number of days worth of log to be scraped.

module_list: List of interesting modules csv and with in quotes. This should get the information only for the 
             modules specified and nothing else.

All : This option does not do any scraping. It just tar-gzips all the /var/adm/messages* files and put's it in the 
output_log_dir. By default /var/adm/scraper/<date> should have it. The tool checks for the required space needed 
in the output directory, if not it will fail. We could enhance it to collect latest 
files.

Examples:
    
        log_scraper.pl
        log_scraper.pl -s 'Apr:12:13:23:12' -e 'Apr:13:13:23:12'
        log_scraper.pl -o /var/adm/tmp/scraper -l 1 
        log_scraper.pl -L "/var/adm/messages /var/adm/messages.0"

=cut
#END
