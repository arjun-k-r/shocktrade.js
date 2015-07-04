package com.shocktrade.javascript.dialogs

import com.ldaniels528.scalascript.ScalaJsHelper._
import com.ldaniels528.scalascript.core.Q
import com.ldaniels528.scalascript.extensions.{ModalInstance, Toaster}
import com.ldaniels528.scalascript.{angular, injected}
import com.shocktrade.javascript.dashboard.ContestService
import com.shocktrade.javascript.discover.QuoteService
import com.shocktrade.javascript.{AutoCompletionController, MySession}
import org.scalajs.dom.console

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g, literal => JS}
import scala.util.{Failure, Success}

/**
 * New Order Dialog Controller
 * @author lawrence.daniels@gmail.com
 */
class NewOrderDialogController($scope: js.Dynamic, $modalInstance: ModalInstance[js.Dynamic], $q: Q, toaster: Toaster,
                               @injected("ContestService") contestService: ContestService,
                               @injected("MySession") mySession: MySession,
                               @injected("NewOrderDialog") newOrderDialog: NewOrderDialogService,
                               @injected("PerksDialog") perksDialog: PerksDialogService,
                               @injected("QuoteService") quoteService: QuoteService,
                               @injected("params") params: js.Dynamic)
  extends AutoCompletionController($q, quoteService) {

  private val messages = emptyArray[String]
  private var processing = false

  $scope.form = JS(
    emailNotify = true,
    accountType = params.accountType,
    symbol = params.symbol,
    quantity = params.quantity
  )
  $scope.quote = JS(symbol = $scope.form.symbol)

  ///////////////////////////////////////////////////////////////////////////
  //          Public Functions
  ///////////////////////////////////////////////////////////////////////////

  $scope.init = () => $scope.orderQuote($scope.form.symbol)

  $scope.autoCompleteSymbols = (searchTerm: String) => autoCompleteSymbols(searchTerm)

  $scope.cancel = () => $modalInstance.dismiss("cancel")

  $scope.getMessages = () => messages

  $scope.isProcessing = () => processing

  $scope.ok = (form: js.Dynamic) => accept(form)

  $scope.orderQuote = (ticker: js.Dynamic) => orderQuote(ticker)

  $scope.getTotal = (form: js.Dynamic) => getTotal(form)

  ///////////////////////////////////////////////////////////////////////////
  //          Private Functions
  ///////////////////////////////////////////////////////////////////////////

  private def orderQuote(ticker: js.Dynamic) = {
    // determine the symbol
    var symbol: String = if (isDefined(ticker.symbol)) ticker.symbol.as[String]
    else {
      val _ticker = ticker.as[String]
      val index = _ticker.indexOf(" ")
      if (index == -1) _ticker else _ticker.substring(0, index)
    }

    if (symbol.nonBlank) {
      symbol = symbol.trim.toUpperCase
      newOrderDialog.lookupQuote(symbol) onComplete {
        case Success(quote) =>
          $scope.quote = quote
          $scope.form.symbol = quote.symbol
          $scope.form.limitPrice = quote.lastTrade
          $scope.form.exchange = quote.exchange
        case Failure(e) =>
          messages.push(s"The order could not be processed (error code ${e.getMessage})")
      }
    }
  }

  private def accept(form: js.Dynamic) = {
    if (isValid(form)) {
      processing = true

      val contestId = mySession.getContestID()
      val playerId = mySession.getUserID()
      console.log(s"contestId = $contestId, playerId = $playerId, form = ${angular.toJson(form)}")

      contestService.createOrder(contestId, playerId, $scope.form) onComplete {
        case Success(contest) =>
          processing = false
          $modalInstance.close(contest)
        case Failure(e) =>
          processing = false
          messages.push(s"The order could not be processed (error code ${e.getMessage})")
      }
    }
  }

  private def getTotal(form: js.Dynamic) = {
    val price = if (isDefined(form.limitPrice)) form.limitPrice.as[Double] else 0.00
    val quantity = if (isDefined(form.quantity)) form.quantity.as[Double] else 0.00
    price * quantity
  }

  private def isValid(form: js.Dynamic) = {
    messages.remove(0, messages.length)

    // perform the validations
    if (!isDefined(form.accountType)) messages.push("Please selected the account to use (Cash or Margin)")
    if (isDefined(form.accountType) && form.accountType === "MARGIN" && !mySession.hasMarginAccount()) messages.push("You do not have a Margin Account (must buy the Perk)")
    if (!isDefined(form.orderType)) messages.push("No Order Type (BUY or SELL) specified")
    if (!isDefined(form.priceType)) messages.push("No Pricing Method specified")
    if (!isDefined(form.orderTerm)) messages.push("No Order Term specified")
    if (!isDefined(form.quantity) || form.quantity === 0d) messages.push("No quantity specified")
    messages.isEmpty
  }

  ///////////////////////////////////////////////////////////////////////////
  //          Initialization
  ///////////////////////////////////////////////////////////////////////////

  for {
    contestId <- Option(mySession.getContestID())
    playerId <- Option(mySession.getUserID())
  } {
    // load the player"s perks
    perksDialog.getMyPerks(contestId, playerId) onComplete {
      case Success(contest) => $scope.form.perks = contest.perkCodes
      case Failure(e) =>
        toaster.error("Error retrieving perks")
    }
  }

}
