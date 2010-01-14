#!/usr/bin/sh
# $Id: copy_gui.sh 11262 2007-07-24 23:46:22Z ad120940 $
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
#utility script to copy gui files from service node to data nodes
#
#make sure that we won't do "real" reboots
./do-servers.sh touch /config/nohoneycomb /config/noreboot
#stop hc
./stop.sh
#gui client
./copy_to_servers.sh /export/tmp/st5800-admingui.jar /opt/honeycomb/web/
./copy_to_servers.sh /export/tmp/st5800-help.jar /opt/honeycomb/web/
./copy_to_servers.sh /export/tmp/jh.jar /opt/honeycomb/web/
./copy_to_servers.sh /export/tmp/index.html /opt/honeycomb/web/
./copy_to_servers.sh /export/tmp/commons-codec-1.3.jar /opt/honeycomb/web/
./copy_to_servers.sh /export/tmp/swing-layout-1.0.jar /opt/honeycomb/web/
./copy_to_servers.sh /export/tmp/concurrent.jar /opt/honeycomb/web/
./copy_to_servers.sh /export/tmp/xmlrpc-2.0.1.jar /opt/honeycomb/web/
#gui server 
./copy_to_servers.sh /export/tmp/st5800-admingui.jar /opt/honeycomb/lib/
#start hc
./start.sh
