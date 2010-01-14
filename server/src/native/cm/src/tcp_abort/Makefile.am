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

AUTOMAKE_OPTIONS = no-exeext

GENKSYMS_SMP_PREFIX = -p smp_
#GENKSYMS_SMP_PREFIX = 

KERNEL_NAME = $(shell uname -r)
KERNEL_VERSION = $(shell uname -r | awk -F'.' '{printf "%d.%d.%d", $$1, $$2, int($$3)}')
KERNEL_INCLUDE = /lib/modules/$(KERNEL_NAME)/build/include
SYSTEM_MAP = /boot/System.map-$(KERNEL_NAME)
MODULE_KSYMS = tcp_ksyms.c

MOD_ARCH_CFLAGS = -Wall -Wstrict-prototypes -O2 -pipe

MOD_CFLAGS = -g -DMODULE -D__KERNEL__ \
	-include $(KERNEL_INCLUDE)/linux/modversions.h  \
	$(MOD_ARCH_CFLAGS) -I$(KERNEL_INCLUDE)

kernelmoddir=${prefix}/lib/modules/$(KERNEL_NAME)/honeycomb

noinst_HEADERS = \
	tcp_abort.txt \
	tcp_ksyms.c \
	tcp_abort.h \
	tcp_abort.c \
	ksym_addr.sh 

kernelmod_PROGRAMS = tcpabort.o
test_PROGRAMS = tcp_test

test_CFLAGS = $(CFLAGS)

tcpabort_o_SOURCES = # For dist target

MODULE_SRCS = tcp_abort.c

tcpabort.o: $(MODULE_SRCS:.c=.o) mod_ksym.ld
	$(LD) -r -o $@ $(MODULE_SRCS:.c=.o) $(shell cat mod_ksym.ld)

$(MODULE_SRCS:%.c=%.o):
	$(CC) -o $@ -c $*.c $(MOD_CFLAGS) 

mod_ksym.ld: ksym_addr.sh $(SYSTEM_MAP) $(MODULE_KSYMS) mod_ksym.ver
	sh ./ksym_addr.sh $(SYSTEM_MAP) $(MODULE_KSYMS) mod_ksym.ver >$@

mod_ksym.ver: $(MODULE_KSYMS)
	$(CC) -E -D__GENKSYMS__ $(CFLAGS) $< | \
	/sbin/genksyms $(GENKSYMS_SMP_PREFIX) -k $(KERNEL_VERSION) >$@

install-data-hook:
	chmod a-x $(DESTDIR)/$(kernelmoddir)/tcpabort.o

clean:
	rm -f mod_ksym.ld mod_ksym.ver