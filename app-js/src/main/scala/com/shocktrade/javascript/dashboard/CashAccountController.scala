package com.shocktrade.javascript.dashboard

import com.github.ldaniels528.scalascript.extensions.Toaster
import com.github.ldaniels528.scalascript.{Controller, injected, scoped}
import com.shocktrade.javascript.MySessionService
import com.github.ldaniels528.scalascript.util.ScalaJsHelper._

import scala.language.postfixOps
import scala.scalajs.js
import scala.scalajs.js.Date

/**
 * Cash Account Controller
 * @author lawrence.daniels@gmail.com
 */
class CashAccountController($scope: CashAccountScope, toaster: Toaster, @injected("MySessionService") mySession: MySessionService)
  extends Controller {

  /////////////////////////////////////////////////////////////////////
  //          Public Functions
  /////////////////////////////////////////////////////////////////////

  @scoped def asOfDate = mySession.cashAccount_?.flatMap(a => Option(a.asOfDate)) getOrElse new Date()

  @scoped def getTotalOrders = Seq("BUY", "SELL") map computeTotalOrdersByType sum

  @scoped def getTotalEquity = computeTotalInvestment + computeFundsAvailable

  @scoped def getTotalBuyOrders = computeTotalOrdersByType(orderType = "BUY")

  @scoped def getTotalSellOrders = computeTotalOrdersByType(orderType = "SELL")

  @scoped def getFundsAvailable = computeFundsAvailable

  @scoped def getTotalInvestment = computeTotalInvestment

  /////////////////////////////////////////////////////////////////////
  //          Private Functions
  /////////////////////////////////////////////////////////////////////

  private def computeFundsAvailable = mySession.cashAccount_?.map(_.cashFunds).getOrElse(0d)

  private def computeTotalInvestment = {
    var total = 0d
    mySession.participant foreach (_.positions.asArray[js.Dynamic] filter (_.accountType === "CASH") foreach (total += _.netValue.as[Double]))
    total
  }

  private def computeTotalOrdersByType(orderType: String) = {
    var total = 0d
    mySession.participant foreach (_.orders.asArray[js.Dynamic] filter (o => o.orderType === orderType && o.accountType === "CASH") foreach { o =>
      total += o.price.as[Double] * o.quantity.as[Double] + o.commission.as[Double]
    })
    total
  }

}

/**
 * Cash Account Controller Scope
 * @author lawrence.daniels@gmail.com
 */
trait CashAccountScope extends js.Object