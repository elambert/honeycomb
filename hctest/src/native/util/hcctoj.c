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



#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "hc.h"
#include "hcoa.h"
#include "hcoaez.h"
#include "hcnvoaez.h"
#include <fcntl.h>
#include <time.h>
#define IO_BUFFER_SIZE 4096
#define ARRAY_CLASS "java/lang/reflect/Array"
#define INPUT_STREAM_CLASS "java/io/InputStream"
#define WRITABLE_BYTE_CHANNEL_CLASS "java/nio/channels/WritableByteChannel"
#define OBJECT_IDENTIFIER_CLASS "com/sun/honeycomb/client/ObjectIdentifier"
#define QA_SYSTEM_RECORD_CLASS "com/sun/honeycomb/client/QASystemRecord"
#define BYTE_ARRAY_CLASS "com/sun/honeycomb/common/ByteArrays"
#define BYTE_BUFFER_CLASS "java/nio/ByteBuffer"
#define HASHMAP_CLASS "java/util/HashMap"
#define OBJECT_OID_KEY "object_id"
#define OBJECT_LINK_ID_KEY "object_link"
#define RETRIEVE_BUFFER_SIZE 16384

// FIXME: object_id in the schema is now named system.object_id

static JNIEnv *global_env = NULL;
static jclass object_id_class;
static jclass qa_sys_rec_class;
static jclass array_class;
static jclass byte_buffer_class;
static jclass hashmap_class;
static jclass byte_array_class;
static jmethodID byte_array_to_byte_array_mid;
static jmethodID input_stream_read_mid;
static jmethodID object_id_ctor_mid;
static jmethodID object_id_hexctor_mid;
static jmethodID object_id_get_bytes_mid;
static jmethodID object_id_to_hex_mid;
static jmethodID sys_rec_set_oid_mid;
static jmethodID sys_rec_set_sda_mid;
static jmethodID sys_rec_set_size_mid;
static jmethodID sys_rec_set_ctime_mid;
static jmethodID sys_rec_set_httpCode_mid;
static jmethodID sys_rec_set_httpMsg_mid;
static jmethodID sys_rec_set_datadigest_mid;
static jmethodID sys_rec_set_digestalgo_mid;
static jmethodID jArray_get_arrayElem_mid;
static jmethodID jArray_get_arrayElem_mid;
static jmethodID jbyteBuffer_wrap_mid;
static jmethodID jwritable_byte_channel_write_mid;
static jmethodID jhashmap_put_mid;

jmethodID get_method_id (JNIEnv *env, const char *classname, const char * method_name, 
		         const char *method_sig, int is_static) {
    jclass _class = (*env)->FindClass(env,classname);
    jmethodID _mID = NULL;
    if (_class == NULL) {
	printf("Could not find class %s.\n", classname);
	return NULL;
    }
    if (is_static == TRUE) {
	_mID= (*env)->GetStaticMethodID(env,_class,method_name, method_sig); 
    } else {
	_mID = (*env)->GetMethodID(env, _class,method_name, method_sig);
    }
    if (_mID == NULL) {
	printf("Can't get a handle on %s.%s::%s.\n",classname, method_name,method_sig);
    }
    (*env)->DeleteLocalRef(env,_class);
    return _mID;
}


