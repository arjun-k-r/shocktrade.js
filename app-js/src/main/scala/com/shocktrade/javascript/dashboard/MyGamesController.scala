package com.shocktrade.javascript.dashboard

import biz.enef.angulate.core.{HttpError, Timeout}
import biz.enef.angulate.{Scope, ScopeController, named}
import com.ldaniels528.angularjs.Toaster
import com.shocktrade.javascript.MySession
import com.shocktrade.javascript.ScalaJsHelper._

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g, literal => JS}
import scala.scalajs.js.annotation.JSExportAll
import scala.util.{Failure, Success}

/**
 * My Games Controller
 * @author lawrence.daniels@gmail.com
 */
@JSExportAll
class MyGamesController($scope: Scope, $timeout: Timeout, toaster: Toaster,
                        @named("ContestService") contestService: ContestService,
                        @named("MySession") mySession: MySession,
                        @named("NewGameDialog") newGameDialog: js.Dynamic) extends ScopeController {

  private var myContests = js.Array[js.Dynamic]()
  private val scope = $scope.asInstanceOf[js.Dynamic]

  ///////////////////////////////////////////////////////////////////////////
  //          Scope Functions
  ///////////////////////////////////////////////////////////////////////////

  scope.initMyGames = () => mySession.userProfile.OID_? foreach loadMyContests

  scope.getMyContests = () => myContests

  scope.getMyRankings = (contest: js.Dynamic) => {
    if (!isDefined(contest)) JS()
    else {
      val rankings = contestService.getPlayerRankings_@(contest, mySession.userProfile.name.as[String])
      if (isDefined(rankings) && isDefined(rankings.player)) rankings.player else JS()
    }
  }

  scope.newGamePopup = () => {
    newGameDialog.popup(JS(
      success = { contest: js.Dynamic => myContests.push(contest) },
      error = { err: HttpError => toaster.pop("error", "Failed to create game", null) }
    ))
  }

  ///////////////////////////////////////////////////////////////////////////
  //          Private Methods
  ///////////////////////////////////////////////////////////////////////////

  private def loadMyContests(userID: String) = {
    // initially, reset the player's contests
    myContests = emptyArray

    if (mySession.isAuthenticated_@) {
      g.console.log(s"Loading 'My Contests' for user '$userID'...")
      contestService.getContestsByPlayerID_@(userID) onComplete {
        case Success(contests: js.Array[js.Dynamic]) =>
          g.console.log(s"Loaded ${contests.length} contest(s)")
          myContests = contests
        case Failure(response: HttpError) =>
          toaster.pop("error", "Failed to load 'My Contests'", null)
      }
    }
    myContests
  }

  ///////////////////////////////////////////////////////////////////////////
  //          Event Listeners
  ///////////////////////////////////////////////////////////////////////////

  /**
   * Listen for contest creation events
   */
  $scope.$on("contest_created", (event: js.Dynamic, contest: js.Dynamic) => loadMyContests(mySession.userProfile.OID))

  /**
   * Listen for contest deletion events
   */
  $scope.$on("contest_deleted", (event: js.Dynamic, contest: js.Dynamic) => loadMyContests(mySession.userProfile.OID))

  /**
   * Listen for user profile changes
   */
  $scope.$watch(mySession.getUserID, (newUserID: String, oldUserID: String) => loadMyContests(newUserID))

}
