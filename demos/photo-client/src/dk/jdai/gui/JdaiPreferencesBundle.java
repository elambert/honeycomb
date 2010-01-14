package dk.jdai.gui;

import java.util.ListResourceBundle;

/**
 * @author Mikkel
 *
 * Resources for the preferences dialog
 */
public class JdaiPreferencesBundle extends ListResourceBundle {

	/**
	 * @see java.util.ListResourceBundle#getContents()
	 */
	public Object[][] getContents() {
        return contents;
    }
    
    private Object[][] contents = {
        {"Save","Save"},
        {"SaveMnemonic",new Character('S')},
        {"Cancel","Cancel"},
        {"CancelMnemonic",new Character('C')},
        {"Prefs","Preferences"},
        {"Error","Error"},
        {"ErrorMsg","One or more settings could not be saved"}
 	};

}
