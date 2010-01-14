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

#
# This module is ONLY supported on amd64.
#
# Attempts to build or deploy on other platforms may have
# undesirable results.  This Makefile is not intended to
# be generic.  It was verified to work on hc-dev2.
#

############################
#------ DEFINITIONS -------#
############################

FILE_SEP          := /
RM                := /bin/rm
CP                := /bin/cp
MKDIR             := /bin/mkdir

MODNAME := hctestmod

MYCWD := .
HC_BUILD_ROOT := $(shell cd $(MYCWD)$(FILE_SEP)..$(FILE_SEP)..$(FILE_SEP)..$(FILE_SEP)..$(FILE_SEP)..$(FILE_SEP)build; pwd)
HC_HCTEST_BUILD_ROOT := $(HC_BUILD_ROOT)$(FILE_SEP)hctest
HC_HCTEST_BUILD_KERNEL_DIR := $(HC_HCTEST_BUILD_ROOT)$(FILE_SEP)dist$(FILE_SEP)kernel
HC_HCTEST_BUILD_HCTESTMOD_DIR := $(HC_HCTEST_BUILD_KERNEL_DIR)$(FILE_SEP)$(MODNAME)
CFILE := $(MODNAME).c
MODULEFILE := $(HC_HCTEST_BUILD_HCTESTMOD_DIR)$(FILE_SEP)$(MODNAME)
OFILE := $(MODULEFILE).o
CONFFILE := $(MODNAME).conf
README := README
DEBUG := 
#DEBUG := -DDEBUG

COMPILECMD := /ws/on10-tools/onbld/bin/i386/cw -_gcc -xarch=amd64 -Ui386 -U__i386 -xO2 ../../intel/amd64/ml/amd64.il -D_ASM_INLINES -Xa -xspace  -_gcc=-ffreestanding  -_gcc=-mcmodel=kernel -_gcc=-mno-red-zone -v -xildoff  -g -xc99=%none -W0,-noglobal -errtags=yes -errwarn=%all -erroff=E_EMPTY_TRANSLATION_UNIT -erroff=E_STATEMENT_NOT_REACHED -xc99=%none  -D_KERNEL $(DEBUG) -D_SYSCALL32 -D_SYSCALL32_IMPL -D_ELF64 -Dsun -D__sun -D__SVR4 -Di86pc -D_MACHDEP -DOPTERON_ERRATUM_88 -DOPTERON_ERRATUM_91 -DOPTERON_ERRATUM_93 -DOPTERON_ERRATUM_95 -DOPTERON_ERRATUM_100 -DOPTERON_ERRATUM_108 -DOPTERON_ERRATUM_109 -DOPTERON_ERRATUM_121 -DOPTERON_ERRATUM_122 -DOPTERON_ERRATUM_131 -D_SIMULATOR_SUPPORT  -DC2_AUDIT   -I../../i86pc -I../../intel -I/usr/include -I /usr/platform/i86pc/include/ -Y I,../../common  -c -o $(OFILE) $(CFILE)

LINKCMD := /usr/ccs/bin/ld -r -o $(MODULEFILE) $(OFILE)

#################################
#------ TOP LEVEL TARGETS ------#
#################################

$(MODNAME): $(CFILE) $(CONFFILE) $(README)
	$(MKDIR) -p $(HC_HCTEST_BUILD_HCTESTMOD_DIR)
	$(COMPILECMD)
	$(LINKCMD)
	$(RM) $(OFILE)
	$(CP) $(CONFFILE) $(README) $(HC_HCTEST_BUILD_HCTESTMOD_DIR)

clean:
	$(RM) -rf $(HC_HCTEST_BUILD_HCTESTMOD_DIR)
