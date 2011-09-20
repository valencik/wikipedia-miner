
function doEventBindings() {
	$("form").submit(function() {
		var ok = true;
		
		if ($('#term1').val() == "") {
			$('#term1').addClass("ui-state-error") ;
			ok = false ;
		}
		
		if ($('#term2').val() == "") {
			$('#term2').addClass("ui-state-error") ;
			ok = false ;
		}
		
		return ok ;
    });
	
	$('#cmdCompare').button() ;
	
	$('#term1').bind('focus', function() {
		$('#relation').hide('slow') ;
		$('#cmdCompare').show('slow') ;
	}) ;
	
	$('#term2').bind('focus', function() {
		$('#relation').hide('slow') ;
		$('#cmdCompare').show('slow') ;
	}) ;
	
	
	
}

function doTooltipBindings() {
	
	$('#disambiguationHelp').qtip({
	      content: "Words are often ambiguous, and can represent multiple topics. This section explains how one topic is chosen automatically to represent each term.",
		  style: { name: 'wmstyle' }
    }) ;
	
	$('#connectionHelp').qtip({
	      content: "This section lists articles that talk about both of the concepts being compared. These are a useful by-product of how Wikipedia Miner calculates relatedness.",
		  style: { name: 'wmstyle' }
    }) ;
	
	$('#snippetHelp').qtip({
	      content: "This section lists sentences that talk about both of the concepts being compared. These are a useful by-product of how Wikipedia Miner calculates relatedness.",
		  style: { name: 'wmstyle' }
    }) ;
}


$(document).ready(function() {
	
	wm_setHost("../../")
	
	doEventBindings() ;
	doTooltipBindings() ;
	
	
	checkProgress() ;
	
}) ;

function checkProgress() {
	
	$.get(
		"../../services/getProgress",
		function(data) {
			
			var xmlResponse = $(data).find("Response") ;
			var progress = Number(xmlResponse.attr("progress")) ;

			if (progress >= 1) {
				ready() ;
			} else {
		
				$('#progress').progressbar(
					{value: Math.floor(progress*100)}
				) ;
		
				setTimeout(
					checkProgress,
					500
				) ;
			}
		}
	) ;	
}


function ready() {
	
	$("#initializing").hide() ;
	$("#ready").show() ;	
	
	var term1 = urlParams["term1"];
	var term2 = urlParams["term2"];
	
	if (term1 != undefined && term2 != undefined) {
		
		$('#instructions').hide() ;
		$('#loadingSpacer').show() ;
		
		$('#term1').val(term1) ;
		$('#term2').val(term2) ;
		
		$.get(
			"../../services/compare", 
			{
				term1: $('#term1').val(), 
				term2: $('#term2').val(),
				interpretations: true,
				connections: true,
				maxConnectionsReturned: 50,
				snippets: true
			},
			function(data){
				processRelatednessResponse($(data).find("Response")) ;
			}
		);
		
	} else {
		$('#relation').hide() ;
		$('#cmdCompare').show() ;
	}
}


function processRelatednessResponse(response) {
	
	$('#loadingSpacer').hide() ;
	
	var unknownTerm = response.attr('unknownTerm') ;
	
	if (unknownTerm != undefined) {

		$('#unkownTerm').html(unknownTerm) ;
		$('#error').show() ;
		
		$('#relation').hide() ;
		$('#cmdCompare').show() ;
		
		return ;
	}
	
	$('#details').show() ;
	
	
	
	$('#relationWeight').html(Math.round(response.attr("relatedness")*100) + "% related") ;
	
	var term1 = urlParams["term1"] ;
	var term2 = urlParams["term2"] ;
	
	$('#byTerm1').html(term1) ;
	$('#byTerm2').html(term2) ;
	
	var xmlInterpretations = $(response.children("Interpretations")) ;
	var xmlInterpretation = $(xmlInterpretations.children()[0]) ;
	
	if (xmlInterpretation.attr('id1') != undefined && xmlInterpretation.attr('id2')) {
	
		$('#sense1').html("<a pageId='" + xmlInterpretation.attr('id1') + "' href='../search/?artId=" + xmlInterpretation.attr('id1') + "'>" + xmlInterpretation.attr('title1') + "</a>") ;
		$('#sense2').html("<a pageId='" + xmlInterpretation.attr('id2') + "' href='../search/?artId=" + xmlInterpretation.attr('id2') + "'>" + xmlInterpretation.attr('title2') + "</a>") ;
	
		wm_addDefinitionTooltipsToAllLinks($('#sense1')) ;
		wm_addDefinitionTooltipsToAllLinks($('#sense2')) ;
	} else {
		$('#senses').hide() ;
		$('#noSenses').show() ;
	}
	
	var candidates1 = Number(xmlInterpretations.attr("term1Candidates")) ;
	var candidates2 = Number(xmlInterpretations.attr("term2Candidates")) ;
	
	if (candidates1 == 2)
		$('#alternatives1').html("there is <a href='../search/?query=" + term1 + "'>1 other sense</a> for this term.") ;
	else if (candidates1 > 2)
		$('#alternatives1').html("there are <a href='../search/?query=" + term1 + "'>" + (candidates1-1) + " other senses</a> for this term") ;
	else
		$('#alternatives1').html("this is the only possible sense.") ;
		
	if (candidates2 == 2)
		$('#alternatives2').html("there is <a href='../search/?query=" + term2 + "'>1 other sense</a> for this term.") ;
	else if (candidates2 > 2)
		$('#alternatives2').html("there are <a href='../search/?query=" + term2 + "'>" + (candidates2-1) + " other senses</a> for this term") ;
	else
		$('#alternatives2').html("this is the only possible sense.") ;
		
		
	
	var sortedConnections = $(response).find("Connection").get().sort(function(a,b) {
		var valA = $(a).attr("title") ;
		var valB = $(b).attr("title") ;
		
		return valA < valB ? -1 : valA == valB? 0 : 1 ;
	}) ;
	
	if (sortedConnections.length > 0) {
		$.each(sortedConnections, function() {
			var xmlConnection = $(this) ;
			
			var connection = $("<a pageId='" + xmlConnection.attr('id') + "' href='../search/?artId=" + xmlConnection.attr('id') + "'>" + xmlConnection.attr('title') + "</a>") ;
			var weight = (Number(xmlConnection.attr('relatedness1')) + Number(xmlConnection.attr('relatedness2'))) / 2 ;
			connection.css('font-size', getFontSize(weight) + "px") ;
			connection.css('color', getFontColor(weight)) ;
			
			var li = $("<li></li>") ;
			li.append(connection) ;

			$('#connections').append(li) ;
		}) ;
		wm_addDefinitionTooltipsToAllLinks($('#connections')) ;
	} else {
		$('#connections').hide() ;
		$('#noConnections').show() ;
	}
	
	var snippets = response.find("Snippet") ;
	
	if (snippets.length > 0) {
		
		$.each(response.find("Snippet"), function() {
			var xmlSnippet = $(this) ;
			var snippet = $("<ul class='snippet ui-corner-all'><p>" + xmlSnippet.text() + " </p> <p class='source'>from <a pageId='" + xmlSnippet.attr('sourceId') + "' href='../search/?artId=" + xmlSnippet.attr('sourceId') + "'>" + xmlSnippet.attr('sourceTitle') + "</a></ul>") ;
			$("#snippets").append(snippet) ;
		}) ;
	} else {
		$('#snippets').hide() ;
		$('#noSnippets').show() ;
	}
	
	wm_addDefinitionTooltipsToAllLinks($('#snippets')) ;
}