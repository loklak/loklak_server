Template.tweetsItem.helpers({
    created_date: function(){
        return moment(this.date).fromNow().toUpperCase();
    },
    displayImgs: function(){
        if(this.image){
            return "<img class=\"materialboxed responsive-img\" width=\"auto\" src="+ this.image +">";
        }
    },
    tweetLink: function(){

        // replace twitter handles with links
        var re = /@(\w+)/g;
        function replacer(match, p1){
            return "<a href=\"https://twitter.com/" + p1 + "\">@" + p1 + "</a>";
        }
        return this.tweet.replace(re, replacer);
    }
});

Template.tweetsItem.rendered = function(){
    Session.set("gettingTweets", false);

    // $(document).ready(function(){
        $('.materialboxed').materialbox();
        // $('.material-placeholder').remove();
        $('p.black-text').linkify({
            format: function (value, type) {
                if (type === 'url' && value.length > 30) {
                    value = value.slice(0, 30) + '…';
                }
                return value;
            }
        });
    // });
}

Template.tweetsItem.events({
    "mouseenter .card-panel": function(event, template){
        template.$('.materialboxed').materialbox();
    },
    "mouseleave .card-panel": function(event, template){
        $("#materialbox-overlay").add();
        $("#materialbox-overlay").remove();



    }
});
