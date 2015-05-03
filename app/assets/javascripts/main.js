(function () {

    /**
     * Main Controller
     * @author lawrence.daniels@gmail.com
     */
    angular.module('shocktrade')
        .controller('MainCtrl', ['$scope', '$interval', '$location', '$log', '$timeout', 'Errors', 'Facebook', 'FavoriteSymbols', 'HeldSecurities', 'MarketStatus', 'MySession', 'ProfileService', 'SignUpDialog',
            function ($scope, $interval, $location, $log, $timeout, Errors, Facebook, FavoriteSymbols, HeldSecurities, MarketStatus, MySession, ProfileService, SignUpDialog) {
                // setup the loading mechanism
                $scope._loading = false;

                $scope.isLoading = function () {
                    return $scope._loading
                };

                $scope.startLoading = function (timeout) {
                    $scope._loading = true;
                    var _timeout = timeout || 4000;

                    /*
                     // set loading timeout
                     var promise = $timeout(function() {
                     console.log("Disabling the loading animation due to time-out (" + _timeout + " msec)...");
                     $scope.loading = false;
                     }, _timeout);*/
                };

                $scope.stopLoading = function () {
                    $timeout(function () {
                        $scope._loading = false;
                    }, 500);
                };

                // setup the market clock
                $scope.marketClock = (new Date()).toTimeString();

                // setup main-specific variables
                $scope.admin = false;
                $scope.tabs = [{
                    "name": "Play",
                    "imageURL": "/assets/images/links/simulation_header.gif",
                    "path": "/play/search",
                    "active": false
                }, {
                    "name": "Connect",
                    "imageURL": "/assets/images/objects/friend_header.gif",
                    "path": "/connect",
                    "active": false
                }, {
                    "name": "Discover",
                    "imageURL": "/assets/images/objects/stock_header.png",
                    "path": "/discover",
                    "active": false
                }, {
                    "name": "Explore",
                    "imageURL": "/assets/images/objects/overview.png",
                    "path": "/explore",
                    "active": false
                }, {
                    "name": "Research",
                    "imageURL": "/assets/images/buttons/search.png",
                    "path": "/search",
                    "active": false
                }, {
                    "name": "News",
                    "imageURL": "/assets/images/objects/headlines.png",
                    "path": "/news",
                    "active": false
                }];

                /**
                 * Sets the main application tab
                 */
                $scope.setMainTab = function (tabIndex) {
                    var tab = $scope.tabs[tabIndex];
                    console.log("Setting main tab to '" + tab.name + "'");
                    $location.path(tab.path);
                    $scope.changeTab(tabIndex);
                };

                /**
                 * Initializes the application
                 */
                $scope.appInit = function () {
                    // set the active tab
                    var tabIndex = determineTab();
                    $scope.tabs[tabIndex].active = true;

                    // setup market status w/updates
                    $interval(function () {
                        $scope.marketClock = (new Date()).toTimeString();
                    }, 1000);

                    // setup the market status updates
                    setupMarketStatusUpdates();
                };

                $scope.alertMessage = function (message) {
                    $("#alert_placeholder").html(
                        '<div class="alert">' +
                        '<a class="close" data-dismiss="alert">x</a>' +
                        '<span>' + message + '</span>' +
                        '</div>');
                };

                $scope.range = function (n) {
                    return new Array(n);
                };

                $scope.facebookLoginStatus = function (fbUserID) {
                    $scope.postLoginUpdates(fbUserID, false);
                };

                $scope.login = function (event) {
                    if (event) {
                        event.preventDefault();
                    }
                    Facebook.login().then(
                        function (response) {
                            var fbUserID = response.authResponse.userID;
                            $scope.postLoginUpdates(fbUserID, true);
                        },
                        function (err) {
                            $log.error("main:login err = " + angular.toJson(err));
                        });
                };

                $scope.logout = function (event) {
                    if (event) {
                        event.preventDefault();
                    }
                    Facebook.logout();
                    MySession.logout();
                };

                $scope.postLoginUpdates = function (fbUserID, userInitiated) {
                    // capture the user ID
                    MySession.fbUserID = fbUserID;

                    // load the user's Facebook profile
                    Facebook.getUserProfile().then(
                        function (response) {
                            MySession.fbProfile = response;
                            MySession.fbAuthenticated = true;
                        },
                        function (err) {
                            Errors.addMessage("Facebook login error - " + err.data);
                        });

                    // load the user's ShockTrade profile
                    ProfileService.getProfileByFacebookID(fbUserID).then(
                        function (profile) {
                            if (!profile.error) {
                                MySession.userProfile = profile;
                                MySession.authenticated = true;

                                loadFacebookFriends();
                                $scope.filters = MySession.userProfile.filters;
                            }
                            else {
                                MySession.nonMember = true;
                                signUpPopup(fbUserID, profile, userInitiated);
                            }
                        },
                        function (err) {
                            Errors.addMessage("ShockTrade Profile retrieval error - " + err.data);
                            signUpPopup(fbUserID, profile, userInitiated);
                        }
                    );
                };

                function loadFacebookFriends() {
                    Facebook.getTaggableFriends().then(
                        function (response) {
                            // $log.info("FaceBook friends = " + angular.toJson(response.data, true));
                            MySession.fbFriends = response.data.sort(function (a, b) {
                                if (a.name < b.name) return -1;
                                else if (a.name > b.name) return 1;
                                else return 0;
                            });
                        },
                        function (err) {
                            Errors.addMessage("Failed to retrieve Facebook friends - " + err.data);
                        });
                }

                function signUpPopup(fbUserID, profile, userInitiated) {
                    if (userInitiated) {
                        SignUpDialog.popup(fbUserID,
                            function (profile) {
                                MySession.userProfile = profile;
                                MySession.authenticated = true;
                            },
                            function (err) {
                                Errors.addMessage("ShockTrade Profile retrieval error - " + err.data);
                            });
                    }
                }

                $scope.isTab = function (tabName) {
                    var path = $location.path();
                    return tabName.indexOf(tabName) != -1;
                };

                $scope.updateTabs = function () {
                    // determine the tab index
                    var tabIndex = determineTab();

                    // deactivate all tabs
                    for (var i = 0; i < $scope.tabs.length; i++) {
                        $scope.tabs[i].active = false;
                    }

                    // select the one we want
                    $scope.tabs[tabIndex].active = true;
                };

                $scope.abs = function (value) {
                    return !value ? value : ((value < 0) ? -value : value);
                };

                $scope.changeTab = function (tabIndex, event) {
                    // deactivate all tabs
                    for (var n = 0; n < $scope.tabs.length; n++) {
                        $scope.tabs[n].active = false;
                    }

                    // select the one we want
                    $scope.tabs[tabIndex].active = true;

                    // change the address bar
                    var tab = $scope.tabs[tabIndex];
                    if (tab.path) {
                        // change the view
                        console.log("Changing view from '" + $location.path() + "' to '" + tab.path + "'");
                        $location.path(tab.path);

                        // prevent the default action
                        if (event) {
                            event.preventDefault();
                        }
                    }
                };

                $scope.getExchangeClass = function(exchange) {
                    if(exchange == null) return null;
                    else {
                        var name = exchange.toUpperCase();
                        if (name.indexOf("NASD") != -1) return "NASDAQ";
                        if (name.indexOf("OTC") != -1) return "OTCBB";
                        else return name;
                    }
                };

                $scope.getPreferenceIcon = function (q) {
                    // fail-safe
                    if (!q || !q.symbol) return "transparent12.png";

                    // check for favorite and held securities
                    var symbol = q.symbol;
                    if (HeldSecurities.isHeld(symbol)) return "star.png";
                    else if (FavoriteSymbols.isFavorite(symbol)) return "favorite_small.png";
                    else return "transparent12.png";
                };

                function determineTab() {
                    // determine the right one
                    var tabIndex = 0;
                    var path = $location.path();
                    if (path.indexOf("/play") != -1) tabIndex = 0;
                    else if (path.indexOf("/connect") != -1) tabIndex = 1;
                    else if (path.indexOf("/discover") != -1) tabIndex = 2;
                    else if (path.indexOf("/explore") != -1) tabIndex = 3;
                    else if (path.indexOf("/search") != -1) tabIndex = 4;
                    else if (path.indexOf("/news") != -1) tabIndex = 5;
                    else if (path.indexOf("/blog") != -1) {
                        if ($scope.admin) tabIndex = 6;
                        else tabIndex = 0;
                    }
                    else tabIndex = 0;

                    // activate the tab
                    return tabIndex;
                }

                function setupMarketStatusUpdates() {
                    $scope.usMarketsOpen = null;
                    $log.info("Retrieving market status...");
                    MarketStatus.getMarketStatus(function (response) {
                        // retrieve the delay in milliseconds from the server
                        var delay = response.delay;
                        if (delay < 0) {
                            delay = response.end - response.sysTime;
                            if (delay <= 300000) {
                                delay = 300000; // 5 minutes
                            }
                        }

                        // set the market status
                        $log.info("US Markets are " + (response.active ? 'Open' : 'Closed') + "; Waiting for " + delay + " msec until next trading start...");
                        setTimeout(function () {
                            $scope.usMarketsOpen = response.active;
                        }, 750);

                        // wait for the delay, then call recursively
                        setTimeout(function () {
                            setupMarketStatusUpdates();
                        }, delay);
                    });
                }

                // watch for changes to the player's profile
                $scope.$watch("MySession.userProfile", function () {
                    if (!$scope.admin && (MySession.userProfile || {}).name === "ldaniels") {
                        $scope.admin = true;
                        $scope.tabs.push({
                            "name": "Blog",
                            "imageURL": "/assets/images/objects/blog.png",
                            "path": "/blog",
                            "active": false
                        });
                    }
                });

            }]);
})();