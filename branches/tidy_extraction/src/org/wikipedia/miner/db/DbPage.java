package org.wikipedia.miner.db;

public class DbPage {

	private String title ;
	private short type ;
	
	public DbPage(String title, short type) {
		this.title = title ;
		this.type = type ;
	}
	
	public String getTitle() { return title ; }
	
	public short getType() { return type ; }
	
	public String toString() {
		return this.title + " (" + this.type + ")" ;
	}
	
}
