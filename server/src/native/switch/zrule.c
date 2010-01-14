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



/*
 * Client application which uses libztmd2.so send commands to ztmd
 * running on a ZNYX switch.
 */

#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <strings.h>

/* For basename(3c) */
#include <libgen.h>

#include "libztmd.h"

#define HASH 0
#define PTYPE "ip"
#define PORT 80
#define SWPORT 0
#define VIP "127.0.0.2"
#define BIND_ADDR "127.0.0.1"

#define JUNK 0xace

static char id[] = "$Id: zrule.c 10855 2007-05-19 02:54:08Z bberndt $";

extern
int znl2_send_rule(char* action, char* type, int multicast_port, char* bind,
                   unsigned int src_mask, unsigned int src_value,
                   unsigned int dest_mask, unsigned int dest_value,
                   unsigned int srcport_mask, unsigned int srcport_value,
                   unsigned int destport_mask, unsigned int destport_value,
                   int swport, const char* progname, int debug, int dont_send);

static 
void parse_mask(const char* s, unsigned int* pmask, unsigned int* pvalue);

static char* progname;

static
void usage() 
{
    fprintf(stderr, "Usage: %s <command> [-d|-v] -b <bind address> "
            "-s <switch port>\n    [-t <packet type>] [-src <value/mask>] "
            "[-dest <value/mask>]\n    [-srcport <value/mask>] "
            "[-destport <value/mask>]\n", progname);
    fprintf(stderr, "Where:\n");
    fprintf(stderr, "       command: add | delete\n");
    fprintf(stderr, "       -v (or -d) Increase verbosity\n");
    fprintf(stderr, "       -p  Multicast port to use, default is 2345\n");
    fprintf(stderr, "       -b Bind multicast socket to <bind address>\n");
    fprintf(stderr, "       -s Direct traffic to switch port <switch port>\n");
    fprintf(stderr, "          (port %d is the switch CPU)\n", CPU_PORT);
    fprintf(stderr, "       -X Drop the matching packets\n");
    fprintf(stderr, "       -A Accept the matching packets (overrides DROP)\n");
    fprintf(stderr, "       -t Packets of type <packet type> (default: %s)\n",
            PTYPE);
    fprintf(stderr, "       -src      value/mask for src address\n");
    fprintf(stderr, "       -dest     value/mask for dest address\n");
    fprintf(stderr, "       -srcport  value/mask for src port\n");
    fprintf(stderr, "       -destport value/mask for dest port\n");
    fprintf(stderr, "If a mask is not specified, it defaults to 0xffffffff.\n");
    fprintf(stderr, "Examples:\n");
    fprintf(stderr, "%s add -src 0x7/11 -dest 10.1.252.96 -destport 80"
            " -s 11\n", progname);
    fprintf(stderr, "    - if the last three bits of src IP addr are equal"
            "11 (decimal),\n      and it's going to 10.1.252.96:80, send it "
            "to port 11 of the switch.\n");
    fprintf(stderr, "%s add -destport 1/1 -s 13\n", progname);
    fprintf(stderr, "    - if the destination port is odd, send it to "
            "switch port 13.\n");
    fprintf(stderr, "%s add -src 10.123.0.0/16 -X\n", progname);
    fprintf(stderr, "    - drop all packets coming from the 10.123 class B network\n");

    fputs("\n", stderr);
}

