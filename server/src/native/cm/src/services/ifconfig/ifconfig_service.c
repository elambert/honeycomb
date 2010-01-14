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



#include <unistd.h>
#include <stdio.h>
#include <libgen.h>
#include <mbox.h>
#include <stdlib.h>
#include <string.h>

#define HEARTBEAT_TIMEOUT 1
#define BUFFER_SIZE 256

#define SCRIPT_FILE "bin/ifconfig_script.sh"

static char *_route = "";
static char *_vip = NULL;
static char *_network = NULL;
static char *_subnet = NULL;
static char *_gateway = NULL;
static int _running = 1;

static void
mb_callback(mb_id_t mb_id,
            mb_action_t action)
{
    int err = 0;
    mb_state_t new_state = SRV_DISABLED;
    int execute = 0;
    char buffer[BUFFER_SIZE];
    
    switch (action) {
    case ACT_INIT:
        new_state = SRV_READY;
        break;

    case ACT_STOP:
        snprintf(buffer, BUFFER_SIZE,
                 "%s/%s %s %s %s %s stop %s",
                 PREFIX,
                 SCRIPT_FILE,
                 _vip,
                 _network,
                 _subnet,
                 _gateway,
                 _route);
        execute = 1;

        new_state = SRV_READY;
        break;

    case ACT_START:
        snprintf(buffer, BUFFER_SIZE,
                 "%s/%s %s %s %s %s start %s",
                 PREFIX,
                 SCRIPT_FILE,
                 _vip,
                 _network,
                 _subnet,
                 _gateway,
                 _route);
        execute = 1;
        new_state = SRV_RUNNING;
        break;

    case ACT_DESTROY:
        new_state = SRV_DISABLED;
        break;

    default:
        fprintf(stderr, "Bad action in mb_callback\n");
        err = 1;
    }

    if (execute) {
        err = system(buffer);
    }

    if (err) {
        new_state = SRV_DISABLED;
    }

    mb_setstate(mb_id, new_state);
}

/*
 * ifconfig_service takes the following parameters (in order) :
 * - vip
 * - network
 * - subnet
 * - gateway
 * - noroute (optionnal)
 */

int
main(int argc,
     char *argv[])
{
    mb_id_t mb_id;
    mb_action_t action;

    if (argc < 5) {
        fprintf(stderr, "Incorrect number of parameter [%d]\n", argc);
        return(1);
    }

    _vip = argv[1];
    _network = argv[2];
    _subnet = argv[3];
    _gateway = argv[4];

    if ((argc == 6)
        && (!strcmp(argv[5], "noroute"))) {
        _route = "noroute";
    }

    mb_id = mb_init(getenv("HC_MAILBOX"), NULL);
    if (mb_id == MB_INVALID_ID) {
        fprintf(stderr, "mb_open failed\n");
        return(1);
    }

    _running = 1;
    while (_running) {
        mb_hbt(mb_id, &action);
        if (action != ACT_VOID) {
            mb_callback(mb_id, action);
        }
        sleep(HEARTBEAT_TIMEOUT);
    }

    printf("Exiting ...\n");

    return(0);
}
