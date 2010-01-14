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



package media.video;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

public class MpegMetadataExtractor {
    public static void extract(Map mpegMetadata, File mpegFile)
        throws FileNotFoundException {
        /*
         * The mpeg metadata is encoded in the file name. The format is:
         * <title>-<author>-<frame rate>.mpeg
         */
        // Remove the extension
        String[] fields = mpegFile.getName().split("\\.");
        String toParse = fields[0];

        String[] mpegTags = toParse.split("-");
        /*
        System.out.println("Title: " + mpegTags[0] + "\n" +
                           "Author: " + mpegTags[1] + "\n" +
                           "Frames per second: " + mpegTags[2]);
        */

        // Put in the metadata
        mpegMetadata.put("Video_Compilation", mpegTags[0]);
        mpegMetadata.put("Video_Author", mpegTags[1]);
        mpegMetadata.put("Video_Frame_Rate", mpegTags[2]);

        // Put the extension
        mpegMetadata.put("File_Extension", "mpg");
    }
}
