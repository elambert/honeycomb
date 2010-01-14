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

bin_PROGRAMS = clustermonitor checkmaster getinfo getallnodes cmm_mastership_release cmm_qualif hc_compute_nodeid

testbin_PROGRAMS = cmm_consistency_check
testbin_SCRIPTS = cmm_check.pl

# clustermonitor

clustermonitor_SOURCES = \
	clustermonitor.c

clustermonitor_CFLAGS = -I.. $(AM_CFLAGS)
clustermonitor_LDFLAGS = -L../lib -lhccmm

# checkmaster

checkmaster_SOURCES = \
	checkmaster.c

checkmaster_CFLAGS = -I.. $(AM_CFLAGS)
checkmaster_LDFLAGS = -L../lib -lhccmm

# getinfo

getinfo_SOURCES = \
	getinfo.c

getinfo_CFLAGS = -I.. $(AM_CFLAGS)
getinfo_LDFLAGS = -L../lib -lhccmm

# getallnodes

getallnodes_SOURCES = \
	getallnodes.c

getallnodes_CFLAGS = -I.. $(AM_CFLAGS)
getallnodes_LDFLAGS = -L../lib -lhccmm

# hccmmrole

#hccmmrole_SOURCES = \
#	hccmmrole.c

#hccmmrole_CFLAGS = -I.. $(AM_CFLAGS)
#hccmmrole_LDFLAGS = -L../lib -lhccmm

# hcgetconf

#hcgetconf_SOURCES = \
#	hcgetconf.c

#hcgetconf_CFLAGS = -I.. $(AM_CFLAGS)
#hcgetconf_LDFLAGS = -L../lib -lhccmm

# cmm_mastership_release

cmm_mastership_release_SOURCES = \
	cmm_mastership_release.c

cmm_mastership_release_CFLAGS = -I.. $(AM_CFLAGS)
cmm_mastership_release_LDFLAGS = -L../lib -lhccmm

# cmm_qualif

cmm_qualif_SOURCES = \
	cmm_qualif.c

cmm_qualif_CFLAGS = -I.. $(AM_CFLAGS)
cmm_qualif_LDFLAGS = -L../lib -lhccmm

# hc_compute_nodeid

hc_compute_nodeid_SOURCES = hc_compute_nodeid.c

# cmm_consistency_check

cmm_consistency_check_CFLAGS = -I.. $(AM_CFLAGS)
cmm_consistency_check_LDFLAGS = -L../lib -lhccmm
