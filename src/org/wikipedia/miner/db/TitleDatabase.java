package org.wikipedia.miner.db;

import gnu.trove.TIntHash;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;

import org.apache.hadoop.record.CsvRecordInput;
import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.db.struct.DbPage;
import org.wikipedia.miner.model.Page.PageType;
import org.wikipedia.miner.util.ProgressTracker;
import org.wikipedia.miner.util.WikipediaConfiguration;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;

public class TitleDatabase extends WDatabase<String,Integer>{

	public TitleDatabase(WEnvironment env, DatabaseType type) {
		super(env, type, new StringBinding(), new IntegerBinding());

		if (type != DatabaseType.articlesByTitle && type != DatabaseType.categoriesByTitle) 
			throw new IllegalArgumentException("type must be either DatabaseType.articlesByTitle or DatabaseType.categoriesByTitle") ;
	}

	@Override
	public WEntry<String, Integer> deserialiseCsvRecord(CsvRecordInput record)
	throws IOException {
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
	public Integer filterCacheEntry(WEntry<String, Integer> e,
			WikipediaConfiguration conf, TIntHash validIds) {

		if (getType() == DatabaseType.articlesByTitle) {
			if (validIds != null && !validIds.contains(e.getValue()))
				return null ;
		}

		return e.getValue();
	}

	@Override
	public void loadFromCsvFile(File dataFile, boolean overwrite, ProgressTracker tracker) throws IOException  {

		if (exists() && !overwrite)
			return ;

		if (tracker == null) tracker = new ProgressTracker(1, WDatabase.class) ;
		tracker.startTask(dataFile.length(), "Loading " + getName() + " database") ;

		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(dataFile), "UTF-8")) ;

		long bytesRead = 0 ;
		int lineNum = 0 ;

		TreeMap<String, Integer> tmp = new TreeMap<String, Integer>() ;

		String line ;
		while ((line=input.readLine()) != null) {
			bytesRead = bytesRead + line.length() + 1 ;
			lineNum++ ;

			CsvRecordInput cri = new CsvRecordInput(new ByteArrayInputStream((line + "\n").getBytes("UTF-8"))) ;

			WEntry<String,Integer> entry = deserialiseCsvRecord(cri) ;

			if (entry != null) {
				tmp.put(entry.getKey(), entry.getValue()) ;
				tracker.update(bytesRead) ;
			}
		}

		
		Database db = getDatabase(false) ;

		for (Map.Entry<String, Integer> entry: tmp.entrySet()) {

			DatabaseEntry k = new DatabaseEntry() ;
			keyBinding.objectToEntry(entry.getKey(), k) ;

			DatabaseEntry v = new DatabaseEntry() ;
			valueBinding.objectToEntry(entry.getValue(), v) ;

			db.put(null, k, v) ;
			//TODO: progress update
		}

		input.close();

		env.cleanAndCheckpoint() ;
		getDatabase(true) ;
	}

}
