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



/* Honeycomb Name/Value Object Archive Client Library
 *
 *
 */

#ifndef __HCNVOAI__
#define __HCNVOAI__

#include "hcnvoa.h"
#include "hcoai.h"
#include "multicell.h"

#ifdef __cplusplus
#extern "C" {
#endif 


typedef struct hc_int_schema_ {
  hc_long_t	count;		/* number of items in the schema */
  hc_string_t	*names;		/* dyn alloc array of names */ 
  hc_type_t	*types;		/* dyn alloc array of types */
  int           *lengths;       // dyn alloc array of lengths
} hc_int_schema_t;

typedef	struct hc_int_nvr_ {
  hc_session_t	*session;	/* the active session for this record */
  hc_long_t	nslots;		/* number of slots in the arrays */
  hc_long_t	count;		/* count of active items  */
  hc_string_t	*names;		/* Parallel tables of names/values */
  hc_value_t	*values;
} hc_int_nvr_t;


#define	SESSION_ERRSTR_LEN 4096
typedef struct hc_int_session_ {
  int64_t	magic_number;	/* Canonical value to identify valid struct */
  hc_schema_t	*schema;	/* Schema from this host/port */
  int32_t	response_code;	/* HTTP Response code of last op */
  char		*errstr;	/* Error string from last op */
  int		errstr_len;	/* Length of stored error string */
  int		connect_errno;	/* os_errno from the connect */
  int		platform_result;/* platform (CURL) error */
  hc_archive_t	*archive;	/* Internal Opaque Stuff (hc_int_archive_t) */
} hc_int_session_t;
#define	Session_archive(sp) (((hc_int_session_t *)(sp))->archive)
#define	Session_response_code(sp) (((hc_int_session_t *)(sp))->response_code)
#define	Session_errstr(sp) (((hc_int_session_t *)(sp))->errstr)
#define	Session_schema(sp) (((hc_int_session_t *)(sp))->schema)

typedef struct hc_query_binding_ {
  struct hc_query_binding_ *next;
  int                      index;
  hc_value_t               value;
} hc_query_binding_t;

typedef struct hc_int_pstmt_ {
  int64_t	     magic_number; // Canonical value to identify valid struct
  hc_string_t        query;
  hc_query_binding_t *bindings;
  hc_int_session_t   *session;
} hc_int_pstmt_t;

/* Make these tests more sophisticated */
#define validate_schema(X) if (X == NULL) return HCERR_INVALID_SCHEMA
#define validate_query_result_set(X) if (X == NULL) return HCERR_INVALID_RESULT_SET
#define validate_query_plus_result_set(X) if (X == NULL) return HCERR_INVALID_RESULT_SET
#define validate_nvr(X) if (X == NULL) return HCERR_INVALID_NVR

/* --> make this a union? */
typedef struct hc_name_value_cell_{
  struct hc_name_value_cell_ *next;
  char *name;
  void *value;
  int length;
  int  v1_1;
} hc_name_value_cell_t;


/* Internal functions from the Synchronous C API */
HONEYCOMB_EXTERN int hc_should_refresh_cells_info(hc_session_t *session);

HONEYCOMB_EXTERN hcerr_t hc_get_value_as_string(hc_value_t value, char **result);
HONEYCOMB_EXTERN hcerr_t hc_nvr_expand(hc_int_nvr_t *nvr,hc_long_t new_nitems);
HONEYCOMB_EXTERN hcerr_t hc_session_update_schema_ez(hc_session_t *session);
HONEYCOMB_EXTERN hcerr_t hc_schema_get_index(hc_int_schema_t *hsp, char *name, hc_long_t *retindex);
HONEYCOMB_EXTERN hcerr_t hc_schema_validate_type(hc_schema_t *schema, char *name, hc_type_t type);
HONEYCOMB_EXTERN hcerr_t hc_schema_free (hc_schema_t *schema);
HONEYCOMB_EXTERN hcerr_t hc_select_all(hc_session_t *session);

