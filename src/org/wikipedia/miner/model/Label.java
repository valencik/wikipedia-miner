package org.wikipedia.miner.model;

import java.util.EnumSet;
import java.util.TreeSet;

import org.wikipedia.miner.db.WEnvironment;
import org.wikipedia.miner.db.struct.DbLabel;
import org.wikipedia.miner.db.struct.DbSenseForLabel;
import org.wikipedia.miner.model.Article.RelatednessDependancy;
import org.wikipedia.miner.util.RelatednessCache;
import org.wikipedia.miner.util.WikipediaConfiguration;
import org.wikipedia.miner.util.text.TextProcessor;



/**
 * A term or phrase that has been used to refer to one or more {@link Article Articles} in Wikipedia. 
 * 
 * These provide your best way of searching for articles relating to or describing a particular term.
 */
public class Label {
	
	
	//properties =============================================================


	private String text ;
	private TextProcessor textProcessor ;

	private long linkDocCount = 0 ;
	private long linkOccCount = 0 ;
	private long textDocCount = 0;
	private long textOccCount = 0;
	
	private Sense[] senses = null ;
	
	protected WEnvironment env ;
	private boolean detailsSet ;
	
	
	
	//constructor =============================================================

	
	/**
	 * Initialises a Label using the default {@link TextProcessor} specified in your {@link WikipediaConfiguration}
	 * 
	 * @param env an active WEnvironment
	 * @param text the term or phrase of interest
	 */
	public Label(WEnvironment env, String text) {
		this.env = env ;
		this.text = text ;
		this.textProcessor = env.getConfiguration().getDefaultTextProcessor() ;
		this.detailsSet = false ;
	}
	
	
	/**
	 * Initialises a Label using the given {@link TextProcessor}.
	 * 
	 * @param env an active WEnvironment
	 * @param text the term or phrase of interest
	 * @param tp a text processor to alter how the given text is matched. If this is null, then texts will be matched directly, without processing. 
	 */
	public Label(WEnvironment env, String text, TextProcessor tp) {
		this.env = env ;
		this.text = text ;
		this.textProcessor = tp ;
		this.detailsSet = false ;
	}
	
	
	

	//public ==================================================================

	@Override
	public String toString() {
		return "\"" + text + "\"" ; 
	}
	
	/**
	 * @return the text used to refer to concepts. 
	 */
	public String getText() {
		return text;
	}
	
	/**
	 * @return true if this label has ever been used to refer to an article, otherwise false
	 */
	public boolean exists() {
		if (!detailsSet) setDetails() ;
		return (senses.length > 0) ;	
	}

	/**
	 * @return the number of articles that contain links with this label used as an anchor.  
	 */
	public long getLinkDocCount() {
		if (!detailsSet) setDetails() ;
		return linkDocCount;
	}

	/**
	 * @return the number of links that use this label as an anchor.  
	 */
	public long getLinkOccCount() {
		if (!detailsSet) setDetails() ;
		return linkOccCount;
	}

	/**
	 * @return the number of articles that mention this label (either as links or in plain text).  
	 */
	public long getDocCount() {
		if (!detailsSet) setDetails() ;
		return textDocCount;
	}

	/**
	 * @return the number of times this label is mentioned in articles (either as links or in plain text).  
	 */
	public long getOccCount() {
		if (!detailsSet) setDetails() ;
		return textOccCount;
	}
	
	/**
	 * @return the probability that this label is used as a link in Wikipedia ({@link #getLinkDocCount()}/{@link #getDocCount()}.  
	 */
	public float getLinkProbability() {
		if (!detailsSet) setDetails() ;
		
		if (textDocCount > 0)		
			return (float) linkDocCount/textDocCount ;
		else
			return 0 ;
	}

	/**
	 * @return	an array of {@link Sense Senses}, sorted by {@link Sense#getPriorProbability()}, that this label refers to.
	 */
	public Sense[] getSenses() {
		if (!detailsSet) setDetails() ;	
		return senses ;
	}
	
