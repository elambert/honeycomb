package com.sun.honeycomb.admin.mgmt;

/**
 * Interface class with one sendMessage method to represent a generic
 * method for sending messages in the system.
 */
public interface MessageSenderInterface {
    /* Generic method to send a message
     * @param message - String representing the method to be sent
     */
    public void sendMessage(String message);
}
