package dk.jdai.model;

import com.sun.imageio.plugins.jpeg.JPEGImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;

/** 
 * The <code>EXIFInfo</code> class is used to read EXIF information
 * from JPEG pictures, that contain such information.
 *
 * @author <a href="mailto:Mikkel@YdeKjaer.dk">Mikkel Yde Kjær</a>
 * @version $Revision: 1.2 $
 */
public class EXIFInfo {

    private boolean intelByteAlign;
    private Map properties = null;
    private File file;
    private byte[] buf;
    private BufferedImage thumbnail;

    private static ResourceBundle labels = ResourceBundle.getBundle("dk.jdai.model.JdaiEXIFInfoBundle",Locale.getDefault());

    /**
     * Creates a new <code>EXIFInfo</code> instance. 
     * Calling just the constructor will not read anything from file.
     *
     * @param file the file holding an EXIF JPEG image
     */
    public EXIFInfo(File file) {
        this.file = file;
    }

    /**
     * This method does the actual read of EXIF meta data. If the
     * specified file is not a JPEG in EXIF format an empty map will be 
     * returned. This is also the case if the file does not exist.
     *
     * @return a <code>Map</code> with all the EXIF values of the image
     */
    public Map getEXIFMetaData()  {
    
        if (properties != null) {
            return properties;
        }
        try {
            properties = new HashMap();
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
            byte head[] = new byte[4];
            byte lenBuf[] = new byte[2];
            byte exifHead[] = new byte[6];
            int length;
            int ifd0Offset;
            int ifd1Offset;
            
            in.read(head);
            
            if (((head[0] & 0xff) != 0xff) 
                || ((head[1] & 0xff) != 0xd8)
                || ((head[2] & 0xff) != 0xff)
                || ((head[3] & 0xff) != 0xe1)) {
                // Not EXIF return empty Map
                return properties;
            }
            
            in.read(lenBuf);
            length = (lenBuf[0] & 0xff) << 8;
            length |= lenBuf[1] & 0xff;
            length -= 2;  // JPEG length includes itself, we don't

            in.read(exifHead);
            
            if ((exifHead[0] != 'E') 
                || (exifHead[1] != 'x')
                || (exifHead[2] != 'i')
                || (exifHead[3] != 'f')
                || (exifHead[4] != 0)
                || (exifHead[5] != 0)) {
                throw new IOException(labels.getString("HeaderNotCorrect"));
            }
                

            buf = new byte[length-6];
            in.read(buf);
            in.close();
                        
            // Read TIFF header
            if (buf[0] == 'I' && buf[1] == 'I') {
                setIntelByteAlign(true);
            } else if (buf[0] == 'M' && buf[1] == 'M') {
                setIntelByteAlign(false);
            } else {
                throw new IOException(labels.getString("WrongByteAlign"));
            }
            if(readNumber(buf,2,2) != 42) {
                throw new IOException(labels.getString("HeaderNotCorrect"));
            }
            ifd0Offset = readNumber(buf,4,4);
        
            // Read information in IFD0
            ifd1Offset = readIFD(buf,8);
            
            readIFD(buf,new Integer((String)properties.get("ExifOffset")).intValue());

            // Read information in IFD1 (thumbnail)
            readIFD(buf,ifd1Offset);

            thumbnail = getThumbnail();
                
            // Garbage collect buf
            buf = null;
                
        } catch (Exception e) {
            properties = new HashMap();
        }
        return properties;
    }

    private int readNumber(byte[] buf, int bufPtr, int numBytes) {
        int result = 0;

        if (isIntelByteAlign()) {
            for(int i=numBytes-1;i>=0;i--) {
                result <<= 8;
                result |= buf[bufPtr+i] & 0xff;                
            }
        } else {
            for(int i=0;i<numBytes;i++) {
                result <<= 8;
                result |= buf[bufPtr+i] & 0xff;
            }
        }
        
        return result;
    }

