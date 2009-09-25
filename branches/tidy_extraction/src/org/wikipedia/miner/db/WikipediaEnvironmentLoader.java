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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.wikipedia.miner.db.WikipediaEnvironment.DatabaseName;
import org.wikipedia.miner.db.WikipediaEnvironment.Statistic;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.util.MarkupStripper;
import org.wikipedia.miner.util.ProgressNotifier;
import org.wikipedia.miner.util.text.CaseFolder;

import com.sleepycat.collections.StoredMap;
import com.sleepycat.je.DatabaseException;

@SuppressWarnings("unchecked")
public class WikipediaEnvironmentLoader {

	ProgressNotifier pn ;
	WikipediaEnvironment we ;

	public WikipediaEnvironmentLoader(File databaseDir, File indexDir) throws DatabaseException {

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
		//loadCategoryLinks(dataDirectory, null) ;
		loadAnchors(dataDirectory, null) ;
		//loadAnchorTexts(dataDirectory, null) ;
		//loadTranslations(dataDirectory, null) ;
		//loadContent(dataDirectory, null) ;
		//indexArticles() ;


		//we.prepareForTextProcessor(new CaseFolder()) ;
		we.close();
	}



	private void loadPageDetails(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException{

		File pageFile = new File(dataDirectory.getPath() + File.separatorChar + "page.csv") ;

		StoredMap<Integer,DbPage> smPageDetails = we.getStoredMap(DatabaseName.PAGE_DETAILS, true, true) ;
		StoredMap<Statistic,Integer> smStats = we.getStoredMap(DatabaseName.STATS, true, true) ;

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

				smPageDetails.put(id, new DbPage(title, type)) ;

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

		smStats.put(Statistic.PAGE_COUNT, pageCount) ;
		smStats.put(Statistic.ARTICLE_COUNT, articleCount) ;
		smStats.put(Statistic.REDIRECT_COUNT, redirectCount);
		smStats.put(Statistic.CATEGORY_COUNT, categoryCount);
		smStats.put(Statistic.DISAMBIG_COUNT, disambigCount);

		if(rootCategoryId<0)
			throw new ParseException("Could not identify root category. Is your language file configured correctly?", 0) ;
		else
			smStats.put(Statistic.ROOT_ID, rootCategoryId) ;

		System.out.print("Syncing database... ") ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		smPageDetails = we.getStoredMap(DatabaseName.PAGE_DETAILS, false, false) ;
		smStats = we.getStoredMap(DatabaseName.STATS, false, false) ;


		System.out.println("...done.") ;

		input.close();
	}

	private void loadPageStructure(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException{

		File pageFile = new File(dataDirectory.getPath() + File.separatorChar + "structure.csv") ;

		StoredMap<Integer, DbStructureNode> smPageStructure = we.getStoredMap(DatabaseName.PAGE_STRUCTURE, true, true) ;

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
				smPageStructure.put(id, DbStructureNode.parseStructureString(structure)) ;

			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}

			pn.update(bytesRead) ;
		}

		System.out.print("Syncing database... ") ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		smPageStructure = we.getStoredMap(DatabaseName.PAGE_STRUCTURE, false, false) ;
		System.out.println("...done.") ;

		input.close();
	}

