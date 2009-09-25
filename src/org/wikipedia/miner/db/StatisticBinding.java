package org.wikipedia.miner.db;

import org.wikipedia.miner.db.WikipediaEnvironment.Statistic;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class StatisticBinding extends TupleBinding<WikipediaEnvironment.Statistic>{

	private static final int INT_SIZE = 4;
	
	// javadoc is inherited
    public WikipediaEnvironment.Statistic entryToObject(TupleInput input) {
    	
	    	int statIndex = input.readInt() ;
	    	return Statistic.values()[statIndex] ;
    }

    // javadoc is inherited
    public void objectToEntry(WikipediaEnvironment.Statistic object, TupleOutput output) {  
    
    		output.writeInt(object.ordinal()) ;
    }

    // javadoc is inherited
    protected TupleOutput getTupleOutput(WikipediaEnvironment.Statistic object) {
        return new TupleOutput(new byte[INT_SIZE]);
    }
}
