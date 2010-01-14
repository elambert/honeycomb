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
#include <sys/stat.h>
#include <sys/asynch.h>
#include <sys/time.h>
#include <sys/mman.h>
#include <stdio.h>
#include <stdarg.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <stdlib.h>
#include <thread.h>
#include <umem.h>
#include <string.h>

#define	MAX_ITER	(4)
#define	PAGESIZE	(4096)
#define	RECSIZE1	(212)
#define	RECSIZE2	(60)
#define	OFFSET		(0)

typedef void (*test_func_t)(char *);

char rec1[RECSIZE1];
char rec2[RECSIZE2];

long pagesize = 0;

static void
print(char *print_string, ...)
{
	int sz;
	va_list args;
	char buffer[1024];

	va_start(args, print_string);
	sz = vsnprintf(buffer, 1024, print_string, args);
	va_end(args);

	(void) write(1, buffer, sz);
}

static void
panic(char *panic_string, ...)
{
	va_list args;
	char buffer[1024];

	va_start(args, panic_string);
	(void) vsnprintf(buffer, 1024, panic_string, args);
	va_end(args);

	print(buffer);

	exit(1);
}

static char
conv_char(int value)
{
	return ((char)(value % 256));
}

static void
setup_records(void)
{
	int counter;

	for (counter = 0; counter < RECSIZE1; counter++) {
		rec1[counter] = conv_char(counter);
	}

	for (counter = 0; counter < RECSIZE2; counter++) {
		rec2[RECSIZE2 - counter - 1] = conv_char(counter);
	}
}

static int
open_file(char *filename, int flags)
{
	int fd = open64(filename, flags, 0777);

	if (fd < 0)
		panic("Can't open file : %s\n", filename); 

	return (fd);
}

static void
close_file(int fd)
{
	if (close(fd) != 0)
		panic("Can't close FD : %u\n", fd);
}

static void
read_full_file(int fd)
{
	int result;
	char buffer[2 * PAGESIZE];

	llseek(fd, 0, SEEK_SET);

	while (read(fd, buffer, (2 * PAGESIZE)) > 0); 
}

static void
read_record(int fd, off_t offset, char *buffer, size_t len)
{
	if (pread64(fd, buffer, len, offset) != len)
		panic("Can't read : %u bytes at : %u \n", len, offset);
}

static void
write_record(int fd, off_t offset, char *buffer, size_t len)
{

	if (pwrite64(fd, buffer, len, offset) != len)
		panic("Can't write : %u bytes at : %u \n", len, offset);
}

void
test_one(char *filename)
{
	int fd = open_file(filename, O_CREAT | O_RDWR);
	int counter;
	off_t off;
	struct stat sresult;

	for (counter = 0; counter < MAX_ITER; counter++) {
		off = (counter * PAGESIZE) + OFFSET;
		write_record(fd, off, rec1, RECSIZE1);
	}

	if (fsync(fd) != 0)
		panic("Can't sync the FD : %u\n", fd);

	if (fstat(fd, &sresult) != 0)
		panic("Can't stat FD : %u\n", fd);

	off = sresult.st_size;

	close_file(fd);

	print("%u\n", off);
}

void
test_two(char *filename)
{
	int fd = open_file(filename, O_RDWR);
	off_t off;
	struct stat sresult;

	if (fstat(fd, &sresult) != 0)
		panic("Can't stat FD : %u\n", fd);

	off = sresult.st_size;

	write_record(fd, off, rec2, RECSIZE2);

	read_full_file(fd);

	close_file(fd);

	print("%u\n", off + RECSIZE2);
}

void
test_three(char *filename)
{
	int fd = open_file(filename, O_RDWR);
	off_t off;
	struct stat sresult;

	if (fstat(fd, &sresult) != 0)
		panic("Can't stat FD : %u\n", fd);

	off = sresult.st_size;

	write_record(fd, off, rec2, RECSIZE2);

	close_file(fd);

	print("%u\n", off + RECSIZE2);
}

test_func_t test_functions[] = {
	test_one,
	test_two,
	test_three
};

void
main(int argc, char **argv)
{
	int testno;
	int total_tests = sizeof(test_functions) / sizeof (char *);

	if (argc != 3)
		panic("usage : %s <filename> <test no>\n", argv[0]);

	testno = atoi(argv[2]);

	if ((testno < 1) || (testno > 3))
		panic("Test number must be between %d to %d\n", 0, 4);

	if ((testno - 1) > total_tests)
		panic("Internal Error");

	setup_records();

	(*test_functions[testno - 1])(argv[1]);
}

/**

This program does three things, it can be selected through the command line.

Test 1:

Command syntax - nfstest <filename> <test no : which is 1>

This test must be run at the server side.

It creates a test file of size 12500 (It can be used for further testing later). 
We don't need to copy the 2M of the DB file again and again. Yes the problem is 
even seen with just four pages.

Test 2:

Command syntax - nfstest <filename> <test no : which is 2>

This test must be run at the client side.

** It opens the file.

** It adds a record of 60 bytes at the end of the file. 

** It sets the file pointer at the begining of the file and reads the whole 
   file.

** It closes the file.

If you check the filesize at the client side using ls, it displays the new size.
Now compare the size between the server and the client. The server still 
displays the old size.

Issuing a sync at the client side brings the file size to the old size.

Test 3:

Command syntax - nfstest <filename> <test no : which is 3>

This test must be run at the client side.

** It opens the file.

** It adds a record of 60 bytes at the end of the file. 

** It closes the file.

*/
