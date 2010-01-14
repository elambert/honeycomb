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



#include <sys/types.h>
#include <fcntl.h>
#include <stdio.h>
#include <devid.h>
#include <sys/ddi_impldefs.h>

#ifdef xx
typedef struct impl_devid {
	char	did_driver[DEVID_HINT_SIZE];	/* driver name - HINT */
	char	did_id[1];			/* start of device id data */
} impl_devid_t;
#endif

main(int ac, char *av[])
{
	int	fd, verbose = 0;
	int	asciilen = 0, i, j = 1;
	ddi_devid_t	devid;
	impl_devid_t	*idevid;

	if (ac < 2) {
		fprintf(stderr, "usage: [-v] %s disk-path\n", av[0]);
		exit(1);
	}

	if (strcmp(av[1], "-v") == 0) {
		verbose++;
		j++;
	}

	bzero(&devid, sizeof(devid));

	for (; j < ac; j++) {
		if ((fd = open(av[j], O_NDELAY|O_RDONLY)) == -1) {
			perror(av[j]);
			continue;
		}
		if (devid_get(fd, &devid) != 0) {
			perror("devid_get");
			continue;
		}

		idevid = (impl_devid_t *)devid;

		if (verbose) {
			printf("device id for: %s length: %d  (%x)\n", av[j],
				DEVID_GETLEN(idevid)-DEVID_HINT_SIZE, devid);

			printf("   device id magic # (msb) : 0x%x\n",
							idevid->did_magic_hi);
			printf("   device id magic # (lsb) : 0x%x\n",
							idevid->did_magic_lo);
			printf("device id revision # (msb) : 0x%x\n",
							idevid->did_rev_hi);
			printf("device id revision # (lsb) : 0x%x\n",
							idevid->did_rev_lo);
			printf("      device id type (msb) : 0x%x\n",
							idevid->did_type_hi);
			printf("      device id type (lsb) : 0x%x\n",
							idevid->did_type_lo);
			printf("length of devid data (msb) : 0x%x\n",
							idevid->did_len_hi);
			printf("length of devid data (lsb) : 0x%x\n",
							idevid->did_len_lo);
			printf("               driver name : %s\n",
							idevid->did_driver);

			i = 0;
			for (; i < DEVID_GETLEN(idevid)-DEVID_HINT_SIZE; i++) {
				if (isprint(idevid->did_id[i])) {
					asciilen++;
				} else if (asciilen == 0)
					asciilen = -1000;
			}

			if (asciilen > 0)
				printf("                 device id : %-*.*s\n",
					asciilen, asciilen, idevid->did_id);

			i = 0;
			for (; i < DEVID_GETLEN(idevid); i++) {
				if ((i % 16) == 0)
					printf("\n%#06x: ", i);
				printf("%02x ", idevid->did_id[i] & 0xff);
			}
			printf("\n");
		} else {
			if (ac > 2) {
				printf("%s: ", av[j]);
			}
			printf("%s\n", idevid->did_id);
		}
		close(fd);
	}
}
