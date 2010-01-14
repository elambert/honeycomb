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



package com.sun.honeycomb.jdai;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.awt.*;
import javax.swing.ImageIcon;
import javax.imageio.stream.*;
import com.sun.imageio.plugins.jpeg.JPEGImageReader;

import dk.jdai.model.*;
import com.sun.honeycomb.fs.*;
import com.sun.honeycomb.adapter.AdapterException;
import com.sun.honeycomb.adapter.Repository;

public class HCPhoto
    implements JdaiPhoto {

    private HCSection section;
    private HCFile hcf;
    private JdaiProgressListener progress;
    private Image cache;
    private File thumbnail;

    HCPhoto(HCSection newSection,
            HCFile newHcf) {
        section = newSection;
        hcf = newHcf;
        progress = null;
        cache = null;
        thumbnail = null;
    }

    /**
     * Get the section this photo belongs to.
     * @return The section.
     */
    public JdaiSection getSection() {
        return(section);
    }

    /**
     * Get the section-unique ID of this photo.
     * @return The ID.
     */
    public String getId() {
        return(hcf.getName());
    }

    private static Image readJpegHCFile(HCFile hcf,
                                        JdaiProgressListener progress)
        throws JdaiReadException {
        Image result = null;
        File tmpFile = null;
        FileOutputStream tmpStream = null;

        try {
            tmpFile = File.createTempFile("hc_jdai_", ".photo"); 	
            tmpStream = new FileOutputStream(tmpFile);
            WritableByteChannel channel = tmpStream.getChannel();

            try {
                hcf.retrieve(channel, 0, -1);
            } catch (AdapterException ignored) {
                ignored.printStackTrace();
            } finally {
                try {
                    channel.close();
                } catch (IOException ignored) {
                }
            }

            result = Toolkit.getDefaultToolkit()
                .createImage(tmpFile.getAbsolutePath());
        
            ImageIcon imageLoader = new ImageIcon(result);
            switch (imageLoader.getImageLoadStatus()) {
            case MediaTracker.LOADING:
                throw new JdaiReadException("media tracker still loading image after requested to wait until finished");
                
            case MediaTracker.COMPLETE:
                result = imageLoader.getImage();
                break;
				
            case MediaTracker.ABORTED:
                throw new JdaiReadException("media tracker aborted image load");
				
            case MediaTracker.ERRORED:
                throw new JdaiReadException("media tracker errored image load");
            }

        } catch (UnsupportedOperationException exc) {
            JdaiReadException e = new JdaiReadException("Couldn't retrieve the file");
            e.initCause(exc);
            throw e;
        } catch (IOException exc) {
            JdaiReadException e = new JdaiReadException("Couldn't retrieve the file");
            e.initCause(exc);
            throw e;
        } finally {
            if (tmpStream != null)
                try {
                    tmpStream.close();
                } catch (IOException e) {
                }
            if (tmpFile != null)
                tmpFile.delete();
        }
        
        return result;
    }

    /**
     * Get a thumbnail of the photo as a BufferedImage for displaying.
     * @return The thumbnail image.
     * @exception JdaiReadException Thrown when thumbnail could not be read.
     */

    public synchronized Image getThumbnail()
        throws JdaiReadException {

        Image image = null;

        if (thumbnail == null) {
            image = constructThumbnail();
        } else {
            image = JdaiImageHelpers.readJpegFile(thumbnail);
        }
     
        int rotation = getSection().getInfoStore().getRotation(this);
        if (rotation != 0) {
            image = JdaiImageHelpers.rotate(image, rotation);
        }
        
        return(image);
    }
    
    /**
     * Refresh the thumbnail of this photo. Is a thumbnail has not been loaded
     * this method does nothing - otherwise it tells the photo to reload the
     * thumnail next time it's needed.
     */
    
    private Image constructThumbnail() {
        Image image = null;
        
        try {
            thumbnail = File.createTempFile("hc_jdai_", ".tmb");
            thumbnail.deleteOnExit();
            
            String offset = ((String)hcf.getInfo().get(Repository.NAMESPACE + "Thumbnail_Offset"));
            if (offset != null) {
                offset = offset.trim().split(" ")[0];
            }
            
            String size = ((String)hcf.getInfo().get(Repository.NAMESPACE + "Thumbnail_Length"));
            if (size != null) {
                size = size.trim().split(" ")[0];
            }

            if ((offset != null) && (size != null)) {
                // Retrieve the partial file
                try {
                    ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
                    WritableByteChannel channel = Channels.newChannel(byteArray);

                    // 12 is the size of the JPEG + EXIF headers
                    long l_offset = Long.parseLong(offset)+12;
                    long l_size = Long.parseLong(size);

                    try {
                        hcf.retrieve(channel, l_offset, l_size);
                        channel.close();
                    } catch (AdapterException ae) {
                        ae.printStackTrace();
                    }

                    image = Toolkit.getDefaultToolkit().createImage(byteArray.toByteArray());

                    ImageIcon imageLoader = new ImageIcon(image);
                    switch (imageLoader.getImageLoadStatus()) {
                    case MediaTracker.LOADING:
                        throw new JdaiReadException("media tracker still loading image after requested to wait until finished");
                
                    case MediaTracker.COMPLETE:
                        image = imageLoader.getImage();
                        break;
				
                    case MediaTracker.ABORTED:
                        throw new JdaiReadException("media tracker aborted image load");
				
                    case MediaTracker.ERRORED:
                        throw new JdaiReadException("media tracker errored image load");
                    }

                } catch (UnsupportedOperationException exc) {
                    JdaiReadException e = new JdaiReadException("Couldn't retrieve the file");
                    e.initCause(exc);
                    throw e;
                }
            } else {
                System.out.println(hcf.getAbsolutePath()+" doesn't have a thumbnail "+offset+" "+size);
                image = getImage(160, 160);
            }
            
            JdaiImageHelpers.writeJpegFile(image, thumbnail);
        } catch (JdaiReadException ignored) {
            ignored.printStackTrace();
        } catch (JdaiWriteException ignored) {
            ignored.printStackTrace();
        } catch (IOException ignored) {
            ignored.printStackTrace();
        }
        
        return(image);
    }

    public void refreshThumbnail() {
        constructThumbnail();
    }

    /**
     * Get the photo itself as a BufferedImage for displaying.
     * @return The image.
     * @exception JdaiReadException Thrown when image could not be read.
     */
    public Image getImage()
        throws JdaiReadException {
        int rotation = getSection().getInfoStore().getRotation(this);
        Image result = JdaiImageHelpers.rotate(getImage(0,0), rotation);
        return result;
    }

    /**
     * Get the photo itself as a BufferedImage for displaying. This
     * method supports resizing the image in a bounding box (the image
     * is never enlarged).
     * @param width Maximum width of the new image.
     * @param height Maximum height of the new image.
     * @return The image.
     * @exception JdaiReadException Thrown when image could not be read.
     */
    public Image getImage(int width, int height)
        throws JdaiReadException {
        Image result = null;

        if (cache == null) {
            cache = readJpegHCFile(hcf, progress);
        }

        if (width != 0 || height != 0) {
            result = JdaiImageHelpers.resizeBBox(cache,
                                                 width,
                                                 height);
        } else {
            result = cache;
        }

        int rotation = getSection().getInfoStore().getRotation(this);
        
        cache = null;
        return result;
    }
    
    /**
     * Method copyTo. Copy photo to another photo (deep copy);
     * @param other the other photo.
     * @throws JdaiReadException when photo or infostore could not be read.
     * @throws JdaiWriteException when photo or infostore could not be written.
     */
    public void copyTo(JdaiPhoto other)
        throws JdaiReadException, JdaiWriteException {
        throw new JdaiWriteException("Copy operation not supported");
    }
    
    /**
     * Method delete. Deletes this photo and information in the infostore of the section.
     * @throws JdaiReadException when photo or infostore could not be read.
     * @throws JdaiWriteException when photo or infostore could not be written.
     */
    public void delete()
        throws JdaiReadException, JdaiWriteException {
        throw new JdaiWriteException("Delete operation not supported");
    }

    /**
     * Get meta information from the photo (e.g. EXIF from digital camera photos).
     * @return An Array of String arrays with metadata.
     */
    public Map getMetaInfo() {
        HashMap result = new HashMap();
        Map info = hcf.getInfo();

        // System MD
        result.put(new String("system.object_id"),
                   info.get("system.object_id"));
        
        result.put(new String("system.object_size"),
                   info.get("system.object_size"));
        
        result.put(new String("system.object_ctime"),
                   info.get("system.object_ctime"));
        
        // Extended MD
        result.putAll(info);

        return(result);
    }

    /**
     * Get meta information from the photo (e.g. EXIF from digital camera photos).
     * @return An HTML String with pretty-printed metadata.
     */
    public String getMetaInfoHtml() {
        return(getMetaInfo().toString());
    }

    /** 
     * Aborts any ongoing image reads.
     */
    public void abortRead() {
        // Not supported
    }

    /**
     * Sets which listener should receive info about progress of reads
     *
     * @param progress The listener
     */
    public void setProgressListener(JdaiProgressListener newProgress) {
        progress = newProgress;
    }

    public int compareTo(Object o) {
        if (o.getClass() != getClass()) {
            return(-1);
        }

        HCPhoto other = (HCPhoto)o;
        return(((String)hcf.getInfo().get(VirtualFile.FIELD_OBJECTID))
               .compareTo((String)other.hcf.getInfo().get(VirtualFile.FIELD_OBJECTID)));
    }
}
