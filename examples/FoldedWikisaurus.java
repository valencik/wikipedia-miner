import java.io.File;

import org.wikipedia.miner.util.WikipediaConfiguration;


public class FoldedWikisaurus extends Wikisaurus{

	
	public FoldedWikisaurus(WikipediaConfiguration conf) {
		super(conf);
	}

	public static void main(String args[]) throws Exception {
		WikipediaConfiguration conf = new WikipediaConfiguration(new File(args[0])) ;
		conf.setDefaultTextProcessor(new TextFolder()) ;

		FoldedWikisaurus thesaurus = new FoldedWikisaurus(conf) ;
		thesaurus.run() ;
	}
}
