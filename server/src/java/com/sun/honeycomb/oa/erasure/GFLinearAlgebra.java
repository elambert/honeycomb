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

import java.nio.ByteBuffer;

import com.sun.honeycomb.resources.ByteBufferPool;

/** 
 * Performs Linear Algebra functions using Galois Field byte
 * arithmetic.  <B>This is not a general linear algebra class.</B> It
 * is associated with the Reed-Solomon algorithm, and simplified to
 * work with only Vandermonde or Cauchy matrices. The Cauchy matrix
 * inverse is not yet implemented.
 *
 * <p>
 *
 * Reference: <a
 * href="http://www.cs.utk.edu/~plank/plank/papers/CS-03-504.html">
 * Note: Correction to the 1997 Tutorial on Reed-Solomon Coding</a>.
 */

final class GFLinearAlgebra {
    /** Native library name */    
    private static final String nativeLibrary = "oamath";

    /**
     * Static section to load the native library.
     */
    static {
        try {
            System.loadLibrary(nativeLibrary);
        } catch(UnsatisfiedLinkError ule) {
            System.out.println("Check LD_LIBRARY_PATH. Can't find " +
                       System.mapLibraryName(nativeLibrary) + " in " +
                       System.getProperty("java.library.path"));
        }
    }

    public static final int VANDERMONDE = 0;
    public static final int CAUCHY = 1;
    
    private int nChecks; /* number of error correction fragments only */
    private int nData; /* The number of data fragments only */
    private int matrixType = VANDERMONDE;  /* Default type is Vandermonde */
    private byte forwardMatrix[][];
    private byte linearForward[];
    
    /** 
     * Constructs a GFLinearAlgebra class with the specified
     * <CODE>nChecks</CODE>, <CODE>nData</CODE>, and
     * <CODE>matrixType</CODE>
     
     * @param nChecks The number of parity fragments in the 
     * {@link ReedSolomonCodec Reed-Solomon Code}.
     
     * @param nData The number of data fragments in the 
     * {@link ReedSolomonCodec Reed-Solomon Code}.
     
     * @param matrixType Currently only GFLinearAlgebra.VANDERMONDE is
     * implemented
     */ 
    
    public GFLinearAlgebra(int nChecks, int nData, int matrixType) {
        this.nChecks = nChecks;
        this.nData = nData;
        this.matrixType = matrixType;

        setMatrixRows();
    }
    
    /* This is not a general inverse. It takes as input the missing
       fragment numbers and the error correction fragment numbers
       which replace them. */
    
    /* It requires that the missing frags be ordered in ascending order */
    
