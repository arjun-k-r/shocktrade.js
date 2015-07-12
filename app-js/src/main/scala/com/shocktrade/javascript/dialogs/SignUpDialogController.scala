package com.shocktrade.javascript.dialogs

import com.github.ldaniels528.scalascript.core.Timeout
import com.github.ldaniels528.scalascript.extensions.{ModalInstance, Toaster}
import com.github.ldaniels528.scalascript.{Controller, angular, injected, scoped}
import com.shocktrade.javascript.ScalaJsHelper._
import com.shocktrade.javascript.social.Facebook
import org.scalajs.dom.console

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => JS}
import scala.util.{Failure, Success}

/**
 * Sign-Up Dialog Controller
 * @author lawrence.daniels@gmail.com
 */
class SignUpDialogController($scope: SignUpDialogScope, $modalInstance: ModalInstance[js.Dynamic], $timeout: Timeout, toaster: Toaster,
                             @injected("Facebook") facebook: Facebook,
                             @injected("SignUpDialog") dialog: SignUpDialogService)
  extends Controller {

  private val messages = emptyArray[String]
  private var loading = false

  console.log(s"facebook.profile = ${angular.toJson(facebook.profile)}")

  $scope.form = JS(
    name = facebook.profile.name,
    facebookID = facebook.facebookID
  )

  @scoped def cancel() = $modalInstance.dismiss("cancel")

  @scoped def createAccount(form: js.Dynamic) = registerUser(form)

  @scoped def getMessages = messages

  @scoped def isLoading = loading

  /**
   * Validates the form
   * @param form the given form
   * @return {boolean}
   */
  private def isValid(form: js.Dynamic) = {
    // clear messages
    messages.remove(0, messages.length)

    // validate the user name
    if (!isDefined(form.userName) || form.userName.as[String].isBlank) {
      messages.push("Screen Name is required")
    }

    // validate the email address
    if (!isDefined(form.email) || form.email.as[String].isBlank) {
      messages.push("Email Address is required")
    }

    if (isDefined(form.email) && !form.email.as[String].isValidEmail) {
      messages.push("The Email Address format is invalid")
    }

    // it"s valid is the messages are empty
    messages.isEmpty
  }

  private def registerUser(form: js.Dynamic) {
    if (isValid(form)) {
      startLoading()
      dialog.createAccount(form) onComplete {
        case Success(profile) =>
          stopLoading()
          if (!isDefined(profile.error)) $modalInstance.close(profile)
          else {
            messages.push(profile.error.as[String])
          }

        case Failure(e) =>
          stopLoading()
          toaster.error(e.getMessage)
          console.log(s"Error registering user: ${e.getMessage}")
      }
    }
  }

  private def startLoading() = loading = true

  private def stopLoading() = $timeout(() => loading = false, 1000)

}

/**
 * Sign-Up Dialog Scope
 * @author lawrence.daniels@gmail.com
 */
trait SignUpDialogScope extends js.Object {
  var form: js.Dynamic = js.native

}