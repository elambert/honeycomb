// JdaiPhotoInfoEditorBundle.java Strings for JdaiPhotoInfoEditor
// $Id: JdaiPhotoInfoEditorBundle.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import java.util.ListResourceBundle;


public class JdaiPhotoInfoEditorBundle extends ListResourceBundle {

    public Object[][] getContents() {
        return contents;
    }
    private Object[][] contents = {
        {"Thumbnail","Thumbnail"},
        {"NoThumbnail","No Thumbnail"},
        {"Caption","Caption:"},
        {"CaptionMnemonic",new Character('C')},
        {"Keywords","Keywords:"},
        {"KeywordsMnemonic",new Character('K')},
        {"SectionCaption","Section Caption:"},
        {"SectionCaptionMnemonic",new Character('A')},
        {"SectionKeywords","Section Keywords:"},
        {"SectionKeywordsMnemonic",new Character('Y')},
        {"UnableRead","Unable to read information store"},
        {"UnableWrite","Unable to write information store"},
        {"WindowTitle", "JDAI Info Editor"},
    };
}
