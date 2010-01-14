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



#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <assert.h>


#include "hc.h"
#include "hcinternals.h"
#include "hcclient.h"
#include "hctestutil.h"
#include "hctestcommon.h"
#include "hcoa.h"
#include "hcoaez.h"
#include "hcnvoa.h"
#include "hcnvoai.h"
#include "platform.h"

#include "sha1.h"

/* Internal Declarations */

#if  defined(_MSC_VER)
#define random()  ((unsigned long) rand() * rand())
#endif /* _MSC_VER */

void
log_session_error_info(hc_session_t *session, hcerr_t err) {
   int32_t connect_errno = -1;
   int32_t platform_result = -1;
   int32_t response_code = -1;
   char *errstr = "";

   hc_session_get_platform_result(session, &connect_errno, &platform_result);
   hc_session_get_status(session, &response_code, &errstr);
   hc_test_log(LOG_ERROR_LEVEL, NULL, 
	       "HC ERROR: Session 0x%lx, err=%d %s\n"
	       "   Response_code=%d Connect_errno %d Platform_result: %d\n"
	       "   Errstr: %s\n", 
	       (long)session, err, hc_decode_hcerr(err), 
	       response_code, connect_errno, platform_result,
	       errstr);
 }
/*
 * Determines whether the error can be treated as a warning instead of an
 * error. 
 * Errors can treat as Warnings:
 * HADB-E-3505  - Relalg error from HADB - non-fatal error; ignore
 */
int
treat_as_warning(hc_session_t * session, hcerr_t err) {
    int warning = FALSE;
    int32_t connect_errno = -1;
    int32_t platform_result = -1;
    int32_t response_code = -1;
    char *errstr = "";
    hc_session_get_platform_result(session, &connect_errno, &platform_result);
    hc_session_get_status(session, &response_code, &errstr);
    if (strstr(errstr, "HADB-E-03505") != NULL) {
        warning = TRUE;
        hc_test_log(LOG_WARN_LEVEL, NULL, 
           "HC WARNING: HADB-E-03505 - Relalg error, handle as warning:\n"
           "   Session 0x%lx, err=%d %s\n"
           "   Response_code=%d Connect_errno %d Platform_result: %d\n",
           (long)session, err, hc_decode_hcerr(err), 
           response_code, connect_errno, platform_result);
     }
    
    return warning;
}
// Converts a time structure to a string
// uses YYYY-mm-dd HH:MM:SS format

void time_to_text (time_t time, char *time_b) {
  struct tm my_timeval;
  struct tm *timeptr = localtime_r(&time,&my_timeval);

  strftime(time_b, HC_TIME_TEXT_SIZE, "%Y-%m-%d %H:%M:%S", timeptr);
}

/* Check to see if a particular file */
/* is currently on our path. If so, */
/* return TRUE, else False */
int on_path(char *bin) {
    char * my_path = getenv(PATH_ENV_VAR);
    char * my_path_copy  = (char *)malloc(strlen(my_path) +1); 
    char * path_tok = NULL;
    int res = TRUE;

     //do a simple validity check
    memset(my_path_copy,'\0',strlen(my_path) +1);
    if (bin == NULL) {
		res = FALSE;
    }
    if (strstr(bin,PATH_SEP) != NULL)  {
		res = FALSE;
    }
    if (strstr(bin,FILE_SEP) != NULL)  {
		res = FALSE;
    }
	if (my_path_copy == NULL) {
		res = FALSE;
	}
	
	if (res != FALSE) {
		//strtok appears to be destructive, have it operate on a copy
		strcpy(my_path_copy, my_path);
		if ((path_tok = strtok(my_path_copy, PATH_SEP)) != NULL) {
			res = FALSE; //set it false for now, if we find we will reset it
			do {
				size_t sz = strlen(path_tok) + strlen(bin) + 2;
				char * bin_path = (char *) malloc(sz);
				FILE *bin_file = NULL;

				memset(bin_path,'\0',sz);
				if (ends_with(path_tok, FILE_SEP)) {
					sprintf(bin_path,"%s%s", path_tok,bin);
				}
				else {
					sprintf(bin_path,"%s%s%s", path_tok,FILE_SEP,bin);
				}
				bin_file = fopen(bin_path,"rb");
				if (bin_file != NULL) {
					fclose(bin_file);
					free(bin_path);
					res = TRUE;
					break;
				} 
				free(bin_path);
			}while ((path_tok = strtok(NULL, PATH_SEP)) != NULL);
		}
	}
    free(my_path_copy);
    return res;
}

/* check to see if a string ends with a */
/* sub-string. If so, return TRUE, else */
/* return FALSE. */
int ends_with(char *source, char *token) {

    size_t source_len = strlen(source);
    size_t token_len = strlen(token);
    int size_diff = source_len - token_len;
    char *s_p = NULL;

    if (token_len > source_len || strstr(source,token) == NULL ) {
	return FALSE;
    }

    s_p = source + size_diff;

    if (strcmp(s_p,token) == 0)
	return TRUE;
    else
	return FALSE;

}


/* generates a random file on the filesystem of a specified size */
/* return number of bytes written into file */
hc_long_t file_generator(hc_long_t size, int seed, char *file_name, FILE *out_file) {
    hc_long_t byteswritten;
    srand(seed);

    strcpy(file_name, hc_tmpnam());

    out_file = fopen(file_name, "wb+");
    if (out_file == NULL) {
	return 0;
    }
    setvbuf(out_file, NULL, _IOFBF, MSG_BUFFER_SIZE);

    for (byteswritten = 0; byteswritten < size && putc((rand() % 126),out_file) != EOF; byteswritten++);

    fclose(out_file);
    return byteswritten; 
}


/* Not currently used by any of our EZ tests. I believe the NB tests use it*/
long qa_read_from_file_data_source (void* stream, char* buff, long n){
  long nbytes;

  nbytes = read((int) stream, buff, n);
  return nbytes;
}

hc_hashlist_t *create_hashlist(int size) {

    hc_hashlist_t *list = NULL;

    if ( (list = (hc_hashlist_t *)malloc(sizeof(hc_hashlist_t))) == NULL) {
		return NULL;
    }
    memset(list,'\0',sizeof(hc_hashlist_t));

    if ( (list->linked_list_array = (hc_llist_t **)malloc(sizeof(hc_llist_t *) * size)) == NULL) {
		free(list);
		return NULL;
    }
    list->list_size = size;
	list->number_of_entries = 0;
    memset(list->linked_list_array,'\0',sizeof(hc_llist_t*)*size);
    return list;
}


// inspired by K & R "The C programming language" hash impl
/* Returns a hash value for a particular string and hashlist */
unsigned hash_string (char *s, hc_hashlist_t *hashlist) {
    unsigned hashval;
    for (hashval = 0; *s != '\0'; s++)
	hashval = *s + 31 * hashval;
    return hashval % hashlist->list_size;
}

int hashlist_contains_key (char *lkey, hc_hashlist_t *hashlist) {
	if (hash_get_nv_pair(lkey, hashlist) != NULL) {
		return TRUE;
	}
	else
		return FALSE;
}


hc_str_nvpair_t *hash_get_nv_pair(char *lkey, hc_hashlist_t *hashlist ) {
    hc_llist_node_t *cur_elem;
    hc_llist_t *list_p;
    list_p = hashlist->linked_list_array[hash_string(lkey,hashlist)];
    if (list_p == NULL) {
		return NULL;
    }
    for (cur_elem = list_p->head; cur_elem != NULL; cur_elem = cur_elem->next){
	hc_str_nvpair_t *cur_ent = cur_elem->entry;
	if (cur_ent->name != NULL && strcmp(lkey, cur_ent->name) == 0) {
	    return cur_ent;
	}
    }
    return NULL;
}

char *hash_get_value(char *lkey, hc_hashlist_t *hashlist) {
    hc_str_nvpair_t *cur_ent;
    if ( (cur_ent = hash_get_nv_pair(lkey,hashlist)) !=NULL) {
	return cur_ent->value;
    }
    return NULL;
}


