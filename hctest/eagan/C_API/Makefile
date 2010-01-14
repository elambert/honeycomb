#
# $Id: Makefile 10858 2007-05-19 03:03:41Z bberndt $
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

SHELL := /bin/sh
OS := $(shell uname -s)
MACH := $(shell uname -p)
ifeq ($(OS), SunOS)
  CC=/opt/SUNWspro/bin/cc
  ifeq ($(MACH), sparc)
    OS_TYPE=sol_sparc
    BUILD_TYPE=build_sol_sparc
  else
    OS_TYPE=sol_x86
    BUILD_TYPE=build_sol_x86
  endif
else
  CC=gcc
  OS_TYPE=Linux
  BUILD_TYPE=build_Linux
endif

$(shell mkdir -p $(OS_TYPE))
#INCLUDE=-I../../../build/client_c/dist/$(OS_TYPE)/include
INCLUDE=-I../../../client_c/honeycomb/common -I../../../client_c/honeycomb/$(OS_TYPE)
LDFLAGS=-L../../../build/client_c/$(BUILD_TYPE)/honeycomb/dist

all:	test_usage_sessions test_usage_query test_usage_large test_range_retrieve

test_usage_sessions:	test_usage_sessions.c
	$(CC) $(INCLUDE) $(LDFLAGS) -g -o $(OS_TYPE)/test_usage_sessions test_usage_sessions.c -lhoneycomb

test_usage_query:	test_usage_query.c
	$(CC) $(INCLUDE) $(LDFLAGS) -g -o $(OS_TYPE)/test_usage_query test_usage_query.c -lhoneycomb

test_usage_large:	test_usage_large.c
	$(CC) $(INCLUDE) $(LDFLAGS) -g -o $(OS_TYPE)/test_usage_large test_usage_large.c -lhoneycomb

test_range_retrieve:	test_range_retrieve.c
	$(CC) $(INCLUDE) $(LDFLAGS) -g -o $(OS_TYPE)/test_range_retrieve test_range_retrieve.c -lhoneycomb

clean:
	rm $(OS_TYPE)/test_usage_sessions $(OS_TYPE)/test_usage_query $(OS_TYPE)/test_usage_large
