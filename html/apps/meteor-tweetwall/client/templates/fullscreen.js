Template.fullscreen.helpers({
  gridList: function(){
    return Tweets.find({}, {sort: { uDate: -1 }, limit: Session.get('tweetLimit')});
  }
});

Template.fullscreen.rendered = function(){
  var $grid = $('.grid');
  var $s = $grid.isotope({
    itemSelector: '.grid-item',
    percentPosition: true,
    columnWidth: '.grid-sizer'
    })
    Session.set("gridInit", true);
  $s.imagesLoaded().progress( function() {
    $s.isotope('layout');
  });


}

Template.fullscreen.onCreated(function () {
  $(".fixed-action-btn").fadeOut();
  $("#nav-mobile").addClass("out");
  Session.set('tweetLimit', 60);
});

/* GRID ITEM */
Template.gridItem.helpers({
    tweetLink: function(){
        // replace twitter handles with links
        var re = /@(\w+)/g;
        function replacer(match, p1){
            return "<a href=\"https://twitter.com/" + p1 + "\">@" + p1 + "</a>";
        }
        return this.tweet.replace(re, replacer);
    },
    showTweet: function(){
        return Session.get("showTweet") === this._id;
    }
})

Template.gridItem.rendered = function(){
    $('p.black-text').linkify({
        format: function (value, type) {
            if (type === 'url' && value.length > 50) {
                value = value.slice(0, 50) + '…';
            }
            return value;
        }
    });

    if(Session.get("gridInit")){
        var $grid = $('.grid');
        var $s = $grid.isotope({
          itemSelector: '.grid-item',
          percentPosition: true,
          columnWidth: '.grid-sizer'
          })
        $s.imagesLoaded().progress( function() {
          $s.isotope('layout');
        });
        $('.grid').isotope('reloadItems');

    };
        // console.log($('.black-text'));


}

Template.gridItem.events({
    "mouseenter .wallPic": function(event, template){
        Session.set("showTweet", this._id);
        // console.log(this._id);

        // console.log($(event.target.id));
        // $("#" + event.target.id).removeClass("hidden");
    },
    "mouseleave .wallPic": function(event, template){
        Session.set("showTweet", "");
        // $("#"+event.target.id).addClass("hidden");
    }
});
