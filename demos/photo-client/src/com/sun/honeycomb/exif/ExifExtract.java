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



package com.sun.honeycomb.exif;

import com.drew.imaging.jpeg.JpegProcessingException;
import java.util.Iterator;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import com.drew.metadata.Metadata;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.jpeg.JpegCommentDirectory;
import com.drew.metadata.Directory;
import com.drew.metadata.MetadataException;
import java.util.Set;
import java.util.Map;
import java.io.IOException;
import java.util.HashSet;
import java.io.ByteArrayInputStream;
import com.drew.metadata.Tag;
import java.util.HashMap;

public class ExifExtract {
    
    private static final String[] ALLOWED_FIELDS = {
        "sensing_method",
        "components_configuration",
        "exposure_bias_value",
        "exif_image_height",
        "f_number",
        "focal_length",
        "date_time",
        "x_resolution",
        "resolution_unit",
        "date_time_original",
        "brightness_value",
        "exposure_time",
        "software",
        "make",
        "aperture_value",
        "light_source",
        "exposure_program",
        "date_time_digitized",
        "exif_version",
        "ycbcr_positioning",
        "shutter_speed_value",
        "flashpix_version",
        "user_comment",
        "thumbnail_data",
        "focal_plane_resolution_unit",
        "y_resolution",
        "thumbnail_offset",
        "color_space",
        "exposure_index",
        "focal_plane_x_resolution",
        "scene_type",
        "orientation",
        "image_description",
        "metering_mode",
        "focal_plane_y_resolution",
        "model",
        "copyright",
        "subject_distance",
        "iso_speed_ratings",
        "flash",
        "compression",
        "file_source",
        "compressed_bits_per_pixel",
        "exif_image_width",
        "thumbnail_length",
        "max_aperture_value"
    };

    private static final int BUFFER_SIZE = 1024 * 1024;
    
    private final Set ALLOWED_EXIF_FIELDS = 
        new HashSet(ALLOWED_FIELDS.length);
    
    public ExifExtract() {
        for (int i=0; i<ALLOWED_FIELDS.length; i++) {
            ALLOWED_EXIF_FIELDS.add(ALLOWED_FIELDS[i]);
        }
    }
    
    /*
     * It is necessary to cache the entire image in memory to avoid a bug
     * in the JpegMetadataReader which throws an exception if
     * InputStream.available() is less than the number of bytes it needs 
     */
    
    public Map parse(File file)
        throws JpegProcessingException, IOException {
        
        InputStream is = null;
        Map result = new HashMap();
        
        try {
            is = new FileInputStream(file);
            Metadata exif = JpegMetadataReader.readMetadata(is);
            Iterator directories = exif.getDirectoryIterator();
            
            while (directories.hasNext()) {
                Directory directory = (Directory) directories.next();

                // ignore Jpeg and other directories
                if ("Exif".equalsIgnoreCase(directory.getName())) {
                    Iterator tags = directory.getTagIterator();
                    while (tags.hasNext()) {
                        Tag tag = (Tag) tags.next();
                        String key = null;
                        try {
                            key = scrubKey(tag.getTagName());
                            if (ALLOWED_EXIF_FIELDS.contains(key.toLowerCase()) &&
                                tag.getDescription() != null) {
                                result.put("photo." + key, 
                                           tag.getDescription());
                            }
                        } catch (MetadataException ignore) {}
                    }
                }
            }

            try {
                // Get the jpeg comment and put it in the map
                Directory commentDirectory =
                    exif.getDirectory(JpegCommentDirectory.class);
                String comment = commentDirectory.getDescription
                    (JpegCommentDirectory.TAG_JPEG_COMMENT);
                if (comment != null) {
                    result.put("photo.Photo_Location", comment);
                }
            } catch (Exception ignore) {
            }

            // Put the default extension as jpg for photos
            //result.put("photo.File_Extension", "jpg");
        } catch (Exception ignore) {
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
        
        return(result);
    }


    
    public void parseAll(File file)
        throws Exception {
        
        InputStream is = null;
        Map result = new HashMap();
        
        try {
            is = new FileInputStream(file);
            Metadata exif = JpegMetadataReader.readMetadata(is);
            Iterator directories = exif.getDirectoryIterator();
            
            while (directories.hasNext()) {
                Directory directory = (Directory) directories.next();

                Iterator tags = directory.getTagIterator();
                while (tags.hasNext()) {
                    Tag tag = (Tag) tags.next();
                    System.out.println(tag.getTagName() + ": " + tag.getDescription());
                }
            }

            // Get the jpeg comment and put it in the map
            Directory commentDirectory =
                exif.getDirectory(JpegCommentDirectory.class);
            String comment = commentDirectory.getDescription
                (JpegCommentDirectory.TAG_JPEG_COMMENT);
            if (comment != null) {
                result.put("photo.Photo_Location", comment);
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }
    
    private static String scrubKey(String str) {
        str = str.replaceAll("/", "_");
        str = str.replaceAll(" ", "_");
        str = str.replaceAll("-", "_");
        return str;
    }
}
