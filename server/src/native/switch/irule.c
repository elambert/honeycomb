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



#include <stdio.h>
#include <string.h>
#include <stdlib.h>

/*
 * Masks: 5 values of interest
 * src_addr, src_port, dest_addr, dest_port, arp_proto
 */

#define IMASK_CMD "/usr/sfw/bin/wget -O - -q http://10.123.45.1/http/cgi-bin/zimask"
#define IRULE_CMD "/usr/sfw/bin/wget -O - -q http://10.123.45.1/http/cgi-bin/zirule"

#define STRSIZE 32
#define MAX_MASKS 20

#define MASK_UNKNOWN 0
#define MASK_ARP 1
#define MASK_IP 2

typedef struct im {
    int start;
    int size;
    int src_addr;
    int src_port;
    int dest_addr;
    int dest_port;
    int arp_proto;
} imask_t;

imask_t masks[MAX_MASKS];
int n_masks = 0;

int n_rules = 0;

int parse_mask(const char* s);

/*
 * imask: take lines by pairs
 * remove first line
 * chop off first 50 chars
 * awk:
    size = strtonum("0x" $1)
    start = strtonum("0x" $2)
    dVals = substr($0, 8, 24)
    if (dVals == " 3  3  5  3  0  5  2  2 ")
        t = "IP"
    if (dVals == " 6  3  3  3  3  3  3  3 ")
        t = "ARP"

    printf("%2d   %2d   %2d    ", i, start, size)
    i++

    # first mask is $11

    if (t == "IP") {
        ports = $13
        sport = substr(ports, 1, 4)
        dport = substr(ports, 5, 4)
        src = $17
        dest = $18
    } else if (t == "ARP") {
        dest = $11
        src = $16
        arptype = substr($12, 1, 4)
    }

    printf("%8s %4s  %8s %4s    %4s\n", src, sport, dest, dport, arptype)
    src = sport = dest = dport = arptype = ""
*/
int read_imasks()
{
    int first_time = 1;
    char buffer[BUFSIZ];
    FILE* proc = 
#ifdef DEBUG
        fopen("zmask.out", "r");
#else
        popen(IMASK_CMD, "r");
#endif
    if (proc == 0)
        return -1;

    while (fgets(buffer, sizeof(buffer), proc) != 0) {
        char* p;
        char* p_size, *p_start;
        char *p_src, *p_dest;
        char *p_src_port, *p_dest_port;
        char* p_arp_proto;
        imask_t* imask = &masks[n_masks];
        char destportbuf[10];
        int m_type = MASK_UNKNOWN;
        char *dValues;

        int linelen = strlen(buffer);
        if (buffer[linelen - 1] == '\n')
            buffer[--linelen] == 0;
        if (!fgets(buffer+linelen, sizeof(buffer)-linelen, proc))
            break;

        if (first_time) {
            first_time = 0;
            continue;
        }

        linelen = strlen(buffer);
        if (buffer[linelen - 1] == '\n')
            buffer[--linelen] == 0;

        p = buffer + 49;

        dValues = p + 8;
        dValues[22] = 0;

        if (strcmp(dValues, "3  3  5  3  0  5  2  2") == 0)
            m_type = MASK_IP;
        else if (strcmp(dValues, "6  3  3  3  3  3  3  3") == 0)
            m_type = MASK_ARP;
        else {
            fprintf(stderr, "!!!\n");
            break;
        }

        p_size = p; p_size[2] = 0;
        p_start = p + 4; p_start[2] = 0;
        if (m_type == MASK_IP) {
            p_dest = p + 94;
            p_src = p + 85;
        }
        else if (m_type == MASK_ARP) {
            p_src = p + 76;
            p_dest = p + 31;
        }
        p_src[8] = p_dest[8] = 0;
        strncpy(destportbuf, p + 53, 5);
        p_dest_port = destportbuf; p_dest_port[4] = 0;
        p_src_port = p + 49; p_src_port[4] = 0;
        p_arp_proto = p + 40; p_arp_proto[4] = 0;

#ifdef DEBUG
        fprintf(stderr, "size \"%s\" start \"%s\" src \"%s\" srcport \"%s\"",
                p_size, p_start, p_src, p_src_port);
        fprintf(stderr, " dest \"%s\" destport \"%s\" arp \"%s\"\n",
                p_dest, p_dest_port, p_arp_proto);
#endif

        imask->size = (int) strtol(p_size, 0, 16);
        imask->start = (int) strtol(p_start, 0, 16);

        imask->src_addr = parse_mask(p_src);
        imask->src_port = parse_mask(p_src_port);
        imask->dest_addr = parse_mask(p_dest);
        imask->dest_port = parse_mask(p_dest_port);
        imask->arp_proto = parse_mask(p_arp_proto);

        n_masks++;
        n_rules += imask->size;
    }
    fclose(proc);

    return 0;
}