	private void loadRedirects(File dataDirectory, ProgressNotifier pn)  throws IOException, ParseException, DatabaseException{

		File redirectFile = new File(dataDirectory.getPath() + File.separatorChar + "redirect.csv") ;

		StoredMap<Integer,int[]> smRedirectsIn = we.getStoredMap(DatabaseName.REDIRECTS_IN, true, true) ; 
		StoredMap<Integer,Integer>smRedirectsOut = we.getStoredMap(DatabaseName.REDIRECTS_OUT, true, true) ; 

		BufferedReader input = new BufferedReader(new FileReader(redirectFile)) ;

		if (pn == null) pn = new ProgressNotifier(1) ;
		pn.startTask(redirectFile.length(), "loading redirects") ;

		long bytesRead = 0 ;
		int linesRead = 0 ;
		String line ;

		Pattern p = Pattern.compile("(\\d*?),(\\d*?)") ;
		TIntObjectHashMap<Vector<Integer>> tempRedirects = new TIntObjectHashMap<Vector<Integer>>() ;


		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;
			linesRead++ ;

			Matcher m = p.matcher(line) ;

			if (m.matches()) {

				int rdFrom = Integer.parseInt(m.group(1)) ;
				int rdTo = Integer.parseInt(m.group(2)) ;


				smRedirectsOut.put(rdFrom, rdTo) ;

				Vector<Integer> rdsIn = tempRedirects.get(rdTo) ;
				if (rdsIn == null) 
					rdsIn = new Vector<Integer>() ;

				rdsIn.add(rdFrom) ;
				tempRedirects.put(rdTo, rdsIn) ;

			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}

			pn.update(bytesRead) ;
		}

		//save all stored redirects
		TIntObjectIterator<Vector<Integer>> iter = tempRedirects.iterator() ; 

		for (int i = tempRedirects.size(); i-- > 0;) {
			iter.advance();

			int toId = iter.key() ;

			int[] fromIds = new int[iter.value().size()] ;
			int j = 0 ;
			for (int fromId:iter.value()) 
				fromIds[j++] = fromId ;

			smRedirectsIn.put(toId, fromIds) ;
		}
		tempRedirects = null ;



		System.out.print("Syncing database... ") ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		smRedirectsIn = we.getStoredMap(DatabaseName.REDIRECTS_IN, false, false) ; 
		smRedirectsOut = we.getStoredMap(DatabaseName.REDIRECTS_OUT, false, false) ; 

		System.out.println("...done.") ;

