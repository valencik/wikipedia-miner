package org.wikipedia.miner.db;

import gnu.trove.*;

import java.io.* ;
import java.text.DecimalFormat;
import java.util.*;


import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.*;


import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.util.ProgressNotifier;
import org.wikipedia.miner.util.text.* ;

import com.sleepycat.bind.tuple.*;
//import com.sleepycat.collections.StoredIterator;
//import com.sleepycat.collections.StoredKeySet;
//import com.sleepycat.collections.StoredMap;
import com.sleepycat.je.*;

public class WikipediaEnvironment extends Environment {

	public enum DatabaseName  
	{
		STATS, PAGE_DETAILS, PAGE_CONTENT, LINKS_IN, LINKS_OUT, REDIRECTS_IN, REDIRECTS_OUT, PARENTS,
		CHILD_ARTICLES, CHILD_CATEGORIES, DEPTHS, LINK_COUNTS, TRANSLATIONS, ANCHOR, ANCHOR_TEXTS, PAGE_STRUCTURE ;
	}

	public enum Statistic {
		PAGE_COUNT, ARTICLE_COUNT, CATEGORY_COUNT, REDIRECT_COUNT, DISAMBIG_COUNT, MAX_DEPTH, ROOT_ID
	}

	protected File databaseDir ;
	protected File indexDir ;


	protected FSDirectory index ;
	protected StandardAnalyzer analyzer = new StandardAnalyzer();
	private QueryParser queryParser ;
	private Searcher searcher ;


	//private HashMap<DatabaseName, StoredMap> openStoredMaps ;
	protected HashMap<String, Database> openDatabases ;
	protected HashMap<String, Database> tempDatabases ;

	//private HashMap<String, StoredMap<String, DbAnchor>> anchorMaps ;
	//private HashMap<String, Database> anchorDatabases ;


	private TIntObjectHashMap<DbPage> cachedPages ;
	private TIntObjectHashMap<int[]>cachedInLinks ;
	//private THashMap<String,DbAnchor> cachedAnchors ;
	private TextProcessor cachedProcessor ;

	protected DatabaseConfig normalConfig ;
	protected DatabaseConfig writingConfig ;
	protected DatabaseConfig tempConfig ;

	protected DbPageBinding pageDetailsBinding = new DbPageBinding() ;
	protected IntegerBinding intBinding = new IntegerBinding() ;
	protected StringBinding strBinding = new StringBinding() ;
	protected IntArrayBinding intArrayBinding = new IntArrayBinding() ;
	protected StringArrayBinding strArrayBinding = new StringArrayBinding() ;
	protected DbLinkArrayBinding linkArrayBinding = new DbLinkArrayBinding() ;
	protected DbAnchorBinding anchorBinding = new DbAnchorBinding() ;
	protected DbAnchorTextArrayBinding anchorTextArrayBinding = new DbAnchorTextArrayBinding() ;
	protected DbStructureNodeBinding structureBinding = new DbStructureNodeBinding() ;
	protected StatisticBinding statBinding = new StatisticBinding() ;

	public WikipediaEnvironment(File databaseDir, File indexDir, EnvironmentConfig config) throws EnvironmentLockedException, DatabaseException, IOException {

		super(databaseDir, config) ;

		this.databaseDir = databaseDir ;
		this.indexDir = indexDir ;

		if (this.indexDir != null) {
			index = FSDirectory.getDirectory(indexDir) ;

			String[] fields = {"title", "content"} ;

			queryParser = new MultiFieldQueryParser(fields, analyzer);
			searcher = new IndexSearcher(index);
		}

		normalConfig = new DatabaseConfig() ;
		normalConfig.setReadOnly(true) ;
		normalConfig.setTransactional(false) ;

		writingConfig = new DatabaseConfig() ;
		writingConfig.setDeferredWrite(true) ;
		writingConfig.setAllowCreate(true) ;
		writingConfig.setExclusiveCreate(true) ;
		writingConfig.setTransactional(false) ;

		tempConfig = new DatabaseConfig() ;
		tempConfig.setTemporary(true) ;
		tempConfig.setAllowCreate(true) ;
		tempConfig.setTransactional(false) ;

		openDatabases = new HashMap<String, Database>() ;
		tempDatabases = new HashMap<String, Database>() ; 

		for (DatabaseName dbName:DatabaseName.values()) {
			try {
				getDatabase(dbName, false, false) ;
			} catch (DatabaseException e) {
				if (!config.getAllowCreate()) {
					throw e ;
				}
			}
		}
		
	}

