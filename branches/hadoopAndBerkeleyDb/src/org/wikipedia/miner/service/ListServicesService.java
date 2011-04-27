package org.wikipedia.miner.service;

import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Element;

public class ListServicesService extends Service{

		public ListServicesService() {
			super("Lists available services", 
					"<p>This service lists the different services that are available.</p>",
					false,false
				);
		}

		@Override
		public Element buildWrappedResponse(HttpServletRequest request,
				Element response) throws Exception {

			for (String serviceName:getHub().getServiceNames()) {
				
				Service service = getHub().getService(serviceName) ;
				
				Element xmlService = getHub().createElement("Service") ;
				xmlService.setAttribute("name", serviceName) ;
				xmlService.appendChild(getHub().createCDATAElement("Details", service.getShortDescription())) ;
				response.appendChild(xmlService) ;
			}
	
			return response ;
		}

}
