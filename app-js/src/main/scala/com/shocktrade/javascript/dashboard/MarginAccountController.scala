package com.shocktrade.javascript.dashboard

import biz.enef.angulate.core.Timeout
import biz.enef.angulate.{ScopeController, named}
import com.ldaniels528.angularjs.Toaster
import com.shocktrade.javascript.MySession
import com.shocktrade.javascript.ScalaJsHelper._

import scala.language.postfixOps
import scala.scalajs.js
import scala.scalajs.js.Date
import scala.scalajs.js.Dynamic.{global => g}
import scala.util.{Failure, Success}

/**
 * Margin Account Controller
 * @author lawrence.daniels@gmail.com
 */
class MarginAccountController($scope: js.Dynamic, $timeout: Timeout, toaster: Toaster,
                              @named("ContestService") contestService: ContestService,
                              @named("MySession") mySession: MySession) extends ScopeController {

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
      contestID <- mySession.contest.flatMap(_.OID_?)
      userID <- mySession.userProfile.OID_?
    } {
      // load the margin accounts market value
      contestService.getMarginMarketValue(contestID, userID) onComplete {
        case Success(contest) =>
          investmentMarketValue = contest.marginMarketValue.as[Double]
        case Failure(e) =>
          toaster.pop("error", "Failed to retrieve the Margin Account's market value", null)
          attemptsLeft -= 1
          if (attemptsLeft > 0) $timeout(() => $scope.initMarginAccount(), 5000)
      }
    }
  }

  /////////////////////////////////////////////////////////////////////
  //          Public Functions
  /////////////////////////////////////////////////////////////////////

  $scope.getAsOfDate = () => mySession.marginAccount_?.flatMap(a => Option(a.asOfDate)) getOrElse new Date()

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

  private def cashFunds = mySession.marginAccount_?.flatMap(a => Option(a.cashFunds).map(_.as[Double])) getOrElse 0.0d

  private def investmentCost = {
    var total = 0d
    mySession.participant foreach (_.positions.asArray[js.Dynamic] filter (_.accountType === "MARGIN") foreach { pos =>
      total += pos.pricePaid.as[Double] * pos.quantity.as[Double]
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
