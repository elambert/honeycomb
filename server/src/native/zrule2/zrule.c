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



#include "libztmd.h"

/* For basename(3c) */
#include <libgen.h>
#include <netdb.h>

static char id[] = "$Id: zrule.c 10855 2007-05-19 02:54:08Z bberndt $";
static char* progname;

static 
void parse_mask(const char* s, unsigned int* pmask, unsigned int* pvalue, int gethostname);
static void parse_type(const char* t);

static void usage() 
{
    fprintf(stderr, "\nUsage: %s <command> [-d|-v] [-i] -b <bind address> "
            "[-s <switch port>]\n    [-t <packet type>] [-src <value/mask>] "
            "[-dest <value/mask>]\n    [-srcport <value/mask>] "
            "[-destport <value/mask>]\n    [-timeout millis] [-retries n]\n",
            progname);
    fprintf(stderr, "Where:\n");
    fprintf(stderr, "       command: add | delete | reset\n");
    fprintf(stderr, "       -v (or -d) Increase verbosity\n");
    fprintf(stderr, "       -i Apply rule to the ingress port %d only\n",
            PORT_EXT);
    fprintf(stderr, "       -b Bind multicast socket to <bind address>\n");
    fprintf(stderr, "       -s Direct traffic to switch port <switch port>\n");
    fprintf(stderr, "          (port %d is the switch CPU)\n", CPU_PORT);
    fprintf(stderr, "       -X Drop the matching packets\n");
    fprintf(stderr, "       -A Accept the matching packets (overrides DROP)\n");
    fprintf(stderr, "       -t Packets of type <packet type> (default: ip)\n");
    fprintf(stderr, "       -src      value/mask for src address\n");
    fprintf(stderr, "       -dest     value/mask for dest address\n");
    fprintf(stderr, "       -srcport  value/mask for src port\n");
    fprintf(stderr, "       -destport value/mask for dest port\n");
    fprintf(stderr, "       -timeout  time to wait for reply from switch\n");
    fprintf(stderr, "       -retries  number of retries on timeout\n");
    fprintf(stderr, "Src/dest ports can only be specified for types tcp and udp.\n");
    fprintf(stderr, "If a mask is not specified, it defaults to 0xffffffff.\n");
    fprintf(stderr, "\nExamples:\n");
    fprintf(stderr, "%s add -src 0x7/11 -dest 10.1.252.96 -t tcp -destport 80"
            " -s 11\n", progname);
    fprintf(stderr, "    - if the last three bits of src IP addr are equal"
            "11 (decimal),\n      and it's TCP going to 10.1.252.96:80, send it "
            "to switch port 11.\n");
    fprintf(stderr, "%s add -t udp -destport 1/1 -s 13\n", progname);
    fprintf(stderr, "    - if it's UDP with destination port odd, send it to "
            "switch port 13.\n");
    fprintf(stderr, "%s add -src 10.123.0.0/16 -X\n", progname);
    fprintf(stderr, "    - drop all packets coming from the 10.123 class B network\n");
    fprintf(stderr, "\nThe \"reset\" command takes no arguments. "
            "For the \"delete\" command,\nthe -s is optional.\n");

    fputs("\n", stderr);
}

static void print_types()
{
  int i;
    fprintf(stderr, "%s: Known packet types:\n", progname);
    
    fprintf(stderr, "\tip\n\ttcp\n\tudp\n\tarp\n");
    for (i = 0; arp_types[i]; i++)
        if (*arp_types[i])
            fprintf(stderr, "\t%s\n", arp_types[i]);
    fprintf(stderr, "\ticmp\n");
    for (i = 0; icmp_types[i]; i++)
        if (*icmp_types[i])
            fprintf(stderr, "\t%s\n", icmp_types[i]);
}

