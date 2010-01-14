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




/* Cross-platform Honeycomb client platform abstraction library */
/* This implementation uses Curl */

/* XXX TODO Currently handling _no_ error codes !!!
 * XXX ALSO TODO convert session_ptr fn to a macro
 */

#include <stdlib.h>
#include <string.h>
#include <assert.h>

#define	COMPILING_HONEYCOMB
#include "curlplatform.h"
#include "hc.h"
#include "hcinternals.h"

static int inited = 0;

/* convert implementation-neutral session set ptr to platform-specific ptr */

hc_curl_http_sessionset_t * 
to_curl_sessionset_ptr(hcoa_http_sessionset_ptr_t generic_ptr) {
  return (hc_curl_http_sessionset_t*) generic_ptr;
}

/* convert implementation-neutral session ptr to platform-specific ptr */
hc_curl_http_session_t * 
to_curl_session_ptr(hcoa_http_session_ptr_t generic_ptr) {
  return (hc_curl_http_session_t*) generic_ptr;
}

/* Called to read more data for writing over the network */
size_t uploadfn(void *ptr, size_t size, size_t nmemb, void *stream) {
  hc_curl_http_session_t *session_ptr = 
    to_curl_session_ptr((hcoa_http_session_ptr_t)stream);
  long bytes_written;

  bytes_written = session_ptr->upload_callback(session_ptr->write_context, ptr, size*nmemb);

  /* The internal convention to cancel a transfer differs 
     from the CURL convention. */
  if (bytes_written < 0)
    bytes_written = CURL_READFUNC_ABORT;

  return bytes_written;
}

/* Called to write more data that has been read off the network */
static size_t downloadfn(void *ptr, size_t size, size_t nmemb, void *stream) {
  hc_curl_http_session_t *session_ptr = 
    to_curl_session_ptr((hcoa_http_session_ptr_t)stream);
  long  bytes_read;
  bytes_read = session_ptr->download_callback(session_ptr->read_context, ptr, size*nmemb);
  return bytes_read;
}

/* Called when there is another header to read off the network */
static size_t headerfn(void *ptr, size_t size, size_t nmemb, void *stream) {
  hc_curl_http_session_t *session_ptr = 
    to_curl_session_ptr((hcoa_http_session_ptr_t)stream);
  size_t bytes_read;
  bytes_read = session_ptr->header_callback(session_ptr->header_context, ptr, size*nmemb);
  return bytes_read;
}

hcerr_t platform_global_init() {
  if (inited) {
    return HCERR_ALREADY_INITED;
  }
  if (curl_global_init(CURL_GLOBAL_ALL)) {
    return HCERR_INIT_FAILED;
  }
  inited = 1;  
  return HCERR_OK;
}

hcerr_t platform_session_init(hcoa_http_sessionset_ptr_t *sessionset_handle) {
  hc_curl_http_sessionset_t *sessionset_ptr = NULL;

  ALLOCATOR(*sessionset_handle, sizeof(hc_curl_http_sessionset_t));
  memset((char *)(*sessionset_handle),0,sizeof(hc_curl_http_sessionset_t));
  
  sessionset_ptr = 
    to_curl_sessionset_ptr(*sessionset_handle);
  
  if ((sessionset_ptr->curlm = curl_multi_init()) == NULL) {
    deallocator(*sessionset_handle);
    *sessionset_handle = NULL;
    return HCERR_INIT_FAILED;
  }
  sessionset_ptr->running = 0;
  sessionset_ptr->active_handles = NULL;
  return HCERR_OK;
}

hcerr_t platform_session_cleanup(hcoa_http_sessionset_ptr_t sessionset_ptr) {
  hc_curl_http_sessionset_t *curl_sessionset_ptr = NULL;
  hc_curl_http_session_t *cur;

  curl_sessionset_ptr = 
    to_curl_sessionset_ptr(sessionset_ptr);

  while ((cur = curl_sessionset_ptr->active_handles) != NULL) {
    close_session(cur);
  }

  curl_multi_cleanup(curl_sessionset_ptr->curlm);

  deallocator(curl_sessionset_ptr);

  return HCERR_OK;

}

hcerr_t platform_global_cleanup() {
  if(!inited) {
    return HCERR_NOT_INITED;
  }
  curl_global_cleanup();
  inited = 0;

  return HCERR_OK;
}


hcerr_t allocate_session_handle(hcoa_http_session_ptr_t *session_handle) {
  ALLOCATOR(*session_handle, sizeof(hc_curl_http_session_t));
  memset((char *)(*session_handle),0,sizeof(hc_curl_http_session_t));
  return HCERR_OK;
}

