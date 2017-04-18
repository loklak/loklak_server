Meteor.methods({

  addTweetArr: function(tweetArr){
    tweetArr.forEach(function(tweet){
      var userScreenName = tweet.user.name;
      var userName = tweet.user.screen_name;
      var userTweet = tweet.text;
      var tweetDate = tweet.created_at;
      var profileImg = tweet.user.profile_image_url_https;
    //   console.log(tweet);

      var imgs = tweet.images;
      var links = tweet.links;

      var twitterPic = "";
      links.forEach(function(link){
          if(link.substring(8,23) == "pic.twitter.com"){
              twitterPic = extractMeta(link).image;
          }
      });
      var instaPic = "";
      imgs.forEach(function(img){
          if(img.substring(8,25) == "www.instagram.com"){
              instaPic = extractMeta(img).image;
          }
      })
      var image = "";
      if(twitterPic){
          image = twitterPic;
      } else {
          image = instaPic;
      }

      Tweets.insert({
          username: userName,
          userScreen: userScreenName,
          tweet: userTweet,
          userPicture: profileImg,
          date: tweetDate,
          image: image,
          images: imgs,
          links: links,
          uDate: moment(tweetDate).valueOf()
      },
      function(error){
          if(error) console.log(error);
      });
    });
  },

  clearTweets:function(){
    Tweets.remove({});
  },

  updateTweets: function(qString){

    SyncedCron.stop();
    // new syncedChron to diff the tweets without adding removing the whole collection
    SyncedCron.add({
      log: false,
      name: 'Parsing from loklak for ' + qString,
      schedule: function(parser) {
        return parser.text('every 30 sec');
      },
      job: function() {
        // get tweets from loklak
        Meteor.call("getLoklakTweets", qString, Meteor.settings.public.apiURL, function(er, res){

          // no res
          if(!res.length){
            Session.set("noSearchTerm", true);
            Session.set("query", "Search something else!");
          } else {

          var currTweetArr = Tweets.find({}, {sort: { uDate: -1 }}).fetch();
          // console.log(currTweetArr[0]);

          if(currTweetArr.length !== 0 ){
            var mostRecentDate = currTweetArr[0].uDate;
            var mostRecentText = currTweetArr[0].tweet;

            // diff
            for (var i = 0; i < res.length; i++) {
              var tweet = res[i];
              var userScreenName = tweet.user.name;
              var userName = tweet.user.screen_name;
              var userTweet = tweet.text;
              var tweetDate = tweet.created_at;
              var profileImg = tweet.user.profile_image_url_https;
              var imgs = tweet.images;
              var links = tweet.links;
              var unixDate = moment(tweetDate).valueOf();
              // only add newer tweets
              if( unixDate <= mostRecentDate || userTweet === mostRecentText) break;

              var twitterPic = "";
              links.forEach(function(link){
                  if(link.substring(8,23) == "pic.twitter.com"){
                      twitterPic = extractMeta(link).image;
                  }
              });
              var instaPic = "";
              imgs.forEach(function(img){
                  if(img.substring(8,25) == "www.instagram.com"){
                      instaPic = extractMeta(img).image;
                  }
              })
              var image = "";
              if(twitterPic){
                  image = twitterPic;
              } else {
                  image = instaPic;
              }

              Tweets.insert({
                  username: userName,
                  userScreen: userScreenName,
                  tweet: userTweet,
                  userPicture: profileImg,
                  date: tweetDate,
                  image: image,
                  images: imgs,
                  links: links,
                  uDate: unixDate
              },
              function(error){
                  if(error) console.log(error);
              });

            }

          } else {
            Meteor.call("addTweetArr", res);
          }
        }
        });
      }
    });

    SyncedCron.start();
  },

  // inserts tweets from loklak api using HTTP
  getLoklakTweets: function(query, apiURL){
    var loklakURL = apiURL + "/search.json?timezoneOffset=-480&q=" + query;
    // syncronous version of HTTP.call, w/o callback
    var result = HTTP.call( 'GET', loklakURL, {});
    return result.data.statuses;
    // return statusArr;

      //   statusArr.forEach(function(tweet){
      //     console.log(tweet);
      //     var userScreenName = tweet.user.name;
      //     var userName = tweet.user.screen_name;
      //     var userTweet = tweet.text;
      //     var tweetDate = tweet.created_at;
      //     var profileImg = tweet.user.profile_image_url_https;
      //     var imgArr = tweet.images;
      //     Tweets.insert({user: userName, userscreen: userScreenName, tweet: userTweet, picture: profileImg, date: tweetDate, images: imgArr},
      //       function(error){
      //           if(error)
      //           console.log(error);
      //     });
      //   })
      // }

  },

  // not in use: get tweet stream using meteor package, requires keys, set in custom_settings.json
  getTwitterTweets:function(query){
    var stream = T.stream('statuses/filter', { track: query })

    stream.on('tweet', Meteor.bindEnvironment(function (tweet) {
      var userScreenName = tweet.user.name;
      var userName = tweet.user.screen_name;
      var userTweet = tweet.text;
      var tweetDate = tweet.created_at;
      var profileImg = tweet.user.profile_image_url;
      var imgArr = tweet.images;

      console.log(userScreenName + " (" + userName + ")" + " said " + userTweet + " at " + tweetDate);
      console.log("=======================================");
      Tweets.insert({user: userName, userscreen: userScreenName, tweet: userTweet, picture: profileImg, date: tweetDate, images: imgArr}, function(error){
        if(error)
        console.log(error);
      });
    }))

  }


});
