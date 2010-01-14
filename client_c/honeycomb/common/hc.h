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



#ifndef __HC__
#define __HC__

#include <sys/types.h>
#include "hchost.h"

#ifdef __cplusplus
extern "C"
{
#endif

   typedef size_t hcsize_t;
   typedef int64_t hc_buffer_index_t;
   typedef uint32_t hc_string_index_t;
   typedef uint32_t hc_handle_type_t;

/* Data types for storing and retrieving metadata from the
   repository. */
   typedef int64_t hc_long_t;
   typedef int64_t hc_date_t;
   typedef uint64_t hc_time_t;
   typedef int64_t hc_timestamp_t;
   typedef double hc_double_t;
   typedef char *hc_string_t;
   typedef short hc_byte_t;

   typedef struct hc_bytearray_ {
     int           len;
     unsigned char *bytes;
   } hc_bytearray_t;

#define OID_HEX_CHRSTR_LENGTH 60+1
#define DIGEST_ALGO_CHRSTR_LENGTH 10+1
#define DIGEST_HEX_CHRSTR_LENGTH 40+1
#define COMMIT_ACK_HEX_CHRSTR_LENGTH 1060	/* 64-bit long as a hex str */

   typedef char hc_oid[OID_HEX_CHRSTR_LENGTH];
   typedef char hc_digest_algo[DIGEST_ALGO_CHRSTR_LENGTH];
   typedef char hc_digest[DIGEST_HEX_CHRSTR_LENGTH];
   typedef char hc_commit_ack[COMMIT_ACK_HEX_CHRSTR_LENGTH];

   typedef enum hc_digest_algs
   {
      HC_SHA1
   } hc_digest_algs_t;


   typedef enum hc_err
   {
      HCERR_OK = 0,
      HCERR_NOT_INITED,
      HCERR_ALREADY_INITED,
      HCERR_INIT_FAILED,
      HCERR_OOM,
      HCERR_NOT_YET_IMPLEMENTED,
      HCERR_SESSION_CREATE_FAILED,
      HCERR_ADD_HEADER_FAILED,
      HCERR_IO_ERR,
      HCERR_FAILOVER_OCCURRED,
      HCERR_CAN_CALL_AGAIN,
      HCERR_GET_RESPONSE_CODE_FAILED,
      HCERR_CONNECTION_FAILED,
      HCERR_BAD_REQUEST,
      HCERR_NO_SUCH_OBJECT,
      HCERR_INTERNAL_SERVER_ERROR,
      HCERR_FAILED_GETTING_FDSET,
      HCERR_FAILED_CHECKING_FDSET,
      HCERR_MISSING_SELECT_CLAUSE,
      HCERR_URL_TOO_LONG,
      HCERR_COULD_NOT_OPEN_FILE,
      HCERR_FAILED_TO_WRITE_TO_FILE,
      HCERR_NULL_SESSION,
      HCERR_NULL_ARCHIVE,
      HCERR_INVALID_ARCHIVE,
      HCERR_INVALID_SESSION,
      HCERR_INVALID_OID,
      HCERR_NULL_HANDLE,
      HCERR_INVALID_HANDLE,
      HCERR_INVALID_SCHEMA,
      HCERR_INVALID_RESULT_SET,
      HCERR_INVALID_NVR,
      HCERR_WRONG_HANDLE_FOR_OPERATION,
      HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION,
      HCERR_READ_PAST_LAST_RESULT,

      HCERR_XML_PARSE_ERROR,
      HCERR_XML_MALFORMED_XML,
      HCERR_XML_EXPECTED_LT,
      HCERR_XML_INVALID_ELEMENT_TAG,
      HCERR_XML_MALFORMED_START_ELEMENT,
      HCERR_XML_MALFORMED_END_ELEMENT,
      HCERR_XML_BAD_ATTRIBUTE_NAME,
      HCERR_XML_BUFFER_OVERFLOW,
      HCERR_BUFFER_OVERFLOW,
      HCERR_OUT_OF_MEMORY,

      HCERR_NO_SUCH_TYPE,
      HCERR_ILLEGAL_VALUE_FOR_METADATA,
      HCERR_NO_SUCH_ATTRIBUTE,
      HCERR_NO_MORE_ATTRIBUTES,
      HCERR_FAILED_GETTING_SILO_DATA,

      HCERR_PLATFORM_NOT_INITED,
      HCERR_PLATFORM_ALREADY_INITED,
      HCERR_PLATFORM_INIT_FAILED,
      HCERR_PLATFORM_HEADER_TOO_LONG,
      HCERR_PLATFORM_TOO_LATE_FOR_HEADERS,
      HCERR_PLATFORM_NOT_ALLOWED_FOR_GET,

      HCERR_FAILED_TO_GET_SYSTEM_RECORD,
      HCERR_PARTIAL_FILE,
      HCERR_ABORTED_BY_CALLBACK,
      HCERR_PLATFORM_GENERAL_ERROR,
      HCERR_ILLEGAL_ARGUMENT,
      HCERR_CLIENT_GAVE_UP

      /* 
       *  NOTE:  IF YOU ARE ADDING A NEW ERROR CODE HERE, ALSO ADD IT
       *  TO hc_decode_hcerr() IN hcoa.c.
       */ 
   } hcerr_t;

   typedef struct hc_system_record_
   {
      hc_oid oid;
      hc_digest_algo digest_algo;
      hc_digest data_digest;
      hc_long_t size;
      hc_long_t creation_time;
      hc_long_t deleted_time;
      char shredMode;
      char is_indexed;	
   } hc_system_record_t;

#define RANGE_RETRIEVE_UNKNOWN_SIZE -1

/*   Data source template.  */
/*   Use a pointer to a function with this signature to upload data  */
/*   from a network or other source to Honeycomb using hcoa_store_ez. */
/*   The stream argument is the opaque (to honeycomb) structure supplied  */
/*   in hcoa_store_ez, and holds implementation-specific state. */
   HONEYCOMB_EXTERN typedef long (*read_from_data_source) (void *stream, char *buff, long n);

/*   Data destination template.  */
/*   Use a pointer to a function with this signature to download data  */
/*   to a network or other destination from Honeycomb using hc_retrieve_ez. */
/*   The stream argument is the opaque (to honeycomb) structure supplied  */
/*   in hc_retrieve_ez, and holds implementation-specific state. */
   HONEYCOMB_EXTERN typedef long (*write_to_data_destination) (void *stream, char *buff, long n);

   /* Headers for malloc/dealloc lookalikes so this library can be used
    * in embedded code.  */
   typedef void *(*allocator_t) (size_t size);	/* malloc */
   typedef void (*deallocator_t) (void *p);	/* free */
   typedef void *(*reallocator_t) (void *p, size_t size);	/* realloc */
  typedef void (*hc_debug_function_t) (const char *fmt,...);	/* printf */

/* typedefs that are hcoa-specific */
typedef void hc_archive_t;		/* Opaque type for the data store archive */

/* Library setup, teardown, and utilities */
HONEYCOMB_EXTERN hcerr_t hc_init(allocator_t, deallocator_t, reallocator_t);
HONEYCOMB_EXTERN void *hc_alloc(size_t size);
HONEYCOMB_EXTERN void hc_free(void *p);
HONEYCOMB_EXTERN void *hc_realloc(void *p, size_t size);
HONEYCOMB_EXTERN hcerr_t hc_cleanup(void);


HONEYCOMB_EXTERN void hc_printf(const char *fmt,...);

HONEYCOMB_EXTERN hcerr_t hc_set_debug_function(hc_debug_function_t fn);

HONEYCOMB_EXTERN hcerr_t hc_set_debug_flags(hc_long_t debug);

HONEYCOMB_EXTERN hcerr_t hcoa_get_platform_name(char *name, int len);

HONEYCOMB_EXTERN char *hc_decode_hcerr(hcerr_t res);


  /* Definitions for hc_session_set_parameter and
     hc_global_set_parameter.   The same parameter definitions are
     used for both operations, though not all parameters make sense in
     both cases.  */
#define	HCOPTTYPE_LONG		0
#define	HCOPTTYPE_HCLONG	10000
#define	HCOPTTYPE_OBJPOINTER	20000
#define	HCOPTTYPE_FUNCPOINTER	30000

#define HCOPT(name,type,number) HCOPT_ ## name = HCOPTTYPE_ ## type + number

typedef enum {

  /* Internal debugging flags */
  HCOPT(DEBUG_FLAGS, HCLONG, 1),

  /* printf-style function for the internal debugging output */
  HCOPT(DEBUG_FUNCTION, FUNCPOINTER, 2),

  /* number of seconds to keep going on a slow connection */
  HCOPT(LOW_SPEED_TIME, LONG, 3)
  
} hc_option;
#undef HCOPT

HONEYCOMB_EXTERN hcerr_t hc_set_global_parameter(hc_option option, ...);


#ifdef __cplusplus
}				/* End #extern "C" scope */
#endif

#endif				/* __HC__ */
