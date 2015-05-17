package com.shocktrade.controllers

import akka.util.Timeout
import com.ldaniels528.commons.helpers.OptionHelper._
import com.shocktrade.server.trading.Contests
import com.shocktrade.util.BSONHelper._
import play.api.Play._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json.{obj => JS}
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc._
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Chart Resources
 * @author lawrence.daniels@gmail.com
 */
object ChartResources extends Controller with MongoController with MongoExtras {
  lazy val mcQ: JSONCollection = db.collection[JSONCollection]("Stocks")

  def getAnalystRatings(symbol: String) = Action.async {
    getImageBinary(s"http://www.barchart.com/stocks/ratingsimg.php?sym=$symbol")
  }

  def getStockChart(symbol: String, size: String, range: String) = Action.async { request =>
    // construct the chart image URL
    val chartURL = size match {
      case "medium" =>
        s"http://chart.finance.yahoo.com/z?s=$symbol&t=$range&q=&l=&z=l&a=v&p=s&lang=en-US&region=US"
      case "small" =>
        s"http://chart.finance.yahoo.com/c/$range/d/$symbol"
      case _ =>
        throw new IllegalArgumentException(s"Invalid size argument '$size'")
    }

    // return the image to the client
    getImageBinary(chartURL)
  }

  private def getImageBinary(chartURL: String): Future[Result] = {
    WS.url(chartURL).getStream().map { case (response, body) =>
      // check that the response was successful
      if (response.status == 200) {
        // get the content type
        val contentType =
          response.headers.get("Content-Type").flatMap(_.headOption).getOrElse("application/octet-stream")

        // if there's a content length, send that, otherwise return the body chunked
        response.headers.get("Content-Length") match {
          case Some(Seq(length)) =>
            Ok.feed(body).as(contentType).withHeaders("Content-Length" -> length)
          case _ =>
            Ok.chunked(body).as(contentType)
        }
      } else {
        BadGateway
      }
    }
  }

  def getExposureByExchange(contestId: String, userName: String) = getExposureByXXX(contestId.toBSID, userName.toBSID, _.exchange)

  def getExposureByIndustry(contestId: String, userName: String) = getExposureByXXX(contestId.toBSID, userName.toBSID, _.industry)

  def getExposureBySector(contestId: String, userName: String) = getExposureByXXX(contestId.toBSID, userName.toBSID, _.sector)

  def getExposureBySecurities(contestId: String, userName: String) = getExposureByXXX(contestId.toBSID, userName.toBSID, _.symbol)

  private def getExposureByXXX(contestId: BSONObjectID, userId: BSONObjectID, fx: Position => String) = Action.async {
    implicit val timeout: Timeout = 10.seconds
    for {
    // lookup the contest by ID
      contest <- Contests.findContestByID(contestId)() map (_ orDie "Game not found")

      // lookup the participant
      participant = contest.participants.find (_.id == userId) orDie s"Player '$userId' not found"

      // get the symbol & quantities for each position
      quantities = participant.positions map (pos => (pos.symbol, pos.quantity))

      // query the symbols for the current market price
      quotes <- QuoteResources.findQuotesBySymbols(quantities map (_._1))

      // create the mapping of symbols to quotes
      mappingQ = Map(quotes map (q => (q.symbol.getOrElse(""), q)): _*)

      // generate the value of each position
      posData = quantities flatMap {
        case (symbol, qty) =>
          for {
            q <- mappingQ.get(symbol)
            exchange <- q.exchange
            lastTrade <- q.lastTrade
            sector <- q.sector
            industry <- q.industry
          } yield Position(symbol, exchange, sector, industry, lastTrade * qty)
      }

      // group the data
      groupedData = posData.groupBy(fx).foldLeft[List[(String, Double)]](List("Cash" -> participant.fundsAvailable.toDouble)) {
        case (list, (label, somePositions)) => (label, somePositions.map(_.value).sum) :: list
      }

      total = groupedData.map(_._2).sum
      percentages = groupedData map { case (label, value) => (label, 100 * (value / total))}

      // produce the chart data
      values = percentages map { case (k, v) => JS("label" -> k, "value" -> v) }

    } yield Ok(JsArray(values))
  }

  /**
   * Truncates the given double to the given precision
   */
  private def trunc(value: Double, precision: Int): Double = {
    val s = math pow(10, precision)
    (math floor value * s) / s
  }

  case class Position(symbol: String, exchange: String, sector: String, industry: String, value: Double)

}