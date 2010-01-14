<!--  UTIL functions -->
<xsl:stylesheet version="2.0"
				xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
				xmlns:dtf="http://dtf">
			
	<xsl:function name="dtf:getDirectoryName">
		<xsl:param name="string"/>
		<xsl:value-of select='replace(substring-before($string,concat("/",replace($string,"[^/]*/",""))),"[^/]*/","")'/>
	</xsl:function>

	<xsl:function name="dtf:getFilename">
		<xsl:param name="string"/>
		<xsl:if test='contains($string,"/")'>
			<xsl:value-of select='dtf:getFilename(substring-after($string,"/"))'/>
		</xsl:if>
		<xsl:if test='not(contains($string,"/"))'>
			<xsl:value-of select="$string"/>
		</xsl:if>
	</xsl:function>
			
	<xsl:template name="stringBeforeLast">
		<xsl:param name="string" />
		<xsl:param name="char" />

		<xsl:variable name="before">
			<xsl:call-template name="stringAfterLast">
				<xsl:with-param name="string" select="$string" />
				<xsl:with-param name="char" select="$char" />
			</xsl:call-template>
		</xsl:variable>

		<xsl:variable name="beforeString">
			<xsl:value-of select="substring-before($string,$before)" />
		</xsl:variable>

		<xsl:value-of
			select="substring($beforeString,0,string-length($beforeString))" />

	</xsl:template>

	<xsl:template name="stringAfterLast">
		<xsl:param name="string" />
		<xsl:param name="char" />
		<xsl:choose>
			<!-- if the string contains the character... -->
			<xsl:when test="contains($string, $char)">
				<!-- call the template recursively... -->
				<xsl:call-template name="stringAfterLast">
					<!-- with the string being the string after the character -->
					<xsl:with-param name="string"
						select="substring-after($string, $char)" />
					<!-- and the character being the same as before -->
					<xsl:with-param name="char" select="$char" />
				</xsl:call-template>
			</xsl:when>
			<!-- otherwise, return the value of the string -->
			<xsl:otherwise>
				<xsl:value-of select="$string" />
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template name="replace-string">
		<xsl:param name="text" />
		<xsl:param name="replace" />
		<xsl:param name="with" />
		<xsl:choose>
			<xsl:when test="contains($text,$replace)">
				<xsl:value-of select="substring-before($text,$replace)" />
				<xsl:value-of select="$with" />
				<xsl:call-template name="replace-string">
					<xsl:with-param name="text"
						select="substring-after($text,$replace)" />
					<xsl:with-param name="replace" select="$replace" />
					<xsl:with-param name="with" select="$with" />
				</xsl:call-template>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$text" />
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
</xsl:stylesheet>