package org.wikipedia.miner.db;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class DbAnchorTextArrayBinding extends TupleBinding<DbAnchorText[]>{


	// javadoc is inherited
	public DbAnchorText[] entryToObject(TupleInput input) {

		short length = input.readShort() ;
		DbAnchorText[] anchorTexts = new DbAnchorText[length] ;
		
		for (int i=0 ; i<length ; i++) {
			String text = input.readString() ; 
			int totalCount = input.readInt();
			int distinctCount = input.readInt();
			
			anchorTexts[i] = new DbAnchorText(text, totalCount, distinctCount)  ;
		}
		return anchorTexts ;
	}

	// javadoc is inherited
	public void objectToEntry(DbAnchorText[] object, TupleOutput output) {  

		output.writeShort(object.length) ;
		
		for (DbAnchorText anchorText:object) {
			output.writeString(anchorText.getText()) ;
			output.writeInt(anchorText.getTotalCount()) ;
			output.writeInt(anchorText.getDistinctCount()) ;
		}
	}



}
