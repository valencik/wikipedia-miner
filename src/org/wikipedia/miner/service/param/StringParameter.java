package org.wikipedia.miner.service.param;

import javax.servlet.http.HttpServletRequest;

public class StringParameter extends Parameter<String> {

	
	public StringParameter(String name, String description, String defaultValue) {
		super(name, description, defaultValue);
	}

	@Override
	public String getValue(HttpServletRequest request) {
		return request.getParameter(getName()) ;
	}

}