hcerr_t populate_c_arrays (JNIEnv *env, jobjectArray jname_array, jobject jvalue_array, 
		           char ** cname_array, char ** cvalues_array, hc_long_t size ) {

    int i;
    if (jArray_get_arrayElem_mid == NULL) {
	  printf("Can not populate name/value arrays as I could not get a handle on %s\n", ARRAY_CLASS);
	  return HCERR_BAD_REQUEST;
    }

    for (i = 0; i < size; i++) {
	  jobject cur_name_elem = (*env)->CallStaticObjectMethod(env,array_class,jArray_get_arrayElem_mid,jname_array,i);
	  const char *currName=(char*)(*env)->GetStringUTFChars(env,cur_name_elem, NULL);
	  char * c_currName = (char *) malloc (strlen(currName) + 1);
	  jobject cur_val_elem = (*env)->CallStaticObjectMethod(env,array_class,jArray_get_arrayElem_mid,jvalue_array,i);
	  const char *val_elem=(char*)(*env)->GetStringUTFChars(env,cur_val_elem, NULL);
	  char * c_val_elem = (char *) malloc (strlen(val_elem) + 1);

	  strcpy (c_currName, currName);
	  (*env)->ReleaseStringUTFChars(env,cur_name_elem, currName);
	  (*env)->DeleteLocalRef(env,cur_name_elem);
	  *cname_array = c_currName;
	  cname_array++;
	  
	  strcpy (c_val_elem, val_elem);
	  (*env)->ReleaseStringUTFChars(env,cur_val_elem, val_elem);
	  (*env)->DeleteLocalRef(env,cur_val_elem);
	  *cvalues_array = c_val_elem;
	  cvalues_array++;
    }
    return HCERR_OK;
}


void markUp_sys_rec(JNIEnv *env, jobject jsys_rec, hc_system_record_t *sys_rec, 
		    char *err_string, hc_long_t response_code) {

    jstring jhexOID = (*env)->NewStringUTF(env,sys_rec->oid);
    jobject oid = (*env)->NewObject(env, object_id_class, object_id_hexctor_mid,jhexOID);
    jstring jdataDigest = (*env)->NewStringUTF(env,sys_rec->data_digest);
    jstring jdataDigestAlgo = (*env)->NewStringUTF(env,sys_rec->digest_algo);
    

    if (jsys_rec == NULL) {
	printf("WARNING! Can not markup Java instance of System Record. Reference to sys record was null");
	return;
    }

    if (sys_rec_set_oid_mid == NULL) {
	printf("Can't find setObjectIdentifier method");
	return;
    }

    // set oid
    (*env)->CallVoidMethod(env, (jsys_rec),sys_rec_set_oid_mid,oid);
	printf("Setting oid. OID should be %s\n",sys_rec->oid);
    if ((*env)->ExceptionCheck(env)) {
	(*env)->ExceptionDescribe(env);
	printf("An exception happened while setting oid. OID should be %s\n",sys_rec->oid);
	return;
    }

    // set data digest
    printf("The data digest in the sys_rec is %s\n", sys_rec->data_digest);
    (*env)->CallVoidMethod(env,(jsys_rec),sys_rec_set_datadigest_mid,jdataDigest);
    if ((*env)->ExceptionCheck(env)) {
	printf("An exception happened while setting data digest. digest should be %s\n",sys_rec->data_digest);
	return;
    }

    // set data digest algo
    (*env)->CallVoidMethod(env, (jsys_rec),sys_rec_set_digestalgo_mid,jdataDigestAlgo);
    if ((*env)->ExceptionCheck(env)) {
	printf("An exception happened while setting data digest algorithm. Algorithm should be %s\n",sys_rec->digest_algo);
	return;
    }
	
    // set size
    (*env)->CallVoidMethod(env, jsys_rec,sys_rec_set_size_mid,(jlong)sys_rec->size);
    if ((*env)->ExceptionCheck(env)) {
	printf("An exception happened while setting size\n");
	return;
    }

    // set ctime
    (*env)->CallVoidMethod(env, jsys_rec,sys_rec_set_ctime_mid,(jlong)sys_rec->creation_time);
    if ((*env)->ExceptionCheck(env)) {
	printf("An exception happened while setting ctime\n");
	return;
    }

    //set reponse code
    (*env)->CallVoidMethod(env, jsys_rec,sys_rec_set_httpCode_mid,(jlong)response_code);
    if ((*env)->ExceptionCheck(env)) {
	printf("An exception happened while setting response code\n");
	return;
    }

    //set response message
    (*env)->CallVoidMethod(env, jsys_rec,sys_rec_set_httpMsg_mid,(*env)->NewStringUTF(env,err_string));
    if ((*env)->ExceptionCheck(env)) {
	printf("An exception happened while setting response message\n");
	return;
    }

    //

    (*env)->DeleteLocalRef(env,oid);
    (*env)->DeleteLocalRef(env,jhexOID);

}