/* our debug callback for printing out what curl sees */


int platform_debug_callback (CURL *curl, curl_infotype type, char *buf, size_t nbytes, void *context) {

  /* We might want to have different hc_debug_flags values 
     for some of the following fields. */
  if (type == CURLINFO_TEXT) {
    HC_LOG_L(("CURL: %.*s",(int)nbytes,buf));
  } else if (type == CURLINFO_HEADER_IN) {
    HC_LOG_L(("< %.*s",(int)nbytes,buf));
  } else if (type == CURLINFO_HEADER_OUT) {
    HC_LOG_L(("> %.*s",(int)nbytes,buf));
  } else if (type == CURLINFO_DATA_IN) {
    //HC_DEBUG_IO_DETAIL(("%.*s",(int)nbytes,buf));
  } else if (type == CURLINFO_DATA_OUT) {
    //HC_DEBUG_IO_DETAIL(("%.*s",(int)nbytes,buf));
  } else if (type == CURLINFO_SSL_DATA_IN) {
  } else if (type == CURLINFO_SSL_DATA_OUT) {
  }

  return 0;
}

hcerr_t create_session(hc_curl_http_sessionset_t *curl_sessionset_ptr,
		       hcoa_http_session_ptr_t *session_handle, 
		       char *url,
		       write_to_data_destination header_callback,
		       void *header_context,
		       write_to_data_destination download_callback,
		       void *read_context,
		       read_from_data_source upload_callback,
		       void *write_context,
                       int allocate_handle) {
  hc_curl_http_session_t *curl_session_ptr = NULL;
  
  if (allocate_handle) {
    hcerr_t err = allocate_session_handle(session_handle);
    if (err != HCERR_OK) {
      HC_ERR_LOG(("create_session(url: %s): returning error status %d (%s)",
		url,err,hc_decode_hcerr(err)));
      return err;
    }
  }

  curl_session_ptr = 
    to_curl_session_ptr(*session_handle);
  
  curl_session_ptr->header = NULL;

  
  curl_session_ptr->sessionset_ptr = curl_sessionset_ptr;
  if((curl_session_ptr->curl = curl_easy_init()) == NULL) {
    deallocator(*session_handle);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }

  if(curl_multi_add_handle(curl_sessionset_ptr->curlm, 
			   curl_session_ptr->curl) != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(*session_handle); 
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  curl_sessionset_ptr->running++;
  
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_URL, url) != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(*session_handle);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED; 
  }

  /* Set connection timeout */
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_LOW_SPEED_LIMIT, 5L) != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(*session_handle);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED; 
  }
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_LOW_SPEED_TIME, hc_low_speed_time) != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(*session_handle);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED; 
  }
  
  if(HC_PLATFORM_VERBOSE) {
    /* Set our debug function.  
       We can also set a context for the debug_function, 
       but we don't need it so far */
    if(curl_easy_setopt(curl_session_ptr->curl, 
			CURLOPT_DEBUGFUNCTION, 
			platform_debug_callback) 
       != CURLE_OK) {
      curl_easy_cleanup(curl_session_ptr->curl);
      deallocator(*session_handle);
      *session_handle = NULL;
      return HCERR_SESSION_CREATE_FAILED;
    }

    /* The debug function has no effect until we enable VERBOSE */
    if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_VERBOSE) != CURLE_OK) {
      curl_easy_cleanup(curl_session_ptr->curl);
      deallocator(*session_handle);
      *session_handle = NULL;
      return HCERR_SESSION_CREATE_FAILED;
    }
  }
  
  curl_session_ptr->completed = 0;
  curl_session_ptr->next = curl_sessionset_ptr->active_handles;
  curl_sessionset_ptr->active_handles = curl_session_ptr;

  curl_session_ptr->header_callback = header_callback;
  curl_session_ptr->header_context = header_context;
  curl_session_ptr->download_callback = download_callback;
  curl_session_ptr->read_context = read_context;
  curl_session_ptr->upload_callback = upload_callback;
  curl_session_ptr->write_context = write_context;
 
  return HCERR_OK;
}


