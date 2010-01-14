#!/usr/bin/perl

#
# Flamebox is a reimplementation of Tinderbox, roughly based on 
# Tinderbox 2 code by Ken Estes.
#
# Rewritten by Riverbed Technology, Inc.
# (C) 2003-2004 Riverbed Technology, Inc. All Rights Reserved.
#
# David Wu (davidwu@riverbed.com)
#
# This code contains portions copied from the Tinderbox 2 tool, which
# carries the following license:
#
## The contents of this file are subject to the Mozilla Public
## License Version 1.1 (the "License"); you may not use this file
## except in compliance with the License. You may obtain a copy of
## the License at http://www.mozilla.org/NPL/
##
## Software distributed under the License is distributed on an "AS
## IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
## implied. See the License for the specific language governing
## rights and limitations under the License.
##
## The Original Code is the Tinderbox build tool.
##
## The Initial Developer of the Original Code is Netscape Communications
## Corporation. Portions created by Netscape are
## Copyright (C) 1998 Netscape Communications Corporation. All
## Rights Reserved.
##
## Complete rewrite by Ken Estes, Mail.com (kestes@staff.mail.com).
##

use CGI;

use lib './../config';
use lib '.';
require '../config/params.pl';

# Need to do autoflush.
$| = 1;

my $cgi = new CGI();

# on linux, zcat might work.
# on solaris, gzcat is needed.
my $uncompresscmd = "gzcat";

print $cgi->header;

$file = "";

if ($cgi->param('file')) {
    $file = $cgi->param('file');
} elsif (@ARGV) {
    $file = shift(@ARGV);
}

if ($file eq "") {
    print $cgi->start_html('flamebox - showlog.cgi');
    print "<body>Need file parameter.</body>\n";
    print $cgi->end_html();
} else {
    if ($file =~ m/^(\d+\/)?\d+\.log\.gz$/) {
    	if((my $id)=($file=~m/^(\d+)\.log\.gz$/)){ # convert old-style to new
    		my $dir=$id%256;
    		$file="$dir/$file";
    	}
	system("$uncompresscmd", "$logpath/$file");
    } else {
	print "<body>Attempting to access non-log file: $file</body>\n";
	print $cgi->end_html();
    }
}

print "\n";

