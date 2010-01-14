// JdaiExplorerBundle.java Strings for JdaiExplorer
// $Id: JdaiExplorerBundle_da_DK.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import java.util.ListResourceBundle;


public class JdaiExplorerBundle_da_DK extends ListResourceBundle {

    public Object[][] getContents() {
        return contents;
    }
    private Object[][] contents = {
        {"WindowTitle","Java Digital Album Infrastructure - Explorer"},
        {"SectionNone","Sektion: ingen"},
        {"Section","Sektion:"},
        {"SectionFormat","{0} {1} - "},
        {"NoPhoto","ingen foto"},
        {"OnePhoto","1 foto"},
        {"MorePhotos","fotos"},
        {"MorePhotosFormat","{0} {1}"},
        {"MenuFile","Fil"},
        {"FileMnemonic",new Character('F')},
        {"MenuTools","Værktøj"},
        {"ToolsMnemonic",new Character('V')},
        {"MenuPhoto","Foto"},
        {"PhotoMnemonic",new Character('o')},
        {"MenuHelp","Hjælp"},
        {"HelpMnemonic",new Character('H')},
        {"SelectRoot","Vælg Foto Rodkatalog"},
        {"SelectDir","Vælg katalog"},
        {"AboutFormat","{0}\n{1} {2}\n\n{3}\n{4}\n{5}\n\n{6}\n{7}\n{8}\n\n{9}"},
        {"About1","Java Digital Album Infrastructure"},
        {"About2","JDAI Explorer Version"},
        {"About3","(C) Copyright 2002-2003"},
        {"About4","af JDAI Development Team"},
        {"About5","(se http://www.jdai.dk/)"},
        {"About6","Dette program distribueres under"},
        {"About7","GNU General Public License (GPL) Version 2"},
        {"About8","(se http://www.gnu.org/licenses/gpl.html)"},
        {"About9","Se README.txt for information om licens på inkluderet software"},
        {"AboutTitle","Om"},
        {"ErrRead","Kunne ikke læse info store"},
        {"QuitAcc",new Integer(java.awt.event.KeyEvent.VK_Q)},
		{"ProgressIndeterminate", "Arbejder..."},
    };
}
