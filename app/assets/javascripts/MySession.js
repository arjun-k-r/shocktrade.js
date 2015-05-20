(function () {
    var app = angular.module('shocktrade');

    /**
     * My Session Service
     * @author lawrence.daniels@gmail.com
     */
    app.factory('MySession', function ($log, $timeout, toaster, ContestService) {
        var service = {
            contest: null
        };

        service.deduct = function (amount) {
            $log.info("Deducting " + amount + " from " + service.userProfile.netWorth);
            service.userProfile.netWorth -= amount;
        };

        /**
         * Indicates whether the given user is an administrator
         * @returns {boolean}
         */
        service.isAdmin = function () {
            return service.userProfile.admin === true;
        };

        /**
         * Indicates whether the user is logged in
         * @returns {boolean}
         */
        service.isAuthorized = function () {
            return service.getUserID() != null;
        };

        service.getContestID = function () {
            return service.contest ? service.contest.OID() : null;
        };

        service.setContest = function (contest) {
            service.contest = contest;
        };

        service.getNetWorth = function () {
            return service.userProfile.netWorth || 0;
        };

        service.getTotalCashAvailable = function () {
            return service.userProfile ? service.userProfile.netWorth : 0.00;
        };

        service.isTotalInvestmentLoaded = function () {
            return service.totalInvestment !== null && service.totalInvestment !== undefined;
        };

        service.getTotalInvestment = function () {
            // lookup the player
            if (!service.isTotalInvestmentLoaded() && !service.totalInvestmentStatus) {
                service.totalInvestmentStatus = "LOADING";

                // load the total investment
                loadTotalInvestment();
            }

            return service.isTotalInvestmentLoaded() ? service.totalInvestment : null;
        };

        function loadTotalInvestment() {
            // set a timeout so that loading doesn't persist
            $timeout(function () {
                if (!service.isTotalInvestmentLoaded()) {
                    $log.error("Total investment call timed out");
                    service.totalInvestmentStatus = "TIMEOUT";
                }
            }, 20000);

            // retrieve the total investment
            $log.info("Loading Total investment...");
            ContestService.getTotalInvestment(service.getUserID())
                .success(function (response) {
                    service.totalInvestment = response.netWorth;
                    service.totalInvestmentStatus = "LOADED";
                    $log.info("Total investment loaded");
                })
                .error(function (response) {
                    toaster.pop('error', 'Error loading total investment', null);
                    service.totalInvestmentStatus = "FAILED";
                    $log.error("Total failed");
                    service.totalInvestmentAttempts += 1;

                    // attempt to load the totalInvestment up to 3 times
                    if (service.totalInvestmentAttempts < 3) {
                        $timeout(function () {
                            $log.info("Requeuing total investment call...");
                            service.totalInvestmentStatus = null;
                            loadTotalInvestment();
                        }, 5000);
                    }
                });
        }

        /**
         * Returns the user ID for the current user
         * @returns {*}
         */
        service.getUserID = function () {
            return service.userProfile ? service.userProfile.OID() : null;
        };

        service.getUserName = function () {
            return service.userProfile.name;
        };

        /**
         * Logout function
         */
        service.logout = function () {
            service.authenticated = false;
            service.fbAuthenticated = false;
            service.fbUserID = null;
            service.fbFriends = [];
            service.fbProfile = {};
            service.userProfile = createSpectatorProfile();
        };

        /**
         * Creates a default 'Spectator' user profile
         * @returns {{name: string, country: string, level: number, lastSymbol: string, friends: Array, filters: *[]}}
         */
        function createSpectatorProfile() {
            return {
                name: "Spectator",
                country: "us",
                level: 1,
                lastSymbol: "MSFT",
                friends: [],
                filters: [{
                    "_id": {"$oid": "5383e53bb90bd6654e3175ad"},
                    name: "Most Active",
                    sortField: "VOLUME",
                    ascending: false,
                    maxResults: 25,
                    conditions: [{
                        field: "VOLUME",
                        operator: ">=",
                        value: 1000000
                    }],
                    headers: ["Symbol", "Last", "Change %", "Volume"],
                    columns: ["symbol", "lastTrade", "changePct", "volume"]
                }, {
                    "_id": {"$oid": "5249eb7ae4b08a1467688d05"},
                    name: "Top Gains",
                    sortField: "CHANGE",
                    ascending: false,
                    maxResults: 25,
                    conditions: [{
                        field: "CHANGE",
                        operator: ">=",
                        value: 25
                    }],
                    headers: ["Symbol", "Last", "Change %", "Volume"],
                    columns: ["symbol", "lastTrade", "changePct", "volume"]
                }, {
                    "_id": {"$oid": "5383e32fb90bd6654e3175ab"},
                    name: "Top Losses",
                    sortField: "CHANGE",
                    ascending: true,
                    maxResults: 25,
                    conditions: [{
                        field: "CHANGE",
                        operator: "<",
                        value: 0
                    }],
                    headers: ["Symbol", "Last", "Change %", "Volume"],
                    columns: ["symbol", "lastTrade", "changePct", "volume"]
                }, {
                    "_id": {"$oid": "5383e3acb90bd6654e3175ac"},
                    name: "Top Spread",
                    sortField: "SPREAD",
                    ascending: true,
                    maxResults: 25,
                    conditions: [{
                        field: "SPREAD",
                        operator: ">=",
                        value: 25
                    }],
                    headers: ["Symbol", "Last", "Spread %", "Change %", "Volume"],
                    columns: ["symbol", "lastTrade", "spread", "changePct", "volume"]
                }]
            }
        }

        (function () {
            // initialize the values as a logged-out user
            service.logout();
        })();

        return service;
    });

})();