hc_str_nvpair_t *hash_put(char *key, char *value, hc_hashlist_t *hashlist) {

    hc_str_nvpair_t *entry = NULL;
    char *new_value = NULL;

    if ( (entry = hash_get_nv_pair(key,hashlist)) != NULL) { // entry exist in hash, replace value

		if ( (new_value = (char *)malloc(strlen(value) + 1)) == NULL) {
			return NULL;
		}
		memset(new_value,'\0',strlen(value) + 1);
		strcpy(new_value,value);
		free(entry->value);
		entry->value = new_value;

	} else {  // entry does not exist in hash

        char *new_key = NULL;
        hc_llist_node_t *list_elem = NULL;
        hc_llist_t *list = NULL;
		unsigned hash_value = hash_string(key,hashlist);

		// do I already have a list? If not make one
		if ( (list = hashlist->linked_list_array[hash_value]) == NULL) {

			if ( (list = (hc_llist_t *)malloc(sizeof (hc_llist_t))) == NULL) {
				return NULL;
			}
			memset(list,'\0',sizeof(hc_llist_t));
			list->head=NULL;

			hashlist->linked_list_array[hash_value] = list;
		} 

		// make list elem
		//if ((list_elem = (hc_llist_node_t *)malloc(sizeof (hc_llist_node_t))) == NULL) {
		  // return NULL;
		  //} 
		//		memset(list_elem,'\0',sizeof(hc_llist_node_t));

		//make entry
		if ((entry = (hc_str_nvpair_t *)malloc(sizeof (hc_str_nvpair_t))) == NULL) {
			return NULL;
		} 
		memset(entry,'\0',sizeof(hc_str_nvpair_t));
		//list_elem->entry = entry;

		//make name
		if ((new_key = (char *)malloc(strlen(key)+1)) == NULL) {
			return NULL;
		}
		memset(new_key,'\0',strlen(key+1));		
		strcpy(new_key,key);
		entry->name = new_key;

		//make value
		if ((new_value = (char *)malloc(strlen(value)+1)) == NULL) {
			return NULL;
		} 
		memset(new_value,'\0',strlen(value) +1 );
		strcpy(new_value,value);
		entry->value = new_value;
		linkedlist_add_nodes(list,1, entry);
	}
	
	hashlist->number_of_entries++;
	
    return entry;

}

void free_nv_pair (void * arg) {
    hc_str_nvpair_t *pair = (hc_str_nvpair_t *) arg;
    if (pair->name) free(pair->name);
    if (pair->value) free(pair->value);
    free(pair);
}

void free_linkedlist (hc_llist_t *list,void (*free_fn) (void *)) {
    hc_llist_node_t *cur_elem = NULL;
    hc_llist_node_t *next_elem = NULL;
    for (cur_elem = list->head; cur_elem != NULL; cur_elem = next_elem) {
		next_elem = cur_elem->next;
		free_fn(cur_elem->entry); 
		free(cur_elem);
    }
    free(list);
}



void free_hashlist (hc_hashlist_t *list) {
    int i;
    for (i = 0; i < list->list_size; i++) {
		hc_llist_t *linked_list= list->linked_list_array[i];
		if (linked_list != NULL) {
		    free_linkedlist(linked_list, free_nv_pair);
		}
    }
    free(list->linked_list_array);
    free(list);
}


void free_r_file(hc_random_file_t *r_file) {
	if (r_file != NULL) {
		if (r_file->sha_context != NULL)
			free(r_file->sha_context);
		free(r_file);
	}
}


/*
 * Simply translates an long to string representation of that long.
 * Perhaps there function in one of the standard C libs that already do this?
 */ 
char * hc_itoa(long number) {
    char *alpha_num;
    long num_cpy = number;
    long num_digits = 0;
    do {
		num_cpy = num_cpy/10;
		++num_digits;
    } while (num_cpy != 0) ;
    alpha_num = (char *)malloc(num_digits + 1);
    memset(alpha_num, '\0', num_digits);
    if (alpha_num != NULL) {
		sprintf(alpha_num,"%ld",number);
    }
    return alpha_num;
}


hc_long_t translate_size(char * size_text) {
    
    hc_long_t size_multiplier;
    char *size_value_txt;
    char *text_ptr;
    size_t num_digits = 0;
    hc_long_t size;

    //parse size_text
    //iter through the text looking for the first non number
    for (text_ptr = size_text; text_ptr < size_text + strlen(size_text); text_ptr++) {
	if (!isdigit(*text_ptr))
	    break;
	num_digits++;
    }


    if (num_digits == 0) {  // there were no digits
	return -1;
    }

    if (num_digits == strlen(size_text)) { // it's all digits
	return atol(size_text);
    }

    if ( (size_value_txt = (char *)malloc(num_digits + 1)) == NULL) {
		return -1;
    }
    memset(size_value_txt,'\0',num_digits +1);

    strncpy(size_value_txt,size_text,num_digits);
    
    if (strcmp(text_ptr,KB_TEXT) == 0) {
		size_multiplier = KB;
    } else if (strcmp(text_ptr,MB_TEXT) == 0) {
		size_multiplier = MB;
    } else if (strcmp(text_ptr,GB_TEXT) == 0) {
		size_multiplier = GB;
    } else {
		free(size_value_txt);
		return -1;
    }
    size = atoi(size_value_txt) * size_multiplier;
    free(size_value_txt);
    return size;
}

/* 
 * Requests byte from a random data generator and places those bytes 
 * in the buffer provided.As bytes are read from the random data generator, 
 * the random_file data structure is updated to include number of bytes 
 * read and it's sha is updated to include the bytes just processes.
 * 
 * NOTE: This function does finalize the sha if:
 * a) random_file->bytes_read = random_file->file_size. 
 * I believe it is bad voodoo to call the sha finalize function 
 * more than once, so clients of this function should be aware of this. 
 *
 * NOTE: This function Mallocs a sha1_context structure. Clients will
 * need to free this structure on their own.
 */
void init_random_data();

long read_from_random_data_generator (void * random_file, char *buf, long len) {
	hc_random_file_t * r_file = NULL;
	sha1_context * sha_context = NULL;
	long bytes_created = 0;
	unsigned char * sha_data_ptr = NULL;
	hc_long_t sha_start = 0;
	hc_long_t sha_len = 0;
	long nbytes;
	
	r_file = (hc_random_file_t*)random_file;

	assert(r_file->file_size >= 0);

	if (r_file->sha1_init != TRUE) {
		sha_context = (sha1_context *) malloc(sizeof(sha1_context)); //XXX MAKE SURE THAT I FREE THIS!!!
		memset(sha_context,'\0',sizeof(sha1_context));
		sha1_starts(sha_context);
		srand(r_file->seed);
		init_random_data();
		r_file->sha_context = sha_context;
		r_file->sha1_init = TRUE;
		assert(r_file->bytes_read == 0);
	} else {
		sha_context = r_file->sha_context;
	}
	assert(r_file->file_size >= r_file->bytes_read);
	
	// I am done with the sha
	if (r_file->bytes_read == r_file->file_size  && 
	    r_file->sha1_init == TRUE) {
		sha1_finish(sha_context,r_file->digest);
		return 0;
	}

	bytes_created = generate_random_data(buf, len,
			(hc_long_t)(r_file->file_size - r_file->bytes_read));

/* 	//get_range_overlap(r_file->bytes_read, bytes_created, r_file->sha_offset, */
/* 	//		  r_file->sha_length, &sha_start,&sha_len); */

	// update sha
	r_file->bytes_read += bytes_created;

	assert(r_file->file_size >= r_file->bytes_read);

	if (bytes_created > 0 && sha_len != -1) {
		//sha_data_ptr = (unsigned char *)buf + (sha_start - r_file->bytes_read); // NOT SURE WHAT I WAS SMOKING WHEN I WROTE THIS
		//sha1_update(sha_context,sha_data_ptr,(long)sha_len);                    // COMMENT OUT FOR THE TIME BEING
		sha1_update(sha_context, (uint8 *)buf, bytes_created);
	}

	//hc_test_log(LOG_DEBUG_LEVEL, NULL, "read_from_random_data_generator(len=%ld, retbytes=%ld)\n",len,*retbytes);
	return bytes_created;
}

