#
# $Id: Defs.macOSX.mk 11721 2008-01-04 20:26:37Z wr152514 $
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
CC                := /usr/bin/cc
CC_EXE_FLAGS      := -o 
CC_FLAGS	  := -fPIC -g -c -o 
CC_INCLUDE_FLAG   := -I
CC_LIBLD_FLAGS    := -lz -dynamiclib -single_module -o 
CC_LIB_PREFIX     := lib
CC_LIB_SUFFIX     := dylib
CC_LIB_TOOL       := $(CC)
CC_OBJ_SUFFIX     := o
CHMOD             := /bin/chmod
CURL_LIB          := libcurl.a
CURL_LIB_COMP     := $(CURL_LIB)
HCCLIENT_LIB      := libhoneycomb.dylib
HCCLIENT_LIB_COMP := $(HCCLIENT_LB)
HCTEST_COMMON_EXE := hctestharness
HCLOAD_COMMON_EXE := hcload
QUERYALL_EXE      := queryall
MCELLQUERY_EXE    := mcellQuery
JAVA_HOME         := /System/Library/Frameworks/JavaVM.framework/Home
MKDIR             := /bin/mkdir
OS                := macOSX
PLATFORM	  := $(OS)
PWD               := /bin/pwd
RM                := /bin/rm
MAKE              := make
