package com.shocktrade.javascript.discover

import biz.enef.angulate.Service
import com.ldaniels528.javascript.angularjs.core.Http

import scala.concurrent.ExecutionContext
import scala.scalajs.js

/**
 * Research Service
 * @author lawrence.daniels@gmail.com
 */
class ResearchService($http: Http) extends Service {

  def search(searchOptions: js.Dynamic)(implicit ec: ExecutionContext) = {
    $http.post[js.Array[js.Dynamic]]("/api/research/search", searchOptions)
  }

}
