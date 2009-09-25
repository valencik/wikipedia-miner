package org.wikipedia.miner.db;

import gnu.trove.*;

import java.io.* ;
import java.util.*;

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

	private HashMap<DatabaseName, StoredMap> openStoredMaps ;
	private HashMap<DatabaseName, Database> openDatabases ;

	private HashMap<String, StoredMap<String, DbAnchor>> anchorMaps ;
	private HashMap<String, Database> anchorDatabases ;

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

	public WikipediaEnvironment(File databaseDir, File indexDir, final boolean loading) throws EnvironmentLockedException, DatabaseException {

		super(databaseDir, new EnvironmentConfig() {

			public boolean getAllowCreate() {
				return loading ;
			}
			public boolean getTransactional() {
				return false ;
			}
			public int getCachePercent() {
				if (loading) {
					return 20 ;
				} else {
					return 70 ;
				}
			}
		}) ;

		EnvironmentConfig ec = this.getConfig() ;
		ec.setConfigParam(EnvironmentConfig.ENV_RUN_CHECKPOINTER, "false") ;
		ec.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false") ;
		
		this.databaseDir = databaseDir ;
		this.indexDir = indexDir ;

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

	public static void main(String[] args) throws Exception {

		File berkeleyDir = new File("/Users/dmilne/Research/wikipedia/databases/simple/20080620") ;
		File luceneDir = new File("/Users/dmilne/Research/wikipedia/indexes/simple/20080620") ;
		
		File dumpDir = new File("/Users/dmilne/Research/wikipedia/data/simple/20080620") ;

		WikipediaEnvironment we = new WikipediaEnvironment(berkeleyDir, luceneDir, false) ;

		try {
			//we.loadData(dumpDir, true) ;

			TextProcessor tp = new CaseFolder() ;
			//we.prepareForTextProcessor(tp) ;

			String text = "Kiwi" ;

			System.out.println("no tp") ;
			DbAnchor anch = we.getAnchor(text, null) ;
			System.out.println(anch.getTotalReferences()) ;
			for (DbSense sense:anch.getSenses()) {
				DbPage p = we.getPageDetails(sense.getDestination()) ;
				System.out.println(" - " + sense.getDestination() + ":" + p.getTitle() + "," + sense.getDistinctCount()) ;
			}

			System.out.println(tp.getName()) ;
			anch = we.getAnchor(text, tp) ;
			System.out.println(anch.getTotalReferences()) ;
			for (DbSense sense:anch.getSenses()) {
				DbPage p = we.getPageDetails(sense.getDestination()) ;
				System.out.println(" - " + sense.getDestination() + ":" + p.getTitle() + "," + sense.getDistinctCount()) ;
			}

		} catch (Exception e) {

			throw e ;

		}

		we.close();

	}


}
