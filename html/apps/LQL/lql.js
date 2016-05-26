var x = new Date();
var currentTimeZoneOffset = x.getTimezoneOffset();
var tzOffset = document.getElementsByName('timezoneoffset');
tzOffset[0].value = currentTimeZoneOffset;
tzOffset[0].disabled = true;

// jQuery radio button handler to show and hide required fields
// depending on the API Chosen.
$(document).ready(function(){
    $('input[type=radio]').click(function(){
        if (this.value == 'search.json') {
        	$("#searchOptions").show();
        }
        else {
        	constructQuery();
        	$("#searchOptions").hide();
        }
    });
});

// Pass the radioObjects and it checks and 
// returns the value of the radio object
function getCheckedRadioValue(radioObj) {
	if(!radioObj)
		return "";
	var radioLength = radioObj.length;
	if(radioLength == undefined)
		if(radioObj.checked)
			return radioObj.value;
		else
			return "";
	for(var i = 0; i < radioLength; i++) {
		if(radioObj[i].checked) {
			return radioObj[i].value;
		}
	}
	return "";
}

// Pass the checkBoxObjects and it returns
// the values of the objects that are checked in
// On the checkbox

function getCheckedCheckboxValue(checkBoxObj) {
	if (!checkBoxObj)
		return "";
	var checkBoxesLength = checkBoxObj.length;
	var checkedItems = [];
	for (var i=0; i < checkBoxesLength; i++) {
		if (checkBoxObj[i].checked == true) {
			checkedItems.push(checkBoxObj[i].value);
		}
	}
	return checkedItems;
}

function queryLoklak() {
	// Generates the XHR and display's content
	queryString = $('#queryGenerated').val();
	$.getJSON(queryString, function (data, status) {
		console.log(data);
		$('#queryResult').val(JSON.stringify(data, null, 2));
	})
}

function constructQuery() {
	var APIRadioObject = document.getElementsByName('api');
	var selectedAPI = getCheckedRadioValue(APIRadioObject);
	// selectedAPI Contains the type of query to be made
	// status.json, search.json, peers.json etc..,
	var minifiedRadioObject = document.getElementsByName('minified');
	var minifiedType = getCheckedRadioValue(minifiedRadioObject);
	// Minified type contains value True/False
	var aggregationsCheckboxObject = document.getElementsByName('aggregations');
	var aggregations = getCheckedCheckboxValue(aggregationsCheckboxObject);

	if (selectedAPI != 'search.json') {
		serviceURL = $(location).attr('href').split('apps/LQL/')[0];
		var constructedURL = serviceURL;
		constructedURL += 'api/' + selectedAPI;
		$('#queryGenerated').val(constructedURL);
	}
	else {
		$('#queryGenerated').val('You have to fill in the required values in search to constuct the URL');
	}
}
