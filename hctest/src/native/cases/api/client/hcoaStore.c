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



#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <unistd.h>
#include <fcntl.h>
#include "hcoa.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"

void load_tests(void);
int hc_store_multiple_files(int *, char **, char *);
int hcoa_store_create_handle(int * , char **, char *);

int main (int argc, char **argv) {
    init();
    if (parse_args(argc, argv) != 0) {
	hc_test_log(LOG_ERROR_LEVEL, "An error occurred while parsing the command line arguments.\n");
	return 2;
    }
    if (help_asked == TRUE) {
	usage();
	return 0;
    }
    load_tests();
    run();
    summarize();
    return 0;
}
void load_tests() {

    /*************************/
    /* HANDLE CREATION TESTS */
    /*************************/

    // create a valid store handle
    char *valid_handle_args[] ={"VALID",hash_get_value(CONTEXT_KEY_DATA_VIP,context_hash),hash_get_value(CONTEXT_KEY_DATA_VIP_PORT,context_hash), "0","0",hc_itoa(HCERR_OK)};
    test_case_t *valid_handle = create_test_case("hcoa.hcoa_store_object_create()[valid]", 6, valid_handle_args, (int (*)(void *, void *,void *))hcoa_store_create_handle);
    if (add_test_to_list(valid_handle) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", valid_handle->name, valid_handle->params);
    }

    // create an invalid store handle (NULL PTR for handle)
    char *invalid_handle_args[] ={"INVALID",hash_get_value(CONTEXT_KEY_DATA_VIP,context_hash),hash_get_value(CONTEXT_KEY_DATA_VIP_PORT,context_hash), "0","0",hc_itoa(HCERR_SESSION_CREATE_FAILED)};
    test_case_t *invalid_handle = create_test_case("hcoa.hcoa_store_object_create()[invalid]", 6, invalid_handle_args, (int (*)(void *, void *,void *))hcoa_store_create_handle);
    invalid_handle->excluded = TRUE; //Exclude due to BUG 6316712
    if (add_test_to_list(invalid_handle) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", invalid_handle->name, invalid_handle->params);
    }

    // create an invalid store handle (NULL PTR for host)
    char *invalid_host_args[] ={"VALID",NULL,hash_get_value(CONTEXT_KEY_DATA_VIP_PORT,context_hash), "0","0",hc_itoa(HCERR_SESSION_CREATE_FAILED)};
    test_case_t *invalid_host = create_test_case("hcoa.hcoa_store_object_create()[invalidHost]", 6, invalid_host_args, (int (*)(void *, void *,void *))hcoa_store_create_handle);
    invalid_host->excluded = TRUE; //Exclude due to BUG 6316712
    if (add_test_to_list(invalid_host) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", invalid_host->name, invalid_host->params);
    }

    // create an invalid store handle (clearly invalid port #)
    char *invalid_port_args[] ={"VALID",hash_get_value(CONTEXT_KEY_DATA_VIP,context_hash),"-1", "0","0",hc_itoa(HCERR_SESSION_CREATE_FAILED)};
    test_case_t *invalid_handle_port = create_test_case("hcoa.hcoa_store_object_create()[invalidPort]", 6, invalid_port_args, (int (*)(void *, void *,void *))hcoa_store_create_handle);
    invalid_handle_port->excluded = TRUE; //Exclude due to BUG 6317187
    if (add_test_to_list(invalid_handle_port) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", invalid_handle_port->name, invalid_handle_port->params);
    }

    // create an invalid store handle (negative chunk size)
    char *invalid_chunk_args[] ={"VALID",hash_get_value(CONTEXT_KEY_DATA_VIP,context_hash),hash_get_value(CONTEXT_KEY_DATA_VIP_PORT,context_hash), "-1","0",hc_itoa(HCERR_SESSION_CREATE_FAILED)};
    test_case_t *invalid_handle_chunk = create_test_case("hcoa.hcoa_store_object_create()[invalidChunk]", 6, invalid_chunk_args, (int (*)(void *, void *,void *))hcoa_store_create_handle);
    invalid_handle_chunk->excluded = TRUE; //Exclude due to BUG 6317187
    if (add_test_to_list(invalid_handle_chunk) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", invalid_handle_chunk->name, invalid_handle_chunk->params);
    }
    
    // create an invalid store handle (negative window size)
    char *invalid_window_args[] ={"VALID",hash_get_value(CONTEXT_KEY_DATA_VIP,context_hash),hash_get_value(CONTEXT_KEY_DATA_VIP_PORT,context_hash), "0","-1",hc_itoa(HCERR_SESSION_CREATE_FAILED)};
    test_case_t *invalid_handle_window = create_test_case("hcoa.hcoa_store_object_create()[invalidWindow]", 6, invalid_window_args, (int (*)(void *, void *,void *))hcoa_store_create_handle);
    invalid_handle_window->excluded = TRUE; //Exclude due to BUG 6317187
    if (add_test_to_list(invalid_handle_window) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", invalid_handle_window->name, invalid_handle_window->params);
    }

   
    /******************/
    /* STORE OP TESTS */
    /******************/

    //store single file of random size
    char *single_file_args[] ={"1", "-1", "0"};
    test_case_t *single_file = create_test_case("hcoa.hcoa_store()", 3, single_file_args, (int (*)(void *, void *,void *))hc_store_multiple_files);
    if (add_test_to_list(single_file) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", single_file->name, single_file->params);
    }

    //write multiple files of random with multiple handles
    char *mfmh_args[] ={"256", "-1", "0"};
    test_case_t *multi_file_multi_handle = create_test_case("hcoa.hcoa_store()[multi]", 3, mfmh_args, (int (*)(void *, void *,void *))hc_store_multiple_files);
    if (add_test_to_list(multi_file_multi_handle) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", multi_file_multi_handle->name, multi_file_multi_handle->params);
    }

    //write multiple files of random size with a single handle
    char *mfsh_args[] ={"2", "-1", "1"};
    test_case_t *multi_file_single_handle = create_test_case("hcoa.hcoa_store()[multi-reuse]", 3, mfsh_args, (int (*)(void *, void *,void *))hc_store_multiple_files);
    multi_file_single_handle->excluded = TRUE;
    if (add_test_to_list(multi_file_single_handle) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", multi_file_single_handle->name, multi_file_single_handle->params);
    }

    // attempt to write a file of 0 bytes
    char *empty_file_args[] ={"1", "0", "0"};
    test_case_t *empty_file = create_test_case("hcoa.hcoa_store()", 3, empty_file_args, (int (*)(void *, void *,void *))hc_store_multiple_files);
    if (add_test_to_list(empty_file) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", empty_file->name, empty_file->params);
    }

    // attempt to write a large file
    // interestingly, this test works like champ against the emulator
    // just so-so against the real-deal (fails half the time against dev315)
    char *file_size =  hash_get_value("bfs",context_hash);
    if (file_size == NULL) {
	file_size = "1GB"; 
    }
    char *big_file_args[] ={"1", file_size,"1" }; 
    test_case_t *big_file= create_test_case("hcoa.hcoa_store()[bigFile]", 3, big_file_args, (int (*)(void *, void *,void *))hc_store_multiple_files);
    if (add_test_to_list(big_file) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", big_file->name, big_file->params);
    }

    // attempt to write a file with out establishing a handle

    // attempt to store to an invalid host/port

    // attempt to write to an HC cluster that is up but can not fulfill the request


    /**********************/
    /* HANDLE CLOSE TESTS */
    /**********************/

    //close a valid session

    //perform a close without first doing an open
}


