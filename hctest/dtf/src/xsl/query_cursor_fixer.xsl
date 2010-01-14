<!--
	Stylesheet used to fix testcases
-->
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="yes"
		doctype-public="-//DTF/DTF XML Script V1.0//EN"
		doctype-system="dtf.dtd" />

	<xsl:template match="query">
		<xsl:element name="query">
			<xsl:attribute name="type"><xsl:value-of select="@type" /></xsl:attribute>
			<xsl:attribute name="event"><xsl:value-of select="@event" /></xsl:attribute>
			<xsl:attribute name="uri"><xsl:value-of select="@uri" /></xsl:attribute>
			<xsl:attribute name="curosr"><xsl:value-of select="@property" /></xsl:attribute>
		</xsl:element>
	</xsl:template>

	<xsl:template match="nextresult">
		<xsl:element name="nextresult">
			<xsl:attribute name="curosr"><xsl:value-of select="@property" /></xsl:attribute>
		</xsl:element>
	</xsl:template>

	<xsl:template
		match="@*|node()|comment()|text()|processing-instruction()">
		<xsl:copy>
			<xsl:apply-templates
				select="@*|node()|comment()|text()|processing-instruction()" />
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>