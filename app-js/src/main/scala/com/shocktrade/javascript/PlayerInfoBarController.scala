package com.shocktrade.javascript

import biz.enef.angulate.core.{HttpService, Timeout}
import biz.enef.angulate.{ScopeController, named}
import com.ldaniels528.angularjs.Toaster
import com.shocktrade.javascript.PlayerInfoBarController._
import com.shocktrade.javascript.ScalaJsHelper._
import com.shocktrade.javascript.dashboard.ContestService

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g, literal => JS}
import scala.util.{Failure, Success}

/**
 * Player Information Bar Controller
 * @author lawrence.daniels@gmail.com
 */
class PlayerInfoBarController($scope: js.Dynamic, $http: HttpService, $timeout: Timeout, toaster: Toaster,
                              @named("ContestService") contestService: ContestService,
                              @named("MySession") mySession: MySession)
  extends ScopeController {

  private var totalInvestmentStatus: Option[String] = None
  private var totalInvestment: Option[Double] = None
  private var attemptsLeft = 3
  private var isVisible = true

  ///////////////////////////////////////////////////////////////////////////
  //          Public Functions
  ///////////////////////////////////////////////////////////////////////////

  $scope.initPlayerBar = () => initPlayerBar()

  $scope.getMyRanking = () => getMyRanking getOrElse JS()

  $scope.getTotalCashAvailable = () => getTotalCashAvailable

  $scope.getTotalInvestment = () => getTotalInvestment

  $scope.getTotalInvestmentStatus = totalInvestmentStatus getOrElse LOADING

  $scope.isTotalInvestmentLoaded = () => isTotalInvestmentLoaded

  $scope.reloadTotalInvestment = () => reloadTotalInvestment()

  $scope.isBarVisible = () => isVisible

  $scope.toggleVisibility = () => isVisible = !isVisible

  ///////////////////////////////////////////////////////////////////////////
  //          Private Functions
  ///////////////////////////////////////////////////////////////////////////

  private def initPlayerBar() {
    g.console.log(s"PlayerInfoBarController init is running...")
    mySession.userProfile.OID_? match {
      case Some(userID) =>
        g.console.log(s"Loading player information for user ID $userID")

        // load the player's total investment
        loadTotalInvestment(userID)
        attemptsLeft = 3

      case None =>
        attemptsLeft -= 1
        if (attemptsLeft > 0) {
          g.console.log("No user ID found... awaiting re-try (5 seconds)")
          $timeout(() => initPlayerBar(), 5000)
        }
    }
  }

  private def getMyRanking: Option[js.Dynamic] = {
    for {
      contest <- mySession.contest
      playerID <- mySession.userProfile.OID_?
    } yield {
      contestService.getPlayerRankings_@(contest, playerID).player
    }
  }

  private def getTotalCashAvailable: Double = mySession.userProfile.netWorth.as[Double]

  private def getTotalInvestment = totalInvestment getOrElse 0.00d

  private def isTotalInvestmentLoaded = totalInvestment.isDefined

  private def reloadTotalInvestment() = totalInvestmentStatus = None

  private def loadTotalInvestment(playerId: String) = {
    // set a timeout so that loading doesn't persist
    $timeout({ () =>
      if (totalInvestment.isEmpty) {
        g.console.error("Total investment call timed out")
        totalInvestmentStatus = Option(TIMEOUT)
      }
    }, delay = 20000)

    // retrieve the total investment
    g.console.log("Loading Total investment...")
    contestService.getTotalInvestment_@(playerId) onComplete {
      case Success(response) =>
        totalInvestment = Option(response.netWorth.as[Double])
        totalInvestmentStatus = Option(LOADED)
        g.console.log("Total investment loaded")
      case Failure(e) =>
        toaster.pop("error", "Error loading total investment", null)
        totalInvestmentStatus = Option(FAILED)
        g.console.error("Total investment call failed")
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  //          Event Listeners
  ///////////////////////////////////////////////////////////////////////////

  // when the player ID changes, load the total investment
  $scope.$watch(mySession.userProfile, (newProfile: js.Dynamic, oldProfile: js.Dynamic) => initPlayerBar())

}

/**
 * Player Information Bar Controller Singleton
 * @author lawrence.daniels@gmail.com
 */
object PlayerInfoBarController {

  val LOADING = "LOADING"
  val LOADED = "LOADED"
  val FAILED = "FAILED"
  val TIMEOUT = "TIMEOUT"

}