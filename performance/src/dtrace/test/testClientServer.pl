#!/usr/bin/perl
#
# $Id: testClientServer.pl 10845 2007-05-19 02:31:46Z bberndt $
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
# testClientServer.pl - A tcp/udp client server application.
#
# XXX: For dtrace probes (ex. profileProcess.sh) use process name
# (execname) as 'testClientServer'.
##

use strict;
use warnings;
use Carp qw(confess);
use English qw(-no_match_vars);
use Getopt::Long;
use IO::Socket;

my $port = 1234;
my $msgSize = 1024; # bytes
my $server;

######################################################################
##
sub usage {
  die("testClientServer [-port port] [-msgSize size] [-protocol udp/tcp] [-server serverAddr] <mode>\n"
       . "    protocol defaults to tcp\n"
       . "    mode is either client or server\n"
       . "    serverAddr should be specified if mode is client\n");
}

######################################################################
##
sub main {
  my $protocol = "tcp";
  my $mode;
  my $help;

  GetOptions(
             "port:s" => \$port,
             "protocol:s" => \$protocol,
             "server:s" => \$server,
             "msgSize=i" => \$msgSize,
             "help!" => \$help,
            );

  if ($help) {
    usage();
  }
  if (($protocol ne "tcp") && ($protocol ne "udp")) {
    print("protocol $protocol not understood\n");
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
    $protocol eq "udp" ? udpClient() : tcpClient();
  } elsif ($mode eq "server") {
    $protocol eq "udp" ? udpServer() : tcpServer();
  } else {
    usage();
  }
}

######################################################################
##
sub tcpClient {
  print("tcpClient\n");
  while (1) {
    my $sock = new IO::Socket::INET (
                                     PeerAddr => $server,
                                     PeerPort => $port,
                                     Proto => 'tcp',
                                    );
    die "Could not create socket: $!\n" unless $sock;

    my $buffer;
    for (my $i = 0; $i < $msgSize; $i++) {
      $buffer = "${buffer}1";
    }
    print $sock $buffer;
    close($sock);
    print("Message of size $msgSize bytes sent\n");
    sleep 5;
  }
}

######################################################################
##
sub tcpServer {
  print("tcpServer\n");
  my $sock = new IO::Socket::INET (
                                   LocalPort => $port,
                                   Proto => 'tcp',
                                   Listen => 1,
                                   Reuse => 1,
                                  );
  die "Could not create socket: $!\n" unless $sock;

  while (my $new_sock = $sock->accept()) {
    $new_sock->autoflush(1);
#    my $text = '';
#    $new_sock->recv($text, $msgSize);
#    my $size = rindex($text, "1") + 1;
#    print "Recevied tcp message of size $size bytes\n";
    while (<$new_sock>) {
      my $size = rindex($_, "1") + 1;
      print "Recevied tcp message of size $size bytes\n";
    }
    close($new_sock);
  }
  close($sock);
}

######################################################################
##
sub udpClient {
  print("udpClient\n");
  my $sock = new IO::Socket::INET (
                                   PeerAddr => $server,
                                   PeerPort => $port,
                                   Proto => 'udp',
                                  );
  die "Could not create socket: $!\n" unless $sock;

  my $buffer;
  for (my $i = 0; $i < $msgSize; $i++) {
    $buffer = "${buffer}1";
  }
  while (1) {
    $sock->send($buffer);
    print("Message of size $msgSize bytes sent\n");
    sleep 5;
  }
  close($sock);
}

######################################################################
##
sub udpServer {
  print("udpServer\n");
  my $sock = new IO::Socket::INET (
                                   LocalPort => $port,
                                   Proto => 'udp',
                                  );
  die "Could not create socket: $!\n" unless $sock;

  while (1) {
    my $text = '';
    $sock->recv($text, $msgSize);
    my $size = rindex($text, "1") + 1;
    print "Recevied udp message of size $size bytes\n";
  }
  close($sock);
}

######################################################################
main(@ARGV);
exit(0);

