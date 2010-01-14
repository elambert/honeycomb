#
# $Id: Makefile 11918 2008-03-16 18:15:23Z ks202890 $
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

RM=/bin/rm -f
CP=/bin/cp -f
CHMOD=/bin/chmod

INITSRCS = honeycomb.in
INITOBJS = $(INITSRCS:%.in=$(BUILDBIN)/%)

BINSRCS = nodemgr_mailbox.sh.in     \
          node_config_editor.sh.in  \
          internal_portmap.sh.in    \
          external_portmap.sh.in    \
          ifconfig_internal.sh.in   \
          ifconfig_script.sh.in     \
          node_configuration.in     \
          node_common.sh.in         \
          format_common.sh.in       \
          disk_replacement.in       \
          diskmap.in		    \
          node_verification.in      \
          update_bootmenu.in        \
          irules.sh.in              \
          imask.sh.in               \
          start_webdav.sh.in        \
          wipe.sh.in                \
          wipe-linux.sh.in          \
          hcsh.in                   \
          disklabel.sh.in           \
	  handle_attach_detach.pl.in \
	  handle_online_offline.pl.in \
	  offline_mirrors.pl.in     \
	  online_mirrors.pl.in      \
	  attach_mirrors.pl.in      \
	  detach_mirrors.pl.in      \
          parse_log.pl.in           \
          list_mbox_alerts.sh.in    \
          metadata_config.sh.in     \
          monitor_ntpserver.sh.in   \
          bios.in                   \
          dimm.in                   \
          cpu.in                    \
          mb.in                     \
          dmidecode.pl.in           \
          mntiso.sh.in              \
          umntiso.sh.in             \
          mntjs.sh.in               \
          umntjs.sh.in              \
          honeycomb_net_filesystem.in \
          honeycomb_config_network.pl.in \
	  dot.bashrc.in \
	  dot.profile.in \
          getdevid.pl.in

BINOBJS = $(BINSRCS:%.in=$(BUILDBIN)/%) \
	setup_admin_account.pl

OBJS= $(INITOBJS) ${SVCOBJS} $(BINOBJS)

.SUFFIXES:

all: $(OBJS)
	@($(CHMOD) ugo+x $(OBJS))
	@($(CP) $(INITOBJS) $(DISTINIT))
	@($(CP) $(BINOBJS) $(DISTBIN))

$(BUILDBIN)/% : %.in
	@(cat $< | nawk ' {\
	gsub("@JAVA", "$(JAVA)"); \
	gsub("@PREFIX", "$(PREFIX)"); \
	gsub("@LOGCONF", "$(LOGCONF)"); \
	gsub("@HCJARS", "$(HCJARS)"); \
	gsub("@WEBDAVCLASSPATH", "$(WEBDAVCLASSPATH)"); \
	gsub("@NODECONFIGCLASSPATH", "$(NODECONFIGCLASSPATH)"); \
	gsub("@EMMA_OPT_RT_CONTROL_FALSE", "$(EMMA_OPT_RT_CONTROL_FALSE)"); \
	gsub("@EMMA_OPT_COVERAGE_FILE_CLI", "$(EMMA_OPT_COVERAGE_FILE_CLI)"); \
	gsub("@EMMA_OPT_COVERAGE_FILE_NODEMGR_MAILBOX", "$(EMMA_OPT_COVERAGE_FILE_NODEMGR_MAILBOX)"); \
	gsub("@EMMA_OPT_COVERAGE_FILE_WEBDAV", "$(EMMA_OPT_COVERAGE_FILE_WEBDAV)"); \
	gsub("@EMMA_OPT_COVERAGE_FILE_START", "$(EMMA_OPT_COVERAGE_FILE_START)"); \
	gsub("@EMMA_OPT_COVERAGE_FILE_STOP", "$(EMMA_OPT_COVERAGE_FILE_STOP)"); \
	gsub("@EMMA_OPT_COVERAGE_FILE_NODECONFIG", "$(EMMA_OPT_COVERAGE_FILE_NODECONFIG)"); \
	print; }' > $@)

clean:
	@($(RM) $(OBJS))
