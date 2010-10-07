package org.wikipedia.miner.db;

import gnu.trove.TIntHash;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.hadoop.record.CsvRecordInput;
import org.apache.log4j.Logger;
import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.db.WEnvironment.StatisticName;
import org.wikipedia.miner.db.struct.*;
import org.wikipedia.miner.model.Page.PageType;
import org.wikipedia.miner.util.WikipediaConfiguration;
import org.wikipedia.miner.util.text.TextProcessor;


import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.StringBinding;

/**
 * A factory for creating WDatabases of various types
 */
public class WDatabaseFactory {

	WEnvironment env ;
	
	/**
	 * Creates a new WDatabaseFactory for the given WEnvironment
	 * 
	 * @param env a WEnvironment
	 */
	public WDatabaseFactory(WEnvironment env) {
		
		this.env = env ;
	}

	/**
	 * @return a database associating page ids with the title, type and generality of the page. 
	 */
	public WDatabase<Integer, DbPage> buildPageDatabase() {

		RecordBinding<DbPage> keyBinding = new RecordBinding<DbPage>() {
			public DbPage createRecordInstance() {
				return new DbPage() ;
			}
		} ;

		return new IntObjectDatabase<DbPage>(
				env, 
				DatabaseType.page,
				keyBinding
		) {
			@Override
			public WEntry<Integer,DbPage> deserialiseCsvRecord(CsvRecordInput record) throws IOException {
				Integer id = record.readInt(null) ;
				
				DbPage p = new DbPage() ;
				p.deserialize(record) ;
				
				return new WEntry<Integer,DbPage>(id, p) ;
			}

			@Override
			public DbPage filterCacheEntry(
					WEntry<Integer, DbPage> e, 
					WikipediaConfiguration conf,
					TIntHash validIds
			) {

				PageType pageType = PageType.values()[e.getValue().getType()] ;

				if (validIds == null || validIds.contains(e.getKey()) || pageType == PageType.category || pageType==PageType.redirect) 
					return e.getValue() ;
				else
					return null ;
			}
		};
	}
	
	/**
	 * @param type either {@link DatabaseType#articlesByTitle} or {@link DatabaseType#categoriesByTitle}.
	 * @return a database associating either article or category titles with their ids.
	 */
	public WDatabase<String,Integer> buildTitleDatabase(DatabaseType type) {
		
		if (type != DatabaseType.articlesByTitle && type != DatabaseType.categoriesByTitle) 
			throw new IllegalArgumentException("type must be either DatabaseType.articlesByTitle or DatabaseType.categoriesByTitle") ;
		
			
		return new WDatabase<String,Integer>(
				env, 
				type, 
				new StringBinding(),
				new IntegerBinding()
		){

			@Override
			public WEntry<String, Integer> deserialiseCsvRecord(
					CsvRecordInput record) throws IOException {
				Integer id = record.readInt(null) ;
				
				DbPage p = new DbPage() ;
				p.deserialize(record) ;
				
				PageType pageType = PageType.values()[p.getType()];
				DatabaseType dbType = getType() ;
				
				if (dbType == DatabaseType.articlesByTitle && (pageType != PageType.article && pageType != PageType.disambiguation && pageType != PageType.redirect))
					return null ;
				
				if (dbType == DatabaseType.categoriesByTitle && pageType != PageType.category)
					return null ;
				
				return new WEntry<String,Integer>(p.getTitle(), id) ;
			}

			@Override
			public Integer filterCacheEntry(
					WEntry<String, Integer> e, WikipediaConfiguration conf,
					TIntHash validIds) {
				
				if (getType() == DatabaseType.articlesByTitle) {
					if (validIds != null && !validIds.contains(e.getValue()))
						return null ;
				}
				
				return e.getValue();
			}
		};	
	}

	/**
	 * @return a database associating String labels with the statistics about the articles (senses) these labels could refer to 
	 */
	public LabelDatabase buildLabelDatabase() {
		return new LabelDatabase(env) ;
	}

