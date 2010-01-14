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

noinst_HEADERS = mbdisk.h disk.h

sbin_PROGRAMS = hcdisk 
bin_PROGRAMS = hcdisk_adm
nodist_sbin_SCRIPTS = disk_check.sh


hcdisk_LDFLAGS = -L$(top_srcdir)/src/mailbox -lmbox -L$(top_srcdir)/src/CMM/lib -lhccmm -luuid

hcdisk_SOURCES = main.c disk.c stat.c mbdisk.c $(top_srcdir)/src/trace/syslog_trace.c ../../common/serialization.c

hcdisk_adm_SOURCES = hcdisk_adm.c $(top_srcdir)/src/trace/stdout_trace.c

AM_CFLAGS += -D_LARGEFILE64_SOURCE -DDISK_CHECK=\"$(sbindir)/disk_check.sh\" -I$(top_srcdir)/src/mailbox -DDISKSERVER_MNTPATH=@DISKSERVER_MNTPATH@ -DHCDISK_ADM_PORT=@HCDISK_ADM_PORT@