/*
 * Masks must be all ones followed by all zeros or vice versa 
 * Input is a hexadecimal string
 * Return value is number of 1s from the left (if negative, from the right)
 */
int parse_mask(const char* s)
{
    int n, k;
    int numBits = strlen(s)*4;
    unsigned long mask = strtoul(s, 0, 16);
    int do_flip = 0;

    if (mask == 0)
        return 0;

    if (strncmp(s, "ffffffff", strlen(s)) == 0)
        return numBits;

    if (mask % 2 == 1) {
        /* We know it's not all 1s; therefore it must be counting from lsb */
        do_flip = 1;
        mask = ~mask;
        if (numBits < 32)
            mask &= ((1 << numBits) - 1);
    }

    /* shift right until the lsb is 1 */
    for (n = 0; mask % 2 == 0; n++)
        mask >>= 1;

    /* Now it should be of the form 000....0111....1 i.e. 2^k - 1 where
       k = numBits - n; compare 1 << k to mask + 1 */
    k = numBits - n;

    if (mask + 1 != (1 << k)) {
        fprintf(stderr, "Can't parse mask \"%s\"\n", s);
        return 0xdeadbeef;
    }

    if (do_flip)
        return k - numBits;
    else
        return k;
}

void print_mask(imask_t* mask)
{
    if (mask->size == 1)
        printf("Extent [%d] src %d srcport %d dest %d destport %d arp %d\n",
           mask->start, mask->src_addr, mask->src_port,
           mask->dest_addr, mask->dest_port, mask->arp_proto);
    else
        printf("Extent [%d, %d] src %d srcport %d dest %d destport %d arp %d\n",
               mask->start, mask->start + mask->size - 1,
               mask->src_addr, mask->src_port,
               mask->dest_addr, mask->dest_port, mask->arp_proto);
}

/*
 * irules:
 * remove lines starting with -----
 * take lines 3 at a time
 * remove lines starting with Index
 * awk:

{
    action = get_action(strtonum("0x" $3))
    src = ip($25)
    dest = ip($26)

    printf("%3d  %2x %5s |", 0+$1, strtonum("0x" $15), action)

    if ($19 != "" && $20 != 0) {
        dest = ip($19)
        printf("    %4x              ", strtonum("0x" substr($20, 1, 4)))
    }
    else
        printf("           %4d %5d ",
            strtonum("0x" substr($21,0,4)), strtonum("0x" substr($21,5,4)))

    printf("%15s %15s ", src, dest)

    if (action == "forwd")
        printf("%2d | %2d\n",
            (strtonum("0x" $25)%2)*2 + strtonum("0x" substr($21,0,4))%2,
            strtonum("0x" $12))
    else
        printf("\n")
}
*/

void split(char* s, char** fields, int nfields)
{
    int i = 1;
    char* p;
    fields[0] = strtok(s, " \t\r\n");
    while ((p = strtok(0, " \t\r\n")) != 0) {
        if (i >= nfields)
            break;
        fields[i++] = p;
    }
    while (i < nfields)
        fields[i++] = 0;
}

void put_addr(char* addr, int nBits)
{
    int i;
    if (nBits == 0) {
        printf("           ");
        return;
    }
    for (i = 0; i < 4; i++) {
        char octet[3] = "  ";
        octet[0] = addr[0];
        octet[1] = addr[1];
        addr += 2;
        if (i < 3)
            printf("%ld.", strtol(octet, 0, 16));
        else
            printf("%ld", strtol(octet, 0, 16));
    }
    if (nBits < 0)
        printf("\\%d ", -nBits);
    else if (nBits < 32)
        printf("/%d ", nBits);
    else
        printf(" ");
}

