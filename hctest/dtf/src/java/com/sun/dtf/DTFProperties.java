package com.sun.dtf;

public class DTFProperties {
   
    // DTF properties
    public static String DTF_VERSION                = "dtf.version";
    
    // node properties
    public static String DTF_NODE_NAME              = "dtf.node.name";
    public static String DTF_NODE_TYPE              = "dtf.node.type";
    public static String DTF_NODE_OS                = "os.name";
    public static String DTF_NODE_OS_ARCH           = "os.arch";
    public static String DTF_NODE_OS_VER            = "os.version";

    // listening properties
    public static String DTF_LISTEN_ADDR            = "dtf.listen.addr";
   
    public static String DTF_LISTEN_PORT            = "dtf.listen.port";
    public static int    DTF_LISTEN_PORT_DEFAULT    = 9999;
   
    // connect properties
    public static String DTF_CONNECT_ADDR           = "dtf.connect.addr";
    public static String DTF_CONNECT_ADDR_DEFAULT   = "localhost";
    
    public static String DTF_CONNECT_PORT           = "dtf.connect.port";
    public static int    DTF_CONNECT_PORT_DEFAULT   = 9999;

    // XML file properties
    public static String DTF_XML_FILENAME           = "dtf.xml.filename";
    public static String DTF_XML_PATH               = "dtf.xml.path";
    
    // DTD file
    public static String DTF_DTD_FILENAME           = "dtf.dtd.filename";
    
    // DTF properties file
    public static String DTF_PROPERTIES             = "dtf.properties";
    
    // recorder DB properties
    public static String DTF_DB_USERNAME            = "dtf.db.username";
    public static String DTF_DB_PASSWORD            = "dtf.db.password";
    
    // Results properties
    public static String DTF_TESTCASE_LOG           = "test.log.filename";

}
