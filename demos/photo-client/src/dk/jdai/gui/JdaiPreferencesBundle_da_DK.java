package dk.jdai.gui;

import java.util.ListResourceBundle;

/**
 * @author Mikkel
 *
 * Resources for the preferences dialog
 */
public class JdaiPreferencesBundle_da_DK extends ListResourceBundle {

	/**
	 * @see java.util.ListResourceBundle#getContents()
	 */
	public Object[][] getContents() {
        return contents;
    }
    
    private Object[][] contents = {
        {"Save","Gem"},
        {"SaveMnemonic",new Character('G')},
        {"Cancel","Annuller"},
        {"CancelMnemonic",new Character('A')},
        {"Prefs","Indstillinger"},
        {"Error","Fejl"},
        {"ErrorMsg","En eller flere instillinger kunne ikke gemmes"}
 	};

}
