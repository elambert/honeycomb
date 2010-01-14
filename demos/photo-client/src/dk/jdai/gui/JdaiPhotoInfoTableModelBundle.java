// JdaiPhotoInfoTableModelBundle.java
// $Id: JdaiPhotoInfoTableModelBundle.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import java.util.ListResourceBundle;


public class JdaiPhotoInfoTableModelBundle extends ListResourceBundle {

    public Object[][] getContents() {
        return contents;
    }
    private Object[][] contents = {
        {"TagHeader","Property"},
        {"ValueHeader","Value"},
    };
}
