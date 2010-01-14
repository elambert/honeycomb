package com.sun.honeycomb.hctest.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.honeycomb.test.util.HoneycombTestException;


/**
 * @author Rodney Gomes [rodney.gomes@sun.com]
 * 
 * Regular Expression Utility Class...
 */

public abstract class HCTestRegExpUtil {

	public static boolean verifyRegExp(String data, String regexp){	
		Pattern pattern = Pattern.compile(regexp);
		Matcher matcher = pattern.matcher(data);
		
		if (matcher.matches()){
			return true;
		} else {
			return false;
		}
	}
	
	public static void verifyRegExp(String data, String regexp, String propertyname) throws HoneycombTestException {
		if (verifyRegExp(data,regexp)){
			return;
		} else {
			throw new HoneycombTestException(propertyname + " does not respect the regular expression: " + regexp);
		}
	}

}