	/**
	 * Returns the semantic relatedness of this label to another, calculated using the given relatedness modes
	 * 
	 * The relatedness measure is described in:
	 * Milne, D. and Witten, I.H. (2008) An effective, low-cost measure of semantic relatedness obtained from Wikipedia links. In Proceedings of the first AAAI Workshop on Wikipedia and Artificial Intelligence (WIKIAI'08), Chicago, I.L.
	 * 
	 * @param label the anchor to which this should be compared.
	 * @param modes the modes to use when measuring relatedness between label senses
	 * @return see above.
	 */
	public float getRelatednessTo(Label label, EnumSet<RelatednessDependancy> dependancies) {
		
		DisambiguatedSensePair sp = this.disambiguateAgainst(label, dependancies) ;
		return sp.getRelatedness() ;
	}
	
	
	/**
	 * Returns the semantic relatedness of this label to another, calculated using the modes recommended by the current wikipedia configuration
	 * 
	 * @see #getRelatednessTo(Label,EnumSet)
	 * @see WikipediaConfiguration#getReccommendedRelatednessModes() ;
	 *	 
	 * @param label the anchor to which this should be compared.
	 * @return see above.
	 */
	public float getRelatednessTo(Label label) {
		
		EnumSet<RelatednessDependancy> dependancies = env.getConfiguration().getReccommendedRelatednessDependancies() ;
		
		DisambiguatedSensePair sp = this.disambiguateAgainst(label, dependancies) ;
		return sp.getRelatedness() ;
	}
	
	
	/**
	 * Disambiguates this label against another, by evaluating the relatedness and prior probabilities of the senses of each label.
	 * 
	 * The approach is described in:
	 * Milne, D. and Witten, I.H. (2008) An effective, low-cost measure of semantic relatedness obtained from Wikipedia links. In Proceedings of the first AAAI Workshop on Wikipedia and Artificial Intelligence (WIKIAI'08), Chicago, I.L.
	 *
	 * @param label the label to disambiguate against
	 * @param modes the modes to use when measuring relatedness between label senses
	 * @return a DisambiguatedSensePair describing the senses chosen for each label, and the relatedness between them.
	 */
	public DisambiguatedSensePair disambiguateAgainst(Label label, EnumSet<RelatednessDependancy> dependancies) {
		
		Label anchCombined = new Label(env, this.getText() + " " + label.getText(), null) ;
		double wc = anchCombined.getLinkDocCount() ;
		if (wc > 0) 
			wc = Math.log(wc)/30 ;
		
		double minProb = 0.01 ;
		double benchmark_relatedness = 0 ;
		double benchmark_distance = 0.40 ;
		
		TreeSet<DisambiguatedSensePair> candidates = new TreeSet<DisambiguatedSensePair>() ;
		
		int sensesA = 0 ;
		int sensesB = 0 ;
		
		
		RelatednessCache rc = new RelatednessCache(dependancies) ;

		for (Label.Sense senseA: this.getSenses()) {

			if (senseA.getPriorProbability() < minProb) break ;
			sensesA++ ;
			sensesB = 0 ;

			for (Label.Sense senseB: label.getSenses()) {

				if (senseB.getPriorProbability() < minProb) break ;
				sensesB++ ;

				//double relatedness = artA.getRelatednessTo(artB) ;
				float relatedness = rc.getRelatedness(senseA, senseB) ;
				float obviousness = (senseA.getPriorProbability() + senseB.getPriorProbability()) / 2 ;

				if (relatedness > (benchmark_relatedness - benchmark_distance)) {

					if (relatedness > benchmark_relatedness + benchmark_distance) {
						//this has set a new benchmark of what we consider likely
						benchmark_relatedness = relatedness ;
						candidates.clear() ;
					}
					candidates.add(new DisambiguatedSensePair(senseA, senseB, relatedness, obviousness)) ;
				}
			}
		}
		
		DisambiguatedSensePair sp = candidates.first() ;
		sp.relatedness += wc ;
		if (sp.relatedness > 1)
			sp.relatedness = 1 ;
		
		return sp ;
	}
	
	/**
	 * Disambiguates this label against another using the relatedness modes recommended by the current Wikipedia configuration. 
	 * 
	 * @see #disambiguateAgainst(Label, EnumSet)
	 * @see WikipediaConfiguration#getReccommendedRelatednessModes() ;
	 * 
	 * @param label
	 * @return a DisambiguatedSensePair describing the senses chosen for each label, and the relatedness between them.
	 */
	public DisambiguatedSensePair disambiguateAgainst(Label label) {
		
		EnumSet<RelatednessDependancy> dependancies = env.getConfiguration().getReccommendedRelatednessDependancies() ;
		
		return disambiguateAgainst(label, dependancies) ;
	}
	
	
	/**
	 * A possible sense for a label
	 */
	public class Sense extends Article {

		
		private long sLinkDocCount ;
		private long sLinkOccCount ;

		private boolean fromTitle ;
		private boolean fromRedirect ;

		//constructor =============================================================
		
		protected Sense(WEnvironment env,  DbSenseForLabel s) {
			
			super(env, s.getId()) ;

			this.sLinkDocCount = s.getLinkDocCount() ;
			this.sLinkOccCount = s.getLinkOccCount() ;
			this.fromTitle = s.getFromTitle() ;
			this.fromRedirect = s.getFromRedirect() ;
		}

		
		//public ==================================================================