long read_from_inputstream_source (void* stream, char *buff, long n){
    hc_long_t bytes_read = 0;
    jbyteArray jba = (*global_env)->NewByteArray(global_env, n);
    bytes_read = (*global_env)->CallIntMethod(global_env, *((jobject *)stream), input_stream_read_mid, jba);
    if (bytes_read != EOF) {
	    (*global_env)->GetByteArrayRegion(global_env,jba,0,bytes_read,(jbyte *)buff);
    }
    (*global_env)->DeleteLocalRef(global_env,jba);

    return bytes_read;
}


// Target Stream is a ref to WritableByteChannel
longe write_to_outstream_target (jobject target_stream, char *source, long n) {
    jobject jchar_array = NULL;
    jobject my_buffer = NULL;
    jint stored;
    if (n <= 0) {
	return HCERR_OK;
    }

    jchar_array = (*global_env)->NewByteArray(global_env,n);
    (*global_env)->SetByteArrayRegion(global_env, jchar_array,0,n,(jbyte *)source);
    my_buffer = (*global_env)->CallStaticObjectMethod(global_env,byte_buffer_class,jbyteBuffer_wrap_mid,jchar_array);
    if (my_buffer == NULL) {
	printf("Unable to instantiate a ByteBuffer class!\n");
	return HCERR_BAD_REQUEST;
    }
    stored = (*global_env)->CallIntMethod(global_env,target_stream, jwritable_byte_channel_write_mid, my_buffer);
    (*global_env)->DeleteLocalRef(global_env,jchar_array);

    return stored;
}


hcerr_t my_md_retrieve (char *host, int port, jbyte *c_oid, char *err_string, int err_string_len, 
		        hc_long_t *response_code, jobject map, jobject sys_map ) {

    hcerr_t status = HCERR_OK;
    hc_retrieve_metadata_handle_t handle;
    int done = FALSE;
    fd_set read_fd_set;
    fd_set write_fd_set;
    int max_read_fd;
    int max_write_fd;
    int max = -10;
    struct timeval timeout;
    char oid [OID_HEX_CHRSTR_LENGTH];
    char **names = NULL;
    char **values = NULL;
    int n = 0;
    long long_response = 0;
    jstring joid_key = NULL;
    jstring joid_value = NULL;
    jstring jloid_key = NULL;
    jstring jloid_value = NULL;
    int i = 0;

    timeout.tv_sec = 0;
    timeout.tv_usec = 100;
    memcpy(oid, c_oid, OID_HEX_CHRSTR_LENGTH);

    // get handle
    status = hc_retrieve_metadata_create(&handle, host, port, oid);
    if (status != HCERR_OK) {
	printf("Unable to establish handle. Error code: %d\n", status);
	return status;
    }

/*     // while not done, do IO */
/*     while (done == FALSE) { */
/* 	int selectres = -1; */
/* 	if(status = hc_retrieve_metadata(&handle, &done) == HCERR_IO_ERR) { */
/* 	    printf("IO ERROR was encoutered while attempting to retrieve Metadata.\n"); */
/* 	    done = 1; */
/* 	    break; */
/* 	} */
/* 	/\* now get the fds and select until somthing happens on them *\/ */
/* 	hcoa_get_read_fd_set(&read_fd_set, &max_read_fd); */
/* 	hcoa_get_write_fd_set(&write_fd_set, &max_write_fd);  */
/* 	max = max_read_fd; */
/* 	if(max_write_fd > max_read_fd) { */
/* 	    max = max_write_fd; */
/* 	} */
/* 	selectres = select(max+1, &read_fd_set, &write_fd_set, NULL, &timeout); */

/*     } */

    status = hc_retrieve_metadata_close(&handle, &long_response, err_string, err_string_len, &names, &values, &n);
    for (i = 0; i < n; i++) {
	    jstring jname = (*global_env)->NewStringUTF(global_env,names[i]);
	    jstring jvalue = (*global_env)->NewStringUTF(global_env,values[i]);
      	    (*global_env)->CallObjectMethod(global_env,map,jhashmap_put_mid,jname,jvalue);
    }
    *response_code=long_response;
   
    ret_handle = handle.retrieve_handle; 
    base_handle = ret_handle.base; 

    joid_key = (*global_env)->NewStringUTF(global_env,OBJECT_OID_KEY);
    joid_value = (*global_env)->NewStringUTF(global_env,oid);
    (*global_env)->CallObjectMethod(global_env,sys_map,jhashmap_put_mid,joid_key,joid_value);

    jloid_key = (*global_env)->NewStringUTF(global_env,OBJECT_LINK_ID_KEY);
    jloid_value = (*global_env)->NewStringUTF(global_env, base_handle.link_oid);
    (*global_env)->CallObjectMethod(global_env,sys_map,jhashmap_put_mid,jloid_key,jloid_value);

    return status;

}


