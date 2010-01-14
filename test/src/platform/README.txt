$Id: README.txt 10856 2007-05-19 02:58:52Z bberndt $

Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.


HOW TO RUN THE PLATFORM TESTS:

I. INTRODUCTION

The Platform tests actually consist of a series of tests, which can be run in whole or 
in part. There are a number of flags to the startup scripts that tell the script
which tests to run, and on which nodes/hosts to run them. The two main control 
scripts are run.sh and platform.sh. It is recommended that you use run.sh unless
you either a) want to run ALL the tests in the suite OR b) you already are 
familiar enough with the platform tests to know which command-line flags to send
to the script. The platform.sh script has no interactive mode, while the run.sh
script will default to interactive mode in the absence of command-line arguments.

The tests that the Platform Test Suite is currently capable of running are:
	1) Simple hdparm tests to evaluate Disk throughput
	2) Bonnie++ tests to do more exhaustive analysis of disk I/O**
	3) SMART Disk tests (on supported platforms) to test the SMART
	   disk interface
	4) Basic Network (TCP) I/O tests using netperf
	5) A more exhaustive (and lengthy) set of netperf tests

	** Since the Bonnie++ tests require mounted file systems, the Test Suite
	   can also initialize the disks, build file systems, and mount them
	   prior to running Bonnie++

II. RUNNING WITH RUN.SH

The run.sh script provided can be used in either interactive mode where it will
ask the user what options to enable or disable, or those options can be controlled
via the command line. In addition, the run.sh script will allow the platform
tests to be run in a loop for a defined number of hours. In general, for every 
Command Line flag that turns ON a specific test there is an Upper Case version 
of the same flag that will turn OFF that test. This can be useful in some 
situations that will be described (trust me). A word of caution: ORDER AND CASE
MATTER!

Command line flags:

	-a :  Will run *all* the tests. This flag will cause the disks to be 
	      initialized, file systems created, and disks mounted, then the
	      Bonnie++, hdparm and SMART tests will all be run on the disks.
	      The complete set of Network I/O tests (both basic and extended)
	      will also be run. NOTE: Using this flag will cause all FURTHER
	      flags to be ignored. If you would like to define a HOST or NODES
	      file to be read, those options must come BEFORE the -a flag!
	      Also, the -a flag will cause all other previously set flags
	      to be reset, and will ignore further command-line arguments.

	-b|B: This flag will turn ON/OFF the Bonnie++ tests. This flag can be
	      used to override the -d|D flag is used AFTER the -d|D flag.

	-d|D: This flag turns ON/OFF *ALL* Disk testing. If you want to run
	      ALL disk tests, this is the flag for you. Using further disk-test
	      related flags (-b|B, -i|I, -s|S) can override this, as long as
	      those options are set AFTER the -d|D flag.

	-e|E: Causes the Extended Network Tests to be run. These tests take an
	      extremely long time to run, and can be error-prone, but they will
	      also place a very heavy load on the Network Interconnect and the
	      CPUs of the systems being tested. It is NOT recommended that these
	      tests be run over the SWAN or open internet as the amount of
	      traffic generated can be quite high.

	-f  : Takes one argument! This flag defines the file containing the list
	      of NODES to be tested. NOTE: This may be different than the list
	      of HOSTS to be tested! This file should contain a list of IP
	      addresses of the INTERNAL Honeycomb nodes to be tested (typically
	      all of the 10.123.45.x variety). It is used by the Network Tests
	      to connect to all of the nodes in a cluster from WITHIN the 
	      cluster, not from outside the cluster. If not specified, this
	      defaults to ./NODES. The format of this file is ONE IP address
	      per line. No comments/blank lines allowed.

	-h  : Takes one argument! This flag defines the file containing a list
	      of HOSTS to be tested. Note that this may (or may not be) the same
	      as the list of NODES to be tested, depending on if you are running 
	      the Platform tests from within the cluster. If you are NOT running
	      the tests from within the cluster itself, you should specify a list
	      of host names or addresses by which the cluster nodes are acessible
	      from OUTSIDE the cluster. There should be the same number of HOSTS
	      in this file as there are NODES in the NODES file. If not specified, 
	      this defaults to the same file as the NODES file (so it *that* was
	      not specified either, it defaults to ./NODES as well). The format
	      of this file is ONE hostname or IP address per line. No comments/
	      blank lines allowed.

	-i|I: Turns ON/OFF disk initialization. If turned ON (-i) all disks found
	      in /proc/partitions will be initialized as follows:

		Partition 1: from block 1 - 123 as ext3
		Partition 2: from block 124 - end of disk as xfs

		For each disk, the first (ext3) partition will NOT be mounted, but
	        the second partition (xfs) WILL be mounted as /data/x where x is
	        the disk number (from 0 - the number of disks). 

	      ONLY the xfs partitions will be mounted and tested with Bonnie++.

	-l:   Takes one Argument! This allows you to specify the LOCATION for your
	      test LOG files. The default is $HOME. Regardless of where you set
	      this LOG LOCATION to be, a new directory in that location will
	      be created of the form LOG-x-x-x with the x's being replaced
	      by HR-MIN-MONTH, except in the case of a long-running tests,
	      in which case the directory CONSOLIDATED_LOGS-x-x-x-x will be 
	      created where the x's are START_HR-END_HR-MIN-MONTH.
	
	-n|N: Turns ON or OFF Network testing. NOTE: This only turns ON or OFF
	      the BASIC Network Tests, not the extended (or exhaustive) tests.
	      if this flag is set from the Command Line WITHOUT either the
 	      -e or -E flags, you will get an interactive question about the
	      exhaustive network tests. If you KNOW you only want to run the
	      Basic Network Tests, you're best off to specify -n -E if you
	      don't want to answer questions.

	-s|S: Turns ON or OFF SMART testing. Until SMART is supported on SCSI
       	      or SCSI-emmulated drives such as those in Honeycomb, turning on
	      this test will fail.

	-t|T: Takes one Argument! This specifies the number of HOURS to run the
	      Platform tests. To initiate a long-running test, specify the length
	      of time in hours, for the test to run. To run the Platform tests
	      only ONCE, use 0 (zero) for the number of hours.

