package com.sun.dtf.util;

import java.util.ArrayList;

public class CollectionsUtil {

    public static boolean isIn(Object obj, ArrayList list) { 
        for (int i = 0; i < list.size(); i++) { 
            if (obj.equals(list.get(i)))
                return true;
        }
        
        return false;
    }
    
}
