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

import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.test.Testcase;
import java.util.logging.Logger;
import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;


public class MDTestcase extends Testcase {
    private static Logger log
        = Logger.getLogger(MDTestcase.class.getName());

    static final String prefix = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    static final String MDNAME = "md";

    public MDTestcase(String name) {
        super(name);
    }

    // ValidateSchema will load a config xml and construct a RootNamespace
    // instance.
    protected RootNamespace validateSchema(String cfg)
        throws EMDConfigException {
        return RootNamespace.loadConfigFromString(cfg, false);
    }

    // ValidateSchema will load a config xml and validate against
    // the committed schema.
    protected void validateSchema(RootNamespace rn, String cfg)
        throws EMDConfigException {
        InputStream stream = null;
        try {
            stream = new ByteArrayInputStream(cfg.getBytes("UTF-8"));
            rn.readConfig(stream, false);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new EMDConfigException(e.getMessage());
        } catch (EMDConfigException e) {
            throw e;
        }finally {
            if (stream != null)
                try { stream.close(); } catch (IOException e) {}
        }
    }

    // validateSchemaFail will validate the failed cases.
    protected void validateSchemaFail(String cfg, String msg)
        throws EMDConfigException {
        validateSchemaFail(null, cfg, msg);
    }

    // validateSchemaFail will validate the failed cases.
    protected void validateSchemaFail(RootNamespace rn, 
            String cfg,
            String msg)
        throws EMDConfigException {
        try {
            if (null == rn) {
                validateSchema(cfg);
            } else {
                validateSchema(rn, cfg);
            }
        } catch (EMDConfigException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
//                System.out.println(e.getCause().getMessage());
                return;
            }
            throw e;
        }
        fail(msg);
    }

    // Construct the metadata config xml.
    protected String getConfigXml(StringBuffer sXml) {
        return getConfigXml(sXml, null, null);
    }

    // Construct the metadata config xml.
    protected String getConfigXml(StringBuffer sXml, 
            StringBuffer vXml, 
            StringBuffer tXml) {
        StringBuffer b = new StringBuffer(prefix).append("<metadataConfig>");
        if (null != sXml) {
            b.append(sXml);
        }
        if (null != vXml) {
            b.append(vXml);
        }
        if (null != tXml) {
            b.append(tXml);
        }
        b.append("</metadataConfig>");
        return b.toString();
    }

    // Construct the schema xml.
    protected StringBuffer schemaXml(StringBuffer nsXml) {
        return schemaXml(nsXml, null);
    }

    // Construct the schema xml.
    protected StringBuffer schemaXml(StringBuffer nsXml, 
            StringBuffer fXml) {
        StringBuffer b = new StringBuffer("<schema>");
        if (null != nsXml) {
            b.append(nsXml);
        }
        if (null != fXml){
            b.append(fXml);
        }
        b.append("</schema>");
        return b;
    }

    //construct the namespace xml.
    protected StringBuffer nsXml(String name) { 
        return nsXml(name, "true", "true", null, null);
    }

    //construct the namespace xml.
    protected StringBuffer nsXml(String name, 
            String writeable, 
            String extensible, 
            StringBuffer subNsXml,
            StringBuffer fXml) {
        StringBuffer b = new StringBuffer("<namespace name=\"");
        if (null != name) {
            b.append(name);
        }
        b.append("\" writeable=\"").append(writeable);
        b.append("\" extensible=\"").append(extensible);
        b.append("\">");
        if (null != subNsXml) {
            b.append(subNsXml);
        }
        if (null != fXml) {
            b.append(fXml);
        }
        b.append("</namespace>");
        return b;
    }

    // Construct the tables xml.
    protected StringBuffer tablesXml(StringBuffer tXml) {
        if (null == tXml) {
            return null;
        }
        StringBuffer b = new StringBuffer("<tables>");
        b.append(tXml);
        b.append("</tables>");
        return b;
    }

    // Construct the tables xml.
    protected StringBuffer tablesXml(int count) {
        return tablesXml(tableXml(count));
    }

    // Construct the tables xml.
    protected StringBuffer tablesXml(String tname) {
        return tablesXml(tableXml(tname));
    }

    // Construct the table xml.
    protected StringBuffer tableXml(String tname) {
        return tableXml(tname, 1, MDNAME);
    }

    // Construct the table xml.
    protected StringBuffer tableXml(int count) {
        return tableXml(MDNAME, count, MDNAME);
    }

    // Construct the table xml.
    protected StringBuffer tableXml(String tname, int count, String cname) {
        return tableXml(tname, columnXml(count, cname));
    }

    // Construct the table xml.
     protected StringBuffer tableXml(String tname, StringBuffer cXml) {
        if (null == cXml) {
            return null;
        }
        StringBuffer b = new StringBuffer("<table name=\"");
        if (null != tname) {
            b.append(tname);
        }
        b.append("\">");
        b.append(cXml);
        b.append("</table>");
        return b;
     }

    // Construct the fsViews xml.
    protected StringBuffer viewsXml(String fname) {
        return viewsXml(viewXml(MDNAME, fname, 1, MDNAME));
    }

    // Construct the fsViews xml for filesonlyatleaflevel 
    protected StringBuffer viewsXml(String fname, 
            String fLeafLevel) {
        return viewsXml(viewXml(MDNAME, fname, 1,
            fLeafLevel, MDNAME));
    }

    // Construct the fsViews xml.
    protected StringBuffer viewsXml(StringBuffer vXml) {
        if (null == vXml) {
            return null;
        }
        StringBuffer b = new StringBuffer("<fsViews>");
        b.append(vXml);
        b.append("</fsViews>");
        return b;
    }

    // Construct the fsView xml.
    protected StringBuffer viewXml(String ns, String fname) {
        return viewsXml(viewXml(MDNAME, ns, fname, 1, MDNAME));
    }

    // Construct the fsView xml.
    protected StringBuffer viewXml(int count, String fname) {
        return viewXml(MDNAME, null, fname, count, MDNAME);
    }

    // Construct the fsView xml.
    protected StringBuffer viewXml(String vname, 
            String fname,
            int count,
            String aname) {
        return viewXml(vname, null, fname, attributeXml(count, aname));
    }

    // Construct the fsView xml for filesonlyatleaflevel
    protected StringBuffer viewXml(String vname,
            String fname,
            int count,
            String fLeafLevel,
            String aname) {
        return viewXml(vname, null, fname, fLeafLevel,
            attributeXml(count, aname));
    }

    // Construct the fsView xml.
    protected StringBuffer viewXml(String vname, 
            String ns,
            String fname,
            int count,
            String aname) {
        return viewXml(vname, ns, fname, attributeXml(count, aname));
    }

    // Construct the fsView xml.
    protected StringBuffer viewXml(String vname, 
            String fname,
            StringBuffer aXml) {
        return viewXml(vname, null, fname, aXml);
    }

    // Construct the fsView xml.
    protected StringBuffer viewXml(String vname, 
            String ns,
            String fname,
            StringBuffer aXml) {
        if (null == aXml) {
            return null;
        }
        StringBuffer b = new StringBuffer("<fsView name=\"");
        if (null != vname) {
            b.append(vname);
        }
        if (null != ns) {
            b.append("\" namespace=\"").append(ns);
        }
        b.append("\" filename=\"");
        if (null != fname) {
            b.append(fname);
        }
        b.append("\">");
        b.append(aXml);
        b.append("</fsView>");
        return b;
    }

    // Construct the fsView xml with filesonlyatleaflevel
    protected StringBuffer viewXml(String vname,
            String ns,
            String fname,
            String fLeafLevel, 
            StringBuffer aXml) {
        if (null == aXml) {
            return null;
        }
        StringBuffer b = new StringBuffer("<fsView name=\"");
        if (null != vname) {
            b.append(vname);
        }
        if (null != ns) {
            b.append("\" namespace=\"").append(ns);
        }
        b.append("\" filename=\"");
        if (null != fname) {
            b.append(fname);
        }
        if (null != fLeafLevel) {
            b.append("\" filesonlyatleaflevel=\"");
            b.append(fLeafLevel);
        }
        b.append("\">");
        b.append(aXml);
        b.append("</fsView>");
        return b;
    }


    // Construct the attribute xml.
    protected StringBuffer attributeXml(int count) {
        return attributeXml(count, MDNAME);
    }

    // Construct the attribute xml.
    protected StringBuffer attributeXml(int count, String name) {
        if (0 == count) {
            return null;
        }
        StringBuffer b = new StringBuffer();
        for (int i = 0; i < count; i++) {
            b.append("<attribute name=\"");
            if (null != name) {
                b.append(name);
                if (i > 0) {
                    b.append(i);
                }
            }
            b.append("\"/>");
        }
        return b;
    }

    // Construct field xml.
    protected StringBuffer fieldXml(int count) {
        return fieldXml(count, MDNAME);
    }

    // Construct field xml.
    protected StringBuffer fieldXml(int count, 
            String name) {
        return fieldXml(count, name, "string", "64", "true");
    }

    // Construct field xml.
    protected StringBuffer fieldXml(int count, 
            String type, 
            String length) {
        return fieldXml(count, MDNAME, type, length, "true");
    }

    // Construct field xml.
    protected StringBuffer fieldXml(String name, String extensible) {
        return fieldXml(1, name, "string", "64", extensible);
    }

    // Construct field xml.
    protected StringBuffer fieldXml(int count, 
            String name, 
            String type,
            String length, 
            String queryable) {
        if (0 == count) {
            return null;
        }
        StringBuffer b = new StringBuffer();
        for (int i = 0; i < count; i++) {
            b.append("<field name=\"");
            if (null != name) {
                b.append(name);
                if (i > 0) {
                    b.append(i);
                }
            }
            b.append("\"");
            if (null != type ) {
                b.append(" type=\"").append(type).append("\"");
            }
            if (null != length) {
                b.append(" length=\"").append(length).append("\"");
            }
            if (null != queryable) {
                b.append(" queryable=\"").append(queryable).append("\"");
            }
            b.append("/>");
        }
        return b;
    }

    // Construct the column xml.
    protected StringBuffer columnXml(int count) {
        return columnXml(count, MDNAME);
    }

    // Construct the column xml.
    protected StringBuffer columnXml(int count, String name) {
        if (0 == count) {
            return null;
        }
        StringBuffer b = new StringBuffer();
        for (int i = 0; i < count; i++) {
            b.append("<column name=\"");
            if (null != name) {
                b.append(name);
                if (i > 0) {
                    b.append(i);
                }
            }
            b.append("\"/>");
        }
        return b;
    }
}
