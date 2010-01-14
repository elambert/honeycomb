#
# $Id: Makefile 11444 2007-08-29 02:21:47Z pc198268 $
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

# Determine platform we are running on
OS := $(shell uname -s)
MACH := $(shell uname -p)
ifeq ($(OS), SunOS)
  REL := $(shell uname -r)
  ifeq ($(REL), 5.9)
    REL=9
  else
    REL=10
  endif
  ifeq ($(MACH), sparc)
    PLATFORM=sol_$(REL)_sparc
  else
    PLATFORM=sol_$(REL)_x86
  endif
else
  ifeq ($(OS), Linux)
    PLATFORM=$(OS)
  else
    PLATFORM=Win32
  endif
endif

# Variable definitions
include ./env/env.$(PLATFORM)
HC_DIR=../$(PLATFORM)/lib
OBJ_DIR=./$(PLATFORM)/obj
BUILD_DIR=./$(PLATFORM)/build

CFLAGS= $(EXTRA_TEST_CFLAGS)
LDFLAGS= $(EXTRA_TEST_LDFLAGS) 

COMMON_INCLUDE = ./common/example_common.h ./common/example_commandline.h ./common/example_metadata.h
HC_INCLUDE = ../include/hc.h ../include/hcclient.h ../$(PLATFORM)/include/hchost.h

COMMON = ./common/example_common.h ./common/example_commandline.c ./common/example_metadata.c
CMD_OBJ = $(OBJ_DIR)/example_common.$(OBJ_EXT) $(OBJ_DIR)/example_commandline.$(OBJ_EXT) 
COMMON_OBJS = $(CMD_OBJ) $(OBJ_DIR)/example_metadata.$(OBJ_EXT)

# Create build directories
$(shell mkdir $(PLATFORM))
$(shell mkdir $(PLATFORM)/build)
$(shell mkdir $(PLATFORM)/obj)

# Copy over libraries into build directory
ifeq ($(PLATFORM), Win32)
  $(shell cp $(HC_DIR)/*.dll $(BUILD_DIR))
endif

# Build all example applications
all:	AddMetadata DeleteRecord Query RetrieveData RetrieveMetadata RetrieveSchema StoreFile CheckIndexed

# Build AddMetadata
AddMetadata:	$(OBJ_DIR)/AddMetadata.$(OBJ_EXT) $(COMMON_OBJS)
	$(CC) $(LDFLAGS) $(LINK_OUT_FLAG)$(BUILD_DIR)/AddMetadata $(OBJ_DIR)/AddMetadata.$(OBJ_EXT) $(COMMON_OBJS)

# Build DeleteRecord
DeleteRecord:	$(OBJ_DIR)/DeleteRecord.$(OBJ_EXT) $(COMMON_OBJS)
	$(CC) $(LDFLAGS) $(LINK_OUT_FLAG)$(BUILD_DIR)/DeleteRecord $(OBJ_DIR)/DeleteRecord.$(OBJ_EXT) $(COMMON_OBJS)

# Build Query
Query:	$(OBJ_DIR)/Query.$(OBJ_EXT) $(COMMON_OBJS)
	$(CC) $(LDFLAGS) $(LINK_OUT_FLAG)./$(BUILD_DIR)/Query $(OBJ_DIR)/Query.$(OBJ_EXT) $(COMMON_OBJS)

# Build RetrieveData
RetrieveData:	$(OBJ_DIR)/RetrieveData.$(OBJ_EXT) $(COMMON_OBJS)
	$(CC) $(LDFLAGS) $(LINK_OUT_FLAG)$(BUILD_DIR)/RetrieveData $(OBJ_DIR)/RetrieveData.$(OBJ_EXT) $(COMMON_OBJS)

# Build RetrieveMetadata
RetrieveMetadata:	$(OBJ_DIR)/RetrieveMetadata.$(OBJ_EXT) $(COMMON_OBJS)
	$(CC) $(LDFLAGS) $(LINK_OUT_FLAG)$(BUILD_DIR)/RetrieveMetadata $(OBJ_DIR)/RetrieveMetadata.$(OBJ_EXT) $(COMMON_OBJS)

# Build RetrieveSchema
RetrieveSchema:	$(OBJ_DIR)/RetrieveSchema.$(OBJ_EXT) $(COMMON_OBJS)
	$(CC) $(LDFLAGS) $(LINK_OUT_FLAG)$(BUILD_DIR)/RetrieveSchema $(OBJ_DIR)/RetrieveSchema.$(OBJ_EXT) $(COMMON_OBJS)

# Build StoreFile
StoreFile:	$(OBJ_DIR)/StoreFile.$(OBJ_EXT) $(COMMON_OBJS)
	$(CC) $(LDFLAGS) $(LINK_OUT_FLAG)$(BUILD_DIR)/StoreFile $(OBJ_DIR)/StoreFile.$(OBJ_EXT) $(COMMON_OBJS) 

# Build CheckIndexed
CheckIndexed:	$(OBJ_DIR)/CheckIndexed.$(OBJ_EXT) $(COMMON_OBJS)
	$(CC) $(LDFLAGS) $(LINK_OUT_FLAG)$(BUILD_DIR)/CheckIndexed $(OBJ_DIR)/CheckIndexed.$(OBJ_EXT) $(COMMON_OBJS)

$(OBJ_DIR)/%.$(OBJ_EXT): %.c
	$(CC) $(CFLAGS) $< $(OBJ_SWITCH)$@

$(OBJ_DIR)/%.$(OBJ_EXT): ./common/%.c
	$(CC) $(CFLAGS) $< $(OBJ_SWITCH)$@