void put_short(short v, int nBits)
{
    if (nBits == 0)
        printf("      ");
    else if (nBits == 16)
        printf("%d  ", v);
    else if (nBits < 0)
        printf(" %d\\%d  ", v, -nBits);
    else
        printf(" %d/%d  ", v, nBits);
}
void put_xshort(short v, int nBits)
{
    if (nBits == 0)
        printf("      ");
    else if (nBits == 16)
        printf("%04x  ", v);
    else if (nBits < 0)
        printf(" %04x\\%d  ", v, -nBits);
    else
        printf(" %04x/%d  ", v, nBits);
}

void awk_proc(char* s)
{
    char *fields[100];
    int action, f_sel;
    char *src, *dest;
    int srcport = 0, destport = 0, arp_proto = 0;
    int o_port = -1;
    imask_t *mask;

#ifdef DEBUG
    fprintf(stderr, ">>> \"%s\"\n", s);
#endif

    fields[0] = 0;              /* awk fields are numbered from 1 */
    split(s, fields+1, 99);
    action = strtoul(fields[3], 0, 16);
    f_sel = strtol(fields[15], 0, 16);
    mask = &masks[f_sel];

    if (mask->arp_proto == 0) {
        /* IP */
        src = fields[25];
        dest = fields[26];
        destport = strtol(fields[21]+4, 0, 16);
        fields[21][4] = 0;
        srcport = strtol(fields[21], 0, 16);
    }
    else {
        /* ARP */
        src = fields[24];
        dest = fields[19];
        fields[20][4] = 0;
        arp_proto = strtoul(fields[20], 0, 16);
    }
    o_port = strtoul(fields[12], 0, 16);

    if (mask->arp_proto == 0) {
        /* IP */
        put_addr(src, mask->src_addr); printf("\t");
        put_short(srcport, mask->src_port); printf("\t");
        put_addr(dest, mask->dest_addr); printf("\t");
        put_short(destport, mask->dest_port); printf("\t");
    }
    else {
        put_addr(src, mask->src_addr); printf("\t\t");
        put_addr(dest, mask->dest_addr); printf("\t\t");
        put_xshort(arp_proto, mask->arp_proto);
    }

    switch (action) {
    case 8: printf("\tcpu"); break;
    case 32: printf("\t-> port %d", o_port); break;
    case 16: printf("\tdrop"); break;
    case 8192: printf("\taccept"); break;
    }

    puts("");
}

int process_irules()
{
    int rule_no = 0;
    char buffer[BUFSIZ];
    FILE* proc = 
#ifdef DEBUG
        fopen("zirule.out", "r");
#else
        popen(IRULE_CMD, "r");
#endif
    if (proc == 0)
        return -1;

    printf("   Source      S.port      Dest        D.port   ARP     Action\n");

    while (fgets(buffer, sizeof(buffer), proc) != 0 && rule_no < n_rules) {
        if (strncmp(buffer, "--------", 8) == 0)
            continue;

        int linelen = strlen(buffer);
        if (!fgets(buffer+linelen, sizeof(buffer)-linelen, proc))
            break;
        linelen = strlen(buffer);
        if (!fgets(buffer+linelen, sizeof(buffer)-linelen, proc))
            break;
        if (strncmp(buffer, "Index", 5) == 0)
            continue;

        awk_proc(buffer);
        rule_no++;
    }
    fclose(proc);

    return 0;
}


int main(int argc, char* argv[])
{
    int i;

    if (read_imasks() < 0) {
        fprintf(stderr, "Couldn't read imasks!\n");
        exit(2);
    }

#ifdef DEBUG
    for (i = 0; i < n_masks; i++)
        print_mask(&masks[i]);
    puts("");
#else
    i;                          /* use variable i */
#endif

    if (process_irules() < 0) {
        fprintf(stderr, "Couldn't process irules!\n");
        exit(2);
    }

    exit(0);
}