    /** 
     * Computes the inverse of the Vandermonde or Cauchy Matrix with the
     * missing rows in the first n-rows <I>(data rows)</I> replaced by
     * rows from the n to n+m <I>(parity rows)</I> range.
     
     * @param missing_frag Indices of the missing rows in the first n
     * rows.
     
     * @param replaced_by_frag Indices of the parity rows that are
     * available. The <CODE>replaced_by_frag.length</CODE> should be
     * <CODE> >= </CODE> to the <CODE>missing_frag.length</CODE>
     
     * @throws IllegalArgumentException is thrown if the
     * <CODE>replaced_by_frag.length</CODE> is smaller than
     * <CODE>missing_frag.length</CODE>
     
     * @return The inverse matrix of the modified Vandermonde or Cauch
     * matrix. This inverse should always exist since the modified
     * Vandermonde or Cauchy matrix is always non-singular
     */    
    public byte[][] getInverse(int missing_frag[], int replaced_by_frag[]) 
        throws IllegalArgumentException {
        
        if (missing_frag == null) {
            return identityMatrix(nData);
        }
        
        if (replaced_by_frag == null) {
            throw new IllegalArgumentException("Error correction frag not " +
                                               "specified");
        }
        
        if(missing_frag.length > replaced_by_frag.length) {
            throw new IllegalArgumentException("csum frags <  missing " +
                                               "fragments");
        }
        
        /* i.e. data frag not missing */
        int [] available_data_frags = new int[nData - missing_frag.length]; 
        
        byte[][] inverse = identityMatrix(nData);
        byte [][] result = new byte[nData][]; 
        int first_replacement_row = nData - missing_frag.length;
        int c=0;
        int c1=0;
        for(int i = 0; i < nData;i++) {
            if((c == missing_frag.length) || (i < missing_frag[c])) {
                available_data_frags[c1++] = i;
            } else {
                c++;
            }
        }
        
        /* Start Gaussian Elimination */
        /* We make use of some properties of the Galois Fields */
        /* E.g. we use the fact that 0 - x = x in Galois Field */
        /* Also we make use of the fact that both the Vandermonde or
           Cauchy matrices are guaranteed to be non-singular, so I do
           not do any checking of singularity, when selecting a
           pivot */
        c=0;
        for(int i = first_replacement_row; i < nData;i++) {
            for(int j = 0; j < available_data_frags.length; j++) {
                inverse[i][j] =
                    (byte) (forwardMatrix[replaced_by_frag[c]]
                            [available_data_frags[j]]^inverse[i][j]); 
            }
            c++;
        }
        
        byte workingMatrix[][] =
            new byte[missing_frag.length][missing_frag.length];
        for(int i = 0; i < missing_frag.length;i++) {
            for(int j=0;j<missing_frag.length;j++) {
                workingMatrix[i][j] = forwardMatrix[replaced_by_frag[i]]
                    [missing_frag[j]];
            }
        }
        
        byte pivot, multiplier;
        for(int i = 0; i < missing_frag.length ; i++) {
            pivot = workingMatrix[i][i];
            /* divide the row by the pivot */
            for(int j = i; j<missing_frag.length;j++) {
                workingMatrix[i][j] = GaloisField256.
                    divide(workingMatrix[i][j],pivot);
            }
            for(int j = 0;j<nData;j++) {
                inverse[first_replacement_row+i][j] = GaloisField256.
                    divide(inverse[first_replacement_row+i][j], pivot);
            }
            
            /* Subtract from the other rows */
            for(int k = 0; k < missing_frag.length; k++) {
                if (k != i) {
                    multiplier = workingMatrix[k][i];
                    for(int j = i; j<missing_frag.length;j++) {
                        workingMatrix[k][j] = 
                            (byte) (GaloisField256.
                                    multiply(workingMatrix[i][j], multiplier) 
                                    ^ workingMatrix[k][j]);
                    }
                    for(int j = 0; j<nData; j++) {
                        inverse[first_replacement_row+k][j] = 
                            (byte) (GaloisField256.
                                    multiply
                                    (inverse[first_replacement_row+i][j], 
                                     multiplier) ^ 
                                    inverse[first_replacement_row+k][j]);
                    }
                }
            }
        }
        
        /* arrange rows */
        int p1=0;
        int p2=0;
        for(int i=0;i<nData;i++) {
            if((p2 < missing_frag.length) && (i == missing_frag[p2])) {
                result[i] = inverse[first_replacement_row+p2];
                p2++;
            } else {
                result[i] = inverse[p1];
                p1++;
            }
        }
        return result;
    }
    
    /** 
     * Multiplies two matrices using Galois Field     
     * @param a First matrix operand
     * @param b Second matrix operand
     * @throws ArithmeticException thrown when the number of columns
     * of <CODE>a</CODE> does not equal the number of rows of
     * <CODE>b</CODE>
     * @return The result of the matrix multiplication
     */
    public byte[][] matmul(byte[][] a, byte[][] b) throws ArithmeticException {
        /* Check the dimensions */
        int aRows = a.length;
        int aColumns = a[0].length;
        int bRows = b.length;
        int bColumns = b[0].length;
        byte [][] result = new byte[aRows][bColumns];
        if (aColumns != bRows) {
            throw new ArithmeticException("A cols don't match num rows of B");
        }
        for(int i=0; i<aRows;i++) {
            for(int j = 0; j < bColumns; j++) {
                result[i][j] = 0;
                for(int k = 0; k < aColumns; k++) {
                    result[i][j] =
                        GaloisField256.add(result[i][j], 
                                           GaloisField256.
                                           multiply(a[i][k],b[k][j]));
                }
            }
        }
        return result;
    }