hcerr_t create_get_session(hcoa_http_sessionset_ptr_t sessionset_ptr,
			   hcoa_http_session_ptr_t *session_handle, 
			   char *url,
			   write_to_data_destination  header_callback,
			   void *header_context,
			   write_to_data_destination download_callback,
			   void *read_context,
			   read_from_data_source upload_callback,
			   void *write_context) {
  hcerr_t result = HCERR_OK;

  hc_curl_http_sessionset_t *curl_sessionset_ptr = 
    to_curl_sessionset_ptr(sessionset_ptr);
  hc_curl_http_session_t *curl_session_ptr = NULL;

  if((result = create_session(curl_sessionset_ptr, session_handle, url, 
			      header_callback, header_context,
			      download_callback, read_context,
			      upload_callback, write_context,
                              TRUE)) != HCERR_OK) {
    return result;
  }
  
  curl_session_ptr = 
    to_curl_session_ptr(*session_handle);
  
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_HTTPGET, (long)1) 
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }

  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_WRITEFUNCTION, downloadfn)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_WRITEDATA,
		      (void*)curl_session_ptr)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_HEADERFUNCTION, headerfn)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }

  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_WRITEHEADER, 
		      curl_session_ptr)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  HC_DEBUG_DETAIL(("create_get_session(session_ptr=0x%lx,url=%s)",(long)curl_session_ptr, url));
  return HCERR_OK;
}

hcerr_t create_post_session(hcoa_http_sessionset_ptr_t sessionset_ptr,
			    hcoa_http_session_ptr_t *session_handle, 
			    char *url,
			    write_to_data_destination  header_callback,
			    void *header_context,
			    write_to_data_destination download_callback,
			    void *read_context,
			    read_from_data_source upload_callback,
			    void *write_context,
                            int allocate_session_handle) {
  hcerr_t result = HCERR_OK;
  
  hc_curl_http_session_t *curl_session_ptr = NULL;
  
  hc_curl_http_sessionset_t *curl_sessionset_ptr = 
    to_curl_sessionset_ptr(sessionset_ptr);
  
  if((result = create_session(curl_sessionset_ptr, session_handle, url, 
			      header_callback, header_context,
			      download_callback, read_context,
			      upload_callback, write_context,
                              allocate_session_handle)) != HCERR_OK) {
    return result;
  }
  
  curl_session_ptr = 
    to_curl_session_ptr(*session_handle);
  
  
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_POST, (long)1)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_WRITEFUNCTION, downloadfn)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_WRITEDATA,
		      (void*)curl_session_ptr)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  
  if(curl_easy_setopt(curl_session_ptr->curl,CURLOPT_READFUNCTION, uploadfn)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_READDATA, 
		      (void*)curl_session_ptr)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  
  if(curl_easy_setopt(curl_session_ptr->curl,CURLOPT_HEADERFUNCTION, headerfn)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  if(curl_easy_setopt(curl_session_ptr->curl, CURLOPT_WRITEHEADER,
		      (void*)curl_session_ptr)
     != CURLE_OK) {
    curl_easy_cleanup(curl_session_ptr->curl);
    deallocator(curl_session_ptr);
    *session_handle = NULL;
    return HCERR_SESSION_CREATE_FAILED;
  }
  
  HC_DEBUG_DETAIL(("create_post_session(session_ptr=0x%lx,url=%s)",(long)curl_session_ptr, url));

  return HCERR_OK;
}

hcerr_t add_request_header(hcoa_http_session_ptr_t session_ptr, 
                           char *name, char *value) {
  hc_curl_http_session_t *curl_session_ptr = to_curl_session_ptr(session_ptr);
  char headerstr[MAX_HTTP_HEADER_LEN];
  CURLcode res = 0;
  
  if(strlen(name) + strlen(value) + 3 > MAX_HTTP_HEADER_LEN) {
    return HCERR_PLATFORM_HEADER_TOO_LONG; /* XXX TODO: real error */
  }
  
  sprintf(headerstr, "%s: %s", name, value);
  
  curl_session_ptr->header = 
    curl_slist_append(curl_session_ptr->header, 
		      headerstr);
  
  if(curl_session_ptr->header == NULL) {
    return HCERR_ADD_HEADER_FAILED;
  }
  
  if((res = curl_easy_setopt(curl_session_ptr->curl, CURLOPT_HTTPHEADER, 
			     (void*)curl_session_ptr->header,
			     headerstr)) != CURLE_OK) {
    return HCERR_ADD_HEADER_FAILED;
  }
  
  return HCERR_OK;
}

