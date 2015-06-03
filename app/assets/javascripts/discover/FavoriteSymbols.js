(function () {
    var app = angular.module('shocktrade');

    /**
     * Favorite Symbols Service
     * @author lawrence.daniels@gmail.com
     */
    app.factory('FavoriteSymbols', function ($rootScope, $http, $log, $q, toaster, MySession) {
        var loaded = false;
        var service = {
            symbols: [],
            quotes: []
        };

        service.getQuotes = function () {
            return service.quotes;
        };

        service.isEmpty = function () {
            return !service.symbols.length;
        };

        service.isFavorite = function (symbol) {
            return indexOf(symbol) != -1;
        };

        service.add = function (symbol) {
            var index = indexOf(symbol);
            if (index == -1) {
                // get the user ID
                var id = MySession.getUserID();
                if (id) {
                    // add the symbol to the profile's Favorites
                    $http.put('/api/profile/' + id + '/favorite/' + symbol)
                        .success(function (response) {
                            service.symbols.unshift(symbol);
                            loadQuotes(service.symbols);
                        })
                        .error(function (response) {
                            $log.error("Failed to remove " + symbol + " from favorites: " + response.error)
                        });
                }
            }
        };

        service.remove = function (symbol) {
            var index = indexOf(symbol);
            if (index != -1) {
                // get the user ID
                var id = MySession.getUserID();
                if (id) {
                    // remove the symbol from the profile's Favorites
                    $http.delete('/api/profile/' + id + '/favorite/' + symbol)
                        .success(function (response) {
                            service.symbols.splice(index, 1);
                            loadQuotes(service.symbols);
                        })
                        .error(function (response) {
                            $log.error("Failed to remove " + symbol + " from favorites: " + response.error)
                        });
                }
            }
        };

        service.setSymbols = function (symbols) {
            service.symbols = symbols;
            loadQuotes(symbols);
        };

        service.getQuotes = function () {
            return service.quotes;
        };

        service.updateQuote = function (quote) {
            for (var n = 0; n < service.quotes.length; n++) {
                if (service.quotes[n].symbol == quote.symbol) {
                    service.quotes[n] = quote;
                    return;
                }
            }
        };

        function indexOf(symbol) {
            for (var n = 0; n < service.symbols.length; n++) {
                if (symbol == service.symbols[n]) {
                    return n;
                }
            }
            return -1;
        }

        function loadQuotes(symbols) {
            console.log("Loading symbols - " + angular.toJson(symbols));
            return $http.post('/api/quotes/list', symbols)
                .success(function (quotes) {
                    $log.info("Loaded " + quotes.length + " quote(s)");
                    service.quotes = quotes;
                })
                .error(function(xhr, error, status) {
                    toaster.pop('error', 'Error loading quots', null);
                });
        }

        /**
         * Listen for quote updates
         */
        $rootScope.$on("quote_updated", function (event, quote) {
            service.updateQuote(quote);
        });

        return service;
    });

})();