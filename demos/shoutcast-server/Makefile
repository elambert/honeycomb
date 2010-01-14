#
# $Id: Makefile 10856 2007-05-19 02:58:52Z bberndt $
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

HC_JAR = ../../dist/lib/honeycomb-client.jar
SHOUTCAST_LIB=shoutcast/lib/libshout.so

CFLAGS = -Ishoutcast/include
LDFLAGS = -Lshoutcast/lib -lshout

all : honeycomb-client.jar $(SHOUTCAST_LIB) HCIce.class HCShout.class libHCIce.so

######################################################################

honeycomb-client.jar : $(HC_JAR)
	cp $(HC_JAR) .

$(SHOUTCAST_LIB) : $(SHOUTCAST_LIB).3
	ln -s `basename $(SHOUTCAST_LIB).3` $(SHOUTCAST_LIB)

%.class : %.java
	javac -classpath $(HC_JAR) *.java

lib%.so : %.c
	gcc -Wall -pedantic -g $(CFLAGS) $(LDFLAGS) -shared -o $@ $<

clean:
	rm -f *~ *.o *.so *.class shoutcast-demo.tgz honeycomb-client.jar

tar: all
	tar cfz shoutcast-demo.tgz README run_hcshout.sh *.class honeycomb-client.jar *.so shoutcast
