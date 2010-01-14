package ExtractLibs;

##############################################################################
#
# $Id: ExtractLibs.pm 11349 2007-08-13 21:49:33Z ks202890 $
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
# Extractor common libraries used by the extractor subtasks.
#
##############################################################################

use File::Basename;

my $RETURN_STATUS = undef;
my $COMMAND_FILE = "/opt/honeycomb/extractor/extractor.cmds";
my $PING = "/usr/sbin/ping";
my $SSH = "/usr/bin/ssh";
my $INTERNAL_SP_IP = "10.123.45.100";
my $SSH_SP = $SSH." -q -l root -o StrictHostKeyChecking=no ".$INTERNAL_SP_IP;
my $INTERNAL_ADMIN_IP = "10.123.45.200";
my $SSH_NODE = $SSH." -q -l root -o StrictHostKeyChecking=no";
my $SSH_MASTER_NODE = $SSH_NODE." ".$INTERNAL_ADMIN_IP;
my $SILO_CONFIG_PROPERTIES = "/config/silo_info.xml";
my $SWITCH_FAILOVER_IP = "10.123.45.1";
my $SSH_SWITCH = $SSH." -q -l nopasswd -p 2222 -o StrictHostKeyChecking=no ".$SWITCH_FAILOVER_IP;
my $SWITCH_CONF = "/etc/honeycomb/switch.conf";
my $NODE_CONFIG_PROPERTIES = "/config/config.properties";
my $CLUSTER_PROPERTIES_FILE = "/export/honeycomb/config/config.properties";
my $CLUSTER_CONF_FILE = "/export/honeycomb/config/cluster.conf";
my $DUMPADM_CONF = "/etc/dumpadm.conf";
my $COREADM_CONF = "/etc/coreadm.conf";

sub print_it {
    my ($message) = @_;
    print($message);
    print("\n");
} # print_it #

sub print_msg {
    my ($class, $message) = @_;
    print($message);
    print("\n");
} # print_msg #

sub print_header {
    my ($class, $message) = @_;
    print("="x80);
    print("\n");
    print("\n");
    print($message);
    print("\n");
    print("="x80);
    print("\n");
} # print_header #

sub run {
    print_it("Run @_");
    return system(@_);
} # run #

sub safe_run {
    my $rc = run(@_);
    if(!$rc) {
        print_it("Successfully ran: @_");
    } else {
        print_it("Failed to run: @_");
    }
    return $rc;
} # safe_run #

sub get_data_vip {
    # First, try the master node if it is reachable.
    my $data_vip = undef;
    my $cmd = $PING." ".$INTERNAL_ADMIN_IP." 1 >> /dev/null 2>&1";
    my $rc = system($cmd);
    if (!$rc) {
      	if (system("$SSH_MASTER_NODE ls $SILO_CONFIG_PROPERTIES >> /dev/null") == 0) {
      	    $cmd = "$SSH_MASTER_NODE grep 'data-vip=' $SILO_CONFIG_PROPERTIES | cut -d\\\" -f6";
      	    $_ = `$cmd`;
      	    chomp;
      	    $data_vip = "$_";
      	}
    } 
    # If that fails, try the failover switch if it is reachable.
    if (!$data_vip) {
      	my $cmd = $PING." ".$SWITCH_FAILOVER_IP." 1 >> /dev/null 2>&1";
      	my $rc = system($cmd);
      	if (!$rc) {
      	    if (system("$SSH_SWITCH ls $SWITCH_CONF >> /dev/null") == 0) {
      		$cmd = "$SSH_SWITCH grep '^DATAVIP=' $SWITCH_CONF | cut -d= -f2";
      		$_ = `$cmd`;
      		chomp;
      	        $data_vip = "$_";
      	    }
      	}
    }
    return($data_vip);
} # get_data_vip #

