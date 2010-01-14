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



package com.sun.honeycomb.util;

import java.util.logging.Level;

/**
 *  External logging levels.
 */
public class ExtLevel extends Level {

    /*  ExtLevel level names. Clients should use getName() method defined in
     *  Level to retrieve level name.
     */
    private static final String EXT_INFO_LEVEL = "EXT_INFO";
    private static final String EXT_WARNING_LEVEL = "EXT_WARNING";
    private static final String EXT_SEVERE_LEVEL = "EXT_SEVERE";
    
    //
    // Defines three levels that will be send out to the loghost.
    //
    public static final Level EXT_INFO = new ExtLevel(EXT_INFO_LEVEL,
      Level.SEVERE.intValue() + 1);

    public static final Level EXT_WARNING = new ExtLevel(EXT_WARNING_LEVEL,
      Level.SEVERE.intValue() + 2);

    public static final Level EXT_SEVERE = new ExtLevel(EXT_SEVERE_LEVEL,
      Level.SEVERE.intValue() + 3);

    public ExtLevel(String name, int value) {
	super(name, value);
    }

    /**
     *  Parses the string level name and creates the matching Level object.
     *
     *  @param name String ExtLevel name
     *  @throws IllegalArgumentException
     *  @returns Level object corresponding to the string level name
     */
    public static synchronized Level parse(String name) throws IllegalArgumentException {
	if (name == null) {
           throw new IllegalArgumentException("Argument is null.");
        }

        name.length();

        if (name.equals(EXT_INFO_LEVEL)) {
            return ExtLevel.EXT_INFO;
        } else if (name.equals(EXT_WARNING_LEVEL)) {
            return ExtLevel.EXT_WARNING;
        } else if (name.equals(EXT_SEVERE_LEVEL)) {
            return ExtLevel.EXT_SEVERE;
        } else {
            return Level.parse(name);
        }
    }

    /**
     *  Determines if the Level is an ExtLevel by comparing level names 
     *  (does not compare by value).
     * 
     *  @param level  Level instance to check
     *  @returns true if Level name matches an ExtLevel name; false othewise
     */
    public static boolean isExtLevel(Level level) {
        boolean returnValue = false;
        String name;

	String checkName = level.getName();

        if (checkName == null) {
            returnValue = false;
        } else if (checkName.equals(EXT_INFO_LEVEL)) {
            returnValue = true;;
        } else if (checkName.equals(EXT_WARNING_LEVEL)) {
            returnValue= true;
        } else if (checkName.equals(EXT_SEVERE_LEVEL)) {
            returnValue = true;
        }        
        return returnValue;

    }
}
