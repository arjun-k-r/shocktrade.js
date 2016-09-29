package com.shocktrade.services

import com.shocktrade.services.NASDAQCompanyListService._
import org.scalajs.nodejs.NodeRequire
import org.scalajs.nodejs.csvparse._
import org.scalajs.nodejs.request.Request
import org.scalajs.nodejs.util.ScalaJsHelper._

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.ScalaJSDefined

/**
  * NASDAQ Company List Service
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
class NASDAQCompanyListService()(implicit require: NodeRequire) {
  // load the modules
  private val request = Request()
  private val csvParse = CsvParse()

  def amex()(implicit ec: ExecutionContext) = {
    download(exchange = "AMEX", url = "http://www.nasdaq.com/screening/companies-by-industry.aspx?exchange=AMEX&render=download")
  }

  def nasdaq()(implicit ec: ExecutionContext) = {
    download(exchange = "NASDAQ", url = "http://www.nasdaq.com/screening/companies-by-industry.aspx?exchange=NASDAQ&render=download")
  }

  def nyse()(implicit ec: ExecutionContext) = {
    download(exchange = "NYSE", url = "http://www.nasdaq.com/screening/companies-by-industry.aspx?exchange=NYSE&render=download")
  }

  private def download(exchange: String, url: String)(implicit ec: ExecutionContext) = {
    request.getFuture(url) flatMap { case (response, data) =>
      val lines = data.split("[\n]").tail.toSeq
      Future.sequence(lines map(toCompanyInfo(exchange, _))) map (_.flatten)
    }
  }

  private def toCompanyInfo(exchange: String, line: String)(implicit ec: ExecutionContext) = {
    csvParse.parseFuture[js.Array[js.Array[String]]](line, new CsvParseOptions()) map { rows =>
      rows.map(values => js.Dictionary(headers zip values.toSeq: _*)).toSeq map { mapping =>
        new NASDAQCompanyInfo(
          symbol = mapping.get("Symbol").flatMap(nullify).orUndefined,
          exchange = exchange,
          name = mapping.get("Name").flatMap(nullify).orUndefined,
          lastSale = mapping.get("LastSale").flatMap(nullify).map(_.toDouble).orUndefined,
          marketCap = mapping.get("MarketCap").flatMap(nullify).map(_.toDouble).orUndefined,
          ADRTSO = mapping.get("ADRTSO").flatMap(nullify).orUndefined,
          IPOyear = mapping.get("IPOyear").flatMap(nullify).map(_.toInt).orUndefined,
          sector = mapping.get("Sector").flatMap(nullify).orUndefined,
          industry = mapping.get("Industry").flatMap(nullify).orUndefined,
          summary = mapping.get("Summary").flatMap(nullify).orUndefined,
          quote = mapping.get("Quote").flatMap(nullify).orUndefined)
      }
    }
  }

  @inline
  private def nullify(s: String) = {
    if (s.isEmpty || s == "n/a") None else Option(s)
  }

}

/**
  * NASDAQ Company List Service Companion
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
object NASDAQCompanyListService {
  private val headers = Seq("Symbol", "Name", "LastSale", "MarketCap", "ADRTSO", "IPOyear", "Sector", "Industry", "Summary", "Quote")

  @ScalaJSDefined
  class NASDAQCompanyInfo(val symbol: js.UndefOr[String],
                          val exchange: js.UndefOr[String],
                          val name: js.UndefOr[String],
                          val lastSale: js.UndefOr[Double],
                          val marketCap: js.UndefOr[Double],
                          val ADRTSO: js.UndefOr[String],
                          val IPOyear: js.UndefOr[Int],
                          val sector: js.UndefOr[String],
                          val industry: js.UndefOr[String],
                          val summary: js.UndefOr[String],
                          val quote: js.UndefOr[String]) extends js.Object

}