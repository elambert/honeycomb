<?xml version="1.0" encoding="UTF-8"?>
<!--
    $Id: silo_info.xsl 10858 2007-05-19 03:03:41Z bberndt $

    Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
    Use is subject to license terms.
-->

<xsl:stylesheet 
    version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    
    <xsl:output 
        method="text"
        encoding="UTF-8"/>  
    
     <!-- cell id -->
     <xsl:param name="cellid" select="1"/>   
     
     <!-- config param name (admin-vip,data-vip,sp-vip,gateway,subnet) -->
     <xsl:param name="paramname" select="string('admin-vip')"/>   
     
     <xsl:template match="Multicell-Descriptor/Cell">
         <xsl:if test="@id = $cellid">
            <xsl:choose>
                <xsl:when test="string('admin-vip') = $paramname">
                    <xsl:value-of select="@admin-vip" />
                </xsl:when>
                <xsl:when test="string('data-vip') = $paramname">
                    <xsl:value-of select="@data-vip" />
                </xsl:when>
                <xsl:when test="string('sp-vip') = $paramname">
                    <xsl:value-of select="@sp-vip" />
                </xsl:when>
                <xsl:when test="string('gateway') = $paramname">
                    <xsl:value-of select="@gateway" />
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="@subnet" />
                </xsl:otherwise>
             </xsl:choose>
         </xsl:if>
     </xsl:template>
    
</xsl:stylesheet>