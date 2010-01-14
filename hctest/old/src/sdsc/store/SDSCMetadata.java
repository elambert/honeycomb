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



// org.eso.fits docs: http://www.hq.eso.org/~pgrosbol/fits_java/docs/index.html

package sdsc.store;

import org.eso.fits.*; 
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.util.Enumeration;

/** This class represents the metadata for an SDSC file */

/* To use this, define the following schema in :

honeycomb.emd.schema = \
long BITPIX, \
long NAXIS, \
long NAXIS1, \
long NAXIS2, \
long ORDATE, \
long DAYNUM, \
long CNPIX1, \
long CNPIX2, \
long PLTSCALE, \
long OBJECT1, \
long OBJECT2, \
double CRPIX1, \
double CRPIX2, \
double CRVAL1, \
double CRVAL2, \
double CROTA1, \
double CROTA2, \
double CDELT1, \
double CDELT2, \
string ORIGIN, \
string CTYPE1, \
string CTYPE2, \
string FULLHEADER, \
string origFilename, \
string mimeType, \
string password

*/

class SDSCMetadata {
    
    /** Attempts to extract and construct all md needed for SDSC app */
    public SDSCMetadata(String origFilename,
                        String mimeType,
                        String password,
                        FitsFile file) throws IllegalArgumentException {
        
        // SDSC-specific fields
        this.origFilename = origFilename;
        this.mimeType = mimeType;
        this.password = password;
        
        // Get FITS fields from prime FITS header 
        FitsHeader hdr = file.getHDUnit(0).getHeader();
        int numKeywords = hdr.getNoKeywords();
        int type = hdr.getType();
        int size = (int) hdr.getDataSize();
        if(verbose) {
            System.out.println("Original File: " + origFilename);
            System.out.println("MIME Type: " + mimeType);
            System.out.println("Password: " + password);
            System.out.println("FITS NAME: " + hdr.getName());
            System.out.println("FITS Type: " + Fits.getType(type));
            System.out.println("FITS Keyword Count: " + numKeywords);
            System.out.println("FITS Size: " + size);
            System.out.println("-----");
        }
        
        StringBuffer fullHeaderBuf = new StringBuffer();
        
        Enumeration en = hdr.getKeywords();
        while (en.hasMoreElements()) {
            FitsKeyword kw = (FitsKeyword) en.nextElement();
            String f = kw.getName();
            
            // Append the header to the full header string
            fullHeaderBuf.append(kw.toString() + fullHeaderSep);
            
            // If this is a field we care about, grab it
            switch(kw.getType()) {
                
            case(FitsKeyword.INTEGER):
                
                // Longs
                
                Long l = new Long(kw.getInt());

                if(f.equals(BITPIX_NAME)) {
                    BITPIX = l;
                    found++;
                } else if(f.equals(NAXIS_NAME)) {
                    NAXIS = l;
                    found++;
                } else if(f.equals(NAXIS1_NAME)) {
                    NAXIS1 = l;
                    found++;
                } else if(f.equals(NAXIS2_NAME)) {
                    NAXIS2 = l;
                    found++;
                } else if(f.equals(ORDATE_NAME)) {
                    ORDATE = l;
                    found++;
                } else if(f.equals(DAYNUM_NAME)) {
                    DAYNUM = l;
                    found++;
                } else if(f.equals(PLTSCALE_NAME)) {
                    PLTSCALE = l;
                    found++;
                } else if(f.equals(OBJECT1_NAME)) {
                    OBJECT1 = l;
                    found++;
                } else if(f.equals(OBJECT2_NAME)) {
                    OBJECT2 = l;
                    found++;
                }
                
                break;
                
            case(FitsKeyword.REAL):
                
                // Doubles
                
                Double d = new Double(kw.getReal());
                
                if(f.equals(CRPIX1_NAME)) {
                    CRPIX1 = d;
                    found++;
                } else if(f.equals(CRPIX2_NAME)) {
                    CRPIX2 = d;
                    found++;
                } else if(f.equals(CRVAL1_NAME)) {
                    CRVAL1 = d;
                    found++;
                } else if(f.equals(CRVAL2_NAME)) {
                    CRVAL2 = d;
                    found++;
                } else if(f.equals(CROTA1_NAME)) {
                    CROTA1 = d;
                    found++;
                } else if(f.equals(CROTA2_NAME)) {
                    CROTA2 = d;
                    found++;
                } else if(f.equals(CDELT1_NAME)) {
                    CDELT1 = d;
                    found++;
                } else if(f.equals(CDELT2_NAME)) {
                    CDELT2 = d;
                    found++;
                }
                break;
                
            case(FitsKeyword.STRING):
                
                // Strings
                
                String s = kw.getString();
                
                if(f.equals(ORIGIN_NAME)) {
                    ORIGIN = s;
                    found++;
                } else if (f.equals(CTYPE1_NAME)) {
                    CTYPE1 = s;
                    found++;
                } else if (f.equals(CTYPE2_NAME)) {
                    CTYPE2 = s;
                    found++;
                }
                
                break;

            default:
            }
        }
        
        FULLHEADER = fullHeaderBuf.toString();
        
        if(verbose) {
            System.out.println(fullHeaderBuf);
            System.out.println("-----");
            System.out.println("found " + found + " of " + numFields + 
                               " headers."); 
        }
    }
    
