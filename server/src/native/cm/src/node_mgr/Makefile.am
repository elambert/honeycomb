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

AM_CFLAGS += -I$(top_srcdir)/src/mailbox -I$(top_srcdir)/src/services/disk

noinst_HEADERS = tree.h config.h action.h list.h sequence.h scheduler.h factory.h \
	server.h forker.h service_monitor.h errors.h node_config.xml.in mailbox.h \
	cmm_interface.h

testbin_PROGRAMS = test_tree test_config test_list test_action test_sequence
sbin_PROGRAMS = hcnmd
bin_PROGRAMS = hcnm_client

dist_testbin_DATA = config_test.xml
dist_pkgdata_DATA = node_config.xml

test_tree_SOURCES = test_tree.c tree.c $(top_srcdir)/src/trace/stdout_trace.c

test_config_SOURCES = test_config.c config.c tree.c forker_stub.c \
	$(top_srcdir)/src/trace/stdout_trace.c mailbox_stub.c
test_config_LDFLAGS = -export-dynamic -lexpat -ldl

test_list_SOURCES = test_list.c list.c $(top_srcdir)/src/trace/stdout_trace.c

test_action_SOURCES = test_action.c action.c forker_stub.c \
	tree.c mailbox_stub.c $(top_srcdir)/src/trace/stdout_trace.c

test_sequence_SOURCES = test_sequence.c action.c sequence.c \
	tree.c list.c forker_stub.c mailbox_stub.c $(top_srcdir)/src/trace/stdout_trace.c

hcnmd_SOURCES = main.c tree.c list.c config.c action.c sequence.c scheduler.c \
	factory.c server.c forker.c service_monitor.c probe_disk.c ../common/serialization.c \
	mailbox.c cmm_interface.c \
	$(top_srcdir)/src/trace/syslog_trace.c
hcnmd_CFLAGS = -DDEFAULT_CONFIG_FILE=@DEFAULT_CONFIG_FILE@ -DDEFAULT_PORT=@DEFAULT_PORT@ $(AM_CFLAGS) -DDISKSERVER_MNTPATH=@DISKSERVER_MNTPATH@ -DINSTALL_SBIN=\"$(sbindir)\" -DINSTALL_VDISK=\"$(datadir)\"
hcnmd_LDFLAGS = -export-dynamic -lexpat -ldl -L$(top_srcdir)/src/mailbox -lmbox -L$(top_srcdir)/src/CMM/lib -lhccmm

hcnm_client_SOURCES = hcnm_client.c $(top_srcdir)/src/trace/stdout_trace.c
hcnm_client_CFLAGS = -DDEFAULT_PORT=@DEFAULT_PORT@ $(AM_CFLAGS)

% : %.in
	cat $< | awk -v CVS_ROOT=`cd $(top_srcdir)/../..; pwd` ' {\
		gsub("@JAVA", "@JAVA@"); \
		gsub("@prefix", "@prefix@"); \
		gsub("@CVS_ROOT", CVS_ROOT); \
		print; }' > $@

clean:
	rm -f node_config.xml
