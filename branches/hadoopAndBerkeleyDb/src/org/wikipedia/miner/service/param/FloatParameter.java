package org.wikipedia.miner.service.param;

import javax.servlet.http.HttpServletRequest;

public class FloatParameter extends Parameter<Float> {

	public FloatParameter(String name, String description,
			Float defaultValue) {
		super(name, description, defaultValue);
	}

	@Override
	public Float getValue(HttpServletRequest request) {
		
		String s = request.getParameter(getName()) ;
		
		if (s == null)
			return getDefaultValue() ;
		else
			return Float.valueOf(s) ;
	}
	
}
