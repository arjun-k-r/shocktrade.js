package com.shocktrade.javascript.dashboard

import com.ldaniels528.scalascript.ScalaJsHelper._
import com.ldaniels528.scalascript.core.Timeout
import com.ldaniels528.scalascript.extensions.Toaster
import com.ldaniels528.scalascript.{Controller, injected}
import com.shocktrade.javascript.MySession
import com.shocktrade.javascript.dialogs.{PerksDialogService, TransferFundsDialogService}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.util.{Failure, Success}

/**
 * Dashboard Controller
 * @author lawrence.daniels@gmail.com
 */
class DashboardController($scope: js.Dynamic, $routeParams: js.Dynamic, $timeout: Timeout, toaster: Toaster,
                          @injected("ContestService") contestService: ContestService,
                          @injected("MySession") mySession: MySession,
                          @injected("PerksDialog") perksDialog: PerksDialogService,
                          @injected("TransferFundsDialog") transferFundsDialog: TransferFundsDialogService)
  extends Controller {

  private var accountMode = false

  /////////////////////////////////////////////////////////////////////
  //          Account Functions
  /////////////////////////////////////////////////////////////////////

  $scope.isCashAccount = () => !accountMode

  $scope.isMarginAccount = () => accountMode

  $scope.toggleAccountMode = () => accountMode = !accountMode

  $scope.getAccountMode = () => accountMode

  $scope.getAccountType = () => if (accountMode) "MARGIN" else "CASH"

  /////////////////////////////////////////////////////////////////////
  //          Pop-up Dialog Functions
  /////////////////////////////////////////////////////////////////////

  $scope.marginAccountDialog = () => {
    transferFundsDialog.popup() onComplete {
      case Success(contest) => mySession.setContest(contest)
      case Failure(e) =>
        if (e.getMessage != "cancel") {
          e.printStackTrace()
        }
    }
  }

  $scope.perksDialog = () => {
    perksDialog.popup() onComplete {
      case Success(contest) =>
        g.console.log(s"Settings contest")
        mySession.setContest(contest)
      case Failure(e) =>
        if (e.getMessage != "cancel") {
          e.printStackTrace()
        }
    }
  }

  /////////////////////////////////////////////////////////////////////
  //          Participant Functions
  /////////////////////////////////////////////////////////////////////

  $scope.isRankingsShown = () => !mySession.contest.exists(_.rankingsHidden.isTrue)

  $scope.toggleRankingsShown = () => mySession.contest.foreach(c => c.rankingsHidden = !c.rankingsHidden)

  $scope.getRankings = () => mySession.contest match {
    case Some(c) =>
      val rankings = contestService.getPlayerRankings(c, mySession.getUserID())
      rankings.participants
    case None => emptyArray[js.Dynamic]
  }

  ///////////////////////////////////////////////////////////////////////////
  //          Initialization
  ///////////////////////////////////////////////////////////////////////////

  // if a contest ID was passed ...
  if (isDefined($routeParams.contestId)) {
    val contestId = $routeParams.contestId.as[String]

    // if the current contest is not the chosen contest ...
    if (!mySession.contest.exists(_.OID == contestId)) {
      g.console.log(s"Loading contest $contestId...")
      contestService.getContestByID(contestId) onComplete {
        case Success(loadedContest) => mySession.setContest(loadedContest)
        case Failure(e) =>
          g.console.error(s"Error loading contest $contestId")
          toaster.error("Error loading game")
          e.printStackTrace()
      }
    }
  }

}