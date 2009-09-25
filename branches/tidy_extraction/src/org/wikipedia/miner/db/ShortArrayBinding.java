package org.wikipedia.miner.db;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class ShortArrayBinding extends TupleBinding<short[]>{

	private static final int SHORT_SIZE = 2;

	// javadoc is inherited
	public short[] entryToObject(TupleInput input) {

		int size = input.getBufferLength()/SHORT_SIZE ;
		short[] values = new short[size] ;

		int i=0 ;

		try {
			while (true)
				values[i++] = (short)input.readShort();

		} catch (IndexOutOfBoundsException e) {}

		return values ;    
	}

	// javadoc is inherited
	public void objectToEntry(short[] object, TupleOutput output) {  
		for(short i:object) 
			output.writeShort(i);
	}

	// javadoc is inherited
	protected TupleOutput getTupleOutput(short[] object) {
		return sizedOutput(object.length);
	}

	/**
	 * Returns a tuple output object of the exact size needed, to avoid
	 * wasting space when a single primitive is output.
	 */
	private static TupleOutput sizedOutput(int length) {

		return new TupleOutput(new byte[SHORT_SIZE*length]);
	}
}
