package com.shocktrade.javascript

import com.github.ldaniels528.scalascript.core.Q
import com.github.ldaniels528.scalascript.{Controller, scoped}
import com.shocktrade.javascript.discover.QuoteService

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.util.{Failure, Success}

/**
 * Represents a symbol auto-completion controller
 * @author lawrence.daniels@gmail.com
 */
abstract class AutoCompletionController($scope: js.Object, $q: Q, quoteService: QuoteService)
  extends Controller {

  @scoped
  def autoCompleteSymbols(searchTerm: String) = {
    val deferred = $q.defer[js.Array[js.Dynamic]]()
    quoteService.autoCompleteSymbols(searchTerm, maxResults = 20) onComplete {
      case Success(response) => deferred.resolve(response)
      case Failure(e) => deferred.reject(e.getMessage)
    }
    deferred.promise
  }

}
