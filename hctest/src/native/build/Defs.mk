#
# $Id: Defs.mk 11721 2008-01-04 20:26:37Z wr152514 $
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

OS := $(shell uname -s)
OS := $(patsubst CYGWIN%,Win32,$(OS))
OS := $(patsubst Darwin%,macOSX,$(OS))
include $(NATIVE_ROOT)/build/Defs.$(OS).mk

NATIVE_ROOT := $(shell cd $(NATIVE_ROOT); $(PWD))
HC_SVN_ROOT := $(shell cd $(NATIVE_ROOT)/../../..; $(PWD))
BUILD_ROOT := $(shell cd $(HC_SVN_ROOT)$(FILE_SEP)build; $(PWD))
HC_CLIENT_BUILD_CURL_ROOT := $(BUILD_ROOT)$(FILE_SEP)client_c$(FILE_SEP)build_$(PLATFORM)$(FILE_SEP)curl
HC_CLIENT_BUILD_CURL_LIB_DIR := $(HC_CLIENT_BUILD_CURL_ROOT)$(FILE_SEP)dist$(FILE_SEP)lib
HC_CLIENT_BUILD_ROOT := $(BUILD_ROOT)$(FILE_SEP)client_c$(FILE_SEP)build_$(PLATFORM)$(FILE_SEP)honeycomb
HC_CLIENT_BUILD_LIB_DIR := $(HC_CLIENT_BUILD_ROOT)$(FILE_SEP)dist
HC_HCTEST_BUILD_LIB_DIR := $(BUILD_ROOT)$(FILE_SEP)hctest$(FILE_SEP)dist$(FILE_SEP)lib$(FILE_SEP)build_$(PLATFORM)
HC_CLIENT_BUILD_INCLUDE_DIR := $(HC_CLIENT_BUILD_ROOT)$(FILE_SEP)src
HC_HCTEST_UTIL_SRC_DIR := $(NATIVE_ROOT)$(FILE_SEP)util
HC_HCTEST_COMMON_SRC_DIR := $(NATIVE_ROOT)$(FILE_SEP)common
HC_HCTEST_BUILD_BIN_DIR := $(BUILD_ROOT)$(FILE_SEP)hctest$(FILE_SEP)dist$(FILE_SEP)bin$(FILE_SEP)build_$(PLATFORM)
HCTEST_UTIL_LIB := $(CC_LIB_PREFIX)hctestutil.$(CC_LIB_SUFFIX)
HCTEST_COMMON_LIB := $(CC_LIB_PREFIX)hctestcommon.$(CC_LIB_SUFFIX)

HARNESS_C_FILES :=  hctestharness.c
HARNESS_O_FILES := $(HARNESS_C_FILES:.c=.$(CC_OBJ_SUFFIX))
TEST_C_FILES    := hcDelete.c hcezGetData.c hcezStoreData.c hcGetSchema.c hcnbGetData.c hcnbGetMD.c hcnbStoreData.c hcnbStoreMD.c hcezQry.c hcezRangeRetrieve.c hcezQryLarge.c hcezQryUsage.c hcezQryPstmt.c hcezSessions.c hcezStoreRetrieve.c hcezStoreRetrieveMD.c hcezStoreRetrieveMDutf8.c hcezStoreRetrieveMDnew.c hcezCompliance.c hcezStoreRetrieveMDlatin1.c hcezMulticellQuery.c
TEST_O_FILES := $(TEST_C_FILES:.c=.$(CC_OBJ_SUFFIX))
COMMON_C_FILES    :=  hctestcommon.c 
COMMON_O_FILES    := $(COMMON_C_FILES:.c=.$(CC_OBJ_SUFFIX))
UTIL_C_FILES    :=  hctestutil.c sha1.c my_getopt.c
UTIL_O_FILES    := $(UTIL_C_FILES:.c=.$(CC_OBJ_SUFFIX))
LOAD_C_FILES	:= load.c test.c
LOAD_O_FILES	:= $(LOAD_C_FILES:.c=.$(CC_OBJ_SUFFIX))
MISC_C_FILES	:= queryall.c 
MISC_O_FILES	:= $(MISC_C_FILES:.c=.$(CC_OBJ_SUFFIX))
MCELLQUERY_C_FILES   := mcellQuery.c
MCELLQUERY_O_FILES   := $(MCELLQUERY_C_FILES:.c=.$(CC_OBJ_SUFFIX))

