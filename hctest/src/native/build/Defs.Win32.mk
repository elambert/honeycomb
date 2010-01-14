#
# $Id: Defs.Win32.mk 11721 2008-01-04 20:26:37Z wr152514 $
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

FILE_SEP          := /
CC                := c:\\Program\ Files\\Microsoft\ Visual\ Studio\ 8\\VC\\bin\\cl.exe
CC_EXE_FLAGS      := /MTd /Fe
CC_FLAGS          := /MTd /c /Fo
CC_INCLUDE_FLAG   := /I
CC_LIBLD_FLAGS    := /out:
CC_LIB_PREFIX     := 
CC_LIB_SUFFIX     := lib
CC_LIB_TOOL       := lib
CC_OBJ_SUFFIX     := obj
CHMOD             := chmod
CURL_LIB          := libcurl.dll
CURL_LIB_COMP     := libcurl_imp.lib
HCCLIENT_LIB      := honeycomb.dll
HCCLIENT_LIB_COMP := honeycomb_imp.lib
HCTEST_COMMON_EXE := hctestharness.exe
HCLOAD_COMMON_EXE := hcload.exe
QUERYALL_EXE      := queryall.exe
MCELLQUERY_EXE    := mcellQuery.exe
MKDIR             := mkdir
OS                := Win32
PLATFORM          := $(OS)
PWD               := cygpath -m `pwd`
RM                := rm
MAKE              := make
WS2_LIB           := WS2_32.lib
YACC              := bison -y

NATIVE_ROOT := $(shell cd $(NATIVE_ROOT); $(PWD))
HC_CLIENT_PTW32_LIB_DIR :=$(NATIVE_ROOT)$(FILE_SEP)external$(FILE_SEP)pthreads-w32-2-7-0-release
HC_CLIENT_PTW32_INCLUDE_DIR := $(HC_CLIENT_PTW32_LIB_DIR)
HC_CLIENT_PTW32_LIB_COMP := pthreadVC2.lib