	/**
	 * @param tp a text processor that should be applied to string labels before indexing and searching
	 * @return a database associating String labels with the statistics about the articles (senses) these labels could refer to 
	 */
	public LabelDatabase buildLabelDatabase(TextProcessor tp) {

		if (tp == null) 
			throw new IllegalArgumentException("text processor must not be null") ;

		return new LabelDatabase(env, tp) ;
	}

	/**
	 * @return a database associating Integer page ids with the labels used to refer to that page
	 */
	public WDatabase<Integer,DbLabelForPageList> buildPageLabelDatabase() {

		RecordBinding<DbLabelForPageList> keyBinding = new RecordBinding<DbLabelForPageList>() {
			public DbLabelForPageList createRecordInstance() {
				return new DbLabelForPageList() ;
			}
		} ;

		return new IntObjectDatabase<DbLabelForPageList>(
				env, 
				DatabaseType.pageLabel, 
				keyBinding
		) {

			@Override
			public WEntry<Integer,DbLabelForPageList> deserialiseCsvRecord(CsvRecordInput record) throws IOException {

				Integer id = record.readInt(null) ;

				DbLabelForPageList labels = new DbLabelForPageList() ;
				labels.deserialize(record) ;
				
				return new WEntry<Integer,DbLabelForPageList>(id, labels) ;
			}

			@Override
			public DbLabelForPageList filterCacheEntry(WEntry<Integer,DbLabelForPageList> e, WikipediaConfiguration conf, TIntHash validIds) {
				
				if (validIds != null && !validIds.contains(e.getKey()))
					return null ;
				
				return e.getValue();
			}
		} ;
	}

	/**
	 * @param type either {@link DatabaseType#pageLinksIn} or {@link DatabaseType#pageLinksOut}.
	 * @return a database associating Integer ids with the ids of articles it links to or that link to it, and the sentence indexes where these links are found
	 */
	public WDatabase<Integer, DbLinkLocationList> buildPageLinkDatabase(DatabaseType type) {

		if (type != DatabaseType.pageLinksIn && type != DatabaseType.pageLinksOut)
			throw new IllegalArgumentException("type must be either DatabaseType.pageLinksIn or DatabaseType.pageLinksOut") ;
		
		RecordBinding<DbLinkLocationList> keyBinding = new RecordBinding<DbLinkLocationList>() {
			public DbLinkLocationList createRecordInstance() {
				return new DbLinkLocationList() ;
			}
		} ;

		return new IntObjectDatabase<DbLinkLocationList>(
				env, 
				type, 
				keyBinding
		) {

			@Override
			public WEntry<Integer, DbLinkLocationList> deserialiseCsvRecord(CsvRecordInput record) throws IOException {

				Integer id = record.readInt(null) ;

				DbLinkLocationList l = new DbLinkLocationList() ;
				l.deserialize(record) ;

				return new WEntry<Integer, DbLinkLocationList>(id, l) ;
			}

			@Override
			public DbLinkLocationList filterCacheEntry(
					WEntry<Integer, DbLinkLocationList> e,
					WikipediaConfiguration conf, TIntHash validIds) {

				int id = e.getKey() ;
				DbLinkLocationList links = e.getValue() ;

				if (validIds != null && !validIds.contains(id))
					return null ;

				ArrayList<DbLinkLocation> newLinks = new ArrayList<DbLinkLocation>() ;

				for (DbLinkLocation ll:links.getLinkLocations()) {
					if (validIds != null && !validIds.contains(ll.getLinkId()))
						continue ;

					newLinks.add(ll) ;
				}

				if (newLinks.size() == 0)
					return null ;

				links.setLinkLocations(newLinks) ;

				return links ;
			}

		} ;
	}

	
	/**
	 * @param type {@link DatabaseType#categoryParents}, {@link DatabaseType#articleParents}, {@link DatabaseType#childCategories},{@link DatabaseType#childArticles}, {@link DatabaseType#redirectSourcesByTarget}, {@link DatabaseType#sentenceSplits}
	 * @return see the description of the appropriate DatabaseType
	 */
	public WDatabase<Integer,DbIdList> buildIntIntListDatabase(final DatabaseType type) {

		switch (type) {
		case categoryParents:
		case articleParents:
		case childCategories:
		case childArticles:
		case redirectSourcesByTarget:
		case sentenceSplits:
			break ;
		default: 
			throw new IllegalArgumentException(type.name() + " is not a valid DatabaseType for IntIntListDatabase") ;
		}

		RecordBinding<DbIdList> keyBinding = new RecordBinding<DbIdList>() {
			public DbIdList createRecordInstance() {
				return new DbIdList() ;
			}
		} ;

		return new IntObjectDatabase<DbIdList>(
				env, 
				type, 
				keyBinding
		) {
			@Override
			public WEntry<Integer, DbIdList> deserialiseCsvRecord(CsvRecordInput record) throws IOException {

				Integer k = record.readInt(null) ;
				
				DbIdList v = new DbIdList() ;
				v.deserialize(record) ;
				
				return new WEntry<Integer, DbIdList>(k,v) ;
			}

			@Override
			public DbIdList filterCacheEntry(WEntry<Integer,DbIdList> e, WikipediaConfiguration conf, TIntHash validIds) {
				int key = e.getKey() ;
				ArrayList<Integer> values = e.getValue().getIds() ;
				
				ArrayList<Integer> newValues = null ;
				
				switch (type) {
				
				case articleParents :
				case sentenceSplits :
				case redirectSourcesByTarget :
					//only cache if key is valid article
					if (validIds == null || validIds.contains(key))
						newValues = values ;
					break ;
				case childArticles :
					//only cache values that are valid articles
					newValues = new ArrayList<Integer>() ;
					for (int v:values) {
						if (validIds == null || validIds.contains(v))
							newValues.add(v) ;
					}
				default :
					//cache everything
					newValues = values ;
				}
				
				if (newValues == null || newValues.size() == 0)
					return null ;
			
				return new DbIdList(newValues) ;
			}
		} ;
	}
	
