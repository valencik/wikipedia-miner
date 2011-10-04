package org.wikipedia.miner.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang.time.DateUtils;
import org.simpleframework.xml.*;

import com.google.gson.annotations.Expose;

public class Client {
	
	public enum Granularity {day,hour,minute} ;

	@Expose
	@Attribute
	private String _name ;
	
	private String _password ;
		
	@Expose
	@ElementList(inline=true)
	private ArrayList<Usage> _usage ;
	
	private static SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss") ;
	
	public Client(String name, String password, int minLimit, int hourLimit, int dayLimit) {
		_name = name ;
		_password = password ;
		
		_usage = new ArrayList<Usage>() ;
		_usage.add(new Usage(Granularity.day, dayLimit)) ;
		_usage.add(new Usage(Granularity.hour, hourLimit)) ;
		_usage.add(new Usage(Granularity.minute, minLimit)) ;
	}
	
	public Client(String name, String password, Client client) {
		_name = name ;
		_password = password ;
		
		_usage = new ArrayList<Usage>() ;
		_usage.add(new Usage(Granularity.day, client.getDayUsage().getLimit())) ;
		_usage.add(new Usage(Granularity.hour, client.getHourUsage().getLimit())) ;
		_usage.add(new Usage(Granularity.minute, client.getMinuteUsage().getLimit())) ;
	}
	
	public String getName() {
		return _name ;
	}
	
	public boolean passwordMatches(String password) {
			
		if (_password == null)
			return true ;
		
		return _password.equals(password) ;
	}

	public boolean update(int usageCost) {
		
		boolean limitExceeded = false ;
		
		for (Usage u:_usage) {
			
			if (u.update(usageCost)) 
				limitExceeded = true ;
		}
		
		return limitExceeded ;
	}
	
	public Usage getMinuteUsage() {
		return _usage.get(Granularity.minute.ordinal()) ;
	}
	
	public Usage getHourUsage() {
		return _usage.get(Granularity.hour.ordinal()) ;
	}
	
	public Usage getDayUsage() {
		return _usage.get(Granularity.day.ordinal()) ;
	}
	
	public class Usage {
		
		@Expose
		@Attribute(name="granularity") 
		private Granularity _granularity ;
		
		@Expose
		@Attribute(name="start") 
		private Date _start ;
		
		@Expose
		@Attribute(name="end")
		private Date _end ;
		
		@Expose
		@Attribute(name="unitCount") 
		int _count ;
		
		@Expose
		@Attribute(name="unitLimit")
		int _limit ;
		
		public Usage(Granularity granularity, int limit) {
			
			_granularity = granularity ;
			_count = 0 ;
			setPeriod() ;
			
			_limit = limit ;
		}
		
		public Date getPeriodStart() {
			return _start ;
		}
		
		public Date getPeriodEnd() {
			return _end ;
		}
		
		public int getCount() {
			return _count ;
		}
		
		public int getLimit() {
			return _limit ;
		}
		
		public boolean limitExceeded() {
			return (_limit > 0 && _count > _limit) ;
		}
		
		private int getGranularityInt() {
			switch(_granularity) {
			case day:
				return Calendar.DAY_OF_MONTH ;
			case hour:
				return Calendar.HOUR ;
			case minute:
				return Calendar.MINUTE ;
			}
			
			return -1 ;
		}
		
		private void setPeriod() {
			
			_start = new Date() ;
			_end = new Date() ;
			
			DateUtils.truncate(_start, getGranularityInt()) ;
			DateUtils.truncate(_end, getGranularityInt()) ;
			
			switch(_granularity) {
			case minute:
				_end = DateUtils.addMinutes(_end, 1) ;
				break ;
			case hour:
				_end = DateUtils.addHours(_end, 1) ;
				break ;
			case day:
				_end = DateUtils.addDays(_end, 1) ;
				break ;
			}
			
		}
		
		protected boolean update(int usageCost) {
			
			Date now = new Date() ;
			
			if (now.after(_end)) {
				setPeriod() ;
				_count = usageCost ;
			} else {
				_count = _count + usageCost ;
			}
			
			return limitExceeded() ;
		}
	}
	
}
