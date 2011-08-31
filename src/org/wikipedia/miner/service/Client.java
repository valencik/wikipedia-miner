package org.wikipedia.miner.service;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import org.apache.commons.lang.time.DateUtils;

public class Client {

	
	private String _name ;
	private String _password ;
	
	private HashMap<Integer, Usage> _usageByGranularity ;	
	
	public Client(String name, String password, int minLimit, int hourLimit, int dayLimit) {
		_name = name ;
		_password = password ;
		
		_usageByGranularity = new HashMap<Integer, Usage>() ;
		_usageByGranularity.put(Calendar.MINUTE, new Usage(Calendar.MINUTE, minLimit)) ;
		_usageByGranularity.put(Calendar.HOUR, new Usage(Calendar.HOUR, hourLimit)) ;
		_usageByGranularity.put(Calendar.DAY_OF_MONTH, new Usage(Calendar.DAY_OF_MONTH, dayLimit)) ;
	}
	
	public Client(String name, String password, Client client) {
		_name = name ;
		_password = password ;
		
		_usageByGranularity = new HashMap<Integer, Usage>() ;
		_usageByGranularity.put(Calendar.MINUTE, new Usage(Calendar.MINUTE, client.getMinuteUsage().getLimit())) ;
		_usageByGranularity.put(Calendar.HOUR, new Usage(Calendar.HOUR, client.getHourUsage().getLimit())) ;
		_usageByGranularity.put(Calendar.DAY_OF_MONTH, new Usage(Calendar.DAY_OF_MONTH, client.getDayUsage().getLimit())) ;
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
		
		for (Usage u:_usageByGranularity.values()) {
			
			if (u.update(usageCost)) 
				limitExceeded = true ;
		}
		
		return limitExceeded ;
	}
	
	public Usage getMinuteUsage() {
		return _usageByGranularity.get(Calendar.MINUTE) ;
	}
	
	public Usage getHourUsage() {
		return _usageByGranularity.get(Calendar.HOUR) ;
	}
	
	public Usage getDayUsage() {
		return _usageByGranularity.get(Calendar.DAY_OF_MONTH) ;
	}
	
	public class Usage {
		
		private int _granularity ;
		private Date _start ;
		private Date _end ;
		int _count ;
		int _limit ;
		
		public Usage(int granularity, int limit) {
			
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
		
		private void setPeriod() {
			
			_start = new Date() ;
			_end = new Date() ;
			
			DateUtils.truncate(_start, _granularity) ;
			DateUtils.truncate(_end, _granularity) ;
			
			switch(_granularity) {
			case Calendar.MINUTE:
				DateUtils.addMinutes(_end, 1) ;
				break ;
			case Calendar.HOUR:
				DateUtils.addMinutes(_end, 1) ;
				break ;
			case Calendar.DAY_OF_MONTH:
				DateUtils.addDays(_end, 1) ;
				break ;
			}
			
			DateUtils.add(_end, _granularity, 1) ;
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
