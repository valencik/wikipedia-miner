package org.wikipedia.miner.service;

import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.Attribute;
import org.wikipedia.miner.model.Wikipedia;

import com.google.gson.annotations.Expose;

public class ProgressService extends Service {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1217650265475115103L;
	
	public ProgressService() {
		super("meta","Monitors progress of service initialization",
				"<p>Wikipedia Miner can take a while to get started. This service allows polling to see how much progress has been made loading up a particular edition of Wikipedia</p>",
				true, false
		);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public Message buildWrappedResponse(HttpServletRequest request) {
		
		Wikipedia wikipedia = getWikipedia(request) ;
		
		double progress = wikipedia.getEnvironment().getProgress() ;
		
		return new Message(request, progress) ;
	}
	
	@Override
	public boolean requiresWikipedia() {
		return false ;
	}
	
	@Override
	public int getUsageCost(HttpServletRequest request) {
		return 0 ;
	}
	
	public static class Message extends Service.Message {
		
		@Expose
		@Attribute
		private double progress ;
		
		private Message(HttpServletRequest request, double progress) {
			super(request) ;
			this.progress = progress ;
		}

		public double getProgress() {
			return progress;
		}
	}

}