hcerr_t my_retrieve (char *host, int port, jobject wbc, jbyte *c_oid, hc_long_t *response_code,
                     char *err_string, int err_string_len, jlong offset, jlong length) {

    hcerr_t status = HCERR_OK;
    hcoa_retrieve_handle_t handle;
    fd_set read_fd_set;
    fd_set write_fd_set;
    int max_read_fd;
    int max_write_fd;
    struct timeval timeout;
    int max = -10;
    int done = FALSE;
    char buf[RETRIEVE_BUFFER_SIZE];
    hc_long_t left = RETRIEVE_BUFFER_SIZE;
    hc_long_t read;
    char oid [OID_HEX_CHRSTR_LENGTH];
    long long_response = 0;

    timeout.tv_sec = 0;
    timeout.tv_usec = 100;

    memcpy(oid, c_oid, OID_HEX_CHRSTR_LENGTH);

    //get retrieve handle (if offset -, then standard, else range)
    if (offset < 0) {
	status = hcoa_retrieve_object_create(&handle,host,port,oid);
    } else {
	status = hcoa_range_retrieve_create(&handle,host,port,oid, (hc_long_t)offset,(hc_long_t)length);
    }
    if (status != HCERR_OK) {
	printf("Unable to establish handle. Error code: %d\n", status);
	return status;
    }

/*     //do IO */
/*     while(done == FALSE) {   */
/* 	int selectres = -1; */
/* 	if(status = hcoa_retrieve(&handle, buf, left-1, &read) == HCERR_IO_ERR) { */
/* 	    printf("IO ERROR\n"); */
/* 	    fflush(stdout); */
/* 	    done = 1; */
/* 	    break; */
/* 	} */
    
/* 	if(read == -1) { */
/* 	    done = TRUE; */
/* 	    break; */
/* 	} */
/* 	buf[read]='\0'; */
    
/* 	// write what we got to the bytechannel */
/* 	write_to_outstream_target (wbc,buf,read); */
/* 	left = RETRIEVE_BUFFER_SIZE; */
    
/* 	/\* now get the fds and select until somthing happens on them *\/ */
/* 	hcoa_get_read_fd_set(&read_fd_set, &max_read_fd); */
/* 	hcoa_get_write_fd_set(&write_fd_set, &max_write_fd);  */
/* 	max = max_read_fd; */
/* 	if(max_write_fd > max_read_fd) { */
/* 	    max = max_write_fd; */
/* 	} */

/* 	selectres = select(max+1, &read_fd_set, &write_fd_set, NULL, &timeout); */
/*     } */


    //close it
    status = hcoa_retrieve_close(&handle, &long_response, err_string, err_string_len);
    *response_code=long_response;
    return status;

}


/************************************/
/* JNI Functions Below              */
/************************************/
  


