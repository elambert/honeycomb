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



/** Honeycomb Client C API HTTP Support
 *   
 * Hide underlying socket package from CAPI
 * Provide building blocks for non-blocking IO
 *  
 * Honeycomb CAPI is the only client of these  
 * functions; they are not exposed to end users
 */

#ifndef __HC_PLATFORM__
#define __HC_PLATFORM__

#include <sys/types.h>
#include "hc.h"

#ifdef __cplusplus
#extern "C" {
#endif 

/* XXX TODO - Check these, or better yet, get them from the underlying impl. */
#define MAX_HTTP_HEADER_LEN 4096
#define MAX_HTTP_URL_LEN 4096
#define HTTP_PROTOCOL_TAG "http"


typedef void *hcoa_http_session_ptr_t;

typedef void *hcoa_http_sessionset_ptr_t;

hcerr_t platform_global_init(void);
hcerr_t platform_global_cleanup(void);

hcerr_t platform_session_init(hcoa_http_sessionset_ptr_t *sessionset_handle);
hcerr_t platform_session_cleanup(hcoa_http_sessionset_ptr_t sessionset_ptr);

HONEYCOMB_EXTERN hcerr_t platform_get_platform_name(char *name, int len);


hcerr_t add_request_header(hcoa_http_session_ptr_t session_ptr, char *name, 
                           char *value);

hcerr_t do_io(hcoa_http_sessionset_ptr_t sessionset_ptr,
	      int *sessions_just_finished);

hcerr_t sessions_in_progress(hcoa_http_sessionset_ptr_t sessionset_ptr,
			     int *num);

hcerr_t session_completion_check(hcoa_http_session_ptr_t session_ptr,
				 int *completed);

hcerr_t record_complete_sessions(hcoa_http_sessionset_ptr_t set);

hcerr_t get_session_platform_result(hcoa_http_session_ptr_t session_ptr,
				    int32_t *code);

hcerr_t get_session_response_code(hcoa_http_session_ptr_t session_ptr,
				  int32_t *code);

hcerr_t get_session_connect_errno(hcoa_http_session_ptr_t session_ptr,
				  int32_t *e);

hcerr_t platform_interpret_error(int32_t response_code, int32_t connect_errno, int32_t platform_result);

hcerr_t close_session(hcoa_http_session_ptr_t session_ptr);

hcerr_t create_get_session(hcoa_http_sessionset_ptr_t sessionset_ptr,
			   hcoa_http_session_ptr_t *session_handle, 
                           char *url,
			   read_from_data_source header_callback,
			   void *header_context,
			   write_to_data_destination download_callback,
			   void *read_context,
			   read_from_data_source upload_callback,
			   void *write_context);

hcerr_t create_post_session(hcoa_http_sessionset_ptr_t sessionset_ptr,
			    hcoa_http_session_ptr_t *session_handle, char *url,
			    write_to_data_destination  header_callback,
			    void *header_context,
			    write_to_data_destination download_callback,
			    void *read_context,
			    read_from_data_source upload_callback,
			    void *write_context,
                            int allocate_handle);

/* We will expose these so that end user will be 
 * able to do a select and block on IO availability
 */
hcerr_t get_multi_fdset(hcoa_http_sessionset_ptr_t sessionset_ptr, 
			fd_set *read_fd_set,
			fd_set *write_fd_set,
			fd_set *exc_fd_set,
			int *max_fdp);


allocator_t allocator;		/* malloc */
deallocator_t deallocator;	/* free */
reallocator_t reallocator;	/* realloc */



/* Writing XML */

typedef void *xml_writer;

typedef hcerr_t (*write_bytes_callback_t) (char *data, int len, int finished, void *stream);

HONEYCOMB_EXTERN hcerr_t start_document (char *document_tag, 
                                         write_bytes_callback_t backend_writer,
                                         void *stream,
                                         xml_writer **writer,
                                         allocator_t a, 
                                         deallocator_t d,
                                         int pretty_print);


/* Caller supplies buffer */
HONEYCOMB_EXTERN hcerr_t start_buffered_document (char *document_tag, 
                                                  char *buffer,
                                                  hc_long_t len,
                                                  xml_writer **writer,
                                                  allocator_t a, 
                                                  deallocator_t d,
                                                  int pp);

HONEYCOMB_EXTERN hcerr_t start_element (xml_writer *writer,
                                        char *element_name, 
                                        char **attribute_names, 
                                        char **attribute_values,
                                        int n_attributes);

HONEYCOMB_EXTERN hcerr_t end_element (xml_writer *writer,
                                      char *element_name);

HONEYCOMB_EXTERN hcerr_t end_document_and_close (xml_writer *writer, hc_long_t *n);




/* Reading XML */

typedef void *xml_parser;

/* Callback function to handle the startElement event */
/* Arguments: element_name, attribute_names, attribute_values, n_attributes */
typedef hcerr_t (*start_element_handler_t) (char*, void*, char**, char**, int);

/* Callback function to handle the endElement event */
typedef hcerr_t (*end_element_handler_t) (char*, void*);

HONEYCOMB_EXTERN hcerr_t xml_create (start_element_handler_t start_element_callback,
                                     end_element_handler_t end_element_callback, 
                                     void *data,
                                     xml_parser **parser,
                                     allocator_t a, deallocator_t d);

/* does this block on input? */
HONEYCOMB_EXTERN hcerr_t xml_parse (xml_parser *parser, char responsebuf[], 
                                    hc_long_t size, hc_long_t *read);


HONEYCOMB_EXTERN hcerr_t xml_cleanup (xml_parser *parser);


/*
  Typical Honeycomb request header:

  POST /store-both?metadata-type=extended&metadata-length=156 HTTP/1.1
  User-Agent: Jakarta Commons-HttpClient/2.0.2
  Host: localhost:8082
  Transfer-Encoding: chunked


  Typical Honeycomb response header:

  HTTP/1.1 200 OK
  Date: Mon, 20 Jun 2005 20:29:50 GMT
  Server: Jetty/4.2.19 (SunOS/5.10 x86 java/1.5.0_01)
  Honeycomb-Version: 1
  Honeycomb-Node: bumble 129.144.88.244
  Content-Type: text/xml
  Transfer-Encoding: chunked
*/

#ifdef __cplusplus
}	/* End #extern "C" scope */
#endif
#endif	/* __HC_PLATFORM__ */
