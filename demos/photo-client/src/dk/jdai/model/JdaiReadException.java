package dk.jdai.model;

/**
 * The JdaiReadException is used when errors regarding reading
 * photos or infostore information occur.
 *
 */
public class JdaiReadException extends Exception {
    /**
     * Creates a new JdaiReadException instance.
     *
     */
    public JdaiReadException() {}

    /**
     * Creates a new JdaiReadException instance witha description.
     *
     * @param msg The description of the exception
     */
    public JdaiReadException(String msg) {
        super(msg);
    }
}