/* Converts a file on the file system into the r_file data structure.
 * This consists of two things:
 * 1) calculating the sha of the data in the file
 * 2) calculating the size of the file.
 * 
 * The purpose of this function is to allow our tests to compare a file in the
 * file-system against an r_file structure. By being able to do so, we can store a 
 * a random file to an HC arhive and than seperately retrieve the file from Honeycomb and
 * store it on the local file-system and then examine the local file to ensure that what 
 * was written to disk is the same as what HC was asked to store. 
 */
int file_to_r_file(char * file_name, hc_random_file_t *r_file) {
	int fd, res, bytes_read;
	size_t buffer_size;
	unsigned char * buffer;
	sha1_context * sha_context;
	
	buffer_size = 4096;
	bytes_read = -1;
	res = 0;
	fd = open(file_name, O_RDONLY | O_BINARY);
	if (fd == -1) {
		hc_test_log(LOG_ERROR_LEVEL, NULL, "Unable to open file %s . \n", file_name);
		return -1;
	}
	
	buffer = (unsigned char *)malloc(buffer_size);
	memset(buffer,'\0',buffer_size);
	sha_context = (sha1_context *)malloc(sizeof(sha1_context));
	memset(sha_context,'\0',sizeof(sha1_context));

	sha1_starts(sha_context);
	r_file->sha1_init = TRUE;
	r_file->sha_context = sha_context;
	
	while (bytes_read != 0) {
		bytes_read = read(fd,buffer,buffer_size);
		if (bytes_read != -1) {
			r_file->file_size = r_file->file_size + bytes_read;
			sha1_update(sha_context,buffer,bytes_read);			
		} else {
			hc_test_log(LOG_ERROR_LEVEL, NULL, "An IO ERROR occurred while reading file %s . \n", file_name);	
			res = -1;
			break;		
		}
	}
	sha1_finish(sha_context,r_file->digest);
	close(fd);
	free(buffer);
	return res;
	
}

hc_long_t write_random_data_to_file(hc_random_file_t *r_file, char * file_name) {
	int outfile;
	long bytes_read;
	char *buffer;
	hc_long_t bytes_written, file_size;
	hcerr_t res;
	
	bytes_read = bytes_written = 0;
	outfile = -1;
	file_size = r_file->file_size;
	outfile = open(file_name, O_CREAT|O_WRONLY|O_LARGEFILE|O_TRUNC|O_BINARY, S_IREAD|S_IWRITE);
	if (outfile == -1) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,"Could not open file %s for writing.\n", file_name);
		return -1;
	}
	buffer = (char *)malloc(4096);
	memset(buffer,'\0',4096);
	
	bytes_read = read_from_random_data_generator(r_file,buffer,4096);
	while (bytes_read > 0) {
		long bw = write(outfile,buffer,bytes_read);
		if(bw != bytes_read) {
				hc_test_log(LOG_ERROR_LEVEL,NULL,"An error occurred writing %ld bytes to file %s.\n", bytes_read, file_name);
				break;
		}
		bytes_written += bw;
		bytes_read = read_from_random_data_generator(r_file,buffer,4096);
	}
	close(outfile);
	if (buffer != NULL)
		free(buffer);
	return bytes_written;
}

/*
 * Takes the date from supplied buffer and "writes" into the r_file data structure.
 * This function is designed to be used as data sink for honeycomb read ops.
 * Also assumes that Honeycomb read ops invoke this function with len of -1 when 
 * EOF is reached.
 *
 * Caller must call init_random_file first to initialize the sha_context
 */
long retrieve_data_to_rfile(void *random_file, char *buffer, long len) {
	hc_random_file_t *r_file;
	sha1_context * sha_context;
	
	r_file = (hc_random_file_t *)random_file;
	
	sha_context = r_file->sha_context;
	assert(sha_context != NULL);

	sha1_update(sha_context,(uint8 *)buffer,len);
	r_file->file_size = r_file->file_size + len;
	return len;
}

static char rand_buffer[16 * 1024];

void init_random_data() {
	int i;
	char *buffer = rand_buffer;
	for (i=0; i<sizeof(rand_buffer); i++) {
		*buffer = (char)rand();
		buffer++;
	}
}

long generate_random_data(char * buffer, long buffer_length, hc_long_t bytes_requested) {
	long i = 0;
	long bytes_to_generate = 0;
	long remaining_bytes;

	assert(bytes_requested >= 0);

	/* deal with type sizing: don't assign long=longlong */
	if (bytes_requested > buffer_length)
		bytes_to_generate = buffer_length;
	else
		bytes_to_generate = bytes_requested;
	remaining_bytes = bytes_to_generate;

	while (remaining_bytes > 0) {
		long bytes_to_copy = remaining_bytes;
		if (bytes_to_copy > sizeof(rand_buffer))
			bytes_to_copy = sizeof(rand_buffer);
		memcpy(buffer, rand_buffer, bytes_to_copy);
		buffer += bytes_to_copy;
		remaining_bytes -= bytes_to_copy;
	}
	return bytes_to_generate;
}

hc_llist_node_t * create_llist_node (void *value) {
	hc_llist_node_t *list_node  = NULL; 
	size_t node_size  = sizeof(hc_llist_node_t);
	list_node = (hc_llist_node_t *)calloc(1,node_size);
	if (list_node == NULL) {
	    return NULL;
	} 
	list_node->entry = value;
	list_node->next = NULL;
	list_node->prev = NULL;
	return list_node;
}


void linkedlist_add_nodes(hc_llist_t * list, size_t num_nodes, ...) {
	hc_llist_node_t *cur_node;
	size_t i;
	va_list elems;

	va_start(elems,num_nodes);

	for (i = 0; i < num_nodes; i++) {
		cur_node = create_llist_node(va_arg(elems,void *));
		//add element into list
		if (list->head == NULL) {
			list->head = cur_node;
			list->tail = cur_node;
		} else {
			hc_llist_node_t *cur_tail = list->tail;
			cur_tail->next = cur_node;
			cur_node->prev = cur_tail;
			list->tail = cur_node;
		}
		list->list_size++;
	}
	va_end(elems);
}

void linkedlist_remove_elem(hc_llist_t * list, hc_llist_node_t *elem) {
	hc_llist_node_t *cur_elem;
	if (elem == NULL || list == NULL) {
	    return;
	}
	if ( (cur_elem = (hc_llist_node_t *)list->head) != NULL) {
	    while (cur_elem != NULL) {
			if (cur_elem == elem) {
		    		hc_llist_node_t *prev;
		    		hc_llist_node_t *next;
		    		prev = (hc_llist_node_t *)cur_elem->prev;
		    		next = (hc_llist_node_t *)cur_elem->next;
		    		if (prev == NULL) {
					list->head = next;
					next->prev = NULL;
		    		} else if (next == NULL) {
					list->tail = prev;
					prev->next = NULL;
		    		} else {
					prev->next = next;
					next->prev = prev;
		    		}
		    		free(elem->entry);
		    		list->list_size--;
		    		free(elem);
		   	 	break;
			} else {
		    		cur_elem = cur_elem->next;
			}
	    }
	}
}

hc_llist_t * hc_create_linkedlist(void) {
   hc_llist_t * list;  
   list = (hc_llist_t *)calloc(1, sizeof (hc_llist_t)); 
   if (list != NULL) {
       list->head = NULL;
       list->tail = NULL;
	   list->list_size = 0;
   }
   return list;
}

