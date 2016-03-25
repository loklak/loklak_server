Template.sidebar.rendered = function () {
  $(".button-collapse").sideNav({
    // menuWidth: 300, // Default is 240
  });
};


Template.suggestionList.events({
  "click .suggestion": function(event, template){
    console.log(event);
    var clickId = event.currentTarget.id;
    Session.setPersistent("query", clickId);
    Session.set("noSearchTerm", false);
    Session.set("gettingTweets", true);

    // remove all docs in Tweets Collection
    Meteor.call("clearTweets");
    Meteor.call("updateTweets", clickId.replace(/ /g, "+"), function(){
        Session.set("gettingTweets", false);
    });

  }
});

Template.suggestionList.helpers({
  suggestList: function(){
    return Suggestions.find();
  }
});

Template.suggestionList.onCreated(function(){

  var self = this;
  	var apiURLSettings = Meteor.settings.public.apiURL; // localhost:9000/api
  console.log(apiURLSettings);

  	self.autorun(function () {
  		self.subscribe('REST2DDP', "loklak-suggest",{variables:{
  			apiURL: apiURLSettings
  		}});
  });

})
