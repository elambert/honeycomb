<!-- 
	DTF XML results processor that converst the xml output into a nice html to 
	browse test results and understand what went wrong and where!
-->

<xsl:stylesheet version="2.0" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
			
	<xsl:import href="util.xsl"/>
    <xsl:output method="html" omit-xml-declaration="yes" indent="yes"/>
    
	<xsl:template match="/">
    	<xsl:apply-templates/>
	</xsl:template>

	<xsl:template match="files">
		<html>
    		<body>
    			<h1>Test Results</h1>
    			<!-- Table of Contents -->
    			<table border="0">
    				<xsl:apply-templates mode="toc" select="."/>
    			</table>
    		</body>
		</html>

		<xsl:apply-templates mode="listing">
			<xsl:sort select="document(.)/testsuite/@start" 
			          order="descending"/>
		</xsl:apply-templates>
	</xsl:template>
	
	<xsl:template match="files" mode="toc">
		<xsl:apply-templates mode="toc">
			<!-- Order by most recent first in terms of the date -->
			<xsl:sort order="descending" select="document(.)/testsuite/@start"/>
		</xsl:apply-templates>	
	</xsl:template>
	
	<xsl:template match="file" mode="toc">
		<xsl:variable name="dir">
			<xsl:value-of select="document(.)/testsuite/@start"/>
		</xsl:variable>

		<xsl:variable name="year">
			<xsl:value-of select="substring-before(document(.)/testsuite/@start,'-')"/>
		</xsl:variable>
		
		<tr>
			<td>
				<a href="results/{$year}/{$dir}.html"><xsl:value-of select="$dir"/></a>
			</td>
			<td>
				<xsl:value-of select="document(.)/testsuite/@name"/>
			</td>
			<td>
				<xsl:value-of select="document(.)/testsuite/@tests"/> tests,
			</td>
			<td>	
				<xsl:value-of select="document(.)/testsuite/@passed"/> passed,
			</td>
			<td>	
      		 	<xsl:if test="document(.)/testsuite/@failed != 0">
      		 		<div style="color:#FF0000">
      		 			<xsl:value-of select="document(.)/testsuite/@failed"/> failed,
      		 		</div>
      		 	</xsl:if>
      		 	<xsl:if test="document(.)/testsuite/@failed = 0">
      		 		0 failed,
      		 	</xsl:if>
      		</td>
			<td>	
    			<xsl:if test="document(.)/testsuite/@skipped != 0">
    				<div style="color:#FFFF00">
    					<xsl:value-of select="document(.)/testsuite/@failed"/> skipped
    				</div>
    			</xsl:if>
				<xsl:if test="document(.)/testsuite/@skipped = 0">
    				0 skipped
    			</xsl:if>
      		</td>
			<td>	
    		    took <xsl:value-of select="document(.)/testsuite/@time"/>s
      		</td>
	    </tr>
	</xsl:template>	
	
	<xsl:template match="file" mode="listing">
		<xsl:variable name="dir">
			<xsl:value-of select="document(.)/testsuite/@start"/>
		</xsl:variable>
		
		<xsl:variable name="year">
			<xsl:value-of select="substring-before(document(.)/testsuite/@start,'-')"/>
		</xsl:variable>
		
		<xsl:result-document href="results/{$year}/{$dir}.html">
    		<html>
        		<body>
        			<a href="javascript:history.back(-1)">Index</a>
					<h4><a name="{$dir}"><xsl:value-of select="$dir"/></a></h4>
        			<table border="0">
						<xsl:apply-templates select="document(.)" mode="listing">
							<xsl:sort select="@start"/>
						</xsl:apply-templates>
					</table>
				</body>
			</html>
		</xsl:result-document>
	</xsl:template>

	<xsl:template match="testcase" mode="listing">
		<tr>
			<td>
				Test: <xsl:value-of select="@name"/> 
			</td>
			<td>
        		<xsl:if test="./passed"><div style="color:#00FF00">[PASSED]</div></xsl:if>
        		<xsl:if test="./failed"><div style="color:#FF0000">[FAILED]</div></xsl:if>
        		<xsl:if test="./skipped"><div style="color:#FFFF00">[SKIPPED]</div></xsl:if>
			</td>
			<xsl:if test="property[@name='test.log.filename']/@value">
    			<td>
    				<xsl:variable name="log">
       		     		<xsl:value-of select="property[@name='test.log.filename']/@value"/>
       		     	</xsl:variable>
        	    	<a href="file:///{$log}">log</a>
            	</td>
        	</xsl:if>
        	<td>
        		took <xsl:value-of select="@time"/>s
        	</td> 
		</tr>
	</xsl:template>
	
	<xsl:template match="testsuite" mode="listing">
		<ul>
    		<table border="0">
        		<tr>
        			<td>
        				Suite: <xsl:value-of select="@name"/>
        			</td>
        			<td>
                		<xsl:if test="./passed"><div style="color:#00FF00">[PASSED]</div></xsl:if>
                		<xsl:if test="./failed"><div style="color:#FF0000">[FAILED]</div></xsl:if>
                		<xsl:if test="./skipped"><div style="color:#FFFF00">[SKIPPED]</div></xsl:if>
        			</td>
					<xsl:if test="property[@name='test.log.filename']/@value">
            			<td>
            				<xsl:variable name="log">
               		     		<xsl:value-of select="property[@name='test.log.filename']/@value"/>
               		     	</xsl:variable>
                	    	<a href="file:///{$log}">log</a>
                    	</td>
                	</xsl:if>
                	<td>
                		took <xsl:value-of select="@time"/>s
                	</td> 
        		</tr>
    		</table>
    		<ul>
    			<table border="0">
    				<xsl:apply-templates mode="listing">
       					<xsl:sort select="@start"/>
    				</xsl:apply-templates>
				</table>
			</ul>
    	</ul>
	</xsl:template>

	<!-- hide any text nodes that I haven't decided to process -->
	<xsl:template match="text()"/>
	<xsl:template match="text()" mode="listing"/>
	
</xsl:stylesheet>