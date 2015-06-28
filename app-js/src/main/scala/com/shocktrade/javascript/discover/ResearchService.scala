package com.shocktrade.javascript.discover

import com.ldaniels528.javascript.angularjs.core.{Service, Http}
import com.shocktrade.javascript.ScalaJsHelper._

import scala.concurrent.ExecutionContext
import scala.scalajs.js

/**
 * Research Service
 * @author lawrence.daniels@gmail.com
 */
class ResearchService($http: Http) extends Service {

  def search(searchOptions: js.Dynamic)(implicit ec: ExecutionContext) = {
    required("searchOptions", searchOptions)
    $http.post[js.Array[js.Dynamic]]("/api/research/search", searchOptions)
  }

}
