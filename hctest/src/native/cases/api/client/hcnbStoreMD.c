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
#include "hcoaez.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"

int HC_STORE_MD = 0;
int HC_STORE_BOTH = 1;

int hcnb_storemd_create_handle_exec(test_case_t *);
int hcnb_storeboth_create_handle_exec(test_case_t *);
hcerr_t hcnbmd_initialize(void *, char *, int, char *, int, hc_long_t, hc_long_t, hc_oid *, hc_session_t*, int);
int hcnb_storeboth_interesting_filesizes_exec (test_case_t *);

char *nb_storemd_tags[] = {"capi","capinb_storemd"};
enum hcnd_storemd_handle_testparms { 
    EXPECTED_RESULT,
	HANDLE_TYPE,
	BUFFER_TYPE,
	VIP_TYPE,
	VIP_PORT_TYPE,
	FAIL_OVER_TYPE,
	OID_TYPE
};




void hcnb_storemd_load_tests(hctest_env_t *test_env) {
	int indx;	
	//HANDLE TEST: handle variants
	hc_llist_t * test_scenarios;
	hc_llist_node_t *cur_node;
	char *argv1[] = {"0",
			 HANDLE_TYPE_STORE_METADATA,
			 "4KB",
			 VIP_TYPE_USER,
			 VIP_PORT_TYPE_USER,
			 FO_DEFAULT,
			 OID_TYPE_VALID};
	char *argv2[] = {"6",
			 HANDLE_TYPE_STORE_METADATA,
			 BUFFER_TYPE_NULL,
			 VIP_TYPE_USER,
			 VIP_PORT_TYPE_USER,
			 FO_DEFAULT,
			 OID_TYPE_VALID};
	char *argv3[] = {"0",
			 HANDLE_TYPE_STORE_METADATA,
			 "4KB",
			 VIP_TYPE_EMPTY,
			 VIP_PORT_TYPE_USER,
			 FO_DEFAULT,
			 OID_TYPE_VALID};

	char *argv4[] = {"6",
			 HANDLE_TYPE_STORE_METADATA,
			 "4KB",
			 VIP_TYPE_NULL,
			 VIP_PORT_TYPE_USER,
			 FO_DEFAULT,
			 OID_TYPE_VALID};
	char *argv5[] = {"0",
			 HANDLE_TYPE_STORE_METADATA,
			 "4KB",
			 VIP_TYPE_IP,
			 VIP_PORT_TYPE_USER,
			 FO_DEFAULT,
			 OID_TYPE_VALID};
	char *argv6[] =  {"0",
			  HANDLE_TYPE_STORE_METADATA,
			  "4KB",
			  VIP_TYPE_USER,
			  VIP_PORT_TYPE_USER,
			  FO_ZERO,
			  OID_TYPE_VALID};
	  
	char *argv7[] = {"6",
			 HANDLE_TYPE_STORE_METADATA,
			 "4KB",
			 VIP_TYPE_USER,
			 VIP_PORT_TYPE_USER,
			 FO_NEGATIVE,
			 OID_TYPE_VALID};
	  
	char *argv8[] =  {"6",
			  HANDLE_TYPE_STORE_METADATA,
			  "4KB",
			  VIP_TYPE_USER,
			  VIP_PORT_TYPE_USER,
			  FO_DEFAULT,
			  OID_TYPE_NULL};
	  
	char *argv9[] =  {"6",
			  HANDLE_TYPE_STORE_METADATA,
			  "4KB",
			  VIP_TYPE_USER,
			  VIP_PORT_TYPE_USER,
			  FO_DEFAULT,
			  OID_TYPE_INVALID};

	test_scenarios = hc_create_linkedlist();
	//linkedlist_add_nodes(test_scenarios, 2,(char *[]){"0",HANDLE_TYPE_NULL}, (char *[]){"0",HANDLE_TYPE_STORE_METADATA}); PUKES 
	linkedlist_add_nodes(test_scenarios, 9, argv1, argv2, argv3, argv4, argv5, argv6, argv7, argv8, argv9);
	
	cur_node = test_scenarios->head;
	while (cur_node != NULL) {
		char **val = (char **)cur_node->entry;
		char *argv[] = {val[EXPECTED_RESULT],val[HANDLE_TYPE],val[BUFFER_TYPE],val[VIP_TYPE],val[VIP_PORT_TYPE],
				val[FAIL_OVER_TYPE],val[OID_TYPE]};
		add_test_to_list(create_and_tag_test_case("capinb_storemd_create_storeboth _handle",
							  7, argv,
							  (int (*)(test_case_t*))hcnb_storemd_create_handle_exec,nb_storemd_tags,2,test_env),test_env,FALSE);
		cur_node = cur_node->next;

	}
	free_linkedlist(test_scenarios,free);

	do {
	  char * max_file_size;
	  char **file_size_ptr;
	  int number_of_file_sizes;
	  char *argv1[] = {"0",
			   HANDLE_TYPE_STORE_METADATA,
			   "4KB",
			   VIP_TYPE_USER,
			   VIP_PORT_TYPE_USER,
			   FO_DEFAULT};
	  char *argv2[] =  {"6",
			    HANDLE_TYPE_STORE_METADATA,
			    BUFFER_TYPE_NULL,
			    VIP_TYPE_USER,
			    VIP_PORT_TYPE_USER,
			    FO_DEFAULT};
	  char *argv3[] =  {"0",
			    HANDLE_TYPE_STORE_METADATA,
			    "4KB",
			    VIP_TYPE_EMPTY,
			    VIP_PORT_TYPE_USER,
			    FO_DEFAULT};
	  char *argv4[] =  {"6",
			    HANDLE_TYPE_STORE_METADATA,
			    "4KB",
			    VIP_TYPE_NULL,
			    VIP_PORT_TYPE_USER,
			    FO_DEFAULT};
	  char *argv5[] =  {"0",
			    HANDLE_TYPE_STORE_METADATA,
			    "4KB",
			    VIP_TYPE_IP,
			    VIP_PORT_TYPE_USER,
			    FO_DEFAULT};	
	  char *argv6[] =  {"0",
			    HANDLE_TYPE_STORE_METADATA,
			    "4KB",
			    VIP_TYPE_USER,
			    VIP_PORT_TYPE_USER,
			    FO_ZERO};
	  char *argv7[] =  {"6",
			    HANDLE_TYPE_STORE_METADATA,
			    "4KB",
			    VIP_TYPE_USER,
			    VIP_PORT_TYPE_USER,
			    FO_NEGATIVE};

	// store both handle tests
	test_scenarios = hc_create_linkedlist();
	//linkedlist_add_nodes(test_scenarios, 2,(char *[]){"0",HANDLE_TYPE_NULL}, (char *[]){"0",HANDLE_TYPE_STORE_METADATA}); PUKES 
	linkedlist_add_nodes(test_scenarios, 7, argv1, argv2, argv3, argv4, argv5, argv6, argv7);
	
	cur_node = test_scenarios->head;
	while (cur_node != NULL) {
		char **val = (char **)cur_node->entry;
		char *argv[] = {val[EXPECTED_RESULT],val[HANDLE_TYPE],val[BUFFER_TYPE],val[VIP_TYPE],val[VIP_PORT_TYPE],
				val[FAIL_OVER_TYPE]};
		add_test_to_list(create_and_tag_test_case("capinb_storemd_create_handle",
							  6, argv,
							  (int (*)(test_case_t*))hcnb_storeboth_create_handle_exec,nb_storemd_tags,2,test_env),test_env,FALSE);
		cur_node = cur_node->next;

	}
	free_linkedlist(test_scenarios,free);
	
	// STORE TESTS --INTERSTING FILE SIZES
/*	char * buffer_size_types_list [] = {
		HCFILE_SIZE_TYPE_EMPTY,
		HCFILE_SIZE_TYPE_ONEBYTE,
		HCFILE_SIZE_TYPE_SMALL,
		"1K",
		"4K"
	}; */
	
	max_file_size = hash_get_value(CONTEXT_KEY_MAX_FILE_SIZE,test_env->context_hash);
	file_size_ptr = NULL;
	number_of_file_sizes = generate_absolute_file_size_list(&file_size_ptr,get_file_size(max_file_size));

	test_scenarios = hc_create_linkedlist();
	for (indx = 0; indx < number_of_file_sizes; indx++) { //FILE_LIST
	  char *argv[] = {"0",*file_size_ptr};
			linkedlist_add_nodes(test_scenarios, 1, argv);
			file_size_ptr++;
	} 
	cur_node = test_scenarios->head;
	while (cur_node != NULL) {
		char **val = (char **)cur_node->entry;
		char *argv[] = {val[EXPECTED_RESULT],val[1]};

		add_test_to_list(create_and_tag_test_case("capinb_storemd_storeboth_interesting_filesizes",
							  2, argv,
							  (int (*)(test_case_t*))hcnb_storeboth_interesting_filesizes_exec,nb_storemd_tags,2,test_env),test_env,FALSE);
		cur_node = cur_node->next;

	}

	free_linkedlist(test_scenarios,free);
	//linkedlist_add_nodes(test_scenarios, 2, (char *[]){"0",
     } while (0);
}


