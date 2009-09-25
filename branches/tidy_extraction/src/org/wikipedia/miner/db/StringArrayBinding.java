package org.wikipedia.miner.db;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class StringArrayBinding extends TupleBinding<String[]>{

	// javadoc is inherited
	public String[] entryToObject(TupleInput input) {
	
		int size = input.readInt() ;
		
		String[] strings = new String[size] ;

		int i=0 ;

		try {
			while (true) 
				strings[i++] = input.readString() ;
		} catch (IndexOutOfBoundsException e) {}

		return strings ;    
	}

	// javadoc is inherited
	public void objectToEntry(String[] object, TupleOutput output) {  
		
		output.writeInt(object.length) ;
		
		for(String s:object) 
			output.writeString(s) ;
	}
}