		input.close();
	}

	private void loadPageLinks(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File pagelinkFile = new File(dataDirectory.getPath() + File.separatorChar + "pagelink.csv") ;

		StoredMap<Integer,DbLink[]>smLinksIn = we.getStoredMap(DatabaseName.LINKS_IN, true, true) ;
		StoredMap<Integer,DbLink[]>smLinksOut = we.getStoredMap(DatabaseName.LINKS_OUT, true, true) ;


		if (pn == null) pn = new ProgressNotifier(6) ;

		Pattern p = Pattern.compile("(\\d*),(\\d*),(\\d*)") ;

		int passes = 5 ;
		for (int pass=0 ; pass < passes ; pass++) {
			pn.startTask(pagelinkFile.length(), "gathering pagelinks (pass " + (pass+1) + " of " + passes + ")") ;
			//pn.startTask(pagelinkFile.length(), "gathering pagelinks") ;

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

									DbLink dbLinkIn = new DbLink(lastFromId, positions) ;
									Vector<DbLink> dbLinksIn = tempLinksIn.get(destId) ;
									if (dbLinksIn == null) 
										dbLinksIn = new Vector<DbLink>() ;

									dbLinksIn.add(dbLinkIn) ;
									tempLinksIn.put(destId, dbLinksIn) ;
								}

								//link out
								if (pass == 0)
									outLinks[i] = new DbLink(destId, positions) ;
							}

							if (pass == 0) {
								Arrays.sort(outLinks) ;
								smLinksOut.put(lastFromId, outLinks) ;
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

						DbLink dbLinkIn = new DbLink(lastFromId, positions) ;
						Vector<DbLink> dbLinksIn = tempLinksIn.get(destId) ;
						if (dbLinksIn == null) 
							dbLinksIn = new Vector<DbLink>() ;

						dbLinksIn.add(dbLinkIn) ;
						tempLinksIn.put(destId, dbLinksIn) ;
					}

					//link out
					if (pass == 0)
						outLinks[i] = new DbLink(destId, positions) ;
				}

				if (pass == 0) {
					Arrays.sort(outLinks) ;
					smLinksOut.put(lastFromId, outLinks) ;
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
				smLinksIn.put(toId, dbLinks) ;
				pn.update() ;
			}
			tempLinksIn.clear();

			we.cleanLog() ;
			we.checkpoint(null) ;
			we.evictMemory() ;

			System.gc();

			input.close();
		}


		System.out.print("Syncing database... ") ;
		smLinksIn = we.getStoredMap(DatabaseName.LINKS_IN, false, false) ;
		smLinksOut = we.getStoredMap(DatabaseName.LINKS_OUT, false, false) ;
		System.out.println("...done.") ;


	}

	private void loadCategoryLinks(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File catLinkFile = new File(dataDirectory.getPath() + File.separatorChar + "categorylink.csv") ;

		StoredMap<Integer,int[]> smParents = we.getStoredMap(DatabaseName.PARENTS, true, true) ;
		StoredMap<Integer,int[]>smChildArticles = we.getStoredMap(DatabaseName.CHILD_ARTICLES, true, true) ;
		StoredMap<Integer,int[]>smChildCategories = we.getStoredMap(DatabaseName.CHILD_CATEGORIES, true, true) ;
		StoredMap<Integer,Integer> smDepths =  we.getStoredMap(DatabaseName.DEPTHS, true, true) ;
		StoredMap<Statistic,Integer> smStats = we.getStoredMap(DatabaseName.STATS, true, false) ;

		BufferedReader input = new BufferedReader(new FileReader(catLinkFile)) ;

		if (pn == null) pn = new ProgressNotifier(4) ;
		pn.startTask(catLinkFile.length(), "summarizing category links") ;

		long bytesRead = 0 ;
		int linesRead = 0 ;
		String line ;

		int lastChildId = -1 ;
		Vector<Integer> tempParents = null ;


		TIntObjectHashMap<Vector<Integer>> tempChildCategories = new TIntObjectHashMap<Vector<Integer>>() ;
		TIntObjectHashMap<Vector<Integer>> tempChildArticles = new TIntObjectHashMap<Vector<Integer>>() ;



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
						int[] parents = new int[tempParents.size()] ;

						int i= 0 ;
						for (int pid:tempParents) 
							parents[i++] = pid ;
						Arrays.sort(parents) ;

						smParents.put(lastChildId, parents) ;
					}

					tempParents = new Vector<Integer>() ;
					lastChildId = childId ;
				}

				tempParents.add(parentId) ;

				DbPage pd = we.getPageDetails(childId) ;

				if (pd != null) {

					if (pd.getType() == Page.CATEGORY) {
						Vector<Integer> children = tempChildCategories.get(parentId) ;
						if (children == null) 
							children = new Vector<Integer>() ;

						children.add(childId) ;
						tempChildCategories.put(parentId, children) ;
					} else {
						Vector<Integer> children = tempChildArticles.get(parentId) ;
						if (children == null) 
							children = new Vector<Integer>() ;

						children.add(childId) ;
						tempChildArticles.put(parentId, children) ;
					}
				}
				pn.update(bytesRead) ;				
			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}
		}

		//save all stored child categories
		pn.startTask(tempChildCategories.size(), "storing child categories") ;
		TIntObjectIterator<Vector<Integer>> iter = tempChildCategories.iterator() ; 
		for (int i = tempChildCategories.size(); i-- > 0;) {
			iter.advance();

			int parent = iter.key() ;

			int[] children = new int[iter.value().size()] ;
			int j = 0 ;
			for (int child:iter.value()) 
				children[j++] = child ;

			smChildCategories.put(parent, children) ;
			pn.update() ;
		}

		//save all stored child categories
		pn.startTask(tempChildArticles.size(), "storing child articles") ;
		iter = tempChildArticles.iterator() ; 
		for (int i = tempChildArticles.size(); i-- > 0;) {
			iter.advance();

			int parent = iter.key() ;

			int[] children = new int[iter.value().size()] ;
			int j = 0 ;
			for (int child:iter.value()) 
				children[j++] = child ;

			smChildArticles.put(parent, children) ;
			pn.update() ;
		}

		//calculate depths of pages (vertical distance from root category), while we have child relations in memory
		pn.startTask(smStats.get(Statistic.PAGE_COUNT), "summarizing and storing page depths") ;
		int currDepth = 0 ;
		Integer currPage = smStats.get(Statistic.ROOT_ID) ;

		Vector<Integer> currLevel = new Vector<Integer>() ;
		Vector<Integer> nextLevel = new Vector<Integer>() ;

		while (currPage != null) {

			if (smDepths.get(currPage) == null) {
				smDepths.put(currPage, currDepth) ;

				Vector<Integer> childArticles = tempChildArticles.get(currPage) ;
				if (childArticles != null) {
					for(int childArticle:childArticles) {
						if (smDepths.get(childArticle)==null)
							smDepths.put(childArticle, currDepth+1) ;
					}
				}

				Vector<Integer> childCategories = tempChildCategories.get(currPage) ;
				if (childCategories != null) {
					for(int childCategory:childCategories) {
						if (smDepths.get(childCategory)==null)
							nextLevel.add(childCategory) ;
					}
				}
				pn.update();
			}

			if (currLevel.isEmpty()) {
				currLevel = nextLevel ;
				nextLevel = new Vector<Integer>() ;
				currDepth++ ;
			}

			if (currLevel.isEmpty())
				currPage = null ;
			else {
				currPage = currLevel.firstElement();
				currLevel.remove(0) ;
			}
		}

		//free up memory 
		tempChildCategories = null ;
		tempChildArticles = null ;

		smStats.put(Statistic.MAX_DEPTH, currDepth) ;

		System.out.print("Syncing database... ") ;

		smParents = we.getStoredMap(DatabaseName.PARENTS, false, false) ;
		smChildArticles = we.getStoredMap(DatabaseName.CHILD_ARTICLES,false, false) ;
		smChildCategories = we.getStoredMap(DatabaseName.CHILD_CATEGORIES, false, false) ;
		smDepths =  we.getStoredMap(DatabaseName.DEPTHS, false, false) ;
		smStats = we.getStoredMap(DatabaseName.STATS, false, false) ;
		System.out.println("...done.") ;

		input.close();
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

		StoredMap<Integer,String>smPageContent = we.getStoredMap(DatabaseName.PAGE_CONTENT, true, true) ;

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
								smPageContent.put(id, content) ;

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
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		smPageContent = we.getStoredMap(DatabaseName.PAGE_CONTENT, false, false) ;
		System.out.println("...done.") ;

		input.close();
	}

	private void loadAnchors(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File anchorsFile = new File(dataDirectory.getPath() + File.separatorChar + "anchor.csv") ;

		StoredMap<String,DbAnchor> smAnchor = we.getStoredMap(DatabaseName.ANCHOR, true, true) ; 


		if (pn==null) pn = new ProgressNotifier(3) ;



		Pattern p = Pattern.compile("\"(.*?)\",(\\d*),(\\d*),(\\d*),(\\d*)", Pattern.DOTALL) ;

		int passes = 5 ;
		for (int pass=0 ; pass < passes ; pass++) {
			pn.startTask(anchorsFile.length(), "gathering anchor senses (pass " + (pass+1) + " of " + passes + ")") ;

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
					String anchor = m.group(1) ;
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

			//save all stored anchors
			pn.startTask(tempSenses.size(), "storing anchor senses (pass " + (pass+1) + " of " + passes + ")") ;
			final ProgressNotifier pn2 = pn ;
			final StoredMap<String, DbAnchor> sm = smAnchor ;

			tempSenses.forEachEntry(new TObjectObjectProcedure<String, Vector<DbSense>>() {

				public boolean execute(String anchor, Vector<DbSense> senses) {
					DbSense[] sortedSenses = senses.toArray(new DbSense[senses.size()]) ;
					Arrays.sort(sortedSenses) ;

					//set counts as 0 for now
					DbAnchor anch = new DbAnchor(0,0,(long)0,0,sortedSenses) ;

					sm.put(anchor, anch) ;
					pn2.update() ;
					return true ;
				}
			}) ;

			tempSenses = null ;

			we.cleanLog() ;
			we.checkpoint(null) ;
			we.evictMemory() ;

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
				String text = m.group(1) ;
				int linkCountTotal = Integer.parseInt(m.group(2)) ;
				int linkCountDistinct = Integer.parseInt(m.group(3)) ;
				long occCountTotal = Long.parseLong(m.group(4)) ;
				int occCountDistinct = Integer.parseInt(m.group(5)) ;

				DbAnchor anchorToStore = new DbAnchor(linkCountTotal, linkCountDistinct, occCountTotal, occCountDistinct, null) ;

				DbAnchor storedAnchor = smAnchor.get(text) ;
				if (storedAnchor != null) 
					anchorToStore.mergeWith(storedAnchor) ;

				smAnchor.put(text, anchorToStore) ;
			} else {
				throw new ParseException("\"" + line + "\" does not match expected format", linesRead) ;
			}

			pn.update(bytesRead) ;
		}


		System.out.print("Syncing database... ") ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		smAnchor = we.getStoredMap(DatabaseName.ANCHOR, false, false) ; 
		System.out.println("...done.") ;

		input.close();
	}


	private void loadAnchorTexts(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File anchorsFile = new File(dataDirectory.getPath() + File.separatorChar + "anchor.csv") ;

		StoredMap<Integer,DbAnchorText[]> smAnchorTexts = we.getStoredMap(DatabaseName.ANCHOR_TEXTS, true, true) ;



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
					String anchor = m.group(1) ;
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

			final StoredMap sm = smAnchorTexts ;
			final ProgressNotifier pn2 = pn ;

			tempAnchorTexts.forEachEntry(new TObjectObjectProcedure<Integer,Vector<DbAnchorText>>() {
				public boolean execute(Integer destination, Vector<DbAnchorText> anchorTexts) {

					DbAnchorText[] sortedAnchorTexts = anchorTexts.toArray(new DbAnchorText[anchorTexts.size()]) ;
					Arrays.sort(sortedAnchorTexts) ;

					sm.put(destination, sortedAnchorTexts) ;
					pn2.update() ;
					return true ;
				}
			} ) ;

			tempAnchorTexts = null ;
			we.cleanLog() ;
			we.checkpoint(null) ;
			we.evictMemory() ;

			System.gc();
			input.close();
		}

		System.out.print("Syncing database... ") ;

		smAnchorTexts = we.getStoredMap(DatabaseName.ANCHOR_TEXTS, false, false) ;
		System.out.println("...done.") ;


	}

	private void loadLinkCounts(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File pageLinksFile = new File(dataDirectory.getPath() + File.separatorChar + "pagelink.csv") ;

		StoredMap<Integer,int[]> smLinkCounts = we.getStoredMap(DatabaseName.LINK_COUNTS, true, true) ;

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
			smLinkCounts.put(iter.key(), iter.value()) ;
			pn.update() ;
		}
		tempLinkCounts = null ;




		System.out.print("Syncing database... ") ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		smLinkCounts = we.getStoredMap(DatabaseName.LINK_COUNTS, false, false) ;
		System.out.println("...done.") ;

		input.close();
	}

	private void loadTranslations(File dataDirectory, ProgressNotifier pn) throws IOException, ParseException, DatabaseException {

		File translationsFile = new File(dataDirectory.getPath() + File.separatorChar + "translation.csv") ;

		StoredMap<Integer,String[]> smTranslations = we.getStoredMap(DatabaseName.TRANSLATIONS, true, true) ; 

		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(translationsFile), "UTF-8")) ;

		if (pn==null) pn = new ProgressNotifier(1) ;
		pn.startTask(translationsFile.length(), "loading translations") ;

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
						if (!tempTranslations.isEmpty())
							smTranslations.put(lastId, tempTranslations.toArray(new String[tempTranslations.size()])) ;
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
		if (!tempTranslations.isEmpty())
			smTranslations.put(lastId, tempTranslations.toArray(new String[tempTranslations.size()])) ;

		System.out.print("Syncing database... ") ;
		we.cleanLog() ;
		we.checkpoint(null) ;
		we.evictMemory() ;
		smTranslations = we.getStoredMap(DatabaseName.TRANSLATIONS, false, false) ;
		System.out.println("...done.") ;

		input.close();
	}


	private void indexArticles() throws IOException, ParseException {

		StandardAnalyzer analyzer = new StandardAnalyzer();
		FSDirectory index = FSDirectory.getDirectory(we.indexDir) ;

		
		IndexWriter w = new IndexWriter(index, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);

		ProgressNotifier pn = new ProgressNotifier(we.getStatisticValue(Statistic.PAGE_COUNT), "Indexing articles for full-text search") ;
		pn.setMinReportProgress(0.0001) ;
		
		MarkupStripper stripper = new MarkupStripper() ;
		String[] unwantedSections = {"see also", "references", "further sources", "further reading", "footnotes", "external links", "bibliography", "notes", "notes and references", "other websites"} ;

		Iterator<Integer> iter = we.getPageIdIterator() ;
		while (iter.hasNext()) {

			int id = iter.next() ;
			DbPage page = we.getPageDetails(id) ;

			if (page.getType() == Page.ARTICLE || page.getType() == Page.DISAMBIGUATION) {

				//System.out.println(id + ":" + page.getTitle()) ;

				String markup = we.getPageContent(id) ;

				if (markup == null) {
					System.out.println("no content") ;
				} else {
					//System.out.println(markup.length()) ;
					
					markup = stripper.stripToPlainText(markup, null) ;
					//markup = stripper.stripSections(markup, unwantedSections, null) ;

					Document doc = new Document();
					doc.add(new Field("content", markup, Field.Store.NO, Field.Index.ANALYZED));
					doc.add(new Field("id", String.valueOf(id), Field.Store.YES, Field.Index.NO));

					w.addDocument(doc);
				}
				pn.update() ;
			}
		}
		w.close();

		try {
			Query q = new QueryParser("content", analyzer).parse("\"New Zealand\"");
			//q.rewrite() ;

			// 3. search
			int hitsPerPage = 1000;
			IndexSearcher searcher = new IndexSearcher(index);

			//Filter filter = new DuplicateFilter("headline") ;

			TopDocCollector collector = new TopDocCollector(hitsPerPage);
			searcher.search(q, collector);

			ScoreDoc[] hits = collector.topDocs().scoreDocs;

			// 4. display results
			System.out.println("Found " + hits.length + " hits.");
			for(int i=0;i<Math.min(hits.length, 10);++i) {
				int docId = hits[i].doc;

				Document d = searcher.doc(docId);

				Integer id = Integer.parseInt(d.get("id")) ;
				DbPage page = we.getPageDetails(id) ;


				System.out.println((i + 1) + ". " + id + ":" + page.getTitle());
				System.out.println(hits[i].score) ;
			}

			// searcher can only be closed when there
			// is no need to access the documents any more.
			searcher.close();
		} catch (Exception e) {
			e.printStackTrace() ;
		}

		index.close();
	}










	public static void main(String[] args) throws Exception {

		File berkeleyDir = new File("/Users/dmilne/Research/wikipedia/databases/en/20090822") ;
		File luceneDir = new File("/Users/dmilne/Research/wikipedia/indexes/en/20090822") ;

		File dumpDir = new File("/Users/dmilne/Research/wikipedia/data/en/20090822") ;

		WikipediaEnvironmentLoader loader = new WikipediaEnvironmentLoader(berkeleyDir, luceneDir) ;

		loader.load(dumpDir, true) ; 
	}

}
