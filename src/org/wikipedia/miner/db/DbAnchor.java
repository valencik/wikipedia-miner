package org.wikipedia.miner.db;

import gnu.trove.THashMap;

import java.util.Arrays;

public class DbAnchor {

	private int totalLinks ;
	private int distinctLinks ;
	
	private long totalReferences ;
	private int distinctReferences ;
	
	private DbSense[] senses ;
	
	public DbAnchor(int totalLinks, int distinctLinks, long totalReferences, int distinctReferences, DbSense[] senses) {
		
		this.totalLinks = totalLinks ;
		this.distinctLinks = distinctLinks ;
		this.totalReferences = totalReferences ;
		this.distinctReferences = distinctReferences ;
		this.senses = senses ;
	}
	
	public int getTotalLinks() {
		return totalLinks;
	}

	public int getDistinctLinks() {
		return distinctLinks;
	}

	public long getTotalReferences() {
		return totalReferences;
	}

	public int getDistinctReferences() {
		return distinctReferences;
	}

	public DbSense[] getSenses() {
		return senses;
	}
	
	public void mergeWith(DbAnchor anchor) {
		
		THashMap<Integer,DbSense> senseHash = new THashMap<Integer,DbSense>() ;
		
		if (senses != null) {
			for (DbSense s:senses) 
				senseHash.put(s.getDestination(), s) ;
		}
		
		if (anchor.senses != null) {
			for (DbSense s1:anchor.senses) {
				
				DbSense s2 = senseHash.get(s1.getDestination()) ;
				if (s2 == null) {
					senseHash.put(s1.getDestination(), s1) ;
				} else {
					DbSense s3 = new DbSense(s1.getDestination(), s1.getTotalCount() + s2.getTotalCount(), s1.getDistinctCount() + s2.getDistinctCount(), (s1.isTitle() || s2.isTitle()), (s1.isRedirect() || s2.isRedirect())) ;
					senseHash.put(s3.getDestination(), s3) ;
				}
			}
		}
		
		DbSense[] mergedSenses = null;
		
		if (!senseHash.isEmpty()) {
		
			mergedSenses = new DbSense[senseHash.size()] ;
			int i=0 ;
			for (DbSense s: senseHash.values()) 
				mergedSenses[i++] = s ;
			
			Arrays.sort(mergedSenses) ;
		}
			
		totalLinks = (short)(totalLinks + anchor.totalLinks) ;
		distinctLinks = (short)(distinctLinks + anchor.distinctLinks) ;
		totalReferences = totalReferences + anchor.totalReferences ;
		distinctReferences = distinctReferences + anchor.distinctReferences ;
		senses = mergedSenses ;
	}
	
	
	
}
