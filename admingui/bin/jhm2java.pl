#!/usr/bin/perl
#
# $Id: jhm2java.pl 10842 2007-05-19 02:24:31Z bberndt $
#
# Copyright © 2008, Sun Microsystems, Inc.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
#   # Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#
#   # Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#
#   # Neither the name of Sun Microsystems, Inc. nor the names of its
# contributors may be used to endorse or promote products derived from
# this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
# TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
# PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
# OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.



#

#
# File:         jhm2java.pl
# Description:  Translate jhm mapping into a Java source file.
# Author:	Roy Lee (From the Sedona Source Tree)
#

if ($#ARGV < 2) {
	print "Usage: jhm2java <jhm file> <java package> <java class name>\n";
	exit -1;
}

$jhmFile = @ARGV[0];
$javaPkg = @ARGV[1];
$javaClassName = @ARGV[2];

if (createJavaClassFile()) {
	die "$0: Failed to translate jhm to Java source file.";
}

exit 0;

#
# Creates the Java class file by transforming jhm file.
#
sub createJavaClassFile {

	# Open source file.
	if (!open(fhJHMFile, "<".$jhmFile)) {
		print "Cannot open $jhmFile.\n";
		return -1;
	}

	$javaClassFile = $javaClassName.".java";

	# Open/Create destination file.
	if (!open(fhJavaClassFile, ">".$javaClassFile)) {
		close(fhJHMFile);
		print "Cannot create $javaClassFile.\n";
		return -1;
	}

	# Transformation.
	printHeader(fhJavaClassFile);

	printf(fhJavaClassFile "package $javaPkg;\n");
	printf(fhJavaClassFile "\n");
	printf(fhJavaClassFile "public class $javaClassName {\n");

	while ($line = readline(fhJHMFile)) {
		if ($line =~ /target="hc_hlp-(\w+)"\s+url="(\S+)\.(\S+)"/s) {
			if (!($3 =~ /#/s)) {
				$key = $1;
				$def = $2;
				$def =~ s/hc_hlp-//g;
				# printf(fhJavaClassFile "\tpublic static final String \U$2"." = \"$1\";\n");
				printf(fhJavaClassFile "    public static final String \U$def"." = \"hc_hlp-$key\";\n");
			}
		}
	}

	printf(fhJavaClassFile "}");

	close(fhJHMFile);
	close(fhJavaClassFile);

	return 0;
}

#
# Prints the header for the Java class file.
#
sub printHeader {
    my ($fileHandle) = @_;

    printf($fileHandle "/*
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

\n");

    return 0;
}
