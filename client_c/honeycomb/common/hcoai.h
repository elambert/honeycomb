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



#ifndef __HCOAI__
#define __HCOAI__

#include "hc.h"
#include "platform.h"
#include "multicell.h"

#ifdef __cplusplus
#extern "C" {
#endif 

#define DEFAULT_PORT 8080

#define WINDOWING 0 /* doesn't work with CURL - needs read vs. write io crank */

#define HC_COMPLETED_CODE_NOT_COMPLETED 0

/* Masks for status bit array */

#define HC_CONFIG_SERVICING_RETRIEVE 1
#define HC_CONFIG_SERVICING_STORE 2

/* HC protocol URL constants-- these correspond to servlets on the honeycomb server */
#define RETRIEVE_URL "retrieve"
#define STORE_URL "store"
#define QUERY_URL "query"
#define DELETE_URL "delete"
#define POWER_OF_TWO_URL "power-of-two"

#define SET_RETENTION_URL "set-retention"
#define SET_RETENTION_RELATIVE_URL "set-retention-relative"
#define GET_RETENTION_URL "get-retention"
#define ADD_HOLD_URL "add-hold"
#define REMOVE_HOLD_URL "remove-hold"
#define GET_DATE_URL "get-date"
#define GET_HOLDS_URL ""
#define GET_HELD_OBJS_URL ""

#define DATE_PARAMETER "date"
#define HOLD_TAG_PARAMETER "hold-tag"
#define RETENTION_LENGTH_PARAMETER "retention-length"

#define INFINTE_RETENTION -1

#define ID_PARAMETER "id"
#define LENGTH_PARAMETER "length"


#define RANGE_HEADER "range"
#define RANGE_SEPARATOR "-"
#define CHUNKSIZE_HEADER "commit-chunksize-bytes"
#define UNKNOWN_SIZE -1
#define SERVER_SUCCESS 200
#define SERVER_ERR_BAD_REQUEST 400
#define SERVER_ERR_NOT_FOUND 404
#define SERVER_ERR_INTERNAL_ERROR 500



#define ERRSTR_MAX_LEN 32000
#define MAX_CACHE_ID_LEN 81

/* Handles for state of individual store/retrieve/delete operations */
static const hc_long_t hc_magic_number = 728537167520360124L;
static const hc_long_t hc_archive_magic_number = 0x7eedbac7bac7feeeL;
static const hc_long_t hc_session_magic_number = 0x7eedbac7bac7feedL;
static const hc_long_t hc_pstmt_magic_number = 0x7eedbaccbac7feedL;
static const hc_long_t HC_MAX_LONG_VALUE = 0x7fffffffffffffffL;

#define ANY_HANDLE 1
#define BASE_HANDLE 2
#define STORE_HANDLE 4
#define RETRIEVE_HANDLE 8
#define DELETE_HANDLE 16
#define STORE_METADATA_HANDLE 32
#define RETRIEVE_METADATA_HANDLE 64
#define RETRIEVE_SCHEMA_HANDLE 128
#define QUERY_HANDLE 256
#define COMPLIANCE_HANDLE 1024
#define CHECK_INDEXED_HANDLE 2048

typedef enum hc_operation_ {
  UNITIALIZED,
  ANY_OP,

  STORE_DATA,
  STORE_METADATA, 
  STORE_BOTH,

  RETRIEVE_DATA, 
  RANGE_RETRIEVE_DATA,
  RETRIEVE_METADATA,
  RETRIEVE_SCHEMA,

  SET_RETENTION,
  SET_RETENTION_RELATIVE,
  GET_RETENTION,
  ADD_HOLDL,
  REMOVE_HOLD,
  GET_DATE,

  QUERY,
  QUERY_PLUS,

  POWER_OF_TWO_OP
} hc_operation_t;


typedef enum hc_op_state_ {
  OP_UNDEF = 0,
  ANY_STATE,

  STORING_DATA,
  ADDING_METADATA, 
  STORING_METADATA, 

  READING_METADATA_RECORD,
  READING_SYSTEM_RECORD,

  /* query */
  QUERY_RETRIEVING_RESULTS,
  QUERY_SERVING_RESULTS,
  QUERY_SERVING_FINAL_RESULTS,
  QUERY_DONE_SERVING_RESULTS,
  QUERY_DONE_SERVING_CELL,
  QUERY_FINISHED
} hc_op_state_t;


