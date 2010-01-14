#!/usr/bin/perl
#
# $Id: switchSpreaderTest.pl 10858 2007-05-19 03:03:41Z bberndt $
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
# Test how well a client's requests are spread over different
# nodes. This test indicates the distribution of requests per
# node. Client requests can land on upto 4 nodes. The test runs in two
# modes: as client or server. Server mode is used on nodes. Server
# accepts tcp connections and send a message indicating the node
# number/machine name it is running on. Client mode is used on a
# client machine, where it continuosly makes tcp connections to
# data-vip and receive messages sent from nodes. Output is printed
# every second. Output is a table where each row has node name and
# number of requests that landed on that node. You should see 4 rows
# and the distribution should be uniform. Honeycomb must be stopped
# before running this script since it uses the same port as honeycomb.
#
# To run:
# 1) copy this script to all nodes.
# 2) touch /config/noreboot and /config/nohoneycomb flags on all nodes.
# 3) verify that you have rules programmed on switch. Use
#    /opt/honeycomb/bin/irules.sh from one of the nodes. You should see
#    37 rules (when no authorized clients are programmed).
# 4) kill all java processes and hadb on nodes, and verify that switch
#    rules have not changed.
# 5) run this script on all nodes.
#    Command is "switchSpreaderTest.pl -port 8080 server &".
# 6) do a 'nslookup devXXX-data' and get the data-vip.
# 7) pick client(s) and run this script. Command is
#    "switchSpreaderTest.pl -server data-vip -port 8080 client"
# 8) repeat above test for port 8079 and by killing nodes.
# 9) if you are really feeling lucky then pick 4 clients (to run the
#    test) so that all 16 nodes get involved.
##

use strict;
use warnings;
use Carp qw(confess);
use English qw(-no_match_vars);
use Getopt::Long;
use IO::Socket;

my $port = 54326;
my $server;

######################################################################
##
sub usage {
  die("switchSpreaderTest.pl [-port port] [-server serverAddr] <mode>\n"
       . "    port: port for server to bind to\n"
       . "    mode is either client or server\n"
       . "    serverAddr should be specified if mode is client\n"
       . "    Note: Read the script file for more instructions\n");
}

######################################################################
##
sub main {
  my $mode;
  my $help;

  GetOptions(
             "port:s" => \$port,
             "server:s" => \$server,
             "help!" => \$help,
            );

  if ($help) {
    usage();
  }

  # left over by GetOptions
  if (scalar(@ARGV) != 1) {
    usage();
  }
  $mode = shift(@ARGV);
  if ($mode eq "client") {
    if (!defined($server)) {
      print("Server not defined\n");
      usage();
    }
    tcpClient();
  } elsif ($mode eq "server") {
    tcpServer();
  } else {
    usage();
  }
}

######################################################################
##
sub tcpClient {
  my $iteration = 0;
  my %messages;

  while (1) {
    my $sock = new IO::Socket::INET (
                                     PeerAddr => $server,
                                     PeerPort => $port,
                                     Proto => 'tcp',
                                     Timeout => 10,
                                    );
    die "Could not create socket: $!\n" unless $sock;
    #print "port " . $sock->sockport() . "\n";
    # each new socket should get a different input port.
    while (<$sock>) {
      chomp;
      if (defined($messages{$_})) {
        $messages{$_}++;
      } else {
        $messages{$_} = 0;
      }
    }
    close($sock);
    $iteration++;
    if ($iteration % 100 == 0) {
      print("=================\n");
      foreach my $key (sort keys %messages) {
        print("$key => $messages{$key}\n");
      }
      print("\n");
    }
    select(undef, undef, undef, 0.01); # sleep 10 millisec
  }
}

######################################################################
##
sub tcpServer {
  my $sock = new IO::Socket::INET (
                                   LocalPort => $port,
                                   Proto => 'tcp',
                                   Listen => 1,
                                   Reuse => 1,
                                  );
  die "Could not create socket: $!\n" unless $sock;

  my $name = `uname -a`;
  my @cmdOut = split(" ", $name);
  $name = $cmdOut[1];

  while (my $newSock = $sock->accept()) {
    $newSock->autoflush(1);
    print $newSock "$name\n";
  }
  close($sock);
}

######################################################################
main(@ARGV);
exit(0);

