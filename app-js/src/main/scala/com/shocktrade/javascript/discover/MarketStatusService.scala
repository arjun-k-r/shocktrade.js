package com.shocktrade.javascript.discover

import com.ldaniels528.scalascript.Service
import com.ldaniels528.scalascript.core.Http
import com.shocktrade.javascript.discover.MarketStatusService.MarketStatus

import scala.concurrent.ExecutionContext

/**
 * Market Status Service
 * @author lawrence.daniels@gmail.com
 */
class MarketStatusService($http: Http) extends Service {

  /**
   * Retrieves the current stock market status
   * @return the current U.S. Stock [[MarketStatus market status]]
   */
  def getMarketStatus(implicit ec: ExecutionContext) = $http.getObject[MarketStatus]("/api/tradingClock/status/0")

}

/**
 * Market Status Service Singleton
 * @author lawrence.daniels@gmail.com
 */
object MarketStatusService {

  /**
   * Represents the current U.S. Market Status
   * @param stateChanged indicates whether the market status has changed since the given timestamp
   * @param active indicates whether the U.S. Markets are active (open)
   * @param sysTime the given timestamp
   * @param delay the delay until trading starts
   * @param start the trading start time
   * @param end the trading end time
   * @example {"stateChanged":false,"active":false,"sysTime":1392092448795,"delay":-49848795,"start":1392042600000,"end":1392066000000}
   */
  case class MarketStatus(stateChanged: Boolean, active: Boolean, sysTime: Double, delay: Double, start: Double, end: Double)

}