	protected Database getDatabase(DatabaseName dbName, boolean writable, boolean clear) throws DatabaseException {
		return getDatabase(dbName.toString(), writable, clear) ;
	}

	protected Database getDatabase(String dbName, boolean writable, boolean clear) throws DatabaseException {

		Database db = openDatabases.get(dbName) ;

		if (writable) {

			if (db != null) 
				db.close();

			if (clear) {
				try {
					removeDatabase(null, dbName.toString()) ;
				} catch (Exception e) {
					System.out.println(e.getMessage()) ;
				} ;
			}

			if (clear)
				this.getConfig().setAllowCreate(true) ;
			else
				writingConfig.setExclusiveCreate(false) ;

			db = openDatabase(null, dbName.toString(), writingConfig);

			this.getConfig().setAllowCreate(false) ;
			writingConfig.setExclusiveCreate(true) ;

		} else {

			if (db == null) {
				db =  openDatabase(null, dbName.toString(), normalConfig);
			} else {
				if (!db.getConfig().getReadOnly()) {
					db.close() ;
					db =  openDatabase(null, dbName.toString(), normalConfig);
				}	
			}
		}

		openDatabases.put(dbName.toString(), db) ;
		return db ;
	}




	public void close() throws DatabaseException {

		System.out.println("Closing") ;
		super.sync() ;

		//boolean anyCleaned = false;
		//while (cleanLog() > 0) {
		//	System.out.println("cleaning") ;
		//	anyCleaned = true;
			
		//}

		//if (anyCleaned) {
			CheckpointConfig force = new CheckpointConfig();
			force.setForce(true);
			checkpoint(force);
		//}


		if (index != null) {
			try {
				searcher.close() ;
			} catch (Exception e) {} ;

			index.close() ;
		}

		for (Database db:openDatabases.values()) {
			try { 
				db.close() ;
			} catch (Exception e) {} ;	
		}

		for (Database db:tempDatabases.values()) {
			try { 
				db.close() ;
			} catch (Exception e) {} ;	
		}

		super.close();
	}


	public Article[] doFullTextSearch(String query, int limit) throws IOException, ParseException{

		TopDocs docs = searcher.search(queryParser.parse(query), null, limit);

		Article[] results = new Article[docs.scoreDocs.length] ;
		int i = 0 ;
		for ( ScoreDoc sd:docs.scoreDocs) {
			Document d = searcher.doc(sd.doc);

			Integer id = Integer.parseInt(d.get("id")) ;
			Article art = new Article(this, id) ;
			art.setWeight(sd.score) ;

			results[i++] = art ;
		}

		return results ;
	}

	/**
	 * Checks if the database has been prepared for use with a particular text processor
	 * 
	 * @param TextProcessor the TextProcessor to be checked.
	 * @return true if this environment is ready to be searched with the given text processor, otherwise false.
	 */
	public boolean isPreparedForTextProcessor(TextProcessor tp) {

		try {
			getDatabase(DatabaseName.ANCHOR + "-" + tp.getName(), false, false) ;

		} catch (DatabaseException e){
			return false ;
		}

		return true ;
	}

