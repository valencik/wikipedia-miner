package org.wikipedia.miner.service.param;

import javax.servlet.http.HttpServletRequest;

public class IntParameter extends Parameter<Integer> {

	public IntParameter(String name, String description, Integer defaultValue) {
		super(name, description, defaultValue);
	}

	@Override
	public Integer getValue(HttpServletRequest request) {
		
		String s = request.getParameter(getName()) ;
		
		if (s==null)
			return getDefaultValue() ;
		else
			return Integer.parseInt(s) ;
	}
}