hcerr_t do_io(hcoa_http_sessionset_ptr_t sessionset_ptr,
	      int *sessions_just_finished) {
  hc_curl_http_sessionset_t *curl_sessionset_ptr = 
    to_curl_sessionset_ptr(sessionset_ptr);
  int running_handles = -1;
  CURLMcode result;
  
  /* turn the crank up to 3 times (this gives other uses of this thread a chance to run) */
  while (TRUE) { 
    result = curl_multi_perform(curl_sessionset_ptr->curlm, &running_handles);
    if (result != CURLM_CALL_MULTI_PERFORM) 
      break;
    HC_DEBUG_IO_DETAIL(("curl_multi_perform asked to be called again -- complying"));
  }

  /* see how many transfers finished and update our finished count */

  *sessions_just_finished = curl_sessionset_ptr->running - running_handles;

  curl_sessionset_ptr->running = running_handles;

  record_complete_sessions(sessionset_ptr);

  if (result != CURLE_OK && result != CURLM_CALL_MULTI_PERFORM) {
    HC_ERR_LOG(("do_io: returning HCERR_IO_ERR result=%d sessions_finished %d sessions remaining %d",
	      result, *sessions_just_finished, running_handles));
    return HCERR_IO_ERR;
  }
  /* 
  HC_DEBUG_DETAIL(("do_io: returning HCERR_OK result=%d sessions_finished %d sessions remaining %d",
		   result, *sessions_just_finished, running_handles));
  */
  return HCERR_OK;
}

hcerr_t sessions_in_progress(hcoa_http_sessionset_ptr_t sessionset_ptr,
			     int *num) {
  hc_curl_http_sessionset_t *curl_sessionset_ptr = 
    to_curl_sessionset_ptr(sessionset_ptr);
  if (curl_sessionset_ptr == NULL) {
    
    return HCERR_GET_RESPONSE_CODE_FAILED;
  }
  *num = curl_sessionset_ptr->running;
  return HCERR_OK;
}


hcerr_t get_session_platform_result(hcoa_http_session_ptr_t session_ptr,
					int32_t *code) {
  hc_curl_http_session_t *curl_session_ptr = 
    to_curl_session_ptr(session_ptr);

  *code = -1;

  if (curl_session_ptr == NULL) {
    return HCERR_GET_RESPONSE_CODE_FAILED;
  }

  *code = curl_session_ptr->curl_result;

  return HCERR_OK;
}

hcerr_t get_session_response_code(hcoa_http_session_ptr_t session_ptr,
				  int32_t *code) {
  hc_curl_http_session_t *curl_session_ptr = 
    to_curl_session_ptr(session_ptr);
  CURLcode res;

  if (curl_session_ptr == NULL) {
    return HCERR_GET_RESPONSE_CODE_FAILED;
  }
  if ((*code = curl_session_ptr->response_code) != 0) {
      return HCERR_OK;
  }
  res = curl_easy_getinfo(curl_session_ptr->curl, 
			  CURLINFO_RESPONSE_CODE,
			  code);
  if(res != CURLE_OK) {
    return HCERR_GET_RESPONSE_CODE_FAILED;
  }
  curl_session_ptr->response_code = *code;
  
  return HCERR_OK;
}


hcerr_t get_session_connect_errno(hcoa_http_session_ptr_t session_ptr,
				  int32_t *e) {
  CURLcode res;
  hc_curl_http_session_t *curl_session_ptr =
    to_curl_session_ptr(session_ptr);

  if (curl_session_ptr == NULL) {
    return HCERR_GET_RESPONSE_CODE_FAILED;
  }
  res = curl_easy_getinfo(curl_session_ptr->curl,
				   CURLINFO_OS_ERRNO,
				   e);
  if(res != CURLE_OK) {
    return HCERR_GET_RESPONSE_CODE_FAILED;
  }
  
  return HCERR_OK;
}
hcerr_t platform_interpret_error(int32_t response_code, int32_t connect_errno, int32_t platform_result) {
  hcerr_t res;

  switch(platform_result) {
  case CURLE_PARTIAL_FILE:
    res = HCERR_PARTIAL_FILE;
    break;
  case CURLE_ABORTED_BY_CALLBACK:
    res = HCERR_ABORTED_BY_CALLBACK;
    break;
  case CURLE_COULDNT_RESOLVE_PROXY:
  case CURLE_COULDNT_RESOLVE_HOST:
  case CURLE_COULDNT_CONNECT:
    res = HCERR_CONNECTION_FAILED;
    break;
  case CURLE_GOT_NOTHING:
    HC_LOG(("WARNING: CURLE_GOT_NOTHING - server unexpectedly returned no data."));
    res = HCERR_CLIENT_GAVE_UP;
    break;
  case CURLE_WRITE_ERROR:
    HC_LOG(("WARNING: CURLE_WRITE_ERROR - client did not request all data available from server."));
    res = HCERR_CLIENT_GAVE_UP;
    break;
  case CURLE_SEND_ERROR:
  case CURLE_RECV_ERROR:
  case CURLE_READ_ERROR:
  case CURLE_HTTP_RETURNED_ERROR:
  case CURLE_OPERATION_TIMEDOUT:
    res = HCERR_IO_ERR;
    break;
  case CURLE_OUT_OF_MEMORY:
    res = HCERR_OOM;
    break;
  default:
    res = HCERR_PLATFORM_GENERAL_ERROR;
    break;
  }
  return res;
}

