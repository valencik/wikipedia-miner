package org.wikipedia.miner.db;

import gnu.trove.*;

import java.io.* ;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
import com.sleepycat.collections.StoredIterator;
import com.sleepycat.collections.StoredKeySet;
import com.sleepycat.collections.StoredMap;
import com.sleepycat.je.*;

@SuppressWarnings("unchecked")
public class WikipediaEnvironment extends Environment {

	protected enum DatabaseName  
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
	

	private HashMap<DatabaseName, StoredMap> openStoredMaps ;
	private HashMap<DatabaseName, Database> openDatabases ;

	private HashMap<String, StoredMap<String, DbAnchor>> anchorMaps ;
	private HashMap<String, Database> anchorDatabases ;
	
	
	private TIntObjectHashMap<DbPage> cachedPages ;
	private TIntObjectHashMap<int[]>cachedInLinks ;
	
	private DatabaseConfig normalConfig ;
	private DatabaseConfig writingConfig ;

	private DbPageBinding pageDetailsBinding = new DbPageBinding() ;
	private IntegerBinding intBinding = new IntegerBinding() ;
	private StringBinding strBinding = new StringBinding() ;
	private IntArrayBinding intArrayBinding = new IntArrayBinding() ;
	private StringArrayBinding strArrayBinding = new StringArrayBinding() ;
	private DbLinkArrayBinding linkArrayBinding = new DbLinkArrayBinding() ;
	private DbAnchorBinding anchorBinding = new DbAnchorBinding() ;
	private DbAnchorTextArrayBinding anchorTextArrayBinding = new DbAnchorTextArrayBinding() ;
	private DbStructureNodeBinding structureBinding = new DbStructureNodeBinding() ;
	private StatisticBinding statBinding = new StatisticBinding() ;
	
	public WikipediaEnvironment(File databaseDir, File indexDir, final boolean loading) throws EnvironmentLockedException, DatabaseException, IOException {

		super(databaseDir, new EnvironmentConfig() {

			public boolean getAllowCreate() {
				return loading ;
			}
			public boolean getTransactional() {
				return false ;
			}
			/*
			public int getCachePercent() {
				if (loading) {
					return 20 ;
				} else {
					return 50 ;
				}
			}*/
		}) ;

		EnvironmentConfig ec = this.getConfig() ;
		//ec.setConfigParam(EnvironmentConfig.ENV_RUN_CHECKPOINTER, "false") ;
		//ec.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false") ;
		
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

		writingConfig = new DatabaseConfig() ;
		writingConfig.setDeferredWrite(true) ;
		writingConfig.setAllowCreate(true) ;

		openDatabases = new HashMap<DatabaseName, Database>() ;
		openStoredMaps = new HashMap<DatabaseName, StoredMap>() ;

		anchorMaps = new HashMap<String, StoredMap<String, DbAnchor>>() ;
		anchorDatabases = new HashMap<String, Database>() ;


		for (DatabaseName dbName:DatabaseName.values()) {

			try {

				//if (dbName != DatabaseName.PAGE_CONTENT) 
					getStoredMap(dbName, false, false) ;
			} catch (DatabaseException e) {
				if (!loading) {
					throw e ;
				}
			}
		}
	}


