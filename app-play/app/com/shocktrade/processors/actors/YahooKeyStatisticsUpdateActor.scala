package com.shocktrade.processors.actors

import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging}
import com.shocktrade.dao.SecuritiesUpdateDAO
import com.shocktrade.processors.actors.YahooKeyStatisticsUpdateActor._
import com.shocktrade.services.yahoofinance.YFKeyStatisticsService
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.{BSONDocument => BS}

/**
  * Yahoo! Key Statistics Update Actor
  * @author lawrence.daniels@gmail.com
  */
class YahooKeyStatisticsUpdateActor(reactiveMongoApi: ReactiveMongoApi) extends Actor with ActorLogging {
  private implicit val ec = context.dispatcher
  private val updateDAO = SecuritiesUpdateDAO(reactiveMongoApi)
  private val counter = new AtomicInteger()

  override def receive = {
    case RefreshAllKeyStatistics =>
      log.info("Loading symbols for key statistics updates...")
      val mySender = sender()

      counter.set(0)
      var count = 0
      updateDAO.getSymbolsForKeyStatisticsUpdate.collect[Seq]() foreach { docs =>
        docs.flatMap(_.getAs[String]("symbol")) foreach { symbol =>
          count += 1
          self ! RefreshKeyStatics(symbol)
        }
        mySender ! count
      }

    case RefreshKeyStatics(symbol) =>
      val ks = YFKeyStatisticsService.getKeyStatisticsSync(symbol)
      import ks._

      updateDAO.updateQuote(ks.symbol, BS(
        "pctHeldByInsiders" -> pctHeldByInsiders,
        "pctHeldByInstitutions" -> pctHeldByInstitutions,
        "dividendYield5YearAvg" -> dividendYield5YearAvg,
        "change52Week" -> change52Week,
        "high52Week" -> high52Week,
        "low52Week" -> low52Week,
        "movingAverage50Day" -> movingAverage50Day,
        "movingAverage200Day" -> movingAverage200Day,
        "avgVolume3Month" -> avgVolume3Month,
        "avgVolume10Day" -> avgVolume10Day,
        "beta" -> beta,
        "bookValuePerShare" -> bookValuePerShare,
        "currentRatio" -> currentRatio,
        "dilutedEPS" -> dilutedEPS,
        "dividendDate" -> dividendDate,
        "EBITDA" -> EBITDA,
        "enterpriseValue" -> enterpriseValue,
        "enterpriseValueOverEBITDA" -> enterpriseValueOverEBITDA,
        "enterpriseValueOverRevenue" -> enterpriseValueOverRevenue,
        "exDividendDate" -> exDividendDate,
        "fiscalYearEndDate" -> fiscalYearEndDate,
        "sharesFloat" -> sharesFloat,
        "forwardAnnualDividendRate" -> forwardAnnualDividendRate,
        "forwardAnnualDividendYield" -> forwardAnnualDividendYield,
        "forwardPE" -> forwardPE,
        "grossProfit" -> grossProfit,
        "lastSplitDate" -> lastSplitDate,
        "lastSplitFactor" -> lastSplitFactor,
        "leveredFreeCashFlow" -> leveredFreeCashFlow,
        "marketCapIntraday" -> marketCapIntraday,
        "mostRecentQuarterDate" -> mostRecentQuarterDate,
        "netIncomeAvailToCommon" -> netIncomeAvailToCommon,
        "operatingCashFlow" -> operatingCashFlow,
        "operatingMargin" -> operatingMargin,
        "pegRatio5YearExp" -> pegRatio5YearExp,
        "payoutRatio" -> payoutRatio,
        "priceOverBookValue" -> priceOverBookValue,
        "priceOverSales" -> priceOverSales,
        "profitMargin" -> profitMargin,
        "earningsGrowthQuarterly" -> earningsGrowthQuarterly,
        "revenueGrowthQuarterly" -> revenueGrowthQuarterly,
        "returnOnAssets" -> returnOnAssets,
        "returnOnEquity" -> returnOnEquity,
        "revenue" -> revenue,
        "revenuePerShare" -> revenuePerShare,
        "change52WeekSNP500" -> change52WeekSNP500,
        "sharesOutstanding" -> sharesOutstanding,
        "sharesShort" -> sharesShort,
        "sharesShortPriorMonth" -> sharesShortPriorMonth,
        "shortPctOfFloat" -> shortPctOfFloat,
        "shortRatio" -> shortRatio,
        "totalCash" -> totalCash,
        "totalCashPerShare" -> totalCashPerShare,
        "totalDebt" -> totalDebt,
        "totalDebtOverEquity" -> totalDebtOverEquity,
        "trailingAnnualDividendYield" -> trailingAnnualDividendYield,
        "trailingPE" -> trailingPE,

        // administrative fields
        "yfKeyStatsRespTimeMsec" -> responseTimeMsec,
        "yfKeyStatsLastUpdated" -> new Date()
      ))

      // log the statistics
      if (counter.incrementAndGet() % 1000 == 0) {
        log.info(s"Processed ${counter.get} key statistics")
      }

    case message =>
      log.error(s"Unhandled message: $message (${Option(message).map(_.getClass.getName).orNull}})")
      unhandled(message)

  }
}

/**
  * Yahoo! Key Statistics Update Actor Singleton
  * @author lawrence.daniels@gmail.com
  */
object YahooKeyStatisticsUpdateActor {

  case object RefreshAllKeyStatistics

  case class RefreshKeyStatics(symbol: String)

}