/* session->completed = 1 if completed, else 0 */
hcerr_t session_completion_check(hcoa_http_session_ptr_t session_ptr,
				 int *completed) {

  hc_curl_http_session_t *curl_session_ptr = 
    to_curl_session_ptr(session_ptr);

  assert(curl_session_ptr != NULL);

  *completed = curl_session_ptr->completed;

  return HCERR_OK;
}

hcerr_t record_complete_sessions(hcoa_http_sessionset_ptr_t sessionset_ptr) {
  hc_curl_http_sessionset_t *set = to_curl_sessionset_ptr(sessionset_ptr);
  int msgs_in_queue;
  CURLMsg *msg;
  CURLMSG meaning;
  CURL *session;

  while((msg = curl_multi_info_read(set->curlm, &msgs_in_queue)) != NULL) {
    meaning = msg->msg;
    session = msg->easy_handle;
    if (meaning == CURLMSG_DONE) {
      hc_curl_http_session_t *cur = set->active_handles;
      while (cur != NULL) {
	if (cur->curl == session)
	  break;
	cur = cur->next;
      }
      if (cur != NULL) {
	cur->completed = 1;
	cur->curl_result = msg->data.result;
	HC_DEBUG(("session_completion_check: session complete with Curl Code %d",cur->curl_result));
      }	/* End if (cur != NULL) */
    }	/* End If (meaning == CURLMSG_DONE) */
  }	/* End While ((msg = curl_multi_info_read(...)) != NULL) */
  
  return HCERR_OK;
}
  
static void print_set(hcoa_http_session_ptr_t session_ptr) {
  hc_curl_http_session_t *curl_session_ptr = to_curl_session_ptr(session_ptr);
  hc_curl_http_session_t *cur;
  hc_curl_http_sessionset_t *set;

  set = curl_session_ptr->sessionset_ptr;
  cur = set->active_handles;
  printf("active_handles sess 0x%x set 0x%x\n", session_ptr, set);
  while (cur) {
    printf("  0x%x\n", cur);
    cur = cur->next;
  }
}


hcerr_t close_session(hcoa_http_session_ptr_t session_ptr) {
  hc_curl_http_session_t *curl_session_ptr = to_curl_session_ptr(session_ptr);
  hc_curl_http_session_t **prev;
  hc_curl_http_session_t *cur;
  hc_curl_http_sessionset_t *set;

  HC_DEBUG_DETAIL(("close_session(session_ptr=0x%lx)",(long)session_ptr));

  if (curl_session_ptr == NULL) {
    return HCERR_OK;
  }
  //print_set(session_ptr);
  set = curl_session_ptr->sessionset_ptr;
  curl_multi_remove_handle(curl_session_ptr->sessionset_ptr->curlm, 
			   curl_session_ptr->curl);
  curl_easy_cleanup(curl_session_ptr->curl);
  curl_slist_free_all(curl_session_ptr->header);
  
  /* Dequeue this session from the list of active sessions */
  prev = &(set->active_handles);
  cur = set->active_handles;
  while (cur && cur != curl_session_ptr) {
    prev = &(cur->next);
    cur = cur->next;
  }
  assert(cur != NULL); 
  if (cur != NULL) {
    *prev = cur->next;
    cur->next = NULL;
  }
  
  deallocator(curl_session_ptr);

  return HCERR_OK;
}


/* We will expose these so that end user will be able to do a select
 * and block on IO availability XXX TODO - perhaps we should have one
 * that take sessionset too (and for write)?
 */
hcerr_t get_multi_fdset(hcoa_http_sessionset_ptr_t sessionset_ptr, 
			fd_set *read_fd_set,
			fd_set *write_fd_set,
			fd_set *exc_fd_set,
			int *max_fdp) {
  hc_curl_http_sessionset_t *curl_sessionset_ptr = 
    to_curl_sessionset_ptr(sessionset_ptr);
  
  *max_fdp = 0;
  if (curl_multi_fdset(curl_sessionset_ptr->curlm,
		       read_fd_set,
		       write_fd_set,
		       exc_fd_set,
		       max_fdp) != CURLE_OK) {
    return HCERR_FAILED_GETTING_FDSET;
  }
  
  return HCERR_OK;
}
