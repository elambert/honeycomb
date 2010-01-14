package com.sun.honeycomb.admin.mgmt;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Wrapper class to ship around either an EventSender or a
 * ReassureThread so the message sending interface does not need to be
 * changed if the implementation changes. Implements
 * MessageSenderInterface.
 */
public class MessageSender implements MessageSenderInterface {
    private String type = null;
    private Object sender;

    private static Logger logger = 
	Logger.getLogger(MessageSender.class.getName());

    /*
     * Constructor to wrap an EventSender object
     * @param e - the EventSender to wrap
     */
    public MessageSender(EventSender e) {
	type = "event";
	sender = e;
    }

    /*
     * Constructor to wrap a Reassure object
     * @param r - the Reassure object to wrap
     */
    public MessageSender(Reassure r) {
	type = "reassure";
	sender = r;
    }
    
    /*
     * Method that returns the wrapped sender object
     * @return Object the sender object with which this instance of
     * MessageSender was created
     */
    public Object getSender() {
	return sender;
    }
    
    /*
     * Implementation of MessageInterface's sendMessage method.
     * Method that takes in a String and sends it out via the wrapped
     * sender object's send mechanism.
     * @param message - String representing the message to be sent
     */
    public void sendMessage(String message) {
	if (type.equals("event")) {
	    try {
		((EventSender) sender).sendAsynchronousEvent(message);
	    } catch (MgmtException e) {
		logger.log(Level.SEVERE, "Failed to send event: ", e);
	    }
	} else if (type.equals("reassure")) {
	    ((Reassure) sender).setMessage(message);
	}
    }

}
