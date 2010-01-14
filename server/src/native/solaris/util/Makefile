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

JAR=$(LIBDIR)/$(SERVERJAR)
STUBCLASS=com.sun.honeycomb.util.SolarisRuntime
STUBFILE=$(BUILDDIR)/exec.h
MODULE=$(BUILDDIR)/libjava-exec.so

# Includes
JAVA_INC=-I$(JAVA_HOME)/include \
         -I$(JAVA_HOME)/include/solaris/util \
         -I$(JAVA_HOME)/include/solaris

INCLUDE=-I$(INCDIR) $(JAVA_INC) -I./.. -I$(BUILDDIR)

DEFS=-DANSI_ARROWS \
	 -DHAVE_STDLIB \
	 -DHAVE_TCGETATTR \
	 -DHIDE -DUSE_DIRENT \
	 -DSYS_UNIX

# Programs
CC=/opt/SUNWspro/bin/cc
CP=cp
RM=rm
JAVAH=javah

CFLAGS=-G $(INCLUDE) $(DEFS)

SRCS=exec.c

OBJS=$(SRCS:%.c=$(BUILDDIR)/%.o)

$(BUILDDIR)/%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

$(MODULE): $(STUBFILE) $(OBJS)
	$(CC) $(CFLAGS) $(OBJS) -o $(MODULE) 

$(STUBFILE): $(JAR)
	$(JAVAH) -classpath $(JAR) -o $(STUBFILE) $(STUBCLASS)

install: $(MODULE)
	$(CP) $(MODULE) $(LIBDIR)

clean:
	$(RM) -f $(OBJS)
	$(RM) -f $(STUBFILE)