    private int readNumberSigned(byte[] buf, int bufPtr, int numBytes) {
        int result = 0;

        if (isIntelByteAlign()) {
            for(int i=numBytes-1;i>=0;i--) {
                result <<= 8;
                if (i == numBytes-1) {
                    result |= buf[bufPtr+i];
                } else {
                    result |= buf[bufPtr+i] & 0xff;                
                }
            }
        } else {
            for(int i=0;i<numBytes;i++) {
                result <<= 8;
                if (i == 0) {
                    result |= buf[bufPtr+i];
                } else {
                    result |= buf[bufPtr+i] & 0xff;
                }
            }
        }
        
        return result;
    }

    private int readIFD(byte[] buf, int ptr) {
        int noEntry = readNumber(buf,ptr,2);
        ptr += 2;

        for(int i=0;i<noEntry;i++) {
            readEntry(buf,ptr);
            ptr += 12;
        }
        
        return  readNumber(buf,ptr,4);
    }

    private void readMakerIFD(byte[] buf, int ptr, String id) {
        int noEntry = readNumber(buf,ptr,2);
        ptr += 2;

        for(int i=0;i<noEntry;i++) {
            if (id.equals("Nikon2")) {
                readNikon2Entry(buf,ptr);
            }
            ptr += 12;
        }
    }

    private void readNikon2Entry(byte[] buf, int bufPtr) {
        int tag = readNumber(buf,bufPtr,2);
        int format = readNumber(buf,bufPtr+2,2);
        int numComp = readNumber(buf,bufPtr+4,4);
        String value = readValue(format,numComp,buf,bufPtr+8);
        String name = "";
        

        switch (tag) {
        case 0x0002:{
            name="MN_ISOSetting";
            int idx = value.indexOf(',');
            value = value.substring(idx+1);
            break;}
        case 0x0003:
            name="MN_ColorMode";
            break;
        case 0x0004:
            name="MN_Quality";
            break;
        case 0x0005:
            name="MN_WhiteBalance";
            break;
        case 0x0006:
            name="MN_ImageSharpening";
            break;
        case 0x0007:
            name="MN_FocusMode";
            break;
        case 0x0008:
            name="MN_FlashSetting";
            break;
        case 0x000f:
            name="MN_ISOSelection";
            break;
        case 0x0080:
            name="MN_ImageAdjustment";
            break;
        case 0x0082:
            name="MN_Adapter";
            break;
        case 0x0085:{
            name="MN_ManualFocusDistance";
            int idx = value.indexOf('/');
            int hi = new Integer(value.substring(0,idx)).intValue();
            int lo = new Integer(value.substring(idx+1)).intValue();
            value = "" + (double)hi/(double)lo;
            break;}
        case 0x0086:{
            name="MN_DigitalZoom";
            int idx = value.indexOf('/');
            int hi = new Integer(value.substring(0,idx)).intValue();
            int lo = new Integer(value.substring(idx+1)).intValue();
            value = (double)hi/(double)lo + "x";
            break;}
        case 0x0088:{
            name="MN_AFFocusPosition";
            int idx = value.indexOf(',');
            int offset = new Integer(value.substring(0,idx)).intValue();
            int num = new Integer(value.substring(idx+1)).intValue();
            switch (buf[offset+1]) {
            case 0:
                value = labels.getString("Center");
                break;
            case 1:
                value = labels.getString("Top");
                break;
            case 2:
                value = labels.getString("Bottom");
                break;
            case 3:
                value = labels.getString("Left");
                break;
            case 4:
                value = labels.getString("Right");
                break;
            }
            break;}
        default:
            return;
        }

        properties.put(name,value);
    }

