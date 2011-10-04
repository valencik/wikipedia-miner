package org.wikipedia.miner.service;

import java.text.DecimalFormat;

import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.*;
import org.wikipedia.miner.model.Wikipedia;

import com.google.gson.annotations.Expose;

public class ProgressService extends Service {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1217650265475115103L;
	
	private DecimalFormat df = new DecimalFormat("#.00") ;
	
	
	public ProgressService() {
		super("meta","Monitors progress of service initialization",
				"<p>Wikipedia Miner can take a while to get started. This service allows polling to see how much progress has been made loading up a particular edition of Wikipedia</p>",
				true, false
		);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public Response buildWrappedResponse(HttpServletRequest request) {
		
		Wikipedia wikipedia = getWikipedia(request) ;
		
		double progress = wikipedia.getEnvironment().getProgress() ;
		
		return new Response(progress) ;
	}
	
	@Override
	public boolean requiresWikipedia() {
		return false ;
	}
	
	@Override
	public int getUsageCost(HttpServletRequest request) {
		return 0 ;
	}
	
	public static class Response extends Service.Response {
		
		@Expose
		@Attribute
		double progress ;
		
		public Response(double progress) {
			this.progress = progress ;
		}
	}

}
