# $Id: Makefile 11721 2008-01-04 20:26:37Z wr152514 $
# Copyright (C) 2005 Sun Microsystems Inc
#

############################
#------ DEFINITIONS -------#
############################

NATIVE_ROOT := $(shell cd ..;pwd)
include $(NATIVE_ROOT)/build/Defs.mk


#################################
#------ TOP LEVEL TARGETS ------#
#################################

all: util common tests loadsrc miscsrc mcellquerysrc \
	harness load misc mcellquery


clean_all: clean all

$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(HARNESS_O_FILES): $(HC_HCTEST_COMMON_SRC_DIR)$(FILE_SEP)$(HARNESS_C_FILES)
	$(MKDIR) -p $(dir $@)
	$(CC) \
	$(CC_INCLUDE_FLAG)$(HC_CLIENT_BUILD_INCLUDE_DIR) $(CC_INCLUDE_FLAG)$(HC_HCTEST_UTIL_SRC_DIR) \
	$(CC_INCLUDE_FLAG)$(HC_HCTEST_COMMON_SRC_DIR) \
	$(CC_FLAGS)$@ $^

harness: $(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(HCTEST_COMMON_EXE) 
ifeq ($(OS),Win32)
$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(HCTEST_COMMON_EXE): $(HARNESS_O_FILES:%=$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)%) \
	                                                   $(TEST_O_FILES:%=$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)%) \
	                                                   $(COMMON_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
	                                                   $(UTIL_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
							   $(HC_CLIENT_BUILD_CURL_LIB_DIR)$(FILE_SEP)$(CURL_LIB_COMP) \
							   $(HC_CLIENT_PTW32_LIB_DIR)$(FILE_SEP)$(HC_CLIENT_PTW32_LIB_COMP) \
							   $(HC_CLIENT_BUILD_LIB_DIR)$(FILE_SEP)$(HCCLIENT_LIB_COMP)
	@echo ""
	@echo "========== Building Harness:Start =========="
	@echo ""
	$(MKDIR) -p $(dir $@)
	$(CC) $(CC_EXE_FLAGS)$@ $^ $(WS2_LIB)
	@echo ""
	@echo "========== Building Harness:Finished =========="
	@echo ""
#	$(CC) -L$(HC_HCTEST_BUILD_LIB_DIR) -L$(HC_HCTEST_BUILD_BIN_DIR) -L$(HC_CLIENT_BUILD_LIB_DIR) -lhoneycomb  -o $@ $^ $(WS2_LIB)
else

$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(HCTEST_COMMON_EXE): $(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(HARNESS_O_FILES) \
	                                                   $(UTIL_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
	                                                   $(COMMON_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
	                                                   $(TEST_O_FILES:%=$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)%) 
	@echo ""
	@echo "========== Building Harness:Start =========="
	@echo ""
	$(MKDIR) -p $(dir $@)
	$(CC) -L$(HC_CLIENT_BUILD_LIB_DIR) -lhoneycomb  $(CC_EXE_FLAGS)$@ $^
	@echo ""
	@echo "========== Building Harness:Finished =========="
	@echo ""
endif


load: $(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(HCLOAD_COMMON_EXE)
ifeq ($(OS),Win32)
$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(HCLOAD_COMMON_EXE): $(LOAD_O_FILES:%=$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)%) \
	$(UTIL_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
	$(COMMON_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
	$(HC_CLIENT_BUILD_CURL_LIB_DIR)$(FILE_SEP)$(CURL_LIB_COMP) \
	$(HC_CLIENT_BUILD_LIB_DIR)$(FILE_SEP)$(HCCLIENT_LIB_COMP) \
	$(HC_CLIENT_PTW32_LIB_DIR)$(FILE_SEP)$(HC_CLIENT_PTW32_LIB_COMP)
	@echo ""
	@echo "========== Building Load :Start =========="
	@echo ""
	$(MKDIR) -p $(dir $@)
	$(CC) $(CC_EXE_FLAGS)$@ $^  $(WS2_LIB)
	@echo ""
	@echo "========== Building Load :Finished =========="
	@echo ""
else
$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(HCLOAD_COMMON_EXE): $(LOAD_O_FILES:%=$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)%) \
	$(UTIL_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
	$(COMMON_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%)
	@echo ""
	@echo "========== Building Load :Start =========="
	@echo ""
	$(MKDIR) -p $(dir $@)
	$(CC) -L$(HC_CLIENT_BUILD_LIB_DIR) -lhoneycomb -lrt $(CC_EXE_FLAGS)$@ $^
	@echo ""
	@echo "========== Building Load :Finished =========="
	@echo ""
endif

misc: $(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(QUERYALL_EXE)
ifeq ($(OS),Win32)
$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(QUERYALL_EXE): $(MISC_O_FILES:%=$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)%) \
	$(HC_CLIENT_BUILD_CURL_LIB_DIR)$(FILE_SEP)$(CURL_LIB_COMP) \
	$(HC_CLIENT_BUILD_LIB_DIR)$(FILE_SEP)$(HCCLIENT_LIB_COMP) \
	$(HC_CLIENT_PTW32_LIB_DIR)$(FILE_SEP)$(HC_CLIENT_PTW32_LIB_COMP)
	@echo ""
	@echo "========== Building Queryall:Start =========="
	@echo ""
	$(MKDIR) -p $(dir $@)
	$(CC) $(CC_EXE_FLAGS)$@ $^  $(WS2_LIB)
	@echo ""
	@echo "========== Building Queryall:Finished =========="
	@echo ""
else
$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(QUERYALL_EXE): $(MISC_O_FILES:%=$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)%)
	@echo ""
	@echo "========== Building Queryall :Start =========="
	@echo ""
	$(MKDIR) -p $(dir $@)
	$(CC) -L$(HC_CLIENT_BUILD_LIB_DIR) -lhoneycomb -lrt $(CC_EXE_FLAGS)$@ $^
	@echo ""
	@echo "========== Building Queryall :Finished =========="
	@echo ""
endif

mcellquery:	$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(MCELLQUERY_EXE)
ifeq ($(OS),Win32)
$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(MCELLQUERY_EXE): $(MCELLQUERY_O_FILES:%=$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)%) \
		$(UTIL_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
		$(COMMON_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
	$(HC_CLIENT_BUILD_CURL_LIB_DIR)$(FILE_SEP)$(CURL_LIB_COMP) \
	$(HC_CLIENT_BUILD_LIB_DIR)$(FILE_SEP)$(HCCLIENT_LIB_COMP) \
	$(HC_CLIENT_PTW32_LIB_DIR)$(FILE_SEP)$(HC_CLIENT_PTW32_LIB_COMP)
	@echo ""
	@echo "========== Building mcellQuery:Start =========="
	@echo ""
	$(MKDIR) -p $(dir $@)
	$(CC) $(CC_EXE_FLAGS)$@ $^  $(WS2_LIB)
	@echo ""
	@echo "========== Building mcellQuery:Finished =========="
	@echo ""
else
$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)$(MCELLQUERY_EXE): $(MCELLQUERY_O_FILES:%=$(HC_HCTEST_BUILD_BIN_DIR)$(FILE_SEP)%) \
		$(UTIL_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%) \
		$(COMMON_O_FILES:%=$(HC_HCTEST_BUILD_LIB_DIR)$(FILE_SEP)%)
	@echo ""
	@echo "========== Building mcellQuery:Start =========="
	@echo ""
	$(MKDIR) -p $(dir $@)
	$(CC) -L$(HC_CLIENT_BUILD_LIB_DIR) -lhoneycomb -lrt $(CC_EXE_FLAGS)$@ $^
	@echo ""
	@echo "========== Building mcellQuery:Finished =========="
	@echo ""
endif

util:
	@echo ""
	@echo "========== Building Util:Start =========="
	@echo ""
	(cd ../util; $(MAKE))
	@echo ""
	@echo "========== Building Util:Finished =========="
	@echo ""

common:
	@echo ""
	@echo "========== Building Common:Start =========="
	@echo ""
	(cd ../common; $(MAKE))
	@echo ""
	@echo "========== Building Common:Finished =========="
	@echo ""

tests:
	@echo ""
	@echo "========== Building Tests:Start =========="
	@echo ""
	(cd ../cases/api/client; $(MAKE))
	@echo ""
	@echo "========== Building Tests:Finished =========="
	@echo ""

loadsrc:
	@echo ""
	@echo "========== Building Load:Start =========="
	@echo ""
	(cd ../load; $(MAKE))
	@echo ""
	@echo "========== Building Load:Finished =========="
	@echo ""

miscsrc:
	@echo ""
	@echo "========== Building Misc:Start =========="
	@echo ""
	(cd ../misc; $(MAKE))
	@echo ""
	@echo "========== Building Misc:Finished =========="
	@echo ""

mcellquerysrc:
	@echo ""
	@echo "========== Building mcellquery::Start =========="
	@echo ""
	(cd ../mcellquery; $(MAKE))
	@echo ""
	@echo "========== Building mcellquery::Finished =========="
	@echo ""

clean:
	@echo ""
	@echo "========== Clean Starting =========="
	@echo ""
	$(RM) -rf $(HC_HCTEST_BUILD_LIB_DIR) $(HC_HCTEST_BUILD_BIN_DIR)
	@echo ""
	@echo "========== Clean Finshed =========="
	@echo ""
