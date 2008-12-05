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
    
Now, if you are happy using the versions of Wikipedia that we have preprocessed and made available, then download one of 
them from the data package on sourceforge, and skip to step 5. Otherwise...
       
2) Download and uncompress a wikipedia xml dump

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
        this is only needed because a lot of people do this directly, instead of using the appropriate template
   
    $root_category
        name of the root category, the one from which all other categories descend   
   
    WARNING: I have only tested these scripts on en and simple Wikipedias. If you get a language version working, 
    then please post the details up on the sourceforge forum.
   
4) Extract csv summaries from the xml dump

    The perl scripts for this are in the extraction directory. 
    
    a) 
    
    The main script, extractWikipediaData, does everything that most users will need (more on what it doesn't do later). To run it, call
   
    	perl extractWikipediaData <dir>
   
    where dir is the directory where you put the xml dump. 
    
    You can supply an extra flag "-noContent" if you just want the structure of how pages link to each other rather than 
    their full textual content. This takes up a huge amount of space, and you can do a lot (finding topics, navigating links, 
    identifying how topics relate to each other, etc) without it.
               
    WARNING: this will keep the computer busy for a day or two; you can check out the forums to see how long it has taken other people.
    If you do need to halt the process then don't worry, it will pick up more-or-less where it left off. 
    
    If the script seems to stall, particularly when summarizing anchors or the links in to each page, then chances are you have run out
    of memory. There is another flag "-passes" to split the data up and make it fit. The default is "-passes 2"; try something higher and
    see how it goes.  
    
    
    b) 
   
    The only csv file that the above script will not extract is anchor_occurance.csv, which compares how often anchor terms are used as links,
    and how often they occur in plain text. Chances are you won't want this--it's mainly useful for identifying how likely terms are
    to correspond to topics, so that topics can be recognized when they occur in plain text.
   
    This takes a very long time to calculate. Yup, longer than extracting all of the other files. Fortunately it is easily parallelized.
    I've included the following scripts so that you can throw multiple computers (or processors) at the problem, if you have them.
   
        - splitData.pl <dir> <n>
                splits the data found in <dir> into <n> seperate files.
                The files are saved as split_0.csv, split_1.csv, ...split_<n-1>.csv within the provided directory.
        - extractAnchorOccurances.pl <dir>
                calculates anchor occurrences from a split file. The directory must contain one of the split files produced by splitData,
                and the anchor.csv file created by extractWikipediaData. The results are saved in <dir>/anchor_occurance_<n>.csv.
        - mergeAnchorOccurances.pl <dir> <n>
                merges results calculated by the separate computers. The <dir> must contain all of the seperate anchor_occurance_<n>.csv files
                the result is saved to <dir>/anchor_occurance.csv
               
5) Import the extracted data into MySQL

    The easiest way to do this is via java--just create an instance of WikipediaDatabase with the details of the database you created
    earlier, and call the loadData() method with the directory containing the extracted csv files.
    This will do the work of creating all of the tables and loading the data into them, and will even give you information on
    how long it's taking. At worst this should take a few hours.
   
    The details of this are in the JavaDoc.
   
    NOTE: You need the MySQLConnectorJ, Trove, and Wikipedia-Miner jar files in the build path to compile and run the java code.
    You may also need to increase the memory available to the Java virtual machine, with the -Xmx flag
   
6) Delete unneeded files

    Don't delete everything. Some of the csv files will be needed for caching data to memory, because its faster to do
    that from file than from the database. So keep the following files:
        - page.csv
        - categorylink.csv
        - pagelink_out.csv
        - pagelink_in.csv
        - anchor_summary.csv
        - anchor_occurance.csv
        - generality.csv
       
    You can delete all of the others. It might be worth zipping the original xml dump up and keeping that though, because they
    don't seem to be archived anywhere for more than a few months.
   
7) Start developing!
   
    Hopefully the JavaDoc (in the doc folder) will be clear enough to get you going. Also have a look at the main methods for
    each of the main classes (Wikipedia, Article, Anchor, Category, etc) for demos on how to use them.
   
    Pop into the SourceForge forum if you have any trouble.
   

