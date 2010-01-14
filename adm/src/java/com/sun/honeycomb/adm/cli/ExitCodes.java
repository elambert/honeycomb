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


package com.sun.honeycomb.adm.cli;

/**
 * <p>
 * This class defines the return codes that ShellCommand
 * implementations should return. These codes mimic
 * /usr/include/sysexits.h; some are here only for the sake of completeness.
 * Implemetors of shell commands should not user return codes that are not
 * listed below! If you wish to return a command specific error value, use
 * EX_CMDBASE plus some value.
 * </p>
 *
 * <p>
 * The documention for sysexist.h reads:
 * </p>
 *  This include file attempts to categorize possible error
 *  exit statuses for system programs, notably delivermail
 *  and the Berkeley network.
 *
 * <pre>
 *  EX_USAGE -- The command was used incorrectly, e.g., with
 *      the wrong number of arguments, a bad flag, a bad
 *      syntax in a parameter, or whatever.
 *  EX_DATAERR -- The input data was incorrect in some way.
 *      This should only be used for user's data & not
 *      system files.
 *  EX_NOINPUT -- An input file (not a system file) did not
 *      exist or was not readable.  This could also include
 *      errors like "No message" to a mailer (if it cared
 *      to catch it).
 *  EX_NOUSER -- The user specified did not exist.  This might
 *      be used for mail addresses or remote logins.
 *  EX_NOHOST -- The host specified did not exist.  This is used
 *      in mail addresses or network requests.
 *  EX_UNAVAILABLE -- A service is unavailable.  This can occur
 *      if a support program or file does not exist.  This
 *      can also be used as a catchall message when something
 *      you wanted to do doesn't work, but you don't know
 *      why.
 *  EX_SOFTWARE -- An internal software error has been detected.
 *      This should be limited to non-operating system related
 *      errors as possible.
 *  EX_OSERR -- An operating system error has been detected.
 *      This is intended to be used for such things as "cannot
 *      fork", "cannot create pipe", or the like.  It includes
 *      things like getuid returning a user that does not
 *      exist in the passwd file.
 *  EX_OSFILE -- Some system file (e.g., /etc/passwd, /etc/utmp,
 *      etc.) does not exist, cannot be opened, or has some
 *      sort of error (e.g., syntax error).
 *  EX_CANTCREAT -- A (user specified) output file cannot be
 *      created.
 *  EX_IOERR -- An error occurred while doing I/O on some file.
 *  EX_TEMPFAIL -- temporary failure, indicating something that
 *      is not really an error.  In sendmail, this means
 *      that a mailer (e.g.) could not create a connection,
 *      and the request should be reattempted later.
 *  EX_PROTOCOL -- the remote system returned something that
 *      was "not possible" during a protocol exchange.
 *  EX_NOPERM -- You did not have sufficient permission to
 *      perform the operation.  This is not intended for
 *      file system problems, which should use NOINPUT or
 *      CANTCREAT, but rather for higher level permissions.
 * </pre>
 */
public interface ExitCodes {
    
    /** 
     * Indicates that command execution is OK and 
     * command execution should.  Don't exit 
     */
    public static final int EX_CONTINUE = -1;
    
     /** Everything's alright */
    public static final int EX_OK = 0;

    /** Command line usage error */
    public static final int EX_USAGE = 64;

    /** Data format error */
    public static final int EX_DATAERR = 65;

    /** cannot open input */
    public static final int EX_NOINPUT = 66;

    /** addressee unknown */
    public static final int EX_NOUSER = 67;

    /** host name unknown */
    public static final int EX_NOHOST = 68;

    /** service unavailable */
    public static final int EX_UNAVAILABLE = 69;

    /** internal software error */
    public static final int EX_SOFTWARE = 70;

    /** system error (e.g., can't fork) */
    public static final int EX_OSERR = 71;

    /** critical OS file missing */
    public static final int EX_OSFILE = 72;

    /** can't create (user) output file */
    public static final int EX_CANTCREAT = 73;

    /** input/output error */
    public static final int EX_IOERR = 74;

    /** temp failure; user is invited to retry */
    public static final int EX_TEMPFAIL = 75;

    /** remote error in protocol */
    public static final int EX_PROTOCOL = 76;

    /** permission denied */
    public static final int EX_NOPERM = 77;

    /** configuration error */
    public static final int EX_CONFIG = 78;

    /** user abort */
    public static final int EX_USERABORT = 79;

    /** multicell hive */
    public static final int EX_NONMASTER = 80;

    /** Command not found */
    public static final int EX_BADCMD = 127;

    /** The base value to be used when returning command-specfic codes */
    public static final int EX_CMDBASE = 200;
};