#
# $Id: Makefile 10977 2007-06-06 00:42:50Z stephb $
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
# BE CAREFUL not to generate any file in the source repository. Everything
# is generated and compiled in BUILDDIR

CC = /opt/SUNWspro/bin/cc
CFLAGS = -mt
LDFLAGS = -G -lpthread -mt -lnsl -lrt
UT_LDFLAGS = -lpthread -mt -lnsl -lrt
JAVAH = javah
RPCGEN=/usr/bin/rpcgen
RPCFLAGS=-M
RM = rm
RMFLAGS = -f
CP = cp
JAR = $(LIBDIR)/$(SERVERJAR)
JAVAINC =  -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/solaris
BDBINC = -I$(BDBPATH)/include
BDBLIB = $(BDBPATH)/lib/libdb.a
INCDIRS = -I$(INCDIR) -I$(BUILDDIR) -I. $(JAVAINC) $(BDBINC) 
SO = libhcnfs.so

TARGET = $(LIBDIR)/$(SO)
STUBCLASS = com.sun.honeycomb.oa.daal.hcnfs.NfsAccess \
            com.sun.honeycomb.oa.daal.hcnfs.HcNfsMgmt
STUBFILE = $(INCDIR)/HcNfsDAAL.h
RPC = mount_rpc.x nfs_rpc.x nlm_rpc.x
SRC = HcNfsDAAL.c fhdb.c globals.c handle_repos.c mount.c nfs.c nlm.c
UTSRC = ut_createfile.c ut_readdir.c ut_mountdisks.c ut_lock.c ut_lock_thread.c


RPC_SRC = $(RPC:%.x=$(BUILDDIR)/%_clnt.c) $(RPC:%.x=$(BUILDDIR)/%_xdr.c)
RPC_HDR = $(RPC:%.x=$(BUILDDIR)/%.h) 
OBJS = $(SRC:%.c=$(BUILDDIR)/%.o) $(RPC_SRC:%.c=%.o)
UTOBJS = $(UTSRC:%.c=$(BUILDDIR)/%.o)
UT = $(UTOBJS:%.o=%)

all : $(TARGET) $(UT)

$(TARGET) : $(STUBFILE) $(RPC_HDR) $(OBJS)
	$(CC) $(LDFLAGS) $(OBJS) $(BDBLIB) -o $(TARGET)

$(STUBFILE): $(JAR)	
	$(JAVAH) -classpath $(JAR) -o $(STUBFILE) $(STUBCLASS)

%.o: %.c
	$(CC) -c $(CFLAGS) $(INCDIRS) $< -o $@

$(BUILDDIR)/%.o: %.c
	$(CC) -c $(CFLAGS) $(INCDIRS) $< -o $@

$(BUILDDIR)/%.h: %.x
	$(RPCGEN) $(RPCFLAGS) -h $< -o $@

$(BUILDDIR)/%_clnt.c: %.x
	$(RPCGEN) $(RPCFLAGS) -l $< -o $@

$(BUILDDIR)/%_xdr.c: %.x
	$(RPCGEN) $(RPCFLAGS) -C -c $< -o $@

$(UT): $(UTOBJS) $(TARGET)
	$(CC) $(OBJS) $(BDBLIB) $@.o -o $@ $(UT_LDFLAGS)

clean :
	$(RM) $(RMFLAGS) $(TARGET) $(STUBFILE) $(RPC_HDR) $(RPC_SRC) $(OBJS)
