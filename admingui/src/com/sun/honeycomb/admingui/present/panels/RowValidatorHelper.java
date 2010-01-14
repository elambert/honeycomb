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

import java.util.List;
import java.util.Vector;


/**
 * Specifies the API's that must be implemented in order to "help" the 
 * table cell renderer determine which cells are valid entries and which ones
 * are not -- 
 */
public interface RowValidatorHelper {
     
    /**
     * This method is responsible for keeping track of which row is currently
     * selected in the table using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @param row The currently selected row within the table
     */
    public void setCurrentSelection(int row);
    /**
     * This method is responsible for keeping track of which row is newly
     * selected in the table using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @param row The newly selected row within the table
     */
    public void setNewSelection(int row);
    /**
     * This method is responsible for adding an invalid row to the List of 
     * of invalid rows and using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @param row The invalid row within the table
     */
    public void addInvalidRow(int row);
    
    /**
     * This method is responsible for removing an invalid row from the List of 
     * of invalid rows and using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @param row The invalid row within the table
     */
    public void removeInvalidRow(int row);
    
    /**
     * This method is responsible for removing an invalid row from the List of 
     * of invalid rows and using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @return List of invalid rows within the table
     */
    public List getInvalidRows();
    
    /**
     * This method is responsible for enabling the implementing class to 
     * return either true/false in a Boolean as to whether or not the given
     * row entry in the table is invalid.
     *
     * @param rowIndex A particular row in the table
     * @param columnIndex A particular column in the table
     *
     * @return Boolean object containing true if the table row is invalid,
     *                  otherwise, false.
     *
     */
    public Boolean isInvalid(int rowIndex);
}