int hcnb_storemd_create_handle_exec(test_case_t *tc) { 
	
	int res, expected_result, port;
	char *host = NULL;
	char **argv = NULL;
	char *buffer = NULL;
	void * handle = NULL;
	hc_long_t chunk_size, window_size, buffer_len;
	hc_oid *oid;
	hctest_env_t *env = NULL;
	hc_archive_t *archive = NULL;
	hc_session_t *sessionp = NULL;
	host = buffer =  NULL;
	argv = NULL;
	chunk_size = window_size = buffer_len =0;

	env = tc->test_env;	
	argv = tc->argv;
	expected_result = atoi(argv[0]);
	//resolve_handle_type((char *)argv[1], (void *)&handle);
	resolve_buffer_type((char *)argv[2],&buffer,&buffer_len);
	resolve_vip(argv[3],&host, env);
	port = resolve_vip_port(argv[4],env);
	resolve_fail_over_values(argv[5],&chunk_size,&window_size);
	resolve_oid_type(argv[6],&oid);
	res = hcnbmd_initialize(handle,buffer,buffer_len,host,port,chunk_size,window_size,oid,sessionp, HC_STORE_MD);
	if (res != expected_result) {
		sprintf(tc->note,"Expected a result code of %s, got a %s\n",hc_decode_hcerr(expected_result), hc_decode_hcerr(res));
		return TEST_FAIL;
	} else {
		return TEST_PASS;
	}
}


