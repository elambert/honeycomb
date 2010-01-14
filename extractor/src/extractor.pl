#!/usr/bin/perl -w

################################################################################
#
# $Id: extractor.pl 11518 2007-09-20 05:19:31Z ks202890 $
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
# Explorer like extractor tool
# - To be run after a system failure to capture status and log information
#
################################################################################

use strict;
use Getopt::Std;
use File::Basename;
use English;

################################################################################
### Extractor Variables
################################################################################

### Unix commands
my $PING = "/usr/sbin/ping";
my $SSH = "/usr/bin/ssh";
my $DATE = "/usr/bin/date";
my $GZIP = "/usr/bin/gzip";
my $TAR = "/usr/sbin/tar";

### General extractor variables
my $MYROOT = File::Basename::dirname($PROGRAM_NAME);
my $MYNAME = basename($PROGRAM_NAME);
my %SUBSYSTEM;
my $THISDATE = undef;
my $TIMEOUT = 60;   # Default maximum time in minutes to wait for subtasks to complete
my $SLEEP_TIME = 10; # Sleep time between checking subtasks
my $LOGDIR = "/var/adm/extractor";
my $LOGDATE = undef;
my $SUMMARY = "Summary";
my $EXIT_STATUS = 0;
my $CONNECT_STATUS = 0;
my $EXTRACT_STATUS = 0;
my $SIGNUM = 0;
my $MESSAGE = undef;
my $LEVEL = 0;
my $MAX_LEVEL = 99;
my $DATA_VIP = undef;
my $DEFAULT_PROCESS_OPTIONS = "-C -L";
my $PROCESS_OPTIONS = undef;
my $LOG_SCRAPER_OPTIONS = undef;
my $REMOVE_LOG = "false";
my $GZIP_IT = "true";

### Cluster Variables
my $INTERNAL_ADMIN_IP = "10.123.45.200";
my $CLUSTER_PROPERTIES_FILE = "/export/honeycomb/config/config.properties";
my $CLUSTER_CONF_FILE = "/export/honeycomb/config/cluster.conf";

### SP Variables
my $INTERNAL_SP_IP = "10.123.45.100";
my $SSH_SP = $SSH." -q -l root -o StrictHostKeyChecking=no ".$INTERNAL_SP_IP;
my $SP_TESTCMD = "date";
my $SP_SCRAPER_OPTIONS = "/var/adm/messages /var/adm/messages.0";

### Node Variables
my $INTERNAL_NODE_IP = "10.123.45.";
my $SSH_NODE = $SSH." -q -l root -o StrictHostKeyChecking=no";
my $SSH_MASTER_NODE = $SSH_NODE." ".$INTERNAL_ADMIN_IP;
my $MASTERNODE_HOSTNAME = undef;
my $MASTER_TESTCMD = "date";
my $NUM_NODES = undef;
my $NODE_CONFIG_PROPERTIES = "/config/config.properties";
my $NODE_TESTCMD = "date";
my $SILO_CONFIG_PROPERTIES = "/config/silo_info.xml";
my $NODE_SCRAPER_OPTIONS = "";

### Cli Variables
my $SSH_CLI = "$SSH root\@$INTERNAL_ADMIN_IP -o StrictHostKeyChecking=no /opt/honeycomb/bin/hcsh -c";
my $MAX_WAIT_CLI_ITERS = 10; 
my $MAX_WAIT_CLI = 60;
my $CLI_TESTCMD = "date";

### Switch Variables
my $SWITCH_FAILOVER_IP = "10.123.45.1";
my $SWITCH_1_IP = "10.123.0.1";
my $SWITCH_2_IP = "10.123.0.2";
my $SSH_SWITCH = $SSH." -q -l nopasswd -p 2222 -o StrictHostKeyChecking=no ".$SWITCH_FAILOVER_IP;
my $SSH_BACKUP_SWITCH = $SSH_SWITCH. " $SSH -q -l nopasswd -p 2222 "."-o StrictHostKeyChecking=no ";
my $SWITCH_PING = "/bin/ping";
my $SWITCH_BACKUP_IP = undef;
my $FSWITCH_TESTCMD = "date";
my $BSWITCH_TESTCMD = "date";
my $SWITCH_CONF = "/etc/honeycomb/switch.conf";
my $SWITCH_SCRAPER_OPTIONS = "";

my $PATH = $ENV{'PATH'};
$ENV{PATH} = "${MYROOT}:${PATH}";

################################################################################
### Extractor Subroutines
################################################################################

sub print_log {
    print LOG "@_\n";
}
 