JNIEXPORT jint JNICALL Java_com_sun_honeycomb_client_Connection_native_1hc_1init (JNIEnv *env, jobject obj ) {

    jclass _object_id_class = (*env)->FindClass(env, OBJECT_IDENTIFIER_CLASS);
    jclass _qa_sys_rec_class = (*env)->FindClass(env, QA_SYSTEM_RECORD_CLASS);
    jclass _array_class = (*env)->FindClass(env, ARRAY_CLASS);
    jclass _byte_buffer_class = (*env)->FindClass(env, BYTE_BUFFER_CLASS);
    jclass _hashmap_class = (*env)->FindClass(env, HASHMAP_CLASS);
    jclass _byte_array_class = (*env)->FindClass(env, BYTE_ARRAY_CLASS);

    global_env = env;
    //Globalize 'em
    qa_sys_rec_class = (*env)->NewGlobalRef(env,_qa_sys_rec_class);
    object_id_class = (*env)->NewGlobalRef(env,_object_id_class);
    array_class = (*env)->NewGlobalRef(env,_array_class);
    byte_buffer_class = (*env)->NewGlobalRef(env,_byte_buffer_class);
    hashmap_class = (*env)->NewGlobalRef(env,_hashmap_class);
    byte_array_class = (*env)->NewGlobalRef(env,_byte_array_class);

    input_stream_read_mid = get_method_id(env, INPUT_STREAM_CLASS, "read","([B)I",FALSE);
    object_id_ctor_mid = get_method_id(env,OBJECT_IDENTIFIER_CLASS,"<init>","([B)V",FALSE);
    object_id_hexctor_mid = get_method_id(env,OBJECT_IDENTIFIER_CLASS,"<init>","(Ljava/lang/String;)V",FALSE);
    object_id_to_hex_mid = get_method_id(env,OBJECT_IDENTIFIER_CLASS,"toHexString","()Ljava/lang/String;",FALSE);
    sys_rec_set_oid_mid = get_method_id(env,QA_SYSTEM_RECORD_CLASS,"setObjectIdentifier","(Lcom/sun/honeycomb/client/ObjectIdentifier;)V",FALSE);
    sys_rec_set_sda_mid = get_method_id(env,QA_SYSTEM_RECORD_CLASS,"setDigestAlgorithm", "(Ljava/lang/String;)V",FALSE);
    sys_rec_set_size_mid = get_method_id(env,QA_SYSTEM_RECORD_CLASS, "setSize", "(J)V",FALSE);
    sys_rec_set_ctime_mid = get_method_id(env,QA_SYSTEM_RECORD_CLASS,"setCreationTime", "(J)V",FALSE);
    sys_rec_set_httpCode_mid = get_method_id(env,QA_SYSTEM_RECORD_CLASS,"setHTTPResponseCode","(J)V",FALSE);
    sys_rec_set_httpMsg_mid = get_method_id(env,QA_SYSTEM_RECORD_CLASS,"setHTTPResponseMessage","(Ljava/lang/String;)V",FALSE);
    sys_rec_set_datadigest_mid = get_method_id(env,QA_SYSTEM_RECORD_CLASS,"setDataDigest","(Ljava/lang/String;)V",FALSE);
    sys_rec_set_digestalgo_mid = get_method_id(env,QA_SYSTEM_RECORD_CLASS,"setDigestAlgorithm","(Ljava/lang/String;)V",FALSE);
    object_id_get_bytes_mid = get_method_id(env, OBJECT_IDENTIFIER_CLASS, "getBytes", "()[B",FALSE);
    jArray_get_arrayElem_mid = get_method_id(env,ARRAY_CLASS,"get","(Ljava/lang/Object;I)Ljava/lang/Object;",TRUE);
    jbyteBuffer_wrap_mid = get_method_id(env,BYTE_BUFFER_CLASS,"wrap","([B)Ljava/nio/ByteBuffer;",TRUE);
    jwritable_byte_channel_write_mid = get_method_id(env,WRITABLE_BYTE_CHANNEL_CLASS,"write","(Ljava/nio/ByteBuffer;)I",FALSE);
    jhashmap_put_mid = get_method_id(env,HASHMAP_CLASS,"put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",FALSE);
    byte_array_to_byte_array_mid = get_method_id(env,BYTE_ARRAY_CLASS,"toByteArray","(Ljava/lang/String;)[B",TRUE);

    hc_init(malloc,free,realloc); 

}