    boolean helper(String name, Object value, boolean first, StringBuffer sb) {
        
        if(value == null) {
            return first;
        }
        
        if(first) {
            first = false;    
        } else {
            sb.append(HCMDSep);
        }
        
        sb.append(name);
        sb.append(HCMDSep);
        sb.append(CSVEscape(value.toString()));
        
        return first;
    }

    /** Formats the SDSCMetadata in HC's n1,v1,n2,v2...HTTP MD store format */
    public String getFormURLEncodedCSV() {
        
        StringBuffer sb = new StringBuffer();
        
        boolean first = true;
        
        // FITS Longs 
        first = helper(BITPIX_NAME, BITPIX, first, sb);
        first = helper(NAXIS_NAME, NAXIS, first, sb);
        first = helper(NAXIS1_NAME, NAXIS1, first, sb);
        first = helper(NAXIS2_NAME, NAXIS2, first, sb);
        first = helper(ORDATE_NAME, ORDATE, first, sb);
        first = helper(DAYNUM_NAME, DAYNUM, first, sb);
        first = helper(CNPIX1_NAME, CNPIX1, first, sb);
        first = helper(CNPIX2_NAME, CNPIX2, first, sb);
        first = helper(PLTSCALE_NAME, PLTSCALE, first, sb);
        first = helper(OBJECT1_NAME, OBJECT1, first, sb);
        first = helper(OBJECT2_NAME, OBJECT2, first, sb);
        
        // FITS Doubles
        first = helper(CRPIX1_NAME, CRPIX1, first, sb);
        first = helper(CRPIX2_NAME, CRPIX2, first, sb);
        first = helper(CRVAL1_NAME, CRVAL1, first, sb);
        first = helper(CRVAL2_NAME, CRVAL2, first, sb);
        first = helper(CROTA1_NAME, CROTA1, first, sb);
        first = helper(CROTA2_NAME, CROTA2, first, sb);
        first = helper(CDELT1_NAME, CDELT1, first, sb);
        first = helper(CDELT2_NAME, CDELT2, first, sb);
        
        // FITS Strings
        first = helper(ORIGIN_NAME, ORIGIN, first, sb);
        first = helper(CTYPE1_NAME, CTYPE1, first, sb);
        first = helper(CTYPE2_NAME, CTYPE2, first, sb);
        
        // FULL FITS Header
        first = helper(FULLHEADER_NAME, FULLHEADER, first, sb);

        // SDSC-Specifc Strings
        first = helper(origFilenameName, origFilename, first, sb);
        first = helper(mimeTypeName, mimeType, first, sb);
        first = helper(passwordName, password, first, sb);
       
        String result = "";
        try {
            result = URLEncoder.encode(sb.toString(), ENCODING);
        } catch(UnsupportedEncodingException uee) {
            System.out.println(ENCODING + " is not a supported encoding");
        }
        return result;
    }
    
