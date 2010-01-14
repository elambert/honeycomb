<!--
Inventory stylesheet can take a list of DTF testscases and 
generate an html list of the inventory of testscases available 
with all the necessary details and links to the tests. 
-->
<xsl:stylesheet version="2.0" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:import href="util.xsl"/>
    <xsl:output method="html" omit-xml-declaration="yes" indent="yes"/>

    <xsl:template match="/">
    	<html>
    		<body>
    			<xsl:apply-templates/>
    		</body>
    	</html>
    </xsl:template>
  
    <xsl:template match="script/testsuite">
		<xsl:param name="dtf.xml.path"/>
		<ol>
			<xsl:for-each select="testscript">
	         	<!-- get the storage id from the uri -->	
	        	<xsl:variable name="storageID">
	                <xsl:value-of 
	                 select='substring-before(substring-after(@uri,"storage://"),"/")'/>
	            </xsl:variable>
	                   
	            <!-- lookup the id path from the createstorage tag --> 
	            <xsl:variable name="storagePath">
	                <xsl:value-of 
	                    select='preceding::local/createstorage[@id=$storageID]//@path'/>
	            </xsl:variable>
	        
	    		<xsl:variable name="value">
	    			<xsl:value-of 
	    			  select="substring-after(substring-before($storagePath,'}'),'{')"/>
	    		</xsl:variable>
	       
	   			<xsl:variable name="link"> 
	                <xsl:value-of select="$dtf.xml.path"/>
	                <xsl:value-of select="substring-after(@uri,$storageID)"/>
	            </xsl:variable>
	                
	            <xsl:variable name="id"> 
	                <xsl:value-of select="document($link)/script/@name"/>
	            </xsl:variable>
	            
	            <xsl:variable name="email">
				  mailto:<xsl:value-of select="document($link)/script/info/author/email"/>
	            </xsl:variable>
	
				<li>
					<h4>
						<a href="{$link}"><xsl:value-of select="$id"/></a>
						<br/>
					 	<a href="{$email}">
							<xsl:value-of 
							      select="document($link)/script/info/author/name"/>
					 	</a>
					</h4>
	                <xsl:variable name="id"> 
	                    <xsl:value-of select="document($link)/script/@name"/>
	                </xsl:variable>
					<xsl:value-of select="document($link)/script/info/description"/>
	            </li>
	    	</xsl:for-each>
    	</ol>
    </xsl:template>

  	<!-- TOC from testsuites -->  
    <xsl:template match="files">
		<h1>Test Suite Inventory</h1>	
		<ul>
    		<xsl:for-each select="file">
    			<!-- save the testsuite filename -->
    	 	    <xsl:variable name="filename">
                      <xsl:value-of select="." />
    	 	    </xsl:variable>
    	 	   
    	 	    <!-- link to each testsuite at the top of the html --> 
    			<xsl:for-each select="document(.)/script/testsuite">
    			    <xsl:variable name="link">#<xsl:value-of select="@name"/></xsl:variable>
    				<li>
						<a href="{$link}"><xsl:value-of select="./@name"/></a>
						(<xsl:value-of  select="count(descendant::testscript)"/> tests)
    				</li>
    			</xsl:for-each>
    		</xsl:for-each>
		</ul>
		
		<ul>
    		<xsl:for-each select="file">
    			<!-- save the testsuite filename -->
    	 	    <xsl:variable name="filepath">
    	 	       <xsl:call-template name="stringBeforeLast">
                      <xsl:with-param name="string" select="." />
                      <xsl:with-param name="char" select="'/'" />
                   </xsl:call-template>
    	 	    </xsl:variable>
    	 	    <xsl:variable name="filename">
                	<xsl:value-of select="." />
    	 	    </xsl:variable>
    	
    			<xsl:for-each select="document(.)/script/testsuite">
    				<!-- print the name of the testsuite -->
    			    <xsl:variable name="link"><xsl:value-of select="@name"/></xsl:variable>
    				<h2>
    					<a name="{$link}"><xsl:value-of select="./@name"/></a>
    				</h2>
        			
    				<!-- process each of the testscripts individually -->	
    				<xsl:apply-templates select=".">
    			    	<!-- set default dtf.xml.path to the location of the 
    			    	     testsuite -->
    			 	    <xsl:with-param name="dtf.xml.path" >
    			 	    	<xsl:value-of select="$filepath"/>
    			 	    </xsl:with-param>
    				</xsl:apply-templates>
    			</xsl:for-each>
    		</xsl:for-each>
		</ul>	
	</xsl:template>
	
    <xsl:template match="text()"/>
    
</xsl:stylesheet>