sub print_both {
    print "@_\n";
    print_log("@_");
} # print_both #

sub print_header {
    print "="x80;
    print LOG "="x80;
    print_both("\n");
    print_both("@_");
    print "="x80;
    print LOG "="x80;
    print_both("\n");
} # print_header #

sub run {
    print_log("Run @_");
    return system(@_);
} # run #

sub safe_run {
    my $rc = run(@_); 
    if(!$rc) {
        print_log("Successfully ran: @_");
    } else {
        print_log("Failed to run: @_");
    } 
    return $rc;
} # safe_run #

sub check_connections {
    print_both ("INFO: Checking network connectability to each subsystem...");
    check_sp();
    check_master();
    check_nodes();
    check_fswitch();
    check_bswitch();
    check_cli();
} # check_connections #

sub check_sp {
    print_both("INFO: Checking Service Processor connectivity...");
    my $ping = $PING." ".$INTERNAL_SP_IP." 1 >> /dev/null 2>&1";
    my $rc = safe_run($ping);
    
    if (!$rc) {
       my $cmd = $SSH_SP." ".$SP_TESTCMD." >> /dev/null 2>&1";
       $rc = safe_run($ping);
       $SUBSYSTEM{sp} = {
           alive => 1, # 1 == alive, 0 == dead
           hostname => "sp",
           ip => $INTERNAL_SP_IP, 
           ping => $ping,
           ssh => $SSH_SP, 
           subtask => "extractSP.pl",
           pid => 0,
           log => "",
        } if (!$rc); 
   } # check_SP #
   if ($rc) {
       print_both("WARNING: Cannot connect to Service Processor. Skipping Service Processor extraction."); 
   } else {
       print_both("INFO: Connection to Service Processor Okay.");
   }
}

sub check_master {
    print_both("INFO: Checking Master Node connectivity...");
    my $ping = $PING." ".$INTERNAL_ADMIN_IP." 1 >> /dev/null 2>&1";
    my $rc = safe_run($ping);

    if (!$rc) {
        my $cmd = $SSH_MASTER_NODE." ".$MASTER_TESTCMD." >> /dev/null 2>&1";
        $rc = safe_run($cmd);
        $SUBSYSTEM{master} = {
            alive => 1, # 1 == alive, 0 == dead
            hostname => "master",
            ip => $INTERNAL_ADMIN_IP, 
            ping => $ping,
            ssh => $SSH_MASTER_NODE, 
            subtask => "extractNode.pl",
            pid => 0,
            log => "",
        } if (!$rc);
    }
    if ($rc) {
        print_both("WARNING: Cannot connect to Master Node. Skipping Master Node extraction.");
    } else {
        print_both("INFO Connection to Master Node Okay");
    }
} # check_master #

sub check_nodes {
    for (my $node=101; $node<=(100+$NUM_NODES); $node++) {
        print_both("INFO: Checking Node hcb".$node." connectivity...");
        my $hostname = "hcb$node";
        my $ping = $PING." ".$INTERNAL_NODE_IP.$node." 1 >> /dev/null 2>&1";
        my $rc = safe_run($ping);

        if (!$rc) {
            my $ip = $INTERNAL_NODE_IP.$node;
            my $ssh = $SSH_NODE." ".$INTERNAL_NODE_IP.$node;
            my $cmd = $SSH_NODE." ".$INTERNAL_NODE_IP.$node." ".$NODE_TESTCMD." >> /dev/null 2>&1";
            $rc = safe_run($cmd);
            $SUBSYSTEM{$hostname} = {
                alive => 1, 
                hostname => $hostname,
                ip => $ip, 
                ping => $ping,
                ssh => $ssh, 
                subtask => "extractNode.pl",
                pid => 0,
                log => "",
            } if (!$rc);
        }
        if ($rc) {
            print_both("WARNING: Cannot connect to $hostname. Skipping $hostname extraction."); 
        } else {
            print_both("INFO: Connection to $hostname Okay.");
        }
    }
} # check_nodes #

sub check_fswitch {
    print_both("INFO: Checking Failover Switch connectivity...");
    # Is failover switch IP pingable? 
    my $ping = $PING." ".$SWITCH_FAILOVER_IP." 1 >> /dev/null 2>&1";
    my $rc = safe_run($ping);
    
    if (!$rc) {
        my $cmd = $SSH_SWITCH." ".$FSWITCH_TESTCMD." >> /dev/null 2>&1 2>&1";
        $rc = safe_run($cmd);
        $SUBSYSTEM{fswitch} = {
            alive => 1, 
            hostname => "fswitch",
            ip => $SWITCH_FAILOVER_IP, 
            ping => $ping,
            ssh => $SSH_SWITCH, 
            subtask => "extractFswitch.pl",
            pid => 0,
            log => "",
        } if (!$rc);
    }
    if ($rc) {
        print_both("WARNING: Cannot connect to Failover Switch. Skipping Failover or Backup extraction.");
    } else {
        print_both("INFO Connection to Failover Switch Okay");
    }
} # check_fswitch *
 
