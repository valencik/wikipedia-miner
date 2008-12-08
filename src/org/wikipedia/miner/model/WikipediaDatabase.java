/*
 *    WikipediaDatabase.java
 *    Copyright (C) 2007 David Milne, d.n.milne@gmail.com
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.wikipedia.miner.model;

import java.io.* ;
import java.sql.*;
import java.util.* ;

import gnu.trove.* ;

import org.wikipedia.miner.util.*;
import org.wikipedia.miner.util.text.*;

/**
 * This class loads, provides access to and maintains the Wikipedia database. It most cases it 
 * should not be used directly - use a Wikipedia instance instead.  
 * 
 * <b>NOTE:</b> Each wikipedia database is only intended to store one instance of Wikipedia. If you require more,
 * (for different languages, or from different points in time) then you must create seperate databases.
 * 
 * @author David Milne
 */
public class WikipediaDatabase extends MySqlDatabase {
	
	private HashMap<String,String> createStatements  ;
	
	private boolean contentImported = true ;
	private boolean anchorOccurancesSummarized = true ;
	
	private int article_count = 0 ;
	private int category_count = 0 ;
	private int redirect_count = 0 ;
	private int disambig_count = 0 ;
	private int maxPageDepth = 0 ;
	
	protected THashMap<String,CachedAnchor> cachedAnchors = null ;
	protected TIntObjectHashMap<CachedPage> cachedPages = null ;
	protected TIntObjectHashMap<int[]> cachedInLinks = null ;
	protected TIntObjectHashMap<int[][]> cachedOutLinks = null ;
	protected TIntIntHashMap cachedGenerality = null ; 
	
	private TextProcessor cachedProcessor = null ;
		
