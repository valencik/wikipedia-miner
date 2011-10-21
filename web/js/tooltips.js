var wm_host = "" ;

$.fn.qtip.styles.wm_definition = { // Last part is the name of the style
   color: 'white',
   background: '#373737' ,
   fontSize: '14px',
   border: {
   	 width: 1,
     radius: 10,
	 color: '#373737'
   },
   position: {
   	 corner: {
   	 	target: 'bottomMiddle',
   	 	tooltip: 'topMiddle'
   	 }
   },
   tip: 'topMiddle', 
   padding: 3 , 
   textAlign: 'left',
   width:400, 
   name: 'cream'
}

function wm_setHost(hostName) {
	wm_host = hostName ;
}

function wm_addDefinitionTooltipsToAllLinks(container, className) {
        
    if (container == null) 
	container = $("body") ;
    
    var links ;

	if (className == null)
		links = container.find("a") ;
	else
		links = container.find("a." + className) ;
        
    $.each(links, function() {
            
        var link = $(this);
        var details = {id:link.attr("pageId"),linkProb:link.attr("linkProb"),relatedness:link.attr("relatedness")} ;
        
        if (details.id != null) {
        	link.qtip(
				{
					content:"",
					style: {
						width: 400,
						padding:3,
						color: 'white',
						background: '#373737' ,
						fontSize: '14px',
						border: {
						   	 width: 1,
						     radius: 8,
							 color: '#373737'
						},
					},   
					position: {
					   	 corner: {
					   	 	target: 'bottomMiddle',
					   	 	tooltip: 'topMiddle'
					   	 }
					},
					tip: 'topMiddle', 
					api: {
						onRender: function(data) {
							wm_renderTooltip(this, details) ;
						}
					}
				}
			) ;
        }  
     
    }) ;       
}

function wm_renderTooltip(tooltip, details) {
	$.getJSON(
		wm_host+"services/exploreArticle",
		{
			id:details.id,
			responseFormat:'JSON',
			definition:true,
			linkFormat:'PLAIN',
			images:true
		},
		function(data) {
			wm_handleTooltipResponse(tooltip, data, details) ;
		}
	) ;
}
			
function wm_handleTooltipResponse(tooltip, data, details) {
	
	var newContent = $("<div/>") ;
	
	var images = data.images ;
	if (images != undefined)
		newContent.append("<img src='" + images[0].url + "' style='float:right;border:1px solid black;margin-left:20px;'></img>") ;
	
	var definition = data.definition ;
	
	if (definition != undefined)
		newContent.append(definition) ;
	else
		newContent.append("no definition available") ;
		
	if (details.linkProb != null) 
    	newContent.append("<p><b>" + Math.round(details.linkProb*100) + "%</b> probability of being a link") ;
                
    if (details.relatedness != null) 
    	newContent.append("<p><b>" + Math.round(details.relatedness*100) + "%</b> related") ;
	
		
	tooltip.updateContent(newContent, false) ;
}