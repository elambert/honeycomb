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
require 'params.pl';
require 'db.pl';

my $cgi = new CGI();

print $cgi->header;
print $cgi->start_html('flamebox - postresult.cgi');

my $srcfile = "";

if (@ARGV == 1) {

    my $logfile = parse_logfile(shift(@ARGV));

    # Print out the output.
    open(SRC, "<$logfile");
    
    while (<SRC>) {
	print $_;
    }

    close SRC;

    print $logfile;

} elsif (defined $cgi->upload('logfile')) {

    my $logfile = $cgi->upload('logfile');

    # Dump the results to a temporary file.
    open(DST, ">/tmp/postresult-$$");
    while (<$logfile>) {
	print DST $_;
    }
    close DST;

    my $logfile = parse_logfile("/tmp/postresult-$$");
    unlink("/tmp/postresult-$$");

    # Print out the output.
    open(SRC, "<$logfile");
    
    while (<SRC>) {
	print $_;
    }

    close SRC;

    my ($logpath,$runid,$suffix)=($logfile=~/^(.*)?\/(\d+)(\..*)$/);
    my $dir=$runid%256; 
    if(! -d("$logpath/$dir")){
        mkdir("$logpath/$dir",755);
	`chmod 755 $logpath/$dir`;
    }

    my $newlogfile="$logpath/$dir/$runid.log";
    `mv $logfile $newlogfile`;

    system("gzip", "$newlogfile");

} else {
    # In CGI mode and no log file specified.
    print $cgi->start_multipart_form(-enctype=>"multipart/form-data");
    print $cgi->filefield(-name=>'logfile');
    print $cgi->submit('submit');
    print $cgi->end_form;
}

print $cgi->end_html;
print "\n";