hc_long_t get_file_size (char *file_size_type) {
	float multiplier = rand_multiplier();
	hc_long_t min_size = 0;
	hc_long_t max_size = 0;
	hc_long_t res;

	if (strcmp(file_size_type,HCFILE_SIZE_TYPE_EMPTY) == 0) {
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_ONEBYTE) == 0) {
		min_size=1;
		max_size=1;
		multiplier = (float)1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_XXSMALL) == 0) {
		min_size = 1;
		max_size = translate_size("1KB");
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_XSMALL) == 0) {
		min_size = translate_size("1KB");
		max_size = translate_size("1MB");
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_SMALL) == 0) {
		min_size = translate_size("1MB");
		max_size = translate_size("10MB");
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_MED) == 0) {
		min_size = translate_size("10MB");
		max_size = translate_size("1GB");
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_LARGE) == 0) {
		min_size = translate_size("1GB");
		max_size = translate_size("10GB");
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_XLARGE) == 0) {
		min_size = translate_size("10GB");
		max_size = translate_size("100GB");
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_MAX) == 0) {
		min_size = translate_size("100GB");
		max_size = translate_size("100GB");
		multiplier = (float)1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_RAND) == 0) {
		min_size = 0;
		max_size = translate_size("100GB");
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_STORE_BUFFER) == 0) {
		min_size = max_size = STORE_BUFFER_SIZE;
		multiplier = (float)1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_STORE_BUFFER_PLUS) == 0) {
		min_size = max_size = STORE_BUFFER_SIZE + 1;
		multiplier = (float)1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_STORE_BUFFER_MINUS) == 0) {
		min_size = max_size = STORE_BUFFER_SIZE - 1;
		multiplier = (float)1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_STORE_BUFFER_HALF) == 0) {
		min_size = STORE_BUFFER_SIZE;
		max_size = STORE_BUFFER_SIZE * 2;
		multiplier = 0.5;
	} else {
		min_size = max_size = translate_size(file_size_type);
		multiplier = (float)1;
	}
	res = min_size + (hc_long_t)((max_size - min_size) * multiplier);

	assert(res >= 0);
	hc_test_log(LOG_DEBUG_LEVEL,NULL,"get_file_size:  file_size="LL_FORMAT"\n",res);

	return res;
}


hc_long_t get_filesize_type_max (char * file_size_type) {
	hc_long_t res = -1;
	if (strcmp(file_size_type,HCFILE_SIZE_TYPE_EMPTY) == 0) {
		res = 0;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_ONEBYTE) == 0) {
		res = 1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_XXSMALL) == 0) {
		res = translate_size("1KB") - 1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_XSMALL) == 0) {
		res = translate_size("1MB") - 1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_SMALL) == 0) {
		res = translate_size("10MB") -1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_MED) == 0) {
		res = translate_size("1GB") -1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_LARGE) == 0) {
		res = translate_size("10GB") - 1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_XLARGE) == 0) {
		res = translate_size("100GB") -1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_MAX) == 0) {
		res =  translate_size("100GB");
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_STORE_BUFFER) == 0) {
		res = STORE_BUFFER_SIZE;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_STORE_BUFFER_PLUS) == 0) {
		res = STORE_BUFFER_SIZE + 1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_STORE_BUFFER_MINUS) == 0) {
		res = STORE_BUFFER_SIZE - 1;
	} else if (strcmp(file_size_type,HCFILE_SIZE_TYPE_STORE_BUFFER_HALF) == 0) {
		res = STORE_BUFFER_SIZE * 1.5;
	} 
	return res;
}


float rand_multiplier() {
	float res = 0;
	int mod = 0; 
	hc_long_t exp = 10;
	int r_num = rand();
	mod = r_num;
	while (mod > 9) {
		exp = 10 * exp;
		mod = mod / 10;
	}
	res = (float)r_num / exp;
	return res;
}

hc_random_file_t * create_random_file(hc_long_t f_size) {
	hc_random_file_t * r_file = (hc_random_file_t *)malloc(sizeof (hc_random_file_t));
	memset(r_file,'\0',sizeof(hc_random_file_t));
	r_file->file_size = f_size;
	r_file->seed = rand();
	r_file->sha1_init = FALSE;
	r_file->sha_context = NULL;
	r_file->bytes_read = 0;
	r_file->sha_offset = 0;
	r_file->sha_length = f_size;
	return r_file;
}

void
init_random_file(hc_random_file_t *r_file) {
	sha1_context * sha_context;

	assert(r_file->sha1_init == FALSE);
	assert(r_file->sha_context == NULL);
	sha_context = (sha1_context *) malloc(sizeof(sha1_context)); //XXX MAKE SURE THAT I FREE THIS!!!
	memset(sha_context,'\0',sizeof(sha1_context));
	sha1_starts(sha_context);
	r_file->sha_context = sha_context;
	r_file->sha1_init = TRUE;
} 

void print_random_file(hc_random_file_t * r_file) {
	int i = 0;
	printf("File Size: %ld\n", (long)r_file->file_size);
	printf("File seed: %d\n", (int)r_file->seed);
	printf("File SHA1: ");
	for (i = 0; i < 20; i++) {
		printf("%x",r_file->digest[i]);
	}
	printf("\n");
}


void digest_to_string(unsigned char digest[20], char * string_digest) {
	int i = 0;
	char * ptr = string_digest;
	for (i =0; i < 20; i++) {
		sprintf( string_digest + i * 2, "%02x", digest[i] );	   
	}
	ptr ='\0';
}



void print_sys_rec (hc_system_record_t *sys_rec) {
	printf("OID %s\n",sys_rec->oid);
	printf("digest alog %s\n",sys_rec->digest_algo);
	printf("data digest %s\n",sys_rec->data_digest);
	printf("size "LL_FORMAT"\n",sys_rec->size);
	printf("Created time "LL_FORMAT"\n",sys_rec->creation_time);
	printf("Deleted time "LL_FORMAT"\n",sys_rec->deleted_time);
	printf("Shread Mode %c\n",sys_rec->shredMode);
	
}


int compare_rfile_to_sys_rec(hc_random_file_t *r_file, hc_system_record_t *sys_rec) {
	char * s_digest;
	int res = TRUE;
	s_digest = (char *) malloc(41);
	memset(s_digest,'\0',41);
	if (r_file->file_size != sys_rec->size) {
		hc_test_log(LOG_INFO_LEVEL,NULL,"File size mismatch. Expected (sys_rec) "LL_FORMAT", got (r_file) "LL_FORMAT".\n", sys_rec->size, r_file->file_size);
		res = FALSE;
	}
	digest_to_string(r_file->digest,s_digest);
	if (strcmp(sys_rec->data_digest,s_digest) != 0) {
		hc_test_log(LOG_INFO_LEVEL,NULL,"File sha mismatch. Expected (sys_rec) %s, got (r_file) %s.\n", sys_rec->data_digest,s_digest);
		res = FALSE;
	}
	free(s_digest);
	return res;
}

int compare_rfiles(hc_random_file_t *this_file, hc_random_file_t *that_file) {
	int res = TRUE;
	char * this_digest;
	char * that_digest;
	this_digest = (char *) malloc(41);
	memset(this_digest,'\0',41);
	that_digest = (char *) malloc(41);
	memset(that_digest,'\0',41);
	if (this_file->file_size != that_file->file_size) {
		hc_test_log(LOG_INFO_LEVEL,NULL,"File size mismatch. Expected "LL_FORMAT", got "LL_FORMAT".\n", this_file->file_size, that_file->file_size);
		res = FALSE;
	}
	digest_to_string(this_file->digest,this_digest);
	digest_to_string(that_file->digest,that_digest);
	if (strcmp(this_digest,that_digest) != 0) {
		hc_test_log(LOG_INFO_LEVEL,NULL,"File sha mismatch. Expected %s, got %s.\n", this_digest, that_digest);
		res = FALSE;
	}
	free(this_digest);
	free(that_digest);
	return res;
}

void get_range_overlap(hc_long_t range_start, hc_long_t range_length, 
                         hc_long_t range_prime_start, hc_long_t range_prime_length, 
						hc_long_t *overlap_start, hc_long_t *overlap_len) {
	hc_long_t range_end = range_start + range_length;
	hc_long_t range_prime_end = range_prime_start + range_prime_length;
	if ( (range_start > range_prime_end ) || (range_end < range_prime_start) ){
		*overlap_start = -1;
		*overlap_len = -1;
		return;
	}  
	
	if (range_start < range_prime_start) {
		*overlap_start = range_prime_start;
	} else {
		*overlap_start = range_start;
	}
	
	if (range_end < range_prime_end) {
		*overlap_len = range_end - *overlap_start;
	} else {
		*overlap_len = range_prime_end - *overlap_start;
	}

}

void md_array_to_hashlist(char **names, char **values, long num_of_values, hc_hashlist_t *list) {
	long i;
	for (i = 0; i < num_of_values; i++) {
		hash_put(names[i],values[i],list);
	}
}

