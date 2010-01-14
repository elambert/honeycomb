/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.admingui.present.panels;

import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.utility.GuiResources;
import java.awt.Component;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

/**
 *
 * @author jb127219
 */
public class TextInputVerifier extends InputVerifier {
    
    /**
     * Constants used to describe the type of text to be verified
     */
    public static final int UNKNOWN = -1;
    public static final int ALPHANUMERICS = 0;
    public static final int DOMAIN_NAME = 1;
    public static final int NAMESPACE_NAME = 2;
    public static final int TBL_VIEW_FIELD_NAMES = 3;
    
    public static final String ALPHA_NUMERICS_RULE = GuiResources.getGuiString(
                        "config.metadata.textVerification.alphanumerics");
    /**
     * Constants used for pattern matching during validation
     */
    
    //  Alphanumerics is just that - letters and positive integers = [A-Za-z0-9]
    private static final String REGEX_ALPHANUMERICS = "[\\w^_]";
    
    // Valid Domain Name can include any of the following chars:
    // letter, number and dash where dashes can't be at the beginning or 
    // end of the name.  Also, no spaces are allowed and length < 68 chars
    private static final String REGEX_DOMAIN_NAME = 
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-](?!\\.)){0,67}[a-zA-Z0-9]?\\.)*" + 
            "[a-zA-Z0-9](?:[a-zA-Z0-9\\-](?!$)){0,67}[a-zA-Z0-9]?$";
    
    // Valid chars are letters, non-negative numbers, and underscore =
    // [A-Za-z0-9_] -- cannot have dash/hyphen or any supplemental unicode chars
    // and namespace name must start with a letter
    // NOTE - non Unicode exp that works = "[a-zA-Z]{1}[0-9a-zA-Z_]*";
    private static final String REGEX_NAMESPACE_NAME = 
                                        "[\\p{L}]{1}[\\p{L}\\p{Nd}_]*";

    
    // Valid chars are letters, non-negative numbers, underscore. The
    // name must start with a letter
    // NOTE - non Unicode exp that works = "[a-zA-Z]{1}[0-9a-zA-Z_]*";
    private static final String REGEX_TBL_VIEW_FIELD_NAMES = 
                                        "[\\p{L}]{1}[\\p{L}\\p{Nd}_]*";
            
                                   
    
    private static final HashMap validationTypes = new HashMap();
    static {
        validationTypes.put(new Integer(ALPHANUMERICS), REGEX_ALPHANUMERICS);
        validationTypes.put(new Integer(DOMAIN_NAME), REGEX_DOMAIN_NAME);
        validationTypes.put(new Integer(NAMESPACE_NAME), REGEX_NAMESPACE_NAME);
        validationTypes.put(new Integer(TBL_VIEW_FIELD_NAMES), 
                                                   REGEX_TBL_VIEW_FIELD_NAMES);
    }
    private static final HashMap validationRules = new HashMap();
    static {
        validationRules.put(new Integer(ALPHANUMERICS), ALPHA_NUMERICS_RULE);
        validationRules.put(new Integer(DOMAIN_NAME), GuiResources.getGuiString(
                        "config.metadata.textVerification.domainName"));
        validationRules.put(new Integer(NAMESPACE_NAME), 
                            GuiResources.getGuiString(
                             "config.metadata.textVerification.metadataName"));
        validationRules.put(new Integer(TBL_VIEW_FIELD_NAMES),
                    GuiResources.getGuiString(
                       "config.metadata.textVerification.metadataName"));
    }
    /**
     * A localized string which describes what the regex pattern is trying to 
     * match during validation.  This is helpful when notifying the user the
     * reason for the verification failure.
     */
    private String validationTypeRule = ALPHA_NUMERICS_RULE;
    
    /**
     * The specific character that caused the pattern matching to fail if it 
     * partially matches the input String -- more information to notify user.
     */
    private String invalidReason = null;
    
    /**
     * Type of text to be validated (e.g. domain name is entered as text and 
     * has associated with it a set of rules in order to be valid) - default is
     * alphanumerics.
     */
    private int type = 0;
    
    /**
     * Pattern to use for validation based on user's input if the text types
     * listed do not suffice.  Default pattern is alphanumerics.
     */
    private Pattern fieldPattern = Pattern.compile(REGEX_ALPHANUMERICS);
       
    /** 
     * Creates a new instance of TextInputVerifier with a default pattern
     * to match of alphanumeric characters
     */
    public TextInputVerifier() {
    }
    
    /** 
     * Creates a new instance of TextInputVerifier taking in one of the
     * pre-defined TextInputVerifier types to be validated.  Each type
     * describes what kind of characters or character pattern is allowed
     * in the text field.  For instance, a text field which stores a 
     * domain name would only allow those characters that comprise a valid
     * domain name.  E.g. Chars can be letters, numbers and hyphen
     * where hyphens can't be at the beginning or end of the name.  
     * Also, no spaces are allowed and length < 68 chars
     */
    public TextInputVerifier(int validationType) {
        if (validationTypes.containsKey(new Integer(validationType))) {
            type = validationType;
            String pattern = 
                    (String)validationTypes.get(new Integer(validationType));
            fieldPattern = Pattern.compile(pattern);
            validationTypeRule = 
                    (String)validationRules.get(new Integer(validationType));
        } else {
            // need to notify that validation type entered isn't valid
        }
    }
    
    /** 
     * Creates a new instance of TextInputVerifier taking in the validation
     * pattern to be matched and the key to a localized string which describes 
     * what the regex pattern is trying to match during validation (e.g. If the
     * regex pattern is alphanumerics, [\\w^_], 
     */
    public TextInputVerifier(Pattern regex, String rule) {
        fieldPattern = regex;
        type = UNKNOWN;
        validationTypeRule = rule;
    }
    
    /** 
     * This method is automatically called by the Swing framework.  The method
     * calls "verify" unless it is overridden here.  We call "verify" explicitly
     * from the class utilizing this input verifier so we need to override this 
     * method here so "verify" isn't called more than once. The "verify" method 
     * ultimately determines if the input is valid and yields focus.
     */
    public boolean shouldYieldFocus(JComponent input) {
        return true;
    }
    
    // This method gets called twice -- once automatically via 
    // "shouldYieldFocus" and once via the code utilizing this class to 
    // determine if the input was valid or not -- this is why we override the
    // "shouldYieldFocus" and simply return true.
    public boolean verify(JComponent input) {
        boolean result = false;
        invalidReason = null;
        if (input instanceof JTextField) {
            JTextComponent txtComp = (JTextComponent)input;
            Matcher fieldMatcher = fieldPattern.matcher(txtComp.getText());
            if (fieldMatcher.matches()) {
                result =  true;
            }
            if (fieldMatcher.lookingAt()) {
                if (fieldMatcher.end() < txtComp.getText().length()) {
                    if (invalidReason == null) {
                        String charAsString = null;
                        char c = txtComp.getText().charAt(fieldMatcher.end());
                        if (Character.isWhitespace(c)) {
                            charAsString = GuiResources.getGuiString(
                                "config.metadata.textVerification.whitespace");
                        } else {
                            charAsString = String.valueOf(c);
                        }
                        invalidReason = GuiResources.getGuiString(
                            "config.metadata.textVerification.invalidChar", 
                                        charAsString);
                    }
                } 
            }
            if (!result) {
                Toolkit.getDefaultToolkit().beep();
                txtComp.selectAll();
            }
        }
        
        return result;
    }
    
    public String getUserValidationMessage() {
        StringBuffer sb = new StringBuffer();
        if (invalidReason != null) {
            sb.append(invalidReason);
            sb.append("\n\n");
        }
        sb.append(validationTypeRule);
        return sb.toString();
    }

}