sub get_backup_switch_ip {
    my $ifconfig_cmd = "ifconfig zhp0 | grep 'inet addr' | awk {'print \$2'} | awk -F: {'print \$2'}";
    $_ = `$SSH_SWITCH $ifconfig_cmd`;
    chomp;
    if($_ eq $SWITCH_1_IP) {
        return $SWITCH_2_IP;
    } elsif($_ eq $SWITCH_2_IP) {
        return $SWITCH_1_IP;
    }
} # get_backup_switch_ip #

sub check_bswitch {
    if ($SUBSYSTEM{fswitch}{alive}) {
        print_both("INFO: Checking Backup Switch connectivity...");
        $SWITCH_BACKUP_IP = get_backup_switch_ip();
        print_log("INFO: Backup Switch IP address $SWITCH_BACKUP_IP");
        # Is switch failover IP pingable? 
        my $ping = $SSH_SWITCH." ".$SWITCH_PING." -c 1 ".$SWITCH_BACKUP_IP." 1 >> /dev/null 2>&1";
        my $rc = safe_run($ping);

        if (!$rc) {
            $SSH_BACKUP_SWITCH .= $SWITCH_BACKUP_IP;
            my $cmd = $SSH_BACKUP_SWITCH." ".$BSWITCH_TESTCMD." >> /dev/null 2>&1";
            $rc = safe_run($cmd);
            $SUBSYSTEM{bswitch} = {
                alive => 1, 
                hostname => "bswitch",
                ip => $SWITCH_BACKUP_IP, 
                ping => $ping,
                ssh => $SSH_BACKUP_SWITCH, 
                subtask => "extractBswitch.pl",
                pid => 0,
                log => "",
            } if (!$rc);
        }
        if ($rc) {
            print_both("WARNING: Cannot connect to Backup Switch. Skipping Backup extraction.");
        } else {
            print_both("INFO Connection to Backup Switch Okay");
        }
    }
} # check_bswitch #

sub check_cli {
    print_both("INFO: Checking ADMIN CLI Connectivity...");
    my $ping = $PING." ".$INTERNAL_ADMIN_IP." 1 >> /dev/null 2>&1";
    my $rc = safe_run($ping);
   
    if (!$rc) {
       for(my $i=0; $i<=$MAX_WAIT_CLI_ITERS; $i++) {
           my $cmd = "$SSH_CLI ".$CLI_TESTCMD." >/dev/null";
           if(run($cmd) != 0) {
               print_both("Wait number $i of $MAX_WAIT_CLI_ITERS for $MAX_WAIT_CLI seconds on the Admin CLI...");
               sleep $MAX_WAIT_CLI;
           } else {
               last;
           }  
       }
       $SUBSYSTEM{admin} = {
           alive => 1, 
           hostname => "admin",
           ip => $INTERNAL_ADMIN_IP, 
           ping => $ping, 
           ssh => $SSH_CLI, 
           subtask => "extractAdmin.pl",
           pid => 0,
           log => "",
       } if (!$rc);
   };
   if ($rc) {
       print_both("WARNING: Cannot connect to Admin CLI. Skipping CLI extraction.");
   } else {
       print_both("INFO Connection to Admin CLI Okay");
   }
}

sub get_masternode_hostname {
    if ($SUBSYSTEM{master}{alive}) {
       $MASTERNODE_HOSTNAME = `$SSH_MASTER_NODE hostname`;
       chomp($MASTERNODE_HOSTNAME);
       print_both("\nINFO: The Master Node hostname is: $MASTERNODE_HOSTNAME");
    }
} # get_masternode_hostname #