sub get_admin_vip {
    # First, try the master node if it is reachable.
    my $admin_vip = undef;
    my $cmd = $PING." ".$INTERNAL_ADMIN_IP." 1 >> /dev/null 2>&1";
    my $rc = system($cmd);
    if (!$rc) {
      	if (system("$SSH_MASTER_NODE ls $SILO_CONFIG_PROPERTIES >> /dev/null") == 0) {
      	    $cmd = "$SSH_MASTER_NODE grep 'admin-vip=' $SILO_CONFIG_PROPERTIES | cut -d\\\" -f10";
      	    $_ = `$cmd`;
      	    chomp;
      	    $admin_vip = "$_";
      	}
    } 
    # If that fails, try the failover switch if it is reachable.
    if (!$admin_vip) {
      	my $cmd = $PING." ".$SWITCH_FAILOVER_IP." 1 >> /dev/null 2>&1";
      	my $rc = system($cmd);
      	if (!$rc) {
      	    if (system("$SSH_SWITCH ls $SWITCH_CONF >> /dev/null") == 0) {
      		$cmd = "$SSH_SWITCH grep '^ADMINVIP=' $SWITCH_CONF | cut -d= -f2";
      		$_ = `$cmd`;
      		chomp;
      	        $admin_vip = "$_";
      	    }
      	}
    }
    return($admin_vip);
} # get_admin_vip #

sub get_num_nodes {
    my $num_nodes = undef;
    # First, try the master node if it is reachable.
    my $cmd = $PING." ".$INTERNAL_ADMIN_IP." 1 >> /dev/null 2>&1";
    my $rc = system($cmd);
    if (!$rc) {
        if (system("$SSH_MASTER_NODE ls $NODE_CONFIG_PROPERTIES >> /dev/null") == 0) {
            $_ = `$SSH_MASTER_NODE grep honeycomb.cell.num_nodes $NODE_CONFIG_PROPERTIES`;
            chomp;
            if(/^honeycomb.cell.num_nodes\s*=\s*(\d+)$/) {
                $num_nodes = $1;
            }
        }
    }
    # If that fails, try various local files if they exist.
    if (!$num_nodes && -e $CLUSTER_PROPERTIES_FILE) {
        $_ = `grep honeycomb.cell.num_nodes $CLUSTER_PROPERTIES_FILE`;
        chomp;
        if(/^honeycomb.cell.num_nodes\s*=\s*(\d+)$/) {
            $num_nodes = $1;
        }
    }
    if (!$num_nodes && -e $CLUSTER_CONF_FILE) {
        $_ = `grep CLUSTERSIZE $CLUSTER_CONF_FILE | awk -F= {'print \$2'}`;
        chomp;
        $num_nodes = $_;
    }
    if (!$num_nodes && -e $NODE_CONFIG_PROPERTIES) {
        $_ = `grep honeycomb.cell.num_nodes $NODE_CONFIG_PROPERTIES`;
        chomp;
        if(/^honeycomb.cell.num_nodes\s*=\s*(\d+)$/) {
            $num_nodes = $1;
        }
    }
    $num_nodes = 16 if (!$num_nodes);
    return($num_nodes);
} # get_num_nodes #

sub check_connection() {
    my ($class, $remote_hostname, $ping_cmd, $ssh_cmd, $test_command) = @_;
    print_it("INFO: Checking $remote_hostname connectivity...");
    my $rc = safe_run($ping_cmd);
    if (!$rc) {
        $cmd = $ssh_cmd." ".$test_command." >> /dev/null 2>&1";
        $rc = safe_run($cmd);
    }
    if (!$rc) {
        print_it("INFO: Connected to $remote_hostname successfully.");
    } else {
        print_it("ERROR: Cannot connect to $remote_hostname. Cannot extract data.");
        exit(2);
    }
} # check_connection #