// argv[0] = handle_ptr_type
// argv[1] = host
// argv[2] = port
// argv[3] = chunk_size
// argv[4] = window_size
// argv[5] = expected_hc_err
int hcoa_store_create_handle(int * argc, char ** argv, char *note) {

    char *handle_ptr_mode;
    hcoa_store_handle_t **handle_ptr = NULL;
    char *host;
    int port;
    hc_long_t chunk_size;
    hc_long_t window_size;
    hcerr_t expected_result;
    hcerr_t actual_result;

    if (*argc < 6) {
	hc_test_log(LOG_ERROR_LEVEL,"Test not properly configured!\n");
	return TEST_ERROR;
    }

    for  (int i = 0; i < *argc; i++) {
	char *cur_opt = argv[i];
	switch (i) {
	    case 0:
		handle_ptr_mode = cur_opt;
		break;
	    case 1:
		host = cur_opt;
		break;
	    case 2:
		if (cur_opt == NULL) {
		    hc_test_log(LOG_ERROR_LEVEL,"Test not properly configured! Can not specify a NULL port.\n");
		    return TEST_ERROR;
		}
		port = atoi(cur_opt);
		break;
	    case 3:
		if (cur_opt == NULL) {
		    hc_test_log(LOG_ERROR_LEVEL,"Test not properly configured! Can not specify a NULL chunk.\n");
		    return TEST_ERROR;
		}
		chunk_size = atoi(cur_opt);
		break;
	    case 4:
		if (cur_opt == NULL) {
		    hc_test_log(LOG_ERROR_LEVEL,"Test not properly configured! Can not specify a NULL window.\n");
		    return TEST_ERROR;
		}
		window_size = atoi(cur_opt);
		break;
	    case 5:
		if (cur_opt == NULL) {
		    hc_test_log(LOG_ERROR_LEVEL,"Test not properly configured! Can not specify a NULL expected result.\n");
		    return TEST_ERROR;
		}
		expected_result = atoi(cur_opt);
		break;
	}
    }

    if (strcmp(handle_ptr_mode,"VALID") == 0) {
	handle_ptr = (hcoa_store_handle_t **)malloc(sizeof(hcoa_store_handle_t *));
    }
    hc_standalone_init();
    hc_test_log(LOG_DEBUG_LEVEL, "Connection initialized.\n");

    if ( (actual_result = hcoa_store_object_create(handle_ptr, host, port, chunk_size, window_size )) == expected_result) {
	hc_cleanup();
	return TEST_PASS;
    } else {
	hc_test_log(LOG_INFO_LEVEL, "Value returned, %s, did not match expected return code, %s\n",
	    hc_decode_hcerr(actual_result), hc_decode_hcerr(expected_result));
	hc_cleanup();
	return TEST_FAIL;
    }

}