sub display_failover_switch {
    if ($SUBSYSTEM{fswitch}{alive} && $SWITCH_BACKUP_IP eq $SWITCH_2_IP) {
        print_both ("INFO: The Primary Switch is the Failover: (IP=$SWITCH_1_IP)");
        $SUBSYSTEM{fswitch}{hostname} = "pswitch";
    } elsif ($SUBSYSTEM{fswitch}{alive} && $SWITCH_BACKUP_IP eq $SWITCH_1_IP) {
        print_both ("INFO: The Secondary Switch is the Failover: (IP=$SWITCH_2_IP)");
        $SUBSYSTEM{fswitch}{hostname} = "sswitch";
    }
    if ($SUBSYSTEM{bswitch}{alive} && $SWITCH_BACKUP_IP eq $SWITCH_2_IP) {
        print_both ("INFO: The Secondary Switch is the Backup: (IP=$SWITCH_2_IP)");
        $SUBSYSTEM{bswitch}{hostname} = "sswitch";
    } elsif ($SUBSYSTEM{bswitch}{alive} && $SWITCH_BACKUP_IP eq $SWITCH_1_IP) {
        print_both ("INFO: The Primary Switch is the Backup: (IP=$SWITCH_1_IP)");
        $SUBSYSTEM{bswitch}{hostname} = "pswitch";
    } else {
        print_both ("INFO: The Backup Switch is not reachable.");
    }
} # display_failover_switch #

sub sigHandler () {
    my $signame = shift;
    our $SIGNUM++;
    local $SIG{HUP} = 'IGNORE';
    local $SIG{INT} = 'IGNORE';
    local $SIG{TERM} = 'IGNORE';
    print_both ("\nWARNING: Signal SIG$signame recieved. $MYNAME cleanup and exit!\n");
    kill($signame, -$$);
    exit(2);
} # sigHandler #

