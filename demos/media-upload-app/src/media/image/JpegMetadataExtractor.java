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



package media.image;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.HashSet;
import java.util.Iterator;

import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifDirectory;
import com.drew.metadata.jpeg.JpegCommentDirectory;

public class JpegMetadataExtractor {
    private static final String[] allowedExifTagNames = {
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
    private static final HashSet allowedExifTags;
    static {
        allowedExifTags = new HashSet();
        for (int i=0; i < allowedExifTagNames.length; i++) {
            allowedExifTags.add(allowedExifTagNames[i]);
        }
    }

    public static void extract(Map jpegMetadata, File jpegFile)
        throws FileNotFoundException {
        Metadata metadata = null;

        // Read the jpeg metadata
        try {
            metadata = JpegMetadataReader.readMetadata(jpegFile);
        } catch (JpegProcessingException e) {
            System.out.println("Error processing jpeg file " +
                               jpegFile.getName() + " Exception: " + e);
            return;
        }

        // Extract the exif tags
        Directory exifDirectory = metadata.getDirectory(ExifDirectory.class);
        Iterator tags = exifDirectory.getTagIterator();
        while (tags.hasNext()) {
            Tag tag = (Tag) tags.next();
            String key = scrubKey(tag.getTagName());
            if (allowedExifTags.contains(key.toLowerCase())) {
                try {
                    jpegMetadata.put(key, tag.getDescription());
                } catch (MetadataException e) {
                    System.out.println("Error getting metadata for key = " +
                                       key + " Exception: " + e);
                }
            }
        }

        // Extract the jpeg comment
        Directory commentDirectory =
            metadata.getDirectory(JpegCommentDirectory.class);
        String jpegComment = null;
        try {
            jpegComment = commentDirectory.getDescription
                (JpegCommentDirectory.TAG_JPEG_COMMENT);
            jpegMetadata.put("Photo_Location", jpegComment);
        } catch (MetadataException e) {
            System.out.println("Error getting jpeg comment. Exception: " + e);
        }

        // Add the extension
        jpegMetadata.put("File_Extension", "jpg");
    }

    private static String scrubKey(String str) {
        str = str.replaceAll("/", "_");
        str = str.replaceAll(" ", "_");
        str = str.replaceAll("-", "_");
        return str;
    }
}
