package org.wikipedia.miner.db;

public class DbAnchorText implements Comparable<DbAnchorText> {

	private String text ;
	private int totalCount ;
	private int distinctCount ;
	
	public DbAnchorText(String text, int totalCount, int distinctCount)  {
		
		this.text = text ;
		this.totalCount = totalCount ;
		this.distinctCount = distinctCount ;
		
	}
	
	public String getText() {
		return text ;
	}
	
	public int getTotalCount() {
		return totalCount ;
	}
	
	public int getDistinctCount() {
		return distinctCount ;
	}
	
	public int compareTo(DbAnchorText at) {
		
		int c = new Integer(at.distinctCount).compareTo(distinctCount) ;
		if (c!=0) return c ;
		
		c = new Integer(at.totalCount).compareTo(totalCount) ;
		if (c!=0) return c ;
		
		return text.compareTo(at.text) ;
	}
	
}
