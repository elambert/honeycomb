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



#ifndef __HC_INTERNALS__
#define __HC_INTERNALS__

#include <string.h>


#include "hcoa.h"
#include "hcoai.h"


#define TRUE 1
#define FALSE 0

#define HC_DEBUG_FLAGS_DEFAULT  0
#define	HC_DEBUG_ERR_IF		(hc_debug_flags & 1)
#define HC_PLATFORM_VERBOSE	(hc_debug_flags & 2)
#define	HC_DEBUG_IF		(hc_debug_flags & 4)
#define	HC_DEBUG_DETAIL_IF	(hc_debug_flags & 8)
#define HC_DEBUG_IO_DETAIL_IF	(hc_debug_flags & 16)
#define HC_DEBUG_MC_IF     	(hc_debug_flags & 32)

HONEYCOMB_EXTERN hc_long_t hc_debug_flags;
HONEYCOMB_EXTERN hc_debug_function_t hc_debug;

#define HC_LOG(arg) { hc_debug arg; }
#define HC_LOG_L(arg) { hc_debug arg;}
#define	HC_ERR_LOG(arg) if (HC_DEBUG_ERR_IF) {hc_debug arg; }
#define	HC_DEBUG(arg) if (HC_DEBUG_IF) { HC_LOG(arg); }
#define	HC_DEBUG_DETAIL(arg) if (HC_DEBUG_DETAIL_IF) { HC_LOG(arg); }
#define	HC_DEBUG_IO_DETAIL(arg) if (HC_DEBUG_IO_DETAIL_IF) { HC_LOG(arg); }
#define	HC_DEBUG_MC(arg) if (HC_DEBUG_MC_IF) { HC_LOG(arg); }



#define IS_HEX_DIGIT(c) ((c >= 'a' && c <= 'f') || \
                   (c >= 'A' && c <= 'F') || \
                   (c >= '0' && c <= '9'))

int hc_isnum(const char *s);

hc_long_t hc_atol(char *s);

/* Number of seconds we will allow a lowspeed connection to persist before
   curl blows it up. */
HONEYCOMB_EXTERN long hc_low_speed_time;

/* The following flag is meaningful only on Windows */
#ifndef	O_BINARY
#define	O_BINARY 0
#endif

/* The following flag may not be meaningful on all platforms */
#ifndef	O_LARGEFILE
#define	O_LARGEFILE 0
#endif



/* Offset of the cellId in the hex representation of the OID. */
#define CELL_ID_OFFSET 4
/* Length in bytes of the cellId for the hex representation of the OID. */
#define CELL_ID_LEN 2

#define CELL_ID_RADIX 16


/* Macros to fill/check the validity of an link_oid */

#define HC_INVALID_OID (const char *) "invalid_oid"

#define FILL_INVALID_OID(oid) \
  do {\
    (void) memset(oid, 0, OID_HEX_CHRSTR_LENGTH); \
    (void) strcpy((char *) oid, HC_INVALID_OID); \
  } while (0)

#define IS_VALID_OID(oid, res) \
  do {\
    if ((oid[strlen(HC_INVALID_OID)] == '\0') && \
        (strncmp((const char*) oid, HC_INVALID_OID, \
                 strlen(HC_INVALID_OID) + 1) == 0)) { \
      res = TRUE; \
    } else { \
      res = FALSE; \
    } \
  } while (0)

/* ALLOCATOR(result,size) */
#define ALLOCATOR(R, S) \
    do { \
      assert(allocator != NULL); \
      (R) = allocator(S); \
        if ((R) == NULL) { \
	  HC_ERR_LOG(("Failed to allocate memory! size=%ld",(long)(S))); \
	  return HCERR_OOM; \
        } \
    } while (0)

#define FREE_HANDLE(handle, handle_type, type_code)                       \
  assert_handle(handle, type_code, ANY_OP, ANY_STATE);        \
  memset((handle_type *) handle, '\0', sizeof(handle_type));    \
  deallocator ((handle_type *) handle)

/* String buffer support */

typedef struct buffer_cell_{
  char *data;
  struct buffer_cell_ *previous;
} buffer_cell_t;


typedef struct buffer_list_{
  buffer_cell_t *string_buffer;
  hc_string_index_t current_token_buffer_size;
  hc_string_index_t current_token_start;
  hc_string_index_t current_token_position;
}
buffer_list_t;

hcerr_t hcoa_retrieve_error(int32_t response_code, int32_t connect_errno, int32_t plaform_result);

hcerr_t copy_errstr(hcoa_base_handle_t *handle, char *buf, hc_string_index_t len);

long get_error(hcoa_base_handle_t *handle, char *buf, long buflen);

hcerr_t allocate_session_handle(hcoa_http_session_ptr_t *session_handle);

hcerr_t hc_copy_string(char *value, char **result);

hcerr_t hc_to_hex_string(int len, unsigned char *value, char *result);

hcerr_t hc_from_hex_string(char *value, int *retlen, unsigned char **result);


/*************/
/* Constants */
/*************/

/* System Record support */
/* XML emements */

#define SYSTEM_MD_TAG "systemMD"
#define VERSION_TAG "version"

/* Compare to constants in NameValueXML and ProtocolConstants */
#define HC_OA_ATTRIBUTE_TAG "attribute"
#define HC_OA_ATTRIBUTES_TAG "attributes"
#define HC_OA_ATTRIBUTE_NAME_TAG "name"
#define HC_OA_ATTRIBUTE_VALUE_TAG "value"
#define HC_OA_ATTRIBUTE_LENGTH_TAG "length"
#define HC_OA_QUERY_RESULTS_COOKIE_TAG "Cookie"
#define	HC_OA_QUERY_INTEGRITY_TIME_TAG "Query-Integrity-Time"


/* Header name */
#define HC_QUERY_BODY_CONTENT_HEADER "query-body-content"
#define HC_BODY_CONTENT_HEADER "body-content"
#define EXPECT_MULTICELL_CFG_HEADER "Expect-Multicell-Config: "
#define MULTICELL_CFG_VERSION "Honeycomb-Multicell-Config-Version"

/* query-body-content header values */
#define HC_QUERY_BODY_CONTENT_WHERE_CLAUSE "where-in-body"
#define HC_QUERY_BODY_CONTENT_COOKIE "cookie-in-body"
#define XML_IN_BODY "xml-in-body"

#define HC_REQUEST_STACK_TRACE_HEADER "send-stack-trace"

#define XML_HDR "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"

/* System record XML serialization tags */

#define HC_OA_OBJECT_ID_KEY "system.object_id"
#define HC_OA_OBJECT_LINK_KEY "system.object_link"
#define HC_OA_OBJECT_SIZE_KEY "system.object_size"
#define HC_OA_OBJECT_CTIME_KEY "system.object_ctime"
#define HC_OA_OBJECT_DIGEST_ALG_KEY "system.object_hash_alg"
#define HC_OA_OBJECT_DIGEST_KEY "system.object_hash"
#define HC_OA_OBJECT_QUERY_READY_KEY "system.query_ready"


/* debugging */
void transition_state(void *handle, hc_op_state_t state);

char *decode_state(hc_op_state_t state);
char *decode_handle_type(hc_handle_type_t ht);
char *decode_op(hc_operation_t op);
#ifdef	WIN32
HONEYCOMB_EXTERN int gettimeofday(struct timeval *tv, char *dummy);
#endif

#endif
