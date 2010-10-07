package org.wikipedia.miner.service.param;

import java.util.ArrayList;
import javax.servlet.http.HttpServletRequest;

public class IntListParameter extends Parameter<ArrayList<Integer>> {

	public IntListParameter(String name, String description, ArrayList<Integer> defaultValue) {
		super(name, description + "(seperate with commas, e.g. \"668,4980\")", defaultValue);
	}

	@Override
	public ArrayList<Integer> getValue(HttpServletRequest request) {
		
		String s = request.getParameter(getName()) ;
		
		if (s == null)		
			return getDefaultValue();
		
		ArrayList<Integer> values = new ArrayList<Integer>() ;
		for (String val:s.split("[,;:]")) {
			values.add(Integer.parseInt(val.trim())) ;	
		}

		return values ;
	}

}
