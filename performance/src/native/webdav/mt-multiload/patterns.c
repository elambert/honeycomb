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



#include <multiload.h>

static int parse(url_t* url, const char* path);
static int getPattern(const char** path, patt_t *pattern);
static int getRoster(patt_t* pattern, const char* s);
static int getRange(patt_t* pattern, const char* s);
static void parseURL(const char* URL, char* hostname, int hbuf_size, int* port,
                     char* URI, int ubuf_size);
static int fillAddress(url_t* url);
static void dump(const char* url_string, url_t* url);

static long serial_no = 0;

mutex_t prng_lock;

/* Parse a URL pattern */
url_t* pattern_parse(const char* s)
{
    url_t* url;

    char hostname[MAXHOSTNAMELEN];
    char path[PATH_MAX];

    if ((url = malloc(sizeof(url_t))) == 0) {
        msg("Couldn't allocate URL!");
        return NULL;
    }
    url->raw_string = newstr(s);

    parseURL(s, hostname, sizeof(hostname), &url->port, path, sizeof(path));

    url->server = newstr(hostname);

    /* Parse the path into patterns */
    if (parse(url, path) != 0)
        return NULL;

    /* Do the DNS lookup */
    if (fillAddress(url) != 0)
        return NULL;

    if (loglevel >= LOG_PARAMS)
        dump(s, url);

    return url;
}

/* Generate the next path from the URL pattern */
int pattern_nextpath(url_t* url, char* buffer, size_t buf_size, int* serial)
{
    int i, body_value;
    int values[TMPSIZE];
    memset(values, 0, sizeof(values));

    /* Inside a critical section, generate random values of the reqd. types */
    mutex_lock(&prng_lock);
    for (i = 0; i < url->num_patterns; i++) {
        patt_t* pattern = &url->patterns[i];
        int v = get_rand_int(pattern->num_values-1);

        if (loglevel >= LOG_PRNS)
            msg("PRN %d", v);

        if (pattern->type == RANGE)
            values[i] = v + pattern->vals.start;
        else
            values[i] = pattern->vals.values[v];
    }

    body_value = get_rand_int(BODY_STATESPACE_SIZE);

    serial_no++;
    if (serial != 0)
        *serial = serial_no;

    mutex_unlock(&prng_lock);

    if (loglevel >= LOG_PRNS)
        msg("PRN %d", body_value);

    /*
     * Lord, what a lousy hack. We want to somehow convert an array
     * into arguments for sprintf. The other end of this problem --
     * how to convert a variable-length list of function parameters
     * into an array -- is handled by stdarg, but not this one. So we
     * assume that there will not be more than 200 attributes in a
     * view and do it this way...
     */
    snprintf(buffer, buf_size, url->fmt,
             values[0], values[1], values[2], values[3], values[4],
             values[5], values[6], values[7], values[8], values[9],
             values[10], values[11], values[12], values[13], values[14],
             values[15], values[16], values[17], values[18], values[19],
             values[20], values[21], values[22], values[23], values[24],
             values[25], values[26], values[27], values[28], values[29],
             values[30], values[31], values[32], values[33], values[34],
             values[35], values[36], values[37], values[38], values[39],
             values[40], values[41], values[42], values[43], values[44],
             values[45], values[46], values[47], values[48], values[49],
             values[50], values[51], values[52], values[53], values[54],
             values[55], values[56], values[57], values[58], values[59],
             values[60], values[61], values[62], values[63], values[64],
             values[65], values[66], values[67], values[68], values[69],
             values[70], values[71], values[72], values[73], values[74],
             values[75], values[76], values[77], values[78], values[79],
             values[80], values[81], values[82], values[83], values[84],
             values[85], values[86], values[87], values[88], values[89],
             values[90], values[91], values[92], values[93], values[94],
             values[95], values[96], values[97], values[98], values[99],
             values[100], values[101], values[102], values[103], values[104],
             values[105], values[106], values[107], values[108], values[109],
             values[110], values[111], values[112], values[113], values[114],
             values[115], values[116], values[117], values[118], values[119],
             values[120], values[121], values[122], values[123], values[124],
             values[125], values[126], values[127], values[128], values[129],
             values[130], values[131], values[132], values[133], values[134],
             values[135], values[136], values[137], values[138], values[139],
             values[140], values[141], values[142], values[143], values[144],
             values[145], values[146], values[147], values[148], values[149],
             values[150], values[151], values[152], values[153], values[154],
             values[155], values[156], values[157], values[158], values[159],
             values[160], values[161], values[162], values[163], values[164],
             values[165], values[166], values[167], values[168], values[169],
             values[170], values[171], values[172], values[173], values[174],
             values[175], values[176], values[177], values[178], values[179],
             values[180], values[181], values[182], values[183], values[184],
             values[185], values[186], values[187], values[188], values[189],
             values[190], values[191], values[192], values[193], values[194],
             values[195], values[196], values[197], values[198], values[199]);

    return body_value;
}

