package org.wikipedia.miner.service;

import java.text.DecimalFormat;

import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Element;
import org.wikipedia.miner.model.Wikipedia;

public class ProgressService extends Service {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1217650265475115103L;
	
	private DecimalFormat df = new DecimalFormat("#.00") ;
	
	
	public ProgressService() {
		super("<p></p>");
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public Element buildWrappedResponse(HttpServletRequest request, Element xmlResponse) {
		
		Wikipedia wikipedia = getWikipedia(request) ;
		
		double progress = wikipedia.getEnvironment().getProgress() ;
	
		xmlResponse.setAttribute("progress", df.format(progress)) ;

		return xmlResponse ;
	}
	
	@Override
	public boolean requiresWikipedia() {
		return false ;
	}

}