	/**
	 * Initializes a newly created WikipediaDatabase and attempts to make a connection to the mysql
	 * database defined by the arguments given, as defined by MySqlDatabase. In addition, it will check
	 * that the wikipedia database is complete; that all necessary tables and indexes exist.
	 *
	 * @param	server	the connection string for the server (e.g 130.232.231.053:8080 or bob:8080)
	 * @param	databaseName	the name of the database (e.g <em>enwiki</em>)
	 * @param	userName		the user for the sql database (null if anonymous)
	 * @param	password	the users password (null if anonymous)
	 * @throws	Exception	if a connection cannot be made.
	 */
	public WikipediaDatabase(String server, String databaseName, String userName, String password) throws Exception{
		
		super(server, databaseName, userName, password, "utf8") ;
		
		createStatements = new HashMap<String,String>() ;
		
		createStatements.put("page", "CREATE TABLE page (" 
				+ "page_id int(8) unsigned NOT NULL, "
				+ "page_title varchar(255) binary NOT NULL, "
				+ "page_type int(2) NOT NULL default '0', "
				+ "PRIMARY KEY  (page_id),"
				+ "UNIQUE KEY type_title (page_type,page_title)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		
		createStatements.put("redirect", "CREATE TABLE redirect (" 
				+ "rd_from int(8) unsigned NOT NULL, "
				+ "rd_to int(8) unsigned NOT NULL, "
				+ "PRIMARY KEY (rd_from, rd_to),"
				+ "KEY rd_to (rd_to)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		
		/*
		createStatements.put("pagelink", "CREATE TABLE pagelink (" 
				+ "pl_from int(8) unsigned NOT NULL, "
				+ "pl_to int(8) unsigned NOT NULL, "
				+ "PRIMARY KEY (pl_from, pl_to), "
				+ "KEY pl_to (pl_to)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		*/
		
		createStatements.put("categorylink", "CREATE TABLE categorylink (" 
				+ "cl_parent int(8) unsigned NOT NULL, "
				+ "cl_child int(8) unsigned NOT NULL, "
				+ "PRIMARY KEY (cl_parent, cl_child), "
				+ "KEY cl_child (cl_child)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;		  
				  
		createStatements.put("translation", "CREATE TABLE translation (" 
				+ "tl_id int(8) unsigned NOT NULL, "
				+ "tl_lang varchar(10) binary NOT NULL, "
				+ "tl_text varchar(255) binary NOT NULL, "
				+ "PRIMARY KEY (tl_id, tl_lang)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
	  
		createStatements.put("disambiguation", "CREATE TABLE disambiguation (" 
				+ "da_from int(8) unsigned NOT NULL, "
				+ "da_to int(8) unsigned NOT NULL, "
				+ "da_index int(3) unsigned NOT NULL, "
				+ "da_scope mediumblob NOT NULL, "
				+ "PRIMARY KEY (da_from, da_to), "
				+ "KEY da_to (da_to)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;	 
		
		createStatements.put("linkcount", "CREATE TABLE linkcount (" 
				+ "lc_id int(8) unsigned NOT NULL, "
				+ "lc_in int(8) unsigned NOT NULL, "
				+ "lc_out int(8) unsigned NOT NULL, "
				+ "PRIMARY KEY (lc_id)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		
		createStatements.put("content", "CREATE TABLE content (" 
				+ "co_id int(8) unsigned NOT NULL, "
				+ "co_content mediumblob NOT NULL, "
				+ "PRIMARY KEY (co_id)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		
		createStatements.put("pagelink_in", "CREATE TABLE pagelink_in (" 
				+ "li_id int(8) unsigned NOT NULL, "
				+ "li_data mediumblob NOT NULL, "
				+ "PRIMARY KEY (li_id)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		
		createStatements.put("pagelink_out", "CREATE TABLE pagelink_out (" 
				+ "lo_id int(8) unsigned NOT NULL, "
				+ "lo_data mediumblob NOT NULL, "
				+ "PRIMARY KEY (lo_id)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
				
		createStatements.put("equivalence", "CREATE TABLE equivalence (" 
				+ "eq_cat int(8) unsigned NOT NULL, "
				+ "eq_art int(8) unsigned NOT NULL, "
				+ "PRIMARY KEY (eq_cat), " 
				+ "UNIQUE KEY (eq_art)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		
		createStatements.put("anchor", "CREATE TABLE anchor (" 
				+ "an_text varchar(300) binary NOT NULL, "
				+ "an_to int(8) unsigned NOT NULL, "
				+ "an_count int(8) unsigned NOT NULL, "
				+ "PRIMARY KEY (an_text, an_to), " 
				+ "KEY (an_to)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		
		createStatements.put("anchor_occurance", "CREATE TABLE anchor_occurance (" 
				+ "ao_text varchar(300) binary NOT NULL, "
				+ "ao_linkCount int(8) unsigned NOT NULL, "
				+ "ao_occCount int(8) unsigned NOT NULL, "
				+ "PRIMARY KEY (ao_text)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		
		createStatements.put("stats", "CREATE TABLE stats (" 
						+ "st_articles int(8) unsigned NOT NULL, "
						+ "st_categories int(8) unsigned NOT NULL, "
						+ "st_redirects int(8) unsigned NOT NULL, "
						+ "st_disambigs int(8) unsigned NOT NULL) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ;
		
		createStatements.put("generality", "CREATE TABLE generality (" 
				+ "gn_id int(8) unsigned NOT NULL, "
				+ "gn_depth int(2) unsigned NOT NULL, "
				+ "PRIMARY KEY (gn_id)) ENGINE=MyISAM DEFAULT CHARSET=utf8;") ; 
		
		try {
			checkDatabase() ;	
			setStats() ;
		} catch (SQLException e) {
			System.out.println("WARNING: wikipedia database is incomplete.") ;
			e.printStackTrace() ;
		}
	}
	
	private void setStats() throws SQLException {
		Statement stmt = createStatement() ;
		ResultSet rs = stmt.executeQuery("SELECT * FROM stats") ;
		
		if (rs.first()) {
			this.article_count = rs.getInt(1) ;
			this.category_count = rs.getInt(2) ;
			this.redirect_count = rs.getInt(3) ;
			this.disambig_count = rs.getInt(4) ;
		}
		
		rs.close() ;
		stmt.close() ;		
	}
	
	public void prepareForMorphologicalProcessor(TextProcessor tp) throws SQLException{
		prepareAnchorsForMorphologicalProcessor(tp) ;
		
		if (tableExists("anchor_occurance"))
			prepareAnchorOccurancesForMorphologicalProcessor(tp) ;
	}
	
	private void prepareAnchorsForMorphologicalProcessor(TextProcessor tp) throws SQLException {
		
		System.out.println("Preparing anchors for " + tp.getName()) ;
		String tableName = "anchor_" + tp.getName() ;
		
		int rows = this.getRowCountExact("anchor") ;
		ProgressDisplayer pd = new ProgressDisplayer("Gathering and processing anchors", rows) ;
		
		Statement stmt = createStatement() ;
		stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName) ;
		stmt.close() ;
		
		stmt = createStatement() ;
		stmt.executeUpdate("CREATE TABLE " + tableName + " (" 
						+ "an_text varchar(500) character set latin1 collate latin1_bin NOT NULL, "
						+ "an_to int(8) unsigned NOT NULL, "
						+ "an_count int(8) unsigned NOT NULL, "
						+ "PRIMARY KEY (an_text, an_to), " 
						+ "KEY (an_to)) ; ") ;
		stmt.close() ;
		
		HashMap<String, Integer> anchorCounts = new HashMap<String, Integer>() ;
		
		
			
		int currRow = 0 ;
		
		int chunkIndex = 0 ;
		int chunkSize = 100000 ;
						
		while (chunkIndex * chunkSize < rows) {
			
			//if (chunkIndex > 10) break ;
			stmt = createStatement() ;
			ResultSet rs = stmt.executeQuery("SELECT * FROM anchor LIMIT " + chunkIndex * chunkSize + ", " + chunkSize ) ;
			
			while (rs.next()) {
				try {
					
					String an_text = new String(rs.getBytes(1), "UTF-8") ;
					long an_to = rs.getLong(2) ;
					int an_count = rs.getInt(3) ;
					
					an_text = tp.processText(an_text) ;
					
					//System.out.println(an_text + "," + an_to + "," + an_count) ;
					
					Integer count = anchorCounts.get(an_text + ":" + an_to) ;
					
					if (count == null)
						anchorCounts.put(an_text + ":" + an_to, an_count) ;
					else
						anchorCounts.put(an_text + ":" + an_to, count + an_count) ;
				
				} catch (Exception e) {e.printStackTrace() ;} ;
				
				currRow++ ;
			}
			
			rs.close() ;
			stmt.close() ;
			
			chunkIndex++ ;
			pd.update(currRow) ;
		}
		
		rows = anchorCounts.size() ;
		pd = new ProgressDisplayer("Saving processed anchors", rows) ;
		
		chunkIndex = 0 ;
		
		currRow = 0 ;
		
		StringBuffer insertQuery = new StringBuffer() ; ;
		
		for(String key:anchorCounts.keySet()) {
			currRow ++ ;
			
			int pos = key.lastIndexOf(':') ;
			
			String an_text = key.substring(0, pos) ;
			long an_to = new Long(key.substring(pos+1)).longValue() ;
			int an_count = anchorCounts.get(key) ;
			
			if (an_text != "") 
				insertQuery.append(" (\"" + addEscapes(an_text) + "\"," + an_to + "," + an_count + "),") ;
			
			if (currRow%chunkSize == 0) {
				if (insertQuery.length() > 0) {
					insertQuery.delete(insertQuery.length()-1, insertQuery.length()) ;
					
					stmt = createStatement() ;
					stmt.executeUpdate("INSERT IGNORE INTO " + tableName + " VALUES" + insertQuery.toString() ) ;
					stmt.close() ;
					
					insertQuery = new StringBuffer() ;
				}
				
				pd.update(currRow) ;
			}
		}
		
		if (insertQuery.length() > 0) {
			insertQuery.delete(insertQuery.length()-1, insertQuery.length()) ;
						
			stmt = createStatement() ;
			stmt.executeUpdate("INSERT IGNORE INTO " + tableName + " VALUES" + insertQuery.toString() ) ;
			stmt.close() ;
		}
	}
	
	private void prepareAnchorOccurancesForMorphologicalProcessor(TextProcessor tp) throws SQLException {
		
		System.out.println("Preparing anchor occurances for " + tp.getName()) ;
		String tableName = "anchor_occurance_" + tp.getName() ;
		
		int rows = this.getRowCountExact("anchor") ;
		ProgressDisplayer pd = new ProgressDisplayer("Gathering and processing anchor occurances", rows) ;
		
		Statement stmt = createStatement() ;
		stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName) ;
		stmt.close() ;
		
		stmt = createStatement() ;
		stmt.executeUpdate("CREATE TABLE " + tableName + " (" 
				+ "ao_text varchar(500) character set latin1 collate latin1_bin NOT NULL, "
				+ "ao_linkCount int(8) unsigned NOT NULL, "				
				+ "ao_occCount int(8) unsigned NOT NULL, "
				+ "PRIMARY KEY (ao_text)) ; ") ;
					
		stmt.close() ;
		
		HashMap<String, Integer[]> occuranceStats = new HashMap<String, Integer[]>() ;
		
		int currRow = 0 ;
		
		int chunkIndex = 0 ;
		int chunkSize = 100000 ;
						
		while (chunkIndex * chunkSize < rows) {
			
			//if (chunkIndex > 10) break ;
			stmt = createStatement() ;
			ResultSet rs = stmt.executeQuery("SELECT * FROM anchor_occurance LIMIT " + chunkIndex * chunkSize + ", " + chunkSize ) ;
			
			while (rs.next()) {
				try {
					
					String ao_text = new String(rs.getBytes(1), "UTF-8") ;
					int ao_linkCount = rs.getInt(2) ;
					int ao_occCount = rs.getInt(3) ;
					
					ao_text = tp.processText(ao_text) ;					
					Integer[] stats = occuranceStats.get(ao_text) ;
					
					if (stats != null) {
						stats[0] = stats[0] + ao_linkCount ;
						stats[1] = stats[1] + ao_occCount ;
					} else {
						stats = new Integer[2] ;
						stats[0] = ao_linkCount ;
						stats[1] = ao_occCount ;
					}
					
					occuranceStats.put(ao_text, stats) ;
				
				} catch (Exception e) {e.printStackTrace() ;} ;
				
				currRow++ ;
			}
			
			rs.close() ;
			stmt.close() ;
			
			chunkIndex++ ;
			pd.update(currRow) ;
		}
		
		rows = occuranceStats.size() ;
		pd = new ProgressDisplayer("Saving processed anchor occurances", rows) ;
		
		chunkIndex = 0 ;
		
		currRow = 0 ;
		
		StringBuffer insertQuery = new StringBuffer() ; ;
		
		for(String anchor:occuranceStats.keySet()) {
			currRow ++ ;
			
			Integer[] stats = occuranceStats.get(anchor) ;
						
			if (anchor != "") 
				insertQuery.append(" (\"" + addEscapes(anchor) + "\"," + stats[0] + "," + stats[1] + "),") ;
			
			if (currRow%chunkSize == 0) {
				if (insertQuery.length() > 0) {
					insertQuery.delete(insertQuery.length()-1, insertQuery.length()) ;
					
					stmt = createStatement() ;
					stmt.executeUpdate("INSERT IGNORE INTO " + tableName + " VALUES" + insertQuery.toString() ) ;
					stmt.close() ;
					
					insertQuery = new StringBuffer() ;
				}
				
				pd.update(currRow) ;
			}
		}
		
		if (insertQuery.length() > 0) {
			insertQuery.delete(insertQuery.length()-1, insertQuery.length()) ;
						
			stmt = createStatement() ;
			stmt.executeUpdate("INSERT IGNORE INTO " + tableName + " VALUES" + insertQuery.toString() ) ;
			stmt.close() ;
		}
	}	
	
	/**
	 * Loads a directory of summarized csv tables into the database. If overwrite is set to true, then 
	 * all previously stored data is overwritten. 
	 * 
	 * If any core files ('page', 'pagelink', 'categorylink', 'redirect', 'disambiguation', 'translation', 'pagelink', 'stats') 
	 * are missing or unreadable, then this method will fail without making any modifications to the stored data, regardless of  
	 * <em>overwrite</em>. If any optional files are found (e.g. 'content', or 'ngram') then these will be loaded as well.
	 * 
	 * 
	 * @param directory the directory in which the data files are located
	 * @param overwrite true if existing tables are to be overwritten
	 * @throws SQLException if there is a problem with the database
	 * @throws IOException if there is a problem with the files to be loaded.
	 */
	public void loadData(File directory, boolean overwrite) throws SQLException, IOException {
		
		//check that all manditory files exist
		
		File page = new File(directory.getPath() + File.separatorChar + "page.csv") ;
		checkFile(page) ;
		
		File redirect = new File(directory.getPath() + File.separatorChar + "redirect.csv") ;
		checkFile(redirect) ;
		
		File disambig = new File(directory.getPath() + File.separatorChar + "disambiguation.csv") ;
		checkFile(disambig) ;
		
		File translation = new File(directory.getPath() + File.separatorChar + "translation.csv") ;
		checkFile(translation) ;
		
		File catlink = new File(directory.getPath() + File.separatorChar + "categorylink.csv") ;
		checkFile(catlink) ;
		
		/*
		File pagelinkFile = new File(directory.getPath() + File.separatorChar + "pagelink.csv") ;
		if (!pagelinkFile.canRead())
			throw new IOException(pagelinkFile.getPath() + " cannot be read") ;
		
		
		//File linkcount = new File(directory.getPath() + File.separatorChar + "linkcount.csv") ;
		//checkFile(linkcount) ; 
		*/
		
		File anchor = new File(directory.getPath() + File.separatorChar + "anchor.csv") ;
		checkFile(anchor) ;
		
		File stats = new File(directory.getPath() + File.separatorChar + "stats.csv") ;
		checkFile(stats) ;
		
		File generality = new File(directory.getPath() + File.separatorChar + "generality.csv") ;
		checkFile(generality) ;
		
		File equivalence = new File(directory.getPath() + File.separatorChar + "equivalence.csv") ;
		checkFile(equivalence) ;
		
		File pagelink_in = new File(directory.getPath() + File.separatorChar + "pagelink_in.csv") ;
		checkFile(pagelink_in) ;
		
		File pagelink_out = new File(directory.getPath() + File.separatorChar + "pagelink_out.csv") ;
		checkFile(pagelink_out) ;
		
		// load manditory tables 
		
		if (overwrite || !tableExists("page")) {
			initializeTable("page") ;
			loadFile(page, "page") ;
		}
		
		if (overwrite || !tableExists("redirect")) {
			initializeTable("redirect") ;
			loadFile(redirect, "redirect") ;
		}
		
		if (overwrite || !tableExists("disambiguation")) {
			initializeTable("disambiguation") ;
			loadFile(disambig, "disambiguation") ;
		}
		
		if (overwrite || !tableExists("translation")) {
			initializeTable("translation") ;
			loadFile(translation, "translation") ;
		}
		
		if (overwrite || !tableExists("categorylink")) {
			initializeTable("categorylink") ;
			loadFile(catlink, "categorylink") ;
		}
		
		/*
		if (overwrite || !tableExists("pagelink")) {
			initializeTable("pagelink") ;
			loadFile(pagelinkFile, "pagelink") ;
		}
		
		if (overwrite || !tableExists("linkcount")) {
			initializeTable("linkcount") ;
			loadFile(linkcount, "linkcount") ;
		}*/
		
		if (overwrite || !tableExists("anchor")) {
			initializeTable("anchor") ;
			loadFile(anchor, "anchor") ;
		}
		
		if (overwrite || !tableExists("stats")) {
			initializeTable("stats") ;
			loadFile(stats, "stats") ;
		}
		
		if (overwrite || !tableExists("generality")) {
			initializeTable("generality") ;
			loadFile(generality, "generality") ;
		}
		
		if (overwrite || !tableExists("equivalence")) {
			initializeTable("equivalence") ;
			loadFile(equivalence, "equivalence") ;
		}
		
		if (overwrite || !tableExists("pagelink_in")) {
			initializeTable("pagelink_in") ;
			loadFile(pagelink_in, "pagelink_in") ;
		}
		
		if (overwrite || !tableExists("pagelink_out")) {
			initializeTable("pagelink_out") ;
			loadFile(pagelink_out, "pagelink_out") ;
		}
				
		if (overwrite || !tableExists("content")) {
			File contentFile = new File(directory.getPath() + File.separatorChar + "content.csv") ;
			
			if (contentFile.canRead()) {
				initializeTable("content") ;
				loadFile(contentFile, "content") ;
			}
		}
		
		if (overwrite || !tableExists("anchor_occurance")) {
			File occFile = new File(directory.getPath() + File.separatorChar + "anchor_occurance.csv") ;
			
			if (occFile.canRead()) {
				initializeTable("anchor_occurance") ;
				loadFile(occFile, "anchor_occurance") ;
			}
		}	
	}
	
	private void checkFile(File file) throws IOException {
		if (!file.canRead())
			throw new IOException(file.getPath() + " cannot be read") ;
	}
		
	private void loadFile(File file, String tableName) throws IOException, SQLException{
		
		long bytes = file.length() ; 
		ProgressDisplayer pd = new ProgressDisplayer("Loading " + tableName, bytes) ;
		pd.update(0) ;
		
		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")) ;
		String line ;
		
		long bytesRead = 0 ;		
		long chunkSize = bytes/100 ;
				
		StringBuffer insertQuery = new StringBuffer() ;
		
		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() ;
				
			insertQuery.append("(" + line + "),") ; 
			
			if (insertQuery.length() > chunkSize) {
				if (insertQuery.length() > 0) {
					insertQuery.delete(insertQuery.length()-1, insertQuery.length()) ;
					
					try {
						Statement stmt = createStatement() ;
						stmt.setEscapeProcessing(false) ;
						stmt.executeUpdate("INSERT IGNORE INTO " + tableName + " VALUES" + insertQuery.toString() ) ;
						stmt.close() ;
					} catch (SQLException e) {
						System.out.println(insertQuery) ;
						throw(e) ;
					}
					
					insertQuery = new StringBuffer() ;
				}
				pd.update(bytesRead) ;
			}
		}
		
		input.close() ;
		
		if (insertQuery.length() > 0) {
			insertQuery.delete(insertQuery.length()-1, insertQuery.length()) ;
			
			Statement stmt = createStatement() ;
			stmt.executeUpdate("INSERT IGNORE INTO " + tableName + " VALUES" + insertQuery.toString() ) ;
			stmt.close() ;
		}
		
		pd.update(bytes) ;
	}
	
	private int getLineCount(File file) throws IOException{
		int count = 0 ;
		
		BufferedReader input = new BufferedReader(new FileReader(file)) ;

		while (input.readLine() != null) 
			count ++ ;
		
		input.close();
		
		return count ;		
	}
	
	private void initializeTable(String tableName) throws SQLException {
		
		Statement stmt ;
		
		if (tableExists(tableName)) {
			stmt = createStatement() ;
			stmt.executeUpdate("TRUNCATE TABLE " + tableName) ;
			stmt.close() ;
			
			stmt = createStatement() ;
			stmt.executeUpdate("DROP TABLE " + tableName) ;
			stmt.close() ;
		}
		
		stmt = createStatement() ;
		stmt.executeUpdate(createStatements.get(tableName)) ;
		stmt.close() ;
	}
	
	
	/**
	 * Checks that this database contains all core tables.
	 * 
	 * @throws SQLException if it doesnt.
	 */
	public void checkDatabase() throws SQLException {
		
		if (!tableExists("page"))
			throw new SQLException("mysql table 'page' does not exist and must be imported.") ;
		
		if (!tableExists("categorylink"))
			throw new SQLException("mysql table 'categorylink' does not exist and must be imported.") ;
				
		if (!tableExists("redirect"))
			throw new SQLException("mysql table 'redirect' does not exist and must be imported.") ;
		
		if (!tableExists("translation"))
			throw new SQLException("mysql table 'translation' does not exist and must be imported.") ;
		
		if (!tableExists("disambiguation"))
			throw new SQLException("mysql table 'disambiguation' does not exist and must be imported.") ;
		
		if (!tableExists("stats"))
			throw new SQLException("mysql table 'stats' does not exist and must be imported.") ;
		
		if (!tableExists("pagelink_in"))
			throw new SQLException("mysql table 'pagelink_in' does not exist and must be imported.") ;
		
		if (!tableExists("pagelink_out"))
			throw new SQLException("mysql table 'pagelink_out' does not exist and must be imported.") ;
		
		if (!tableExists("equivalence"))
			throw new SQLException("mysql table 'equivalence' does not exist and must be imported.") ;
		
		if (!tableExists("generality"))
			throw new SQLException("mysql table 'generality' does not exist and must be imported.") ;
		
		if (!tableExists("content")){
			contentImported = false ;
			System.err.println("WARNING: page content has not been imported. You will only be able to retrieve the structure of wikipedia, not it's content.") ;
		}
		
		if (!tableExists("anchor_occurance")){
			anchorOccurancesSummarized = false ;
			System.err.println("WARNING: anchor occurances have not been imported. You will not be able to calculate how often anchor terms occur as plain text.") ;
		}
	}
	
	/**
	 * Checks if the database has been prepared for use with a particular morphological processor
	 * 
	 * @param TextProcessor the TextProcessor to be checked.
	 * @throws SQLException if the data has not been prepared for this TextProcessor.
	 */
	public void checkMorphologicalProcessor(TextProcessor TextProcessor) throws SQLException {
			
		if (!tableExists("anchor_" + TextProcessor.getName()))
			throw new SQLException("anchors have not been prepared for the morphological processor \"" + TextProcessor.getName() + "\"") ;
		
		if (!tableExists("ngram")) {
			if (!tableExists("ngram_" + TextProcessor.getName()))
				throw new SQLException("ngrams have not been prepared for the morphological processor \"" + TextProcessor.getName() + "\"") ;
		}
	}

	public boolean isContentImported() {
		return contentImported;
	}
	
	public boolean areAnchorOccurancesSummarized() {
		return anchorOccurancesSummarized;
	}
	
	public int getArticleCount() {
		return article_count;
	}

	public int getCategoryCount() {
		return category_count;
	}

	public int getDisambigCount() {
		return disambig_count;
	}

	public int getRedirectCount() {
		return redirect_count;
	}
	
	public int getPageCount() {
		return article_count + category_count + disambig_count + redirect_count ;
	}
	
	public int getMaxPageDepth() throws SQLException{
		if (maxPageDepth > 0)
			return maxPageDepth ;
			
		Statement stmt = createStatement() ;
		ResultSet rs = stmt.executeQuery("SELECT MAX(gn_depth) FROM generality") ;
		
		if (rs.first()) {
			try {
				maxPageDepth = rs.getInt(1) ; 
			} catch (Exception e) {} ;
		}
		
		rs.close() ;
		stmt.close() ;
		
		return maxPageDepth ;
	}
	
	public void cacheAnchors(File dir, TextProcessor tp) throws IOException{
		
		File occuranceFile = new File(dir.getPath() + File.separatorChar + "anchor_occurance.csv") ;
		File anchorFile = new File(dir.getPath() + File.separatorChar + "anchor_summary.csv") ;
		//int rows = getLineCount(ngramFile) ;
		
		cachedAnchors = new THashMap<String,CachedAnchor>() ;
		
		if (occuranceFile.canRead()) {
			BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(occuranceFile), "UTF-8")) ;
			
			ProgressDisplayer pd = new ProgressDisplayer("caching anchor occurances", occuranceFile.length()) ;
			
			long bytesRead = 0 ;
			long bytesInChunk = 0 ;
			long chunkSize = occuranceFile.length()/10 ;
			String line ;
						
			while ((line=input.readLine()) != null) {
				bytesRead = bytesRead + line.length() ;
				bytesInChunk = bytesInChunk + line.length() ;
				
				int sep2 = line.lastIndexOf(',') ;
				int sep1 = line.lastIndexOf(',', sep2-1) ;
				
				String ngram = line.substring(1, sep1-1) ;
				int linkCount = new Integer(line.substring(sep1+1, sep2)) ;
				int occCount = new Integer(line.substring(sep2+1)) ;
				
				if (tp != null) 
					ngram = tp.processText(ngram) ;
				
				// if we are doing morphological processing, then we need to resolve collisions
				CachedAnchor ca = cachedAnchors.get(ngram) ;
				if (ca != null)
					ca = new CachedAnchor(linkCount + ca.linkCount, occCount + ca.occCount) ;
				else 
					ca = new CachedAnchor(linkCount, occCount) ;
							
				cachedAnchors.put(ngram, ca) ;
				
				if(bytesInChunk > chunkSize) {
					pd.update(bytesRead) ;
					bytesInChunk = 0 ;
				}
			}
			input.close();
		}
		
		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(anchorFile), "UTF-8")) ;
		
		ProgressDisplayer pd = new ProgressDisplayer("caching anchor destinations", anchorFile.length()) ;
		
		long bytesRead = 0 ;
		long bytesInChunk = 0 ;
		long chunkSize = anchorFile.length()/10 ;
		String line ;
						
		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() ;
			bytesInChunk = bytesInChunk + line.length() ;
			
			int sep = line.lastIndexOf(',') ;
			String anchor = line.substring(1, sep-1) ;
			String data = line.substring(sep+2, line.length()-1) ;
			
			//System.out.println(anchor + " -> " + data) ;
			
			String[] temp = data.split(";") ;

			Integer[][] senses = new Integer[temp.length][2] ;

			int i = 0 ;
			for (String t:temp) {
				String[] values = t.split(":") ;
				senses[i][0] = new Integer(values[0]) ;
				senses[i][1] = new Integer(values[1]) ;

				i++ ;
			}
			
			if (tp != null) 
				anchor = tp.processText(anchor) ;
			
			CachedAnchor ca = cachedAnchors.get(anchor) ;
			if (ca == null) {
				ca = new CachedAnchor(senses) ;
				cachedAnchors.put(anchor, ca) ;
			} else {
				ca.addSenses(senses) ;
			}
			
			if(bytesInChunk > chunkSize) {
				pd.update(bytesRead) ;
				bytesInChunk = 0 ;
			}
		}	
		
		this.cachedProcessor = tp ;
	}
	
	public void cachePages(File dir) throws IOException {
		
		File pageFile = new File(dir.getPath() + File.separatorChar + "page.csv") ;
		
		System.out.println(getPageCount() + ", " + getLineCount(pageFile)) ;
		
		cachedPages = new TIntObjectHashMap<CachedPage>(getPageCount() , 1) ;
		
		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(pageFile), "UTF-8")) ;
		ProgressDisplayer pd = new ProgressDisplayer("caching pages", pageFile.length()) ;
		
		long bytesRead = 0 ;
		long bytesInChunk = 0 ;
		long chunkSize = pageFile.length()/10 ;
		String line ;
		
		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() ;
			bytesInChunk = bytesInChunk + line.length() ;
			
			int sep1 = line.indexOf(',') ;
			int sep2 = line.lastIndexOf(',') ;
			
			int id = new Integer(line.substring(0, sep1)) ;
			String title = line.substring(sep1+2, sep2-1) ;
			int type = new Integer(line.substring(sep2+1)) ;
					
			CachedPage p = new CachedPage(title, type) ;
			cachedPages.put(id, p) ;
			
			if(bytesInChunk > chunkSize) {
				pd.update(bytesRead) ;
				bytesInChunk = 0 ;
			}
		}
		input.close();
	}
	
	public void cacheInLinks(File dir) throws IOException {
		
		File file = new File(dir.getPath() + File.separatorChar + "pagelink_in.csv") ;		
		cachedInLinks = new TIntObjectHashMap<int[]>(getLineCount(file), 1) ;
		
		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")) ;
		ProgressDisplayer pd = new ProgressDisplayer("caching links into pages", file.length()) ;
		
		long bytesRead = 0 ;
		long bytesInChunk = 0 ;
		long chunkSize = file.length()/10 ;
		String line ;
		
		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() ;
			bytesInChunk = bytesInChunk + line.length() ;
						
			int pos = line.indexOf(',') ;
			int id = new Integer(line.substring(0, pos)) ;
			String data = line.substring(pos+2, line.length()-1) ;
			
			String[] temp = data.split(":") ;
			int[] links = new int[temp.length] ;
			
			int i = 0 ;
			for (String t:temp) {
				links[i] = new Integer(t) ;
				i++ ;
			}
			
			cachedInLinks.put(id, links) ;
			
			if(bytesInChunk > chunkSize) {
				pd.update(bytesRead) ;
				bytesInChunk = 0 ;
			}
		}
		
		input.close();
	}
	
	public void cacheOutLinks(File dir) throws IOException{
		
		File file = new File(dir.getPath() + File.separatorChar + "pagelink_out.csv") ;
		
		cachedOutLinks = new TIntObjectHashMap<int[][]>(getPageCount(), 1) ;
		
		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")) ;
		ProgressDisplayer pd = new ProgressDisplayer("caching links out from pages", file.length()) ;
		
		long bytesRead = 0 ;
		long bytesInChunk = 0 ;
		long chunkSize = file.length()/10 ;
		String line ;
		
		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() ;
			bytesInChunk = bytesInChunk + line.length() ;
						
			int pos = line.indexOf(',') ;
			int id = new Integer(line.substring(0, pos)) ;
			String data = line.substring(pos+2, line.length()-1) ;
		
		
			String[] temp = data.split(";") ;

			int[][] links = new int[temp.length][2] ;

			int i = 0 ;
			for (String t:temp) {
				String[] values = t.split(":") ;
				links[i][0] = new Integer(values[0]) ;
				links[i][1] = new Integer(values[1]) ;

				i++ ;
			}
			cachedOutLinks.put(id, links) ;
			
			if(bytesInChunk > chunkSize) {
				pd.update(bytesRead) ;
				bytesInChunk = 0 ;
			}
		}
	}
	