void link_list_to_string(hc_llist_t * list, char **value_ptr) {
	hc_llist_node_t * cur_node = list->head;
	size_t strsize = 0;
	char *cur_entry = NULL;
	char *cur_string = NULL;
	int space_needed = FALSE;
	
	*value_ptr = NULL;
	//iterate once to determine size
	while (cur_node != NULL) {
		cur_entry = (char *)cur_node->entry;
		if (cur_entry != NULL) {
			strsize += strlen(cur_entry) + 1; // will need the extra char for the space sep
		}
		cur_node = cur_node->next;
	}
	
	if (strsize <=0 ) {
		return;
	}
	
	cur_string = (char *)malloc(strsize);
	if (cur_string == NULL) {
		return;
	} else {
		memset(cur_string,'\0',strsize);
	}
	
	// iterate again. this time copy the data
	cur_node = list->head;
	while (cur_node != NULL) {
		cur_entry = (char *)cur_node->entry;
		if (cur_entry != NULL) {
			if (space_needed) {
				strcat(cur_string," ");			
			}
			strcat(cur_string,(char *)cur_entry);
		}
		space_needed = TRUE;
		cur_node = cur_node->next;
	}
	
	*value_ptr = cur_string;
}



void
free_array(char** array, int size)
{
  int i = 0;
  for (i = 0; i < size; i++) {
    free(array[i]);
  }
  free(array);
}


char*
create_uniform_string(char val, int size)
{
  int i = 0;
  char *str = (char*) malloc(size * sizeof(char));
  CHECK_MEM(str);
  for (i = 0; i < (size - 1); i++) {
    str[i] = val;
  }
  str[size - 1] = '\0';
  return str;
}

char*
create_random_uniform_string(int size)
{
  int i = 0;
  char val = 0;
  char *str = (char*) malloc(size * sizeof(char));
  CHECK_MEM(str);
  while (!isalpha(val) && !isdigit(val)) {
      val = random() % 128;
  }
  for (i = 0; i < (size - 1); i++) {
    str[i] = val;
  }
  str[size - 1] = '\0';
  return str;
}

static int
getlen(hc_schema_t *schema, char *name) {
    int len;
    hcerr_t err = hc_schema_get_length(schema, name, &len);
    if (err != HCERR_OK) {
      printf("getting length of [%s]: %d\n", name, err);
      exit(1);
    }
    return len;
}

char**
init_str_values(hc_schema_t *schema, char **names, char **def_str_vals,
                int default_size,
                int uniform_size,
                int max_size)
{
  int i = 0;
  int len;

  char** vals = (char**) calloc(sizeof(char*), default_size + uniform_size);
  CHECK_MEM(vals);

  for (i = 0; i < default_size; i++) {
    vals[i] = strdup(def_str_vals[i]);
    CHECK_MEM(vals[i]); 
    len = getlen(schema, names[i]);
    if (strlen(vals[i]) > len)
      vals[i][len] = '\0';
  }

  for (i = default_size; i < default_size + uniform_size; i++) {
    len = getlen(schema, names[i]);
    vals[i] = create_uniform_string('a' + i, len);
    CHECK_MEM(vals[i]);    
  }
  return vals;
}

char**
init_long_values(int size, hc_long_t** long_values)
{
  int i = 0;
  int nb_digits = 0;
  long val;
  long tmp;
  char** vals = (char**) calloc(sizeof(char*), size);
  CHECK_MEM(vals);

  *long_values = (hc_long_t*) calloc(sizeof(hc_long_t), size);
  CHECK_MEM(*long_values);

  for (i = 0; i < size; i++) {
    nb_digits = 0;
    val = random();
    if (val == 0) {
       val = 1;
    }
    tmp = val;
    while (tmp > 0) {
      tmp = tmp /10;
      nb_digits++;
    }
    vals[i] = (char*) malloc(nb_digits + 2);
    CHECK_MEM(vals[i]);
    if (snprintf(vals[i], nb_digits + 1, "%ld", val) >= nb_digits+2) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "calloc too small in init_long_values (%d)",nb_digits+2);
      assert(FALSE);
    }
    (*long_values)[i] = val;
  }
  return vals;
}

char**
init_double_values(int size, hc_double_t** double_values)
{
  int i = 0;
  int dblsize;
  long val1;
  long val2;
  hc_double_t val;
  const int DOUBLE_PRECISION_SIZE = 30;	/* over-estimate */

  char** vals = (char**) calloc(sizeof(char*), size);
  CHECK_MEM(vals);

  *double_values = (hc_double_t*) calloc(sizeof(hc_double_t), size);
  CHECK_MEM(*double_values);

  for (i = 0; i < size; i++) {
    val1 = random();
    val2 = random();
    if (val1 == 0) val1 = 1;
    val = (double)val2 / (double)val1;
    // increase exponent
    if (i % 2) {
      val *= 7498;
    }
    // make it negative.
    if (i %3) {
      val = -val;
    }
    vals[i] = (char*) calloc(DOUBLE_PRECISION_SIZE, 1);
    CHECK_MEM(vals[i]);
    if (snprintf(vals[i], DOUBLE_PRECISION_SIZE, "%.17G", val) == DOUBLE_PRECISION_SIZE) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "init_double_values:"
		  "     DOUBLE_PRECISION_SIZE (%d) is too low!\n"
		  "     Queries will have errors until this is fixed!",
		  DOUBLE_PRECISION_SIZE);
    }
    (*double_values)[i] = val;
  }
  return vals;
}

char**
init_date_values(int size, struct tm **date_values)
{
  int i = 0;
  char** vals = (char**) calloc(sizeof(char*), size);
  CHECK_MEM(vals);

  *date_values = (struct tm *) calloc(sizeof(struct tm), size);
  CHECK_MEM(*date_values);

  for (i = 0; i < size; i++) {
    struct tm *tm = &((*date_values)[i]);
    time_t val = random();

    gmtime_r(&val, tm);
    vals[i] = (char*) malloc(strlen("xxxx-xx-xx") + 1);
    CHECK_MEM(vals[i]);
    sprintf(vals[i], "%d-%02d-%02d", 1900+tm->tm_year, tm->tm_mon+1, 
                                                       tm->tm_mday);
  }
  return vals;
}

char**
init_time_values(int size, time_t **time_values)
{
  int i = 0;
  char** vals = (char**) calloc(sizeof(char*), size);
  CHECK_MEM(vals);

  *time_values = (time_t *) calloc(sizeof(time_t), size);
  CHECK_MEM(*time_values);

  for (i = 0; i < size; i++) {
    time_t val = random() % 86400;
    struct tm tm;

    (*time_values)[i] = val;

    gmtime_r(&val, &tm);

    vals[i] = (char*) malloc(strlen("xx:xx:xx") + 1);
    CHECK_MEM(vals[i]);
    sprintf(vals[i], "%02d:%02d:%02d", tm.tm_hour, tm.tm_min, tm.tm_sec);
  }
  return vals;
}

char**
init_timestamp_values(int size, struct timespec **ts_values)
{
  int i = 0;
  time_t time_test = 0;
  char** vals = (char**) calloc(sizeof(char*), size);
  CHECK_MEM(vals);

  *ts_values = (struct timespec *) calloc(sizeof(struct timespec), size);
  CHECK_MEM(*ts_values);
 
  for (i = 0; i < size; i++) {
    struct tm tm;
    struct timespec *ts = &((*ts_values)[i]);
    /* gmtime_r on Windows has range limitations, so 
       do not generate truly random timestamps. */
    ts->tv_sec = random() & 0x7fffffff;
    ts->tv_nsec = (random() % 1000) * 1000000;
    /*
     * gmtime_t takes a time_t. On some platforms (Windows)
     * the time_t type defined outside timespec is 8 bytes
     * whereas the time_t inside timespec is only 4 bytes.
     * So declare a standalone time_t varb and pass that.
     */
    time_test = ts->tv_sec;
    gmtime_r(&time_test, &tm);
    vals[i] = (char*) malloc(strlen("xxxx-xx-xxTxx:xx:xx.xxxZ") + 1);
    sprintf(vals[i], "%d-%02d-%02dT%02d:%02d:%02d.%03dZ",
          1900+tm.tm_year, 1+tm.tm_mon, tm.tm_mday,
          tm.tm_hour, tm.tm_min, tm.tm_sec, ts->tv_nsec / 1000000);
  }
  return vals;
}

