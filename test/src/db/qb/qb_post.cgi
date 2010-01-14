#!/usr/bin/perl -w

############################################
# CGI interface to post data to QB database
# 
# Use to post test runs, results, metrics
# Supports HTTP post (file upload) and CLI mode
#
# Author: Daria Mehra
# Revision: $Id: qb_post.cgi 10856 2007-05-19 02:58:52Z bberndt $
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



############################################

use strict;
use QBParse;
use QBDB;
use CGI;
use CGI::Carp qw( fatalsToBrowser );


my $cgi = new CGI();
print $cgi->header;
print $cgi->start_html('QB POST');

if (defined $cgi->upload('infile') and
    defined $cgi->param('target')) { # CGI parameters given
    my $target = $cgi->param('target');
    my $id = post_data($target, $cgi->upload('infile'));
    print $cgi->hr, $cgi->h2("QB POST: $target ID = $id OK"), $cgi->hr;
    
} else { # display Web form
    
    print $cgi->hr;
    print $cgi->h2("Post data to QB database from an input file");
    print $cgi->hr;
    print $cgi->start_multipart_form( -enctype => "multipart/form-data");
    print "What do you want to post? \t";
    print $cgi->popup_menu( -name => 'target',
			    -values => ['run', 'result', 'metric', 'bug', 'build']);
    print $cgi->p;
    print "Where is the data? \t";
    print $cgi->filefield( -name => 'infile');
    print $cgi->submit('submit');
    print $cgi->end_form;
    print $cgi->hr;
    
}		

print $cgi->end_html;		


# given type of data to post (test run, result etc.)
# and a file handle to the data, post it to QB database
# returns ID of the posted item
#
sub post_data {
    my ($target, $infile) = @_; # infile is a file handle
    my $parse_data = "parse_".$target; 
    my $post_data = "post_".$target;
    no strict 'refs'; # permit using lexical as sub name
    my $data = &$parse_data($infile);
    my $id = &$post_data(%$data);
    return $id;
}