/* Split a path into a format string and an array of patterns */
static int parse(url_t* url, const char* path)
{
    int n_patt = 0, fmt_size = 0;
    patt_t patterns[TMPSIZE];
    char fmt_buf[BUFSIZ];
    int rc;

    while (*path != 0) {
        /* Look for a % or { while copying into fmt_buf */
        switch (*path) {

        case 0:
            break;

        case '%':
            /* Double it */
            fmt_buf[fmt_size] = fmt_buf[fmt_size+1] = *path++;
            fmt_size += 2;
            break;

        case '{':
            if ((rc = getPattern(&path, &patterns[n_patt])) != 0)
                return rc;
            strcpy(fmt_buf + fmt_size, patterns[n_patt].fmt);
            fmt_size += strlen(patterns[n_patt].fmt);
            n_patt++;
            break;

        default:
            fmt_buf[fmt_size++] = *path++;
            break;
        }
    }
    fmt_buf[fmt_size] = 0;

    url->num_patterns = n_patt;
    url->patterns = malloc(n_patt * sizeof(patt_t));
    memcpy(url->patterns, patterns, n_patt * sizeof(patt_t));

    url->fmt = newsubstr(fmt_buf, fmt_size);

    return 0;
}

/* 
 * "path" points to a pattern surrounded by {}. Swallow it and make
 * path point to just after, and return parsed pattern.
 */
static int getPattern(const char** path, patt_t *pattern)
{
    char pat[TMPSIZE];
    char *colon, *dash, *comma;
    int psize, rc;

    const char* p = *path + 1;        /* Skip the first '{' */
    const char *q = strchr(p, '}');
    if (q == 0) {
        msg("Pattern \"%s\" has unclosed left brace", *path);
        return 1;
    }
    psize = q - p;
    memcpy(pat, p, psize);
    pat[psize] = 0;

    /* OK, parse pat */

    colon = strchr(pat, ':');
    dash = strchr(pat, '-'); 
    comma = strchr(pat, ',');

    /* Exactly one of dash and comma, and colon has to come before */
    if (comma != 0 && dash != 0) {
        msg("Pattern \"%s\" uses both a comma and a dash!", pat);
        return 1;
    }
    if (comma == 0 && dash == 0) {
        msg("Pattern \"%s\" needs either ',' or '-'", pat);
        return 1;
    }
    if (colon != 0) {
        if (dash != 0 && colon > dash) {
            msg("The '-' is before the ':' in \"%s\"!", pat);
            return 1;
        }
        else if (comma != 0 && colon > comma) {
            msg("The ',' is before the ':' in \"%s\"!", pat);
            return 1;
        }
    }

    if (colon != 0) {
        char* fmt = newsubstr(pat-1, colon - pat+1);
        fmt[0] = '%';
        pattern->fmt = fmt;
        p = colon + 1;
    }
    else {
        pattern->fmt = "%d";
        p = pat;
    }

    if (comma != 0) {
        if ((rc = getRoster(pattern, p)) != 0)
            return rc;
    }
    else {
        if ((rc = getRange(pattern, p)) != 0)
            return rc;
    }

    *path = q + 1;
    return 0;
}

