<!--
	Stylesheet used to fix testcases
-->
<xsl:stylesheet version="2.0"
	            xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml"
                indent="yes"
                doctype-public="-//DTF/DTF XML Script V1.0//EN"
                doctype-system="dtf.dtd"/>

	<xsl:template match="createstorage">
		<xsl:if test="@id='OUTPUT'">	
    		<xsl:element name="createstorage">
    			<xsl:attribute name="id"><xsl:value-of select="@id"/></xsl:attribute>
    			<xsl:attribute name="path"><xsl:value-of select="@path"/>/${dtf.datestamp}</xsl:attribute>
    		</xsl:element>	
		</xsl:if>
		<xsl:if test="@id!='OUTPUT'">	
			<xsl:copy>
				<xsl:apply-templates select="@*|node()|comment()|text()|processing-instruction()"/>
			</xsl:copy>
		</xsl:if>
	</xsl:template>

	<xsl:template match="@*|node()|comment()|text()|processing-instruction()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()|comment()|text()|processing-instruction()"/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>