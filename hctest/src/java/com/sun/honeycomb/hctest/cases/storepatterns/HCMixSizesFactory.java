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



package com.sun.honeycomb.hctest.cases.storepatterns;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import com.sun.honeycomb.hctest.cases.interfaces.HCFileSizeCases;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.util.HoneycombTestException;


public class HCMixSizesFactory {
	
	private static Hashtable mixes = new Hashtable();
	
	private static ArrayList mix1 = new ArrayList();
	private static ArrayList mix2 = new ArrayList();
	private static ArrayList mix3 = new ArrayList();
	private static ArrayList mix4 = new ArrayList(); 
	private static ArrayList ofotomix = new ArrayList();
	private static ArrayList boundarymix = new ArrayList();
	private static ArrayList multichunk = new ArrayList();
	private static ArrayList stanfordmix = new ArrayList();
        private static ArrayList fewmegsmix = new ArrayList();
        private static ArrayList gemix = new ArrayList();
    

	static {
		mix1.add(new Long(1024));
		mix1.add(new Long(HoneycombTestConstants.ONE_MEGABYTE));
		mix1.add(new Long(50*HoneycombTestConstants.ONE_MEGABYTE));
		
		mix2.add(new Long(50*HoneycombTestConstants.ONE_MEGABYTE));
		mix2.add(new Long(100*HoneycombTestConstants.ONE_MEGABYTE));
		mix2.add(new Long(500*HoneycombTestConstants.ONE_MEGABYTE));
		
		mix3.add(new Long(500*HoneycombTestConstants.ONE_MEGABYTE));
		mix3.add(new Long(HoneycombTestConstants.ONE_GIGABYTE));
		mix3.add(new Long(10*HoneycombTestConstants.ONE_GIGABYTE));
		
		mix4.add(new Long(100*HoneycombTestConstants.ONE_GIGABYTE));
		
                // 1, 2, 3, 4, 5 MB
                fewmegsmix.add(new Long(1*HoneycombTestConstants.ONE_MEGABYTE));
                fewmegsmix.add(new Long(2*HoneycombTestConstants.ONE_MEGABYTE));
                fewmegsmix.add(new Long(3*HoneycombTestConstants.ONE_MEGABYTE));
                fewmegsmix.add(new Long(4*HoneycombTestConstants.ONE_MEGABYTE));
                fewmegsmix.add(new Long(5*HoneycombTestConstants.ONE_MEGABYTE));

		ofotomix.add(new Long(900*1024));
		ofotomix.add(new Long(5*1024));
		ofotomix.add(new Long(15*1024));
		ofotomix.add(new Long(50*1024));
		
		boundarymix = HCFileSizeCases.getSizeList();

                // GE mix: 100k, 500k, 1MB, 10MB
                gemix.add(new Long(100*1024));
                gemix.add(new Long(500*1024));
                gemix.add(new Long(1*HoneycombTestConstants.ONE_MEGABYTE));
                gemix.add(new Long(10*HoneycombTestConstants.ONE_MEGABYTE));
		
		// 1.5GB		
		multichunk.add(new Long(HoneycombTestConstants.ONE_GIGABYTE + HoneycombTestConstants.ONE_GIGABYTE/2));
		// 4GB
		multichunk.add(new Long(HoneycombTestConstants.ONE_GIGABYTE*4));

                // stanford mix: 1MB, 5MB, 10MB
                //
                // Hoping to control the ratio here, thus multiple entries.
                //
                // do a random 1 in 15 here
                Random rand = new Random(System.currentTimeMillis());
                int doBig= Math.abs(rand.nextInt())%15;
                if(doBig==1) {
                    stanfordmix.add(new Long(2*HoneycombTestConstants.ONE_GIGABYTE));
                    stanfordmix.add(new Long(4*HoneycombTestConstants.ONE_GIGABYTE));
                }
                for(int i=0;i<100;i++) {
                    stanfordmix.add(new Long(1*HoneycombTestConstants.ONE_MEGABYTE));
                    stanfordmix.add(new Long(5*HoneycombTestConstants.ONE_MEGABYTE));
                    stanfordmix.add(new Long(10*HoneycombTestConstants.ONE_MEGABYTE));
                    stanfordmix.add(new Long(1*HoneycombTestConstants.ONE_MEGABYTE));
                    stanfordmix.add(new Long(5*HoneycombTestConstants.ONE_MEGABYTE));
                    stanfordmix.add(new Long(10*HoneycombTestConstants.ONE_MEGABYTE));
                    stanfordmix.add(new Long(1*HoneycombTestConstants.ONE_MEGABYTE));
                    stanfordmix.add(new Long(5*HoneycombTestConstants.ONE_MEGABYTE));
                    stanfordmix.add(new Long(10*HoneycombTestConstants.ONE_MEGABYTE));                    
                    stanfordmix.add(new Long(300*HoneycombTestConstants.ONE_MEGABYTE));
                }


		
		mixes.put("mix1",mix1);
		mixes.put("mix2",mix2);
		mixes.put("mix3",mix3);
		mixes.put("mix4",mix4);
		mixes.put("ofotomix",ofotomix);
		mixes.put("boundarymix",boundarymix);
		mixes.put("multichunk",multichunk);
                mixes.put("stanfordmix",stanfordmix);
                mixes.put("fewmegsmix",fewmegsmix);
                mixes.put("gemix",gemix);
	}

	// TODO: add functionatlity to parse a string like mixname:{value1,value2,value3} 
	// and add this mix to the current mixes :)

	public HCMixSizesFactory() {
		super();
	}
	
	public static ArrayList getFileSizes(String mixname) throws HoneycombTestException{
		if (mixes.containsKey(mixname)){
			return (ArrayList)mixes.get(mixname);
		} else {
			throw new HoneycombTestException("Inexistent Mix name: " + mixname);
		}
	}	
}
