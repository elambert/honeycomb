package dk.jdai.gui;

import java.awt.Component;

/**
 * JdaiOptions must be implemented by all classes that have
 * settings, which should be included in the preferences dialog.
 *
 */
public interface JdaiOptions {

    /**
     * Returns the preferences component
     *
     * @return The component to set preferences
     */
    public Component getComponent();

    /**
     * Returns name og preferences tab
     *
     * @return Name of tab
     */
    public String getName();
    
    /**
	 * Called when the whole preferences dialog is saved
	 * @return true if all was saved correctly, false otherwise
	 */
	public boolean save();
    
    /**
	 * Called when the whole preferences dialog is canceled
	 */
	public void cancel();
}
