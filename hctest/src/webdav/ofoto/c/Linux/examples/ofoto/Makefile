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

include ../Makefile.env

HC_DIR=../../lib

CFLAGS= -g -I$(HC_DIR)/src -Wall -Ineon-0.25.5/src $(EXTRA_TEST_LDFLAGS) 
LDFLAGS1= -L../../lib -lhoneycomb $(EXTRA_TEST_LDFLAGS) 
LDFLAGS2= -L../../lib -L/usr/local/lib $(EXTRA_TEST_LDFLAGS) 
LIBS = libneon.a libexpat.a

BIN1 = store
SRCS1 = ../common/HoneycombMetadata.c of_file.c of_gen.c of_store.c
OBJS1 = $(SRCS1:.c=.$(OBJ_EXT))

BIN2 = get
SRCS2 = of_file.c of_gen.c of_get.c
OBJS2 = $(SRCS2:.c=.$(OBJ_EXT))

BIN3 = getrange
SRCS3 = of_file.c of_getrange.c
OBJS3 = $(SRCS3:.c=.$(OBJ_EXT))

BIN4 = checkdirs
SRCS4 = of_gen.c of_checkdirs.c
OBJS4 = $(SRCS4:.c=.$(OBJ_EXT))

BIN5 = propfind
SRCS5 = of_propfind.c
OBJS5 = $(SRCS5:.c=.$(OBJ_EXT))

BIN6 = put
SRCS6 = of_file.c of_gen.c of_put.c 
OBJS6 = $(SRCS6:.c=.$(OBJ_EXT))

BIN7 = mkdirs
SRCS7 = of_gen.c of_mkdirs.c
OBJS7 = $(SRCS7:.c=.$(OBJ_EXT))

BIN8 = getfile
SRCS8 = getfile.c
OBJS8 = $(SRCS8:.c=.$(OBJ_EXT))

BIN9 = putfile
SRCS9 = putfile.c
OBJS9 = $(SRCS9:.c=.$(OBJ_EXT))

all: $(BIN1) $(BIN2) $(BIN3) $(BIN4) $(BIN5) $(BIN6) $(BIN7) $(BIN8) $(BIN9)

$(BIN1): $(OBJS1)
	$(CC) $(LDFLAGS1) -o $(BIN1) $(OBJS1)

$(BIN2): $(OBJS2)
	$(CC) $(LDFLAGS2) -o $(BIN2) $(OBJS2) $(LIBS)

$(BIN3): $(OBJS3)
	$(CC) $(LDFLAGS2) -o $(BIN3) $(OBJS3) $(LIBS)

$(BIN4): $(OBJS4)
	$(CC) $(LDFLAGS2) -o $(BIN4) $(OBJS4) $(LIBS)

$(BIN5): $(OBJS5)
	$(CC) $(LDFLAGS2) -o $(BIN5) $(OBJS5) $(LIBS)

$(BIN6): $(OBJS6)
	$(CC) $(LDFLAGS2) -o $(BIN6) $(OBJS6) $(LIBS)

$(BIN7): $(OBJS7)
	$(CC) $(LDFLAGS2) -o $(BIN7) $(OBJS7) $(LIBS)

$(BIN8): $(OBJS8)
	$(CC) $(LDFLAGS2) -o $(BIN8) $(OBJS8) $(LIBS)

$(BIN9): $(OBJS9)
	$(CC) $(LDFLAGS2) -o $(BIN9) $(OBJS9) $(LIBS)

%.$(OBJ_EXT): %.c
	$(CC) $(CFLAGS) -c $< $(OBJ_SWITCH)$@