    /** 
     * Generate the parity fragments by multiplying the data fragments
     * with the lower rows <I>parity rows</I>of the Vandermonde or
     * Cauchy matrix
     
     * @param dataFrags A ByteBuffer array of the data fragments in order.
     
     * @throws ArithmeticException is thrown if the number of data
     * frag rows does not match the number of columns in the
     * Vandermonde or Cauchy matrix
     
     * @return An array of the parity fragments in order
     */    
    
    public ByteBuffer[] generateParityFrags(ByteBuffer[] dataFrags) 
        throws ArithmeticException {
        
        byte[][] dataFragsAsBytes = new byte[nData][];
        for(int i = 0; i < nData; i++) {
            dataFragsAsBytes[i] = new byte[dataFrags[i].limit()];
            dataFrags[i].get(dataFragsAsBytes[i]);
            dataFrags[i].flip();
        }
        byte[][] checkFragsAsBytes = matmul(forwardMatrix, dataFragsAsBytes);
        ByteBuffer[] checkFragsBuffers = new ByteBuffer[nChecks];
        for(int i = 0; i < nChecks; i++) { 
	    checkFragsBuffers[i] = ByteBuffer.wrap(checkFragsAsBytes[i]);
	}
        return checkFragsBuffers;
    }
    
    /**
     * A faster version of the above. Returns all the parity frags in one
     * buffer.
     */
    public ByteBuffer generateParityFragsFast(ByteBuffer block, int fragSize) 
        throws ArithmeticException {
        // NOTE - caller is responsible for returning this to pool
        ByteBuffer parityResult = 
            ByteBufferPool.getInstance().checkOutBuffer(fragSize*nChecks);
    
        nativeGenerateParity(block.array(), nData, nChecks, fragSize, 
                             linearForward, GaloisField256.linearMulTable,
                             (short)(GaloisField256.MAX_SIZE+1),
                             parityResult.array());
        parityResult.limit(parityResult.capacity());
        parityResult.position(0);
        return parityResult;
    }

    /**
     * Method to calculate parity fragments using the reed solomon algorithm.
     *
     * @param dataFragments the array of fixed size data fragments
     * @param parityFragments the array to store the parity fragments
     */
    public void calculateParityFragments(ByteBuffer[] dataFragments,
                                         ByteBuffer[] parityFragments,
                                         int fragSize)
        throws ArithmeticException {
        nativeCalculateParity(dataFragments,
                              parityFragments,
                              nData,
                              nChecks,
                              fragSize, 
                              linearForward,
                              GaloisField256.linearMulTable,
                              (short)(GaloisField256.MAX_SIZE+1));
    }

    /* Native implementation, that is faster due to ptr arith., etc. */
    private native void nativeCalculateParity(ByteBuffer[] dataFragments,
                                              ByteBuffer[] parityFragments,
                                              int nData,
                                              int nChecks, 
                                              int fragSize,
                                              byte[] linearForward,
                                              byte[] mulTable,
                                              short multTableRowWidth)
        throws IllegalArgumentException;

    /* Native implementation, that is faster due to ptr arith., etc. */
    private native void nativeGenerateParity(byte[] block,
                                             int nData,
                                             int nChecks, 
                                             int fragSize,
                                             byte[] linearForward,
                                             byte[] mulTable, 
                                             short multTableRowWidth,
                                             byte[] parityResult);

