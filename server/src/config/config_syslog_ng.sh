#!/bin/sh
#
# $Id: config_syslog_ng.sh 10855 2007-05-19 02:54:08Z bberndt $
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

#
# creates the syslog-ng config file by replacing few keyowrds in the
# cluster_properties file.
# usage: config_syslog_ng.sh cluster_properties syslog_ng_templ syslog-ng.conf
#        syslog_sh_in syslog_sh_out


CLUSTER_PROPERTIES=$1
SYSLOG_TEMPLATE_FILE=$2
SYSLOG_CONF_FILE=$3
SYSLOG_IN=$4
SYSLOG_OUT=$5

rm -f $SYSLOG_CONF_FILE
rm -f $SYSLOG_OUT

cat $CLUSTER_PROPERTIES  |
nawk 'BEGIN {FS = "\\\n "; RS=""; OFS="\n"}
/honeycomb.cm.cmm.nodes/ { 
    for (x = 2; x <= NF; x++) 
        print $x
    }' | 
nawk 'BEGIN {FS = " "} 
{
    nodeId[NR] = $1
    ipaddr[NR] = $2
}
END {
    sources = ""
    destinations = ""
    logPaths = ""
    fifos = ""
    for(x = 1; x <= NR; x++)  {
      sources = sprintf("%ssource s_pipe%d\n {pipe(\"/tmp/node%d\");};\n",
          sources, nodeId[x], nodeId[x]);
      destinations =  \
          sprintf("%sdestination udp_dest%d \n { udp(\"%s\" port(514)); };\n", 
          destinations, nodeId[x], ipaddr[x])
      logPaths = \
          sprintf("%slog { source(s_pipe%d); destination(udp_dest%d); };\n", 
          logPaths, nodeId[x], nodeId[x])
      fifos = \
          sprintf("%smkfifo /tmp/node%d\n", fifos, nodeId[x]);
     } 
     while (( getline < conf_in ) > 0) {
        if(match($0,"#PIPE_SOURCES#") > 0)  
            { print sources >> conf_out }
        else if(match($0, "#PIPE_DESTINATIONS#") > 0) 
            { print destinations >> conf_out }
        else if(match($0, "#LOG_PATHS#") > 0) 
            { print logPaths >> conf_out }
        else print $0 >> conf_out
     }
     close(conf_in)      
     close(conf_out)
     syslog_conf = sprintf("SYSLOG_CONF=%s",conf_out)
     while (( getline < syslogsh_in ) > 0) {
         if(match($0,"#FIFOS#") > 0) {print fifos >> syslogsh_out}
         else if(match($0, "#SYSLOG_CONF_VAR#") > 0 ) 
             { print syslog_conf >> syslogsh_out }
         else print $0 >> syslogsh_out
     }
     close(syslogsh_in)
     close(syslogsh_out)

}' conf_in=$SYSLOG_TEMPLATE_FILE syslogsh_in=$SYSLOG_IN conf_out=$SYSLOG_CONF_FILE syslogsh_out=$SYSLOG_OUT
chmod u+x $SYSLOG_OUT