All of these arguments can be set via Command Line flags as noted above. If run.sh
is executed with NO Command Line arguments, or with only the -l, -h, or -f options
(or any combination of those options), you will enter Interactive Mode and will be
prompted for which options to enable or disable. 

NOTE: The LOG LOCATION, NODES FILE, and HOST FILE can ONLY be set to values other
than the default via Command Line arguments. These values cannot be changed through
the Interactive Process.

III. RESULTS FROM RUN.SH

If you are running run.sh in a non-long-running test (using a -t 0, or defining
Duration as 0 via the Interactive Process) you will be given an opportunity at the
END of the test run to view the results of your tests. Regardless of whether or not
you choose to view the results then, all the tests results are available in
LOG_LOCATION/platform.log (where LOG_LOCATION is either the LOG LOCATION you
specified on the command line, or the default location. Either way the run.sh
script will inform you of the location of your results.).

IV. RUNNING FROM PLATFORM.SH

It is possible to run the Platform Tests directly from platform.sh, bybassing
run.sh, though I don't recommend it. ALL of the same flags are accepted by platform.sh
as by run.sh with the following exceptions:

	-t:    This flag for test-duration is NOT supported by platform.sh

In addition, whereas the DEFAULT action of run.sh is to drop into Interactive
Mode in the absence of Command Line arguments, the DEFAULT action of platform.sh
is to simply turn ON ALL tests (including disk initialization!) and run them
without further notice.

V. RESULTS FROM PLATFORM TESTS

At this point in time, ONLY the results from hdparm and BASIC Network tests
are evaluated and interpreted by the platform tests. Bonnie++, SMART, and the
Exhaustive Network tests will return results in the form of log files written
to the LOG LOCATION specified, but they will NOT be interpreted for you by
the script. 

Results from the BASIC Network Tests and hdparm tests WILL be interpreted by 
the script and PASS/FAIL results reported.

VI. EXAMPLES

To run the Basic Disk and Network tests only once, without initializing the disks, 
where the list of hosts is in the file myhosts and the list of nodes is in mynodes:

	./run.sh -d -B -n -E -I -l /tmp/LOGS -h myhosts -f mynodes

To run the Basic Netowrk tests only, but for 10 hours using default LOG,
NODES and HOST files:

	./run.sh -n -E -D -t 10


