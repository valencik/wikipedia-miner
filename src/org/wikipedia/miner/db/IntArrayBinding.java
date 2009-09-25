package org.wikipedia.miner.db;
import com.sleepycat.bind.tuple.*;




public class IntArrayBinding extends TupleBinding<int[]>{

	private static final int INT_SIZE = 4;
	
	// javadoc is inherited
    public int[] entryToObject(TupleInput input) {
    	
    	int size = input.getBufferLength()/INT_SIZE ;
    	int[] values = new int[size] ;
    	
    	int i=0 ;
    	
    	try {
    		while (true)
    			values[i++] = (int)input.readInt();
    	    
    	} catch (IndexOutOfBoundsException e) {}
    	
    	return values ;    
    }

    // javadoc is inherited
    public void objectToEntry(int[] object, TupleOutput output) {  
    	for(int i:object) 
    		output.writeInt(i);
    }

    // javadoc is inherited
    protected TupleOutput getTupleOutput(int[] object) {
        return sizedOutput(object.length);
    }

    /**
     * Returns a tuple output object of the exact size needed, to avoid
     * wasting space when a single primitive is output.
     */
    private static TupleOutput sizedOutput(int length) {

        return new TupleOutput(new byte[INT_SIZE*length]);
    }
}