HONEYCOMB_EXTERN hcerr_t hc_qrs_fetch_ez(hc_query_result_set_t *rset, hc_long_t *countp);

/* Low-level Encoding and Decoding of Values */
HONEYCOMB_EXTERN hcerr_t hc_encode_long(char *buffer, char tag, hc_long_t value);
HONEYCOMB_EXTERN hcerr_t hc_encode_double(char *buffer, hc_double_t value);
HONEYCOMB_EXTERN hcerr_t hc_encode_date(char *buffer, struct tm *tm);
HONEYCOMB_EXTERN hcerr_t hc_encode_time(char *buffer, time_t t);
HONEYCOMB_EXTERN hcerr_t hc_encode_timestamp(char *buffer, struct timespec *ts);
HONEYCOMB_EXTERN hcerr_t hc_encode_byte(char *buffer, hc_byte_t value);
HONEYCOMB_EXTERN hcerr_t decode64_to_string(char *src, char **dest);

HONEYCOMB_EXTERN hcerr_t hc_decode_value(char *buffer, hc_value_t *valuep,hc_type_t type);
HONEYCOMB_EXTERN hcerr_t hc_decode_value1_1(char *buffer, hc_value_t *valuep, hc_type_t type);
HONEYCOMB_EXTERN hcerr_t hc_decode_long(char *buffer, hc_long_t *valuep);
HONEYCOMB_EXTERN hcerr_t hc_decode_double(char *buffer, int hex, hc_double_t *valuep);
HONEYCOMB_EXTERN hcerr_t hc_decode_byte(char *buffer, hc_byte_t *valuep);

HONEYCOMB_EXTERN void encode64(int len, const unsigned char *in_buf, char datatype, char *out_buf);
HONEYCOMB_EXTERN int decode64(const char *in_buf, unsigned char *out_buf);

/* Internal functions from the Non-Synchronous C API */

/* external 'hidden functions' */
HONEYCOMB_EXTERN hcerr_t hc_store_both_cell_ez(hc_session_t *session,
                              int cellid,
                              read_from_data_source data_source_reader,
                              void *cookie,
                              hc_nvr_t *pubnvr,
                              hc_system_record_t *system_record);

/*
 * Store Metadata
 */

typedef struct hc_store_metadata_handle_ {
  /* The base handle must come first */
  hcoa_store_handle_t store_handle;
  hc_int_session_t *session;

  xml_writer *writer;
  char *wbuffer;		/* Where to write data from */
  int wbuflen;			/* size of write-data buffer */

  /* Fields for store-metadata */
  hc_oid oid;			/* The OID to link to */

  /* Fields for store-both */
  read_from_data_source md_data_source_reader;
  void *md_stream;
}
hc_store_metadata_handle_t;

HONEYCOMB_EXTERN hcerr_t hc_internal_store_metadata_create(hc_store_metadata_handle_t *handle, 
                                                           char *buffer,
                                                           hc_long_t buffer_length,
                                                           hc_int_session_t *session,
                                                           hc_long_t chunk_size_bytes, 
                                                           hc_long_t window_size_chunks, 
                                                           hc_oid oid);
HONEYCOMB_EXTERN hcerr_t hc_internal_add_metadata(hc_store_metadata_handle_t *handle, char *name, char *value);

HONEYCOMB_EXTERN hcerr_t hc_internal_store_metadata_close(hc_store_metadata_handle_t *handle, 
                                                          hc_system_record_t *sysrec);


/* Should match the "read_from_data_source" function prototype */
long hc_store_metadata_upload_callback(void *context, char *buffer, long size);

hcerr_t hc_store_metadata_worker(hc_store_metadata_handle_t *handle, 
                                 int *finishedp);

hcerr_t hc_store_metadata_start_transmit(hc_store_metadata_handle_t *metadata_handle);

/*
 * Store Both
 */

