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



package com.sun.honeycomb.oa.erasure;

/** 
 * Performs the Galois Field Mathematical Operations:
 *
 * {@link #add(byte, byte) <CODE>add</CODE>}, 
 * {@link #subtract(byte, byte) <CODE>subtract</CODE>}, 
 * {@link #multiply(byte, byte) <CODE>multiply</CODE>}, 
 * {@link #divide(byte, byte) <CODE>divide</CODE>}, and 
 * {@link #power(byte, byte) <CODE>exponentiation</CODE>}
 *
 * It is fixed to GF(2^8) (GF(256))
 */

class GaloisField256 {
    
    private static final int NBITS = 8;
    public static final short MAX_SIZE = (1 << NBITS) - 1;
    /* This mask allows us to treat a byte as unsigned */
    private static final short MASK = 0xff; 
    /* 285 = 100011101 (poly=1+x^2+x^3+x^4+x^8) */
    private static final short primPol = 285;  
    private static final byte[] logTable;
    private static final byte[] invLogTable;
    private static final byte[][] mulTable; /* Used for speed */
    private static final byte[][] divTable; /* Used for speed */
    public static final byte[] linearMulTable;

    static {
        logTable = new byte[MAX_SIZE+1];
        invLogTable = new byte[MAX_SIZE+1];
        mulTable = new byte[MAX_SIZE+1][MAX_SIZE+1];
        linearMulTable = new byte[(MAX_SIZE+1) * (MAX_SIZE+1)];
        divTable = new byte[MAX_SIZE+1][MAX_SIZE+1];
        invLogTable[0] = 1;
        logTable[0]= -1; /* log[0] is actually not defined */

        /* Fill the log and exp tables */
        for(int i=1; i<MAX_SIZE+1; i++) {
            int x = (invLogTable[i-1]&MASK) * 2;
            if (x > MAX_SIZE) { x ^= primPol; }
            invLogTable[i] = (byte) x;
            logTable[(invLogTable[i]&MASK)] = (byte) i;
        }

        /* Fill the multiplication and division tables */
        
        /* These tables are not needed, but will make mult, and div a
         * little faster */
        for(int i = 0; i < MAX_SIZE + 1; i++) {
            mulTable[0][i] = mulTable[i][0] = 0;
            divTable[0][i] = 0;
        }
    
        for(int i = 0; i < MAX_SIZE + 1; i++) {
            divTable[i][0] = -1; /* It is actually not defined */
        }
    
        for(int i=1; i < MAX_SIZE + 1; i++) {
            for(int j=1; j < MAX_SIZE + 1; j++) {
                mulTable[i][j] = 
                    invLogTable[mod255((short) ((logTable[i] & MASK)+
                                                (logTable[j]&MASK)))];
                divTable[i][j] = 
                    invLogTable[mod255((short)((logTable[i]&MASK)-
                                               (logTable[j]&MASK)+ MAX_SIZE))];
            }
        }
    
        int l=0;
        for(int i=0; i < MAX_SIZE+1; i++) {
            for(int j=0; j < MAX_SIZE+1; j++) {
                linearMulTable[l++] = mulTable[i][j];
            }
        }
    }
        
    /** 
     * Add two bytes. It is just the <CODE>XOR</CODE> of the two bytes
     * @param a First operand
     * @param b Second operand
     * @return The XOR of the two operands
     */ 
    
    public static byte add(byte a, byte b) {
        return (byte) (a ^ b);
    }
    
    /** 
     * Subtract two bytes. It is just the <CODE>XOR</CODE> of the two
     * bytes (<I>i.e. Addition is the same as subtraction in a Galois
     * Field</I>)
     *
     * @param a The first operand
     * @param b The second operand
     * @return The subtraction of the two operands.
     */    
    public static byte subtract(byte a, byte b) {
	return (byte) (a ^ b);
    }

    /** 
     * The multiplication of two bytes in Galois Field. The result is
     * also a byte
     *
     * @param a The first operand
     * @param b The second operand
     * @return The result of the multiplication
     */   
    
    public static byte multiply(byte a, byte b) {
	return mulTable[a&MASK][b&MASK];
    }

    /** 
     * The division of two bytes
     * @param a The numerator
     * @param b The denominator
     * @throws ArithmeticException Occurs when there is a division by zero
     * @return The result of <CODE>a/b</CODE> truncated as a byte
     * using the Galois Field arithmetic
     */    

    public static byte divide(byte a, byte b) 
	throws ArithmeticException {

	if(b == 0) {
	    throw new ArithmeticException("Division by 0");
	}
	return divTable[a&MASK][b&MASK];
    }
  
    /** 
     * Computes the value of <CODE>a raised to the bth power</CODE>
     * @param a The base number to be raised to the <CODE>bth power</CODE>
     * @param b The exponent
     * @return The value of <CODE>a raised to the bth power</CODE>
     * truncated to a byte using the Galois Field arithmetic
     */  

    public static byte power(byte a, byte b) {
        if(b == 0) { return (byte) 1; }
        if(a == 0) { return (byte) 0; }
        short x = (short) (b & MASK);
        byte result = 1;
        for (short n = 0; n < x; n++) {
            result = multiply(a, result);
        }
        return result;
    }

    private static short mod255(short n) {
	/* n is assumed to be a positive short */
	while (n >= MAX_SIZE) {
	    n -= MAX_SIZE;
	    n = (short) ((n >>> NBITS) + (n & MAX_SIZE));
        }
	return (short) n;
    }

    /** 
     * A simple test for the class
     */  
    
    public static void main(String[] args) {
	System.out.println(MASK);
	System.out.println("14 x 33 = "+(mulTable[14][33]&MASK));
	System.out.println("33 x 14 = "+(mulTable[33][14]&MASK));
	System.out.println("33 / 14 = "+(divTable[33][14]&MASK));
	System.out.println("14 / 33 = "+(divTable[14][33]&MASK));       
	System.out.println("log(255) = "+(logTable[255]&MASK));
	System.out.println("invLog(255) = "+(invLogTable[255]&MASK));
    }
}