/*
 *  init_binary_values: create an array of binary, where each
 *  binary size+value has 256 bytes in the array, 1st of these bytes
 *  is the count 1..255 in length out of 256.
 *
 *  max_len is the caller specified maximum length of a binary value.
 */
char**
init_binary_values(hc_schema_t *schema, char **names, int size, 
                   unsigned char **bin_values, int max_len)
{
  int i = 0;
  char** vals = (char**) calloc(sizeof(char*), size);
  CHECK_MEM(vals);

  *bin_values = (unsigned char *) calloc(256, size);
  CHECK_MEM(*bin_values);
  for (i = 0; i < size; i++) {
    int index = i * 256;
    int len = getlen(schema, names[i]);
    int j;

    if (len > 255)
      len = 255;
    
    if (len > max_len)
      len = max_len;
    
    // set the count for the number of bytes for this value
    (*bin_values)[index] = len;
    index++;
    vals[i] = (char*) malloc(2*len + 1);
    for (j=0; j<len; j++) {
      unsigned char c = random() % 256;
      (*bin_values)[index++] = c;
      sprintf(vals[i] + 2 * j, "%02x", c);
    }
  }
  return vals;
}


/*
 * Build the string used to query (using long param)
 *
 */

char*
get_query_string(char** names, char** values, int nb_md, hc_type_t type)
{
  int i;
  int size = 0;
  char *query = NULL;
  char *andstr = "";
  char *tocp;
	  
  // nb_md can be zero, which is the same as 1

  i = 0;
  do {
    int namesize = strlen(names[i]);
    int valuesize = strlen(values[i]);
    int itemsize = namesize + valuesize + 5;
    int querysize;

    if (type == HC_DOUBLE_TYPE) {
      itemsize += valuesize + valuesize + 50; //at least enough for abs(xxx - val) < abs(val * 1.0E14)
    } else if (type == HC_STRING_TYPE || type == HC_CHAR_TYPE) {
      itemsize += 2; // for the single-quote ' chars
    } else if (type == HC_DATE_TYPE) {
      itemsize += strlen("{date ''}");
    } else if (type == HC_TIME_TYPE) {
      itemsize += strlen("{time ''}");
    } else if (type == HC_TIMESTAMP_TYPE) {
      itemsize += strlen("{timestamp ''}");
    } else if (type == HC_BINARY_TYPE) {
      itemsize += strlen("{binary ''}");
    }
    if (i > 0) {
      itemsize += 5;
    }
    size += itemsize;
  } while  (++i < nb_md);


  query = (char*) malloc(size);
  CHECK_MEM(query);
  tocp = query;

  i = 0;
  do {
    if (i > 0) {
      andstr = " AND ";
    }
    /* FIXME:  Use {type 'value'} literals here for non-standard types */
    if (type == HC_STRING_TYPE || type == HC_CHAR_TYPE) {
      tocp += sprintf(tocp,"%s\"%s\"='%s'",andstr, names[i], values[i]);
    } else if (type == HC_DATE_TYPE) {
      tocp += sprintf(tocp,"%s\"%s\"={date '%s'}",andstr, names[i], values[i]);
    } else if (type == HC_TIME_TYPE) {
      tocp += sprintf(tocp,"%s\"%s\"={time '%s'}",andstr, names[i], values[i]);
    } else if (type == HC_TIMESTAMP_TYPE) {
      tocp += sprintf(tocp,"%s\"%s\"={timestamp '%s'}",andstr, 
                                                       names[i], values[i]);
    } else if (type == HC_BINARY_TYPE) {
      tocp += sprintf(tocp,"%s\"%s\"={binary '%s'}",andstr, 
                                                       names[i], values[i]);
    } else {
      tocp += sprintf(tocp,"%s\"%s\"=%s",andstr, names[i], values[i]);
    }
    assert (tocp < query+size);
  } while  (++i < nb_md);
  return query;
}


/*
 * Check the list of oids stored are part of the query
 */
int
check_results_query(hc_oid* stored_oids,
                   hc_oid* query_oids,
                   int nb_stored_oids,
                   int nb_query_oids)
{
  int i, j;
  int found = 1;

  for (i = 0; i < nb_stored_oids; i++) {
    found = 0;
    for (j = 0; j < nb_query_oids; j++) {
      if (strcmp(stored_oids[i], query_oids[j]) == 0) {
        found = 1;
        break;
      }
    }
    if (!found) {
      return -1;
    }
  }
  return 0;
}

int
check_results_query_result_set(hc_oid* stored_oids,
			       int nb_stored_oids,
			       hc_query_result_set_t *qrs)
{
	int i, j;
	int found = 0;
	hc_oid oid;
	int done = 0;
	hcerr_t res;
        int n_results = 0;
    
    do {
	res = hc_qrs_next_ez(qrs, &oid, NULL, &done);
	if (res != HCERR_OK) {
            printf("hc_qrs_next_ez: %s\n", hc_decode_hcerr(res));
	    return(-1);
	}
        
        if (!done) {
            n_results++;
            for (j = 0; j<nb_stored_oids; j++) {
                if (strcmp(stored_oids[j], oid) == 0) {
                    found = 1;
                    break;
                }
            }
        }
    } while ((!done) && (found == 0));
    
    if (!found) {
        printf("oid[s] not found (in %d results):\n", n_results);
        for (j = 0; j<nb_stored_oids; j++)
            printf("\t%s\n", stored_oids[j]);
    }

    return(found ? 0 : -1);
}

int 
check_resultset_qry (hc_oid *stored_oids, 
		     hc_hashlist_t *qrs_oids,
		     long nb_stored_oids) 
{
	long i;
	
	for (i = 0; i < nb_stored_oids; i++) {
		if (hashlist_contains_key(stored_oids[i],qrs_oids) !=TRUE) {	
  			return -1;
		}
	}
	return 0;
	
}


/*
 * make sure there is at least nb_entries of this type in the schema,
 * otherwise we loop for ever...
 */
char**
get_random_entry_from_schema(hc_schema_t* schema, hc_type_t type, int nb_md,
    char* ns)
{
  hc_long_t count;
  int found = 0;
  int *used_entries;
  hc_long_t num_used;
  char** res;
  char *sc_name;
  hc_type_t sc_type;
  hcerr_t hcerr;
  char* name_space = NULL;
  
  if (nb_md < 1) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "get_random_entry_from_schema - "
        "Invalid number of entries requested: %d\n", nb_md);
    exit(1);
  }
  hc_schema_get_count(schema,&count);
  num_used = 0;
  used_entries = (int*) calloc(sizeof(int), count);
  res = (char**) malloc(sizeof(char*) * nb_md);
  CHECK_MEM(used_entries);
  CHECK_MEM(res);
  if (ns != NULL) {
    name_space = (char*) calloc(sizeof(char), strlen(ns) + 2);
    sprintf(name_space,"%s.", ns);
  }

  do {
    int index = random() % (int)count;
    hcerr = hc_schema_get_type_at_index(schema, index, &sc_name, &sc_type);
    if (hcerr != HCERR_OK) {
        printf("get_random_entry_from_schema - ERROR %d\n", hcerr);
        exit(1);
    }
    // skip if already used or checked 
    if (used_entries[index] == 1) {
        continue;
    }
    
    // set this entry as used
    used_entries[index] = 1;
    num_used = num_used + 1;

    // skip "system" metadata; nonqueryable fields
    if ((strncmp(sc_name, "system.", strlen("system.")) == 0) ||
        (strncmp(sc_name, "filesystem.", strlen("filesystem.")) == 0) ||
        (strncmp(sc_name, "nonqueryable.", strlen("nonqueryable.")) == 0)) {
      continue;
    }
    // Check for field in namespace
    if (name_space != NULL &&
        (strncmp(sc_name, name_space, strlen(name_space)) != 0)) {
        continue;
    }
    if (sc_type == type) {
      res[found] = strdup(sc_name);
      CHECK_MEM(res[found]);
      used_entries[index] = 1;
      num_used = num_used + 1;
      found++;
    }
  } while(found < nb_md && num_used < count);
  
  if (name_space != NULL)
    free(name_space);
  
  free(used_entries);
  
  // Error if can't find requested number of items of specified type
  if (found < nb_md ) {
    if (ns != NULL) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "get_random_entry_from_schema - "
        "Could not find %d entries of hc_type_t: %d in namespace: %s"
        ". Check the correct schema is loaded.\n",  nb_md, type, ns);
    } else {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "get_random_entry_from_schema - "
        "Could not find %d entries of hc_type_t: %d"
        ". Check the correct schema is loaded.\n",  nb_md, type);       
    }

    exit(1);
  }

  return res;
}

