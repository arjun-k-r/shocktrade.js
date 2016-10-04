package com.shocktrade.client.dialogs

import org.scalajs.angularjs.http.Http
import org.scalajs.angularjs.toaster.Toaster
import org.scalajs.angularjs.uibootstrap.{Modal, ModalInstance, ModalOptions}
import org.scalajs.angularjs.{Controller, Scope, Service, injected}
import org.scalajs.nodejs.util.ScalaJsHelper._
import com.shocktrade.client.MySessionService
import com.shocktrade.client.dialogs.TransferFundsDialogController.{TransferFundsResult, _}
import com.shocktrade.client.models.contest.Contest

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.util.{Failure, Success}

/**
  * Transfer Funds Dialog Service
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
class TransferFundsDialog($http: Http, $modal: Modal) extends Service {

  /**
    * Transfer Funds pop-up dialog
    */
  def popup(): Future[TransferFundsResult] = {
    val modalInstance = $modal.open[TransferFundsResult](new ModalOptions(
      templateUrl = "transfer_funds_dialog.html",
      controller = classOf[TransferFundsDialogController].getSimpleName
    ))
    modalInstance.result
  }

  def transferFunds(contestId: String, playerId: String, form: TransferFundsForm): Future[Contest] = {
    $http.post[Contest](s"/api/contest/$contestId/margin/$playerId", form)
  }

}

/**
  * Transfer Funds Dialog Controller
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
class TransferFundsDialogController($scope: TransferFundsScope, $modalInstance: ModalInstance[TransferFundsResult], toaster: Toaster,
                                    @injected("MySessionService") mySession: MySessionService,
                                    @injected("TransferFundsDialog") dialog: TransferFundsDialog)
  extends Controller {

  private val messages = emptyArray[String]

  $scope.actions = TransferActions
  $scope.form = TransferFundsForm(
    cashFunds = mySession.cashAccount_?.orUndefined.flatMap(_.cashFunds),
    marginFunds = mySession.marginAccount_?.orUndefined.flatMap(_.cashFunds)
  )

  /////////////////////////////////////////////////////////////////////
  //          Public Functions
  /////////////////////////////////////////////////////////////////////

  $scope.init = () => {
    // TODO compute the net value of the stock in the margin account
  }

  $scope.getMessages = () => messages

  $scope.hasMessages = () => messages.nonEmpty

  $scope.cancel = () => $modalInstance.dismiss("cancel")

  $scope.accept = (form: TransferFundsForm) => {
    if (isValidated(form)) {
      (for {
        contestId <- mySession.contest_?.flatMap(_._id.toOption)
        userId <- mySession.userProfile._id.toOption
      } yield {
        dialog.transferFunds(contestId, userId, form) onComplete {
          case Success(response) => $modalInstance.close(response)
          case Failure(e) => messages.push("Failed to deposit funds")
        }
      }) getOrElse toaster.error("No game selected")
    }
  }

  /////////////////////////////////////////////////////////////////////
  //          Private Functions
  /////////////////////////////////////////////////////////////////////

  /**
    * Validates the given transfer funds form
    * @param form the given [[TransferFundsForm transfer funds form]]
    * @return true, if the form does not contain errors
    */
  private def isValidated(form: TransferFundsForm) = {
    // clear the messages
    messages.removeAll()

    // first, perform coarse validation
    if (form.action.isEmpty) messages.push("Please select an Action")
    else if (form.amount.isEmpty) messages.push("Please enter the desired amount")
    else {
      // next, perform fine-grained validation
      if (form.amount.exists(_ <= 0)) messages.push("Please enter an amount greater than zero")
      if (isInsufficientCashFunds(form)) messages.push("Insufficient funds in your cash account to complete the request")
      if (isInsufficientMarginFunds(form)) messages.push("Insufficient funds in your margin account to complete the request")
    }

    messages.isEmpty
  }

  private def isInsufficientCashFunds(form: TransferFundsForm) = {
    (for {
      action <- form.action.toOption if action.source == CASH
      amount <- form.amount.toOption
      cashFunds <- form.cashFunds.toOption
    } yield amount > cashFunds).contains(true)
  }

  private def isInsufficientMarginFunds(form: TransferFundsForm) = {
    (for {
      action <- form.action.toOption if action.source == MARGIN
      amount <- form.amount.toOption
      marginFunds <- form.marginFunds.toOption
    } yield amount > marginFunds).contains(true)
  }

}

/**
  * Transfer Funds Dialog Controller Singleton
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
object TransferFundsDialogController {

  type TransferFundsResult = Contest

  private val CASH = "CASH"
  private val MARGIN = "MARGIN"

  private val TransferActions = js.Array(
    TransferFundsAction(label = "Cash to Margin Account", source = CASH),
    TransferFundsAction(label = "Margin Account to Cash", source = MARGIN))

}

/**
  * Transfer Funds Scope
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@js.native
trait TransferFundsScope extends Scope {
  // variables
  var actions: js.Array[TransferFundsAction]
  var form: TransferFundsForm

  // functions
  var init: js.Function0[Unit]
  var getMessages: js.Function0[js.Array[String]]
  var hasMessages: js.Function0[Boolean]
  var cancel: js.Function0[Unit]
  var accept: js.Function1[TransferFundsForm, Unit]

}

/**
  * Transfer Funds Form
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@js.native
trait TransferFundsForm extends js.Object {
  var action: js.UndefOr[TransferFundsAction]
  var amount: js.UndefOr[Double]
  var cashFunds: js.UndefOr[Double]
  var marginFunds: js.UndefOr[Double]
}

/**
  * Transfer Funds Form Singleton
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
object TransferFundsForm {

  def apply(action: js.UndefOr[TransferFundsAction] = js.undefined,
            amount: js.UndefOr[Double] = js.undefined,
            cashFunds: js.UndefOr[Double] = js.undefined,
            marginFunds: js.UndefOr[Double] = js.undefined) = {
    val form = New[TransferFundsForm]
    form.action = action
    form.amount = amount
    form.cashFunds = cashFunds
    form.marginFunds = marginFunds
    form
  }
}

/**
  * Transfer Funds Action
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@js.native
trait TransferFundsAction extends js.Object {
  var label: String
  var source: String
}

/**
  * Transfer Funds Action Singleton
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
object TransferFundsAction {

  def apply(label: String, source: String) = {
    val action = New[TransferFundsAction]
    action.label = label
    action.source = source
    action
  }
}


