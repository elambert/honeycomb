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



package com.sun.honeycomb.emd.config;

import java.io.PrintStream;
import java.io.IOException;
import java.io.Writer;

public class Column {
    
    public static final String TAG_COLUMN = "column";
    public static final String ATT_NAME = "name";
    
    private String name;       // Field name for this column
    private String columnName; // HADB column name for this column
    private Table table;
    private int tableIndex;
    private Field field;
    
    public Column(Table _table,
		  Field _field,
		  int _tableIndex) {
        table = _table;
        field = _field;
        name = field.getQualifiedName();
        setTableIndex(_tableIndex);
    }

    public String getName() {
        return name;
    }

    public String getFieldName() {
        return(name);
    }
    
    public String getColumnName() {
        return(columnName);
    }

    public int getTableIndex() {
        return(tableIndex);
    }
    
    private void setTableIndex(int _tableIndex) {
        tableIndex = _tableIndex;
        int dot = name.lastIndexOf('.');

        String fName;
        if (dot < 0)
            fName = name;
        else
            fName = name.substring(dot+1);

        //If there are any non-ASCII chars in the column name, give up
        // and just use "col" for the column name.
        if (!fName.matches("\\p{Alpha}(\\p{Alnum}|_)*")) {
            fName = "col";
        }
        columnName = fName + "_" + tableIndex;
    }
    
    public Field getField() {
	return field;
    }

    public Table getTable() {
	return table;
    }

    public void export(Writer out,
            String prefix)
            throws IOException {
        out.write( prefix+"<"+TAG_COLUMN+
                                " "+ATT_NAME+"=\""+name+
                                "\"/>\n");
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append("<Column \"").append(name).append("\" ");
        sb.append(table.getName()).append(':').append(tableIndex);

        return sb.append(">").toString();
    }
    

    public int getFieldSize() {
        int type = field.getType();
        int result = 0;		// all fields have 2 bytes overhead
        int length;
        switch (type) {
        case Field.TYPE_DOUBLE:
        case Field.TYPE_LONG:
            result = 8 + 2;
            break;
                
        case Field.TYPE_STRING:
            length = field.getLength();
            result = (length*2)+2;
            break;
                
        case Field.TYPE_BINARY:
        case Field.TYPE_CHAR:
            length = field.getLength();
            result = length + 2;
            break;

        case Field.TYPE_OBJECTID:
            result = Field.OBJECTID_SIZE + 2;
            break;

        case Field.TYPE_DATE:
            length = 4+2;
            break;

        case Field.TYPE_TIME:
            length = 8+2;
            break;

        case Field.TYPE_TIMESTAMP:
            length = 8+2;
            break;
                
        default:
            throw new RuntimeException("unknown type "+field.getTypeString());
        }
        return result;
    }
    
}
