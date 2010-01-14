package dk.jdai.model;

/**
 * The JdaiWriteException is used when errors regarding writing
 * photos or infostore information occur.
 *
 */
public class JdaiWriteException extends Exception {
    /**
     * Creates a new JdaiWriteException instance.
     *
     */
    public JdaiWriteException() {}

    /**
     * Creates a new JdaiWriteException instance witha description.
     *
     * @param msg The description of the exception
     */
    public JdaiWriteException(String msg) {
        super(msg);
    }
}
