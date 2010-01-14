#!/usr/bin/perl -w
#
# $Id: qb_update_reports.cgi 10856 2007-05-19 02:58:52Z bberndt $
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
#############################################################
# CGI wrapper around qb_reports.pl to update report contents.
#############################################################

use strict;
use CGI;
use CGI::Carp qw( fatalsToBrowser );

# Report definition file qbreports.xml lives here in svn checkout
my $SVNDOCROOT = "/home/www/hc-web/svn/docs";
# Subversion client binary with setuid, runs as build user
my $SVNBIN = "/usr/local/apache/cgi-bin/qb/svn_build";

my $cgi = new CGI();

# update suite-level report
my $cmd = $cgi->param('cmd');
$cmd = "suite" unless (defined $cmd);

# which suite
my $target = $cgi->param('target');
$target = "ALL" unless (defined $target);

# which HTML page to display after updating
my $page = $cgi->param('page');

# which action to take: update HTML, or reload suitedef
my $action = $cgi->param('action');

# reload suite definitions
if ($action eq "-L") {
    
    # remove lock file, if any was left in svn checkout (workaround for recurring issue)
    `$SVNBIN cleanup --config-dir=/home/build/.subversion ${SVNDOCROOT}/Test`;
        
    # update report definition file in local subversion checkout
    `$SVNBIN update --config-dir=/home/build/.subversion --non-interactive -N ${SVNDOCROOT}/Test/qbreports.xml`;

    `./qb_reports.pl $cmd $action $target`; # Reload
    $action = "-H"; # should update HTML
}

# execute the update script which regenerates HTML page
if ($action eq "-H") {
    `./qb_reports.pl $cmd $action $target`;
}

# redirect back to (now updated) HTML page
print "Status: 302 Refreshed\nLocation: $page\n\n";