JNIEXPORT jint JNICALL Java_com_sun_honeycomb_client_Connection_native_1hc_1store_1object
  (JNIEnv *env, jobject obj, jstring jhost,jint jport, jobject byte_channel, jobject jsys_rec) {

      // allocate response structures
      hcerr_t result = HCERR_OK;
      int err_string_len = ERRSTR_MAX_LEN;
      hc_long_t response_code;
      char *err_string = (char *)malloc(ERRSTR_MAX_LEN);
      long long_response = 0;
      int port;
      const char *host = NULL;
      hc_system_record_t *sys_rec = NULL;

      if (err_string == NULL) {
	  return HCERR_OOM; 
      }
      sys_rec = (hc_system_record_t *)malloc(sizeof(hc_system_record_t));
      if (sys_rec == NULL) {
	  return HCERR_OOM; 
      }
      memset(sys_rec,'\0',sizeof(hc_system_record_t));

      // get host and port 
      host = (*env)->GetStringUTFChars(env, jhost, NULL);
      if (host == NULL) {
	  return HCERR_OOM; 
      }
      port = jport;

      //do it
      result = hcoa_store_ez( (char *)host, port, 
			    read_from_inputstream_source, (void *)&byte_channel, 
			    sys_rec, &long_response, err_string, err_string_len);
      response_code=long_response;

      //mark up java sys_record
      markUp_sys_rec(env,jsys_rec,sys_rec,err_string, response_code);

      free(err_string);
      free(sys_rec); 

      return result; 
}


JNIEXPORT jint JNICALL Java_com_sun_honeycomb_client_Connection_native_1hc_1store_1metadata
  (JNIEnv *env, jobject obj, jstring jhost,jint jport, jobject jsys_rec, jobject loid, 
   jobjectArray namesArray, jobjectArray valuesArray, jint md_size) {

      // allocate response structures
      hcerr_t result = HCERR_OK;
      int err_string_len = ERRSTR_MAX_LEN;
      hc_long_t response_code;
      char *err_string = (char *)malloc(ERRSTR_MAX_LEN);
      hc_system_record_t *sys_rec = NULL;
      const char *host = NULL;
      int port;
      jstring hex_oid = NULL;
      char *c_loid =NULL;
      long array_size;
      char **c_namesArray = NULL;
      char **c_valuesArray = NULL;
      long long_response = 0;

      if (err_string == NULL) {
	  return HCERR_OOM; 
      }
      sys_rec = (hc_system_record_t *)malloc(sizeof(hc_system_record_t));
      if (sys_rec == NULL) {
	  return HCERR_OOM; 
      }
      memset(sys_rec,'\0',sizeof(hc_system_record_t));

      // get host and port 
      host = (*env)->GetStringUTFChars(env, jhost, NULL);
      if (host == NULL) {
	  return HCERR_OOM; 
      }
      port = jport;

      hex_oid = (*env)->CallObjectMethod(env,loid,object_id_to_hex_mid);
      c_loid = (char *)(*env)->GetStringUTFChars(env,hex_oid,NULL); 
      array_size = md_size  * (sizeof(char**));
      c_namesArray = (char **) malloc(array_size);
      c_valuesArray = (char **) malloc(array_size);
      populate_c_arrays(env, namesArray, valuesArray, c_namesArray, c_valuesArray, md_size);

      result = hc
      result = hc_store_metadata_ez((char *)host, port, c_loid, c_namesArray, c_valuesArray, md_size, sys_rec, 
                             &long_response, (char *)err_string, err_string_len);
      response_code = long_response;

    
      markUp_sys_rec(env,jsys_rec,sys_rec,err_string, response_code);

      //XXX: free array values

      (*env)->ReleaseStringUTFChars(env,jhost, host);

      (*env)->ReleaseStringUTFChars(env,jhost,host);
      (*env)->ReleaseStringUTFChars(env,hex_oid, c_loid);
      (*env)->DeleteLocalRef(env,hex_oid);
      free(err_string);
      free(sys_rec); 
      return result; 
}


