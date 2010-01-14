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



#ifndef __HCTESTUTIL__
#define __HCTESTUTIL__

#include <stdarg.h>
#include "sha1.h"
#include "hcclient.h"
#include "hcoa.h"
#include "hcinternals.h"

#define MSG_BUFFER_SIZE 4096
#define HC_TIME_TEXT_SIZE 20
#define PATH_ENV_VAR "PATH"
#define PATH_SEP ":"
#define FILE_SEP "/"
#define HASH_SIZE 1024
#define STORE_OP_STORE_DATA 0
#define STORE_OP_STORE_NVDATA 1
#define STORE_OP_STORE_METADATA 2

#define KB 1024
#define KB_TEXT "KB"
#define MB 1048576L
#define MB_TEXT "MB"
#define GB 1073741824L
#define GB_TEXT "GB"

#define HCFILE_SIZE_TYPE_NEGATIVE "neg_file"
#define HCFILE_SIZE_TYPE_ONEBYTE "onebyte_file"
#define HCFILE_SIZE_TYPE_EMPTY "empty_file"
#define HCFILE_SIZE_TYPE_XXSMALL "xxsmall_file"
#define HCFILE_SIZE_TYPE_XSMALL "xsmall_file"
#define HCFILE_SIZE_TYPE_SMALL "small_file"
#define HCFILE_SIZE_TYPE_MED "med_file"
#define HCFILE_SIZE_TYPE_LARGE "large_file"
#define HCFILE_SIZE_TYPE_XLARGE "xlarge_file"
#define HCFILE_SIZE_TYPE_MAX "max_file"
#define HCFILE_SIZE_TYPE_STORE_BUFFER "buffer_size"
#define HCFILE_SIZE_TYPE_STORE_BUFFER_PLUS "buffer_size"
#define HCFILE_SIZE_TYPE_STORE_BUFFER_MINUS "buffer_size"
#define HCFILE_SIZE_TYPE_STORE_BUFFER_HALF "buffer_half"
#define HCFILE_SIZE_TYPE_RAND "rand_file"

#define HC_STRING_METADATA "string_md"
#define HC_LONG_METADATA "long_md"
#define HC_DOUBLE_METADATA "double_md"

#define HC_MAX_STR_LENGTH_CLUSTER 512
#define HC_MAX_STR_LENGTH_EMULATOR 256


#define CHECK_MEM(x) \
  do { \
    if ((x) == NULL) {                          \
      fprintf(stderr, "no more memory... abort");       \
      exit(1);                                          \
    } \
  } while (0)



typedef struct hc_str_nvpair_ {
    char *name;
    char *value;
} hc_str_nvpair_t;

//typedef struct hc_llist_elem_ {
typedef struct hc_llist_node_ {
    void *entry;
    struct hc_llist_node_ *next;
    struct hc_llist_node_ *prev;
} hc_llist_node_t;

typedef struct hc_llist_ {
    hc_llist_node_t *head;
    hc_llist_node_t *tail;
    int list_size;
} hc_llist_t;

typedef struct hc_hashlist_ {
    int list_size;
	int number_of_entries;
    hc_llist_t **linked_list_array;
} hc_hashlist_t;

typedef struct hc_random_file_ {
    hc_long_t file_size;
    unsigned char digest[20];
    int seed;
    int sha1_init;
    sha1_context *sha_context;
    hc_long_t bytes_read;
    hc_long_t sha_offset;
    hc_long_t sha_length;
} hc_random_file_t;


typedef struct hc_schema_entry_ {
    hc_type_t type; // type of field
    char* name; // name of field
    int size; // for char, string and binary fields the size
    struct hc_schema_entry_* next;
} hc_schema_entry_t;