HONEYCOMB_EXTERN hcerr_t hc_internal_store_both_create(hc_store_metadata_handle_t *handle, 
                                                       char *buffer,
                                                       hc_long_t buffer_length,
						       read_from_data_source ext_data_source_reader,
						       void *ext_stream,
                                                       hc_int_session_t *session,
                                                       int cellid,
                                                       hc_long_t chunk_size_bytes, 
                                                       hc_long_t window_size_chunks);


/*
 * Retrieve Metadata
 */


#define HC_RETRIEVE_METADATA_BUFFER_SIZE 16384
//--> First member must be basehandle
typedef struct hc_retrieve_metadata_handle_ {
  /* The base handle must come first */
  hcoa_retrieve_handle_t retrieve_handle;
  hc_int_session_t *session;

  xml_parser *parser;
  char *value_tag;
  char *element_tag;

  hc_name_value_cell_t *top;
  hc_name_value_cell_t *last;
  hc_long_t count;

  // whether to interpret as 1.1 encoding
  int v1_1;

  /* Where to send the data downloaded from the server */
  write_to_data_destination read_md_data_writer;
  void *read_md_stream;

  /* opaque reference for query support */
  void *data;
} hc_retrieve_metadata_handle_t;



/*
 * Retrieve Schema
 */

typedef hc_retrieve_metadata_handle_t hc_retrieve_schema_handle_t;

#define MAX_SELECT_LENGTH 1024

typedef struct hc_query_handle_{
  /* The base handle must come first */
  hc_retrieve_metadata_handle_t retrieve_metadata_handle;

  /* --> can we make this a slot of constant size? */
  char *xml;
  char *honeycomb_cookie;
  char *old_cookie;
  char *request_path;
  hc_long_t query_integrity_time;	/* for query as a whole */

  /* current result cell. */
  cell_id_t mcell_ids[MAX_CELLS];
  int ncells;
  int cur_cell;
  int32_t results_per_fetch;

  /* Retain the query string so that we can fetch the next response set */
  char url[MAX_HTTP_URL_LEN];

  int response_count;
  int *record_attributes_count;

  /* The allocated size of the lists. */
  int attribute_count_list_size;

  /* For accessing results */
  int current_record;

  start_element_handler_t start_element_handler;
  end_element_handler_t end_element_handler;
} hc_query_handle_t;

hcerr_t hcoa_internal_store_close(hcoa_store_handle_t *handle,
				  int32_t *response_code, char *errstr, hc_string_index_t errstrlen,
				  hc_system_record_t *sysrec);

hcerr_t hcoa_internal_retrieve_close(hcoa_retrieve_handle_t *handle,
				     int32_t *response_code,
				     char *errstr, hc_string_index_t errstrlen);
long hc_retrieve_metadata_download_callback(void *context, char *buff, long nbytes);

hcerr_t hc_internal_retrieve_schema_create(hc_retrieve_schema_handle_t *handle, 
					   hc_int_session_t *session);


hcerr_t hc_internal_retrieve_schema (hc_retrieve_schema_handle_t *handle,
				     int *finished);

hcerr_t hc_internal_retrieve_schema_close(hc_retrieve_schema_handle_t *handle);

hcerr_t hc_internal_create_query(hc_query_handle_t *handle, 
				 hc_string_t query,
                                 hc_int_pstmt_t *pstmt,
				 hc_int_session_t *session,
				 int32_t results_per_fetch, 
				 hc_long_t chunk_size_bytes, 
				 hc_long_t window_size_chunks, char* cache_id);


hcerr_t hc_internal_retrieve_query(hc_query_handle_t *handle, 
                                   hc_long_t *results, 
                                   int *finished, 
                                   int *skip_select);

hcerr_t hc_internal_next_query_result (hc_query_handle_t *handle, 
							hc_oid *oids);

hcerr_t hc_internal_close_query (hc_query_handle_t *handle, int all);


hcerr_t hc_internal_create_query_plus(hc_query_handle_t *handle, 
				      hc_string_t query,
                                      hc_int_pstmt_t *pstmt,
				      hc_string_t fields[], int n_fields,
				      hc_int_session_t *session,
				      int32_t results_per_fetch, 
				      hc_long_t chunk_size_bytes, 
				      hc_long_t window_size_chunks);