JNIEXPORT jint JNICALL Java_com_sun_honeycomb_client_Connection_native_1hc_1store_1both
  (JNIEnv *env, jobject obj, jstring jhost,jint jport, jobject input_stream, jobject jsys_rec,
  jobjectArray namesArray, jobjectArray valuesArray, jint md_size) {


      // allocate response structures
result = HCERR_OK;
      int err_string_len = ERRSTR_MAX_LEN;
      hc_long_t response_code;
      char *err_string = (char *)malloc(err_string_len);
      hc_system_record_t *sys_rec = (hc_system_record_t *)malloc(sizeof(hc_system_record_t));
      char *host = (char *)(*env)->GetStringUTFChars(env, jhost, NULL);
      long array_size = 0;
      long long_response = 0;
      char **c_namesArray = NULL;
      char **c_valuesArray = NULL;
      hc_nvr_t *nvr;

      if (sys_rec == NULL) {
	  return HCERR_OOM; 
      }
      memset(sys_rec,'\0',sizeof(hc_system_record_t));

      array_size = md_size  * (sizeof(char**));
      c_namesArray = (char **) malloc(array_size);
      c_valuesArray = (char **) malloc(array_size);
      populate_c_arrays(env, namesArray, valuesArray, c_namesArray, c_valuesArray, md_size); 

      nvr = NULL;
      result = hc_connection_bind_ez(host, jport, 
				     &long_response, err_string, err_string_len);
      result = hc_nvr_from_string_arrays(&nvr, c_namesArray, c_valuesArray, md_size);

      result = hc_store_both_ez(host, jport, 
		      		read_from_inputstream_source, (void *)&input_stream, 
				nvr,
				sys_rec, 
				&long_response, err_string, err_string_len);
      hc_nvr_free(nvr);
      response_code = long_response;
      markUp_sys_rec(env,jsys_rec,sys_rec,err_string, response_code);

      //XXX: free the array values 
      (*env)->ReleaseStringUTFChars(env,jhost,host);
      free(err_string);
      free(sys_rec); 

      return result;  
}


JNIEXPORT jint JNICALL Java_com_sun_honeycomb_client_Connection_native_1hc_1delete_1object
  (JNIEnv *env, jobject obj, jstring jhost,jint jport, jobject oid, jobject statObj ) {

      // allocate response structures
      hcerr_t result = HCERR_OK;
      int err_string_len = ERRSTR_MAX_LEN;
      long response_code;
      char *err_string = NULL;
      hc_system_record_t *sys_rec = NULL;
      char *host = (char *)(*env)->GetStringUTFChars(env, jhost, NULL);
      int port = 0;
      long long_response = 0;
      jstring hex_oid = NULL;
      jbyte *c_oid = NULL;
      jclass stat_class;
      jfieldID stat_fid;
      jfieldID reas_fid;
      jstring reas_jstring;

      if (statObj == NULL) {
	  return HCERR_BAD_REQUEST;
      }

      err_string = (char *)malloc(ERRSTR_MAX_LEN);
      if (err_string == NULL) {
	  return HCERR_OOM; 
      }
      sys_rec = (hc_system_record_t *)malloc(sizeof(hc_system_record_t));
      if (sys_rec == NULL) {
	  return HCERR_OOM; 
      }
      memset(sys_rec,'\0',sizeof(hc_system_record_t));

      // get host and port 
      if (host == NULL) {
	  return HCERR_OOM; 
      }
      port = jport;

      hex_oid = (*env)->CallObjectMethod(env,oid,object_id_to_hex_mid);
      c_oid = (signed char*)(*env)->GetStringUTFChars(env,hex_oid,NULL); 
	
      long_response = 0;
      result = hc_delete_ez(host, port, c_oid, &long_response, err_string, err_string_len);
      response_code = long_response;


      // markup the stat obj
      // XXX move this to common code
      stat_class = (*env)->GetObjectClass(env,statObj);
      stat_fid = (*env)->GetFieldID(env,stat_class,"status","J");
      (*env)->SetLongField(env,statObj,stat_fid,(jlong)response_code);
      reas_fid = (*env)->GetFieldID(env,stat_class,"reason","Ljava/lang/String;");
      reas_jstring = (*env)->NewStringUTF(env,err_string);
      (*env)->SetObjectField(env,statObj,reas_fid,reas_jstring);
      
      (*env)->ReleaseStringUTFChars(env,jhost,host);
      (*env)->ReleaseStringUTFChars(env,hex_oid,(const char *)c_oid);
      (*env)->DeleteLocalRef(env,hex_oid);
      (*env)->DeleteLocalRef(env,reas_jstring);

      free(err_string);
      free(sys_rec); 
      return result;

}