    private void readEntry(byte[] buf, int bufPtr) {
        int tag = readNumber(buf,bufPtr,2);
        int format = readNumber(buf,bufPtr+2,2);
        int numComp = readNumber(buf,bufPtr+4,4);
        String value = readValue(format,numComp,buf,bufPtr+8);
        String name = "";

        switch (tag) {
        case 0x010e:
            name = "ImageDescription";
            break;
        case 0x010f:
            name = "Make";
            break;
        case 0x0110:
            name = "Model";
            break;
        case 0x0112:
            name = "Orientation";
            break;
        case 0x0131:
            name = "Software";
            break;
        case 0x0132:
            name = "DateTime";
            break;
        case 0x013e:
            name = "WhitePoint";
            break;
        case 0x013f:
            name = "PrimaryChromaticities";
            break;
        case 0x0211:
            name = "YCbCrCoefficients";
            break;
        case 0x0213:
            name = "YCbCrPositioning";
            break;
        case 0x0214:
            name = "ReferenceBlackWhite";
            break;
        case 0x8298:
            name = "Copyright";
            break;
        case 0x8769:
            name = "ExifOffset";
            break;
        case 0x829a: {
            name = "ExposureTime";
            int idx = value.indexOf('/');
            int hi = new Integer(value.substring(0,idx)).intValue();
            int lo = new Integer(value.substring(idx+1)).intValue();
            if (lo <= hi) {
            	value = "" + (Math.round((double)hi/(double)lo) + Math.round(Math.IEEEremainder(hi, lo)*100.0/(double)lo)/100.0);
            } else {
	            value = "1/" + Math.round((double)lo/(double)hi);
            }
            break;}
        case 0x829d: {
            name = "FNumber";
            int idx = value.indexOf('/');
            int hi = new Integer(value.substring(0,idx)).intValue();
            int lo = new Integer(value.substring(idx+1)).intValue();
            value = "f/" + (double)hi/(double)lo;
            break;}
        case 0x8822:
            name = "ExposureProgram";
            if (value.equals("1")) {
                value = labels.getString("Manual");
            } else if (value.equals("2")) {
                value = labels.getString("Normal");
            } else if (value.equals("3")) {
                value = labels.getString("Aperature priority");
            } else if (value.equals("4")) {
                value = labels.getString("Shutter priority");
            } else if (value.equals("5")) {
                value = labels.getString("Creative");
            } else if (value.equals("6")) {
                value = labels.getString("Action");
            } else if (value.equals("7")) {
                value = labels.getString("Portrait");
            } else if (value.equals("8")) {
                value = labels.getString("Landscape");
            }
            break;
        case 0x8827:
            name = "ISOSpeedRatings";
            break;
        case 0x9000:
            name = "ExifVersion";
            break;
        case 0x9003:
            name = "DateTimeOriginal";
            break;
        case 0x9004:
            name = "DateTimeDigitized";
            break;
        case 0x9101:
            name = "ComponentsConfiguration";
            if (value.equals("\001\002\003\0")) {
                value = labels.getString("YCbCr");
            } else if (value.equals("\004\005\006\0")) {
                value = labels.getString("RGB");
            }
            break;
        case 0x9102:
            name = "CompressedBitsPerPixel ";
            break;
        case 0x9201: {
            name = "ExposureTime";
            int idx = value.indexOf('/');
            int hi = new Integer(value.substring(0,idx)).intValue();
            int lo = new Integer(value.substring(idx+1)).intValue();
            double speed = Math.pow(2,(double)hi/(double)lo);
            if (speed < 1) {
            	value = "" + (int)(1.0/speed);
            } else {
	            value = "1/" + (int)speed;
            }
            break;}
        case 0x9202: {
            name = "FNumber";
            int idx = value.indexOf('/');
            int hi = new Integer(value.substring(0,idx)).intValue();
            int lo = new Integer(value.substring(idx+1)).intValue();
            double apval = Math.pow(Math.sqrt(2),(double)hi/(double)lo);
            value = "f/" + ((double)((int)Math.round(apval*10.0)))/10.0;
            break;}
        case 0x9203:
            name = "BrightnessValue";
            break;
        case 0x9204: {
            name = "ExposureBiasValue";
            int idx = value.indexOf('/');
            int hi = new Integer(value.substring(0,idx)).intValue();
            int lo = new Integer(value.substring(idx+1)).intValue();
            if (hi == 0) {
                value = "0";
            }
            break;}
        case 0x9205: {
            name = "MaxApertureValue";
            int idx = value.indexOf('/');
            int hi = new Integer(value.substring(0,idx)).intValue();
            int lo = new Integer(value.substring(idx+1)).intValue();
            double apval = Math.pow(Math.sqrt(2),(double)hi/(double)lo);
            value = "f/" + ((double)((int)Math.round(apval*10.0)))/10.0;
            break;}
        case 0x9206:
            name = "SubjectDistance";
            break;
        case 0x9207:
            name = "MeteringMode";
            if (value.equals("0")) {
                value = labels.getString("Unknown");
            } else if (value.equals("1")) {
                value = labels.getString("Average");
            } else if (value.equals("2")) {
                value = labels.getString("Center weigthed average");
            } else if (value.equals("3")) {
                value = labels.getString("Spot");
            } else if (value.equals("4")) {
                value = labels.getString("Multi-spot");
            } else if (value.equals("5")) {
                value = labels.getString("Multi-segment");
            } else if (value.equals("6")) {
                value = labels.getString("Partial");
            } else if (value.equals("255")) {
                value = labels.getString("Other");
            }                    
            break;
        case 0x9208:
            name = "LightSource";
            if (value.equals("0")) {
                value = labels.getString("Auto");
            } else if (value.equals("1")) {
                value = labels.getString("Daylight");
            } else if (value.equals("2")) {
                value = labels.getString("Flourescent");
            } else if (value.equals("3")) {
                value = labels.getString("Tungsten");
            } else if (value.equals("10")) {
                value = labels.getString("Flash");
            } else if (value.equals("17")) {
                value = labels.getString("Standard light A");
            } else if (value.equals("18")) {
                value = labels.getString("Standard light B");
            } else if (value.equals("19")) {
                value = labels.getString("Standard light C");
            } else if (value.equals("20")) {
                value = labels.getString("D55");
            } else if (value.equals("21")) {
                value = labels.getString("D65");
            } else if (value.equals("22")) {
                value = labels.getString("D75");
            } else if (value.equals("255")) {
                value = labels.getString("Other");
            }            
            break;
        case 0x9209:
            name = "Flash";
            if (value.equals("0")) {
                value = labels.getString("No");
            } else if (value.equals("1")) {
                value = labels.getString("Yes");
            } else if (value.equals("5")) {
                value = labels.getString("Yes, not detected");
            } else if (value.equals("7")) {
                value = labels.getString("Yes, detected");
            }
            break;
        case 0x920a: {
            name = "FocalLength";
            int idx = value.indexOf('/');
            int hi = new Integer(value.substring(0,idx)).intValue();
            int lo = new Integer(value.substring(idx+1)).intValue();
            double val = (double)hi/(double)lo;
            value = "" + val;
            break;}
        case 0x927c:{
            name = "MakerNote";
            if(format == 7) {
                int idx = value.indexOf(',');
                int offset = new Integer(value.substring(0,idx)).intValue();
                int num = new Integer(value.substring(idx+1)).intValue();
                readMaker(buf,offset);
            } else {
                readMaker(value);
            }
            break;}
        case 0x9286:
            name = "UserComment";
            break;
        case 0x9290:
            name = "SubSecTime";
            break;
        case 0x9291:
            name = "SubSecTimeOriginal";
            break;
        case 0x9292:
            name = "SubsecTimeDigitized";
            break;
        case 0xa000:
            name = "FlashPixVersion";
            break;
        case 0xa001:
            name = "ColorSpace";
            if (value.equals("1")) {
                value = labels.getString("sRGB");
            } else if (value.equals("65535")) {
                value = labels.getString("Uncalibrated");
            }
            break;
        case 0xa002:
            name = "ExifImageWidth";
            break;
        case 0xa003:
            name = "ExifImageHeight";
            break;
        case 0xa004:
            name = "RelatedSoundFile";
            break;
        case 0xa005:
            name = "ExifInteroperabilityOffset";
            break;
        case 0xa20e:
            name = "FocalPlaneXResolution";
            break;
        case 0xa20f:
            name = "FocalPlaneYResolution";
            break;
        case 0xa210:
            name = "FocalPlaneResolutionUnit";
            if (value.equals("1")) {
                value = labels.getString("No unit");
            } else if (value.equals("2")) {
                value = labels.getString("Inch");
            } else if (value.equals("3")) {
                value = labels.getString("Centimeter");
            }
            break;
        case 0xa215:
            name = "ExposureIndex";
            break;
        case 0xa217:
            name = "SensingMethod";
            break;
        case 0xa300:
            name = "FileSource";
            break;
        case 0xa301:
            name = "SceneType";
            break;
        case 0xa302:
            name = "CFAPattern";
            break;
        case 0x0100:
            name = "ImageWidth";
            break;
        case 0x0101:
            name = "ImageLength";
            break;
        case 0x0102:
            name = "BitsPerSample";
            break;
        case 0x0103:
            name = "Compression";
            if (value.equals("1")) {
                value = labels.getString("None");
            } else if (value.equals("6")) {
                value = labels.getString("Jpeg");
            }
            break;
        case 0x0106:
            name = "PhotometricInterpretation";
            if (value.equals("1")) {
                value = labels.getString("Monochrome");
            } else if (value.equals("2")) {
                value = labels.getString("RGB");
            } else if (value.equals("6")) {
                value = labels.getString("YCbCr");
            }
            break;
        case 0x0111:
            name = "StripOffsets";
            break;
        case 0x0115:
            name = "SamplesPerPixel";
            break;
        case 0x0116:
            name = "RowsPerStrip";
            break;
        case 0x0117:
            name = "StripByteConunts";
            break;
        case 0x011c:
            name = "PlanarConfiguration";
            break;
        case 0x0201:
            name = "JpegIFOffset";
            break;
        case 0x0202:
            name = "JpegIFByteCount";
            break;
        case 0x0212:
            name = "YCbCrSubSampling";
            break;
        default:
            return;
        }

        properties.put(name,value);
    }

