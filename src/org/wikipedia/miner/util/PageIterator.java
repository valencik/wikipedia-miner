/*
 *    PageIterator.java
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

package org.wikipedia.miner.util;

import java.util.* ;
import java.io.File;
import java.sql.* ;

import org.wikipedia.miner.db.DbPageIterator;
import org.wikipedia.miner.db.WikipediaEnvironment;
import org.wikipedia.miner.model.* ;

import com.sleepycat.je.DatabaseException;

/**
 * @author David Milne
 * 
 * Provides efficient iteration over the pages in Wikipedia
 */
public class PageIterator implements Iterator<Page> {

	DbPageIterator iter ;
	
	Page nextPage = null ;
	
	short pageType = -1 ;

	/**
	 * Creates an iterator that will loop through all pages in Wikipedia.
	 * 
	 * @param database an active (connected) Wikipedia database.
	 */
	public PageIterator(WikipediaEnvironment environment) throws DatabaseException {
		iter = new DbPageIterator(environment) ;

		queueNext() ;
	}

	/**
	 * Creates an iterator that will loop through all pages of the given type in Wikipedia.
	 * 
	 * @param database an active (connected) Wikipedia database.
	 * @param pageType the type of page to restrict the iterator to (ARTICLE, CATEGORY, REDIRECT or DISAMBIGUATION_PAGE)
	 * @throws SQLException if there is a problem with the Wikipedia database.
	 */
	public PageIterator(WikipediaEnvironment environment, short pageType) throws DatabaseException {
		iter = new DbPageIterator(environment) ;
		this.pageType = pageType ;
		
		queueNext() ;
	}

	public boolean hasNext() {
		return !(nextPage == null) ;
	}

	public void remove() {
		throw new UnsupportedOperationException() ;
	}

	public Page next() {

		if (nextPage == null)
			throw new NoSuchElementException() ;

		Page p = nextPage ;
		queueNext() ;
		
		return p ;
	}
	
	private void queueNext() {
		nextPage=iter.next() ;
		
		if (pageType >=0) {
			while (nextPage.getType() != pageType) {
				try {
					nextPage = iter.next();
				} catch (NoSuchElementException e) {
					nextPage = null ;
					break ;
				}
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		File berkeleyDir = new File("/Users/dmilne/Research/wikipedia/databases/simple/20080620") ;
		File luceneDir = new File("/Users/dmilne/Research/wikipedia/indexes/simple/20080620") ;
		
		Wikipedia wikipedia = new Wikipedia(berkeleyDir, luceneDir) ;
		
		Iterator<Page> iter = wikipedia.getPageIterator(Page.DISAMBIGUATION) ;
		while (iter.hasNext()) {
			Page p = iter.next() ;
			System.out.println(p + "," + p.getType()) ;
		}
		
	}
}
