##
# $Id: Makefile 11949 2008-04-02 08:32:24Z ds158322 $
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
# Top level makefile
#
# Note: Use bash, please!
##

help:
	@echo "Usage:\n\
# build \n\
make check_commit      build and run a bunch of checks before commit\n\
make all               build all directories under trunk\n\
make <package>         build an individual package (and dependencies)\n\
make cleanall          clean all build directories\n\
make clean_<package>   run 'ant clean' in a package directory\n\
\t\t\t<package> is dir name under trunk, such as server\n\
# svn \n\
make check_tree        check if the source tree is upto date\n\
make updateall         update all pacakges under trunk\n\
make update_<package>  run 'svn update' on a package directory\n\
\t\t\t<package> is dir name under trunk, such as server\n\
# install/test\n\
make run_unit_tests    run unit tests against local packages\n\
make type=<ramdisk|bootnet|pkgadd> install_<sys>     pkgadd all local packages to a HON cluster\n\
make client=<cl> install_client    install hctest onto given client\n\
make client=<cl> install_perf      install performance test suite on given client\n\
make client=<cl> quicktest_<sys>   quicktest against <sys> from <cl>\n\
make client=<cl> regression_<sys>  launch regression tests on <cl> \n\
\n\
\t\t\t<sys> is the remote cluster, such as dev321\n\
\t\t\t<cl> is client, e.g. cl33 (default: local machine) \n\

DIRS_TO_BUILD := client \
                 common \
                 mgmt \
                 filesystem \
                 md_caches \
                 md_clustra \
                 server \
                 ndmp \
                 test \
                 hctest \
                 unit_tests \
                 whitebox \
                 external \
                 platform \
                 suitcase \
                 client_c \
                 sdk \
                 performance \
                 adm \
                 admingui \
                 emulator \
                 itest    \
                 extractor


CLUSTER_NAMES := \
    dev301 dev302 dev303 dev304 dev305 \
    dev306 dev307 dev308 dev309 dev310 dev311 dev312 dev313 dev314 dev315 \
    dev316 dev317 dev318 dev319 dev320 dev321 dev322 dev323 dev324 dev325 dev327 \
    dev328 dev329 dev330 dev331 dev401 dev402 dev403 factory\

OS := $(shell uname -s)
OS := $(patsubst CYGWIN%,Win32,$(OS))
OS := $(patsubst Darwin%,macOSX,$(OS))
ROOT := $(shell cd ..; pwd)
ifeq ($(OS),Win32)
ROOT := $(shell cygpath -m $(ROOT))
endif

ANT_FLAGS := -emacs -quiet
ANT := ant

## Build all
# XXX platform attempts to build server and other packages, and causes
# delays.
all: version pkgs

pkgs: $(DIRS_TO_BUILD)

$(DIRS_TO_BUILD):
	cd $(ROOT)/$@ && $(ANT) $(ANT_FLAGS) -Dbasedir=$(ROOT)/build build_pkg

# XXX: We should move these dependencies to build.xml
platform common: external

client server md_caches emulator: common mgmt

filesystem md_clustra unit_tests whitebox emulator hctest: server

sdk hctest: client emulator

sdk suitcase openedition: emulator

hctest sdk: client_c

hctest: sdk

hctest: whitebox

performance: server client test

emulator: md_clustra adm ndmp admingui

#Special tags for perf test
dtf: 
	cd $(ROOT)/hctest && $(ANT) $(ANT_FLAGS) dtf

openedition: 
	cd $(ROOT)/emulator && $(ANT) $(ANT_FLAGS) openedition

#Special tags to build the HADB-based Advanced Emulator (see hc-twiki page AdvancedEmulator)
emulator_hadb: emulator
	cd $(ROOT)/emulator && $(ANT) $(ANT_FLAGS) -Dbasedir=$(ROOT)/build dist_hadb

sdk_hadb: emulator_hadb sdk

## Unit tests
run_unit_tests:
	cd $(ROOT)/build/unit_tests/dist && ./run_tests.sh
	cd $(ROOT)/build/unit_tests/deleteTest/dist && ./runAll.sh

## Clean a directory
DIRS_TO_CLEAN := $(foreach dir, $(DIRS_TO_BUILD), clean_$(dir))
$(DIRS_TO_CLEAN):
	@DIR=`echo $@ | cut -c7-`; echo "\nCleaning $$DIR\n"; cd $(ROOT)/$$DIR && $(ANT) clean