	/**
	 * @return a database associating integer id of redirect with the id of its target
	 */
	public WDatabase<Integer,Integer> buildRedirectTargetBySourceDatabase() {
		
		return new IntObjectDatabase<Integer>(
				env, 
				DatabaseType.redirectTargetBySource, 
				new IntegerBinding()
		) {

			@Override
			public WEntry<Integer, Integer> deserialiseCsvRecord(
					CsvRecordInput record) throws IOException {
				int k = record.readInt(null) ;
				int v = record.readInt(null) ;
				
				return new WEntry<Integer, Integer>(k,v) ;
			}

			@Override
			public Integer filterCacheEntry(
					WEntry<Integer, Integer> e, WikipediaConfiguration conf,
					TIntHash validIds) {
				
				if (validIds != null && !validIds.contains(e.getValue()))
					return null ; 
				
				return e.getValue();
				
			}
		} ;
	}
	
	/**
	 * @return a database associating integer {@link WEnvironment.StatisticName#ordinal()} with the value relevant to this statistic.
	 */
	public IntObjectDatabase<Long> buildStatisticsDatabase() {
		
		return new IntObjectDatabase<Long>(
				env, 
				DatabaseType.statistics, 
				new LongBinding()
		) {

			@Override
			public WEntry<Integer, Long> deserialiseCsvRecord(
					CsvRecordInput record) throws IOException {
				
				String statName = record.readString(null) ;
				Long v = record.readLong(null) ;
				
				Integer k = null;
				
				try {
					k = StatisticName.valueOf(statName).ordinal() ;
				} catch (Exception e) {
					Logger.getLogger(WDatabaseFactory.class).warn("Ignoring unknown statistic: " + statName) ;
					return null ;
				}
				return new WEntry<Integer, Long>(k,v) ;
			}

			@Override
			public Long filterCacheEntry(
					WEntry<Integer, Long> e, WikipediaConfiguration conf,
					TIntHash validIds) {
				return e.getValue() ;
			}
		} ;
	}

	
	/**
	 * @return a database associating integer id of page with its content, in mediawiki markup format
	 */
	public WDatabase<Integer,String> buildMarkupDatabase() {
		return new MarkupDatabase(env) ;
	}
}



