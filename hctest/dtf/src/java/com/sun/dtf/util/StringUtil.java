package com.sun.dtf.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {
    /**
     * 
     * @param str1
     * @param str2
     * @return
     */
    public static boolean equals(String str1, String str2) {
        if (str1 == null || str2 == null)
            return true;
        else
            return str1.equals(str2);
    }
   
    /**
     * 
     * @param str1
     * @param str2
     * @return
     */
    public static boolean equalsIgnoreCase(String str1, String str2) {
        if (str1 == null || str2 == null)
            return true;
        else
            return str1.equalsIgnoreCase(str2);
    }
   
    /**
     * Natural comparison for strings containing numbers and alpha numeric 
     * sequences. This is really useful for ordering files in a directory but
     * even more useful for comparing arbitrary strings that can be alphanumeric
     * or just a number inside a string.
     *  
     * @param str1
     * @param str2
     * @return
     */
    public static int naturalCompare(String str1, String str2) { 
       Pattern num = Pattern.compile("[0-9]+");
       Pattern nan = Pattern.compile("[^0-9]+");
    
       while (str1 != null && str1.trim().length() != 0 && 
              str2 != null && str2.trim().length() != 0) { 
           
           Matcher num1 = num.matcher(str1);
           Matcher num2 = num.matcher(str2);
           
           if (num1.find() && num2.find()) { 
               String n1 = num1.group();
               String n2 = num2.group();
              
               long l1 = new Long(n1).longValue();
               long l2 = new Long(n2).longValue();
              
               if (l1 == l2) { 
                   str1 = str1.substring(num1.end(),str1.length());
                   str2 = str2.substring(num2.end(),str2.length());
               } else {
                   return (l1 < l2 ? -1 : 1);
               }
           } else { 
               Matcher nan1 = nan.matcher(str1);
               Matcher nan2 = nan.matcher(str2);
               
               if (nan1.find() && nan2.find()) { 
                   String s1 = nan1.group();
                   String s2 = nan2.group();
                  
                   if (s1.equals(s2)) {
                       str1 = str1.substring(nan1.end(),str1.length());
                       str2 = str2.substring(nan2.end(),str2.length());
                   } else { 
                       return s1.compareTo(s2);
                   }
               } else { 
                   // nan and number cna't be compared
                   return -1;
               }
           }
       } 
       
       return 0; 
    }

    /**
     * 
     * @param name
     * @param string
     * @param length
     * @return
     */
    public static String padString(String name, char pad, int length) {
        if (name.length() <= length) { 
            int padlength = length - name.length();
            for (int i = 0; i < padlength; i++)
                name = name + pad;
            
            return name;
        }
        
        return name.substring(0,name.length()-3) + "..";
    }
   
    /**
     * 
     * @param source
     * @param pattern
     * @param replace
     * @return
     */
    public static String replace(String source, String pattern, String replace) {
        if (source != null) {
            final int len = pattern.length();
            StringBuffer sb = new StringBuffer();
            int found = -1;
            int start = 0;

            while ((found = source.indexOf(pattern, start)) != -1) {
                sb.append(source.substring(start, found));
                sb.append(replace);
                start = found + len;
            }

            sb.append(source.substring(start));

            return sb.toString();
        } else
            return "";
    }
    
    public static boolean isNumber(String number) { 
        try { 
            Double num = new Double(number);
            return true;
        } catch (NumberFormatException e) { 
            return false;
        }
    }
}
