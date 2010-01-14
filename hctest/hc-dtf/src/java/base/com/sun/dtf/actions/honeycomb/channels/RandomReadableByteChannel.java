package com.sun.dtf.actions.honeycomb.channels;

import java.util.Random;


/**
 * 
 * @author Rodney Gomes
 *
 */
public class RandomReadableByteChannel extends BufferReadableByteChannel {
   
    private int BUFFER_SIZE=1024*1024; // MB
    
    private int RANDOM_SEED=123456789; 
        // hard coded seed but the offset changes the actual pattern
    
    public RandomReadableByteChannel(long offset, long length) {
        super(offset, null,length);
        Random random = new Random(RANDOM_SEED);
        byte[] bytes = new byte[BUFFER_SIZE];
        random.nextBytes(bytes);
        init(bytes,length);
    }
}
