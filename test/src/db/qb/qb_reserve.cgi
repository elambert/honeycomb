#!/usr/bin/perl -w

#
# $Id: qb_reserve.cgi 11669 2007-11-21 18:55:37Z jd151549 $
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

############################################
# CGI interface to QB reservations database
# 
# Author: Joshua Dobies
############################################

use strict;
use QBDB;
use Data::Dumper;
use CGI;
use CGI::Carp qw( fatalsToBrowser );
use Tie::IxHash;

# CONSTANTS
my $ACTION = "action";
my $OWNER = "owner";
my $RESOURCE = "resource";
my $RESERVATION = "reservation";
my $LIST = "list";
my $RESERVE = "reserve";
my $UNRESERVE = "unreserve";

my $cgi = new CGI();
my %cgi_params = $cgi->Vars;

my $action = $cgi_params{$ACTION};

sub list()
{
  my $reservables = reservables();
  print "<table>\n";
  print "<tr bgcolor=lightgray>\n";
  print "<td>Resource</td><td>Owned By</td><td>Reserved Since</td>\n";
  print "</tr>\n";
  foreach my $tuple (@$reservables)
  {
    my $action = $RESERVE;
    my $bgcolor = "lightgreen";
    my $owner = "";
    my $since = "";
    my $reservation = "";

    my ($reservable) = @$tuple;
    my $owner_data = currentReservation($reservable);
    if (defined($owner_data))
    {
      ($reservation, $owner, $since) = @$owner_data;
      $action = $UNRESERVE;
      $bgcolor = "pink";
    }

    print "<form action=\"qb_reserve.cgi\" method=\"post\">\n";
    print "<input type=\"hidden\" name=\"${ACTION}\" value=\"${action}\">\n";
    print "<input type=\"hidden\" name=\"${RESOURCE}\" value=\"${reservable}\">\n";
    print "<tr bgcolor=${bgcolor}>\n";
    print "<td><a href=\"qb_lab.cgi?${RESOURCE}=${reservable}\">${reservable}</a></td>\n";
    if (!defined($owner_data))
    {
      print "<td>\n";
      print "<input type=\"text\" name=\"${OWNER}\">\n";
      print "</td>\n";
    }
    else
    {
      print "<input type=\"hidden\" name=\"${RESERVATION}\" value=\"${reservation}\">\n";
      print "<td>${owner}</td>\n";
    }
    print "<td>${since}</td>\n";
    print "<td><input type=\"submit\" value=\"${action}\"></td>\n";
    print "</tr>\n";
    print "</form>\n";
  }
  print "</table>\n";
}

sub reserve()
{
  my $resource = $cgi_params{$RESOURCE};
  my $owner = $cgi_params{$OWNER};
  checkout($resource, $owner);
}

sub unreserve()
{
  my $reservation = $cgi_params{$RESERVATION};
  checkin($reservation);
}

if (defined($action))
{
  if ($action eq $RESERVE)
  {
    reserve();
  }
  elsif ($action eq $UNRESERVE)
  {
    unreserve();
  };
  db_commit();
}

print $cgi->header;
print "\n";
print $cgi->start_html('QB RESERVE');
print "\n";
list();
db_commit();
print $cgi->end_html;
print "\n";