int main(int argc, char *argv[]) 
{
    int optind;
    int pretend = 0;            /* Don't actually send, just pretend */
    int reset = 0;

    unsigned int mask, value;

    int swport = UNSPEC;
    int action = NOOP;
    unsigned char Xflag = 0, Aflag = 0;
    char* bind_addr = 0, *action_s = 0;

    set_name(progname = basename(argv[0]));

    /* Get the action */
    action_s = argv[1];
    if (action_s == NULL) {
        fprintf(stderr, "\n%s: The action is required.\n", progname);
        usage();
        exit(1);
    }

    if (strcmp(action_s, "reset") == 0)
        action = RESET;
    else if (strcmp(action_s, "delete") == 0)
        action = DELETE;
    else if (strcmp(action_s, "add") == 0)
        action = ADD;
    else {
        fprintf(stderr, "\n%s: Unknown action \"%s\"\n", progname, action_s);
        usage();
        exit(1);
    }

    /* Check the arguments */
    switch (action) {
    case ADD:
        if (argc < 4) {
            fprintf(stderr, "\n%s: Options -b and -s are required.\n",
                    progname);
            usage();
            exit(1);
        }
        break;

    case DELETE:
        /* For delete, -s is optional */
    case RESET:
        if (argc < 3) {
            fprintf(stderr, "\n%s: Option -b is required.\n", progname);
            usage();
            exit(1);
        }
        break;

    default:
        fprintf(stderr, "%s: Internal error: action = %d\n", progname,
                action);
        exit(5);
    }

    optind = 2;                 /* We've handled two arguments */
    while (optind < argc) {
        char* option = argv[optind];
        char* optarg = argv[optind + 1];
        optind++;

        if (*option != '-') {
            fprintf(stderr, "\n%s: Expected option instead of \"%s\"\n",
                    progname, option);
            usage();
            exit(1);
        }
        if (strcmp(option, "-d") == 0 || strcmp(option, "-v") == 0) {
            more_verbose();
            continue;
        }
        else if (strcmp(option, "-h") == 0) {
            usage();
            print_types();
            exit(0);
        }
        else if (strcmp(option, "-X") == 0)
            Xflag++;
        else if (strcmp(option, "-n") == 0)
            pretend = 1;
        else if (strcmp(option, "-i") == 0) {
            ingress = 1;
            mypid = ZTMD_ZRULE_INGRESS_PID;
        } else if (strcmp(option, "-A") == 0)
            Aflag++;
        else {
            optind++;           /* All others take an argument */
            if (!optarg || *optarg == 0) {
                fprintf(stderr, "\n%s: Option \"%s\" requires an argument.\n",
                        progname, option);
                usage();
                exit(1);
            }

            if (strcmp(option, "-b") == 0)
                bind_addr = optarg;
            else if (strcmp(option, "-s") == 0)
                swport = strtoul(optarg, 0, 0);

            else if (strcmp(option, "-t") == 0)
                parse_type(optarg);

            else if (strcmp(option, "-src") == 0) {
                parse_mask(optarg, &mask, &value, 1);
                set_src(mask, value);
            }
            else if (strcmp(option, "-dest") == 0) {
                parse_mask(optarg, &mask, &value, 0);
                set_dest(mask, value);
            }
            else if (strcmp(option, "-srcport") == 0) {
                parse_mask(optarg, &mask, &value, 0);
                set_srcport(mask, value);
            }
            else if (strcmp(option, "-destport") == 0) {
                parse_mask(optarg, &mask, &value, 0);
                set_destport(mask, value);
            }
            else if (strcmp(option, "-timeout") == 0) {
                long t = strtol(optarg, 0, 10);
                set_timeout(t);
            }
            else if (strcmp(option, "-retries") == 0) {
                long n = strtol(optarg, 0, 10);
                set_retries(n);
            }
            else
                fprintf(stderr,
                        "\n%s: Option \"%s\" unrecognized, skipping.\n",
                        progname, option);
        }
    }

    if (action == ADD && swport == UNSPEC && !Aflag && !Xflag) {
        fprintf(stderr, "\n%s: Switch port must be specified.\n", progname);
        usage();
        exit(1);
    }

    if (swport != UNSPEC) {
        if (Aflag || Xflag || action == RESET) {
             fprintf(stderr, "\n%s: Switch port must not be specified "
                     "for reset, -A or -X\n", progname);
             usage();
             exit(1);
        }

        if (swport != CPU_PORT && (swport < 0 || swport > 18)) {
            fprintf(stderr,
                    "\n%s: Switch port must be in [1..18] (inclusive)\n",
                    progname);
            usage();
            exit(1);
        }
    }

    if (pretend)
        bind_addr = 0;
    else
        if (bind_addr == 0) {
            fprintf(stderr,
                    "\n%s: Address to bind to (-b) must be specified.\n",
                    progname);
            usage();
            exit(1);
        }

    if (Xflag)
        swport = NULL_PORT;
    if (Aflag)
        swport = ACCEPT;

    if (nl2_initialize(bind_addr) != 0) {
        fprintf(stderr, "%s: Couldn't initialize with \"%s\"\n", progname,
                (bind_addr? bind_addr : "(null)"));
        exit(2);
    }

    if (nl2_send_rule(action, swport) != 0) {
        fprintf(stderr, "%s: Error trying to send request.\n", progname);
        exit(2);
    }

    exit(0);
}

