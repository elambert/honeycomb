#!/usr/bin/perl -w

#
# $Id: qb_lab.cgi 10856 2007-05-19 02:58:52Z bberndt $

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
my $RESOURCE = "resource";
my $TUPLE = "tuple";

sub printAttribute($$)
{
  my ($name, $value) = @_;
  print("<tr bgcolor=\"lightgreen\">\n");
  print("<td><strong><small>${name}</small></strong></td>\n");
  print("<td>${value}</td>\n");
  print("</tr>\n");
}

sub absolutePath($)
{
  my ($resource) = @_;

  my $done = 0;
  my $path = "";
  while ($done == 0)
  {
    my $tuple = $resource->{tuple};
    my $class = $resource->{class};
    my $id = $resource->{id};
    my $tree = $resource->{tree};
    my $parent = $resource->{parent};
    $path = "&nbsp;/&nbsp;<a href=\"qb_lab.cgi?${RESOURCE}=${id}\">${id}</a>${path}";

    my $parents = loadResources("id = '${parent}' and tree = ${tree}", 0);
    if (!defined($parents))
    {
      $done = 1;
    }
    else
    {
      $resource = @$parents[0];
    }
  }

  return $path;
}

my $cgi = new CGI();
my %cgi_params = $cgi->Vars;

print $cgi->header;
print "\n";
print $cgi->start_html('QB LAB');
print "\n";

my $resource = $cgi_params{$RESOURCE};
my $tuple = $cgi_params{$TUPLE};

my $where = undef;
if (defined($tuple))
{
  $where = "tuple = ${tuple}";
}
elsif (defined($resource))
{
  my $tree = latestTree();
  if (defined($tree))
  {
    $where = "id = '${resource}' and tree = ${tree}";
  }
}

if (defined($where))
{
  print("<strong><big>QB LAB</big></strong><br>\n");
  print("<br>");

  my $resources = loadResources($where, 1);

  if (defined($resources))
  {
    foreach my $resource (@$resources)
    {
      my $tuple = $resource->{tuple};
      my $id = $resource->{id};
      my $tree = $resource->{tree};
      my $class = $resource->{class};
      my $parent = $resource->{parent};
      my $notes = $resource->{notes};
      my $created = $resource->{created};
      my $deleted = $resource->{deleted};
      my $attributes = $resource->{attributes};
  
      print("<strong>RESOURCE:&nbsp;&nbsp;</strong>\n");
      print("<br><br>");

      print("<table>\n");
      print("<tr>\n");
  
      print("<td valign=\"top\">\n");
      print("<table>\n");
      print("<tr bgcolor=\"lightgray\">\n");
      print("<td colspan=\"2\"><strong><big>" . absolutePath($resource) . "</big></strong></td>\n");
      print("</tr>\n");
  
      printAttribute("Class", $class);
      printAttribute("ID", $id);
      printAttribute("Last Modified", $created);
  
      foreach my $name (sort(keys(%$attributes)))
      {
        my $value = $attributes->{$name};
        printAttribute($name, $value);
      }
  
      print("</table>\n");
      print("</td>\n");

      print("<td valign=\"top\">\n");
      print("<table>\n");

      print("<tr>\n");
      print("<td><big><strong>&nbsp;</strong></big></td>\n");
      print("</tr>\n");
  
      my $children = loadResources("parent = '${id}' and tree = ${tree} order by class asc", 0);
      if (defined($children))
      {
        foreach my $child (@$children)
        {
          $tuple = $child->{tuple};
	  $id = $child->{id};
	  $class = $child->{class};
          print("<tr><td><strong>&nbsp;/&nbsp;<a href=\"qb_lab.cgi?${RESOURCE}=${id}\">${id}</strong></a><small>&nbsp;&nbsp;(${class})</small></td></tr>\n");
        }
      }

      print("</table>");
      print("</td>");

      print("</tr>");
      print("</table>");

      print("${notes}");
    }
  }
}
  
db_commit();
print $cgi->end_html;
print "\n";