typedef long (*qa_read_from_data_source) (void* stream, char* buff, long n);
extern void log_session_error_info(hc_session_t *session, hcerr_t err);
extern void time_to_text(time_t, char *);
extern int on_path(char *bin);
extern int ends_with(char *, char *);
extern int test_store(qa_read_from_data_source, void *, hcoa_store_handle_t *, hc_system_record_t *);
extern hc_long_t file_generator(hc_long_t, int, char *, FILE *);
extern hc_hashlist_t *create_hashlist(int);
extern int hashlist_contains_key (char *lkey, hc_hashlist_t *hashlist);
extern unsigned hash_string (char *, hc_hashlist_t *);
extern hc_str_nvpair_t *hash_get_nv_pair(char *, hc_hashlist_t *);
extern char *hash_get_value(char *l, hc_hashlist_t *);
extern hc_str_nvpair_t *hash_put(char *, char *, hc_hashlist_t *);
extern void free_hashlist (hc_hashlist_t *);
extern void free_linkedlist (hc_llist_t *,void (*free_fn) (void *));
extern void linkedlist_add_nodes (hc_llist_t *, size_t, ...);
extern void free_nv_pair (void *);
extern char *hc_itoa(long);
extern hc_long_t translate_size(char * );
extern long qa_read_from_file_data_source (void* , char* , long);
extern long read_from_random_data_generator (void * random_file, char *buf, long len);
extern long generate_random_data(char * , long, hc_long_t );  
extern hcerr_t do_store_from_generator(hc_archive_t *archive,hc_random_file_t *, int , char * , size_t , void *);
extern hc_llist_t * hc_create_linkedlist(void);
extern hc_long_t get_file_size (char * );
extern hc_random_file_t * create_random_file(hc_long_t);
extern float rand_multiplier(void);
extern void init_random_file(hc_random_file_t *);
extern void print_random_file(hc_random_file_t * );
extern void print_sys_rec(hc_system_record_t *);
extern int store_tmp_data_file(char *, int, hc_long_t, hc_system_record_t *);
extern int compare_rfile_to_sys_rec(hc_random_file_t *, hc_system_record_t *);
extern long retrieve_data_to_rfile(void *, char *,long);
extern int compare_rfiles(hc_random_file_t *, hc_random_file_t *);
extern int file_to_r_file(char *, hc_random_file_t *);
extern hcerr_t do_retrieve_to_r_file(hc_archive_t *,void *);
extern void get_range_overlap(hc_long_t, hc_long_t, hc_long_t, hc_long_t, hc_long_t *, hc_long_t *);
extern void free_r_file(hc_random_file_t *);
extern void digest_to_string(unsigned char digest[20], char *);
extern hc_llist_node_t * create_llist_node (void *);
extern hc_long_t write_random_data_to_file(hc_random_file_t *, char *);
extern void linkedlist_remove_elem(hc_llist_t *, hc_llist_node_t *);
extern void md_array_to_hashlist(char **, char **, long, hc_hashlist_t *);
extern void link_list_to_string(hc_llist_t *, char **);
extern int store_file_with_md(char *, int, hc_long_t, hc_system_record_t *);

extern char* create_uniform_string(char val, int size);
extern char* create_random_uniform_string(int size);
extern char** init_str_values(hc_schema_t *schema, char **names, 
				char** init_str_vals, 
				int default_length, 
				int uniform_length, 
				int max_size);
extern char** init_long_values(int size, hc_long_t** long_values);
extern char** init_double_values(int size, hc_double_t** double_values);
extern char** init_date_values(int size, struct tm **date_values);
extern char** init_time_values(int size, time_t **time_values);
extern char** init_timestamp_values(int size, struct timespec **ts_values);
extern char** init_binary_values(hc_schema_t *schema, char **names,
                                 int size, unsigned char **bin_values,
                                 int max_len);
extern char* get_query_string(char** names, char** values, int nb_md, hc_type_t type); 
extern int check_results_query(hc_oid* stored_oids, hc_oid* query_oids, int nb_stored_oids, int nb_query_oids);
extern int check_results_query_result_set(hc_oid* stored_oids, int nb_stored_oids, hc_query_result_set_t *qrs);
extern int check_resultset_qry (hc_oid *stored_oids, hc_hashlist_t *qrs_oids, long nb_stored_oids);
extern char** get_random_entry_from_schema(hc_schema_t* schema , hc_type_t type, int nb_md, char *ns);
extern int get_random_ns_entries_from_schema(hc_schema_t* schema,
        char* ns, hc_schema_entry_t** md_list, int max_fields, int queryable);
extern void free_metadata_fields_list(hc_schema_entry_t* md_list);
extern hcerr_t test_store_data(char *host, int port, char *filename, hc_oid *oid, int32_t *response_code, char* errstr, int errstr_len);
extern void fprintres(hcerr_t res);
extern void free_array(char** array, int size);
extern int treat_as_warning(hc_session_t * session, hcerr_t err);

extern char *hc_tmpnam();

#endif