## Clean everything 
cleanall: $(DIRS_TO_CLEAN)
	@echo "Final clean up"
	@rm -fr factory_test pkgdir alogger

## Update a package
DIRS_TO_UPDATE := $(foreach dir, $(DIRS_TO_BUILD) build, update_$(dir))
$(DIRS_TO_UPDATE):
	@DIR=`echo $@ | cut -c8-`; echo "\nUpdating $$DIR\n"; cd $(ROOT)/$$DIR && svn update

## Update all packages
updateall:
	@cd $(ROOT); \
  if svn info > /dev/null 2>&1; then \
    svn update; \
  else \
    svn update $(DIRS_TO_UPDATE); \
  fi; \

## Basic verification to check if tree is upto date
check_tree:
	@check_dir () { \
    cd $$1; \
    echo "Checking $$1"; \
    TMPFILE=`mktemp` && svn status -u | grep "[!|~|*]" > $$TMPFILE; \
    if [ -s $$TMPFILE ]; then \
      echo "Following files are out of date:"; \
      cat $$TMPFILE; \
      rm -f $$TMPFILE; \
      exit 1; \
    fi; \
    echo "$$1 is in sync"; \
    rm -f $$TMPFILE; \
  }; \
  cd $(ROOT); \
  if svn info > /dev/null 2>&1; then \
    check_dir $(ROOT); \
  else \
    for dir in $(DIRS_TO_BUILD); do \
      if [ -d $(ROOT)/$$dir ]; then \
        check_dir $(ROOT)/$$dir; \
      else \
        echo "$$dir is not under version control"; \
      fi; \
    done; \
  fi;

## Some checks before comitting files
check_commit: check_tree cleanall version all run_unit_tests

## Install packages to remote cluster by calling script
INSTALL := $(foreach cluster, $(CLUSTER_NAMES), install_$(cluster))
$(INSTALL):
	@if [ -z "$(type)" ]; then \
	    echo "usage: make type=<ramdisk|bootnet|pkgadd> install_<devXXX>"; \
	elif [ "$(type)" != "ramdisk" -a "$(type)" != "bootnet" -a "$(type)" != "pkgadd" ]; then \
	    echo "usage: make type=<ramdisk|bootnet|pkgadd> install_<devXXX>"; \
	else \
	    CLUSTER=`echo $@ | cut -c9-`; \
	    INSTALLTYPE=$(type); \
	    echo "\nInstalling $$CLUSTER ($$INSTALLTYPE)\n"; \
	    INSTALLTYPE=$$INSTALLTYPE $(ROOT)/tools/buildinstalltest/install.sh $$CLUSTER $$pkgdir; \
	fi

## Run quick test against remote cluster, using local test packages
QUICKTEST := $(foreach cluster, $(CLUSTER_NAMES), quicktest_$(cluster))
$(QUICKTEST):
	# if client not specified, that's okay, defaults to local machine
	@CLUSTER=`echo $@ | cut -c11-`; echo "\nStarting quicktest on $$CLUSTER\n"; cd $(ROOT)/tools/buildinstalltest && ./quicktest.sh $$CLUSTER $$client
## Run full regression against remote cluster
REGRESSION := $(foreach cluster, $(CLUSTER_NAMES), regression_$(cluster))
$(REGRESSION):
	# if client not specified, that's okay, defaults to local machine
	@CLUSTER=`echo $@ | cut -c12-`; echo "\nStarting regression on $$CLUSTER\n"; cd $(ROOT)/tools/buildinstalltest && ./regressiontest.sh $$CLUSTER $$client

## Install the client with local hctest tar
install_client: dtf
	@if [ -z "$(client)" ]; then	\
	    echo "usage: make client=<cl> install_client";	\
	    exit 1;	\
	fi
	@client=`echo $(client) | sed s/cl//g`; \
	$(ROOT)/hctest/src/cluster/utils/update-client.sh -l cur -c -b $(ROOT) -u cur $$client

## Install perf test suite on the client
install_perf: dtf
	@if [ -z "$(client)" ]; then \
	    echo "usage: make client=<cl> install_client"; \
	    exit 1; \
	fi
	@$(ROOT)/performance/src/scripts/install_client.sh -r $(ROOT) -c $$client

env:
	echo $$foo

version:
	@./make_version.sh $(DIRS_TO_BUILD)

.PHONY: version all $(DIRS_TO_BUILD)
