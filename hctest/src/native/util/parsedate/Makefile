#
# A utility to convert human-language date spec into a time_t
#
# $Id: Makefile 10895 2007-05-23 04:19:24Z pc198268 $
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

NATIVE_ROOT := $(shell cd ../..;pwd)
include $(NATIVE_ROOT)/build/Defs.mk

OS := $(shell uname)
OSFLAGS =  -DOS=$(OS)

YACC = bison

PARSEDATE = $(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)parsedate

PARSEDATE_Y_FILES    :=  getdate.y
PARSEDATE_C_FILES    :=  parsedate.c

PARSEDATE_O_FILES    := $(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)$(PARSEDATE_C_FILES:.c=.$(CC_OBJ_SUFFIX)) \
			$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)$(PARSEDATE_Y_FILES:.y=.$(CC_OBJ_SUFFIX))

$(PARSEDATE): $(PARSEDATE_O_FILES)
	$(CC) $(CC_EXE_FLAGS)$@ $^

# build: $(PARSEDATE_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%)

# C files
$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%.$(CC_OBJ_SUFFIX): %.c
	$(CC) $(OSFLAGS) $(CC_FLAGS)$@ $<

# Yacc files
$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%.$(CC_OBJ_SUFFIX): %.y
	$(YACC) -o $(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)$*.c $<
	cp getdate.h $(HC_HCTEST_BUILD_LIB_DIR)
	$(CC) $(CC_FLAGS)$@ $(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)$*.c
	$(RM) $(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)$*.c

.PHONY: clean

clean:
	$(RM) -f $(PARSEDATE) $(PARSEDATE_O_FILES)

