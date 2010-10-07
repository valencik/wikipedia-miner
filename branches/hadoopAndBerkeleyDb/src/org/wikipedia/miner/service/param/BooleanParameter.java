package org.wikipedia.miner.service.param;

import javax.servlet.http.HttpServletRequest;

public class BooleanParameter extends Parameter<Boolean> {

	public BooleanParameter(String name, String description,
			Boolean defaultValue) {
		super(name, description, defaultValue);
	}

	@Override
	public Boolean getValue(HttpServletRequest request) {
		
		String s = request.getParameter(getName()) ;
		
		if (s == null)
			return getDefaultValue() ;
		
		if (s.trim().length() == 0)
			return true ;
		
		return Boolean.valueOf(s) ;
	}

}
