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

#
# TODO: include the jni header into dist 
#

JNI_INC=$(top_srcdir)/../include

include_HEADERS = mbox.h
noinst_HEADERS = conf.h 

sbin_PROGRAMS = hcmboxd
testbin_PROGRAMS = test_mailbox

lib_LIBRARIES = libmbox.a
lib_LTLIBRARIES = libjmbox.la

libmbox_a_CFLAGS = $(AM_CFLAGS) -lpthread -DMBOXD_PORT=@MBOXD_PORT@ -DMBOXD_GROUP=\"@MBOXD_GROUP@\"
libjmbox_la_CFLAGS = -pthread -I@JAVA_INCLUDE@ -I@JAVA_INCLUDE@/linux -I$(JNI_INC) $(AM_CFLAGS) -DMBOXD_PORT=@MBOXD_PORT@ -DMBOXD_GROUP=\"@MBOXD_GROUP@\"

hcmboxd_CFLAGS = $(AM_CFLAGS) -DMBOXD_PORT=@MBOXD_PORT@ -DMBOXD_GROUP=\"@MBOXD_GROUP@\" -DMBOXD_MAXSIZE=@MBOXD_MAXSIZE@
hcmboxd_LDFLAGS = -L$(top_srcdir)/src/mailbox -lmbox -L$(top_srcdir)/src/CMM/lib -lhccmm

libmbox_a_SOURCES = mbox.c
libjmbox_la_SOURCES = jmbox.c mbox.c
hcmboxd_SOURCES = mboxd.c network.c $(top_srcdir)/src/trace/syslog_trace.c

test_mailbox_SOURCES = test_mailbox.c $(top_srcdir)/src/trace/stdout_trace.c
test_mailbox_LDFLAGS = -L$(top_srcdir)/src/mailbox -lmbox