int main(int argc, char *argv[]) 
{
    int optind;
    int debug = 0, dont_send = 0;

    unsigned int src_mask = 0, src_value = 0;
    unsigned int dest_mask = 0, dest_value = 0;
    unsigned int srcport_mask = 0, srcport_value = 0;
    unsigned int destport_mask = 0, destport_value = 0;
    short swport = JUNK;
    unsigned char Xflag = 0, Aflag = 0;
    char* bind_addr = 0;
    int multicast_port = -1;
    char* type = 0;

    /* Get the action */
    char *action = argv[1];

    progname = basename(argv[0]);

    /* Check the arguments */
    if (argc < 4 || action == NULL) {
        fprintf(stderr, "\nThe action, -b and -s are required.\n");
        usage();
        exit(1);
    }
    if (!(strcmp(action, "add") == 0 || strcmp(action, "delete") == 0)) {
        fprintf(stderr, "\nUnknown action \"%s\"\n", action);
        usage();
        exit(1);
    }

    optind = 2;                 /* We've handled two arguments */
    while (optind < argc) {
        char* option = argv[optind];
        char* optarg = argv[optind + 1];
        optind++;

        if (*option != '-') {
            fprintf(stderr, "\nExpected option instead of \"%s\"\n", option);
            usage();
            exit(1);
        }
        if (strcmp(option, "-d") == 0 || strcmp(option, "-v") == 0) {
            debug++;
            continue;
        }
        else if (strcmp(option, "-X") == 0)
            Xflag++;
        else if (strcmp(option, "-n") == 0)
            dont_send++;
        else if (strcmp(option, "-A") == 0)
            Aflag++;
        else {
            optind++;           /* All others take an argument */
            if (*optarg == 0) {
                fprintf(stderr, "\nOption \"%s\" requires an argument.\n",
                        option);
                usage();
                exit(1);
            }


            if (strcmp(option, "-b") == 0)
                bind_addr = optarg;
            else if (strcmp(option, "-p") == 0) 
                multicast_port = atoi(optarg);
            else if (strcmp(option, "-s") == 0)
                swport = strtoul(optarg, 0, 0);
            else if (strcmp(option, "-t") == 0)
                type = optarg;
            else if (strcmp(option, "-src") == 0)
                parse_mask(optarg, &src_mask, &src_value);
            else if (strcmp(option, "-dest") == 0)
                parse_mask(optarg, &dest_mask, &dest_value);
            else if (strcmp(option, "-srcport") == 0)
                parse_mask(optarg, &srcport_mask, &srcport_value);
            else if (strcmp(option, "-destport") == 0)
                parse_mask(optarg, &destport_mask, &destport_value);
            else
                fprintf(stderr, "\nOption \"%s\" unrecognized, skipping.\n",
                        option);
        }
    }

    if (!Aflag && !Xflag && (swport == JUNK || swport < 0)) {
        fprintf(stderr, "\nSwitch port must be specified and non-negative.\n");
        usage();
        exit(1);
    }
    if (swport >= 32) {
        fprintf(stderr, "Switch port must be between 0 and 31 (inclusive)\n.");
        usage();
        exit(1);
    }
    if (bind_addr == 0) {
        fprintf(stderr, "Address to bind to (-b) must be specified.\n");
        usage();
        exit(1);

    }

    if (Xflag)
        swport = NULL_PORT;
    if (Aflag)
        swport = ACCEPT;

    if (znl2_send_rule(action, type, multicast_port, bind_addr,
                       src_mask, src_value, dest_mask, dest_value,
                       srcport_mask, srcport_value,
                       destport_mask, destport_value,
                       swport, progname, debug, dont_send) < 0) {
        fprintf(stderr, "%s: error trying to send multicast message.\n",
                progname);
        exit(2);
    }

    exit(0);
}

static 
void parse_mask(const char* s, unsigned int* pmask, unsigned int* pvalue)
{
    /*
     * Parse a string of the form value[(/|!)mask]
     *
     * The mask may be specified in any base and is optional, in which
     * case 0xffffffff is returned. The value is either a numerical
     * value or dotted octets. If / is used to separate mask and value,
     * it's an ordinary mask: number of bits, counting from the left.
     * If ! was used, it's a literal mask.
     *
     * Errors are reported to stderr.
     */
    unsigned int mask = 0xffffffff;
    unsigned int value = 0;

    char* p, *m, *v;
    char spec[100];
    int o1, o2, o3, o4;         /* octets */

    if (s == 0) return;
    strncpy(spec, s, sizeof(spec));

    /* Look for a '!' or '/' */
    if ((p = strchr(spec, '!')) != 0 || (p = strchr(spec, '/')) != 0) {
        int literal = (*p == '!');
        *p++ = 0;
        v = spec;
        m = p;
        if (literal)
            mask = strtoul(m, &p, 0);
        else {
            unsigned n_bits = strtoul(m, &p, 0);
            mask = (1 << n_bits) - 1;
            mask <<= 32 - n_bits;
        }
        if (p != 0 && *p != 0) {
            fprintf(stderr, "Bogus trailing characters in mask: \"%s\"", p);
            return;
        }
    }
    else
        v = spec;

    /* Value can be a numeric or dotted quad */
    if (sscanf(v, "%d.%d.%d.%d", &o1, &o2, &o3, &o4) == 4)
        value = ((o1 * 256 + o2) * 256 + o3) * 256 + o4;
    else {
        value = strtoul(v, &p, 0);
        if (p != 0 && *p != 0) {
            fprintf(stderr, "Bogus trailing characters in value: \"%s\"", p);
            return;
        }
    }

    *pmask = mask;
    *pvalue = value;
}