	public void cacheGenerality(File dir) throws IOException {
		
		File pageFile = new File(dir.getPath() + File.separatorChar + "generality.csv") ;		
		cachedGenerality = new TIntIntHashMap(getPageCount() , 1) ;
				
		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(pageFile), "UTF-8")) ;
		ProgressDisplayer pd = new ProgressDisplayer("caching generality", pageFile.length()) ;
		
		long bytesRead = 0 ;
		long bytesInChunk = 0 ;
		long chunkSize = pageFile.length()/10 ;
		String line ;
		
		int maxDepth = 0 ;
		
		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() ;
			bytesInChunk = bytesInChunk + line.length() ;
			
			int sep = line.indexOf(',') ;			
			int id = new Integer(line.substring(0, sep)) ;
			int depth = new Integer(line.substring(sep+1).trim()) ;
							
			cachedGenerality.put(id, depth) ;
			
			if (depth > maxDepth) maxDepth = depth ;			
			
			if(bytesInChunk > chunkSize) {
				pd.update(bytesRead) ;
				bytesInChunk = 0 ;
			}
		}
		
		this.maxPageDepth = maxDepth ;
		
		input.close();
	}
	
	public boolean arePagesCached() {
		return !(cachedPages == null) ;
	}
	
	public boolean areAnchorsCached(TextProcessor tp) {
		
		if (cachedAnchors == null)
			return false ;
		
		String nameA = "null" ;
		if (cachedProcessor != null) nameA = cachedProcessor.getName() ;
		
		String nameB = "null" ;
		if (tp != null) nameB = tp.getName() ;
		
		if (!nameA.equals(nameB))
			return false ;
		
		return true ;
	}
	
	public boolean areOutLinksCached() {
		return !(cachedOutLinks == null) ;
	}
	
	public boolean areInLinksCached() {
		return !(cachedInLinks == null) ;
	}
	
	public boolean isGeneralityCached() {
		return !(cachedGenerality == null) ;
	}
	
	protected class CachedPage {
		String title ;
		int type ;
		
		public CachedPage(String title, int type) {
			this.title = title ;
			this.type = type ;
		}		
	}
	
	protected class CachedAnchor {
		int linkCount ;
		int occCount ;
		Integer[][] senses ;
		
		public CachedAnchor(int linkCount, int occCount) {
			this.linkCount = linkCount ;
			this.occCount = occCount ;
		}
		
		public CachedAnchor(Integer[][] senses) {
			this.occCount = -1 ;  //flag this as unavailable
			this.linkCount = 0 ;
			
			this.senses = senses ;
			
			for (Integer[] sense:senses) 
				linkCount = linkCount + sense[1] ;
		}
		
		public void addSenses(Integer[][] senses) {
			if (this.senses == null) {
				this.senses = senses ;
				return ;
			}
						
			// merge the senses
			HashMap<Integer,Integer> senseCounts = new HashMap<Integer,Integer>() ;
			
			for (Integer[] sense: this.senses) {
				senseCounts.put(sense[0], sense[1]) ;			
			}
			
			for (Integer[] sense: senses) {
				Integer count = senseCounts.get(sense[0]) ;
				
				if (count == null)
					count = sense[1] ;
				else
					count = count + sense[1] ;
				
				senseCounts.put(sense[0], count) ;	
			}
			
			// sort the merged senses
			TreeSet<Sense> orderedSenses = new TreeSet<Sense>() ;
			for (Integer senseId: senseCounts.keySet()) {
				Integer count = senseCounts.get(senseId) ;
				orderedSenses.add(new Sense(senseId, count)) ;
			}
			
			// store 
			this.senses = new Integer[orderedSenses.size()][2] ;
			
			int index = 0 ;
			for (Sense sense: orderedSenses) {
				Integer[] s = {sense.id, sense.count} ;
				this.senses[index] = s ;			
				index++ ;
			}
		}
		
		private class Sense implements Comparable {
			Integer id ;
			Integer count ;
			
			public Sense(Integer id, Integer count) {
				this.id = id ;
				this.count = count ;
			}
			
			public int compareTo(Object o) {
				Sense s = (Sense) o ;
				int cmp = s.count.compareTo(count) ;
				if (cmp == 0) 
					cmp = id.compareTo(s.id) ;
				return cmp ;
			}
		}
	}
	
	
	public static void main(String[] args) {
		try {
			Wikipedia wikipedia = Wikipedia.getInstanceFromArguments(args) ;
			
			File dataDirectory = new File("/research/wikipedia/data/en/20071120/") ;
			wikipedia.getDatabase().loadData(dataDirectory, false) ;
			
			/*
			long memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() ;
			
			wikipedia.getDatabase().cacheInLinks(new File("/research/wikipedia/data/en/20071120")) ;
			//wikipedia.getDatabase().cacheOutLinks(new File("/research/wikipedia/data/en/20071120")) ;
			
			long memEnd = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() ;
			
			System.out.println((memEnd-memStart) + " memory used") ;
			*/
			//self.prepareForMorphologicalProcessor(new Cleaner()) ;
			//self.prepareForMorphologicalProcessor(new SimpleStemmer()) ;
			
		} catch (Exception e) {
			e.printStackTrace() ;
		}
	}
	
}
