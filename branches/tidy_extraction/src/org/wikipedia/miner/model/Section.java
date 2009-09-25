package org.wikipedia.miner.model;

import java.util.Arrays;

import org.wikipedia.miner.db.DbStructureNode;

public class Section extends StructureNode {

	private short level ;
	private int[] titleBounds ;
	
	private Paragraph[] paragraphs ;
	private int[] paragraphStarts ;
	
	private Section[] subsections ;
	private int[] subsectionStarts ;
	
	public Section(DbStructureNode dbNode) {
		super(null, StructureNode.ROOT, dbNode.getStartPosition()) ;
		initialize(dbNode, (short)0) ;
	}
	
	public Section(StructureNode parent, DbStructureNode dbNode, short level) {
		super(parent, StructureNode.SECTION, dbNode.getStartPosition()) ;
		initialize(dbNode, level) ;
	}
	
	private void initialize(DbStructureNode dbNode, short level) {
		
		this.level = level ;
		
		DbStructureNode[] dbChildren = dbNode.getChildren() ;
		
		//first child always indicates the title of the section
		titleBounds = new int[2] ;
		titleBounds[0] = dbChildren[0].getSentenceBreaks()[0] ;
		titleBounds[1] = dbChildren[0].getSentenceBreaks()[1] ;
		
		int numParagraphs = 0 ;
		for (int i=1 ; i< dbChildren.length ; i++) {
			if (dbChildren[i].getChildren() == null){
				//this is a paragraph
				numParagraphs++ ;
			} else {
				break ;
			}
		}
		
		paragraphs = new Paragraph[numParagraphs] ;
		paragraphStarts = new int[numParagraphs] ;
		subsections = new Section[dbChildren.length - (numParagraphs + 1)] ;
		subsectionStarts = new int[dbChildren.length - (numParagraphs + 1)] ;
		
		for (int i=1 ; i< dbChildren.length ; i++) {
			if (i<=numParagraphs) {
				paragraphs[i-1] = new Paragraph(this, dbChildren[i]) ;
				paragraphStarts[i-1] = dbChildren[i].getStartPosition() ;
			} else {
				subsections[i-(numParagraphs+1)] = new Section(this, dbChildren[i], (short) (level+1)) ;
				subsectionStarts[i-(numParagraphs+1)] = dbChildren[i].getStartPosition() ;
			}
		}
		
		if (subsections.length > 0) {
			this.end = subsections[subsections.length-1].end ;
		} else {
			this.end = paragraphs[paragraphs.length-1].end ;
		}
	}
	
	public int[] getTitleBounds() {
		return titleBounds ;
	}
	
	public short getLevel() {
		return level ;
	}
	
	public StructureNode getSurroundingStructureNode(int pos) {
		
		if (subsections.length > 0 && subsectionStarts[0] < pos) {
			//look in subsections
			int index = Arrays.binarySearch(subsectionStarts, pos) ;
			if (index < 0)
				index = Math.abs(index) -2 ;
			
			if (index < subsections.length) 
				return subsections[index].getSurroundingStructureNode(pos) ;
			else
				return this ;
		} else {
			// look in paragraphs
			int index = Arrays.binarySearch(paragraphStarts, pos) ;
			if (index < 0)
				index = Math.abs(index) -2 ;
			
			if (index < paragraphs.length) 
				return paragraphs[index] ;
			else
				return this ;
		}
	}
	
	public StructureNode getSurroundingStructureNode(int pos1, int pos2) {
		
		if (subsections.length > 0 && subsectionStarts[0] < pos1 && subsectionStarts[0] < pos2) {
			
			//look in subsections
			int index1 = Arrays.binarySearch(subsectionStarts, pos1) ;
			if (index1 < 0)
				index1 = Math.abs(index1) -2 ;
			
			int index2 = Arrays.binarySearch(subsectionStarts, pos2) ;
			if (index2 < 0)
				index2 = Math.abs(index2)-2 ;
			
			if (index1 == index2 && index1 < subsectionStarts.length-1) 
				return subsections[index1].getSurroundingStructureNode(pos1, pos2) ;
			else
				return this ;
		}
		
		if (subsectionStarts[0] > pos1 && subsectionStarts[0] > pos2) {
			
			//look in paragraphs
			int index1 = Arrays.binarySearch(paragraphStarts, pos1) ;
			if (index1 < 0)
				index1 = Math.abs(index1) -2 ;
			
			int index2 = Arrays.binarySearch(paragraphStarts, pos2) ;
			if (index2 < 0)
				index2 = Math.abs(index2)-2 ;
			
			if (index1 == index2 && index1 < paragraphStarts.length-1) 
				return paragraphs[index1] ;
			else
				return this ;
		}
		
		return this ;
	}
}
