#
# $Id: params.pl 11634 2007-11-01 22:16:51Z sm193776 $
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
# These are the default repository, module, and branch.
#
$repository = $ENV{FB_REPOSITORY};
if (!defined($repository)) {
    $repository = "/usr/local/svnrepos/honeycomb";
}
$module = $ENV{FB_MODULE};
if (!defined($module)) {
    $module = "honeycomb";
}
$branch = $ENV{FB_TRUNK};
if (!defined($branch)) {
    $branch = "trunk";
}

#
# The default spacing for the rows in seconds.
#
$period = 5 * 60;

#
# The default time in seconds for page.
#
$length = 6 * 60 * 60;

#
# Unix directory path and the url path, respectively, to the task images 
# (i.e., vertical text for the column headings in Flamebox output table), 
# and type of image files.
#
$htmlroot = ""; # this might be /flamebox or nothing, depending on config
$imagepath = "/export/home/build/svn/trunk/tools/flamebox/html/task_img";
$imageurl = "$htmlroot/task_img";
$imagetype = "png"; # png, gif, or whatever is supported by your GD.pm

#
# This the unix directory path and the url path, respectively, to the logs.
#
$logpath = "/export/home/flamebox/logs";
$logurl = "/fbox-cgi/showlog.cgi";

#
# Web server
$flamebox_home = "http://hc-flamebox.sfbay.sun.com";

# [dmehra] Version control system - where flamebox pulls up checkins from
#
# Currently supported are "cvs" (CVS repository with Bonsai Web viewer)
# and "svn" (Subversion repository with recent svn-compatible ViewCVS viewer)
#
# Note: the rest of the variables are named "bonsai*" when they really apply to any VC.
# I left the names intact for easier backwards compatibility.
#
#$vctype = "cvs"; # default
$vctype = "svn"; 

#
# This is the url to bonsai query interface. 
# Include the treeid parameter here if using CVS (not for Subversion).
#
$bonsaiurl = "https://subversion.sfbay.sun.com/viewcvs/view/";

#
# These are the parameters to the flamebox database.
#
$dburl = "DBI:mysql:flamebox:hc-web";
$dbuser = "nobody";
$dbpasswd = "";

#
# These are the parameters to the bonsai database.
#
$bonsai_dburl = "DBI:mysql:viewcvs:hc-web";
$bonsai_dbuser = "nobody";
$bonsai_dbpasswd = "";

#============================================================================
#
# User-extensible subroutines
#

