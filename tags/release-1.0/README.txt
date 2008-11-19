Wikipedia Miner Toolkit
Copyright (C) 2008 David Milne, University Of Waikato
Wikipedia Miner comes with ABSOLUTELY NO WARRANTY; for details see LICENSE.txt
This is free software, and you are welcome to redistribute it


Requirements
---------------------------------------------------------

	Perl 
		- http://www.perl.com/
		
	Parse:MediaWikiDump 
		- http://search.cpan.org/dist/Parse-MediaWikiDump/
		
	MySQL
		- http://dev.mysql.com/downloads/
		
	Java SDK
		- http://www.java.com/en/
		
	MySQLConnectorJ toolkit
		- http://www.mysql.com/products/connector/j/
		
	Trove toolkit
		- http://trove4j.sourceforge.net/
		
	At least 3G of memory
	
	Lots of time and hard-drive space...
	
	
Installation
---------------------------------------------------------

1) Set up a mysql server

	This should be configured for largish MyISAM databases. You will need a database to store the wikipedia data, and an 
	account that has write access to it.
		
2) Download and uncompress the wikipedia data

	http://download.wikimedia.org/enwiki/
	
	We want the file with current versions of article content, the one that ends with "pages-articles.xml"
	Uncompress it and put it in a folder on its own, on a drive where you have lots of space.
	
3) Tweak the extraction script for your version of Wikipedia

	This is only necessary if not working with the en (English) Wikipedia. Open up 
	extraction/extractWikipediaData.pl in a text editor. Under the section called 
	"tweaking for different versions of wikipedia", modify the following variables:
	
	@disambig_templates
		array of template names that are used to identify disambiguation pages
		
	@disambig_categories
		array of category names that disambiguation pages belong to
		this is only needed because alot of people do this directly, instead of using the appropriate template
	
	$root_category
		name of the root category, the one from which all other categories descend	
	
	WARNING: I have only tested these scripts on en and simple Wikipedias. Its on my to-do list.
	
3) Extract cvs summaries from the xml dump

	The perl scripts for this are in the extraction directory. The main one, extractWikipediaData, does everything 
	that most users will need (more on what it doesn't do later). To run it, call 
	
	perl extractWikipediaData <dir>
	
	where dir is the directory where you put the xml dump. You can also supply an extra flag "-noContent" if you just want
	the structure of how pages link to each other rather than thier full textual content. This takes up a huge amount of space,
	and you can do a lot (finding topics, navigating links, identifying how topics relate to each other, etc) without it.
	
	WARNING: this will keep the computer busy for a couple of days. If you do need to halt the process then dont worry, it will 
	pick up where it left off. 
	
	The only cvs file that this will not extract is anchor_occurance.csv, which compares how often anchor terms are used as links, 
	and how often they occur in plain text. Chances are you wont want this--it's mainly useful for identifying how likely terms are 
	to correspond to topics, so that topics can be recognized when they occur in plain text.
	
	This takes a very long time to calculate. Yup, longer than extracting all of the other files. Fortunately it is easily paralizable. 
	I've included the following scripts so that you can throw multiple computers at the problem, if you have them.
	
		- splitData.pl <dir> <n>
				splits the data found in <dir> into <n> seperate files. 
				The files are saved as split_0.csv, split_1.csv, ...split_<n-1>.csv within the provided directory.
		- extractAnchorOccurances.pl <dir>
				calculates anchor occurances from a split file. The directory must contain one of the split files produced by splitData, 
				and the anchor.csv file created by extractWikipediaData. The results are saved in <dir>/anchor_occurance_<n>.csv.
		- mergeAnchorOccurances.pl <dir> <n>
				merges results calculated by the seperate computers. The <dir> must contain all of the seperate anchor_occurance_<n>.csv files
				the result is saved to <dir>/anchor_occurance.csv
				
4) Import the extracted data into MySQL

	The easiest way to do this is via java--just create an instance of WikipediaDatabase with the details of the database you created 
	earlier, and call the loadData() method with the directory containing the extracted csv files (details in the JavaDoc). 
	This will do the work of creating all of the tables and loading the data into them, and will even give you information on 
	how long it's taking. At worst this should take a few hours.
	
	The details of this are in the JavaDoc.
	
	NOTE: You need the MySQLConnectorJ, Trove, and WikipediaMiner jar files in the build path to comple and run the java code.
	You may also need to increase the memory available to the Java virtual machine, with the -Xmx flag
	
5) Delete unneeded files

	Dont delete everything. Some of the csv files will be needed for caching data to memory, because its faster to do 
	that from file than from the database. So keep the following files:
		- page.csv
		- categorylink.csv
		- pagelink_out.csv
		- pagelink_in.csv
		- anchor_summary.csv
		- anchor_occurance.csv
		
	You can delete all of the others. It might be worth zipping the original xml dump up and keeping that though, because they
	dont seem to be archived anywhere for more than a few months.
	
5) Start developing!
	
	Hopefully the JavaDoc (in the doc folder) will be clear enough to get you going. Also have a look at the main methods for 
	each of the main classes (Wikipedia, Article, Anchor, Category, etc) for demos on how to use them.
	
	If you have any trouble, post it up on the SourceForge forum.
	
