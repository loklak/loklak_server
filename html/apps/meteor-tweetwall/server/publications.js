Meteor.publish('tweets', function() {
 return Tweets.find();
});
Meteor.publish('suggestions', function() {
 return Suggestions.find();
});