    private void setMatrixRows() {
        /* The forward matrix is the lower m rows of an (n + m) x n
         * Vandermonde matrix */
        
        /* The matrix is manipulated such that the upper n rows are
         * the identity matrix */
        
        forwardMatrix = new byte[nChecks][nData];
        linearForward = new byte[nChecks * nData];
        if(matrixType == CAUCHY) { // not implemented 
            throw new IllegalArgumentException("CAUCHY not implemented");
        } else {
            // this is a temporary matrix 
            byte[][] upperVandermonde = new byte[nData][nData]; 
            for(short i = 0; i < nData; i++) {
                for(short j = 0; j < nData; j++) {
                    upperVandermonde[i][j] = 
                        GaloisField256.power((byte) i, (byte) j);
                }
            }
            
            for(short i = 0; i < nChecks; i++) {
                for(short j = 0; j < nData; j++) {
                    forwardMatrix[i][j] = 
                        GaloisField256.power((byte) (i+nData), (byte) j);
                }
            }
            
            // now transform the Uppervandermonde to unity and make
            // the necessary changes to the forward matrix
            byte pivot = 0;
            for(short i = 1; i < nData; i++) {
                // find the pivot
                if(upperVandermonde[i][i] != 0) {
		    pivot = upperVandermonde[i][i];
		} else { // find a non-zero pivot and swap columns
                    boolean pivotNotFound = true;
                    short x = (short) (i+1);
                    while(pivotNotFound) {
                        if(upperVandermonde[i][x] == 0) { 
			    x++;
			} else {
                            pivotNotFound = false;
                            pivot = upperVandermonde[i][x];
                        }
                    }
                    // swap columns 
                    swapColumns(upperVandermonde, i, x);
                    swapColumns(forwardMatrix, i, x);
                }
                
                // divide by the pivot
                divideColumn(upperVandermonde, i, pivot);
                divideColumn(forwardMatrix, i, pivot);
                
                // zero all the previous columns in the upperVandermonde
                // and do the equivalent manipulations on the forward matrix
                for(short j = 0; j < i; j++) {
                    byte multiplier = upperVandermonde[i][j];
                    addMulColumn(upperVandermonde, i, j, multiplier);
                    addMulColumn(forwardMatrix, i, j, multiplier);
                }
                
                for(short j = (short) (i+1); j < nData; j++) {
                    byte multiplier = upperVandermonde[i][j];
                    addMulColumn(upperVandermonde, i, j, multiplier);
                    addMulColumn(forwardMatrix, i, j, multiplier);
                }
            }
        }
        
        short l=0;
        for(short i = 0; i < nChecks; i++) {
            for(short j = 0; j < nData; j++) {
                linearForward[l++] = forwardMatrix[i][j];
            }
        }
    }
    
    // I made the following methods private because they are not
    // general purpose for example, there is no error checking
    private static void swapColumns(byte[][] a, short column1, short column2) {
        byte temp;
        for(int i = 0; i < a.length; i++) {
            temp = a[i][column1];
            a[i][column1] = a[i][column2];
            a[i][column2] = temp;
        }
    }
    
    private static void divideColumn(byte[][] a, short columnNum, byte value) {
        for(int i = 0; i < a.length; i++) {
            a[i][columnNum] = GaloisField256.divide(a[i][columnNum], value);
        }
    }
    
    private static void addMulColumn(byte[][] a, short fromColumn, 
                                     short toColumn, byte multiplier) {
        for(int i = 0; i < a.length; i++) {
            a[i][toColumn] = 
                GaloisField256.add(GaloisField256.
                                   multiply(a[i][fromColumn], multiplier), 
                                   a[i][toColumn]);
        }
    }

    /** A utility to generate an identity square matrix of a specific size
     * @param n The rank of the identity matrix
     * @return The square identity matrix of rank <CODE>n</CODE>
     */    
    public static byte[][] identityMatrix(int n) {
        // We do not use the sparsity property
        byte[][] result = new byte[n][n];
        for(int i=0; i< n;i++) {
            result[i][i] = 1;
        }
        return result;
    }
}