	/**
	 * Prepares the database so that it can be efficiently searched with the given text processor 
	 *
	 * @param tp the text processor to prepare this database for
	 * @throws DatabaseException if there is a problem with the Wikipedia database
	 */
	public void prepareForTextProcessor(TextProcessor tp) throws DatabaseException{

		System.out.println("Preparing anchors for " + tp.getName()) ;
		String dbName =DatabaseName.ANCHOR + "-" + tp.getName() ;

		//make sure database is available and empty
		System.out.print("Deleting old anchors... ") ;
		try {

			this.removeDatabase(null, dbName) ;

		} catch (DatabaseException e) {
			//no database to delete
		}

		cleanLog() ;
		checkpoint(null) ;
		sync() ;

		System.out.println(" ...done.") ;

		Database dbProcessedAnchors = this.openDatabase(null, dbName, writingConfig) ;
		openDatabases.put(dbName, dbProcessedAnchors) ;









		Database dbOrigAnchors = openDatabases.get(DatabaseName.ANCHOR) ;
		//dbOrigAnchors.getConfig() ;


		int passes = 8 ;
		ProgressNotifier pn = new ProgressNotifier(2*passes) ;

		for (int pass=0 ; pass<passes ; pass++) {

			pn.startTask(dbOrigAnchors.count(), "Gathering and processing anchors (pass " + (pass+1) + " of " + passes + ")") ;

			THashMap<String,DbAnchor> tmpProcessedAnchors = new THashMap<String, DbAnchor>() ;

			DatabaseEntry key = new DatabaseEntry() ;
			DatabaseEntry value = new DatabaseEntry() ;
			Cursor c = dbOrigAnchors.openCursor(null, null) ;
			//int count = 0 ;
			while (c.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {

				//if (count++ > 1000) break ;

				String origText = strBinding.entryToObject(key) ;
				String processedText = tp.processText(origText) ;



				if (Math.abs(processedText.hashCode()) % passes == pass) {

					//System.out.println("\"" + origText + "\",\"" + processedText + "\"") ;

					DbAnchor anchorToStore = anchorBinding.entryToObject(value) ;

					DbAnchor storedAnchor = tmpProcessedAnchors.get(processedText) ; 

					if (storedAnchor == null) {
						tmpProcessedAnchors.put(processedText, anchorToStore) ;
					} else {
						//need to merge anchors
						storedAnchor.mergeWith(anchorToStore) ;
						tmpProcessedAnchors.put(processedText, storedAnchor) ;
					}
				}
				pn.update() ;
			}

			System.out.println(tmpProcessedAnchors.size() + " anchors to store") ;

			//Save gathered anchors
			pn.startTask(tmpProcessedAnchors.size(), "Storing processed anchors (pass " + (pass+1) + " of " + passes + ")") ;

			//final StoredMap<String, DbAnchor> sm = new StoredMap<String, DbAnchor>(db, strBinding, anchorBinding, true) ;
			final ProgressNotifier pn2 = pn ;
			final Database db = dbProcessedAnchors ;

			//final DatabaseEntry k = new DatabaseEntry() ;
			//final DatabaseEntry v = new DatabaseEntry() ;

			tmpProcessedAnchors.forEachEntry(new TObjectObjectProcedure<String, DbAnchor>() {
				public boolean execute(String text, DbAnchor anchor) {


					DatabaseEntry k = new DatabaseEntry() ;
					strBinding.objectToEntry(text, k) ;

					DatabaseEntry v = new DatabaseEntry() ;
					anchorBinding.objectToEntry(anchor, v) ;



					try {
						//System.out.println(text + db.count()) ;

						db.put(null, k, v) ;
					} catch (DatabaseException e) { System.out.println("huh?") ; } ;

					pn2.update() ;
					return true ;
				}
			}) ;

			tmpProcessedAnchors.clear() ;
			System.gc() ;

			dbProcessedAnchors.sync() ;
			cleanLog() ;
			checkpoint(null) ;
			evictMemory() ;
		}



		System.out.print("Syncing database... ") ;
		dbProcessedAnchors.close();
		dbProcessedAnchors = this.openDatabase(null, dbName, normalConfig) ;
		openDatabases.put(dbName, dbProcessedAnchors) ;

		//StoredMap<String, DbAnchor> smAnchorSenses = new StoredMap<String, DbAnchor>(dbProcessedAnchors, strBinding, anchorBinding, false) ;
		//anchorMaps.put(tp.getName(), smAnchorSenses) ;
		System.out.println("...done.") ;
	}




	public DbPage getPageDetails(int id) throws DatabaseException {

		if (arePagesCached()) 
			return cachedPages.get(id) ;

		DatabaseEntry k = new DatabaseEntry() ;
		this.intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.PAGE_DETAILS, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return pageDetailsBinding.entryToObject(v) ;
		else
			return null ;

	}

	public String getPageContent(int id) throws DatabaseException {

		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.PAGE_CONTENT, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return strBinding.entryToObject(v) ;
		else
			return null ;
	}

	public DbStructureNode getPageStructure(int id) throws DatabaseException {

		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.PAGE_STRUCTURE, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return structureBinding.entryToObject(v) ;
		else
			return null ;
	}

	public Integer getRedirectTarget(int id) throws DatabaseException {
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.REDIRECTS_OUT, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return intBinding.entryToObject(v) ;
		else
			return null ;
	}

	public int[] getRedirects(int id) throws DatabaseException {
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.REDIRECTS_IN, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return intArrayBinding.entryToObject(v) ;
		else
			return null ;
	}

	public int[] getParents(int id) throws DatabaseException {
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.PARENTS, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return intArrayBinding.entryToObject(v) ;
		else
			return null ;
	}

	public int[] getChildCategories(int id)  throws DatabaseException {
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.CHILD_CATEGORIES, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return intArrayBinding.entryToObject(v) ;
		else
			return null ;
	}

	public int[] getChildArticles(int id) throws DatabaseException {
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.CHILD_ARTICLES, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return intArrayBinding.entryToObject(v) ;
		else
			return null ;
	}

	public DbAnchor getAnchor(String text) throws DatabaseException {

		Database db ;

		if (this.areAnchorsCached(null))
			db = tempDatabases.get(DatabaseName.ANCHOR.toString()) ;
		else
			db = getDatabase(DatabaseName.ANCHOR, false, false) ;

		DatabaseEntry k = new DatabaseEntry() ;
		strBinding.objectToEntry(text, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = db.get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return anchorBinding.entryToObject(v) ;
		else
			return null ;
	}


	public DbAnchor getAnchor(String text, TextProcessor tp) throws DatabaseException {

		if (tp == null) 
			return getAnchor(text) ;

		if (!isPreparedForTextProcessor(tp)) 
			throw new DatabaseException("WikipediaEnvironment is not prepared for " + tp.getName()) ;

		String dbName = DatabaseName.ANCHOR + "-" + tp.getName() ;
		Database db ;
		if (this.areAnchorsCached(tp))
			db = tempDatabases.get(dbName) ;
		else
			db = getDatabase(dbName, false, false) ;

		DatabaseEntry k = new DatabaseEntry() ;
		strBinding.objectToEntry(tp.processText(text), k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = db.get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return anchorBinding.entryToObject(v) ;
		else
			return null ;
	}

	public DbAnchorText[] getAnchorTexts(int id) throws DatabaseException {
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.ANCHOR_TEXTS, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return anchorTextArrayBinding.entryToObject(v) ;
		else
			return null ;
	}

	public Integer getDepth(int id) throws DatabaseException {
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.DEPTHS, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return intBinding.entryToObject(v) ;
		else
			return null ;
	}

	public DbLink[] getLinksOut(int id) throws DatabaseException {
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.LINKS_OUT, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return linkArrayBinding.entryToObject(v) ;
		else
			return null ;
	}

	public DbLink[] getLinksIn(int id) throws DatabaseException {
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.LINKS_IN, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return linkArrayBinding.entryToObject(v) ;
		else
			return null ;
	}

	public int[] getLinkIdsIn(int id) throws DatabaseException {

		if (areInLinksCached()) 
			return cachedInLinks.get(id) ;

		DbLink[] dbLinks = getLinksIn(id) ;

		if (dbLinks == null)
			return null ;

		int[] linkIds = new int[dbLinks.length] ;

		for (int i=0 ; i<dbLinks.length ; i++)
			linkIds[i] = dbLinks[i].id ;

		return linkIds ;
	}

	public int[] getLinkCounts(int id) throws DatabaseException{
		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.LINK_COUNTS, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return intArrayBinding.entryToObject(v) ;
		else
			return null ;
	}

	public HashMap<String,String> getTranslations(int id) throws DatabaseException {

		DatabaseEntry k = new DatabaseEntry() ;
		intBinding.objectToEntry(id, k) ;

		DatabaseEntry v = new DatabaseEntry() ;
		String[] t  ;

		OperationStatus s = getDatabase(DatabaseName.TRANSLATIONS, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			t =  strArrayBinding.entryToObject(v) ;
		else
			t = null ;

		HashMap<String, String> translations = new HashMap<String,String>() ;

		if (t != null) {	
			for (int i=0 ; i<t.length ; i+=2) 
				translations.put(t[i], t[i+1]) ;
		}

		return translations ;
	}

	public Integer getStatisticValue(Statistic stat) throws DatabaseException {

		DatabaseEntry k = new DatabaseEntry() ;
		statBinding.objectToEntry(stat, k) ;

		DatabaseEntry v = new DatabaseEntry() ;

		OperationStatus s = getDatabase(DatabaseName.STATS, false, false).get(null, k, v, LockMode.READ_COMMITTED) ; 
		if (s.equals(OperationStatus.SUCCESS)) 
			return intBinding.entryToObject(v) ;
		else
			return null ;
	}

	/**
	 * Identifies the set of valid article ids which fit the given constrains. Useful for specifying a subset of 
	 * articles that we are interested in caching.
	 * 
	 * @param minLinkCount the minimum number of links that an article must have (both in and out)
	 * @param pn an optional progress notifier
	 * @return the set of valid ids which fit the given constrains. 
	 */
	public TIntHashSet getValidPageIds(int minLinkCount, ProgressNotifier pn) throws DatabaseException {

		TIntHashSet pageIds = new TIntHashSet() ;

		if (pn == null) pn = new ProgressNotifier(1) ;

		Database dbLinkCounts = getDatabase(DatabaseName.LINK_COUNTS, false, false) ;
		pn.startTask(dbLinkCounts.count(), "gathering valid page ids") ;

		Cursor c = dbLinkCounts.openCursor(null, null) ;
		c.setCacheMode(CacheMode.UNCHANGED) ;

		DatabaseEntry key = new DatabaseEntry() ;
		DatabaseEntry value = new DatabaseEntry() ;

		while (c.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
			Integer id = intBinding.entryToObject(key) ;
			int[] linkCounts = intArrayBinding.entryToObject(value) ;

			pn.update();

			if (linkCounts[1] > minLinkCount && linkCounts[3] >= minLinkCount)
				pageIds.add(id) ;
		}

		c.close() ;
		return pageIds ;
	}

	/**
	 * Caches pages, so that titles and types can be retrieved 
	 * very quickly without consulting the database.
	 * 
	 * @param validIds an optional set of ids. Only anchors that point to these ids, and only destinations within this list will be cached. 
	 * @param pn an optional progress notifier
	 * @throws DatabaseException if there is a problem with the underlying data.
	 */
	public void cachePages(TIntHashSet validIds, ProgressNotifier pn) throws DatabaseException {

		Database dbPage = getDatabase(DatabaseName.PAGE_DETAILS, false, false) ;

		if (pn == null) pn = new ProgressNotifier(1) ;
		pn.startTask(dbPage.count(), "caching pages") ;

		//if (validIds == null)
		//	cachedPages = new TIntObjectHashMap<DbPage>((int)dbPage.count(), 1) ;
		//else
		//	cachedPages = new TIntObjectHashMap<DbPage>(validIds.size(), 1) ;

		Cursor c = dbPage.openCursor(null, null) ;
		//c.setCacheMode(CacheMode.UNCHANGED) ;

		DatabaseEntry key = new DatabaseEntry() ;
		DatabaseEntry value = new DatabaseEntry() ;

		while (c.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
			Integer id = intBinding.entryToObject(key) ;
			DbPage page = pageDetailsBinding.entryToObject(value) ;

			if (validIds == null || validIds.contains(id) || page.getType() == Page.CATEGORY || page.getType()==Page.REDIRECT) {

				//this is a page we think is likely to get used a lot, so do this sneaky trick to ensure it is cached.
				c.setCacheMode(CacheMode.KEEP_HOT) ;
				c.getCurrent(key, value, LockMode.DEFAULT) ;
				c.setCacheMode(CacheMode.DEFAULT) ;
			}
			cachedPages.put(id, page) ;

			pn.update() ;
		}

		c.close() ;
	}

	/**
	 * Caches links in to pages, so these and relatedness measures can be calculated very quickly,
	 * without consulting the database.
	 * 
	 * @param dir	the directory containing csv files extracted from a Wikipedia dump.
	 * @param validIds an optional set of ids. Only anchors that point to these ids, and only destinations within this list will be cached. 
	 * @param pn an optional progress notifier
	 * @throws IOException if the relevant files cannot be read.
	 */
	public void cacheInLinks(TIntHashSet validIds, ProgressNotifier pn) throws DatabaseException {

		Database dbLinks = getDatabase(DatabaseName.LINKS_IN, false, false) ;

		if (pn == null) pn = new ProgressNotifier(1) ;
		pn.startTask(dbLinks.count(), "caching links into pages") ;

		//if (validIds == null)
		//	cachedInLinks = new TIntObjectHashMap<int[]>((int)dbLinks.count(), 1) ;
		//	else
		//		cachedInLinks = new TIntObjectHashMap<int[]>(validIds.size(), 1) ;

		Cursor c = dbLinks.openCursor(null, null) ;
		c.setCacheMode(CacheMode.UNCHANGED) ;

		DatabaseEntry key = new DatabaseEntry() ;
		DatabaseEntry value = new DatabaseEntry() ;

		while (c.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
			Integer id = intBinding.entryToObject(key) ;

			if (validIds == null || validIds.contains(id)) {

				c.setCacheMode(CacheMode.KEEP_HOT) ;
				c.getCurrent(key, value, LockMode.DEFAULT) ;
				c.setCacheMode(CacheMode.DEFAULT) ;

				/*
						DbLink[] links = linkArrayBinding.entryToObject(value) ;

						TIntArrayList linkIds = new TIntArrayList() ;

						for (DbLink l:links) {
							if (validIds == null || validIds.contains(l.id)) 
								linkIds.add(l.id) ;
						}

						cachedInLinks.put(id, linkIds.toNativeArray()) ;
				 */
			}

			pn.update() ;
		}

		c.close() ;
	}


	/**
	 * Caches anchors, destinations, and occurrence counts (if these have been summarized), so that they can 
	 * be searched very quickly without consulting the database.
	 * 
	 * @param dir	the directory containing csv files extracted from a Wikipedia dump.
	 * @param tp	an optional text processor
	 * @param validIds an optional set of ids. Only anchors that point to these ids, and only destinations within this list will be cached.
	 * @param minLinkCount the minimum number of times a destination must occur for a particular anchor before it is cached. 
	 * @param pn an optional progress notifier
	 * @throws IOException if the relevant files cannot be read.
	 */
	public void cacheAnchors(TextProcessor tp, TIntHashSet validIds, Integer minLinkCount, Float minLinkProbability, Float minSenseProbability, ProgressNotifier pn) throws DatabaseException{

		if (tp != null && !isPreparedForTextProcessor(tp)) 
			throw new DatabaseException("WikipediaEnvironment is not prepared for " + tp.getName()) ;

		String dbName = DatabaseName.ANCHOR.toString();
		if (tp != null) 
			dbName = dbName + "-" + tp.getName() ;

		Database dbOrig = getDatabase(dbName, false, false) ;

		Database dbTemp = this.openDatabase(null, "tmp" + dbName, tempConfig) ;
		tempDatabases.put(dbName, dbTemp) ;

		if (pn == null) pn = new ProgressNotifier(1) ;
		pn.startTask(dbOrig.count(), "caching anchors") ;

		Cursor c = dbOrig.openCursor(null, null) ;
		c.setCacheMode(CacheMode.UNCHANGED) ;

		DatabaseEntry key = new DatabaseEntry() ;
		DatabaseEntry value = new DatabaseEntry() ;

		int count = 0 ;
		int totalAnchors = 0 ;
		int cachedAnchors = 0 ;

		long totalSenses = 0 ;
		long cachedSenses = 0 ;

		DecimalFormat df = new DecimalFormat("0%") ;

		while (c.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {

			pn.update();	



			String text = strBinding.entryToObject(key) ;
			DbAnchor anchor = anchorBinding.entryToObject(value) ;

			totalAnchors++ ;
			totalSenses = totalSenses + anchor.getSenses().length ;



			if (count++ == 100000)  {

				
				//System.out.println(" - anchors: " + cachedAnchors + " out of " + totalAnchors + ", " + df.format((float)cachedAnchors/totalAnchors)) ;
				//System.out.println(" - senses: " + cachedSenses + " out of " + totalSenses + ", " + df.format((float)cachedSenses/totalSenses)) ;

				count = 0 ;
			}



			if (minLinkCount != null && anchor.getTotalLinks() < minLinkCount) 
				continue ;



			float linkProb ;
			if (anchor.getDistinctReferences() == 0)
				linkProb = 0 ;
			else
				linkProb = (float)anchor.getDistinctLinks() / anchor.getDistinctReferences() ;

			if (minLinkProbability != null && linkProb < minLinkProbability)
				continue ;

			//System.out.println(text) ;

			Vector<DbSense> trimmedSenses = new Vector<DbSense>() ;
			int trimmedTotalLinks = 0 ;
			int trimmedDistinctLinks = 0 ;

			for (DbSense sense:anchor.getSenses()) {
				if (validIds == null || validIds.contains(sense.getDestination())) {

					if (minLinkCount != null && sense.getTotalCount() < minLinkCount) 
						continue ;

					float senseProb ;
					if (anchor.getTotalLinks() == 0)
						senseProb = 0 ;
					else
						senseProb = (float)sense.getTotalCount()/anchor.getTotalLinks() ;

					//System.out.println(" - " + sense.getTotalCount() + ", " + anchor.getTotalLinks() + ", " + senseProb ) ; 

					if (minSenseProbability != null && senseProb < minSenseProbability)
						break ;

					trimmedSenses.add(sense) ;
					cachedSenses ++ ;

					trimmedTotalLinks += sense.getTotalCount() ;
					trimmedDistinctLinks += sense.getDistinctCount() ;
				}
			}

			if (trimmedSenses.size() > 0) {
				DbSense[] ts = trimmedSenses.toArray(new DbSense[trimmedSenses.size()]) ;
				DbAnchor a = new DbAnchor(trimmedDistinctLinks, trimmedDistinctLinks, anchor.getTotalReferences(), anchor.getDistinctReferences(), ts) ;

				DatabaseEntry data = new DatabaseEntry() ;
				anchorBinding.objectToEntry(a, data) ;

				dbTemp.put(null, key, data) ;
				cachedAnchors++ ;
			}

		}

		c.close() ;

		this.cachedProcessor = tp ;
	}

	public boolean arePagesCached() {
		return cachedPages != null ;
	}

	public boolean areInLinksCached() {
		return cachedInLinks != null ;
	}

	public void printStats() throws DatabaseException {
		System.err.println("\n\n=========================================\n\n") ;

		
		StatsConfig config = new StatsConfig() ;
		config.setClear(true) ;

		System.err.println(getStats(config)) ;
		
		System.err.println("\n\n=========================================\n\n") ;

	}
	
	public boolean preloadDatabase(DatabaseName dbName) throws DatabaseException{

		Database dbInLinks = getDatabase(dbName, false, false) ;
		PreloadStats s = dbInLinks.preload(new PreloadConfig()) ;

		return s.getStatus() == PreloadStatus.SUCCESS ;
	}

	public boolean areAnchorsCached(TextProcessor tp) {

		String dbName = DatabaseName.ANCHOR.toString() ;
		if (tp != null)
			dbName = dbName + "-" + tp.getName() ;

		return (tempDatabases.containsKey(dbName)) ;
	}

	public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.out.println("Please specify two directories, one for the berkeley database, and one for the lucene index") ;
		}

		File berkeleyDir = new File(args[0]) ;
		File luceneDir = new File(args[1]) ;

		WikipediaEnvironment we = new WikipediaEnvironment(berkeleyDir, luceneDir, getEnvironmentConfig(false)) ;

		try {

			//System.out.println(we.getStats(null)) ;

			long memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

			TextProcessor tp = new CaseFolder() ;

			TIntHashSet ids = we.getValidPageIds(5, null) ;
			we.printStats() ;
			//wikipedia.getDatabase().cacheParentIds(dataDirectory, pn) ;
			//wikipedia.getDatabase().cacheGenerality(dataDirectory, ids, null) ;
			//wikipedia.getEnvironment().cachePages(ids, pn) ;
			//wikipedia.getDatabase().cacheAnchors(dataDirectory, tp, ids, 3, pn) ;
			we.cacheInLinks(ids, null) ;
			we.printStats() ;
			
			we.cacheAnchors(tp, ids, 3, (float)0.02, (float)0.03, null) ;
			we.printStats() ;

			ids.clear() ;
			
			long memEnd = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

			System.out.println( (memEnd-memStart) + " bytes") ;

			System.out.println("are anchors cached: " + we.areAnchorsCached(tp)) ;

			DbAnchor anch = we.getAnchor("TED", tp) ;
			System.out.println(anch.getTotalLinks() + ", " + anch.getTotalReferences());

			for (DbSense s:anch.getSenses()) {

				float senseProb = (float)s.getTotalCount()/anch.getTotalLinks() ;

				//DbPage p = we.getPageDetails(s.getDestination()) ;

				Page p = Page.createPage(we, s.getDestination()) ;

				System.out.println(" - " + p.getTitle() + ", " + senseProb + ", " + s.getTotalCount()) ;



			}
			
			StatsConfig config = new StatsConfig() ;
			config.setClear(true) ;

			

			//System.out.println(we.getStats(null)) ;

		} catch (Exception e) {

			throw e ;

		} finally {

			System.out.println("Blah") ;

			we.close();
		}

	}
	
	
	public static EnvironmentConfig getEnvironmentConfig(boolean loading) {
		
		EnvironmentConfig config = new EnvironmentConfig() ;
		
		//config.setReadOnly(readonly) ;
		config.setAllowCreate(loading) ;
		
		config.setTransactional(false) ;
		config.setLocking(false) ;
		
		if (loading)
			config.setCachePercent(30) ;
		else
			config.setCachePercent(80) ;
		
		
		config.setConfigParam(EnvironmentConfig.LOG_FAULT_READ_SIZE, String.valueOf(1024*32)) ;
		//config.setConfigParam(EnvironmentConfig.ENV_RUN_CHECKPOINTER, "false") ;
		//config.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false") ;
		
		return config ;
	}


}
