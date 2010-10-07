package org.wikipedia.miner.model;

import gnu.trove.TIntHashSet;

import org.wikipedia.miner.db.struct.DbPage;
import org.wikipedia.miner.db.WEnvironment;

/**
 * This class represents redirects in Wikipedia; the links that have been defined to connect synonyms to the correct article
 * (i.e <em>Farming</em> redirects to <em>Agriculture</em>).  
 * It is intended to contain all properties and methods that are relevant for a redirect. 
 * 
 * @author David Milne
 */
public class Redirect extends Page {
	
	/**
	 * Initialises a newly created Redirect so that it represents the article given by <em>id</em>.
	 * 
	 * @param env	an active WikipediaEnvironment
	 * @param id	the unique identifier of the article
	 */
	public Redirect(WEnvironment env, int id) {
		super(env, id) ;
	}
	
	protected Redirect(WEnvironment env, int id, DbPage pd) {
		super(env, id, pd) ;
	}
	
	
	/**
	 * Returns the Article that this redirect points to. This will continue following redirects until it gets to an article 
	 * (so it deals with double redirects). If a dead-end or loop of redirects is encountered, null is returned
	 * 
	 * @return	the equivalent Article for this redirect.
	 */	//TODO: should just resolve double redirects during extraction.
	public Article getTarget() {

		Article target = null;

		int currId = id ;

		TIntHashSet redirectsFollowed = new TIntHashSet() ;

		while (target == null && !redirectsFollowed.contains(currId)) {
			redirectsFollowed.add(currId) ;

			Integer targetId = env.getDbRedirectTargetBySource().retrieve(currId) ;

			if (targetId == null) 
				return null ;
			
			target = (Article)Page.createPage(env, targetId) ;
			
			if (target.getType() == PageType.redirect)
				currId = targetId ;
			else
				return target ;
		}

		return target ;		
	}
	
}

