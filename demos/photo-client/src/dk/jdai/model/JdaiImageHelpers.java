// JdaiImageHelpers.java
// $Id: JdaiImageHelpers.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.model;

import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.geom.AffineTransform;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Toolkit;
import java.awt.Image;
import javax.swing.ImageIcon;

/**
 * Provides helper functionality for image operations.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiImageHelpers {

    public static final int NORTH = JdaiPhotoInfoStore.NORTH;
    public static final int EAST  = JdaiPhotoInfoStore.EAST;
    public static final int SOUTH = JdaiPhotoInfoStore.SOUTH;
    public static final int WEST  = JdaiPhotoInfoStore.WEST;
    
    /**
     * Reads a JPEG file into a BufferedImage at the specified maximum size.
     * And where a reader aware object and a progress litener is set.
     * @param file The file to read from.
     * @param width Maximum width of the image.
     * @param height Maximum height of the image.
     * @param progress The progress listener to use
     * @return The image.
     * @exception JdaiReadException when image cannot be read.
     */
    public static Image readJpegFile(File file, int width, int height,
                                     JdaiProgressListener progress)
            throws JdaiReadException {
        Image result = null;

        if (file.exists()) {
            result = Toolkit.getDefaultToolkit().createImage(file.getAbsolutePath());
        } else {
            throw new JdaiReadException("File does not exist: " + file);
        }
        
        if(progress != null) 
            progress.indeterminate(file);
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

        if (progress != null) 
            progress.complete(file);

        if (width != 0 && height != 0)
            result = JdaiImageHelpers.resizeBBox(result,width,height);

        return result;
    }

    /**
     * Reads a JPEG file into an Image at the specified maximum size.
     * @param file The file to read from.
     * @param width Maximum width of the image.
     * @param height Maximum height of the image.
     * @return The image.
     * @exception JdaiReadException when image cannot be read.
     */
    public static Image readJpegFile(File file, int width, int height)
        throws JdaiReadException {
        return readJpegFile(file, width, height, null);
    }

    /**
     * Reads a JPEG file into an Image.
     * And where a reader aware object and a progress litener is set.
     * @param file The file to read from.
     * @param progList The progress listener to use
     * @return The image.
     * @exception JdaiReadException when image cannot be read.
     */
    public static Image readJpegFile(File file, JdaiProgressListener progList)
        throws JdaiReadException {
        return readJpegFile(file,0,0,progList);
    }

    /**
     * Reads a JPEG file into an Image.
     * @param file The file to read from.
     * @return The image.
     * @exception JdaiReadException when image cannot be read.
     */
    public static Image readJpegFile(File file) throws JdaiReadException {
        return readJpegFile(file, 0, 0, null);
    }

    /**
     * Writes a JPEG file to a file.
     * @param image The image to write.
     * @param file The file to write to.
     */
    public static void writeJpegFile(Image image, File file)
        throws JdaiWriteException {
        /* TODO:
           This doesn't work in read-only directories - 
           FileNotFoundException is not caught, and flow continues!?!?!? 
           Line 3 writes FileNotFoundException and stack trace to System.out
           Line 5 afterwards throws an exception caught below
           STRANGE!
        */
        try {
            BufferedImage bImage;

            if (image instanceof BufferedImage) {
                bImage = (BufferedImage)image;
            } else {
                bImage = new BufferedImage(image.getWidth(null), image.getHeight(null), 
                                           BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g2d = bImage.createGraphics();
                g2d.drawImage(image, new AffineTransform(), null);
                g2d.dispose();
            }

            Iterator writers = ImageIO.getImageWritersByFormatName("jpeg");
            ImageWriter writer = (ImageWriter)writers.next();
            ImageOutputStream ios = ImageIO.createImageOutputStream(file);
            writer.setOutput(ios);
            writer.write(bImage);
        } catch (IOException e) {
            throw new JdaiWriteException(e.getMessage());
        }
    }

    /**
     * Resizes the image in a bounding box (the image is never enlarged).
     * @param inImg The image to resize.
     * @param width Maximum width of the new image.
     * @param height Maximum height of the new image.
     * @return The (potentially) resized image.
     */
    public static Image resizeBBox(Image inImg, int width, int height) {

        /* determine the scale */
        double scaley = (double)height/(double)inImg.getHeight(null);
        double scalex = (double)width/(double)inImg.getWidth(null);
        double scale = Math.min(scaley,scalex);

        /* determine size of new image */
        int scaledW = (int)(scale*inImg.getWidth(null));
        int scaledH = (int)(scale*inImg.getHeight(null));
        
        /* Set the scale */
        AffineTransform tx = new AffineTransform();

        /* If the image is smaller than the desired image size, don't scale */
        if (scale < 1.0d) {
            tx.scale(scale, scale);
        } else {
            return inImg;
        }

        /* create an image buffer in which to paint. */
        BufferedImage outImg = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_3BYTE_BGR); //TYPE_INT_RGB);

        /* Paint image */
        Graphics2D g2d = outImg.createGraphics();
        g2d.drawImage(inImg, tx, null);
        g2d.dispose();

        return outImg;
    }

    /**
     * Rotates the image NORTH (no rotation), EAST, SOUTH or WEST.
     * @param inImg The image to rotate.
     * @param rotation The rotation (NORTH, EAST, SOUTH, WEST).
     * @return The rotated image.
     */
    public static Image rotate(Image inImg, int rotation) {

        if (rotation != NORTH) {
            BufferedImage outImg = null;
            AffineTransform tx = new AffineTransform();
            switch (rotation) {
            case EAST:
                tx.translate(inImg.getHeight(null),0.0);
                tx.rotate(Math.toRadians(90));
                outImg = new BufferedImage(inImg.getHeight(null),
                                           inImg.getWidth(null), 
                                           BufferedImage.TYPE_3BYTE_BGR);//TYPE_INT_RGB);
                break;
            case SOUTH:
                tx.translate(inImg.getWidth(null),inImg.getHeight(null));
                tx.rotate(Math.toRadians(180));
                outImg = new BufferedImage(inImg.getWidth(null), 
                                           inImg.getHeight(null),
                                           BufferedImage.TYPE_3BYTE_BGR);//TYPE_INT_RGB);
                break;
            case WEST:
                tx.translate(0.0,inImg.getWidth(null));
                tx.rotate(Math.toRadians(270));
                outImg = new BufferedImage(inImg.getHeight(null),
                                           inImg.getWidth(null), 
                                           BufferedImage.TYPE_3BYTE_BGR);//TYPE_INT_RGB);
                break;
            }
            
            /* Paint image */
            Graphics2D g2d = outImg.createGraphics();
            g2d.drawImage(inImg, tx, null);
            g2d.dispose();
            return outImg;

        } else {
            return inImg;
        }
    }
}