		/**
		 * Returns the number of documents that contain links that use the surrounding label as anchor text, and point to this sense as the destination.
		 * 
		 * @return the number of documents that contain links that use the surrounding label as anchor text, and point to this sense as the destination.  
		 */
		public long getLinkDocCount() {
			return sLinkDocCount;
		}


		/**
		 * Returns the number of links that use the surrounding label as anchor text, and point to this sense as the destination.
		 * 
		 * @return the number of links that use the surrounding label as anchor text, and point to this sense as the destination.
		 */
		public long getLinkOccCount() {
			return sLinkOccCount;
		}


		/**
		 * Returns true if the surrounding label is used as a title for this sense article, otherwise false
		 * 
		 * @return true if the surrounding label is used as a title for this sense article, otherwise false
		 */
		public boolean isFromTitle() {
			return fromTitle;
		}

		/**
		 * Returns true if the surrounding label is used as a redirect for this sense article, otherwise false
		 * 
		 * @return true if the surrounding label is used as a redirect for this sense article, otherwise false
		 */
		public boolean isFromRedirect() {
			return fromRedirect;
		}
		
		
		/**
		 * Returns the probability that the surrounding label goes to this destination 
		 * 
		 * @return the probability that the surrounding label goes to this destination 
		 */
		public float getPriorProbability() {

			if (getSenses().length == 1)
				return 1 ;

			if (linkOccCount == 0)
				return 0 ;
			else 			
				return ((float)sLinkOccCount) / linkOccCount ;
		}
		
		/**
		 * Returns true if this is the most likely sense for the surrounding label, otherwise false
		 * 
		 * @return true if this is the most likely sense for the surrounding label, otherwise false
		 */
		public boolean isPrimary() {
			return (this == senses[0]) ;
		}
		
	}
	
	/**
	 * The result of measuring the relatedness of two labels: the disambiguated sense chosen to 
	 * represent each label, and their relatedness.
	 */
	public class DisambiguatedSensePair implements Comparable<DisambiguatedSensePair> {
		
		private Sense senseA ;
		private Sense senseB ;
		private float relatedness ;
		private float obviousness ;
		
		//constructor =============================================================
		
		protected DisambiguatedSensePair(Sense senseA, Sense senseB, float relatedness, float obviousness) {
			this.senseA = senseA ;
			this.senseB = senseB ;
			this.relatedness = relatedness ;
			this.obviousness = obviousness ;			
		}
		
		//public ==================================================================
		
		@Override
		public int compareTo(DisambiguatedSensePair cp) {
			return new Float(cp.obviousness).compareTo(obviousness) ;
		}
		
		@Override
		public String toString() {
			return senseA + "," + senseB + ",r=" + relatedness + ",o=" + obviousness ;
		}
		
		
		/**
		 * Returns the sense chosen to represent the first label
		 * 
		 * @return the sense chosen to represent the first label
		 */
		public Sense getSenseA() {
			return senseA ;
		}
		
		/**
		 * Returns the sense chosen to represent the second label
		 * 
		 * @return the sense chosen to represent the second label
		 */
		public Sense getSenseB() {
			return senseB ;
		}
		
		/**
		 * Returns the semantic relatedness of the two labels
		 * 
		 * @return the semantic relatedness of the two labels
		 */
		public float getRelatedness() {
			return relatedness ;
		}
		
		/**
		 * Returns the average prior probability of the two senses 
		 * 
		 * @return the average prior probability of the two senses 
		 */
		public float getAvgPriorProbability() {
			return obviousness ;
		}
	}
	
	
	
	
	//protected and private ====================================================
	
	




	private void setDetails() {
		
		try {
			DbLabel lbl = env.getDbLabel(textProcessor).retrieve(text) ;
		
			if (lbl == null) {
				throw new Exception() ;
			} else {
				setDetails(lbl) ;
			}
		} catch (Exception e) {
			this.senses = new Sense[0] ;
			detailsSet = true ;
		}
	}
	
	private void setDetails(DbLabel lbl) {
		
		this.linkDocCount = lbl.getLinkDocCount() ;
		this.linkOccCount = lbl.getLinkOccCount() ;
		this.textDocCount = lbl.getTextDocCount() ;
		this.textOccCount = lbl.getTextOccCount() ;
		
		this.senses = new Sense[lbl.getSenses().size()] ;
		
		int i = 0 ;
		for (DbSenseForLabel dbs:lbl.getSenses()) {
			this.senses[i] = new Sense(env, dbs) ;
			i++ ;
		}
		
		this.detailsSet = true ;
	}	
}
