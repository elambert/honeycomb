<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:param name="indent-increment" select="'    '" />

	<xsl:template match="*">
		<xsl:param name="indent" select="'&#xA;'" />

		<xsl:value-of select="$indent" />
		<xsl:copy>
			<xsl:copy-of select="@*" />
			<xsl:apply-templates>
				<xsl:with-param name="indent"
					select="concat($indent, $indent-increment)" />
			</xsl:apply-templates>
			<xsl:value-of select="$indent" />
		</xsl:copy>
	</xsl:template>

	<xsl:template match="comment()|processing-instruction()">
		<xsl:copy />
	</xsl:template>

	<xsl:template match="text()[normalize-space(.)='']" />

</xsl:stylesheet>