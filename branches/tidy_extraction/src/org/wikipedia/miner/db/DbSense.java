package org.wikipedia.miner.db;

public class DbSense implements Comparable<DbSense> {

	private int destination ;
	private int totalCount ;
	private int distinctCount ;
	private boolean isRedirect ;
	private boolean isTitle ;
	
	public DbSense(int destination, int totalCount, int distinctCount, boolean isTitle, boolean isRedirect)  {
		this.destination = destination ;
		this.totalCount = totalCount ;
		this.distinctCount = distinctCount ;
		this.isRedirect = isRedirect ;
		this.isTitle = isTitle ;
	}
	
	public Integer getDestination() {
		return destination ;
	}
	
	public Integer getTotalCount() {
		return totalCount ;
	}
	
	public Integer getDistinctCount() {
		return distinctCount ;
	}
	
	public boolean isRedirect() {
		return isRedirect ;
	}
	
	public boolean isTitle() {
		return isTitle ;
	}
	
	public int compareTo(DbSense s) {
		
		int c = new Integer(s.distinctCount).compareTo(distinctCount) ;
		if (c==0)
			return new Integer(destination).compareTo(s.destination) ;
		else
			return c ;
	}
	
	
	
}
