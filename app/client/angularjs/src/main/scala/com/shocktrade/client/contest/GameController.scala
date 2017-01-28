package com.shocktrade.client.contest

import com.shocktrade.client.MySessionService
import com.shocktrade.client.models.contest.Contest
import io.scalajs.npm.angularjs.toaster.Toaster
import io.scalajs.npm.angularjs.{Controller, Location, Scope}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success}

/**
  * Abstract Game Controller
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
abstract class GameController($scope: GameScope,
                              $location: Location,
                              toaster: Toaster,
                              mySession: MySessionService,
                              portfolioService: PortfolioService)
  extends Controller {

  $scope.enterGame = (aContest: js.UndefOr[Contest]) => aContest foreach { contest =>
    if ($scope.isParticipant(contest)) {
      val results = for {
        contestID <- contest._id.toOption
        playerID <- mySession.userProfile._id.toOption
      } yield (contestID, playerID)

      results match {
        case Some((contestID, playerID)) =>
          contest.loading = true
          mySession.loadContestByID(contestID) onComplete {
            case Success(result) =>
              $scope.$apply(() => contest.loading = false)
              $location.path(s"/dashboard/$contestID")
            case Failure(e) =>
              contest.loading = false
          }
        case None =>
          toaster.error("The contest has not been loaded properly")
      }
    }
    else {
      toaster.error("You must join the contest first")
    }
  }

  $scope.isParticipant = (aContest: js.UndefOr[Contest]) => aContest exists { contest =>
    contest.participants.exists(_.exists(_.is(mySession.userProfile._id)))
  }

}

/**
  * Game Scope
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@js.native
trait GameScope extends Scope {
  // functions
  var enterGame: js.Function1[js.UndefOr[Contest], Unit] = js.native
  var isParticipant: js.Function1[js.UndefOr[Contest], Boolean] = js.native
}