int hcnb_storeboth_create_handle_exec(test_case_t *tc) {
	/*
	int res, expected_result, port;
	char *host, **argv, *buffer;
	hc_store_metadata_handle_t * handle = NULL;
	hc_long_t chunk_size, window_size, buffer_len;
	
	host = buffer =  NULL;
	argv = NULL;
	chunk_size = window_size = buffer_len =0;
	
	argv = tc->argv;
	expected_result = atoi(argv[0]);
	resolve_handle_type(argv[1], (void *)&handle);
	resolve_buffer_type(argv[2],&buffer,&buffer_len);
	resolve_vip(argv[3],&host);
	port = resolve_vip_port(argv[4]);
	resolve_fail_over_values(argv[5],&chunk_size,&window_size);
	res = hcnbmd_initialize(handle,buffer,buffer_len,host,port,chunk_size,window_size,NULL,HC_STORE_BOTH);
	if (res != expected_result) {
		sprintf(tc->note,"Expected a result code of %s, got a %s\n",hc_decode_hcerr(expected_result), hc_decode_hcerr(res));
		return TEST_FAIL;
	} else {
		return TEST_PASS;
	}
	shutdown_test(handle, buffer); */
  return 0;
}

/*
hcerr_t hcnbmd_add_metadata_exec (test_case_t *tc) { 
	//[0] expected return code
	//[1] Valid OID
	//[2] Number of records[0 .. ]
	//[3] Type of Metadata [string/double/long/random]
	//[4] Size of meta data [buffer/buffer - 1/0/random]
	// genarate OID
	// if ! OID, then delete it
	// for 
	//adds meta_data to it
	// should try things like empty md
	// 
	
}
*/

hcerr_t hcnbmd_initialize(void *handle, char *buffer, int buffer_length, char *host, int port, 
						  hc_long_t chunk_size, hc_long_t window_size, hc_oid *oid, hc_session_t *sessionp, int handle_type) {
	int res;
	res = hc_init(malloc,free,realloc);
	if (res != HCERR_OK && res != HCERR_ALREADY_INITED ) {
		hc_test_log(LOG_ERROR_LEVEL,NULL, "Unable to initialize honeycomb session. hc-init() returned %s.\n",hc_decode_hcerr(res));
		return res;
	}
	res = hc_session_create_ez(host,port,&sessionp);
	if (res != HCERR_OK && res != HCERR_ALREADY_INITED ) {
		hc_test_log(LOG_ERROR_LEVEL,NULL, "Unable to create honeycomb session. hc_session_create_ez() returned %s.\n",hc_decode_hcerr(res));
		return res;
	}
	
/* FIX THE FOLLOWING.   The Create ops now specify the read_from_data_source function and the stream. */

/* 	if (handle_type == HC_STORE_MD) */
/* 		res = hc_store_metadata_create(&handle,buffer,buffer_length,sessionp,chunk_size,window_size,*oid); */
/* 	else if (handle_type == HC_STORE_BOTH) { */
/* 		res = hc_store_both_create(&handle,buffer,buffer_length,sessionp,chunk_size,window_size); */
/* 	} else { */
/* 		hc_test_log(LOG_ERROR_LEVEL,NULL,"Unrecognized handle type\n"); */
/* 		res = HCERR_BAD_REQUEST; */
/* 	} */

	return res;
}

