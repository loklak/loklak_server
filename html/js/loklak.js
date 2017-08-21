var app = angular.module("loklak", ["ngRoute"]);
app.controller("status", function ($scope, $http) {
    $http.get("api/status.json").success(function (data, status, headers, config) {
        $scope.index = data.index;
        $scope.commit = data.commit;
        // Max 40 chars
        $scope.commit.comment = $scope.commit.comment.length > 50 ?
            $scope.commit.comment.substring(0, 47) + "..." : $scope.commit.comment;
    });
});

app.controller("search", function ($scope, $http) {
    $scope.query = "";
    $scope.results = [];
    $scope.search = function () {
        if ($scope.query != "") {
            $scope.results = [];
            $http.get("/api/search.json?q=" + $scope.query).success(function (data, status, navs, config) {
                for (var i = 0; i < data.statuses.length; i++) {
                    $scope.results.push(data.statuses[i].text);
                }
            });
        }
    }
});

app.filter("reverse", function () {
    return function (items) {
        if (!items || !items.length) {
            return;
        }
        return items.slice().reverse();
    };
});

angular.element(document).ready(function () {
    var navString = "";
    var winLocation = window.location.href;
    $.getJSON("/cms/topmenu.json", function (data) {
        navItems = data.items;
        navItems = navItems.reverse();
        var count = 0;
        $.each(navItems, function (index, itemData) {
            name = Object.keys(itemData);
            link = itemData[name];
            // Now construct the li items
            liItem = "<li>";
            if (winLocation.indexOf(link) != -1 && count != 1) {
                liItem = "<li class=\"active\">";
                count = count + 1;
            }
            if (name !== "Download") {
                liItem += "<a href=\"" + link + "\">" + name + "</a></li>";
            }
            liItem = $(liItem);
            $("#navbar > ul").prepend(liItem);
        });
    });
});

var didScroll;
var lastScrollTop = 0;
var delta = 5;
var navbarHeight = $("nav").outerHeight();

$(window).scroll(function (event) {
    didScroll = true;
});

function hasScrolled() {
    var st = $(this).scrollTop();
    if (Math.abs(lastScrollTop - st) <= delta)
        return;
    if (st > lastScrollTop && st > navbarHeight + 80) {
        $("nav").removeClass("nav-down").addClass("nav-up");
    } else { // if (st + $(window).height() < $(document).height()) {
        $("nav").removeClass("nav-up").addClass("nav-down");
    }
    lastScrollTop = st;
}

setInterval(function () {
    if (didScroll) {
        hasScrolled();
        didScroll = false;
    }
}, 100);
