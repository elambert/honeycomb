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

BDB_DIR=$(SRC_DIR)/bdb-4.4.20
BDB_LIB=$(BDB_DIR)/libdb.a
LDFLAGS=$(BDB_LIB) -lrt -mt -lpthread

SUCKER_SRCS = \
	database.c			\
	sucker.c			\
	scrub.c				\
	progress.c

ANALYZE_SRCS = \
	database.c			\
	analyze.c			\
	merger.c

DUMP_SRCS = \
	database.c			\
	dump.c

SCRIPTS = \
	run_sucker.sh

DOCS = \
	README.sucker

all: $(DIST_DIR)/bin/sucker $(DIST_DIR)/bin/analyze $(DIST_DIR)/bin/dump		\
	$(SCRIPTS:%=$(DIST_DIR)/bin/%)												\
	$(DOCS:%=$(DIST_DIR)/doc/%)

$(BUILD_DIR)/%.o: %.c
	cc -c -g $< -I$(BDB_DIR) -o $@

$(DIST_DIR)/bin/sucker: $(SUCKER_SRCS:%.c=$(BUILD_DIR)/%.o)
	cc -o $@ $(SUCKER_SRCS:%.c=$(BUILD_DIR)/%.o) $(LDFLAGS)

$(DIST_DIR)/bin/analyze: $(ANALYZE_SRCS:%.c=$(BUILD_DIR)/%.o)
	cc -o $@ $(ANALYZE_SRCS:%.c=$(BUILD_DIR)/%.o) $(LDFLAGS)

$(DIST_DIR)/bin/dump: $(DUMP_SRCS:%.c=$(BUILD_DIR)/%.o)
	cc -o $@ $(DUMP_SRCS:%.c=$(BUILD_DIR)/%.o) $(LDFLAGS)

$(DIST_DIR)/bin/%: %
	cp $< $(DIST_DIR)/bin
	chmod 755 $@

$(DIST_DIR)/doc/%: %
	cp $< $(DIST_DIR)/doc
