package org.wikipedia.miner.db;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class DbStructureNodeBinding extends TupleBinding<DbStructureNode>{


	// javadoc is inherited
	public DbStructureNode entryToObject(TupleInput input) {
		
		int startPos = input.readInt();
		
		short sbCount = input.readShort() ;
		int[] sentenceBreaks = null ;
		if (sbCount > 0) {
			sentenceBreaks = new int[sbCount] ;
			
			for (int i=0 ; i<sbCount ; i++)
				sentenceBreaks[i] = input.readInt() ;
		}

		short childLength = input.readShort();
		DbStructureNode[] children = null ;
		
		if (childLength > 0) {
			children = new DbStructureNode[childLength] ;
			for (int i=0 ; i<childLength ; i++) {
				children[i] = entryToObject(input) ;
			}
		}
		
		if (sbCount > 0) {
			return new DbStructureNode(sentenceBreaks) ;
		} else {
			return new DbStructureNode(startPos, children) ;
		}
	}

	// javadoc is inherited
	public void objectToEntry(DbStructureNode object, TupleOutput output) {  

		output.writeInt(object.getStartPosition()) ;
		
		int[] sbs = object.getSentenceBreaks() ;
		
		if (sbs != null) {
			output.writeShort(sbs.length) ;
			for (int sb:sbs) 
				output.writeInt(sb) ;
		} else {
			output.writeShort(0) ;
		}
		
		DbStructureNode[] kids = object.getChildren() ;
		
		if (kids != null) {
			output.writeShort(object.getChildren().length) ;
			
			for(DbStructureNode kid: kids) 
				objectToEntry(kid, output) ;
		} else {
			output.writeShort(0) ;
		}
	}



}
