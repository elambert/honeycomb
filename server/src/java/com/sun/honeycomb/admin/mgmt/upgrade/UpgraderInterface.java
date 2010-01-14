package com.sun.honeycomb.admin.mgmt.upgrade;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.MessageSender;

/**
 * Interface class for the Upgrader class to be loaded dynamically
 * during upgrade.
 */
public interface UpgraderInterface {
    /*
     * First method to call to set up upgrade after status check.
     * @return int - return status
     */
    public int initialize();

    /*
     * Method to allow user interaction with the CLI during upgrade -
     * gets messages to output to the CLI to prompt the user for
     * yes/no answers during upgrade.
     * @return String the next question to be asked
     */
    public String getNextQuestion();
    
    /*
     * Method to allow actions to be performed and state to be set as
     * a result of user interaction with the CLI during
     * upgrade. Returns the name of the next method to be invoked as a
     * result of the answer to the previous question asked.
     * @param answer - the boolean response by the user to the
     * question asked via getNextQuestion()
     * @ return String the name of the method to be invoked
     */
    public String getNextMethod(boolean answer);

    /*
     * Method that returns the confirmation question which is asked
     * after all the initial setup is complete, before the actual
     * upgrade commences.  Allows user to stop upgrade before it
     * starts.
     * @return String the confirmation question
     */
    public String getConfirmQuestion();

    /*
     * Method used when upgrade is invoked with --force or -f flag.
     * Performs the equivalent actions as always calling
     * getNextMethod(true) for all the methods.
     * @param msg - the MessageSender object by which the method
     * reports status to the CLI
     * @return int the status code
     */
    public int setForceOptions(MessageSender msg);
    
    /*
     * Method used to execute a basic, pre-upgrade status check of the
     * system's health so upgrade can be exited early on if the system
     * is in the incorrect state.
     * @param msg - the MessageSender by which the method reports to the CLI
     * @return int the status code
     */
    public int statusCheck(MessageSender msg);

    /*
     * Method used to access the version string of the currently
     * running system.
     * @return String the old version
     */
    public String oldVer();

    /*
     * Method used to access the version string of the new image.
     * @return String the new version
     */
    public String newVer();

    /*
     * Method used to download the new upgrade image for an http upgrade.
     * @param msg - the MessageSender by which the method reports to the CLI
     * @param src - the URL of the iso to be downloaded
     * @return int the status code
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public int httpFetch(MessageSender msg, String src) throws MgmtException;

    /*
     * Method that performs the bulk of the upgrade
     * functionality. Starts the actual upgrade.
     * @param msg - the MessageSender by which the method reports to the CLI
     * @param spdvd - the String indicating if this is a DVD upgrade
     * @param cellid - the cellid of the cell being upgraded
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public int startUpgrade
	(MessageSender msg, String spdvd, byte cellid) throws MgmtException;

    /*
     * Method that performs all the post-upgrade tasks, such as
     * unmounting and rebooting.  Last method to be called during upgrade.
     * @param msg - the MessageSender by which the method reports to the CLI
     * @param type - integer indicating what type of upgrade (dvd,
     * http, etc) is being performed
     * @param success - boolean indicating if upgrade was a success
     * @param cellid - cellid of the cell being upgraded
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public int finishUpgrade(MessageSender msg, int type, boolean success,
		      byte cellid) throws MgmtException;
      
}
