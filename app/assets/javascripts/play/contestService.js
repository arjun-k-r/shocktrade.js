/**
 * Contest Service
 * @author lawrence.daniels@gmail.com
 */
angular
    .module('shocktrade')
    .factory('ContestService', function($http, $log, $q, Errors, QuoteService) {
        var service = {};

        service.createContest = function(form) {
            return $http({method: "PUT", url: "/api/contest", data: angular.toJson(form)});
        };

        service.deleteContest = function(contestId) {
            return $http.delete('/api/contest/' + contestId);
        };

        service.getContestByID = function(contestId) {
            return $http.get("/api/contest/" + contestId);
        };

        service.getParticipantByID = function(contestId, playerId) {
            return $http.get("/api/contest/" + contestId + "/player/" + playerId);
        };

        service.getRankings = function(contestId) {
            return $http.get("/api/contest/" + contestId + "/rankings");
        };

        service.getContestsByPlayerID = function(playerId) {
            return $http.get('/api/contests/player/' + playerId).then(function(response) {
                var contests = response.data;
                return contests.map(insertID);
            })
        };

        service.findContests = function (searchOptions) {
            return $http({method: 'POST', url: '/api/contests/search', data: angular.toJson(searchOptions)})
                .then(function(response) {
                    var contests = response.data;
                    return contests.map(insertID)
                });
        };

        service.joinContest = function(contestId, playerInfo) {
            return $http({method : 'PUT', url : "/api/contest/" + contestId + "/player", data : angular.toJson(playerInfo)});
        };

        service.quitContest = function(contestId, playerId) {
            return $http.delete('/api/contest/' + contestId + "/player/" + playerId);
        };

        service.startContest = function(contestId) {
            return $http.get("/api/contest/" + contestId + "/start");
        };

        /////////////////////////////////////////////////////////////////////////////
        //			Miscellaneous
        /////////////////////////////////////////////////////////////////////////////

        service.getRestrictions = function() {
            return $http.get("/api/contests/restrictions");
        };

        service.getChart = function(contestId, participantName, chartName) {
            // build the appropriate URL
            var uriString = (chartName == "gains" || chartName == "losses")
                ? "/api/charts/performance/" + chartName + "/" + contestId + "/" + participantName
                : "/api/charts/exposure/" + chartName + "/" + contestId + "/" + participantName;

            // load the chart representing the securities
            return $http.get(uriString).then(function(response) {
                return response.data;
            });
        };

        /////////////////////////////////////////////////////////////////////////////
        //			Chat
        /////////////////////////////////////////////////////////////////////////////

        service.sendChatMessage = function(contestId, message) {
            return $http({
                method : 'PUT',
                url : '/api/contest/' + contestId + '/chat',
                data : angular.toJson(message)
            });
        };

        /////////////////////////////////////////////////////////////////////////////
        //			Positions & Orders
        /////////////////////////////////////////////////////////////////////////////

        service.createOrder = function(contestId, playerId, order) {
            return $http({method : "PUT", url : "/api/order/" + contestId + "/" + playerId, data : angular.toJson(order)});
        };

        service.deleteOrder = function(contestId, playerId, orderId) {
            return $http.delete('/api/order/' + contestId + '/' + playerId + '/' + orderId);
        };

        service.getHeldSecurities = function(playerId) {
            return $http.get("/api/positions/" + playerId);
        };

        service.orderQuote = function(symbol) {
            $log.info("Loading symbol " + symbol + "'...");
            return $http.get('/api/quotes/order/symbol/' + symbol)
                .then(function(response) {
                    var quote = response.data;
                    if(quote.symbol) {
                        $log.info("Setting lastSymbol as " + quote.symbol);
                        QuoteService.lastSymbol = quote.symbol;
                    }
                    return quote;
                });
        };

        return service;
    });