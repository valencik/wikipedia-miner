package org.wikipedia.miner.db;

import com.sleepycat.bind.tuple.* ;


public class DbLinkArrayBinding extends TupleBinding<DbLink[]>{


	// javadoc is inherited
	public DbLink[] entryToObject(TupleInput input) {

		int length = input.readInt() ;
		DbLink[] links = new DbLink[length] ;
		
		for (int i=0 ; i<length ; i++) {
			int id = input.readInt() ;
			
			if (id >= 0) {
				short positionCount = input.readShort() ;
				int[] positions = new int[positionCount] ;
				for (int j=0 ; j<positionCount ; j++) 
					positions[j] = input.readInt() ;
				
				links[i] = new DbLink(id, positions) ;
			}
		}
		return links ;
	}

	// javadoc is inherited
	public void objectToEntry(DbLink[] object, TupleOutput output) {  

		output.writeInt(object.length) ;
		
		for (DbLink link:object) {
			if (link == null) {
				output.writeInt(-1) ;
			}else {
				output.writeInt(link.getId()) ;
				int[] positions = link.getPositions() ;
				
				output.writeShort(positions.length) ;
				for (int pos:positions) 
					output.writeInt(pos) ;
			}
		}
	}



}
