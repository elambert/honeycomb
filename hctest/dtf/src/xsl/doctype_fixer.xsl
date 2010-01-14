<!--
	Stylesheet used to fix testcase DOCTYPE element.
-->
<xsl:stylesheet version="2.0"
	            xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml"
                indent="yes"
                doctype-public="-//DTF/DTF XML Script V1.0//EN"
                doctype-system="dtf.dtd"/>

	<xsl:template match="@*|node()|comment()|text()|processing-instruction()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()|comment()|text()|processing-instruction()"/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>