#
# parse_line extension for honeycomb purposes
# i didn't want to further clutter parse_line space
#
sub parse_line 
{
    my ($line) = @_;
    $flamebox_email = "-email"; # hack
 
    # first match on things that we always want to flag as errors
    my @always_errors = ( "request failed with IOException", # never correct
                          "OA_UT:.*NEW FAILURE.*",
                         );
    foreach (@always_errors) {
	return "ERROR" . $flamebox_email if ($line =~ /$_/);
    }

    # then match expressions that should be skipped and regarded as OK
    my @ok = ( "failed as expected", # not an error
               "error.gif", # these are flamebox's own files that show up
               "warning.gif", # as errors/warnings
               "error.c", # ntp code
               "error.h", # ntp code
               "error.o", # ntp code
               "error.Tpo", # ntp code
               "Got expected exception", # not an error
               "As expected", # not an error
               "Possibly fixed", # not an error
               "failed correctly", # not an error
               "WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!", # ah, ssh 
               "unmappable character for encoding ASCII", # from external src
               "deprecated-list.html", # file checked in with this name
               " has been deprecated", # sigh, errors in external stuff
               "Dependency checking failed", # our pkgadds are sketchy...
               "ERROR: information for.*was not found", # ok if pkg not found
               "ERROR - cannot access local node proxy", # happens if nodemgr_mailbox.sh called too early
               "parameter.*PSTAMP.*set to", # normal pkg build msg
               "INFO:", # overused now...
               "INF:", # overused now...
               "external/jetty-4.2.20/.*/error.*",
               "PASS RESULT_ID",
               ".*failed.*IOException.*HttpRecoverableException.*",
               "OA_UT:.*", # unit test trick to skip all other messages 
               "WRN: Tag set is not active skipping set up",
               "ignoring until bug 6273390 is fixed",
               "ERR: Found undesirable string match \.\*\?\-\*\?\-\.\*\?\-\.\*\? in the returned error msg:",
               "ERR: ERROR: failed to retrieve object:",
               "ERR: ERROR: failed to retrieve file:",
               "WRN: Could only find deleted queriable metadata entries",
               "ERR: ERROR: expected 1 results but got 0 for query",
               "DO NOT RUN THIS TEST WHILE LOAD IS GOING ON FROM OTHER ACTIVITIES",
               "IT WILL DESTROY YOUR SYSTEM CACHES",
               "WebDAVCache::GET max=10000, low=8000",
               "WebDAVCache::SET max=100, low=80",
               "WebDAVCache::SET max=10000, low=8000",
	       );
    foreach (@ok) {
	return "OK" if ($line =~ /$_/);
    }

    # then match expressions that require action: INFO, ERROR, WARNING
 
    my @info = ( "REGRESSION TEST TASK", # task delimiters 
                 "INSTALL TASK",
                 "BUILD TASK",
		 "SKIPPING", # another major step: skipping a test
		 "START RUN_ID", # another major step test run started
		 # "START SUITE", # another major step test suite started
		 "END SUITE", # another major step test suite ended
		 "END RUN_ID", # another major step test run ended
		 );
    foreach (@info) {
	return "INFO" if ($line =~ /$_/);
    }

    my @warn = ( "WRN:",
                 #"ERR: Failed to", # XXX ack, sometimes this is ok, more
                                   # work needed on this one...real
                                   # failures will be caught on the "FAIL
                                   # RESULT" match below
                 #"Server hcb.* failed to", # like above
                 "ERR: ERROR: ", # like above
                 "Exiting test with ERROR", # like above
                 "Found undesirable string ", # Open bug on this for now...
                 "audit failed", # some known audit issues currently...
                 "org.postgresql.util.PSQLException: ERROR.*out of range for type double precision", # some known audit issues currently...
		 );
    foreach (@warn) {
	return "WARNING" . $flamebox_email if ($line =~ /$_/);
    }

    my @error = ( 'BUILD FAILED', # line printed when build fails
                  'INSTALL/REGRESSIONTEST FAILED', #unable to get buildname for install and test tasks
                  'FAIL RESULT_ID', # line printed when a test fails
                  'Error:.*exit value.*\d+.*signal.*\d+.*core.*\d+', # line printed when tasks exit prematurely' 
                  'Error: all honeycomb nodes did not come online',
                  'Error: cluster did not get into desired state...exiting',
                  'Killed', # we timed out and were killed unexpectedly
                  'unexpected exception', 
                  'Test threw an exception, assuming failure',
                  'SUM:\s*FAIL',
                  'ERROR: HADB status is unknown',
                  # Data Path Regression Test Regular Expressions
                  'Range retrieve test: FAIL',
                  'Regression tests: FAIL',
                  'Interfaces test: FAIL',
                  'Negative tests: FAIL',
                  'Delete tests: FAIL',
                  # C API Regular Expressions
                  'C API compile: FAIL',
                  'C API testhcclient: FAIL',
                  'C API hctestharness: FAIL',
                  'C API hcload: FAIL',
                  'C API queryall: FAIL',
                  'C API Regression (Compile and Run): FAIL',
		  );
    foreach (@error) {
	return "ERROR" . $flamebox_email if ($line =~ /$_/);
    }

    # if nothing matched, proceed to parse_line
    return "OK";
}

1;