    private String readValue(int format, int numComp,byte[] buf,int bufPtr) {
        StringBuffer retVal = new StringBuffer("");
        int offset=0;

        switch(format) {
        case 1:
        case 6:
            if (numComp <= 4) {
                for (int i=0;i<numComp;i++) {
                    if (format == 1) {
                        retVal.append((i>0?",":"")).append(buf[bufPtr+i]);
                    } else {
                        retVal.append((i>0?",":"")).append(readNumberSigned(buf,bufPtr+i,1));
                    }
                }
            } else {
                offset = readNumber(buf,bufPtr,4);
                for (int i=0;i<numComp;i++) {
                    if (format == 1) {
                        retVal.append((i>0?",":"")).append(buf[offset+i]);
                    } else {
                        retVal.append((i>0?",":"")).append(readNumberSigned(buf,offset+i,1));
                    }
                }
            }
            break;
        case 2:
            if (numComp <= 4) {
                for (int i=0;i<numComp;i++) {
                    if ((char)buf[bufPtr+i] != '\0') {
                        retVal.append(String.valueOf((char)buf[bufPtr+i]));
                    }
                }
            } else {
                offset = readNumber(buf,bufPtr,4);
                for (int i=0;i<numComp;i++) {
                    if ((char)buf[offset+i] != '\0') {
                        retVal.append(String.valueOf((char)buf[offset+i]));
                    }
                }
            }
            break;
        case 7:
            if (numComp <= 4) {
                retVal.append(""+bufPtr);
            } else {
                offset = readNumber(buf,bufPtr,4);
                retVal.append(""+offset);
            }
            retVal.append(","+numComp);
            break;
        case 3:
        case 8:
            if (numComp <= 2) {
                for (int i=0;i<numComp;i++) {
                    if (format == 3) {
                        retVal.append((i>0?",":"")).append(readNumber(buf,bufPtr+(i*2),2));
                    } else {
                        retVal.append((i>0?",":"")).append(readNumberSigned(buf,bufPtr+(i*2),2));
                    } 
                }
            } else {
                offset = readNumber(buf,bufPtr,4);
                for (int i=0;i<numComp;i++) {
                    if (format == 3) {
                        retVal.append((i>0?",":"")).append(readNumber(buf,offset+(i*2),2));
                    } else {
                        retVal.append((i>0?",":"")).append(readNumberSigned(buf,offset+(i*2),2));
                    }
                }
            }
            break;
        case 4:
        case 9:
            if (numComp <= 1) {
                for (int i=0;i<numComp;i++) {
                    if (format == 4) {
                        retVal.append((i>0?",":"")).append(readNumber(buf,bufPtr+(i*4),4));
                    } else {
                        retVal.append((i>0?",":"")).append(readNumberSigned(buf,bufPtr+(i*4),4));
                    }
                }
            } else {
                offset = readNumber(buf,bufPtr,4);
                for (int i=0;i<numComp;i++) {
                    if (format == 4) {
                        retVal.append((i>0?",":"")).append(readNumber(buf,offset+(i*4),4));
                    } else {
                        retVal.append((i>0?",":"")).append(readNumberSigned(buf,offset+(i*4),4));
                    }
                }
            }
            break;
        case 5:
        case 10:
            offset = readNumber(buf,bufPtr,4);
            for (int i=0;i<numComp;i++) {
                if (format == 5) {
                    retVal.append((i>0?",":"")).append(readNumber(buf,offset+(i*8),4)).append("/").append(
                            readNumber(buf,offset+(i*8)+4,4));
                } else {
                    retVal.append((i>0?",":"")).append(readNumberSigned(buf,offset+(i*8),4)).append("/").append(
                            readNumberSigned(buf,offset+(i*8)+4,4));
                }                    
            }
            break;
        }
        
        return retVal.toString();
    }

