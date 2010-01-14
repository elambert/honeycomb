/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <fcntl.h>
#include "platform.h"
#include "hcoa.h"
#include "hcnvoa.h"
#include "hcclient.h"
#include "hcoaez.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"

char *nb_getmd_tags[] = {"capi","capinb_getmd"};
int hcnb_getmd_simpletest_exec(test_case_t *);


void hcnb_getmd_load_tests(hctest_env_t *test_env) {
  char *argv[] = {NULL};
	add_test_to_list(create_and_tag_test_case("capinb_getmd_simpletest",
						  3, argv,
						  (int (*)(test_case_t*))hcnb_getmd_simpletest_exec,nb_getmd_tags,2,test_env),test_env,FALSE);
}


int hcnb_getmd_simpletest_exec(test_case_t *tc) {

	char **argv = NULL;
	char *test_vip = NULL;
	char *errstr = NULL;
	char **names_r = NULL;
	char **values_r = NULL;
	char *names[] = {"first","longlarge","doublesmall"};
	char *values[] = {"astring","1234567","3.14"};
	hcerr_t res;
	hc_long_t file_size; 
	long response_code;
	hc_random_file_t *r_file = NULL;
	void * md_handle = NULL;
	hc_system_record_t sys_rec;
	hc_hashlist_t  *md_list = NULL;
	int port,max,done,count,max_read_fd,max_write_fd,test_res;
	long errstr_len = 4096;
	fd_set read_fd_set,write_fd_set;
	struct timeval timeout;
	hc_nvr_t *nvr = NULL;
	hc_nvr_t *nvr2 = NULL;
	hc_session_t *session = NULL;
	hctest_env_t *env = NULL;
	int i;
	
	env = tc->test_env;
	argv = tc->argv;
	test_vip = env->data_vip;
	port = env->data_vip_port;
	file_size = 4096;
	errstr = (char *)malloc(errstr_len);
	r_file = create_random_file(4096);


	//add_md
	res = hc_session_create_ez(test_vip,port,&session);
	res = hc_nvr_create_from_string_arrays(session, &nvr,names,values,3);
	res = hc_store_both_ez(session,read_from_random_data_generator,r_file,nvr,&sys_rec);
	res = hc_nvr_free(nvr);
	md_list = create_hashlist(1012);
	md_array_to_hashlist(names,values,3,md_list);

	// get_md 
	res = hc_retrieve_metadata_ez(session,(hc_oid *)sys_rec.oid,&nvr2);
	res = hc_nvr_convert_to_string_arrays(nvr2,&names_r,&values_r,&count);
	if (count != md_list->number_of_entries) {
	  hc_test_log (LOG_ERROR_LEVEL,NULL,"Number of md entries do not match! Expected %d, got %d\n",(int)md_list->number_of_entries, (int)count);
	  test_res = TEST_FAIL;
	}
	for (i = 0; i < count; i++) {
	  char *cur_name,*cur_val, *exp_val;
	  cur_name = *names_r;
	  cur_val = *values_r;
	  exp_val = hash_get_value(cur_name,md_list);
	  if (strcmp(exp_val, cur_val) != 0) {
	    test_res = TEST_FAIL;
	    hc_test_log(LOG_ERROR_LEVEL,NULL,"Value retrieved for key %s does not match expected valule. Expected %s, got %s \n",cur_name,exp_val,cur_val);
	  }
	  names_r++;
	  values_r++;
	}
  free_r_file(r_file);
  free_hashlist(md_list);
  free(errstr);
  
  return test_res;
	
}