JNIEXPORT jint JNICALL Java_com_sun_honeycomb_client_Connection_native_1hc_1retrieve_1object
  (JNIEnv *env, jobject obj, jstring jhost, jint jport, jobject oid, jobject wbc, 
  jboolean metadata, jlong offset, jlong length, jobject statObj, jobject map, jobject sys_map) {

      // allocate response structures
      hcerr_t result = HCERR_OK;
      int err_string_len = ERRSTR_MAX_LEN;
      hc_long_t response_code = 0;
      char *err_string = NULL;
      hc_system_record_t *sys_rec = NULL;
      char *host = (char *)(*env)->GetStringUTFChars(env, jhost, NULL);
      jstring hex_oid = NULL;
      jbyte *c_oid = NULL;
      jclass stat_class = NULL;
      jfieldID stat_fid = NULL;
      jfieldID reas_fid = NULL;
      jstring reas_jstring = NULL;

      err_string = (char *)malloc(err_string_len);
      sys_rec = (hc_system_record_t *)malloc(sizeof(hc_system_record_t));
      if (sys_rec == NULL) {
	  return HCERR_OOM; 
      }

      hex_oid = (*env)->CallObjectMethod(env,oid,object_id_to_hex_mid);
      c_oid = (signed char *)(*env)->GetStringUTFChars(env,hex_oid,NULL); 

      if (metadata == JNI_TRUE) {
	result=my_md_retrieve(host,jport, c_oid, err_string, err_string_len, &response_code, map, sys_map);
      }
      else {
	result = my_retrieve (host, jport, wbc, c_oid, &response_code, err_string, err_string_len, offset, length);
      }

      stat_class = (*env)->GetObjectClass(env,statObj);
      stat_fid = (*env)->GetFieldID(env,stat_class,"status","J");
      (*env)->SetLongField(env,statObj,stat_fid,(jlong)response_code);
      reas_fid = (*env)->GetFieldID(env,stat_class,"reason","Ljava/lang/String;");
      reas_jstring = (*env)->NewStringUTF(env,err_string);
      (*env)->SetObjectField(env,statObj,reas_fid,reas_jstring);

      (*env)->ReleaseStringUTFChars(env,jhost,host);
      (*env)->ReleaseStringUTFChars(env,hex_oid,(const char *)c_oid);
      (*env)->DeleteLocalRef(env,hex_oid);
      (*env)->DeleteLocalRef(env,reas_jstring);

      free(err_string);
      free(sys_rec); 
      return result;
}



JNIEXPORT void JNICALL Java_com_sun_honeycomb_client_Connection_native_shut_down
  (JNIEnv *env, jobject obj) {

      hc_cleanup();

      (*env)->DeleteGlobalRef(env,qa_sys_rec_class);
      (*env)->DeleteGlobalRef(env,object_id_class);
      (*env)->DeleteGlobalRef(env,byte_buffer_class);
      (*env)->DeleteGlobalRef(env,hashmap_class);
}