sub fork_exec_subtasks() {
    print_both ("\nINFO: Background subtasks for each connectable subsystem...");
    for my $subsystem (keys %SUBSYSTEM) {
        if ($SUBSYSTEM{$subsystem}{alive}) {
            my $hostname = $SUBSYSTEM{$subsystem}{hostname};
            next if ($hostname eq "master"); # We'll get the master using the node array for now.
            my $logdir = "$LOGDIR/$hostname";
            my $rc = `mkdir -p $logdir`;
            if ($rc) {
               print("WARNING: Cannot create $logdir for $hostname, error: $rc");
               next;
            }
            my $logfile = "$logdir/$SUMMARY";
            $rc = `touch $logfile`;
            if ($rc) {
               print("WARNING: Cannot create $logfile for $hostname, error: $rc");
               next;
            }
            my $ip = $SUBSYSTEM{$subsystem}{ip};
            my $ping = $SUBSYSTEM{$subsystem}{ping};
            my $ssh = $SUBSYSTEM{$subsystem}{ssh};
            # Don't set the LEVEL if we aren't processing commands.
            my $process_options = $PROCESS_OPTIONS;
            my $cmd_options = "";
            if ($process_options =~ /-C/) {
                $cmd_options = "-l $LEVEL" ;
            }
            # Only run the log_scraper on the SP if it's reachable. On each node if it's not.
            my $log_scraper_options = "";
            if ($process_options =~ /-L/) {
                if ($subsystem eq "sp") {
                   $log_scraper_options = $LOG_SCRAPER_OPTIONS || $SP_SCRAPER_OPTIONS;
                } elsif ($subsystem =~ /hcb1/ && $SUBSYSTEM{sp}{alive}) {
                   for ($process_options) {s/-L//; s/^\s+//; s/\s+$//}
                } elsif ($subsystem =~ /hcb1/ && !$SUBSYSTEM{sp}{alive}) {
                   $log_scraper_options = $LOG_SCRAPER_OPTIONS || $NODE_SCRAPER_OPTIONS;
                } elsif ($subsystem =~ /switch/) {
                   $log_scraper_options = $LOG_SCRAPER_OPTIONS || $SWITCH_SCRAPER_OPTIONS;
                } elsif ($subsystem eq "admin") {
                   for ($process_options) {s/-L//; s/^\s+//; s/\s+$//}
                } 
                $log_scraper_options = "-S \"$log_scraper_options\"" if ($log_scraper_options);
            }
            if ($process_options =~ /-M/) {
                if ($subsystem eq "admin") {
                   for ($process_options) {s/-M//; s/^\s+//; s/\s+$//}
                } 
            }
            if ($process_options =~ /-D/) {
                if ($subsystem eq "admin" || $subsystem =~ /switch/) {
                   for ($process_options) {s/-D//; s/^\s+//; s/\s+$//}
                } 
            }
            next if ($process_options eq "");
            my $options = "$process_options $cmd_options $log_scraper_options -r $hostname -i $ip -p \"$ping\" -s \"$ssh\" -o $logdir";
            my $cmd = "$MYROOT/$SUBSYSTEM{$subsystem}{subtask} $options";
            $rc = fork;
            if ($rc == 0){
                exec($cmd." >$logfile 2>&1");
                exit(0);
            } elsif (defined $rc) {
                # This is the parent
            } else {
                print_both ("WARNING: Fork of $SUBSYSTEM{$subsystem}{hostname} failed with status: $rc");
                $EXTRACT_STATUS = 3; 
            }
            $SUBSYSTEM{$subsystem}{pid} = $rc;
            $SUBSYSTEM{$subsystem}{log} = $logfile;
            print_both ("INFO: Running the background subtask for $SUBSYSTEM{$subsystem}{hostname} (pid=$SUBSYSTEM{$subsystem}{pid}).");
            print_log($cmd);
        }
    }
} # fork_exec_subtasks *

sub wait_on_subtasks () {
    print_both ( "\nINFO: Waiting on each subtask process to exit...");
    my $timeout = $TIMEOUT * 60;
    for (my $time=0; $time<=$timeout; $time+=$SLEEP_TIME) {
        my $complete = 1;
        for my $subsystem (keys %SUBSYSTEM) {
            if ($SUBSYSTEM{$subsystem}{pid}) {
                $complete = 0;
                my $rc = system("ps -fp $SUBSYSTEM{$subsystem}{pid} | grep $SUBSYSTEM{$subsystem}{subtask} >>/dev/null");
                if ($rc) {
                    $SUBSYSTEM{$subsystem}{pid} = 0;
                    $_ = `grep '^ERROR: ' $SUBSYSTEM{$subsystem}{log}`;
                    chomp;
                    if ($_ =~ /^ERROR: /) {
                        print_both ("WARNING: The subtask for $SUBSYSTEM{$subsystem}{hostname} lists errors in: $SUBSYSTEM{$subsystem}{log}");
                        $EXTRACT_STATUS = 3; 
                    } else {
                        $_ = `grep '^WARNING: ' $SUBSYSTEM{$subsystem}{log}`;
                        chomp;
                        if ($_ =~ /^WARNING: /) {
                            print_both ("WARNING: The subtask for $SUBSYSTEM{$subsystem}{hostname} lists warnings in: $SUBSYSTEM{$subsystem}{log}");
                            $EXTRACT_STATUS = 3; 
                        } else {
                            print_both ("INFO: The subtask for $SUBSYSTEM{$subsystem}{hostname} completed normally.");
                        }
                    }
                }
            }
        }
        next if ($complete);
        sleep($SLEEP_TIME);
    }

    # Kill all child processes that timed out
    local $SIG{TERM} = 'IGNORE';
    kill('TERM', -$$); 
    $SIG{TERM} = \&sigHandler;
    for my $subsystem (keys %SUBSYSTEM) {
        if ($SUBSYSTEM{$subsystem}{pid}) {
            print_both( "ERROR: Subtask $SUBSYSTEM{$subsystem}{hostname} timed out.");
            $SUBSYSTEM{$subsystem}{pid} = 0;
            $EXTRACT_STATUS = 3; 
        }
    }
} # wait_on_subtasks #

sub get_num_nodes {
    my $cmd = $PING." ".$INTERNAL_ADMIN_IP." 1 >> /dev/null 2>&1";
    my $rc = system($cmd);
    if (!$rc) {
        if (system("$SSH_MASTER_NODE ls $NODE_CONFIG_PROPERTIES >> /dev/null") == 0) {
            $_ = `$SSH_MASTER_NODE grep honeycomb.cell.num_nodes $NODE_CONFIG_PROPERTIES`;
            chomp;
            if(/^honeycomb.cell.num_nodes\s*=\s*(\d+)$/) {
                $NUM_NODES = $1;
            }
        } elsif(-e $CLUSTER_PROPERTIES_FILE) {
            $_ = `grep honeycomb.cell.num_nodes $CLUSTER_PROPERTIES_FILE`;
            chomp;
            if(/^honeycomb.cell.num_nodes\s*=\s*(\d+)$/) {
                $NUM_NODES = $1;
            }
        } elsif(-e $CLUSTER_CONF_FILE) {
            $_ = `grep CLUSTERSIZE $CLUSTER_CONF_FILE | awk -F= {'print \$2'}`;
            chomp;
            $NUM_NODES = $_;
        } elsif(-e $NODE_CONFIG_PROPERTIES) {
            $_ = `grep honeycomb.cell.num_nodes $NODE_CONFIG_PROPERTIES`;
            chomp;
            if(/^honeycomb.cell.num_nodes\s*=\s*(\d+)$/) {
                $NUM_NODES = $1;
            }
        }
    } else {
        $NUM_NODES = 16;
        print_both( "WARNING: Cannot deternmine the number of nodes. Using default: $NUM_NODES");
    }
} # get_num_nodes #

sub get_data_vip {
    # First, try the master node if it is reachable.
    my $cmd = $PING." ".$INTERNAL_ADMIN_IP." 1 >> /dev/null 2>&1";
    my $rc = system($cmd);
    if (!$rc) {
        if (system("$SSH_MASTER_NODE ls $SILO_CONFIG_PROPERTIES >> /dev/null") == 0) {
            $cmd = "$SSH_MASTER_NODE grep 'data-vip=' $SILO_CONFIG_PROPERTIES | cut -d\\\" -f6";
            $_ = `$cmd`;
            chomp;
            $DATA_VIP = "$_";
        }
    } 
    # if that fails, try the failover switch if it is reachable.
    if (!$DATA_VIP) {
        my $cmd = $PING." ".$SWITCH_FAILOVER_IP." 1 >> /dev/null 2>&1";
        my $rc = system($cmd);
        if (!$rc) {
            if (system("$SSH_SWITCH ls $SWITCH_CONF >> /dev/null") == 0) {
               $cmd = "$SSH_SWITCH grep '^DATAVIP=' $SWITCH_CONF | cut -d= -f2";
               $_ = `$cmd`;
               chomp;
               $DATA_VIP = "$_";
            }
        }
    }
} # get_data_vip #

sub gzip_output {
    my $logdir = dirname($LOGDIR);
    my $zipdir = basename($LOGDIR);
    my $zipfile = $zipdir.".tar.gz";
    print("Compressing output to: $logdir/$zipfile\n");
    my $cmd = "cd $logdir ; $TAR -Ecf - ${zipdir} | $GZIP -9c > $zipfile";
    my $rc = `$cmd`;
    if ($rc) {
        print("ERROR: Cannot $GZIP $zipdir to $zipfile. Status: $rc\n");
        $EXIT_STATUS = 2;
    }
} # gzip_output #

sub usage {
   print <<EOF;

NAME:
    $MYNAME - stk5800 extractor program 
    
SYNOPSIS:
    $MYNAME [-C [-l level]] [-L [-S scraper_options]] [-M] [-D] [-A]
            [-c name] [-o logdir] [-R] [-Z true|false] [-T timeout]

    $MYNAME [-h]

DESCRIPTION:
    Extractor main program for gathering command output, message logs, 
    and crash information for the Service Processor, Nodes, and Switches 
    that are reachable on the internal stk5800 network. $MYNAME 
    then runs background 'subtask' programs to gather this information 
    for each responding subsystem. 

WHERE:
    -C : Gather command output defined by the extractor.cmds file.
    -L : Gather log scraper output from the Service processor if it is
         reachable, from each reachable node if it is not. 
    -M : Copy the message logs. This is NOT run by default.
    -D : Copy the core and crash dump files. This is NOT run by default.
    -A : Run all of the processing options listed above.
         
     NOTE: Do not mix any of the above options (except -L) with the
           options described below.

    -l : The assigned 'level' of commands to be run by the subtask processes.
         The default is 0, run all commands. Levels 0 through 10 are valid.
    -o : The output directory path for $MYNAME and the 'Summary', command
         output, messages, and core files for each subtask. All generated
         files are located under this directory when this option is used. 
         The default output directory is /var/adm/extractor/<data_vip>-<date> 
         and is always cleaned of all content at startup.
    -c : The 'cluster' name. When used, this string is inserted into the output
         directory name instead of using the <data_vip>.
    -S : The 'scraper_options' that will be passed to the log_scraper when it
         is run. Validation of user supplied options is done by the log_scraper 
         utility. See the NOTES for further information and limitations.
    -R : Remove the default output directory content and compressed files. The 
         default is NOT to remove the default output directory. 
    -Z : Gzip (true) or do not gzip (false) the output directory. 
         The default is to gzip the output directory.
    -T : The 'timeout' in minutes used to determine if any subtask are hung. 
         The default is $TIMEOUT minutes. Once the 'timeout' has expired all 
         subtasks are stopped and $MYNAME exits.

NOTES:
    When any of the -C, -L, -M, or -D  processing options are specified, only 
    the specific output for those options is gathered. Options '$DEFAULT_PROCESS_OPTIONS' 
    are the default when no processor options are specified. Options -L, -M, or 
    -D are ignored on subsystems that do not support these options.

    Option '-C' commands are read from the extractor.cmds file and processed 
    by each subtask according to the subsystem type and the level assigned 
    to each command defined in the extractor.cmds file.

    Log scraper option '-o' is not supported by $MYNAME. 

    Log scraper option '-L' is only meaningful for the Service Processor and 
    Node messages. Log scraping of the switches is not supported so all
    messages are gathered from the switches. The defaults used are:
      o Service Processor: /var/adm/messages and /var/adm/messages.0
        are scraped
      o Nodes: /var/adm/messages and /var/adm/messages.0 files are scraped
      o Switches: All /var/log/messages* files are gathered
   
    Read the log_scraper readme for details about the options.

    The extractor.pl program can be invoked via cron(1M) if desired.

FILES:
    extractor.cmds: 
        The command configuration file defining all option '-C' commands for 
        each subsystem type, their output log file, and their processing 
        'level'.
    Summary: 
        The 'Summary' results for the $MYNAME program. A 'Summary' file 
        is created by each subtask under its assigned subsystem named 
        directory.  For example, the Service Processor will generate a 
        sp/Summary file.
           
USAGE:
    1) Run $MYNAME using all default options.
    
       # $MYNAME

    2) Run $MYNAME specifying a start and end date for the log_scraper.

       # $MYNAME -S "-s Apr:12:13:23:12 -e Apr:13:13:23:12"

    3) Run $MYNAME to capture only the level 20 commands defined by the
       extractor.cmds.

       # $MYNAME -C -l 20 

    4) Run $MYNAME to capture all messages and crash or core dump files
       and remove all default output directories and gzip files.
    
       # $MYNAME -M -D -R

    5) Run $MYNAME to capture all information it is capable of gathering.
    
       # $MYNAME -A