typedef struct hcoa_base_handle_ {
  struct hcoa_base_handle_ *next;
  hc_long_t magic_number;
  hc_handle_type_t handle_type;
  hc_operation_t op_type;
  hc_op_state_t op_state;

  struct hc_int_archive_ *archive;	/* Forward reference */

  char url[MAX_HTTP_URL_LEN];
  char cache[MAX_CACHE_ID_LEN];
  hcoa_http_session_ptr_t session_ptr;
  char *rbuf;
  char *rend;
  hc_buffer_index_t rbuflen;
  char *wbuf;
  char *wend;
  hc_buffer_index_t wbuflen;
//%% hc_int_query_t *query;
  int wcompleted;
  int rcompleted;
  int response_code;
  int connect_errno;
  int platform_result;
  char errstr[ERRSTR_MAX_LEN];
  hc_oid link_oid;
  hc_string_index_t errstrlen;
}  hcoa_base_handle_t;

/* These macros count on any specialized handle storing */
/* the base handle in the first bytes of the struct. */
#define valid_handle(X) ((hcoa_base_handle_t *)X)->magic_number == hc_magic_number
#define valid_archive(X) ((hc_int_archive_t *)X)->magic_number == hc_archive_magic_number
#define valid_session(X) ((hc_int_session_t *)X)->magic_number == hc_session_magic_number
#define handle_type(X) ((hcoa_base_handle_t *)X)->handle_type
#define op_type(X) ((hcoa_base_handle_t *)X)->op_type
#define op_state(X) ((hcoa_base_handle_t *)X)->op_state
#define session_ptr(X) ((hcoa_base_handle_t *)X)->session_ptr

#define require_ok(X) { hcerr_t __Y = X; if (__Y != HCERR_OK) return __Y; }

hcerr_t hc_is_valid_handle(void *handle, hc_handle_type_t handle_type, 
                           hc_operation_t op, hc_op_state_t state);
hcerr_t assert_handle(void *handle, hc_handle_type_t handle_type, 
			    hc_operation_t op, hc_op_state_t state);

hcerr_t hc_is_valid_oid (hc_oid oid);
hcerr_t assert_oid (hc_oid oid);

#define validate_handle(handle, handle_type, op, state)  \
  if (hc_is_valid_handle(handle, handle_type, op, state) != HCERR_OK)       \
    return hc_is_valid_handle(handle, handle_type, op, state)


#define validate_archive(archive)  \
  if (archive == NULL) \
    return HCERR_NULL_ARCHIVE; \
  else if (!(valid_archive(archive))) \
    return HCERR_INVALID_ARCHIVE

#define validate_session(session)  \
  if (session == NULL) \
    return HCERR_NULL_SESSION; \
  else if (!(valid_session(session))) \
    return HCERR_INVALID_SESSION

#define validate_oid_ptr(oid) \
  if (oid == NULL) \
    return HCERR_INVALID_OID; \
  else if (hc_is_valid_oid(*oid) != HCERR_OK) \
    return hc_is_valid_oid(*oid)

#define validate_oid(oid) \
  if (hc_is_valid_oid(oid) != HCERR_OK) \
    return hc_is_valid_oid(oid)
      

typedef struct hc_int_archive_ {
  hcoa_http_sessionset_ptr_t hcoa_http_sessionset_ptr;
  int    get_multicell_config;
  xml_parser *parser;
  cell_t default_cell;
  silo_t *silo;		/* cf multicell.h */
  silo_t *silo_parse;
  hc_long_t magic_number;
} hc_int_archive_t;

#define	Archive_silo(arc) (((hc_int_archive_t *)(arc))->silo)

/* Assertions for internal code checking */

/* The DEBUG_HONEYCOMB feature controls internal consistency checks
   that are helpful in debugging the code.   We leave it turned on for
   the first few Honeycomb releases. 
*/
#define	DEBUG_HONEYCOMB
#ifdef	DEBUG_HONEYCOMB
#define	debug_assert assert
#else
#define	debug_assert(exp) ((void)0)
#endif

/* The DEBUG_QUEUES feature enables some rarely used queue-length
   checks in the handling of received metadata. It is normally left
   turned off. */
#ifdef	DEBUG_QUEUES
#define	queue_assert assert
#else
#define	queue_assert(exp) ((void)0)
#endif

typedef struct hcoa_store_handle_ {
  hcoa_base_handle_t base;
  int cellid;
  int failsafe;
  int system_record_complete;	/* TRUE if system record received */
  hc_long_t chunk_size_bytes;
  hc_long_t window_size_chunks;
  hc_long_t last_committed_chunk;  /* 1 indexed */
  hc_long_t last_sent_chunk;       /* 1 indexed */
  hc_long_t last_sent_byte;        /* 1 indexed */
  hc_commit_ack last_commit_ack;   /* Buffer for storing ack bytes */
  int last_commit_ack_len;	   /* nb of ack bytes rcvd */
  int all_chunks_committed;
  int write_completed;
  hc_system_record_t hc_system_record;
  xml_parser *parser;
  read_from_data_source store_data_source_reader;
  void *store_stream;
#define HC_SYSTEM_METADATA_BUFFER_SIZE 16384
  char readbuffer[HC_SYSTEM_METADATA_BUFFER_SIZE];
} hcoa_store_handle_t;

