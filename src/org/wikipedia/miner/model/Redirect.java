/*
 *    Redirect.java
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

import org.wikipedia.miner.db.*;

import gnu.trove.TIntHashSet;

import java.util.*;

/**
 * This class represents redirects in Wikipedia; the links that have been defined to connect synonyms to the correct article
 * (i.e <em>Farming</em> redirects to <em>Agriculture</em>).  
 * It is intended to contain all properties and methods that are relevant for a redirect. 
 * 
 * @author David Milne
 */
public class Redirect extends Page {

	/**
	 * Initializes a newly created Redirect so that it represents the page given by <em>id</em> and <em>title</em>.
	 * 
	 * This is the most efficient constructor as no database lookup is required.
	 * 
	 * @param database	an active WikipediaDatabase 
	 * @param id	the unique identifier of the redirect
	 * @param title	the (case dependent) title of the redirect
	 */
	public Redirect(WikipediaEnvironment environment, int id, String title) {
		super(environment, id, title, REDIRECT) ;
	}

	/**
	 * Initializes a newly created Redirect so that it represents the redirect given by <em>id</em>.
	 * 
	 * @param database	an active WikipediaDatabase
	 * @param id	the unique identifier of the redirect
	 */
	public Redirect(WikipediaEnvironment environment, int id) {
		super(environment, id) ;
	}

	/**
	 * Returns the Article that this redirect points to. This will continue following redirects until it gets to an article 
	 * (so it deals with double redirects). If a dead-end or loop of redirects is encountered, null is returned
	 * 
	 * @return	the equivalent Article for this redirect.
	 */	
	public Article getTarget() {

		Article target = null;

		int currId = id ;

		TIntHashSet redirectsFollowed = new TIntHashSet() ;

		while (target == null && !redirectsFollowed.contains(currId)) {
			redirectsFollowed.add(currId) ;

			Integer targetId = environment.getRedirectTarget(currId) ;

			if (targetId == null) 
				return null ;

			DbPage pd = environment.getPageDetails(currId) ;

			switch(pd.getType()) {	

			case ARTICLE: 
				target = new Article(environment, targetId, pd.getTitle()) ;
				break ;
			case REDIRECT:
				currId = targetId ; 
				break ;
			case DISAMBIGUATION:
				target = new Disambiguation(environment, targetId, pd.getTitle()) ;
				break ;		
			}
		}

		return target ;		
	}
}