int hcnb_storeboth_interesting_filesizes_exec (test_case_t *tc) { 
	hc_random_file_t * r_file;
	char **argv = NULL;
	char *host = NULL;
	char *file_size_type = NULL;
	char *buffer = NULL;
	char *errstr = NULL;
	char *s_digest = NULL;
	hctest_env_t *env = NULL; 
	int port = -1;
	hc_long_t chunk_size,window_size,file_size = 0;
	void *handle = NULL;
	hc_session_t *sessionp = NULL;
	hc_archive_t *archive = NULL;
	size_t buffer_len = STORE_BUFFER_SIZE;
	hc_system_record_t sys_rec;
	int32_t response_code;
	hc_string_index_t errstrlen = 4096;
	hcerr_t res;

	env = tc->test_env;
	
	//handle = (hc_store_metadata_handle_t *)malloc(sizeof(hc_store_metadata_handle_t));

	s_digest = (char *) malloc(41);
	memset(s_digest,'\0',41);
	argv = tc->argv;
	res = atoi(argv[0]);
	file_size_type = argv[1];
	buffer = (char *) malloc(buffer_len);
	if (buffer == NULL ) {
		sprintf(tc->note,"Unable to allocate enough buffer space for store buffer. Needed %ld .",buffer_len);
		return TEST_ERROR;
	}
	memset(buffer,'\0',buffer_len);
	errstr = (char *) malloc(errstrlen);
	if (errstr == NULL ) {
		sprintf(tc->note,"Unable to allocate enough buffer space for store buffer. Needed %d .",errstrlen);
	}
	memset(errstr,'\0',errstrlen);

	resolve_vip(VIP_TYPE_USER,&host,env);
	port = resolve_vip_port(VIP_PORT_TYPE_USER,env);
	resolve_fail_over_values(FO_ZERO, &chunk_size, &window_size);
  	file_size = get_file_size(file_size_type);	
  	r_file = create_random_file(file_size);	
	res = hcnbmd_initialize(handle, buffer,buffer_len,host,port,chunk_size,window_size,NULL,sessionp,HC_STORE_BOTH);
	hc_session_get_archive (sessionp, &archive);	
	hc_test_log(LOG_INFO_LEVEL,env, "About to store a file of size "LL_FORMAT,file_size);
	res = do_store_from_generator(archive,r_file, STORE_OP_STORE_NVDATA, buffer, buffer_len, handle);
	if (res != HCERR_OK) {
		sprintf(tc->note,"Store operation returned unexpected result code. Got %s, expected %s\n",hc_decode_hcerr(res),hc_decode_hcerr(0));
		printf("SHUTDOWN\n");
		hc_store_metadata_close(handle, &sys_rec);
		printf("ERROR STRING is %s \n", errstr);
		free_r_file(r_file);
		return TEST_FAIL;
	}

	res =  hc_store_metadata_close(handle, &sys_rec);
	if (res != HCERR_OK) {
		sprintf(tc->note,"Store close operation returned unexpected result code. Got %s, expected %s\n",hc_decode_hcerr(res),hc_decode_hcerr(0));
		free_r_file(r_file);
		return TEST_FAIL;
	}

	if (sys_rec.size != file_size) {
		sprintf(tc->note,"Store operation resulted in unmatched file sizes. Expected:"LL_FORMAT", got:"LL_FORMAT"\n",file_size, sys_rec.size);
		free_r_file(r_file);
		return TEST_FAIL;
	}
	
	digest_to_string(r_file->digest,s_digest);
	if (strcmp(sys_rec.data_digest,s_digest) != 0) {
		print_random_file(r_file);
		sprintf(tc->note,"Store operation resulted in unmatched SHA values. Expected %s, got %s\n",sys_rec.data_digest,s_digest);
		free_r_file(r_file);
		return TEST_FAIL;
	}
	
	free_r_file(r_file);
	return TEST_PASS;
	
}



/*
capinb_storemd_create_handle	 ensure that a storemd handle can be obtained and that the API "handles" invalid/illegal values.(valid/invalid/null)
capinb_storemd_create_storeboth_handle	 ensure that a storemd handle can be obtained and that the API "handles" invalid/illegal values.(valid/invalid/null)
capinb_storemd_add_metadata_interesting_mdsizes	  
capinb_storemd_storeboth_interesting_filesizes	 ensure that API can store metadata in the interesting files size set(invalid sizes?)
capinb_storemd_storeboth_interesting_buffersizes	 (invalid sizes?)
capinb_storemd_storeboth_interesting_mdsizes	  
capinb_storemd_interesting_mdcontent	 (escape chars, empty, diff encodings)
capinb_storemd_invalid_OID	 (poorly formed, wrong OID)
capinb_storemd_interesting_md_datacombos	   
capinb_storemd_usage	 (Invalid order of ops, store with null/wrong handle,Improper Close -invalid/null handle, bad error string length size, NP for response_code, errstr, sysrec-)
capinb_storemd_badcluster	 full/invalid
capinb_storemd_invalid_ops_order	  
capinb_storemd_invalid_handle
*/