int hc_store_multiple_files(int * argc, char ** argv, char *note) {

    char *test_vip =hash_get_value(CONTEXT_KEY_DATA_VIP,context_hash);
    int test_vip_port =atoi(hash_get_value(CONTEXT_KEY_DATA_VIP_PORT,context_hash));
    int first_time = TRUE;
    hcoa_store_handle_t *handle = NULL;
    int reuse_handle = TRUE;
    int err;
    hc_long_t size;
    int iters;

    if (test_vip == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "No data VIP has been provided.\n");
	return TEST_ERROR;
    }

    if (*argc < 2) {
	hc_test_log(LOG_ERROR_LEVEL, "you must pass in the number of store operations you want to execute and the max size of each file.\n");
	return TEST_ERROR;
    }
    iters = atoi(argv[0]);
    if (strcmp(argv[1], "-1") == 0) { // A -1 value means random size
	size = -1;
    } 
    else {
	size = translate_size(argv[1]); // A -1 value means size is random
	if (size == -1) {
	    hc_test_log(LOG_ERROR_LEVEL, "Invalid file size %s.\n", argv[1]);
	    return TEST_ERROR;
	}
    }

    if (*argc <= 3) {
	reuse_handle = atoi(argv[2]);
    }

    hc_test_log(LOG_DEBUG_LEVEL, "about to store %d files to %s:%d.\n",iters, test_vip,test_vip_port);

    if ((err = hc_standalone_init()) != HCERR_OK) {
	hc_test_log(LOG_ERROR_LEVEL, "hc_standalone_init failed! Return code was %s\n",hc_decode_hcerr(err));
	return TEST_ERROR;
    } else {
	hc_test_log(LOG_DEBUG_LEVEL, "hc_standalone_init succeeded!\n");
    }
    for (int i = 0; i < iters;i++) {
	FILE *input_file;
	char file_name[1024];
	hc_long_t sizegend;
	int in_file_fd = -1;

	if (first_time == TRUE || reuse_handle != TRUE) {
	    if (handle != NULL)
		free (handle);
	    if ((err = hcoa_store_object_create(&handle, test_vip, test_vip_port,0,0)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, "Unable to create store handle! Return code was %s\n",hc_decode_hcerr(err));
		return err;
	    } else {
		hc_test_log(LOG_DEBUG_LEVEL, "hcoa_store_object_create succeeded!\n");
	    }
	    first_time = FALSE;
	}
	if (size == -1) {
	    size = rand();
	}
	if ( ( sizegend=file_generator(size, rand(),file_name, input_file) ) != size) {
	    hc_test_log(LOG_ERROR_LEVEL, "the size of the file generated, %ld, does not match the size requested  %ld.\n", (long)sizegend,(long)size);
	    return TEST_ERROR;
	}
	if((in_file_fd = open(file_name, O_RDONLY | O_BINARY)) == -1) {
	    hc_test_log(LOG_ERROR_LEVEL, "Failed to open data file '%s' for store: %d\n", file_name, in_file_fd);
	    return TEST_ERROR;
	}
	err = test_store (&read_from_file_data_source, (void *)in_file_fd, handle );
	close(in_file_fd);
	remove(file_name);

    }
    hc_cleanup(); 

    if (err != HCERR_OK) {
	hc_test_log(LOG_INFO_LEVEL, "Store operation returned %s.\n", hc_decode_hcerr(err));
	return TEST_FAIL;
    }
    return TEST_PASS;
}

