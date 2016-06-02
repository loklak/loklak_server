var app = angular.module('appListApp', ['loklak']);

app.controller("app_list", function($scope, $http) {
    $scope.apps = [];
    $scope.categoryKeys = [];
    $http.jsonp('/api/apps.json?callback=JSON_CALLBACK')
    .success(function(data, status, headers, config) {
        $scope.categoryKeys = data.categories;
        $scope.apps = data.apps;
        $scope.categoryKeys.unshift('All');
    });

    $scope.categoryFilter = function(event) {
        item = event.target.id;
        if (item != 'All') {
            $('div.span2').hide();
            qConstruct = 'div.span2#'+item;
            $(qConstruct).show();
        }
        else {
            $('div.span2').show();
        }
    }
});

app.filter('nospace', function () {
    return function (value) {
        return (!value) ? '' : value.replace(/ /g, '');
    };
});