    public int fieldsFound() {
        return found;
    }

    public int numInterestingFields() {
        return numFields;
    }

    private static String CSVEscape(String s) {
        return s.replaceAll(",", "\\\\,");
    }

    // SDSC FITS Metadata Fieldnames
    
    // Longs
    static final String BITPIX_NAME = "BITPIX";
    static final String NAXIS_NAME =  "NAXIS";
    static final String NAXIS1_NAME = "NAXIS1";
    static final String NAXIS2_NAME = "NAXIS2";
    static final String ORDATE_NAME = "ORDATE";
    static final String DAYNUM_NAME = "DAYNUM";
    static final String CNPIX1_NAME = "CNPIX1";
    static final String CNPIX2_NAME = "CNPIX2";
    static final String PLTSCALE_NAME = "PLTSCALE";
    static final String OBJECT1_NAME = "OBJECT1";
    static final String OBJECT2_NAME = "OBJECT2";
    
    // Doubles
    static final String CRPIX1_NAME = "CRPIX1";
    static final String CRPIX2_NAME = "CRPIX2";
    static final String CRVAL1_NAME = "CRVAL1";
    static final String CRVAL2_NAME = "CRVAL2";
    static final String CROTA1_NAME = "CROTA1";
    static final String CROTA2_NAME = "CROTA2";
    static final String CDELT1_NAME = "CDELT1";
    static final String CDELT2_NAME = "CDELT2";

    // Strings
    static final String ORIGIN_NAME = "ORIGIN";
    static final String CTYPE1_NAME = "CTYPE1";
    static final String CTYPE2_NAME = "CTYPE2";
    static final String FULLHEADER_NAME = "FULLHEADER";
    
    // SDSC-Specific Fieldnames
    static final String ORIGFILENAME_NAME = "origFilename";
    static final String MIMETYPE_NAME = "mimeType";
    static final String PASSWORD_NAME = "password";
    
    // SDSC FITS Metadata Fields
    
    // Longs
    Long BITPIX = null;
    Long NAXIS = null;
    Long NAXIS1 = null;
    Long NAXIS2 = null;
    Long ORDATE = null;
    Long DAYNUM = null;
    Long CNPIX1 = null;
    Long CNPIX2 = null;
    Long PLTSCALE = null;
    Long OBJECT1 = null;
    Long OBJECT2 = null;

    // Doubles
    Double CRPIX1 = null;
    Double CRPIX2 = null;
    Double CRVAL1 = null;
    Double CRVAL2 = null;
    Double CROTA1 = null;
    Double CROTA2 = null;
    Double CDELT1 = null;
    Double CDELT2 = null;

    // Strings
    String ORIGIN = null;
    String CTYPE1 = null;
    String CTYPE2 = null;
    String FULLHEADER = null;  // Concatination of all other headers

    // SDSC-specific Fieldnames
    String origFilenameName = "origFilename";
    String mimeTypeName = "mimeType";
    String passwordName = "password";
    
    // SDSC-specific Fields
    
    String origFilename = null;
    String mimeType = null;;
    String password = null;

    // Output FITS Info and Headers
    boolean verbose = false;
    
    // Numver of FITS fields SDSC is interested in
    static final int numFields = 22;

    // Number of FITS fields we found which SDSC is interested in
    int found = 0;

    // FULLHEADER Formatting
    static final char HCMDSep = ',';
    static final char fullHeaderSep = '\n';
    
    // URL Encoding
    private static final String ENCODING="UTF-8";
                  
}
