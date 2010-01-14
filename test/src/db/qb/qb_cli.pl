#!/usr/bin/perl -w

############################################
# CLI to post data to QB database
# 
# Use to post test runs, results, metrics
#
# XXX: This program requires LWP::UserAgent perl module,
# which is not installed on hc-dev2 or client machines.
# Use qb_cli.sh instead - same functionality.
#
# Author: Daria Mehra
# Revision: $Id: qb_cli.pl 10856 2007-05-19 02:58:52Z bberndt $
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



#############################################

use strict;
use LWP::UserAgent;

my $target = shift;
my $infile = shift;

my $url = "http://qb.sfbay.sun.com/qb_post.cgi";

my $www = new LWP::UserAgent();
my $res = $www->post($url, Content_Type => 'form-data',
		     Content => [ target => $target, infile => [$infile]]);
if ($res->is_success) {
    if ($res->content =~ /QB.*ID = (\d+)/) {
	print "QB POST: $target ID = $1 OK \n";
    } else { # this shouldn't happen
	print $res->content;
    }
} else {
    die $res->status_line;
}

