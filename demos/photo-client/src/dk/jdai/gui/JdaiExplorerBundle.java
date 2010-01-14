// JdaiExplorerBundle.java Strings for JdaiExplorer
// $Id: JdaiExplorerBundle.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import java.util.ListResourceBundle;


public class JdaiExplorerBundle extends ListResourceBundle {

    public Object[][] getContents() {
        return contents;
    }
    private Object[][] contents = {
        {"WindowTitle","Java Digital Album Infrastructure - Explorer"},
        {"SectionNone","Section: none"},
        {"Section","Section:"},
        {"SectionFormat","{0} {1} - "},
        {"NoPhoto","no photos"},
        {"OnePhoto","1 photo"},
        {"MorePhotos","photos"},
        {"MorePhotosFormat","{0} {1}"},
        {"MenuFile","File"},
        {"FileMnemonic",new Character('F')},
        {"MenuTools","Tools"},
        {"ToolsMnemonic",new Character('T')},
        {"MenuPhoto","Photo"},
        {"PhotoMnemonic",new Character('P')},
        {"MenuHelp","Help"},
        {"HelpMnemonic",new Character('H')},
        {"SelectRoot","Select Photo Root Directory"},
        {"SelectDir","Select Directory"},
        {"AboutFormat","{0}\n{1} {2}\n\n{3}\n{4}\n{5}\n\n{6}\n{7}\n{8}"},
        {"About1","Java Digital Album Infrastructure for Honeycomb"},
        {"About2","Based on JDAI Explorer Version"},
        {"About3","(C) Copyright 2002-2003"},
        {"About4","by the JDAI Development Team"},
        {"About5","(see http://www.jdai.dk/)"},
        {"About6","The JDAI program is distributed under the terms"},
        {"About7","of the GNU General Public License (GPL) Version 2"},
        {"About8","(see http://www.gnu.org/licenses/gpl.html)"},
        {"AboutTitle","About"},
        {"ErrRead","Could not read info store"},
        {"QuitAcc",new Integer(java.awt.event.KeyEvent.VK_Q)},
        {"ProgressIndeterminate", "Working..."},
    };
}
