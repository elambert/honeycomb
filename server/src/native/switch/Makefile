#
# $Id: Makefile 10855 2007-05-19 02:54:08Z bberndt $
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
# Please don't put any solaris-make-specific or gnumake-specific
# constructs here!

CFLAGS = -g -DOS=SunOS -DSolaris -I.
CC = /opt/SUNWspro/bin/cc
LIBS = -ldl -lsocket -lnsl -lresolv

SRCS = zrule.c irule.c libztmd.c
OBJS = ${BUILDDIR}/zrule.o ${BUILDDIR}/irule.o ${BUILDDIR}/libztmd.o

all: ${BUILDDIR}/zrule ${BUILDDIR}/irule

${BUILDDIR}/zrule: ${BUILDDIR}/zrule.o ${BUILDDIR}/libztmd.o
	${CC} ${CFLAGS} -o ${BUILDDIR}/zrule ${BUILDDIR}/zrule.o ${BUILDDIR}/libztmd.o $(LIBS)

${BUILDDIR}/irule: ${BUILDDIR}/irule.o
	${CC} ${CFLAGS} -o ${BUILDDIR}/irule ${BUILDDIR}/irule.o $(LIBS)

${BUILDDIR}/zrule.o: zrule.c libznl2.h libztmd.h
	$(CC) $(CFLAGS) -c zrule.c -o ${BUILDDIR}/zrule.o

${BUILDDIR}/libztmd.o: libztmd.c libznl2.h libztmd.h
	$(CC) $(CFLAGS) -c libztmd.c -o ${BUILDDIR}/libztmd.o

${BUILDDIR}/%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

install: ${BUILDDIR}/zrule ${BUILDDIR}/irule
	cp ${BUILDDIR}/zrule ${BINDIR}
	cp ${BUILDDIR}/irule ${BINDIR}

clean:
	-rm -f $(OBJS) ${BUILDDIR}/zrule ${BUILDDIR}/irule core

