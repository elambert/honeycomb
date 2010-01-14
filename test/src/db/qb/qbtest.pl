#!/usr/bin/perl -w

#############################################
# Sample use cases of QBDB module
#
# Author: Daria Mehra
# Revision: $Id: qbtest.pl 10856 2007-05-19 02:58:52Z bberndt $
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
use QBDB;

my $run = post_run( command => "/foo/bar -duh",
		    bed => "brand new testbed",
		    tester => "dm155201",
		    start_time => "05-01-20 16:45:00",
		    env => "MOOD=GREAT FAIL=WILL",
		    comments => "First try at auto inserting",
		    );

print "SUCCESS: Inserted new run: ID=$run \n";

my $result = post_result( testproc => "QBTest",
		     parameters => "take=1",
		     run => $run,
		     submitter => "dm155201",
		     build => "qb_testware",
		     notes => "I hope this works",
		     );

print "SUCCESS: Inserted new result: ID=$result \n";

my $metric = post_metric( result => $result,
			  name => "retries",
			  units => "count",
			  datatype => "int",
			  value => "4",
			  at_time => "05-01-21 14:00:48",
			  );

print "SUCCESS: Inserted new metric: ID=$metric \n";

$metric = post_metric( result => $result,
		       name => "speed",
		       units => "count",
		       datatype => "int",
		       value => "150",
		       );

print "SUCCESS: Inserted another metric: ID=$metric \n";

$result = post_result( id => $result,
		       status => "fail",
		       timestamp => "05-01-21 15:00:00",
		       notes => "This sure did not work",
		       );

print "SUCCESS: Updated result: ID=$result \n";

$run = post_run( id => $run,
		 end_time => "05-01-21 15:10:00",
		 comments => "First try at updating",
		 );

print "SUCCESS: Updated run: ID=$run \n";

post_bug ( result => $result,
	   bug => "BUG1235"
	   );

print "SUCCESS: Posted bug \n";

				


