<!--
	Stylesheet used to fix testcases
-->
<xsl:stylesheet version="2.0"
	            xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml"
                indent="yes"
                doctype-public="-//DTF/DTF XML Script V1.0//EN"
                doctype-system="dtf.dtd"/>

	<xsl:template match="info">
		<xsl:copy>
			<xsl:apply-templates/>
		</xsl:copy>
		<xsl:if test="not(following-sibling::local/createstorage[@id='INPUT'])">
    		<xsl:element name="local">
        		<xsl:element name="createstoreage">
        			<xsl:attribute name="id">INPUT</xsl:attribute>
        			<xsl:attribute name="path">${dtf.xsl.path}/input</xsl:attribute>
        		</xsl:element>	
        		<xsl:element name="loadproperties">
        			<xsl:attribute name="uri">storage://INPUT/hc.properties</xsl:attribute>
        		</xsl:element>	
        	</xsl:element>
    	</xsl:if>
	</xsl:template>

	<xsl:template match="lockcomponent">
		<xsl:element name="lockcomponent">
			<xsl:attribute name="id"><xsl:value-of select="@id"/></xsl:attribute>
			<xsl:attribute name="type">${hc.client.type}</xsl:attribute>
		</xsl:element>	
	</xsl:template>

	<xsl:template match="store">
		<xsl:element name="store">
			<xsl:attribute name="datavip">${hc.cluster.datavip}</xsl:attribute>
			<xsl:attribute name="port">${hc.cluster.port}</xsl:attribute>
			<xsl:apply-templates/>
		</xsl:element>	
	</xsl:template>

	<xsl:template match="file">
		<xsl:element name="file">
			<xsl:attribute name="size">${hc.file.size}</xsl:attribute>
			<xsl:attribute name="type">${hc.file.type}</xsl:attribute>
		</xsl:element>	
	</xsl:template>
	
	<xsl:template match="@*|node()|comment()|text()|processing-instruction()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()|comment()|text()|processing-instruction()"/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>