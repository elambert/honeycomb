#!/usr/bin/perl -w
#
# $Id: qb_cleanup.pl 10856 2007-05-19 02:58:52Z bberndt $
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
use Getopt::Long qw(:config no_ignore_case bundling);
use POSIX;
use QBDB;

sub usage {

    print
<<EOF

NAME
    $0 - Clean up (rename, delete) testcases in QB database

SYNOPSIS
    $0 <delete|rename> <args>

    $0 delete -t testcase -s scenario

    $0 rename --testcase=from-testcase --scenario=from-scenario --tot=to-testcase --tos=to-scenario

DESCRIPTION
    Run with <delete> argument, this tool will delete the testcase/tag mapping in QB DB
    for the given testcase scenario. Test results will NOT be permanently deleted, but 
    they will no longer appear in reports.

    Run with <rename> argument, this tool will rename given testcase scenario to a different name.
    The testcase/tag mapping will remain the same, so the testcase will appear in reports
    just like it did previously, but with a different name.

    You may NOT rename tags this way. If you want the same testcase to appear in a different report,
    based on its tags, you should run this tool in delete mode, then re-run your modified testcase, so
    it will post a new result with desired tags.

EOF
;
    exit(1);
}

sub cleanup_failed {
    my ($msg) = @_;
    print "ERROR: $msg \n";
    exit(1);
}

sub select_testcase {
    my ($testcase, $scenario) = @_;

    my $select = "SELECT testcase.id FROM testproc, testcase \n";
    $select .= "WHERE testproc.name LIKE \"$testcase\" AND \n";
    $select .= "testcase.parameters LIKE \"$scenario\" AND \n";
    $select .= "testcase.testproc = testproc.id \n";

    my $result = db_select_arrayref($select);
    my $testcase_id = $result->[0]->[0];

    print "Testcase ID: $testcase_id \n";

    return $testcase_id;
}
    
sub delete_testcase_tags {
    my ($testcase, $scenario) = @_;

    if (!defined $testcase || !defined $scenario) {
        cleanup_failed("Undefined testcase/scenario names");
    }

    my $testcase_id = select_testcase($testcase, $scenario);
    if (!defined $testcase_id) {
        cleanup_failed("Testcase ID not found for testcase: $testcase, scenario: $scenario");
    }

    my $delete = "DELETE FROM testcase_tag WHERE testcase = $testcase_id \n";
    db_do($delete);
    db_commit();    

    print "Deleted testcase tags for testcase: $testcase, scenario: $scenario \n";
}

sub rename_testcase {
    my ($from_t, $from_s, $to_t, $to_s) = @_;

    if (!defined $from_t || !defined $from_s ||
        !defined $to_t || !defined $to_s) {
        cleanup_failed("Undefined testcase/scenario names");
    }
    
    my $source_id = select_testcase($from_t, $from_s);
    if (!defined $source_id) {
        cleanup_failed("Testcase ID not found for testcase: $from_t, scenario: $from_s");
    }

    my $target_id = select_testcase($to_t, $to_s);
    if (defined $target_id) {
        # The testcase with desired name/scenario already exists. Special case.
        
        # Will update results for the source testcase to point to target testcase.
        my $update_results = "UPDATE result SET testcase = $target_id WHERE testcase = $source_id";
        db_do($update_results);
        
        # Also update testcase_tag mapping from source to target testcase ID.
        my $update_ttags = "UPDATE testcase_tag SET testcase = $target_id WHERE testcase = $source_id";
        db_do($update_results);
    
    } else {
        # New testcase/scenario name. Perform simple rename.
    
        my $update_t = "UPDATE testproc SET name=\"$to_t\" WHERE name LIKE \"$from_t\" \n";
        db_do($update_t);
    }
    
    db_commit();
    print "Renamed testcase: $from_t, scenario: $from_s to testcase: $to_t, scenario: $to_s \n";
}

################################################################################
# main
################################################################################

my $cmd = undef;
$cmd = shift(@ARGV);

if (!defined $cmd || ($cmd =~ /help/) || ($cmd =~ /-h/)) {
    usage();
}

my ($cur_tname, $cur_sname, $to_tname, $to_sname);

if (!GetOptions("t|testcase=s" => \$cur_tname,
                "s|scenario=s" => \$cur_sname,
                "totestcase:s" => \$to_tname,
                "toscenario:s" => \$to_sname)) {
    usage();
}

if ($cmd eq "delete") {
    delete_testcase_tags($cur_tname, $cur_sname);
} elsif ($cmd eq "rename") {
    rename_testcase($cur_tname, $cur_sname, $to_tname, $to_sname);
} else {
    cleanup_failed("Command not supported: $cmd. Run $0 --help");
}

exit(0);

################################################################################
