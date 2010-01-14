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

CC = /opt/SUNWspro/bin/cc
LDFLAGS = -G
JAVAH = javah
RM = rm
RMFLAGS = -f
CP = cp
JAR = $(LIBDIR)/$(SERVERJAR)
JAVAINC = -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/solaris
INCDIRS = -I$(INCDIR)  $(JAVAINC)

LOGSO = libjsyslog.so

LOGTARGET = $(LIBDIR)/$(LOGSO)

LOGSTUBCLASS = com.sun.honeycomb.util.SysLog;

LOGSTUBFILE =  $(INCDIR)/jsyslog.h

LOGSRC = jsyslog.c

all : $(LOGTARGET)

LOGOBJS = $(LOGSRC:%.c=$(BUILDDIR)/%.o)

$(LOGTARGET) : $(LOGSTUBFILE) $(LOGOBJS)
	$(CC) $(LDFLAGS) $(LOGOBJS) -o $(LOGTARGET)

$(BUILDDIR)/%.o: %.c
	$(CC) $(INCDIRS) -c -o $@ $< 

$(LOGSTUBFILE) : $(JAR)	
	$(JAVAH) -classpath $(JAR) -o $(LOGSTUBFILE) $(LOGSTUBCLASS)

javah : $(LOGSTUBFILE)

clean :
	$(RM) $(RMFLAGS) $(LOGTARGET)
	$(RM) $(RMFLAGS) $(LOGSTUBFILE)
