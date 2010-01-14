// JdaiPhotoInfoEditorBundle_da_DK.java Strings for JdaiPhotoInfoEditor
// $Id: JdaiPhotoInfoEditorBundle_da_DK.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import java.util.ListResourceBundle;


public class JdaiPhotoInfoEditorBundle_da_DK extends ListResourceBundle {

    public Object[][] getContents() {
        return contents;
    }
    private Object[][] contents = {
        {"Thumbnail","Thumbnail"},
        {"NoThumbnail","Intet thumbnail"},
        {"Caption","Billedtekst:"},
        {"CaptionMnemonic",new Character('B')},
        {"Keywords","Nøgleord:"},
        {"KeywordsMnemonic",new Character('N')},
        {"SectionCaption","Sektionstekst:"},
        {"SectionCaptionMnemonic",new Character('S')},
        {"SectionKeywords","Sektionsnøgleord:"},
        {"SectionKeywordsMnemonic",new Character('e')},
        {"UnableRead","Kan ikke læse billedinformationer"},
        {"UnableWrite","Kan ikke skrive billedinformationer"},
		{"WindowTitle", "JDAI indtastning af billedinformationer"},
    };
}