	protected StoredMap getStoredMap(DatabaseName dbName, boolean writable, boolean clear) throws DatabaseException {

		Database db = openDatabases.get(dbName) ;

		if (writable) {
			//if loading, then completely remove existing database, and start a new one

			if (db != null) 
				db.close();

			if (clear) {
				try {
					removeDatabase(null, dbName.toString()) ;
				} catch (Exception e) {
					System.out.println(e.getMessage()) ;
				} ;
			}

			this.getConfig().setAllowCreate(true) ;
			db = openDatabase(null, dbName.toString(), writingConfig);
			this.getConfig().setAllowCreate(false) ;
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

		openDatabases.put(dbName, db) ;

		StoredMap sm = null ;
		if (!writable) {
			sm = openStoredMaps.get(dbName) ;
			if(sm != null && sm.isWriteAllowed() == writable)
				return sm ;
		}

		switch (dbName) {

		case STATS:
			sm = new StoredMap<Statistic,Integer>(db, statBinding, intBinding, writable) ;
			break ;
		case PAGE_DETAILS: 
			sm = new StoredMap<Integer,DbPage>(db, intBinding, pageDetailsBinding, writable) ;
			break ;
		case PAGE_STRUCTURE:
			sm = new StoredMap<Integer, DbStructureNode>(db, intBinding, structureBinding, writable) ;
			break ;
		case DEPTHS: 
			sm = new StoredMap<Integer,Integer>(db, intBinding, intBinding, writable) ;
			break ;
		case REDIRECTS_IN:
			sm = new StoredMap<Integer,int[]>(db, intBinding, intArrayBinding, writable) ;
			break ;
		case REDIRECTS_OUT:
			sm = new StoredMap<Integer,Integer>(db, intBinding, intBinding, writable) ;
			break ;
		case PARENTS: 
		case CHILD_CATEGORIES:
		case CHILD_ARTICLES:
			sm = new StoredMap<Integer,int[]>(db, intBinding, intArrayBinding, writable) ;
			break ;
		case PAGE_CONTENT:
			sm  = new StoredMap<Integer,String>(db, intBinding, strBinding, writable) ;
			break ;
		case LINKS_IN:
		case LINKS_OUT:
			sm  = new StoredMap<Integer,DbLink[]>(db, intBinding, linkArrayBinding, writable) ;
			break ;	
		case LINK_COUNTS:
			sm = new StoredMap<Integer, int[]>(db, intBinding, intArrayBinding, writable) ;
			break ;
		case ANCHOR:
			sm = new StoredMap<String, DbAnchor>(db, strBinding, anchorBinding, writable) ;
			break ;
		case ANCHOR_TEXTS:
			sm = new StoredMap<Integer, DbAnchorText[]>(db, intBinding, anchorTextArrayBinding, writable) ;
			break ;
		case TRANSLATIONS: 
			sm = new StoredMap<Integer,String[]>(db, intBinding, strArrayBinding, writable) ;
			break ;
		}

		openStoredMaps.put(dbName, sm) ;

		return sm ;
	}




	public void close() throws DatabaseException {

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

		for (Database db:anchorDatabases.values()) {
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

		StoredMap<String, DbAnchor> smAnchor = anchorMaps.get(tp.getName()) ; 
		if (smAnchor == null) {
			try {
				Database dbAnchor = this.openDatabase(null, DatabaseName.ANCHOR + "-" + tp.getName(), normalConfig) ;
				anchorDatabases.put(tp.getName(), dbAnchor) ;
				smAnchor = new StoredMap<String, DbAnchor>(dbAnchor, strBinding, anchorBinding, false) ;
				anchorMaps.put(tp.getName(), smAnchor) ;
			} catch (DatabaseException e){
				return false ;
			}
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
		Database db = anchorDatabases.get(tp.getName()) ;
		if (db != null) 
			this.removeDatabase(null, dbName) ;

		db = this.openDatabase(null, dbName, writingConfig) ;
		anchorDatabases.put(tp.getName(), db)	 ;



		StoredMap<String,DbAnchor> smAnchors = openStoredMaps.get(DatabaseName.ANCHOR) ;
		Database dbAnchors = openDatabases.get(DatabaseName.ANCHOR) ;
		StoredKeySet<String> setAnchors = new StoredKeySet<String>(dbAnchors, strBinding,false) ;

		int passes = 5 ;
		ProgressNotifier pn = new ProgressNotifier(2*passes) ;

		for (int pass=0 ; pass<passes ; pass++) {

			StoredIterator<String> iterAnchors = setAnchors.storedIterator(false) ;

			pn.startTask(dbAnchors.count(), "Gathering and processing anchors (pass " + (pass+1) + " of " + passes + ")") ;

			THashMap<String,DbAnchor> tmpProcessedAnchors = new THashMap<String, DbAnchor>() ;

			while (iterAnchors.hasNext()) {
				String origAnchor = iterAnchors.next() ;
				String processedAnchor = tp.processText(origAnchor) ;

				if (Math.abs(processedAnchor.hashCode()) % passes == pass) {

					DbAnchor anchorToStore = smAnchors.get(origAnchor) ;

					DbAnchor storedAnchor = tmpProcessedAnchors.get(processedAnchor) ;

					if (storedAnchor == null) {
						tmpProcessedAnchors.put(processedAnchor, anchorToStore) ;
					} else {
						//need to merge anchors
						storedAnchor.mergeWith(anchorToStore) ;
						tmpProcessedAnchors.put(processedAnchor, storedAnchor) ;
					}
				}
				pn.update() ;
			}

			//Save gathered anchors
			pn.startTask(tmpProcessedAnchors.size(), "Storing processed anchors (pass " + (pass+1) + " of " + passes + ")") ;

			final StoredMap<String, DbAnchor> sm = new StoredMap<String, DbAnchor>(db, strBinding, anchorBinding, true) ;
			final ProgressNotifier pn2 = pn ;

			tmpProcessedAnchors.forEachEntry(new TObjectObjectProcedure<String, DbAnchor>() {
				public boolean execute(String text, DbAnchor anchor) {
					sm.put(text, anchor) ;
					pn2.update() ;
					return true ;
				}
			}) ;
			
			tmpProcessedAnchors.clear() ;
			System.gc() ;

			cleanLog() ;
			checkpoint(null) ;
			evictMemory() ;
		}



		System.out.print("Syncing database... ") ;
		db.close();
		db = this.openDatabase(null, dbName, normalConfig) ;
		anchorDatabases.put(tp.getName(), db) ;

		StoredMap<String, DbAnchor> smAnchorSenses = new StoredMap<String, DbAnchor>(db, strBinding, anchorBinding, false) ;
		anchorMaps.put(tp.getName(), smAnchorSenses) ;
		System.out.println("...done.") ;
	}




	public DbPage getPageDetails(int id)  {
		
		if (arePagesCached()) 
			return cachedPages.get(id) ;
		
		StoredMap<Integer, DbPage> smPageDetails = openStoredMaps.get(DatabaseName.PAGE_DETAILS) ;
		return smPageDetails.get(id) ;
	}

	public String getPageContent(int id) {
		StoredMap<Integer,String> smPageContent = openStoredMaps.get(DatabaseName.PAGE_CONTENT) ;

		if (smPageContent == null)
			return null ;

		return smPageContent.get(id) ;
	}

	public DbStructureNode getPageStructure(int id) {
		StoredMap<Integer,DbStructureNode> smPageStructure = openStoredMaps.get(DatabaseName.PAGE_STRUCTURE) ;
		return smPageStructure.get(id) ;
	}

	public int getRedirectTarget(int id) {
		StoredMap<Integer,Integer> smRedirectsOut = openStoredMaps.get(DatabaseName.REDIRECTS_OUT) ;
		return smRedirectsOut.get(id) ;
	}

	public int[] getRedirects(int id) {
		StoredMap<Integer,int[]> smRedirectsIn = openStoredMaps.get(DatabaseName.REDIRECTS_IN) ;
		return smRedirectsIn.get(id) ;
	}

	public int[] getParents(int id) {
		StoredMap<Integer,int[]> smParents = openStoredMaps.get(DatabaseName.PARENTS) ;
		return smParents.get(id) ;
	}

	public int[] getChildCategories(int id) {
		StoredMap<Integer,int[]> smChildCategories = openStoredMaps.get(DatabaseName.CHILD_CATEGORIES) ;
		return smChildCategories.get(id) ;
	}

	public int[] getChildArticles(int id) {
		StoredMap<Integer,int[]> smChildArticles = openStoredMaps.get(DatabaseName.CHILD_ARTICLES) ;
		return smChildArticles.get(id) ;
	}

	public DbAnchor getAnchor(String text) {

		StoredMap<String, DbAnchor> smAnchor =  openStoredMaps.get(DatabaseName.ANCHOR) ;
		return smAnchor.get(text) ;
	}

	public DbAnchor getAnchor(String text, TextProcessor tp) throws DatabaseException {

		if (tp == null) 
			return getAnchor(text) ;

		if (!isPreparedForTextProcessor(tp)) 
			throw new DatabaseException("WikipediaEnvironment is not prepared for " + tp.getName()) ;

		StoredMap<String, DbAnchor>smAnchor = this.anchorMaps.get(tp.getName()) ;
		return smAnchor.get(tp.processText(text)) ;
	}

	public DbAnchorText[] getAnchorTexts(int id) {
		StoredMap<String, DbAnchorText[]> smAnchorTexts = openStoredMaps.get(DatabaseName.ANCHOR_TEXTS);
		return smAnchorTexts.get(id) ;
	}

	public Integer getDepth(int id) {
		StoredMap<Integer, Integer> smDepths = openStoredMaps.get(DatabaseName.DEPTHS) ;
		return smDepths.get(id) ;
	}

	public DbLink[] getLinksOut(int id)  {
		StoredMap<Integer, DbLink[]> smLinksOut = openStoredMaps.get(DatabaseName.LINKS_OUT) ;
		return smLinksOut.get(id) ;
	}

	public DbLink[] getLinksIn(int id) {
		StoredMap<Integer, DbLink[]> smLinksIn = openStoredMaps.get(DatabaseName.LINKS_IN) ;
		return smLinksIn.get(id) ;
	}
	
	public int[] getLinkIdsIn(int id) {
		
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

	public int[] getLinkCounts(int id) {
		StoredMap<Integer, int[]> smLinkCounts = openStoredMaps.get(DatabaseName.LINK_COUNTS) ;
		return smLinkCounts.get(id) ;
	}

	public HashMap<String,String> getTranslations(int id) {

		StoredMap<Integer, String[]> smTranslations =  openStoredMaps.get(DatabaseName.TRANSLATIONS) ;

		HashMap<String, String> translations = new HashMap<String,String>() ;

		String[] t = smTranslations.get(id) ;
		if (t != null) {	
			for (int i=0 ; i<t.length ; i+=2) 
				translations.put(t[i], t[i+1]) ;
		}

		return translations ;
	}

	public Integer getStatisticValue(Statistic stat)  {
		StoredMap<Statistic, Integer> smStats = openStoredMaps.get(DatabaseName.STATS) ; 
		return smStats.get(stat) ;
	}

	public Iterator<Integer> getPageIdIterator() {

		Database dbPageDetails = openDatabases.get(DatabaseName.PAGE_DETAILS) ;
		StoredKeySet<Integer> pageIds = new StoredKeySet<Integer>(dbPageDetails, intBinding,false) ;
		return pageIds.storedIterator(false) ;
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
				
		Database dbLinkCounts = this.openDatabases.get(DatabaseName.LINK_COUNTS) ;
		pn.startTask(dbLinkCounts.count(), "gathering valid page ids") ;
		
		Cursor c = dbLinkCounts.openCursor(null, null) ;
		
		DatabaseEntry key = new DatabaseEntry() ;
		DatabaseEntry value = new DatabaseEntry() ;
		
		while (c.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
	        Integer id = intBinding.entryToObject(key) ;
	        int[] linkCounts = intArrayBinding.entryToObject(value) ;
	        
	        	pn.update();
	
			if (linkCounts[1] > minLinkCount && linkCounts[3] >= minLinkCount)
				pageIds.add(id) ;
		}
		
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
		
		Database dbPage = this.openDatabases.get(DatabaseName.PAGE_DETAILS) ;
		
		if (pn == null) pn = new ProgressNotifier(1) ;
		pn.startTask(dbPage.count(), "caching pages") ;
		
		if (validIds == null)
			cachedPages = new TIntObjectHashMap<DbPage>((int)dbPage.count(), 1) ;
		else
			cachedPages = new TIntObjectHashMap<DbPage>(validIds.size(), 1) ;
		
		Cursor c = dbPage.openCursor(null, null) ;
		
		DatabaseEntry key = new DatabaseEntry() ;
		DatabaseEntry value = new DatabaseEntry() ;
		
		while (c.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
	       Integer id = intBinding.entryToObject(key) ;
	       DbPage page = pageDetailsBinding.entryToObject(value) ;
	       
	       if (validIds == null || validIds.contains(id) || page.getType() == Page.CATEGORY || page.getType()==Page.REDIRECT) 
				cachedPages.put(id, page) ;
		
			pn.update() ;
		}
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
		
		Database dbLinks = this.openDatabases.get(DatabaseName.LINKS_IN) ;
		
		if (pn == null) pn = new ProgressNotifier(1) ;
		pn.startTask(dbLinks.count(), "caching links into pages") ;
		
		if (validIds == null)
			cachedInLinks = new TIntObjectHashMap<int[]>((int)dbLinks.count(), 1) ;
		else
			cachedInLinks = new TIntObjectHashMap<int[]>(validIds.size(), 1) ;
		
		Cursor c = dbLinks.openCursor(null, null) ;
		
		DatabaseEntry key = new DatabaseEntry() ;
		DatabaseEntry value = new DatabaseEntry() ;
		
		while (c.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
			Integer id = intBinding.entryToObject(key) ;
			
			if (validIds == null || validIds.contains(id)) {
				
				DbLink[] links = linkArrayBinding.entryToObject(value) ;
			
				TIntArrayList linkIds = new TIntArrayList() ;
				
				for (DbLink l:links) {
					if (validIds == null || validIds.contains(l.id)) 
						linkIds.add(l.id) ;
				}
				
				cachedInLinks.put(id, linkIds.toNativeArray()) ;
			}

			pn.update() ;
		}
	}

	public boolean arePagesCached() {
		return cachedPages != null ;
	}
	
	public boolean areInLinksCached() {
		return cachedInLinks != null ;
	}

	public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.out.println("Please specify two directories, one for the berkeley database, and one for the lucene index") ;
		}
		
		File berkeleyDir = new File(args[0]) ;
		File luceneDir = new File(args[1]) ;
		
		WikipediaEnvironment we = new WikipediaEnvironment(berkeleyDir, luceneDir, false) ;

		try {
			
			//System.gc();
			//long memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

			TextProcessor tp = new CaseFolder() ;
			
			String query = "Best Landmarks in New Zealand" ;
			
			//TIntHashSet validIds = we.getValidPageIds(5, null) ;
			//we.cacheInLinks(validIds, null) ;
			
			//System.gc();
			//long memEnd = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

			Article[] arts = we.doFullTextSearch(query, 100) ;
			for (Article art:arts) {
				
				System.out.println(art + "," + art.getWeight()) ;
				
			}

			//System.out.println( (memEnd-memStart) + " bytes") ;
			
		} catch (Exception e) {

			throw e ;

		}

		we.close();

	}


}
