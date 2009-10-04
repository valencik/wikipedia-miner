package org.wikipedia.miner.db;

import gnu.trove.*;

import java.io.*;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.wikipedia.miner.db.WikipediaEnvironment.DatabaseName;
import org.wikipedia.miner.db.WikipediaEnvironment.Statistic;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.util.MarkupStripper;
import org.wikipedia.miner.util.ProgressNotifier;
import org.wikipedia.miner.util.text.CaseFolder;

import com.sleepycat.collections.StoredMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;

@SuppressWarnings("unchecked")
public class WikipediaEnvironmentLoader {

	ProgressNotifier pn ;
	WikipediaEnvironment we ;

	public WikipediaEnvironmentLoader(File databaseDir, File indexDir) throws DatabaseException, IOException {

		we = new WikipediaEnvironment(databaseDir, indexDir, true) ;

		//pn = new ProgressNotifier() ;


	}



	public void load(File dataDirectory, boolean overwrite) throws IOException, DatabaseException, ParseException {
		//TODO: check that all necessary files exist before doing anything
		//implement overwrite option
		//use one progress monitor
		//don't insist on page structure, content, or anchor_occurrences (but create stats to warn that these aren't available)

		//loadPageDetails(dataDirectory, null) ;
		//loadPageStructure(dataDirectory, null) ;
		//loadRedirects(dataDirectory, null) ;
		//loadLinkCounts(dataDirectory, null) ;
		//loadPageLinks(dataDirectory, null) ;
		loadCategoryLinks(dataDirectory, null) ;
		//loadAnchors(dataDirectory, null) ;
		//loadAnchorTexts(dataDirectory, null) ;
		//loadTranslations(dataDirectory, null) ;
		//loadContent(dataDirectory, null) ;
		
		//we.prepareForTextProcessor(new CaseFolder()) ;
		
		//indexArticles() ;

		we.close();
	}

	private void storeStat(Statistic stat, Integer value) throws DatabaseException {
		
		Database dbStats = we.getDatabase(DatabaseName.STATS, true, false) ;
		
		DatabaseEntry k = new DatabaseEntry() ;
		we.statBinding.objectToEntry(stat, k) ;
		
		DatabaseEntry v = new DatabaseEntry() ;
		we.intBinding.objectToEntry(value, v) ;
		
		dbStats.put(null, k, v) ;
	}

	private void loadPageDetails(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException{

		File pageFile = new File(dataDirectory.getPath() + File.separatorChar + "page.csv") ;

		Database dbPageDetails = we.getDatabase(DatabaseName.PAGE_DETAILS, true, true) ;
		

		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(pageFile), "UTF-8")) ;

		if (pn == null) pn = new ProgressNotifier(1) ;
		pn.startTask(pageFile.length(), "loading page details") ;

		long bytesRead = 0 ;
		int linesRead = 0 ;
		String line ;

		int pageCount = 0 ;
		int articleCount = 0 ;
		int categoryCount = 0 ;
		int redirectCount = 0 ;
		int disambigCount = 0 ;
		int rootCategoryId = -1 ;

		Pattern p = Pattern.compile("(\\d*?),\"(.*?)\",(\\d*?)", Pattern.DOTALL) ;

		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;
			linesRead++ ;

			Matcher m = p.matcher(line) ;

			if (m.matches()) {

				int id = Integer.parseInt(m.group(1)) ;
				String title = m.group(2) ;
				short type = Short.parseShort(m.group(3)) ;

				DatabaseEntry k = new DatabaseEntry() ;
				we.intBinding.objectToEntry(id, k) ;
				
				DatabaseEntry v = new DatabaseEntry() ;
				we.pageDetailsBinding.objectToEntry(new DbPage(title, type), v) ;
				
				dbPageDetails.put(null, k, v) ;

				pageCount++ ;
				switch(type) {
				case Page.ARTICLE: 
					articleCount++ ;
					break ;
				case Page.CATEGORY:
					categoryCount++ ;
					break ;
				case Page.DISAMBIGUATION:
					disambigCount++ ;
					break ;
				case Page.REDIRECT:
					redirectCount++ ;
					break ;
				}
				//TODO: get this from language file
				if (type==Page.CATEGORY && title.equalsIgnoreCase("fundamental") ) 
					rootCategoryId=id ;

			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}

