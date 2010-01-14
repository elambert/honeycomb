#
# $Id: Makefile 11493 2007-09-11 23:19:37Z sm152240 $
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

OS := $(shell uname)

ifeq ($(OS),SunOS)
CC = /opt/SUNWspro/bin/cc
LINT = /opt/SUNWspro/bin/lint
CFLAGS = -I. -g -D$(OS) -mt
LIBS = -lsocket -lresolv -lnsl -lthread
#CFLAGS = -I. -g -D$(OS) -mt -DPTHREADS
#LIBS = -lsocket -lresolv -lnsl -lpthread
endif

ifeq ($(OS),Linux)
CC = gcc
CFLAGS = -I. -D$(OS) -DPTHREADS -g -Wall
LIBS = -lpthread
endif

# Allow the makefile to work even if it's not called from the
# top-level build.xml
ifeq ($(BUILDDIR),)
BUILDDIR = .
endif
ifeq ($(BINDIR),)
BINDIR = .
endif

# The rules

.PHONY: install clean

OBJ = ${BUILDDIR}/multiload.o ${BUILDDIR}/headers.o ${BUILDDIR}/util.o \
      ${BUILDDIR}/patterns.o ${BUILDDIR}/task.o

EXE = ${BUILDDIR}/multiload

install: ${EXE}
	-cp $< ${BINDIR}

${BUILDDIR}/multiload: $(OBJ)
	$(CC) -o $@ $(OBJ) $(LIBS)

$(OBJ): multiload.h util.h Makefile

${BUILDDIR}/%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f ${EXE} $(OBJ)
