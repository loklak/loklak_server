var app = angular.module('appListApp', ['loklak']);

app.controller("app_list", function($scope, $http) {
    $scope.apps = [];
    $scope.categoryKeys = [];
    $http.jsonp('/api/apps.json?callback=JSON_CALLBACK')
    .success(function(data, status, headers, config) {
        $scope.categoryKeys = data.categories;
        $scope.apps = data.apps;
    });
    console.log($scope.apps);

    $scope.categoryFilter = function(event) {
        console.log(event.target.id);
        item = event.target.id;
        item = item.replace(/ /g, '');
        $('div.span2').hide();
        qConstruct = '#'+item;
        $(qConstruct).show();
    }
});

app.filter('nospace', function () {
    return function (value) {
        return (!value) ? '' : value.replace(/ /g, '');
    };
});

angular.module("loklak")
.config(function ($routeProvider) {
    $routeProvider.
    when("#AboutProject", { 
        controller: "init('About project')",
        activetab: "AboutProject"
    }).
    when("#Demo", {
        controller: "init('Demo')",
        activetab: "Demo"
    }).
    when("#MessageSearch", {
        controller: "init('Message Search')",
        activetab: "MessageSearch"
    }).
    when("#TweetsAnalytics", {
        controller: "init('Tweets analytics')",
        activetab: "TweetsAnalytics"

    }).
    when("#MessageVisualizer", {
        controller: "init('Message Visualizer')",
        activetab: "MessageVisualizer"
    }).
    when("#AccountsAPI", {
        controller: "init('Accounts API')",
        activetab: "AccountsAPI"

    }).
    when("#PeersAPI", {
        controller: "init('Peers API')",
        activetab: "PeersAPI"
    }).
    when("#SuggestionSearch", {
        controller: "init('Suggestion Search')",
        activetab: "SuggestionSearch"

    }).
    when("#TweetSearch", {
        controller: "init('Tweet search')",
        activetab: "TweetSearch"
    }). 
    when("#All", {
        controller: "init('All')",
        activetab: "All"
    }).
    when("#UsersAPI", {
        controller: "init('Users API')",
        activetab: "UsersAPI"
    });
});
