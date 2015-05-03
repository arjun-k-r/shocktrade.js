(function () {
    // get the application reference
    var app = angular.module('shocktrade');

    /**
     * Sign-Up Service
     * @author lawrence.daniels@gmail.com
     */
    app.factory('SignUpDialog', function ($http, $log, $modal, $q) {
        var service = {};

        /**
         * Sign-up Modal Dialog
         */
        service.popup = function (fbUserID, successCB, errorCB) {
            // create an instance of the dialog
            var $modalInstance = $modal.open({
                controller: 'SignUpCtrl',
                templateUrl: 'sign_up.htm',
                resolve: {
                    fbUserID: function () {
                        return fbUserID;
                    }
                }
            });

            $modalInstance.result.then(function (form) {
                form.facebookID = fbUserID;
                service.createAccount(form).then(
                    function (response) {
                        if (successCB) successCB(response);
                    },
                    function (err) {
                        if (errorCB) errorCB(err);
                    }
                );
            }, function () {
                $log.info('Modal dismissed at: ' + new Date());
            });
        };

        service.createAccount = function (form) {
            $log.info("Creating account " + angular.toJson(form));
            var deferred = $q.defer();
            $http({
                method: "POST",
                url: "/api/profile/create",
                data: angular.toJson(form)
            })
                .success(function (data, status, headers, config) {
                    deferred.resolve(data);
                })
                .error(function (response) {
                    deferred.reject(response);
                });
            return deferred.promise;
        };

        return service;
    });

    /**
     * Sign-Up Controller
     * @author lawrence.daniels@gmail.com
     */
    app.controller('SignUpCtrl', ['$scope', '$modalInstance', function ($scope, $modalInstance) {
        $scope.form = {};

        $scope.ok = function () {
            $modalInstance.close($scope.form);
        };

        $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
        };
    }]);

})();