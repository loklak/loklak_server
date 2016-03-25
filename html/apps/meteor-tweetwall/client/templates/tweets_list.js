Template.tweetsList.helpers({
	tweets: function() {
		// unstable
		// // .find() triggers invalidation
		// // gets new array result from client DDP poll
		// LoklakTweets.find();
		// var arrDDP = LoklakTweets.find().fetch();
		// if(arrDDP.length>0){
		// 	// if no tweets set null so isNew is true later
		// 	var tweetArr = Tweets.find().fetch();
		// 	var newestTweetName = tweetArr.length ? moment(tweetArr[0].tweet) : null;
		// 	// rough diff, if same tweet break
		// 	for(var i=0; i<arrDDP.length; i++){
		// 		var currTweet = arrDDP[i];
		// 		if(i===1){
		// 			console.log(moment(currTweet.created_at));
		// 			console.log(newestTweetName);
		// 		}
		//
		// 		var isNew = newestTweetName ? moment(currTweet.created_at).isAfter(newestTweetName) : true;
		// 		if(isNew) Meteor.call("addTweet", currTweet);
		// 		else break;
		// 	}
		// }

		return Tweets.find({}, {sort: { uDate: -1 }, limit: Session.get('tweetLimit')});
	},
	noSearchTerm: function(){
		return Session.get("noSearchTerm");
	}
});

Template.tweetsList.onCreated(function () {
	// polls loklak tweets as REST API and uses as DDP
	// Meteor.call("clearTweets");
	if(Tweets.find().count()===0) {
		Session.set("noSearchTerm", true);
		Session.set("query", "Just another Twitter Wall");
	}
	$(".fixed-action-btn").fadeIn();
	$("#nav-mobile").removeClass("out");

	Session.set('tweetLimit', 50);
// 	var self = this;
// 	var apiURLSettings = Meteor.settings.public.apiURL; // localhost:9000/api
// console.log(apiURLSettings);
//
// 	self.autorun(function () {
// 		self.subscribe('REST2DDP', "loklak-tweets",{variables:{
// 			apiURL: apiURLSettings,
// 			queryString: ""
// 		}});
// });
});
