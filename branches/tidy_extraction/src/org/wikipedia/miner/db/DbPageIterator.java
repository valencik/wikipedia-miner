package org.wikipedia.miner.db;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.wikipedia.miner.db.WikipediaEnvironment.DatabaseName;
import org.wikipedia.miner.model.Page;


import com.sleepycat.je.*;



public class DbPageIterator implements Iterator<Page> {

	WikipediaEnvironment environment ;
	Cursor cursor ;
	Page nextPage ;
	
	DatabaseEntry key ;
	DatabaseEntry value ;

	/**
	 * Creates an iterator that will loop through all pages in Wikipedia.
	 * 
	 * @param database an active (connected) Wikipedia database.
	 */
	public DbPageIterator(WikipediaEnvironment environment) throws DatabaseException{
		
		this.environment = environment ;
		
		Database dbPage = environment.getDatabase(DatabaseName.PAGE_DETAILS, false, false) ;
		cursor = dbPage.openCursor(null, null) ;

		queueNext() ;	
	}

	public boolean hasNext() {
		return nextPage != null;
	}

	public void remove() {
		throw new UnsupportedOperationException() ;
	}

	public Page next()  {
		try {
			
			if (nextPage == null)
				throw new NoSuchElementException() ;
	
			Page p = nextPage ;
			queueNext() ;
			
			return p ;
		
		} catch (DatabaseException e) {
			throw new NoSuchElementException() ;
		}
	}
	
	private void queueNext() throws DatabaseException {
		
		if (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
			int id = environment.intBinding.entryToObject(key) ;
			DbPage p = environment.pageDetailsBinding.entryToObject(value) ;
			nextPage = Page.createPage(environment, id, p.getTitle(), p.getType()) ;
		}
	}
	
}