/*
 * Get the list of metadata fields with types for the specified
 * namespace
 * returns the number of metadata fields found. Does not include
 * non-queryable fields.
 */
int
get_random_ns_entries_from_schema(hc_schema_t* schema, char* ns,
        hc_schema_entry_t** md_list, int max_fields, int queryable)
{
  hc_long_t count;
  int i, num_fields, j, k = 0;
  int found = 0;
  int *used_entries;
  int used_count = 0;
  int num_left = 0;
  hc_schema_entry_t *entry, *tmp_entry;
  char *sc_name;
  hc_type_t sc_type;
  char* default_ns = "perf_types";
  char* name_space = NULL;
  int length;
  hcerr_t hcerr;
  
  entry = NULL;
  hcerr = hc_schema_get_count(schema, &count);
  if (hcerr != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "cannot retrieve schema, abort...\n");
        //log_session_error_info(session, hcerr);
        exit(num_fields);
  }
  num_fields = (int) count;  
  if (ns == NULL || strlen(ns) == 0) {
      ns = default_ns;
  }
    
  name_space = (char*) calloc(sizeof(char), strlen(ns) + 2);
  sprintf(name_space,"%s.", ns);
  used_entries = (int*) calloc(sizeof(int), num_fields);
  for (i = 0; i < num_fields; i++) {
      used_entries[i] = i;
  }
  CHECK_MEM(used_entries);  
  if (max_fields > num_fields) {
      max_fields = num_fields;
  }
  num_left = num_fields;
  // Get all the fields in the specified namespace
  while (found < max_fields && used_count < num_fields && num_left > 0) {
 
    k = random() % num_left;
    i = used_entries[k];
    num_left--;
    for (j = k; j < num_left; j++) {
        used_entries[j] = used_entries[j+1];
    }
    hc_schema_get_type_at_index(schema, i, &sc_name, &sc_type);
    if (hcerr != HCERR_OK) {
        printf("get_ns_entries_from_schema_ran - ERROR %d\n", hcerr);
        return(0);        
    }
    
    // skip over anything not in the defined namespace
    if (strncmp(sc_name, name_space, strlen(name_space)) == 0) {
        // skip nonqueryable fields if requested
        if (queryable != 0 &&
            (strncmp(sc_name, "nonqueryable.", strlen("nonqueryable.")) == 0)) {
          continue;
        }

        tmp_entry = (hc_schema_entry_t*) calloc(sizeof(hc_schema_entry_t), 1);
        if (found != 0) {
            entry->next = tmp_entry;
        } else {
            *md_list = tmp_entry;
        }
        entry = tmp_entry;
        entry->name = strdup(sc_name);
        entry->type = sc_type;
        entry->next = NULL;
        length = 0;
        if (sc_type == HC_STRING_TYPE || sc_type == HC_CHAR_TYPE ||
            sc_type == HC_BINARY_TYPE) {
            hc_schema_get_length(schema, sc_name, &length);
        }
        entry->size = length;
        found++; 
    }
  }
  if (name_space != NULL)
      free(name_space);
  return (found);
}

/*
 * Frees the linked list of metadata fields entries.
 */
void
free_metadata_fields_list(hc_schema_entry_t* md_list) {
    hc_schema_entry_t *entry = NULL;
    entry = md_list;
    while (md_list != NULL) {
        char *name = md_list->name;
        if (name != NULL)
            free(name);
        entry = md_list;
        md_list = md_list->next;
        free(entry);
    }
}
int 
store_file_with_md(char *host, 
		   int port, 
		   hc_long_t file_size, 
		   hc_system_record_t *sys_rec)  {

	hcerr_t result;
	hc_random_file_t * r_file = NULL;
	hcoa_store_handle_t handle;
	size_t buff_len = 4096;
	int32_t response_code;
	hc_session_t *session = NULL;
	hc_archive_t *archive = NULL;
	hc_nvr_t *nvr = NULL;

	int infile = -1;
	char * md_names[] = {"third","fourth"};
	char * md_values[] = {"avalue","bvalue"};
	r_file = create_random_file(file_size);	

	result = hc_init(malloc, free, realloc);
	if (result != HCERR_OK && result != HCERR_ALREADY_INITED) {
		return FALSE;
	}
	if (hc_session_create_ez (host, port, &session) != HCERR_OK) {
		return FALSE;
	}
	if (hc_session_get_archive(session, &archive) != HCERR_OK) {
		return FALSE;
	}
	if(hc_nvr_create_from_string_arrays(session, &nvr, md_names,  md_values, 2) != HCERR_OK) {
		return FALSE;
	}
	
	if (hc_store_both_ez (session, read_from_random_data_generator,(void*)r_file, nvr,sys_rec) != HCERR_OK) {
		return FALSE;
	}	
	free_r_file(r_file);
	hc_nvr_free(nvr);
	hc_session_free(session);
   	hc_cleanup();
	return TRUE;
}

int 
store_tmp_data_file(char *host, 
			int port, 
			hc_long_t file_size, 
			hc_system_record_t *sys_rec) 
{
	hcerr_t result;
	hc_random_file_t * r_file;
	hcoa_store_handle_t handle;
	size_t buff_len = 4096;
	int32_t response_code;
	hc_session_t *session;
	hc_archive_t *archive;
	
	r_file = create_random_file(file_size);	

	result = hc_init(malloc, free, realloc);
	if (result != HCERR_OK && result != HCERR_ALREADY_INITED) {
                printf("hc_init: %s\n", hc_decode_hcerr(result));
		return FALSE;
	}
        result = hc_session_create_ez (host, port, &session);
	if (result != HCERR_OK) {
                printf("hc_init: %s\n", hc_decode_hcerr(result));
		return FALSE;
	}
        result = hc_store_both_ez (session, &read_from_random_data_generator,
                                   r_file, NULL, sys_rec);
	if (result != HCERR_OK) {
                hc_test_log(LOG_ERROR_LEVEL, NULL, "store_tmp_data_file: %s\n",
                               hc_decode_hcerr(result));
		return FALSE;
	}	
	free_r_file(r_file);
	hc_session_free(session);
   	hc_cleanup();

	return TRUE;
}

int check_valid_oid (hc_oid oid) {
  int i = 0;

  if (oid == NULL) {
    return FALSE;
  }

  if (strcmp(HC_INVALID_OID, oid) == 0){
    /* This is OK; query and retrieve schema use */
    /* this value because there is no oid involved */
    return TRUE;
  }
  for (i=0; i<OID_HEX_CHRSTR_LENGTH-1; i++){
    if (!(oid[i] >= '0' && oid[i] <= '9') &&
        !(oid[i] >= 'a' && oid[i] <= 'f') &&
        !(oid[i] >= 'A' && oid[i] <= 'F')){
      return FALSE;
    }
  }
  return TRUE;
}

static int tmp_count = 0;
static char tmp_file[1024];

char *hc_tmpnam() {
  char *tmp_dirs[] = {
    // /tmp doesn't have much space on our linux test clnts,
    // so prefer /mnt/test if there
    "/mnt/test", "/tmp", "C:/WINDOWS/Temp"
    };
  int i;

  for (i=0; i<3; i++) {
    struct  stat buf;

    if (stat(tmp_dirs[i], &buf) == -1)
      continue;

    if (buf.st_mode & S_IFDIR) {
      sprintf(tmp_file, "%s/hc_tmpnam_%d_%d", tmp_dirs[i], getpid(), 
              tmp_count++);
      //printf("hc_tmpnam: %s\n", tmp_file);
      return tmp_file;
    }
  }
  fprintf(stderr, "hc_tmpnam: can't find a tmp dir - tried:\n");
  for (i=0; i<3; i++)
    fprintf(stderr, "\t%s\n", tmp_dirs[i]);
  fprintf(stderr, "exiting\n");
  exit(1);
}

