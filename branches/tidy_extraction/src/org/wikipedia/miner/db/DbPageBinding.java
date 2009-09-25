package org.wikipedia.miner.db;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class DbPageBinding extends TupleBinding<DbPage>{

	// javadoc is inherited
	public DbPage entryToObject(TupleInput input) {

		String title = input.readString() ;
		short type = input.readShort();
		
		return new DbPage(title, type) ;
	}

	// javadoc is inherited
	public void objectToEntry(DbPage object, TupleOutput output) {  

		output.writeString(object.getTitle()) ;
		output.writeShort(object.getType()) ;
	}



}
