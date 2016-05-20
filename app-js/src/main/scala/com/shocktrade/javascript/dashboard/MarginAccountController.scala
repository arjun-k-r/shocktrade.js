package com.shocktrade.javascript.dashboard

import com.github.ldaniels528.meansjs.angularjs.{Controller, Scope, Timeout, injected}
import com.github.ldaniels528.meansjs.angularjs.toaster.Toaster
import com.github.ldaniels528.meansjs.util.ScalaJsHelper._
import com.shocktrade.javascript.MySessionService

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success}

/**
  * Margin Account Controller
  * @author lawrence.daniels@gmail.com
  */
class MarginAccountController($scope: MarginAccountScope, $timeout: Timeout, toaster: Toaster,
                              @injected("ContestService") contestService: ContestService,
                              @injected("MySessionService") mySession: MySessionService) extends Controller {

  private val interestRate = 0.15
  private val initialMargin = 0.50
  private val maintenanceMargin = 0.25
  private var investmentMarketValue = 0.0d

  /////////////////////////////////////////////////////////////////////
  //          Initialization Function
  /////////////////////////////////////////////////////////////////////

  private var attemptsLeft = 3

  $scope.initMarginAccount = () => {
    investmentMarketValue = investmentCost

    for {
      contestID <- mySession.contest.flatMap(_._id.toOption)
      userID <- mySession.userProfile._id.toOption
    } {
      // load the margin accounts market value
      contestService.getMarginMarketValue(contestID, userID) onComplete {
        case Success(contest) =>
          investmentMarketValue = contest.marginMarketValue
        case Failure(e) =>
          toaster.error("Failed to retrieve the Margin Account's market value")
          attemptsLeft -= 1
          if (attemptsLeft > 0) $timeout(() => $scope.initMarginAccount(), 5000)
      }
    }
  }

  /////////////////////////////////////////////////////////////////////
  //          Public Functions
  /////////////////////////////////////////////////////////////////////

  $scope.getAsOfDate = () => mySession.marginAccount_?.flatMap(a => Option(a.asOfDate)) getOrElse new js.Date()

  $scope.getBuyingPower = () => cashFunds / initialMargin

  $scope.getCashFunds = () => cashFunds

  $scope.getInterestPaid = () => mySession.marginAccount_?.flatMap(a => Option(a.interestPaid)) getOrElse 0.0d

  $scope.getInterestRate = () => interestRate

  $scope.getInitialMargin = () => initialMargin

  $scope.getMaintenanceMargin = () => maintenanceMargin

  $scope.getInvestmentCost = () => investmentCost

  $scope.getInvestmentMarketValue = () => investmentMarketValue

  $scope.isAccountInGoodStanding = () => cashFunds >= maintenanceMarginAmount

  $scope.getMarginAccountEquity = () => marginAccountEquity

  $scope.getMaintenanceMarginAmount = () => maintenanceMarginAmount

  // TODO round to nearest penny
  $scope.getMarginCallAmount = () => maintenanceMarginAmount - cashFunds

  /////////////////////////////////////////////////////////////////////
  //          Private Functions
  /////////////////////////////////////////////////////////////////////

  private def cashFunds = mySession.marginAccount_?.map(_.cashFunds) getOrElse 0.0d

  private def investmentCost = {
    var total = 0d
    mySession.participant foreach (_.positions filter (_.accountType == "MARGIN") foreach { pos =>
      total += pos.pricePaid.toDouble * pos.quantity.toDouble
    })
    total
  }

  private def marginAccountEquity = {
    val myInvestmentCost = investmentCost
    cashFunds + (Math.max(investmentMarketValue, myInvestmentCost) - myInvestmentCost)
  }

  private def maintenanceMarginAmount = {
    val maintenanceAmount = (investmentCost - marginAccountEquity) * maintenanceMargin
    if (maintenanceAmount > 0) maintenanceAmount else 0.0d
  }

}

/**
  * Margin Account Scope
  * @author lawrence.daniels@gmail.com
  */
@js.native
trait MarginAccountScope extends Scope {
  // functions
  var initMarginAccount: js.Function0[Unit]
  var getAsOfDate: js.Function0[js.Date]
  var getBuyingPower: js.Function0[Double]
  var getCashFunds: js.Function0[Double]
  var getInterestPaid: js.Function0[Double]
  var getInterestRate: js.Function0[Double]
  var getInitialMargin: js.Function0[Double]
  var getMaintenanceMargin: js.Function0[Double]
  var getInvestmentCost: js.Function0[Double]
  var getInvestmentMarketValue: js.Function0[Double]
  var isAccountInGoodStanding: js.Function0[Boolean]
  var getMarginAccountEquity: js.Function0[Double]
  var getMaintenanceMarginAmount: js.Function0[Double]
  var getMarginCallAmount: js.Function0[Double]

}