/* s is a comma-separated set of integers */
static int getRoster(patt_t* pattern, const char* s)
{
    const char* p = s;
    int i;
    int num_values = (*s != 0); /* num_values is 1 more than ',' count */

    /* Count the number of commas in s */
    while (*p != 0 && (p = strchr(p, ',')) != 0) {
        p++; num_values++;
    }

    pattern->type = ROSTER;
    pattern->num_values = num_values;
    pattern->vals.values = malloc(num_values * sizeof(int));
    pattern->next_val = 0;

    p = s;
    for (i = 0; i < num_values; i++) {
        char* endp;
        pattern->vals.values[i] = strtol(p, &endp, 10);
        if (*endp != 0 && *endp != ',') {
            msg("Supurious chars in integer \"%s\"", p);
            return 1;
        }
        p = endp + 1;
    }

    return 0;
}

/* s is a range <from>-<to> */
static int getRange(patt_t* pattern, const char* s)
{
    char* endp;

    pattern->type = RANGE;

    pattern->vals.start = strtol(s, &endp, 10);
    if (*endp != '-') {
        msg("Spurious chars \"%s\" in starting value", endp);
        return 1;
    }

    pattern->num_values = strtol(endp+1, &endp, 10) - pattern->vals.start + 1;
    if (*endp != 0) {
        msg("Suprious chars \"%s\" in ending value", endp);
        return 1;
    }

    pattern->next_val = pattern->vals.start;

    return 0;
}

/* Split a URL into server name, port, and path */
static void parseURL(const char* URL, char* hostname, int hbuf_size, int* port,
                     char* URI, int ubuf_size)
{
    char *p, *q;

    char url_buf[10*BUFSIZ];
    const char* uri;

    int h_offset = 7;
    
    if (strncmp(URL, "http://", 7) == 0)
        strncpy(url_buf, URL, sizeof(url_buf));
    else {
        msg("Assuming leading http:// in URL.");
        snprintf(url_buf, sizeof(url_buf), "http://%s", URL);
    }
    URL = url_buf;

    uri = strchr(URL + h_offset, '/');
    if (!uri) {
        /* Hostname only */
        strncpy(hostname, URL + h_offset, MAXHOSTNAMELEN);
	URI[0] = '/'; URI[1] = 0;
    }
    else {
        strncpy(hostname, URL + h_offset, uri-URL);
        hostname[(uri-URL) - h_offset] = 0;
	strncpy(URI, uri, ubuf_size);
    }
    
    p = strchr(hostname, ':');
    if (p) {
        *p++ = 0;
        *port = (int) strtoul(p, &q, 10);
        if (*q)
            msg("Extra characters \"%s\" in port ignored", q);
    }
    else
        *port = HTTP_PORT;

}

static int fillAddress(url_t* url)
{
    struct hostent* host;

    if ((host = gethostbyname(url->server)) == 0) {
        msg("%s: no such host", url->server);
        return 2;
    }

    /* Initialize the destination address.*/
    memset((char *)&url->addr, 0, sizeof(url->addr));
    url->addr.sin_family = AF_INET;
    url->addr.sin_port = htons((u_short) url->port);
    memcpy(&url->addr.sin_addr, host->h_addr, host->h_length);

    return 0;
}

#ifdef TEST
int conn_fill_address(url_t* url) { return 0; }
void usage() { exit(0);}
#else
#define main() static void testmain()
#endif

main()
{
    char* u = "http://foo:8080/bar%20/{02x:0-256}/{02x:0,2,4,6}/{13,17}.jpg";
    url_t* url = pattern_parse(u);

    dump(u, url);

    exit(0);
}

static void dump(const char* url_string, url_t* url)
{
    int i, j;
    msg("URL: %s", url_string);
    msg("Address: \"%s:%d\"", url->server, url->port);
    msg("Format string: \"%s\"", url->fmt);
    msg("Patterns (%d):\n", url->num_patterns);
    for (i = 0; i < url->num_patterns; i++)
        if (url->patterns[i].type == RANGE)
            msg("    %d - %d", url->patterns[i].vals.start,
                   url->patterns[i].vals.start + url->patterns[i].num_values - 1);
        else {
            msg("   ");
            for (j = 0; j < url->patterns[i].num_values; j++)
                msg(" %d", url->patterns[i].vals.values[j]);
            puts("");
        }


#ifndef TEST
    (void)testmain;
#endif
}
