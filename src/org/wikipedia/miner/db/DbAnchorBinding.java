package org.wikipedia.miner.db;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class DbAnchorBinding extends TupleBinding<DbAnchor>{

	// javadoc is inherited
	public DbAnchor entryToObject(TupleInput input) {
	
		int totalLinks = input.readInt() ;
		int distinctLinks = input.readInt() ;
		long totalReferences = input.readLong() ;
		int distinctReferences = input.readInt() ;
		
		short senseCount = input.readShort() ;
		
		DbSense[] senses = null;
		
		if (senseCount > 0) {
			
			senses = new DbSense[senseCount] ;
			for (int i= 0 ; i<senseCount ; i++) {
				int destination = input.readInt() ;
				int totalCount = input.readInt();
				int distinctCount = input.readInt();
				
				boolean isTitle = input.readBoolean() ;
				boolean isRedirect = input.readBoolean() ;
				
				senses[i] = new DbSense(destination, totalCount, distinctCount, isTitle, isRedirect) ;
			}
		}

		return new DbAnchor(totalLinks, distinctLinks, totalReferences, distinctReferences, senses) ;
	}

	// javadoc is inherited
	public void objectToEntry(DbAnchor object, TupleOutput output) {  
		
		output.writeInt(object.getTotalLinks()) ;
		output.writeInt(object.getDistinctLinks()) ;
		output.writeLong(object.getTotalReferences()) ;
		output.writeInt(object.getDistinctReferences()) ;
		
		
		DbSense[] senses = object.getSenses() ;
		
		if (senses == null) {
			output.writeShort(0) ;
		} else {
			output.writeShort(object.getSenses().length) ;
			
			for(DbSense s:senses) {
				output.writeInt(s.getDestination()) ;
				output.writeInt(s.getTotalCount()) ;
				output.writeInt(s.getDistinctCount()) ;
				output.writeBoolean(s.isTitle()) ;
				output.writeBoolean(s.isRedirect()) ;
			}
		}
	}
}
