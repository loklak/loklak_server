angular.module('myApp', ['ngStorage', 'ngMaterial', 'ngMessages', 'angularGrid' ,'ngSanitize','dcbImgFallback', 'loklak'])


// dataservice.polltweets(@querytype, @queryterm) -> polls & adds to $localStorage
// $localStorage.tweets: stores tweet objs in array

// eg. api call ("/api/search.json?q=from:myhandle")
// querytype: eg. "search" (using /api/search.json)
// queryterm: eg. "from:myhandle" (refer to http://loklak.org/api.html for more)

.controller('MainCtrl', ['$scope', '$interval', '$localStorage', '$q','$sce', 'queryService',
    function($scope, $interval, $localStorage, $q, $sce, queryService) {
        var vm = this;
        var promise;
        
        $scope.apis = ('search suggest').split(' ').map(function(apitype) {
            return {type: apitype}
        })
        
        $scope.query = {
            type: 'search',
            term: 'loklak',
            interval: 1
        }
        
        $scope.$storage = $localStorage.$default({
          tweets: []
        })
        // $scope.$storage.tweets=[];
        
        // Angular Grid
        var vm = this;
        vm.limit = 25;
        vm.loadingMore = false;
        
        // Initial slice, extract from storage to vm.wallTweets
        vm.wallTweets = $scope.$storage.tweets.slice(0,vm.limit);
        // Watch for prepends to storage -> prepend to wallTweets
        $scope.$watch(
            function(){ 
                return $scope.$storage.tweets.length; 
            }, 
            function(newVal, oldVal){    
                // console.log("new tweet");
                var numNewTweets = newVal- oldVal;
                vm.limit += numNewTweets;
                // // console.log(newVal+', '+oldVal);
                if(newVal===0) return;
                if(oldVal===0){
                    // // console.log(vm.limit);
                    vm.wallTweets = $scope.$storage.tweets.slice(0,vm.limit);
                }else{
                    var holding = [];
                    while(numNewTweets>0){
                        var tweet = $scope.$storage.tweets[numNewTweets-1];
                        // // console.log(tweet);
                        holding.splice(0,0,tweet);
                        numNewTweets--;
                    }
                    vm.wallTweets = holding.concat(vm.wallTweets);
                    
                }
            }
        );
        // Watch if scrolled to end -> append 25 tweets to wallTweets
        vm.loadMoreTweets = function(){
            if(vm.loadingMore) return;
            vm.loadingMore = true;
            var promise =  $q.when($scope.$storage.tweets)
            .then(function(){
                vm.loadingMore = true;
                // console.log("loading from storage");
                var newLength = $scope.$storage.tweets.length;
                var oldLength = vm.wallTweets.length;
                var numNewTweets = 0;
                while(numNewTweets<25 ){
                    if(vm.wallTweets.length>=newLength) break;
                    var tweet = $scope.$storage.tweets[oldLength+numNewTweets];
                    vm.wallTweets.push(tweet);
                    numNewTweets++;
                }
                vm.loadingMore = false;

            })
            return promise;
        }

        $scope.handlify = function(handle){
            return $sce.trustAsHtml("<a href=\"https://twitter.com/" + handle + "\">@" + handle + "</a>");
        }
        $scope.rmvSpan = function(username) {
            return username.replace(/<img.*?false" alt="(.).*?>/g,"$1").replace(/&nbsp;/g," ");
        };
        $scope.poll = function(type, term, interval){
            
            // clear previous storage & display & interval
            $scope.$storage.tweets = [];
            $interval.cancel(promise);
            vm.wallTweets = [];
            vm.limit = 25
            
            // first call
            queryService.queryTweets(type, term);

            // poll every few minutes
            promise = $interval(function(){
                queryService.queryTweets(type, term);
            } , interval*60000);
        }
        
        $scope.tweetify = function(_str){
            if (!_str) {
                return;
            }
            // linkify
            var _text = _str.replace(
                /(https?:\/\/[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|])/gi,
                function($0) {
                    var innerhtml = $0;
                    if($0.length > 50){
                        innerhtml = $0.substring(0,50) + '...';
                    } 
                    return '<a href=\"' + $0 + '\">' + innerhtml + '</a>';
                });

            // check empty
            if (!_text) {
                return '';
            }
            
            // Twitter
            _text = _text.replace(/(|\s)*@([\u00C0-\u1FFF\w]+)/g, '$1<a href="https://twitter.com/$2" target="_blank">@$2</a>');
            _text = _text.replace(/(^|\s)*#([\u00C0-\u1FFF\w]+)/g, '$1<a href="https://twitter.com/search?q=%23$2" target="_blank">#$2</a>');
            return _text;
            
        }
    }
])

.config(function($mdThemingProvider) {
  // Configure a dark theme with primary foreground yellow
  $mdThemingProvider.theme('docs-dark', 'default')
    .primaryPalette('yellow')
    .dark();
});
