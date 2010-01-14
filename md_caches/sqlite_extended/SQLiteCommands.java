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


import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.emd.config.RootNamespace;
import java.util.ArrayList;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.emd.config.FsView;
import com.sun.honeycomb.emd.config.FsAttribute;

public class SQLiteCommands {

    public static String createSchemaCommand(String tableName) 
        throws EMDConfigException {

        StringBuffer result = new StringBuffer();
        ArrayList fields = new ArrayList();
        RootNamespace.getInstance().getFields(fields, true);

        result.append("create table "+tableName+" (");

        for (int i=0; i<fields.size(); i++) {
            Field field = (Field)fields.get(i);
            if (i>0) {
                result.append(", ");
            }

            field.getQualifiedName(result, "_");

            switch (field.getType()) {
            case Field.TYPE_BYTE:
                result.append(" integer");
                break;
            
            case Field.TYPE_LONG:
                result.append(" integer");
                break;

            case Field.TYPE_DOUBLE:
                result.append(" float");
                break;
            
            case Field.TYPE_STRING:
                result.append(" text");
                break;

            case Field.TYPE_BLOB:
                result.append(" blob");
                break;

            case Field.TYPE_DATE:
                result.append(" text");
                break;
                
            case Field.TYPE_TIME:
                result.append(" text");
                break;
            }
        
            if (field.getQualifiedName().equals
                (SystemMetadata.FIELD_NAMESPACE+"."+SystemMetadata.FIELD_OBJECTID)) {
                result.append(" primary key");
            }
        }

        result.append(")");

        return(result.toString());
    }

    public static String createViewCommand(FsView view) {
        StringBuffer result = new StringBuffer();
        ArrayList atts = view.getAttributes();

        result.append("create index "+view.getName()+" on main (");
        for (int i=0; i<atts.size(); i++) {
            if (i>0) {
                result.append(", ");
            }
            ((FsAttribute)atts.get(i)).field.getQualifiedName(result, "_");
        }
        result.append(")");
        
        return(result.toString());
    }
}