sub check_version(){
    my ($class, $remote_hostname, $ssh_cmd, $version_file, $minimum_version) = @_;
    print_it("INFO: Checking $remote_hostname version in $version_file...");
    my $version = undef;
    my $cmd = undef;
    if ($remote_hostname =~ /switch/) {
        $cmd = $ssh_cmd." cat $version_file | cut -d'/' -f7";
    } elsif ($remote_hostname =~ /admin/) {
        $cmd = $ssh_cmd." $version_file";
    } else {
        $cmd = $ssh_cmd." head -1 $version_file";
    }
    $_ = `$cmd`;
    chomp;
    if ($remote_hostname =~ /switch/) {
        $version = $_;
        for ($version) {s/^\s+//; s/\s+$//; } # Remove leading and trailing whitespace
    } else {
        $version = $1 if(/\[(\S+)\]/);
    }
    if($version =~ /$minimum_version/) {
        print_it("INFO: $remote_hostname version is $version.");
    } else {
        print_it("ERROR: $remote_hostname must be version $minimum_version. Cannot extract data.");
        exit (3);
    }
} # check_version #

sub process_commands() {
    my ($class, $remote_hostname, $ssh_cmd, $request, $logdir) = @_;
    my $commands = basename($COMMAND_FILE);
    print_it("INFO: Running level $request commands from $commands on subsystem: $remote_hostname");
    $RETURN_STATUS = 0;

    # Open the command file
    if (!open(OO, $COMMAND_FILE)) {
        print_it("ERROR: Couldn't open $COMMAND_FILE command file.");
        exit(2);
    }

    if ($remote_hostname =~ /^hcb/) {
        $remote_hostname = "node";
    } elsif ($remote_hostname =~ /^.switch$/) {
        $remote_hostname = "switch";
    }

    while (my $line = <OO>) {
        chomp($line);
        next if ($line =~ /^[	 ]*#/ || $line =~ /^=/ || $line eq "");

        # Read parameters from the command file
        my ($level, $subsystem, $logfile, $command) = split(":", $line);

        # Proccess command if it is for this subsystem and the correct level
        if ($subsystem =~ /$remote_hostname/ && ($request == 0 || $level =~/$request/)) {
            for ($logfile) {s/^\s+//; s/\s+$//; } # Remove leading and trailing whitespace
            my $ignore_status = 0;
            if ($command =~ /<IgnoreStatus .*>/) {
                $ignore_status = $command;
                for ($ignore_status) {s/.*<IgnoreStatus// ; s/>.*//} # Capture status to expected
                for ($ignore_status) {s/^\s+//; s/\s+$//; } # Remove leading and trailing white space
                for ($command) {s/<IgnoreStatus .*> // ; s/<IgnoreStatus .*>$//} # Remove from command
            }
            my $pre_cmd = "";
            if ($command =~ /<PreCmd .*>/) {
                $pre_cmd = $command;
                for ($pre_cmd) {s/.*<PreCmd// ; s/>.*//} # Capture the pre command list to execute
                for ($pre_cmd) {s/^\s+//; s/\s+$//; } # Remove leading and trailing white space
                for ($command) {s/<PreCmd .*> //; s/<PreCmd .*>$//} # Remove from command
            }
            my $post_cmd = "";
            if ($command =~ /<PostCmd .*>/) {
                $post_cmd = $command;
                for ($post_cmd) {s/.*<PostCmd// ; s/>.*//} # Capture the post command list to execute
                for ($post_cmd) {s/^\s+//; s/\s+$//; } # Remove leading and trailing white space
                for ($command) {s/<PostCmd .*> //; s/<PostCmd .*>$//} # Remove from command
            }
            for ($command) {s/^\s+//; s/\s+$//; } # Remove leading and trailing whitespace

            # Proccess commands the have <1-n> ranges.
            if ($command =~ /<[0-9][0-9]*-[0-9][0-9]*>/) {
                my $rc = system("mkdir -p $logdir/$logfile");
                if ($rc) {
                    print_it("WARNING: Cannot create $logfile directory, error: $rc");
                    $RETURN_STATUS++;
                } else {
                    my ($cmd1, $range) = split("<", $command);
                    ($range) = split(">", $range);
                    my ($skipit, $cmd2) = split(">", $command);
                    my ($start,$stop) = split("-", $range);
                    for (my $i=$start; $i<=$stop; $i++) {
                        my $rc = system("touch $logdir/$logfile/$i");
                        if ($rc) {
                            print_it("WARNING: Cannot create $logfile/$i, error: $rc");
                            $RETURN_STATUS++;
                        } else {
                            print_it("INFO: Running '$cmd1$i$cmd2' command, output to '$logfile/$i' logfile.");
                            my $cmd = "$ssh_cmd \"$cmd1$i$cmd2 ; echo ExtractCmdStatus=\\\$?\ \" >$logdir/$logfile/$i 2>&1";
                            print_it($cmd);
                            $rc = system("$cmd");
                            check_ssh_status($rc, "$cmd1$i$cmd2", "$logdir/$logfile/$i", $ignore_status);
                        }
                    }
                }

            # Proccess <copy> commands
            } elsif ($command =~ /^<copy> /) {
                my $rc = system("mkdir -p $logdir/$logfile");
                if ($rc) {
                    print_it("WARNING: Cannot create $logfile directory, error: $rc");
                    $RETURN_STATUS++;
                } else {
                    my (@array) = split(" ", $ssh_cmd);
                    my $ip = $array[$#array];
                    my ($copy, $copy_target) = split(" ", $command);
                    print_it("INFO: Copying '$copy_target' to the '$logfile' directory.");
                    my $cmd = "scp -pr root\@$ip:$copy_target $logdir/$logfile 2>&1";
                    print_it($cmd);
                    $rc = system("$cmd");
                    if ($rc && $rc != $ignore_status) {
                        print_it("WARNING: The scp to copy $copy_target failed with status: $rc");
                        $RETURN_STATUS++;
                    }
                }

            # Proccess the <prtvtoc> command
            } elsif ($command =~ /^<prtvtoc>/) {
                my $rc = system("mkdir -p $logdir/$logfile");
                if ($rc) {
                    print_it("WARNING: Cannot create $logfile directory, error: $rc");
                    $RETURN_STATUS++;
                } else {
                    my $cmd = $ssh_cmd." \"echo | format | grep DEFAULT\" ";
                    my @lines = `$cmd`;
                    for my $line (@lines) {
                        for ($line) {s/^\s+//; s/\s+$//; }
                        my ($slice, $disk) = split(" ",$line);
                        my $rc = system("touch $logdir/$logfile/${disk}s2");
                        if ($rc) {
                            print_it("WARNING: Cannot create $logfile/${disk}s2, error: $rc");
                            $RETURN_STATUS++;
                        } else {
                            print_it("INFO: Running 'prtvtoc /dev/rdsk/${disk}s2' command, output to '$logfile/${disk}s2' logfile.");
                            my $cmd = "$ssh_cmd \"prtvtoc /dev/rdsk/${disk}s2\ ; echo ExtractCmdStatus=\\\$? \" >$logdir/$logfile/${disk}s2 2>&1";
                            print_it($cmd);
                            $rc = system("$cmd");
                            check_ssh_status($rc, "prtvtoc /dev/rdsk/${disk}s2", "$logdir/$logfile/${disk}s2", $ignore_status);
                        }
                    }
                }

            # Process simple commamds
            } else {
                my $rc = system("touch $logdir/$logfile");
                if ($rc) {
                    print_it("WARNING: Cannot create $logfile file, error: $rc");
                    $RETURN_STATUS++;
                } else {
                    print_it("INFO: Running '$command' command, output to '$logfile' logfile.");
                    if ($remote_hostname =~ /^admin/) {
                        my $cmd = "$ssh_cmd \"$command\" >$logdir/$logfile 2>&1";
                        print_it($cmd);
                        $rc = system("$cmd");
                        check_admin_status($rc, "$command", "$logdir/$logfile");
                    } else {
                        my $cmd = "$ssh_cmd \"$pre_cmd $command $post_cmd; echo ExtractCmdStatus=\\\$? \" >$logdir/$logfile 2>&1";
                        print_it($cmd);
                        $rc = system("$cmd");
			check_ssh_status($rc, "$command", "$logdir/$logfile", $ignore_status);
                    }
                }
            }
        }
    }
    # Close the command file
    close(OO);
    return($RETURN_STATUS);
} # process_commands #

sub run_celltest() {
    my ($class, $remote_hostname, $ssh_cmd, $logdir, $options) = @_;
    $logdir = "$logdir/celltest";
    my $command = "celltest";
    my $logfile = "Summary";
    my $output = "Output";
    my $copy_target = undef;
    $RETURN_STATUS = 0;

    my $rc = system("mkdir -p $logdir");
    if ($rc) {
        print_it("WARNING: Cannot create $logdir directory, error: $rc");
        return($RETURN_STATUS++);
    }
    $rc = system("touch $logdir/$logfile");
    if ($rc) {
        print_it("WARNING: Cannot create $logfile file, error: $rc");
        return($RETURN_STATUS++);
    }
    $rc = system("touch $logdir/$output");
    if ($rc) {
        print_it("WARNING: Cannot create $logdir/$output directory, error: $rc");
        return($RETURN_STATUS++);
    }
    $data_vip = get_data_vip();
    if (!$data_vip) {
        print_it("WARNING: Cannot determine the Data VIP to run '$command'.");
        return($RETURN_STATUS++);
    }
    $admin_vip = get_admin_vip();
    if (!$admin_vip) {
        print_it("WARNING: Cannot determine the Admin VIP to run '$command'.");
        return($RETURN_STATUS++);
    }
    $num_nodes = get_num_nodes();
    if (!$num_nodes) {
        print_it("WARNING: Cannot determine the Node count to run '$command'.");
        return($RETURN_STATUS++);
    }
    print_it("INFO: Running '$command $data_vip $admin_vip $num_nodes' command, output to '$logfile' logfile.");
    my $cmd = "$ssh_cmd \"$command $data_vip $admin_vip $num_nodes ; echo ExtractCmdStatus=\\\$? \" >$logdir/$logfile 2>&1";
    print_it($cmd);
    $rc = system("$cmd");
    my $status = check_ssh_status($rc, "$command", "$logdir/$logfile", 0);

    # Copy the remote output file to the local $output file if the remote output file exists
    $cmd = "grep '^Test log:' $logdir/$logfile";
    $_ = `$cmd`;
    chomp;
    (my $skip1, my $skip2, $copy_target) = split(" ", $_);
    if (system("$ssh_cmd ls $copy_target >> /dev/null") == 0) {
        my (@array) = split(" ", $ssh_cmd);
        my $ip = $array[$#array];
        print_it("INFO: Copying $copy_target output from $remote_hostname to '$output'.");
        $cmd = "scp -pr root\@$ip:$copy_target $logdir/$output";
        print_it($cmd);
        $rc = system("$cmd >>$logdir/$logfile 2>&1");
        if ($rc) {
            print_it("WARNING: The scp to copy $copy_target failed with status: $rc");
            return($RETURN_STATUS++);
        }
    }
    return($RETURN_STATUS);
} # run_celltest #
               
sub run_log_scraper() {
    my ($class, $remote_hostname, $ssh_cmd, $logdir, $options) = @_;
    $logdir = "$logdir/log_scraper";
    my $command = "log_scraper.pl";
    my $logfile = Summary;
    my $output = "Output";
    my $copy_target = "/var/adm/scraper/*/*";
    $RETURN_STATUS = 0;

    my $rc = system("mkdir -p $logdir");
    if ($rc) {
        print_it("WARNING: Cannot create $logdir directory, error: $rc");
        return($RETURN_STATUS++);
    }
    $rc = system("touch $logdir/$logfile");
    if ($rc) {
        print_it("WARNING: Cannot create $logfile file, error: $rc");
        return($RETURN_STATUS++);
    }
    $rc = system("mkdir -p $logdir/$output");
    if ($rc) {
        print_it("WARNING: Cannot create $logdir/$output directory, error: $rc");
        return($RETURN_STATUS++);
    }
    print_it("INFO: Running the '$command' on $remote_hostname with options '$options'");
    my $cmd = "$ssh_cmd \"$command $options ; echo ExtractCmdStatus=\\\$? \" >$logdir/$logfile 2>&1";
    print_it($cmd);
    $rc = system("$cmd");
    my $status = check_ssh_status($rc, "$command", "$logdir/$logfile", 0);

    # Determine the list of files to copy and copy them
    $cmd = "$ssh_cmd \"ls -1 $copy_target\"";
    print_it("INFO: Copying $copy_target from $remote_hostname to '$output'.");
    print_it($cmd);
    open(MESSAGE_LIST, "$cmd |");
    while ($file = <MESSAGE_LIST>) {
        chomp($file);
        my $filename = basename($file);
        my $cmd = "$ssh_cmd \"cat $file\" >$logdir/$output/$filename 2>&1";
        print_it($cmd);
        $rc = system("$cmd");
        if ($rc) {
            print_it("WARNING: The copy of $file failed with status: $rc");
            $RETURN_STATUS++;
        } elsif (!-f "$logdir/$output/$filename") {
            print_it("WARNING: The copy $copy_target failed to copy one or more files.");
            $RETURN_STATUS++;
        }
    }

    return($RETURN_STATUS);
} # run_log_scraper #

sub copy_message_files() {
    my ($class, $remote_hostname, $ssh_cmd, $request, $logdir) = @_;
    my $output = "message_files";
    $logdir = "$logdir/$output";
    $RETURN_STATUS = 0;

    my $rc = system("mkdir -p $logdir");
    if ($rc) {
        print_it("WARNING: Cannot create $logdir directory, error: $rc");
        return($RETURN_STATUS++);
    }
    # Determine the list of files to copy and copy them
    my $copy_target = "/var/adm/messages*";
    my $cmd = "$ssh_cmd \"ls -1 $copy_target\"";
    if ($remote_hostname eq "pswitch") {
        $copy_target = "/var/log/messages*";
        $cmd = "$ssh_cmd \"ls -1 $copy_target\"";
    } elsif ($remote_hostname eq "sswitch") {
        $copy_target = "/var/log/messages*";
        $cmd = "$ssh_cmd \\\"ls -1 $copy_target\\\"";
    }
    print_it("INFO: Copying $copy_target from $remote_hostname to '$output'.");
    print_it($cmd);
    open(MESSAGE_LIST, "$cmd |");
    while ($file = <MESSAGE_LIST>) {
        chomp($file);
        my $filename = basename($file);
        my $cmd = "$ssh_cmd \"cat $file\" >$logdir/$filename 2>&1";
        if ($remote_hostname eq "pswitch") {
            $cmd = "$ssh_cmd \"cat $file\" >$logdir/$filename 2>&1";
        } elsif ($remote_hostname eq "sswitch") {
            $cmd = "$ssh_cmd \"cat $file\" >$logdir/$filename 2>&1";
        }
        print_it($cmd);
        $rc = system("$cmd");
        if ($rc) {
            print_it("WARNING: The copy of $file failed with status: $rc");
            $RETURN_STATUS++;
        }
    }

    return($RETURN_STATUS);
} # copy_messages #

sub copy_dump_files() {
    my ($class, $remote_hostname, $ssh_cmd, $request, $logdir) = @_;
    my (@array) = split(" ", $ssh_cmd);
    my $ip = $array[$#array];
    my $output = "dump_files";
    $logdir = "$logdir/$output";
    $RETURN_STATUS = 0;
    print_it("INFO: Copying crash and core dump files from $remote_hostname to '$output'.");

    my $dump_dir = undef;
    if (system("$ssh_cmd ls $DUMPADM_CONF >> /dev/null") == 0) {
      	my $cmd = "$ssh_cmd grep 'DUMPADM_SAVDIR=' $DUMPADM_CONF";
        print_it($cmd);
      	$dump_dir = `$cmd`;
      	chomp;
        for ($dump_dir) {s/.*DUMPADM_SAVDIR=//; s/\s+$//}
        if ($dump_dir) {
            my $rc = system("mkdir -p $logdir/crash");
            if (!$rc) {
                print_it("INFO: Copying '$dump_dir' to the '$output/crash' directory.");
                my $cmd = "scp -pr root\@$ip:$dump_dir $logdir 2>&1";
                print_it($cmd);
                $rc = system("$cmd");
                if ($rc) {
                    print_it("WARNING: The scp to copy $dump_dir failed with status: $rc");
                    $RETURN_STATUS++;
                }
            } else {
                print_it("WARNING: Cannot create $logdir/crash directory, error: $rc");
                $RETURN_STATUS++;
            }
        } else {
                print_it("WARNING: Cannot determine the dumpadm directory path to run '$command'.");
                $RETURN_STATUS++;
        }
    } else {
        print_it("WARNING: Cannot locate the $DUMPADM_CONF file.");
        $RETURN_STATUS++;
    }

    my $core_dir = undef;
    if (system("$ssh_cmd ls $COREADM_CONF >> /dev/null") == 0) {
      	my $cmd = "$ssh_cmd grep 'COREADM_INIT_PATTERN=' $COREADM_CONF";
        print_it($cmd);
      	$core_dir = `$cmd`;
      	chomp;
        for ($core_dir) {s/.*COREADM_INIT_PATTERN=//; s/\/%.*$//; s/\s+$//}
        if ($core_dir) {
            my $rc = system("mkdir -p $logdir/core");
            if (!$rc) {
                print_it("INFO: Copying '$core_dir' to the '$output/core' directory.");
                my $cmd = "scp -pr root\@$ip:$core_dir $logdir 2>&1";
                print_it($cmd);
                $rc = system("$cmd");
                if ($rc) {
                    print_it("WARNING: The scp to copy $core_dir failed with status: $rc");
                    $RETURN_STATUS++;
                }
            } else {
                print_it("WARNING: Cannot create $logdir/core directory, error: $rc");
                $RETURN_STATUS++;
            }
        } else {
            print_it("WARNING: Cannot determine the coreadm directory path to run '$command'.");
            $RETURN_STATUS++;
        }
    } else {
        print_it("WARNING: Cannot locate the $COREADM_CONF file.");
        $RETURN_STATUS++;
    }

    return($RETURN_STATUS);
} # copy_dump_files #

sub check_ssh_status() {
    my ($rc, $command, $logfile, $ignore_status) = @_;
    if ($rc) {
        print_it("WARNING: The ssh for '$command' failed with status: $rc");
        return(++$RETURN_STATUS);
    }
    my (@array) = split(" ", $command);
    my $cmd = $array[0];
    $rc = system("grep \"$cmd: command not found\" $logfile");
    if (!$rc) {
        print_it("WARNING: The '$cmd' command was not found.");
        return(++$RETURN_STATUS);
    }
    $rc = system("grep \"$cmd: No such file or directory\" $logfile");
    if (!$rc) {
        print_it("WARNING: There is no such file or directory as '$cmd'.");
        return(++$RETURN_STATUS);
    }
    $cmd = "grep 'ExtractCmdStatus=[0-9][0-9]*' $logfile";
    $_ = `$cmd`;
    chomp;
    my ($skip, $status) = split("=", $_);
    if ($status && $status != $ignore_status) {
        print_it("WARNING: The '$command' command failed with status: $status");
        return(++$RETURN_STATUS);
    }
} # check_ssh_status #

sub check_admin_status() {
    my ($rc, $command, $logfile) = @_;
    if ($rc) {
        print_it("WARNING: The ssh for $command failed with status: $rc");
        return(++$RETURN_STATUS);
    }
    my (@array) = split(" ", $command);
    my $cmd = $array[0];
    $rc = system("grep \"$cmd; command not found\" $logfile");
    if (!$rc) {
        print_it("WARNING: The '$cmd' command was not found.");
        return(++$RETURN_STATUS);
    }
    $rc = system("grep \"Usage: $cmd\" $logfile");
    if (!$rc) {
        print_it("WARNING: Invalid usage of the '$cmd' command.");
        return(++$RETURN_STATUS);
    }
} # check_admin_status *

1;
