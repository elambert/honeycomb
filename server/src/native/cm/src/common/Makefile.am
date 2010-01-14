#
# $Id: Makefile.am 10855 2007-05-19 02:54:08Z bberndt $
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

etcdir=${prefix}/etc
initddir=${etcdir}/init.d

AM_CFLAGS += -I$(top_srcdir)/src/mailbox

noinst_HEADERS = serialization.h honeycomb.in \
	nodemgr_mailbox.sh.in config_reader.sh.in \
	cluster_config.properties.in

lib_LIBRARIES = libmbobject.a

testbin_SCRIPTS = start_ftp.sh
testetc_DATA = ftpd.conf

libmbobject_a_SOURCES = serialization.c

nodist_initd_SCRIPTS = honeycomb
nodist_bin_SCRIPTS = nodemgr_mailbox.sh config_reader.sh

dist_pkgdata_DATA = hc_logging.properties cluster_config.properties mysql_config.sql

% : %.in
	cat $< | awk ' {\
		gsub("@JAVA", "@JAVA@"); \
		gsub("@prefix", "@prefix@"); \
		print; }' > $@

clean:
	rm -f honeycomb nodemgr_mailbox.sh config_reader.sh cluster_config.properties \
		start_ftp.sh