			pn.update(bytesRead) ;
		}

		storeStat(Statistic.PAGE_COUNT, pageCount) ;
		storeStat(Statistic.ARTICLE_COUNT, articleCount) ;
		storeStat(Statistic.REDIRECT_COUNT, redirectCount);
		storeStat(Statistic.CATEGORY_COUNT, categoryCount);
		storeStat(Statistic.DISAMBIG_COUNT, disambigCount);

		if(rootCategoryId<0)
			throw new ParseException("Could not identify root category. Is your language file configured correctly?", 0) ;
		else
			storeStat(Statistic.ROOT_ID, rootCategoryId) ;

		System.out.print("Syncing database... ") ;
		dbPageDetails.sync() ;
		
		we.cleanLog() ;
		we.checkpoint(null) ;
		
	
		System.out.println("...done.") ;

		input.close();
	}

	private void loadPageStructure(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException{

		File pageFile = new File(dataDirectory.getPath() + File.separatorChar + "structure.csv") ;

		Database dbPageStructure = we.getDatabase(DatabaseName.PAGE_STRUCTURE, true, true) ;

		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(pageFile), "UTF-8")) ;

		if (pn == null) pn = new ProgressNotifier(1) ;
		pn.startTask(pageFile.length(), "loading page structure") ;

		long bytesRead = 0 ;
		int linesRead = 0 ;
		String line ;

		Pattern p = Pattern.compile("(\\d*?),\"(.*?)\"") ;

		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;
			linesRead++ ;

			Matcher m = p.matcher(line) ;

			if (m.matches()) {

				int id = Integer.parseInt(m.group(1)) ;
				String structure = m.group(2) ;
				
				DatabaseEntry k = new DatabaseEntry() ;
				we.intBinding.objectToEntry(id, k) ;
				
				DatabaseEntry v = new DatabaseEntry() ;
				we.structureBinding.objectToEntry(DbStructureNode.parseStructureString(structure), v) ;
				
				dbPageStructure.put(null, k, v) ;

			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}

			pn.update(bytesRead) ;
		}

		System.out.print("Syncing database... ") ;
		
		dbPageStructure.sync() ; 
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		System.out.println("...done.") ;

		input.close();
	}

	private void loadRedirects(File dataDirectory, ProgressNotifier pn)  throws IOException, ParseException, DatabaseException{

		File redirectFile = new File(dataDirectory.getPath() + File.separatorChar + "redirect.csv") ;

		Database dbRedirectsIn = we.getDatabase(DatabaseName.REDIRECTS_IN, true, true) ; 
		Database dbRedirectsOut = we.getDatabase(DatabaseName.REDIRECTS_OUT, true, true) ; 

		BufferedReader input = new BufferedReader(new FileReader(redirectFile)) ;

		if (pn == null) pn = new ProgressNotifier(2) ;
		pn.startTask(redirectFile.length(), "gathering redirects") ;

		long bytesRead = 0 ;
		int linesRead = 0 ;
		String line ;

		Pattern p = Pattern.compile("(\\d*?),(\\d*?)") ;
		TIntObjectHashMap<TIntArrayList> tempRedirects = new TIntObjectHashMap<TIntArrayList>() ;


		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;
			linesRead++ ;

			Matcher m = p.matcher(line) ;

			if (m.matches()) {

				int rdFrom = Integer.parseInt(m.group(1)) ;
				int rdTo = Integer.parseInt(m.group(2)) ;

				DatabaseEntry k = new DatabaseEntry() ;
				we.intBinding.objectToEntry(rdFrom, k) ;
				
				DatabaseEntry v = new DatabaseEntry() ;
				we.intBinding.objectToEntry(rdTo, v) ;

				dbRedirectsOut.put(null, k, v) ;

				TIntArrayList rdsIn = tempRedirects.get(rdTo) ;
				if (rdsIn == null) 
					rdsIn = new TIntArrayList() ;

				rdsIn.add(rdFrom) ;
				tempRedirects.put(rdTo, rdsIn) ;

			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}

			pn.update(bytesRead) ;
		}

		//save all stored redirects
		TIntObjectIterator<TIntArrayList> iter = tempRedirects.iterator() ; 
		pn.startTask(tempRedirects.size(), "storing redirects") ;

		for (int i = tempRedirects.size(); i-- > 0;) {
			iter.advance();

			int toId = iter.key() ;
			int[] fromIds = iter.value().toNativeArray() ;
			
			DatabaseEntry k = new DatabaseEntry() ;
			we.intBinding.objectToEntry(toId, k) ;
			
			DatabaseEntry v = new DatabaseEntry() ;
			we.intArrayBinding.objectToEntry(fromIds, v) ;

			dbRedirectsIn.put(null, k, v) ;
			
			pn.update() ;
		}
		tempRedirects = null ;



		System.out.print("Syncing database... ") ;
		dbRedirectsIn.sync() ;
		dbRedirectsOut.sync() ;
		
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ; 

		System.out.println("...done.") ;

		input.close();
	}

	private void loadPageLinks(File dataDirectory, ProgressNotifier pn, int passes) throws IOException, ParseException, DatabaseException {

		File pagelinkFile = new File(dataDirectory.getPath() + File.separatorChar + "pagelink.csv") ;

		Database dbLinksIn = we.getDatabase(DatabaseName.LINKS_IN, true, true) ;
		Database dbLinksOut = we.getDatabase(DatabaseName.LINKS_OUT, true, true) ;

		
		Pattern p = Pattern.compile("(\\d*),(\\d*),(\\d*)") ;

		if (pn == null) pn = new ProgressNotifier(passes * 2) ;

		for (int pass=0 ; pass < passes ; pass++) {
			pn.startTask(pagelinkFile.length(), "gathering pagelinks (pass " + (pass+1) + " of " + passes + ")") ;

			TIntObjectHashMap<Vector<DbLink>> tempLinksIn = new TIntObjectHashMap<Vector<DbLink>>() ;
			TIntObjectHashMap<TIntArrayList> tempLinksOut = new TIntObjectHashMap<TIntArrayList>() ;

			long bytesRead = 0 ;
			int linesRead = 0 ;
			String line ;

			int lastFromId = -1 ;
			BufferedReader input = new BufferedReader(new FileReader(pagelinkFile)) ;

			while ((line=input.readLine()) != null) {
				bytesRead = bytesRead + line.length() + 1 ;
				linesRead++ ;

				Matcher m = p.matcher(line) ;

				if (m.matches()) {

					int fromId = Integer.parseInt(m.group(1)) ;
					int toId = Integer.parseInt(m.group(2)) ;
					int pos = Integer.parseInt(m.group(3)) ;

					if (fromId != lastFromId) {
						if (lastFromId >= 0) {

							//at this point, we have gathered all links out from lastId, and built a hash of each outId to vector of positions
							//lets save out links to db if this is our first pass, and save in links to memory if relevant to this particular pass.

							DbLink[] outLinks = new DbLink[tempLinksOut.size()] ;

							TIntObjectIterator<TIntArrayList> iter = tempLinksOut.iterator() ; 
							for (int i = tempLinksOut.size(); i-- > 0;) {
								iter.advance();

								int destId = iter.key() ;
								int[] positions = iter.value().toNativeArray() ;

								// link in
								if (destId % passes == pass) {

									DbLink linkIn = new DbLink(lastFromId, positions) ;
									Vector<DbLink> linksIn = tempLinksIn.get(destId) ;
									if (linksIn == null) 
										linksIn = new Vector<DbLink>() ;

									linksIn.add(linkIn) ;
									tempLinksIn.put(destId, linksIn) ;
								}

								//link out
								if (pass == 0)
									outLinks[i] = new DbLink(destId, positions) ;
							}

							if (pass == 0) {
								Arrays.sort(outLinks) ;
								
								DatabaseEntry k = new DatabaseEntry() ;
								we.intBinding.objectToEntry(toId, k) ;
								
								DatabaseEntry v = new DatabaseEntry() ;
								we.linkArrayBinding.objectToEntry(outLinks, v) ;

								dbLinksOut.put(null, k, v) ;
							}
						}
						tempLinksOut.clear() ;
						lastFromId = fromId ;
					}


					TIntArrayList positions = tempLinksOut.get(toId) ;
					if (positions == null) 
						positions = new TIntArrayList() ;

					positions.add(pos) ;
					tempLinksOut.put(toId, positions) ;

				} else {
					throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
				}
				pn.update(bytesRead) ;
			}

			if (lastFromId >= 0) {

				//we still have last page to deal with
				//at this point, we have gathered all links out from lastId, and built a hash of each outId to vector of positions
				//lets save out links to db if this is our first pass, and save in links to memory if relevant to this particular pass.

				DbLink[] outLinks = new DbLink[tempLinksOut.size()] ;

				TIntObjectIterator<TIntArrayList> iter = tempLinksOut.iterator() ; 
				for (int i = tempLinksOut.size(); i-- > 0;) {
					iter.advance();

					int destId = iter.key() ;
					int[] positions = iter.value().toNativeArray() ;

					// link in
					if (destId % passes == pass) {

						DbLink linkIn = new DbLink(lastFromId, positions) ;
						Vector<DbLink> linksIn = tempLinksIn.get(destId) ;
						if (linksIn == null) 
							linksIn = new Vector<DbLink>() ;

						linksIn.add(linkIn) ;
						tempLinksIn.put(destId, linksIn) ;
					}

					//link out
					if (pass == 0)
						outLinks[i] = new DbLink(destId, positions) ;
				}

				if (pass == 0) {
					Arrays.sort(outLinks) ;
					
					DatabaseEntry k = new DatabaseEntry() ;
					we.intBinding.objectToEntry(lastFromId, k) ;
					
					DatabaseEntry v = new DatabaseEntry() ;
					we.linkArrayBinding.objectToEntry(outLinks, v) ;

					dbLinksOut.put(null, k, v) ;
				}
			}


			//now store gathered inlinks
			pn.startTask(tempLinksIn.size(), "storing pagelinks in  (pass " + (pass+1) + " of " + passes + ")") ;

			TIntObjectIterator<Vector<DbLink>> iter = tempLinksIn.iterator() ; 
			for (int i = tempLinksIn.size(); i-- > 0;) {
				iter.advance();

				int toId = iter.key() ;
				Vector<DbLink> inLinks = iter.value() ;

				DbLink[] dbLinks = inLinks.toArray(new DbLink[inLinks.size()]) ;
				
				DatabaseEntry k = new DatabaseEntry() ;
				we.intBinding.objectToEntry(toId, k) ;
				
				DatabaseEntry v = new DatabaseEntry() ;
				we.linkArrayBinding.objectToEntry(dbLinks, v) ;

				dbLinksIn.put(null, k, v) ;

				pn.update() ;
			}
			tempLinksIn.clear();

			System.out.print("Syncing database... ") ;
			dbLinksIn.sync() ;
			dbLinksOut.sync() ;
			we.cleanLog() ;
			we.checkpoint(null) ;
			we.evictMemory() ;
			System.out.println("...done.") ;

			System.gc();

			input.close();
		}
	}

	private void loadCategoryLinks(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File catLinkFile = new File(dataDirectory.getPath() + File.separatorChar + "categorylink.csv") ;

		Database dbParents = we.getDatabase(DatabaseName.PARENTS, true, true) ;
		Database dbChildArticles = we.getDatabase(DatabaseName.CHILD_ARTICLES, true, true) ;
		Database dbChildCategories = we.getDatabase(DatabaseName.CHILD_CATEGORIES, true, true) ;
		
		BufferedReader input = new BufferedReader(new FileReader(catLinkFile)) ;

		if (pn == null) pn = new ProgressNotifier(4) ;
		pn.startTask(catLinkFile.length(), "gathering category links") ;

		long bytesRead = 0 ;
		int linesRead = 0 ;
		String line ;

		int lastChildId = -1 ;
		TIntArrayList tempParents = null ;

		TIntObjectHashMap<TIntArrayList> tempChildCategories = new TIntObjectHashMap<TIntArrayList>() ;
		TIntObjectHashMap<TIntArrayList> tempChildArticles = new TIntObjectHashMap<TIntArrayList>() ;

		Pattern p = Pattern.compile("(\\d*?),(\\d*?)") ;

		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;
			linesRead++ ;

			Matcher m = p.matcher(line) ;

			if (m.matches()) {

				int parentId = Integer.parseInt(m.group(1)) ;
				int childId = Integer.parseInt(m.group(2)) ;

				if (childId != lastChildId) {

					if (lastChildId > 0) {
						//need to save gathered parents
						
						tempParents.sort() ;
						
						DatabaseEntry k = new DatabaseEntry() ;
						we.intBinding.objectToEntry(lastChildId, k) ;
						
						DatabaseEntry v = new DatabaseEntry() ;
						we.intArrayBinding.objectToEntry(tempParents.toNativeArray(), v) ;

						dbParents.put(null, k, v) ;
					}

					tempParents = new TIntArrayList() ;
					lastChildId = childId ;
				}

				tempParents.add(parentId) ;

				DbPage pd = we.getPageDetails(childId) ;

				if (pd != null) {

					if (pd.getType() == Page.CATEGORY) {
						TIntArrayList children = tempChildCategories.get(parentId) ;
						if (children == null) 
							children = new TIntArrayList() ;

						children.add(childId) ;
						tempChildCategories.put(parentId, children) ;
					} else {
						TIntArrayList children = tempChildArticles.get(parentId) ;
						if (children == null) 
							children = new TIntArrayList() ;

						children.add(childId) ;
						tempChildArticles.put(parentId, children) ;
					}
				}
				pn.update(bytesRead) ;				
			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}
		}
		
		input.close();

		//save all stored child categories
		pn.startTask(tempChildCategories.size(), "storing child categories") ;
		TIntObjectIterator<TIntArrayList> iter = tempChildCategories.iterator() ; 
		for (int i = tempChildCategories.size(); i-- > 0;) {
			iter.advance();

			int parent = iter.key() ;
			int[] children = iter.value().toNativeArray() ; 
			
			DatabaseEntry k = new DatabaseEntry() ;
			we.intBinding.objectToEntry(parent, k) ;
			
			DatabaseEntry v = new DatabaseEntry() ;
			we.intArrayBinding.objectToEntry(children, v) ;

			dbChildCategories.put(null, k, v) ;
			
			pn.update() ;
		}

		//save all stored child categories
		pn.startTask(tempChildArticles.size(), "storing child articles") ;
		iter = tempChildArticles.iterator() ; 
		for (int i = tempChildArticles.size(); i-- > 0;) {
			iter.advance();

			int parent = iter.key() ;
			int[] children = iter.value().toNativeArray() ; 
			
			DatabaseEntry k = new DatabaseEntry() ;
			we.intBinding.objectToEntry(parent, k) ;
			
			DatabaseEntry v = new DatabaseEntry() ;
			we.intArrayBinding.objectToEntry(children, v) ;

			dbChildArticles.put(null, k, v) ;
			pn.update() ;
		}

		//calculate depths of pages (vertical distance from root category), while we have child relations in memory
		pn.startTask(we.getStatisticValue(Statistic.PAGE_COUNT), "gathering page depths") ;
		int currDepth = 0 ;
		Integer currPage = we.getStatisticValue(Statistic.ROOT_ID) ;

		final TIntArrayList currLevel = new TIntArrayList() ;
		final TIntArrayList nextLevel = new TIntArrayList() ;
		final TIntIntHashMap tempDepths = new TIntIntHashMap() ;

		while (currPage != null) {

			if (!tempDepths.containsKey(currPage)) {
				tempDepths.put(currPage, currDepth) ;

				TIntArrayList childArticles = tempChildArticles.get(currPage) ;
				final int d = currDepth+1 ;
				if (childArticles != null) {
					childArticles.forEach(new TIntProcedure() {
						public boolean execute(int childArticle) {
							if (!tempDepths.containsKey(childArticle))
								tempDepths.put(childArticle, d) ;
							return true ;
						}
					}) ;
				}

				TIntArrayList childCategories = tempChildCategories.get(currPage) ;
				if (childCategories != null) {
					childCategories.forEach(new TIntProcedure() {
						public boolean execute(int childCategory) {
							if (!tempDepths.containsKey(childCategory))
								nextLevel.add(childCategory) ;
							return true ;
						}
					}) ;
				}
				
			}

			if (currLevel.isEmpty()) {
				currLevel.add(nextLevel.toNativeArray()) ;
				nextLevel.clear();
				currDepth++ ;
			}

			if (currLevel.isEmpty())
				currPage = null ;
			else {
				currPage = currLevel.get(0);
				currLevel.remove(0) ;
			}
			
			pn.update(tempDepths.size());
		}
		
		//save gathered depths
		
		final Database dbDepths =  we.getDatabase(DatabaseName.DEPTHS, true, true) ;
		
		pn.startTask(we.getStatisticValue(Statistic.PAGE_COUNT), "storing page depths") ;
		final ProgressNotifier pn2 = pn ;
		tempDepths.forEachEntry(new TIntIntProcedure() {
			
			public boolean execute(int id, int depth) {
				try {
					DatabaseEntry k = new DatabaseEntry() ;
					we.intBinding.objectToEntry(id, k) ;
					
					DatabaseEntry v = new DatabaseEntry() ;
					we.intBinding.objectToEntry(depth, v) ;
	
					dbDepths.put(null, k, v) ;
				} catch (Exception e) {
					System.out.println("Cannot store page depths") ;
					return false ;
				}
				
				pn2.update();
				return true ;
			}
		});
		
		

		//free up memory 
		tempChildCategories.clear() ;
		tempChildArticles.clear() ;
		tempDepths.clear() ;

		storeStat(Statistic.MAX_DEPTH, currDepth) ;

		System.out.print("Syncing database... ") ;

		dbParents.sync() ; 
		dbChildArticles.sync() ; 
		dbChildCategories.sync() ; 
		dbDepths.sync() ; 
		
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		
		System.gc() ;

		System.out.println("...done.") ;

		
	}

	private void loadContent(File dataDir, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File[] dumpFiles = dataDir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith("-pages-articles.xml");
			} 
		}) ;

		if (dumpFiles.length > 1) 
			throw new IOException("There is more than one dump file in " + dataDir.getPath()) ;

		if (dumpFiles.length == 0) 
			throw new IOException("Could not locate xml dump file in " + dataDir.getPath()) ;

		Database dbPageContent = we.getDatabase(DatabaseName.PAGE_CONTENT, true, true) ;

		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(dumpFiles[0]), "UTF8")) ;

		if (pn == null) pn = new ProgressNotifier(1) ;
		pn.startTask(dumpFiles[0].length(), "loading page content") ;

		long bytesRead = 0 ;
		int pagesRead = 0 ;
		String line ;

		StringBuffer pageMarkup = null ;

		Pattern pagePattern = Pattern.compile("\\s*<(/?)page>\\s*") ;
		Pattern idPattern = Pattern.compile("<id>(\\d*)</id>") ;
		Pattern contentPattern = Pattern.compile("<text.*?>(.*)<\\/text>", Pattern.DOTALL) ;

		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;

			Matcher pageMatcher = pagePattern.matcher(line) ;
			if (pageMatcher.matches()) {

				if (pageMatcher.group(1).length() == 0)  {
					pageMarkup = new StringBuffer() ;
					pagesRead++ ;
				} else {
					//Now we have a complete page, lets deal with it
					//A full parse is overkill. All we want is id and content.

					Matcher idMatcher = idPattern.matcher(pageMarkup) ;
					if (idMatcher.find()) {

						int id = Integer.parseInt(idMatcher.group(1)) ;

						if (we.getPageDetails(id) != null) {

							Matcher contentMatcher = contentPattern.matcher(pageMarkup) ;
							if (contentMatcher.find()) {
								String content = contentMatcher.group(1) ;

								content = StringEscapeUtils.unescapeXml(content) ;
								
								DatabaseEntry k = new DatabaseEntry() ;
								we.intBinding.objectToEntry(id, k) ;
								
								DatabaseEntry v = new DatabaseEntry() ;
								we.strBinding.objectToEntry(content, v) ;
				
								dbPageContent.put(null, k, v) ;

							} else {
								new ParseException("Could not locate content in page markup", pagesRead) ;
							}
						} else {
							//This is not a page we are interested in.
						}

					} else {
						throw new ParseException("Could not locate id in page markup", pagesRead) ;
					}
					pageMarkup = null ;
				}
			} else {
				if (pageMarkup != null) {
					pageMarkup.append(line) ;
					pageMarkup.append("\n") ;
				}
			}

			pn.update(bytesRead) ;
		}

		System.out.print("Syncing database... ") ;
		
		dbPageContent.sync() ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;

		System.out.println("...done.") ;

		input.close();
	}

	private void loadAnchors(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File anchorsFile = new File(dataDirectory.getPath() + File.separatorChar + "anchor.csv") ;
		
		//ensure that anchor database is empty
		Database dbAnchor = we.openDatabases.get(DatabaseName.ANCHOR) ;
		if (dbAnchor != null)
			dbAnchor.close();
		
		try {
			we.removeDatabase(null, DatabaseName.ANCHOR.toString()) ;
		} catch (DatabaseException e) {};

		we.cleanLog() ;
		we.checkpoint(null) ;
		
		dbAnchor = we.openDatabase(null, DatabaseName.ANCHOR.toString(), we.writingConfig) ;
		
		final DatabaseEntry k = new DatabaseEntry() ;
		final DatabaseEntry v = new DatabaseEntry() ;

		Pattern p = Pattern.compile("\"(.*?)\",(\\d*),(\\d*),(\\d*),(\\d*)", Pattern.DOTALL) ;

		int passes = 5 ;
		if (pn==null) pn = new ProgressNotifier((2*passes) + 1) ;

		for (int pass=0 ; pass < passes ; pass++) {
			pn.startTask(anchorsFile.length(), "gathering anchors (pass " + (pass+1) + " of " + passes + ")") ;

			BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(anchorsFile), "UTF-8")) ;

			THashMap<String,Vector<DbSense>> tempSenses = new THashMap<String,Vector<DbSense>>() ; 

			long bytesRead = 0 ;
			int linesRead = 0 ;
			String line ;

			while ((line=input.readLine()) != null) {
				bytesRead = bytesRead + line.length() + 1 ;
				linesRead++ ;

				Matcher m = p.matcher(line) ;
				if (m.matches()) {
					String anchor = m.group(1).trim() ;
					int destination = Integer.parseInt(m.group(2)) ;
					int totalCount = Integer.parseInt(m.group(3)) ;
					int distinctCount = Integer.parseInt(m.group(4)) ;
					short type = Short.parseShort(m.group(5)) ;

					if (Math.abs(anchor.hashCode()) % passes == pass) {

						DbSense s = new DbSense(destination, totalCount, distinctCount, type==2, type==1) ;

						Vector<DbSense> senses = tempSenses.get(anchor) ;
						if (senses == null)
							senses = new Vector<DbSense>() ;
						senses.add(s) ;

						tempSenses.put(anchor, senses) ;
					}
				} else {
					throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
				}

				pn.update(bytesRead) ;
			}
			input.close();

			//save all gathered anchors
			pn.startTask(tempSenses.size(), "storing anchors (pass " + (pass+1) + " of " + passes + ")") ;
			final ProgressNotifier pn2 = pn ;
			final Database db = dbAnchor ;
			
			

			tempSenses.forEachEntry(new TObjectObjectProcedure<String, Vector<DbSense>>() {

				public boolean execute(String text, Vector<DbSense> senses) {
					DbSense[] sortedSenses = senses.toArray(new DbSense[senses.size()]) ;
					Arrays.sort(sortedSenses) ;

					//set counts as 0 for now
					DbAnchor anchor = new DbAnchor(0,0,(long)0,0,sortedSenses) ;

					we.strBinding.objectToEntry(text, k) ;
					we.anchorBinding.objectToEntry(anchor, v) ;
					
					try {
						db.put(null, k, v) ;
					} catch (DatabaseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					pn2.update() ;
					return true ;
				}
			}) ;

			tempSenses = null ;
			
			dbAnchor.sync() ;

			we.cleanLog() ;
			we.checkpoint(null) ;

			System.gc();
		}



		//now read in anchor counts, and store them

		File anchorOccurrencesFile = new File(dataDirectory.getPath() + File.separatorChar + "anchor_occurrence.csv") ;
		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(anchorOccurrencesFile), "UTF-8")) ;

		pn.startTask(anchorOccurrencesFile.length(), "storing anchor counts") ;

		long bytesRead = 0 ;
		int linesRead = 0 ;
		String line ;
		p = Pattern.compile("\"(.*?)\",(\\d*),(\\d*),(\\d*),(\\d*)", Pattern.DOTALL) ;

		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;
			linesRead++ ;

			Matcher m = p.matcher(line) ;
			if (m.matches()) {
				String text = m.group(1).trim() ;
				int linkCountTotal = Integer.parseInt(m.group(2)) ;
				int linkCountDistinct = Integer.parseInt(m.group(3)) ;
				long occCountTotal = Long.parseLong(m.group(4)) ;
				int occCountDistinct = Integer.parseInt(m.group(5)) ;

				DbAnchor anchorToStore = new DbAnchor(linkCountTotal, linkCountDistinct, occCountTotal, occCountDistinct, null) ;

				we.strBinding.objectToEntry(text, k) ;
				
				if (dbAnchor.get(null, k, v, null) == OperationStatus.SUCCESS){
					DbAnchor storedAnchor = we.anchorBinding.entryToObject(v) ; 
					anchorToStore.mergeWith(storedAnchor) ;
				}

				we.anchorBinding.objectToEntry(anchorToStore, v) ;
				dbAnchor.put(null, k, v) ;
			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}

			pn.update(bytesRead) ;
		}


		System.out.print("Syncing database... ") ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		
		System.out.println("...done.") ;

		input.close();
	}


	private void loadAnchorTexts(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File anchorsFile = new File(dataDirectory.getPath() + File.separatorChar + "anchor.csv") ;

		final Database dbAnchorTexts = we.getDatabase(DatabaseName.ANCHOR_TEXTS, true, true) ;

		int passes = 5 ;
		if (pn==null) pn = new ProgressNotifier(passes * 2) ;

		Pattern p = Pattern.compile("\"(.*?)\",(\\d*),(\\d*),(\\d*),(\\d*)", Pattern.DOTALL) ;

		for (int pass=0;pass<passes;pass++) {

			pn.startTask(anchorsFile.length(), "gathering anchors by destination (pass " + (pass+1) + " of " + passes + ")") ;

			THashMap<Integer,Vector<DbAnchorText>> tempAnchorTexts = new THashMap<Integer,Vector<DbAnchorText>>() ; 

			long bytesRead = 0 ;
			int linesRead = 0 ;
			String line ;

			BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(anchorsFile), "UTF-8")) ;

			while ((line=input.readLine()) != null) {
				bytesRead = bytesRead + line.length() + 1 ;
				linesRead++ ;

				Matcher m = p.matcher(line) ;
				if (m.matches()) {
					String anchor = m.group(1).trim() ;
					int destination = Integer.parseInt(m.group(2)) ;
					int totalCount = Integer.parseInt(m.group(3)) ;
					int distinctCount = Integer.parseInt(m.group(3)) ;

					if (destination % passes == pass) {

						DbAnchorText at = new DbAnchorText(anchor, totalCount, distinctCount) ;

						Vector<DbAnchorText> anchorTexts = tempAnchorTexts.get(destination) ;
						if (anchorTexts == null)
							anchorTexts = new Vector<DbAnchorText>() ;
						anchorTexts.add(at) ;

						tempAnchorTexts.put(destination, anchorTexts) ;
					}
				} else {
					throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
				}

				pn.update(bytesRead) ;
			}

			//save all stored anchor texts
			pn.startTask(tempAnchorTexts.size(), "storing anchor texts by destination (pass " + (pass+1) + " of " + passes + ")") ;

			final ProgressNotifier pn2 = pn ;

			tempAnchorTexts.forEachEntry(new TObjectObjectProcedure<Integer,Vector<DbAnchorText>>() {
				public boolean execute(Integer destination, Vector<DbAnchorText> anchorTexts) {

					try {
						DbAnchorText[] sortedAnchorTexts = anchorTexts.toArray(new DbAnchorText[anchorTexts.size()]) ;
						Arrays.sort(sortedAnchorTexts) ;
	
						DatabaseEntry k = new DatabaseEntry() ;
						we.intBinding.objectToEntry(destination, k) ;
						
						DatabaseEntry v = new DatabaseEntry() ;
						we.anchorTextArrayBinding.objectToEntry(sortedAnchorTexts, v) ;
		
						dbAnchorTexts.put(null, k, v) ;
					} catch (DatabaseException e) {
						e.printStackTrace() ;
						return false ;
					}
					
					pn2.update() ;
					return true ;
				}
			} ) ;

			System.out.print("Syncing database... ") ;
			tempAnchorTexts.clear() ;
			dbAnchorTexts.sync() ;
			we.cleanLog() ;
			we.checkpoint(null) ;
			we.evictMemory() ;
			
			System.gc();
			
			System.out.println("...done.") ;
			
			
			input.close();
		}
	}

	private void loadLinkCounts(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File pageLinksFile = new File(dataDirectory.getPath() + File.separatorChar + "pagelink.csv") ;

		Database dbLinkCounts = we.getDatabase(DatabaseName.LINK_COUNTS, true, true) ;

		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(pageLinksFile), "UTF-8")) ;

		if (pn==null) pn = new ProgressNotifier(2) ;
		pn.startTask(pageLinksFile.length(), "gathering link counts") ;

		long bytesRead = 0 ;
		int linesRead = 0 ;
		String line ;
		Pattern p = Pattern.compile("(\\d*),(\\d*),(\\d*)") ;

		int lastFrom = -1 ;
		short out_distinct = 0 ;
		short out_total = 0 ;

		TIntObjectHashMap<int[]> tempLinkCounts = new TIntObjectHashMap<int[]>() ;
		TIntHashSet outSeen = new TIntHashSet() ;

		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;
			linesRead++ ;

			Matcher m = p.matcher(line) ;

			if (m.matches()) {

				int fromId = Integer.parseInt(m.group(1)) ;
				int toId = Integer.parseInt(m.group(2)) ;

				if (fromId != lastFrom) {

					if (lastFrom > 0) {
						//need to save links out from lastFrom
						int[] lcOut =  tempLinkCounts.get(lastFrom) ;
						if (lcOut == null)
							lcOut = new int[4] ;

						lcOut[0] = out_total ;
						lcOut[1] = out_distinct ;

						tempLinkCounts.put(lastFrom, lcOut) ;
					}

					//reset out counts
					out_distinct = 0 ;
					out_total = 0 ;
					outSeen.clear() ;

					lastFrom = fromId ;
				}

				out_total ++ ;

				//save in count
				int[] lcIn = tempLinkCounts.get(toId) ;
				if (lcIn == null)
					lcIn = new int[4] ;

				lcIn[2]++ ;

				if (!outSeen.contains(toId)) {
					out_distinct ++ ;
					lcIn[3]++ ;
					outSeen.add(toId) ;
				}

				tempLinkCounts.put(toId, lcIn) ;
			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}
			pn.update(bytesRead) ;
		}

		//save all stored linkcounts

		pn.startTask(tempLinkCounts.size(), "storing gathered link counts") ;
		TIntObjectIterator<int[]> iter = tempLinkCounts.iterator() ; 
		for (int i = tempLinkCounts.size(); i-- > 0;) {
			iter.advance();
			
			DatabaseEntry k = new DatabaseEntry() ;
			we.intBinding.objectToEntry(iter.key(), k) ;
			
			DatabaseEntry v = new DatabaseEntry() ;
			we.intArrayBinding.objectToEntry(iter.value(), v) ;

			dbLinkCounts.put(null, k, v) ;
		
			pn.update() ;
		}
		tempLinkCounts = null ;




		System.out.print("Syncing database... ") ;
		dbLinkCounts.sync() ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		System.out.println("...done.") ;

		input.close();
	}

	private void loadTranslations(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File translationsFile = new File(dataDirectory.getPath() + File.separatorChar + "translation.csv") ;

		Database dbTranslations = we.getDatabase(DatabaseName.TRANSLATIONS, true, true) ; 

		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(translationsFile), "UTF-8")) ;

		if (pn==null) pn = new ProgressNotifier(1) ;
		pn.startTask(translationsFile.length(), "gathering and storing translations") ;

		long bytesRead = 0 ;
		int linesRead = 0 ;
		String line ;
		Pattern p = Pattern.compile("(\\d*),\"(.*)\",\"(.*)\"") ;

		int lastId = -1 ;
		Vector<String> tempTranslations = new Vector<String>();

		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;
			linesRead++ ;

			Matcher m = p.matcher(line) ;

			if (m.matches()) {

				int id = Integer.parseInt(m.group(1)) ;
				String lang = m.group(2) ;
				String translation = m.group(3) ;

				if (id != lastId) {

					if (lastId >= 0) {
						//need to save translations gathered for lastId
						if (!tempTranslations.isEmpty()) {
							
							DatabaseEntry k = new DatabaseEntry() ;
							we.intBinding.objectToEntry(lastId, k) ;
							
							DatabaseEntry v = new DatabaseEntry() ;
							we.strArrayBinding.objectToEntry(tempTranslations.toArray(new String[tempTranslations.size()]), v) ;

							dbTranslations.put(null, k, v) ;
						}
							
					}

					tempTranslations = new Vector<String>() ;
					lastId = id ;
				}

				tempTranslations.add(lang) ;
				tempTranslations.add(translation) ;

			}else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}
		}

		//need to save last stored translations
		if (!tempTranslations.isEmpty()) {
			DatabaseEntry k = new DatabaseEntry() ;
			we.intBinding.objectToEntry(lastId, k) ;
			
			DatabaseEntry v = new DatabaseEntry() ;
			we.strArrayBinding.objectToEntry(tempTranslations.toArray(new String[tempTranslations.size()]), v) ;

			dbTranslations.put(null, k, v) ;
		}

		System.out.print("Syncing database... ") ;
		dbTranslations.sync() ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		System.out.println("...done.") ;

		input.close();
	}


	private void indexArticles() throws IOException, DatabaseException {

		IndexWriter w = new IndexWriter(we.index, we.analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);

		ProgressNotifier pn = new ProgressNotifier(we.getStatisticValue(Statistic.PAGE_COUNT), "Indexing articles for full-text search") ;
		pn.setMinReportProgress(0.0001) ;
		
		MarkupStripper stripper = new MarkupStripper() ;
		//String[] unwantedSections = {"see also", "references", "further sources", "further reading", "footnotes", "external links", "bibliography", "notes", "notes and references", "other websites"} ;

		DbPageIterator iter = new DbPageIterator(we) ; 
		while (iter.hasNext()) {

			Page page = iter.next();

			if (page.getType() == Page.ARTICLE || page.getType() == Page.DISAMBIGUATION) {

				String markup = we.getPageContent(page.getId()) ;

				if (markup == null) {
					//System.out.println("no content") ;
				} else {
					markup = stripper.stripToPlainText(markup, null) ;
					
					StringBuffer anchorText = new StringBuffer() ;
					
					DbAnchorText[] ats = we.getAnchorTexts(page.getId()) ;
					if (ats != null) {
						for (DbAnchorText at:ats) {
							if (at.getTotalCount() >= 3) {
								anchorText.append(at.getText()) ;
								anchorText.append("\n\n") ;
							}					
						}
					}
					
					Document doc = new Document();
					doc.add(new Field("title", page.getTitle(), Field.Store.NO, Field.Index.ANALYZED)) ;
					doc.add(new Field("synonyms", anchorText.toString(), Field.Store.NO, Field.Index.ANALYZED)) ;
					doc.add(new Field("content", markup, Field.Store.NO, Field.Index.ANALYZED));
					doc.add(new Field("id", String.valueOf(page.getId()), Field.Store.YES, Field.Index.NO));

					w.addDocument(doc);
				}
			}
			pn.update() ;
		}
		w.close();

	}

	public static void main(String[] args) throws Exception {

		if (args.length != 3) {
			System.out.println("Please specify three directories, one for the berkeley database, one for the lucene index, and one containing the xml dump and csv files.") ;
		}
		
		File berkeleyDir = new File(args[0]) ;
		File luceneDir = new File(args[1]) ;
		File dumpDir = new File(args[2]) ;

		WikipediaEnvironmentLoader loader = null ;
		
		try {
			loader = new WikipediaEnvironmentLoader(berkeleyDir, luceneDir) ;
			loader.load(dumpDir, true) ;
		} catch (Exception e) {
			e.printStackTrace() ;
			
		} finally {
			if (loader != null)
				loader.we.close();
		}

		 
	}

}