typedef struct hcoa_retrieve_handle_ {
  hcoa_base_handle_t base;

  /* Where to send the data downloaded from the server */
  write_to_data_destination read_data_writer;
  void *read_stream;

} hcoa_retrieve_handle_t;

typedef struct hcoa_range_retrieve_handle_ {
  hcoa_retrieve_handle_t retrieve_handle;

  hc_long_t firstbyte;
  hc_long_t lastbyte;
} hcoa_range_retrieve_handle_t;

typedef hcoa_retrieve_handle_t hcoa_delete_handle_t;
typedef hcoa_retrieve_handle_t hcoa_compliance_handle_t;

int hc_isnum(const char *s);

hcerr_t init_base(hcoa_base_handle_t *handle, hc_archive_t *archive, hc_handle_type_t handle_type, hc_operation_t op);

HONEYCOMB_EXTERN hcerr_t hcoa_internal_retrieve_object_create(hcoa_retrieve_handle_t *handle, 
							      hc_archive_t *archive, 
							      hc_oid oid,
							      write_to_data_destination data_writer, void *stream);
HONEYCOMB_EXTERN hcerr_t hcoa_internal_delete_object_create(hcoa_delete_handle_t *handle,
							    hc_archive_t *archive, 
							    hc_oid oid,
							    char *buf, int buflen);

HONEYCOMB_EXTERN long hcoa_retrieve_to_buffer_callback(void *context, char *buff, long nbytes);
HONEYCOMB_EXTERN long hcoa_retrieve_callback(void *context, char *buffer, long size);
HONEYCOMB_EXTERN hcerr_t hcoa_session_completion_check(void *handle, int *finishedp);

HONEYCOMB_EXTERN hcerr_t hcoa_select_all(hc_archive_t *session);

HONEYCOMB_EXTERN int hcoa_should_refresh_cells_info(hc_archive_t *archive);

hcerr_t hcoa_archive_update_create(hc_int_archive_t *pubarc);

hcerr_t hcoa_archive_update_read_complete(hcoa_base_handle_t *base, hc_int_archive_t *arc);

hcerr_t hcoa_archive_close_handle(void *handle);

/* Store helper function to do 1st half of init - init the handle */

hcerr_t hcoa_store_create_init_handle(hcoa_store_handle_t *handle, 
				      hc_archive_t *archive, 
				      read_from_data_source data_source_reader,
				      void *stream,
                                      int cellid,
				      hc_long_t chunk_size_bytes,	
				      hc_long_t window_size_chunks,
				      hc_oid link_oid);


/* store helper function to do second half init - set up HTTP POST stuff */

hcerr_t hcoa_store_create_init_http(hcoa_store_handle_t *handle);

long hcoa_store_upload_from_buffer(void *handle,
				   char *buffer, 
				   long size);

long hcoa_store_upload_callback(void *context, 
				char *buffer, 
				long size);

long hcoa_store_read_callback(void *context,
				 char *readbuf, 
				 long size);


hcerr_t hcoa_store_create(hcoa_store_handle_t *handle, 
			  hc_archive_t *archive, 
			  read_from_data_source store_data_source_reader,
			  void *stream,
			  hc_long_t chunk_size_bytes, 
			  hc_long_t window_size_chunks);

/* retrieve helper function to do second half init - set up HTTP get stuff */
hcerr_t hcoa_retrieve_create_init_http(hcoa_base_handle_t *base);
hcerr_t hcoa_retrieve_start_transmit(hcoa_base_handle_t *base);

/* Base function used by all retrieves-- including queries */
hcerr_t hcoa_retrieve_create(hcoa_retrieve_handle_t *handle,
			     hc_archive_t *archive, 
			     char *retrieve_url,
			     hc_oid link_oid,
			     write_to_data_destination data_writer, 
			     void *stream);

/* from multicell.c */
extern cell_t *get_cell(hc_int_archive_t *archive, cell_id_t id);
extern cell_t *get_store_cell(hc_int_archive_t *archive);
extern cell_t *lookup_cell(hc_int_archive_t *archive, int cellid);

#ifdef __cplusplus
}	/* End #extern "C" scope */
#endif

#endif	/* __HCOAI__ */
