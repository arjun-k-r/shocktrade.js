package com.shocktrade.javascript.profile

import biz.enef.angulate._
import biz.enef.angulate.core.Timeout
import com.ldaniels528.angularjs.Toaster
import com.shocktrade.javascript.MySession
import com.shocktrade.javascript.ScalaJsHelper._

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g, literal => JS}
import scala.util.{Failure, Success}

/**
 * Home Controller
 * @author lawrence.daniels@gmail.com
 */
class HomeController($scope: js.Dynamic, $timeout: Timeout, toaster: Toaster,
                     @named("MySession") mySession: MySession,
                     @named("ProfileService") profileService: ProfileService)
  extends ScopeController {

  private var selectedFriend: js.Dynamic = null

  /////////////////////////////////////////////////////////////////////////////
  //			Public Functions
  /////////////////////////////////////////////////////////////////////////////

  $scope.initHome = () => {
    $timeout(() =>
      if (selectedFriend == null) {
        mySession.fbFriends.headOption foreach (selectedFriend = _)
      }, 5.seconds)
  }

  $scope.getAwards = () => mySession.userProfile.awards.asArray[js.Dynamic]

  $scope.getFriends = () => mySession.fbFriends

  $scope.getNextLevelXP = () => mySession.userProfile.nextLevelXP.asOpt[Double].getOrElse(0d)

  $scope.getSelectedFriend = () => selectedFriend

  $scope.selectFriend = (friend: js.Dynamic) => selectFriend(friend)

  $scope.getStars = () => js.Array(1 to mySession.userProfile.rep.asOpt[Int].getOrElse(3): _*)

  $scope.getTotalXP = () => mySession.userProfile.totalXP.asOpt[Double].getOrElse(0d)

  /////////////////////////////////////////////////////////////////////////////
  //			Private Functions
  /////////////////////////////////////////////////////////////////////////////

  private def selectFriend = (friend: js.Dynamic) => {
    g.console.log(s"selecting friend ${toJson(friend)}")
    selectedFriend = friend
    if (!isDefined(friend.profile)) {
      profileService.getProfileByFacebookID(friend.userID.as[String]) onComplete {
        case Success(profile) =>
          friend.profile = profile
        case Failure(e) =>
          friend.profile = JS()
          friend.error = e.getMessage
          g.console.error(s"Error loading profile for ${friend.userID}: ${e.getMessage}")
      }
    }
  }

}