static 
void parse_mask(const char* s, unsigned int* pmask, unsigned int* pvalue, int gethostname)
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
    char host_or_port[100];
    int o1, o2, o3, o4;         /* octets */
    struct hostent *hostentp = 0;

    if (s == 0) return;
    strncpy(spec, s, sizeof(spec));

    /* Look for a '!' or '/' */
    if ((p = strchr(spec, '!')) != 0 || (p = strchr(spec, '/')) != 0) {
        int literal = (*p == '!');
        *p++ = 0;
        v = spec;
        strncpy(host_or_port, spec, abs(p - v));
        m = p;
        if (literal)
            mask = strtoul(m, &p, 0);
        else {
            unsigned n_bits = strtoul(m, &p, 0);
            mask = (1 << n_bits) - 1;
            mask <<= 32 - n_bits;
        }
        if (p != 0 && *p != 0) {
            fprintf(stderr, "%s: Bogus trailing characters in mask: \"%s\"",
                    progname, p);
            return;
        }
    }
    else {
        v = spec;
        strncpy(host_or_port, spec, sizeof(host_or_port));
    }

    if (sscanf(v, "%d.%d.%d.%d", &o1, &o2, &o3, &o4) == 4) {
        value = ((o1 * 256 + o2) * 256 + o3) * 256 + o4;
    } 
    else {
         value = strtoul(v, &p, 0);
         if (p != 0 && *p != 0) {
             /* possibly a valid hostname, only for -src option */
             if (gethostname) {
                 hostentp = gethostbyname(host_or_port);
                 if (hostentp) {
                     struct in_addr **a;
                     a = (struct in_addr **)hostentp->h_addr_list;
                     if (sscanf(inet_ntoa(**a), "%d.%d.%d.%d", &o1, &o2, &o3, &o4) == 4) {
                         value = ((o1 * 256 + o2) * 256 + o3) * 256 + o4;
                     }
                 }
             }
             if (!hostentp) {
                 fprintf(stderr, "%s: Bogus trailing characters in value: \"%s\"",
                         progname, p);
                 return;
             }
         }
    }
    *pmask = mask;
    *pvalue = value;
}

static int find_str(const char* s, const char *names[])
{
    int i;
    char lcopy[BUFSIZ], *p = lcopy;
    if (!s || !*s)
        return -1;

    /* Replace any '_' with '-' */
    while (*s) {
        if (*s == '_')
            *p++ = '-';
        else
            *p++ = *s;
        s++;
        if ((p - lcopy) >= (sizeof(lcopy)-1))
            break;
    }
    *p = 0;

    for (i = 0; names[i]; i++)
        if (strcasecmp(lcopy, names[i]) == 0)
            return i;

    return -1;
}

static void parse_type(const char* packet_type)
{
    int ind;

    if (strcasecmp(packet_type, "ip") == 0) {
        set_packet_type(PACKETTYPE_IP);
        return;
    }

    if (strcasecmp(packet_type, "tcp") == 0) {
        set_packet_type(PACKETTYPE_IP);
        set_protocol(PROTO_TCP);
        return;
    }

    if (strcasecmp(packet_type, "udp") == 0) {
        set_packet_type(PACKETTYPE_IP);
        set_protocol(PROTO_UDP);
        return;
    }

    if (strncasecmp(packet_type, "icmp", 4) == 0) {
        if (packet_type[4] == 0) { /* All ICMP */
            set_packet_type(PACKETTYPE_IP);
            set_protocol(PROTO_ICMP);
            return;
        }

        if ((ind = find_str(packet_type, icmp_types)) >= 0) {
            set_packet_type(PACKETTYPE_IP);
            set_protocol(PROTO_ICMP);
            set_icmp_type(ind);
            return;
        }
    }

    if  (strcasecmp(packet_type, "arp") == 0) {
        /* All ARP */
        set_packet_type(PACKETTYPE_ARP);
        return;
    }

    if ((ind = find_str(packet_type, arp_types)) >= 0) {
        set_packet_type(PACKETTYPE_ARP);
        set_arp_opcode(ind);
        return;
    }

    fprintf(stderr, "%s: unknown packet type \"%s\"\n", progname, packet_type);
    print_types();
}

