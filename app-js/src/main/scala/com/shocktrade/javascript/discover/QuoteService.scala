package com.shocktrade.javascript.discover

import biz.enef.angulate.core.{HttpPromise, HttpService}
import biz.enef.angulate.{Service, named}
import com.shocktrade.javascript.MySession
import com.shocktrade.javascript.ScalaJsHelper._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportAll

/**
 * Quote Services
 * @author lawrence.daniels@gmail.com
 */
@JSExportAll
class QuoteService($http: HttpService, @named("MySession") mySession: MySession)
  extends Service {

  def autoCompleteSymbols: js.Function2[String, Int, HttpPromise[js.Array[js.Dynamic]]] = (searchTerm: String, maxResults: Int) => {
    val queryString = params("searchTerm" -> searchTerm, "maxResults" -> maxResults)
    $http.get[js.Array[js.Dynamic]](s"/api/quotes/autocomplete$queryString")
  }

  def getExchangeCounts: js.Function0[HttpPromise[js.Array[js.Dynamic]]] = () => {
    $http.get[js.Array[js.Dynamic]]("/api/exchanges")
  }

  def getFilterQuotes: js.Function1[js.Dynamic, HttpPromise[js.Dynamic]] = (filter: js.Dynamic) => {
    $http.post[js.Dynamic]("/api/quotes/filter/mini", filter)
  }

  def getPricing: js.Function1[js.Array[String], HttpPromise[js.Dynamic]] = (symbols: js.Array[String]) => {
    $http.post[js.Dynamic]("/api/quotes/pricing", symbols)
  }

  def getRiskLevel: js.Function1[String, HttpPromise[js.Dynamic]] = (symbol: String) => {
    $http.get[js.Dynamic](s"/api/quotes/riskLevel/$symbol")
  }

  def getStockQuoteList: js.Function1[js.Array[String], HttpPromise[js.Array[js.Dynamic]]] = (symbols: js.Array[String]) => {
    $http.post[js.Array[js.Dynamic]]("/api/quotes/list", symbols)
  }

  def getStockQuote: js.Function1[String, HttpPromise[js.Dynamic]] = (symbol: String) => {
    $http.get[js.Dynamic](s"/api/quotes/symbol/$symbol")
  }

  def getTradingHistory: js.Function1[String, HttpPromise[js.Array[js.Dynamic]]] = (symbol: String) => {
    $http.get[js.Array[js.Dynamic]](s"/api/quotes/tradingHistory/$symbol")
  }

  ////////////////////////////////////////////////////////////////////
  //			Sector Exploration Functions
  ///////////////////////////////////////////////////////////////////

  def loadSectorInfo: js.Function1[String, HttpPromise[js.Array[js.Dynamic]]] = (symbol: String) => {
    $http.get[js.Array[js.Dynamic]](s"/api/explore/symbol/$symbol")
  }

  def loadSectors: js.Function0[HttpPromise[js.Array[js.Dynamic]]] = () => {
    $http.get[js.Array[js.Dynamic]]("/api/explore/sectors")
  }

  def loadNAICSSectors: js.Function0[HttpPromise[js.Array[js.Dynamic]]] = () => {
    $http.get[js.Array[js.Dynamic]]("/api/explore/naics/sectors")
  }

  def loadIndustries: js.Function1[String, HttpPromise[js.Array[js.Dynamic]]] = (sector: String) => {
    val queryString = params("sector" -> sector)
    $http.get[js.Array[js.Dynamic]](s"/api/explore/industries$queryString")
  }

  def loadSubIndustries: js.Function2[String, String, HttpPromise[js.Array[js.Dynamic]]] = (sector: String, industry: String) => {
    val queryString = params("sector" -> sector, "industry" -> industry)
    $http.get[js.Array[js.Dynamic]](s"/api/explore/subIndustries$queryString")
  }

  def loadIndustryQuotes: js.Function3[String, String, String, HttpPromise[js.Array[js.Dynamic]]] = (sector: String, industry: String, subIndustry: String) => {
    val queryString = params("sector" -> sector, "industry" -> industry, "subIndustry" -> subIndustry)
    $http.get[js.Array[js.Dynamic]](s"/api/explore/quotes$queryString")
  }

  private def setFavorites(updatedQuotes: js.Array[js.Dynamic]) = {
    updatedQuotes.foreach { quote =>
      quote.favorite = mySession.isFavoriteSymbol(quote.symbol.as[String])
    }
    updatedQuotes
  }

}
