<?xml version="1.0" encoding="ISO-8859-1"?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:variable name="service_name" select="/WikipediaMinerResponse/@service_name"/>
<xsl:variable name="server_path" select="/WikipediaMinerResponse/@server_path"/>
<xsl:variable name="term1" select="/WikipediaMinerResponse/CompareResponse/@term1"/>
<xsl:variable name="term2" select="/WikipediaMinerResponse/CompareResponse/@term2"/>

<xsl:template match="WikipediaMinerResponse/CompareResponse">
  <html xmlns="http://www.w3.org/1999/xhtml">
  <head>
  	<xsl:choose>
	  	<xsl:when test="@unspecifiedParameters">
	  		<title>Wikipedia Miner compare service</title>
		</xsl:when>
		<xsl:otherwise>
			<title><xsl:value-of select="@term1"/> and <xsl:value-of select="@term2"/> | Wikipedia Miner compare service</title>
		</xsl:otherwise>
  	</xsl:choose>

  	<link rel="stylesheet" href="css/style.css" type="text/css"/> 
  </head>
  <body>
  
   <div class="box" style="float:right; width:420px; margin-right: 0em ; margin-top: 0em">
		<form method="get" action="{$service_name}">
			<input type="hidden" name="task" value="compare"></input>
			<input type="hidden" name="getSenses" value="true"></input>
			<input type="hidden" name="getSnippets" value="true"></input>
			<input type="hidden" name="getMutualLinks" value="true"></input>
			<input type="text" name="term1" style="width: 150px" value="{$term1}"></input>&#160;
			<input type="text" name="term2" style="width: 150px" value="{$term2}"></input>&#160;
			<input type="submit" value="Compare" style="width: 75px;"></input>
		</form>
		
		<div class="tl"></div>
		<div class="tr"></div>
		<div class="bl"></div>
		<div class="br"></div>
	</div>
	
	<a href="http://wikipedia-miner.sf.net">Wikipedia Miner</a> | <a href="{$server_path}/{$service_name}"> services</a>  
	
	
	<xsl:choose>
	
		<xsl:when test="@unspecifiedParameters">
	  	
	  	<h1 style="margin-top:5px"><em>Compare</em> Service</h1>
	  	
	  	<p>
	  		Welcome to the Wikipedia Miner compare service, for measuring the semantic relatedness between terms, 
	  		phrases, and Wikipedia concepts. From this you can tell, for example, that New Zealand has more to do with 
	  		<a href="{$service_name}?task=compare&amp;getSenses=true&amp;getSnippets=true&amp;getArticlesInCommon=true&amp;term1=New Zealand&amp;term2=Rugby">Rugby</a> than 
	  		<a href="{$service_name}?task=compare&amp;getSenses=true&amp;getSnippets=true&amp;getArticlesInCommon=true&amp;term1=New Zealand&amp;term2=Soccer">Soccer</a>, or that Geeks are more into 
	  		<a href="{$service_name}?task=compare&amp;getSenses=true&amp;getSnippets=true&amp;getArticlesInCommon=true&amp;term1=Geek&amp;term2=Computer Games">Computer Games</a> than the 
	  		<a href="{$service_name}?task=compare&amp;getSenses=true&amp;getSnippets=true&amp;getArticlesInCommon=true&amp;term1=Geek&amp;term2=Olympic Games">Olympic Games</a> 
	  	</p>
	  		
	  	<p>
	  		The relatedness measures are calculated from the links going into and out of each if the relevant Wikipedia pages. 
	  		Links that are common to both pages are used as evidence that they are related, while links that are unique to one 
	  		or the other indicate the opposite. 
	  	</p>	
	  	<p>	
	  		The measure is symmetric, so comparing <i>Rugby</i> to <i>Soccer</i> is the same as comparing <i>Soccer</i> to <i>Rugby</i>. 
	  	</p>
	  	
		<div class="note">
                        <p>
                                <em>Note:</em> This service is machine-readable.
                        </p>
                        <p class="small">
                                It can be made to return XML by appending <i>&amp;xml</i> to the request.
                        </p>
                        <p class="small">
                                Feel free to point a bot or a service here (via POST or GET, it doesn't matter). There are some
                                <a href="{$service_name}?task=compare&amp;help">additional parameters</a> available if you do.
                                Bear in mind that we may restrict access if usage becomes excessive.
                                You can always run these services yourself by installing your own version of Wikipedia Miner.
                        </p>
                </div>
  	</xsl:when>
	
	  <xsl:when test="@unknownTerm">
	  
	  	<h1 style="margin-top:0px"><em>Compare</em> Service</h1>
	  	<p>
	  		I have no idea what you mean by <em><xsl:value-of select="@unknownTerm"/></em>.
  		</p>
  	</xsl:when>
  	
  	<xsl:otherwise>
  		| <a href="{$server_path}/{$service_name}?task=compare">compare service</a> 
  		
  		<h1 style="margin-top:5px">
  			<em><xsl:value-of select="@term1"/></em> and 
 				<em><xsl:value-of select="@term2"/></em>
  		</h1>
		
		<h2>
 			These terms are <em><xsl:value-of select="round(@relatedness*100)"/>%</em> related
		</h2>
		
		<p class="explanation">
			The relatedness measure was calculated from the links going into and out of each page. 
			Links that are common to both pages are used as evidence that they are related, while 
			links that are unique to one or the other indicate the opposite.
		</p>
  		
 		<xsl:if test="Sense1">
 			
			<h2>Senses</h2> 
			
			
	 		<p class="explanation">
	 			There are <em><xsl:value-of select="Sense1/@candidates"/></em> 
	 			things you could have meant by <em><xsl:value-of select="@term1"/></em> 
	 			(click <a href="{$service_name}?task=search&amp;term={$term1}">here</a> to search for them). 
	 			We are assuming you mean 
	 		</p>
	 			<ul class="senses">
					<li>
						<div class="sense">
							<a href="{$service_name}?task=search&amp;id={Sense1/@id}&amp;term={Sense1/@title}">
								<xsl:value-of select="Sense1/@title"/>
							</a>
							<p class="definition"><xsl:copy-of select="Sense1/FirstSentence"/></p>
						</div>
					</li>
				</ul>
				
			
			<p class="explanation">
	 			There are <em><xsl:value-of select="Sense2/@candidates"/></em> 
	 			things you could have meant by <em><xsl:value-of select="@term2"/></em> 
	 			(click <a href="{$service_name}?task=search&amp;term={@term2}">here</a> to search for them). 
	 			We are assuming you mean 
	 		</p>	
	 			<ul class="senses">
					<li>
						<div class="sense">
							<a href="{$service_name}?task=search&amp;id={Sense2/@id}&amp;term={Sense2/@title}">
								<xsl:value-of select="Sense2/@title"/>
							</a>
							<p class="definition"><xsl:copy-of select="Sense2/FirstSentence"/></p>
						</div>
					</li>
				</ul>
				
		</xsl:if>
			
			
			
			
		
		<xsl:if test="ArticlesInCommon/ArticleInCommon">
			<h2>Mutual Links</h2>
			
			<p class="explanation">
				Articles which link to both <em><xsl:value-of select="@term1"/></em> 
				and <em><xsl:value-of select="@term2"/></em> are shown below, to help to explain how they are related. 
				Wikipedia Miner makes these very cheap to 
				obtain, as a consequence of how the relatedness measure is calculated.  
	  		</p>
	  		
			<ul class="horizontal">
				<xsl:for-each select="ArticlesInCommon/ArticleInCommon">
				<xsl:sort select="@title"/>
					<li>
						<a href="{$service_name}?task=search&amp;id={@id}&amp;term={@title}" style="color:rgb({round(150-(150*((@relatedness1*@relatedness2) div 2)))},{round(200-(200*((@relatedness1*@relatedness2) div 2)))},200)"  title="{round(@relatedness1*100)}% relatedness to {$term1}, {round(@relatedness2*100)}% relatedness to {$term2}">
							<xsl:value-of select="@title"/>
						</a>
					</li>
				</xsl:for-each>
			</ul>
			<div class="break"></div>
		</xsl:if>
		
		<xsl:if test="Snippets/Snippet">
			
			<h2>Snippets</h2>
			
			<p class="explanation">
				The sentences below explain how <em><xsl:value-of select="@term1"/></em> 
				and <em><xsl:value-of select="@term2"/></em> are related. 
				Wikipedia Miner makes these cheap to obtain by summarizing the structure of articles.  
	  		</p>
			
			<ul>
				<xsl:for-each select="Snippets/Snippet">
					<li>
						<p class="snippetText"><xsl:copy-of select="."/></p>
							
						<p class="snippetSource">
							from 
							<a href="{$service_name}?task=search&amp;id={@sourceId}&amp;term={@sourceTitle}">
							<xsl:value-of select="@sourceTitle"/>
							</a>
						</p>
						
					</li>
				</xsl:for-each>
			</ul>	
		</xsl:if>
			
		</xsl:otherwise>
	</xsl:choose>

 		<script type="text/javascript">
			var gaJsHost = (("https:" == document.location.protocol) ? "https://ssl." : "http://www.");
			document.write(unescape("%3Cscript src='" + gaJsHost + "google-analytics.com/ga.js' type='text/javascript'%3E%3C/script%3E"));
		</script>
		<script type="text/javascript">
			var pageTracker = _gat._getTracker("UA-611266-7");
			pageTracker._initData();
			pageTracker._trackPageview();
		</script>



  </body>
  </html>
</xsl:template>

</xsl:stylesheet>