EOF
} # usage #

################################################################################
### Start of Main 
################################################################################

# Check commandline options
my @commandline = @ARGV;
my (%opts);
if (!getopts("?hCLMDAl:c:o:p:S:RZ:T:", \%opts)) {
   usage();
   exit(1);
}

# Display help usage
if ($opts{h} || $opts{'?'}) {
   usage();
   exit(0);
}

# Command line parameters are not supported.
if (( $#ARGV >= 0)) {
   usage();
   exit(1);
}

# Determine the subtask processing options
if ($opts{C}) {
    $PROCESS_OPTIONS = "${PROCESS_OPTIONS} -C";
}
if ($opts{L}) {
    $PROCESS_OPTIONS = "${PROCESS_OPTIONS} -L";
}
if ($opts{M}) {
    $PROCESS_OPTIONS = "${PROCESS_OPTIONS} -M";
}
if ($opts{D}) {
    $PROCESS_OPTIONS = "${PROCESS_OPTIONS} -D";
}
if ($opts{A}) {
    $PROCESS_OPTIONS = "-C -L -M -D";
}
$PROCESS_OPTIONS = $DEFAULT_PROCESS_OPTIONS if (!$PROCESS_OPTIONS);

# Set the command processing level
if ($opts{l}) {
    if ($opts{l} >= 0 && $opts{l} <= $MAX_LEVEL) {
        $LEVEL = $opts{l};
    } else {
        print("ERROR: Only 'level' 0 through $MAX_LEVEL is valid.");
        exit(1);
    }
}

# Set the log_scraper options
if ($opts{S}) {
    if ($opts{S} =~ /-o /) {
        print("ERROR: log_scraper option '-o' cannot be used with $MYNAME.");
        exit(1);
    }
    $LOG_SCRAPER_OPTIONS = "$opts{S}";
}

# Override the default subtask timeout
if ($opts{T}) {
    $TIMEOUT = $opts{T};
}

# Override the default remove of the output directory
if ($opts{R}) {
    $REMOVE_LOG = "true";
}

# Override the default compress of the output directory
if ($opts{Z}) {
    if ($opts{Z} eq "true" || $opts{Z} eq "false") {
        $GZIP_IT = $opts{Z};
    } else {
        print("ERROR: Option '-Z' can only be 'true' or 'false'.");
        exit(2);
    }
}

# Determine the path to the base $LOGDIR
$LOGDATE = `$DATE '+%Y.%m.%d.%H.%M'`;
chomp ($LOGDATE);
if ($opts{o} && $opts{c}) {
    $LOGDIR = "$opts{o}/$opts{c}-$LOGDATE";
} elsif ($opts{c}) {
    $LOGDIR = "$LOGDIR/$opts{c}-$LOGDATE";
} elsif ($opts{o}) {
    $LOGDIR = "$opts{o}";
} else {
    # Get the Data VIP to prefix to the dated directory and gzip file.
    get_data_vip();
    $LOGDATE = "$DATA_VIP-$LOGDATE" if ($DATA_VIP);
    # Remove any old directories and files
    if ($REMOVE_LOG eq "true") {
        my $rc=`rm -rf $LOGDIR/*`;
        if ($rc) {
            print_both("ERROR: Cannot remove directories and files under $LOGDIR. Error: $rc");
            exit(2);
        }
    }
    $LOGDIR = "$LOGDIR/$LOGDATE";
}

# Create $LOGDIR and $SUMMARY file. Open $SUMMARY file.
my $rc = `mkdir -p $LOGDIR`;
if ($rc) {
    print_both("ERROR: Cannot create $LOGDIR, error: $rc");
    exit(2);
}
$rc = `touch $LOGDIR/$SUMMARY`;
if ($rc) {
    print_both("ERROR: Cannot create $LOGDIR/$SUMMARY, error: $rc");
    exit(2);
}
$rc = open (LOG, ">$LOGDIR/$SUMMARY");
if (!$rc) {
    print_both("ERROR: Cannot Open $LOGDIR/$SUMMARY, error $!");
    exit(2);
}

# Determine the number of nodes in the system
get_num_nodes();

$THISDATE = `$DATE`;
my $commandline = "$MYNAME";
while (@commandline) {
    $commandline .= " ".shift(@commandline);
}
$MESSAGE = "Starting $MYNAME on: $THISDATE". 
           "Command Line: $commandline\n".
           "Average Tool Runtime: 20 minutes or more based on the options specified.\n". 
           "\n".
           "The following steps are performed by $MYNAME:\n". 
           "o Check the network connection to Service Processor, Nodes, Switches,\n".  
           "  and CLI.\n".  
           "o Background the Service Processor, Nodes, Switches, and CLI subtask\n".  
           "  programs.\n".  
           "o Wait for all background subtask programs to complete and report\n".  
           "  report their status.\n".  
           "o Run the log_scraper to gather message logs from each subsystem\n".  
           "  If the extractor is run from SP, the scraper will run only on the SP\n".
           "  But if run from one of the nodes, scraper will be run on each of the nodes\n".
           "  if SP is down, else it will be run only on SP.\n".
           "  Also copy the messages file from the switches.\n".  
           "o Copy the message logs from the sp or from each node subsystem\n".  
           "o Copy the crash and core dump files from each subsystem\n".  
           "o Compress the output directory\n".  
           "\n".
           "Summary output in: $LOGDIR/$SUMMARY\n".
           "Program output under: $LOGDIR/\n";  
$MESSAGE = "$MESSAGE"."The gzip output in: ${LOGDIR}.tar.gz\n" if ($GZIP_IT eq "true");
print_header($MESSAGE);

# Define the signals we want to catch.
$SIG{HUP} = \&sigHandler;
$SIG{INT} = \&sigHandler;
$SIG{TERM} = \&sigHandler;

# Check if the switches, service processor, and cluster nodes are sshable
check_connections();

# See what subsystems we have to extract data from..
my $EXTRACT_CNT = 0;
for my $subsystem (keys %SUBSYSTEM) {
   $EXTRACT_CNT++ if ($SUBSYSTEM{$subsystem}{alive})
}

if ($EXTRACT_CNT) {
   # Determine the Master hostname
   get_masternode_hostname();

   # Display which switch is the failover
   display_failover_switch();

   # Start background subtasks on each subsystem that is alive to do the real work.
   fork_exec_subtasks();

   # Wait on each subtask to complete normally, with errors, or timeout.
   wait_on_subtasks();

}

# Check and define exit status
$THISDATE = `$DATE`;
if (!$EXTRACT_CNT) {
    $MESSAGE = "Could not find connectable subsystems to extract on: $THISDATE";
    $EXIT_STATUS = 10;
} elsif ($CONNECT_STATUS) {
    $MESSAGE = "$MYNAME encountered connection errors or warnings on: $THISDATE";
    $EXIT_STATUS = $CONNECT_STATUS;
} elsif ($EXTRACT_STATUS) {
    $MESSAGE = "$MYNAME encountered extraction errors or warnings on: $THISDATE";
    $EXIT_STATUS = $EXTRACT_STATUS;
} else {
    $MESSAGE = "Normal completion of $MYNAME on: $THISDATE"; 
}
$MESSAGE = "$MESSAGE"."Summary output in: $LOGDIR/$SUMMARY\n".
       "Program output under: $LOGDIR/\n";  
$MESSAGE = "$MESSAGE"."The gzip output in: ${LOGDIR}.tar.gz\n" if ($GZIP_IT eq "true");
print_header($MESSAGE);

close LOG;

# gzip the LOGDIR results
gzip_output() if ($GZIP_IT eq "true");

exit($EXIT_STATUS);
