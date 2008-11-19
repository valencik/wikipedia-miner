/*
 *    SensePage.java
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


/**
 * This class represents an entry in a disambiguation page: a sense for which the 
 * 
 * @author David Milne
 */
public class SensePage extends Page{

	private int index ;
	private String scopeNote ;
	
	public SensePage(WikipediaDatabase database, int id, String title, int type, int index, String scopeNote) {
		super(database, id, title, type) ;
		this.index = index ;
		this.scopeNote = scopeNote ;
	}
	
	/** 
	 * Returns the position at which this sense was found on the disambiguation page (starting from 1). More obvious, well known senses typically 
	 * occur earlier and have smaller indexes.
	 * 
	 * @return the position at which this sense was found
	 */
	public int getIndex() {
		return index ;
	}
	
	/** 
	 * Returns the supportive text that was used to describe this sense on the disambguation page.  
	 * 
	 * @return descriptive text.
	 */
	public String getScopeNote() {
		return scopeNote ;
	}
	
	/** 
	 * Returns the article identified by this sense.   
	 * 
	 * @return the article.
	 */
	public Article getArticle() {
		if (type == ARTICLE)
			return new Article(database, id, title) ;
		
		if (type == DISAMBIGUATION)
			return new Disambiguation(database, id, title) ;
		
		return null ;		
	}
}