    private boolean isIntelByteAlign() {
        return intelByteAlign;
    } 

    private void setIntelByteAlign(boolean align) {
        intelByteAlign = align;
    }

    /**
     * Tests if a thumbnail is included in EXIF data.
     *
     * @return true if thumbnail exists
     */
    public boolean hasThumbnail() {
        getEXIFMetaData();
        return properties.containsKey("JpegIFOffset");
    }

    private int getThumbnailOffset() {
        return new Integer((String)properties.get("JpegIFOffset")).intValue();
    }

    private int getThumbnailLength() {
        return new Integer((String)properties.get("JpegIFByteCount")).intValue();
    }

    /**
     * Extracts thumbnail image from EXIF data.
     *
     * @return the thumbnail as a <code>BufferedImage</code>. null is
     * returned if no thumbnail exists. (use hasThumbnail()).
     * @exception IOException Thrown if image could not be read
     */
    public BufferedImage getThumbnail() throws IOException {
        if (!hasThumbnail()) {
            return null;
        }

        if (thumbnail != null || buf == null) {
            return thumbnail;
        } else {
            InputStream is = new ByteArrayInputStream(buf,
                                                      getThumbnailOffset(),
                                                      getThumbnailLength());
            ImageInputStream iis = new MemoryCacheImageInputStream(is);
            JPEGImageReader thumbReader = new JPEGImageReader(null);
            thumbReader.setInput(iis);
            BufferedImage ret = thumbReader.read(0, null);
            thumbReader.dispose();
            return ret;
        }
    }

    /**
     * Get the list of possible fields.
     *
     * @return The list
     */
    public static String[] getFieldList() {
        return JdaiEXIFInfoPrefs.getInstance().getFieldList();
    }

    /**
     * Get the displayname for the given field - with locale support. 
     *
     * @param field The field to get name of.
     * @return The name - or "" if field does not exist.
     */
    static public String getFieldName(String field) {
        try {
            return labels.getString(field);
        } catch (MissingResourceException e) {
            return "";
        }
    }

    private void readMaker(byte[] buf,int offset) {
        if (((String)properties.get("Make")).equalsIgnoreCase("Nikon")) {
            if (buf[offset] != 'N') {
                readMakerIFD(buf,offset,"Nikon2");
            }
        }
    }

    private void readMaker(String str) {
        readMaker(str.getBytes(),0);
    }
}
