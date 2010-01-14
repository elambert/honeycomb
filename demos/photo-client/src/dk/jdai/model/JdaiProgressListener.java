// JdaiProgressListener.java
// $Id: JdaiProgressListener.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.model;

/**
 * This interface is used to report information about progress status
 *
 * @author <a href="mailto:Mikkel@YdeKjaer.dk">Mikkel Yde Kjær</a>
 * @version $Revision: 1.2 $
 */
public interface JdaiProgressListener {

    /**
     * This method is called periodically during a process
     *
     * @param source The object performing the task
     * @param percetageDone The approximate percentage of job completed
     */
    public void progress(Object source,float percentageDone);

    /**
     * This method is called when the task is being done, but
     * no information about completeness is available
     *
     * @param source The object performing the task
     */
    public void indeterminate(Object source);

    /**
     * This method is called when the task is completed
     *
     * @param source The object performing the task
     */
    public void complete(Object source);
}
