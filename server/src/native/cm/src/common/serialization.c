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



#include <malloc.h>
#include <trace.h>
#include <string.h>
#include <netinet/in.h>

#include "serialization.h"

/*
 * Type definitions
 */

typedef struct {
    mb_id_t mailbox;

    unsigned char *buffer;
    size_t length;
    size_t current_pos;
} hc_serial_t;

#define UUID_SIZE 16

/*
 * Private routines
 */

/*
 * API implementation
 */

hc_serialization_t *
hc_serialization_open(mb_id_t mailbox,
                      short mailbox_type,
                      short version,
                      int first_write)
{
    hc_serial_t *serial = NULL;
    short sequence_number;
    union {
        unsigned char c[2];
        uint16_t s;
    } sequence_union;
    mb_error_t err;

    serial = (hc_serial_t*)malloc(sizeof(hc_serial_t));
    if (!serial) {
        return(NULL);
    }

    serial->mailbox = mailbox;
    serial->length = mb_len(mailbox);
    serial->buffer = (unsigned char*)malloc(serial->length);
    if (!serial->buffer) {
        free(serial);
        return(NULL);
    }
    serial->current_pos = 4;

    /* Format the mailbox header */
    if (first_write) {
        sequence_number = 1;
    } else {
        err = mb_read(mailbox, sequence_union.c, 8, 2);
        if (err != MB_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Failed to retrieve the current sequence number");
            hc_serialization_abort((hc_serialization_t*)serial);
            return(NULL);
        }

        sequence_number = ntohs(sequence_union.s);
        if (sequence_number == 0x7FFF) {
            sequence_number = 1;
        } else {
            sequence_number++;
        }
    }
    
    (int)hc_serialization_write_short((hc_serialization_t*)serial, version);
    (int)hc_serialization_write_short((hc_serialization_t*)serial, mailbox_type);
    (int)hc_serialization_write_short((hc_serialization_t*)serial, sequence_number);

    return((hc_serialization_t*)serial);
}

int
hc_serialization_write_short(hc_serialization_t *handle,
                             short value)
{
    hc_serial_t *serial = (hc_serial_t*)handle;
    union {
        uint16_t s;
        unsigned char c[2];
    } val;
    int i;

    if (serial->current_pos+2 > serial->length) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Not enough space in the mailbox [hc_serialization_write_short]");
        return(1);
    }

    val.s = htons(value);
    
    for (i=0; i<2; i++) {
        serial->buffer[serial->current_pos] = val.c[i];
        serial->current_pos++;
    }

    return(0);
}

int
hc_serialization_write_int(hc_serialization_t *handle,
                           int value)
{
    hc_serial_t *serial = (hc_serial_t*)handle;
    union {
        uint32_t i;
        unsigned char c[4];
    } val;
    int i;

    if (serial->current_pos+4 > serial->length) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Not enough space in the mailbox [hc_serialization_write_int]");
        return(1);
    }

    val.i = htonl(value);
    
    for (i=0; i<4; i++) {
        serial->buffer[serial->current_pos] = val.c[i];
        serial->current_pos++;
    }

    return(0);
}

int
hc_serialization_write_float(hc_serialization_t *handle,
                             float value)
{
    union {
        float    f;
        uint32_t i;
    } val;

    val.f = value;
    return hc_serialization_write_int(handle, val.i);
}

int
hc_serialization_write_uuid(hc_serialization_t *handle,
                            unsigned char uuid[UUID_SIZE])
{
    hc_serial_t *serial = (hc_serial_t*)handle;
    unsigned int i;

    if (serial->current_pos+(UUID_SIZE+1) > serial->length) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Not enough space in the mailbox \
                 [hc_serialization_write_uuid]");
        return(1);
    }

    /* uuid size comes first */
    serial->buffer[serial->current_pos++] = UUID_SIZE;

    for (i=0; i<UUID_SIZE; i++) {
        serial->buffer[serial->current_pos] = uuid[i];
        serial->current_pos++;
    }

    return(0);
}

int
hc_serialization_write_string(hc_serialization_t *handle,
                              char *string)
{
    hc_serial_t *serial = (hc_serial_t*)handle;
    union {
        uint16_t i;
        unsigned char c[2];
    } length;
    int string_length, i;

    string_length = strlen(string);
    if (serial->current_pos+string_length+2 > serial->length) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Not enough space in the mailbox [hc_serialization_write_string]");
        return(1);
    }

    length.i = htons(string_length);
    
    serial->buffer[serial->current_pos] = length.c[0];
    serial->buffer[serial->current_pos+1] = length.c[1];
    serial->current_pos += 2;

    for (i=0; i<string_length; i++) {
        serial->buffer[serial->current_pos] = (unsigned char)string[i];
        serial->current_pos++;
    }

    return(0);
}

void
hc_serialization_commit(hc_serialization_t *handle)
{
    hc_serial_t *serial = (hc_serial_t*)handle;
    mb_error_t err;
    size_t length = serial->current_pos;

    serial->current_pos = 0;
    (int)hc_serialization_write_int(handle, length);

    /* Do the actual write to the mailbox */
    
    err = mb_write(serial->mailbox,
                   (char*)serial->buffer,
                   0,
                   length);
    if (err != MB_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Couldn't write the datas in the mailbox");
    }

    free(serial->buffer);
    serial->buffer = NULL;
    free(serial);
}

void
hc_serialization_abort(hc_serialization_t *handle)
{
    hc_serial_t *serial = (hc_serial_t*)handle;

    free(serial->buffer);
    serial->buffer = NULL;
    free(serial);
}
