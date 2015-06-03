(function () {
    var app = angular.module('shocktrade');

    /**
     * Profile Controller
     * @author lawrence.daniels@gmail.com
     */
    app.controller('ProfileController', ['$scope', '$location', '$log', function ($scope, $location, $log) {
        $scope.profileTabs = [{
            "name": "My Awards",
            "path": "/profile/awards",
            "icon": "fa fa-trophy",
            "active": false
        }, {
            "name": "My Statistics",
            "path": "/profile/statistics",
            "icon": "fa fa-bar-chart",
            "active": false
        }];

        $scope.initProfile = function () {
            setActiveTab(profileTabIndex());
        };

        $scope.changeProfileTab = function (index) {
            if (index < $scope.profileTabs.length) {
                $location.url($scope.profileTabs[index].path);
            }
        };

        function profileTabIndex() {
            var path = $location.url();
            if (path.indexOf('awards') !== -1) return 0;
            else if (path.indexOf('statistics') !== -1) return 1;
            else return 0;
        }

        function setActiveTab(index) {
            var n = 0;
            angular.forEach($scope.profileTabs, function (tab) {
                tab.active = (n++ === index);
            });
        }

    }]);

})();