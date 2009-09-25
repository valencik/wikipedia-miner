package org.wikipedia.miner.model;

public class StructureNode {

	public static final short ROOT = 0 ;
	public static final short SECTION = 1 ;
	public static final short PARAGRAPH = 2 ;
	
	protected StructureNode parent ;
	protected short type ;
	
	protected int start ;
	protected int end ;
	
	public StructureNode(StructureNode parent,  short type, int start) {
		this.parent = parent ;
		this.type = type ;
		this.start = start ;
	}
	
	public short getType() {
		return type ;
	}
	
	public int getStart() {
		return start ;
	}
	
	public int getEnd() {
		return end ;
	}
}