// THE FUNCTIONS LISTED BELOW ARE CURRENTLY NOT NEEDED

hcerr_t do_retrieve_to_r_file(hc_archive_t *archive, void *handle) {
  hcerr_t res;
  int done = FALSE;
  int selectres;
  
  res = HCERR_OK;
  while(done == FALSE) {  
    
    res = hcoa_io_worker(handle,&done);
    if (res != HCERR_OK) {
      break;
    }
    if (!done) {
      selectres = hcoa_select_all(archive);
    }
  }
  return res;
} 



hcerr_t do_store_from_generator(hc_archive_t *archive, hc_random_file_t *r_file, int store_type, char * buf, size_t buf_len, void *handle) {
  char* storeinptr = buf;
  char* storeoutptr = buf;
  long bytesread = 0;
  hc_long_t bytesstored = 0;
  hcerr_t res = -1;
  int selectres = -1;
  fd_set read_fd_set, write_fd_set;
  int max_read_fd, max_write_fd;
  struct timeval timeout;
  int iopending = 0;
  int max = -1;
  int readfinished = 0;
  int storefinished = 0;
  int firsttime = 1;

  timeout.tv_sec = 0;
  timeout.tv_usec = 100;

/*   while(!storefinished) {    */
/*     //read data */
/*     if(!readfinished && (buf_len - (storeinptr - buf)) > 0) { */

/* 		while ((buf_len - (storeinptr - buf)) > 0) { */
/* 			read_from_random_data_generator (r_file,buf,(buf_len-(storeinptr-buf)),&bytesread); */
/* 			if (bytesread < 0) */
/* 				break; */
/* 			storeinptr += bytesread; */
/* 		} //while */

/* 		if(bytesread <= 0) { */
/* 			readfinished = 1; */
/* 		} // bytesread <=0 */

/*     } // readfinished */

/*     //register fd w/select */
    
/*     hcoa_get_read_fd_set(archive,&read_fd_set, &max_read_fd); */
/*     hcoa_get_write_fd_set(archive, &write_fd_set, &max_write_fd);  */
 
/*     max = max_read_fd; */
/*     if(max_write_fd > max_read_fd) { */
/*       max = max_write_fd; */
/*     } */

/*     // select on IO */
/*     while(!firsttime && !iopending) { // this app only cares about hc io */

/*       while((selectres = select(max+1, &read_fd_set, &write_fd_set, NULL, &timeout))==0) { */
/* 	; */
/*       } */

/*       if(selectres > 0) { */
/* 		hcoa_store_io_pending(handle, &read_fd_set, &write_fd_set, &iopending); */
/*       } */

/*     }     */

/*     // write what is in our buffer, or 0 if there is nothing.  At the same time read anything coming in. - TODO handle errors  */
/*     if (store_type == STORE_OP_STORE_DATA) { */
/* 		res = hcoa_store(handle, storeoutptr, (hc_long_t) (storeinptr-storeoutptr), &bytesstored); */
/*     } else if (store_type == STORE_OP_STORE_NVDATA) { */
/* 		res = hc_store_data( (hc_store_metadata_handle_t *)handle, storeoutptr, (hc_long_t) (storeinptr-storeoutptr), &bytesstored); */
/*     } */

/*     if (res != HCERR_OK && res != HCERR_CAN_CALL_AGAIN) { */
/*       		printf("RESULT CODE  MISMATCH \n"); */
/* 	return res; */
/*     } */

/*     // once we stored once and primed the pump, wait for select till next  */
/*     if(bytesstored > 0) { */
/*       firsttime = 0; */
/*     } */


/*     // hcoa_store indicates we are done writing with -1  */
/*     storefinished = (bytesstored == -1); */
    
/*     if(!storefinished) { */
/*       // advance ptr by anything that was stored  */
/*       storeoutptr += bytesstored; */
      
/*       // reset buffer if we've written it all out so we can read more in */
/*       if((storeoutptr - buf) == buf_len) { */
/* 		storeinptr = storeoutptr = buf; */
/*       } */
/*     } */
/*   }// store finished */
/*   return res; */

  return 0;
} 


/* Need to fix API first... 

int test_store(qa_read_from_data_source data_source_reader, void *stream, hcoa_store_handle_t *handle, hc_system_record_t *sys_rec) {
  char storebuf[MSG_BUFFER_SIZE];
  char* storeinptr = storebuf;
  char* storeoutptr = storebuf;
  int bytesread = 0;
  hc_long_t bytesstored = 0;
  hcerr_t res = -1;
  int selectres = -1;
  fd_set read_fd_set, write_fd_set;
  int max_read_fd, max_write_fd;
  struct timeval timeout;
  int iopending = 0;
  int max = -1;
  char *errstr;
  int errstrlen = MSG_BUFFER_SIZE;     
  int readfinished = 0;
  int storefinished = 0;
  int firsttime = 1;
  hc_long_t last_committed = 0;
  hc_long_t last_last_committed = 0;
  long response_code = 0;

  timeout.tv_sec = 0;
  timeout.tv_usec = 100;

     
  if ((errstr = (char *)malloc(MSG_BUFFER_SIZE)) == NULL) {
      printf("WARNING: Not able to malloc enough space for error string. I asked for %d bytes but got nada!\n", MSG_BUFFER_SIZE);
  } // errstr
  memset(errstr,'\0',MSG_BUFFER_SIZE);

  while(!storefinished) {   


  if(!readfinished && (MSG_BUFFER_SIZE - (storeinptr - storebuf)) > 0) {
      while( ((MSG_BUFFER_SIZE - (storeinptr - storebuf)) > 0) &&
	     ( (bytesread = (*data_source_reader) (stream, storeinptr, (MSG_BUFFER_SIZE- (storeinptr-storebuf)) )) > 0)) {
	storeinptr += bytesread;
      } //while
      if(bytesread <= 0) {
	readfinished = 1;
      } // bytesread <=0
    } // readfinished

    // select till there's pending data to read, and/or write channels are ready 
    //TODO: handle errors 
    hcoa_get_read_fd_set(&read_fd_set, &max_read_fd);
    hcoa_get_write_fd_set(&write_fd_set, &max_write_fd); 
    max = max_read_fd;
    if(max_write_fd > max_read_fd) {
      max = max_write_fd;
    }
    
    while(!firsttime && !iopending) { // this app only cares about hc io
      while((selectres = select(max+1, &read_fd_set, &write_fd_set, NULL, &timeout))==0) {
	;
      }
      if(selectres > 0) {
	hcoa_store_io_pending(handle, &read_fd_set, &write_fd_set, &iopending);
      }
    }    

    // write what is in our buffer, or 0 if there is nothing.  At the same time read anything coming in. - TODO handle errors 
    res = hcoa_store(handle, storeoutptr, (hc_long_t) (storeinptr-storeoutptr), &bytesstored);
    printf("RES: is %s\n", hc_decode_hcerr(res));

    // check the last committed byte (for curiosity only for now 
    hcoa_get_last_committed_offset(handle, &last_committed);
    if(last_committed > last_last_committed) {
      printf("last committed byte: "LL_FORMAT"\n", last_committed);
      last_last_committed = last_committed;
    }
    
    // once we stored once and primed the pump, wait for select till next 
    if(bytesstored > 0) {
      firsttime = 0;
    }

    printf("BYTESTORED "LL_FORMAT"\n",bytesstored);
    
    // hcoa_store indicates we are done writing with -1 
    storefinished = (bytesstored == -1);
    
    if(!storefinished) {
      // advance ptr by anything that was stored 
      storeoutptr += bytesstored;
      
      // reset buffer if we've written it all out so we can read more in
      if((storeoutptr - storebuf) == MSG_BUFFER_SIZE) {
	storeinptr = storeoutptr = storebuf;
      }
    }
   
  }
  // close the handle and print out the system record fields 
  res = hcoa_store_close(handle, &response_code, errstr, errstrlen, sys_rec);
  printf("ERROR: %s\n", errstr);
  return res;
} 

*/
