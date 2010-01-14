package com.sun.dtf.actions.logging;

import com.sun.dtf.actions.util.CDATA;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.logger.DTFLogger;

/**
 * @dtf.tag log
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Logging ability to be able to place messages in the test run 
 *               that are similar to the ones that are logged within the test
 *               code itself.
 * 
 * @dtf.tag.example 
 * <log level="warn">Attention!</log>
 */

public class Log extends CDATA {
    
    public final static String INFO  = "info";
    public final static String WARN  = "warn";
    public final static String ERROR = "error";

    /**
     * @dtf.attr level
     * @dtf.attr.desc Specifies the level of logging to be printed, this can 
     *                be one of the 3 following:
     *                
     *                <table border="1">
     *                  <tr>
     *                      <th>Log Level</th> 
     *                      <th>Description</th> 
     *                  </tr>
     *                  <tr>
     *                      <td>info</td>
     *                      <td>Info log level is used in normal conditions.</td>
     *                  </tr>
     *                  <tr>
     *                      <td>warn</td>
     *                      <td>Warning log level is used in conditions that may require attention.</td>
     *                  </tr>
     *                  <tr>
     *                      <td>error</td>
     *                      <td>Error log level is used in error conditions, and usually require attention.</td>
     *                  </tr>
     *                </table> 
     */
    private String level = null;
    
    /**
     * @dtf.attr tag
     * @dtf.attr.desc The tag will be used when logging as the first tag in the 
     *                logline that is being logged. 
     *                
     *                For example:
     *                <pre>
     *                [java] ERROR 15/04/2008 09:05:33 YOURTAGHERE          - your message
     *                [java] INFO  15/04/2008 09:05:33 YOURTAGHERE          - your message
     *                </pre>
     */
    private String tag = null;

    public void execute() throws DTFException {
        String l = getLevel();
        DTFLogger logger = null;
        
        if (getTag() == null) 
            logger = getLogger();
        else 
            logger = DTFLogger.getLogger(getTag());
        
        if (l.equalsIgnoreCase(INFO)) {
            logger.info(getCDATA());
        } else if (l.equalsIgnoreCase(WARN)) { 
            logger.warn(getCDATA());
        } else if (l.equalsIgnoreCase(ERROR)) { 
            logger.error(getCDATA());
        }
    }

    public String getLevel() throws ParseException { return replaceProperties(level); }
    public void setLevel(String level) { this.level = level; }

    public String getTag() throws ParseException { return replaceProperties(tag); }
    public void setTag(String tag) { this.tag = tag; } 
}