hcerr_t hc_internal_next_query_plus_result(hc_query_handle_t *handle, 
                                            hc_oid *oid, hc_nvr_t **nvrp);

/* CHECK_INDEXED */
typedef struct hc_check_indexed_handle_ {
  /* The base handle must come first */
  hcoa_retrieve_handle_t retrieve_handle;
  hc_int_session_t *session;

#define HC_CHECK_INDEXED_BUFFER_SIZE 1024
  char readbuffer[HC_CHECK_INDEXED_BUFFER_SIZE];
} hc_check_indexed_handle_t;

hcerr_t hc_internal_check_indexed_create(hc_check_indexed_handle_t *handle,
					 hc_int_session_t *session, 
					 hc_oid oid);
hcerr_t hc_internal_check_indexed_close(hc_check_indexed_handle_t *handle, 
					int *resultp);

/* from multicell.c */
extern hcerr_t init_query_mcell(hc_query_handle_t *handle);
extern void cleanup_query_mcell(hc_query_handle_t *handle);
extern cell_t *get_cur_query_cell(hc_query_handle_t *handle);

hcerr_t hc_nvr_add_from_encoded_string(hc_nvr_t *pubnvr, char *encoded_name, char *encoded_value, int v1_1);

//Include a header that matches mktime.c in this directory
time_t mktime(struct tm *tm);
time_t mkgmtime(struct tm *tm);

/* HC protocol URL constants */

/* Servlet names, the first part of the request path */

#define RETRIEVE_METADATA_URL "retrieve-metadata"
#define STORE_METADATA_URL "store-metadata"
#define STORE_BOTH_URL "store-both"
#define GET_CACHE_CONFIGURATION_URL "get-configuration"
#define QUERY_PLUS_PATH "query-select"
#define QUERY_PATH "query"
#define CHECK_INDEXED_URL "check-indexed"

/* Request parameters, embedded in the query string */

#define METADATA_LENGTH_PARAMETER "metadata-length"
#define CACHE_PARAMETER "metadata-type"
#define NVOA_ID "extended"
#define QUERY_PARAMETER "where-clause"
#define COOKIE_PARAMETER "cookie"
#define SELECT_PARAMETER "select-clause"
#define MAX_RESULTS_PARAMETER "maxresults"
#define SYSTEM_CACHE "system"
#define QUERY_HOLD_PARAMETER "queryHold"

/* XML element names */
#define NVOA_TAG "relationalMD"
#define QUERY_PLUS_TAG "Query-Plus-Results"
#define QUERY_PLUS_RESULT_TAG "Result"
#define QUERY_RESULT_TAG "Result"
#define QUERY_ATTRIBUTE_TAG "attribute"
#define NVOA_NAME_TAG "name"
#define NVOA_VALUE_TAG "value"
#define OID_TAG "oid"

#define NVOA_SCHEMA_NAME_TAG "name"
#define NVOA_SCHEMA_VALUE_TAG "type"


#define LONG_TYPE "long"
#define DOUBLE_TYPE "double"
#define BYTE_TYPE "byte"
#define STRING_TYPE "string"
#define CHAR_TYPE "char"
#define BINARY_TYPE "binary"
#define DATE_TYPE "date"
#define TIME_TYPE "time"
#define TIMESTAMP_TYPE "timestamp"
#define	OBJECTID_TYPE "objectid"

#define LONG_TAG 'L'
#define DOUBLE_TAG 'D'
#define CHAR_TAG 'C'
#define TIMESTAMP_TAG 'T'
#define TIME_TAG 't'
#define DATE_TAG 'd'
#define STRING_TAG 'S' 
#define BINARY_TAG 'B' 
#define OBJECTID_TAG 'X'


#ifdef __cplusplus
}	/* End #extern "C" scope */
#endif

#endif	/* __HCNVOAI__ */
