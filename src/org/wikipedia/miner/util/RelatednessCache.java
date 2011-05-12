/*
 *    RelatednessCache.java
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

import org.wikipedia.miner.comparison.ArticleComparer;
import org.wikipedia.miner.model.Article;

import gnu.trove.* ;

/**
 * This class caches the results of calculating relatedness measures between articles; 
 * If all article comparisons are performed via this class, then no calculations will be repeated.
 */
public class RelatednessCache {

	TLongDoubleHashMap cachedRelatedness ;
	ArticleComparer comparer ;
	
	/**
	 * Initialises the relatedness cache, where relatedness will be measured using the given {@link  ArticleComparer}.
	 *  
	 * @param comparer the comparer to use. 
	 */
	public RelatednessCache(ArticleComparer comparer) {
		cachedRelatedness = new TLongDoubleHashMap() ;
		this.comparer = comparer ;
	}
	
	
	/**
	 * Calculates (or retrieves) the semantic relatedness of two articles. 
	 * The result will be identical to that returned by {@link ArticleComparer#getRelatedness(Article, Article)}
	 * 
	 * @param art1 
	 * @param art2
	 * @return the semantic relatedness of art1 and art2
	 */
	public double getRelatedness(Article art1, Article art2) throws Exception {
		
		//generate unique key for this pair
		long min = Math.min(art1.getId(), art2.getId()) ;
		long max = Math.max(art1.getId(), art2.getId()) ;
		long key = min + (max << 30) ;
		
		double relatedness ;
				
		if (!cachedRelatedness.containsKey(key)) {		
			relatedness = comparer.getRelatedness(art1, art2) ;		
			cachedRelatedness.put(key, relatedness) ;
		} else {
			relatedness = cachedRelatedness.get(key) ;
		}
		
		//System.out.println(art1 + " vs. " + art2 + ", " + relatedness) ;
		return relatedness ;
	}	
}
