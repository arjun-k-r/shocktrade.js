package com.shocktrade.common.dao
package securities

import org.scalajs.nodejs.mongodb._

import scala.concurrent.ExecutionContext
import scala.scalajs.js

/**
  * Securities Update DAO
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@js.native
trait SecuritiesUpdateDAO extends SecuritiesDAO

/**
  * Stock Update DAO Companion
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
object SecuritiesUpdateDAO {

  /**
    * Stock Update DAO Extensions
    * @param dao the given [[SecuritiesUpdateDAO Stock DAO]]
    */
  implicit class SecuritiesUpdateDAOExtensions(val dao: SecuritiesUpdateDAO) extends AnyVal {

    @inline
    def findSymbolsForUpdate(cutOffTime: js.Date) = {
      dao.find(
        selector = doc("active" $eq true, "symbol" $ne null /*, $or("yfCsvLastUpdated" $exists false, "yfCsvLastUpdated" $lt cutOffTime)*/),
        projection = SecuritiesRef.Fields.toProjection)
        .sort(js.Array("symbol", 1))
        .toArrayFuture[SecuritiesRef]
    }

    @inline
    def findSymbolsForKeyStatisticsUpdate(cutOffTime: js.Date) = {
      dao.find(
        selector = doc("active" $eq true, "symbol" $ne null/*, "exchange" $in (js.Array("NASDAQ", "NYQ", "NYSE"))*//*, $or("yfCsvLastUpdated" $exists false, "yfCsvLastUpdated" $lt cutOffTime)*/),
        projection = SecuritiesRef.Fields.toProjection)
        .sort(js.Array("symbol", 1))
        .toArrayFuture[SecuritiesRef]
    }

    @inline
    def updateQuote(quote: SecurityUpdateQuote) = {
      dao.updateOne(
        filter = "symbol" $eq quote.symbol,
        update = $set(
          "exchange" -> quote.exchange,
          "lastTrade" -> quote.lastTrade,
          "open" -> quote.open,
          "close" -> quote.close,
          "tradeDate" -> quote.tradeDate,
          "tradeTime" -> quote.tradeTime,
          "tradeDateTime" -> quote.tradeDateTime,
          "volume" -> quote.volume,
          "errorMessage" -> quote.errorMessage,
          "yfCsvResponseTime" -> quote.yfCsvResponseTime,
          "yfCsvLastUpdated" -> quote.yfCsvLastUpdated
        )
      )
    }

    @inline
    def updateQuotes(quotes: Seq[SecurityUpdateQuote]) = {
      dao.bulkWrite(js.Array(
        quotes map { quote =>
          updateOne(
            filter = "symbol" $eq quote.symbol,
            update = $set(
              "exchange" -> quote.exchange,
              "lastTrade" -> quote.lastTrade,
              "open" -> quote.open,
              "close" -> quote.close,
              "tradeDate" -> quote.tradeDate,
              "tradeTime" -> quote.tradeTime,
              "tradeDateTime" -> quote.tradeDateTime,
              "volume" -> quote.volume,
              "errorMessage" -> quote.errorMessage,
              "yfCsvResponseTime" -> quote.yfCsvResponseTime,
              "yfCsvLastUpdated" -> quote.yfCsvLastUpdated
            ))
        }: _*)
      )
    }

  }

  /**
    * Securities Update DAO Constructor
    * @param db the given [[Db database]]
    */
  implicit class SecuritiesUpdateDAOConstructor(val db: Db) extends AnyVal {

    @inline
    def getSecuritiesUpdateDAO(implicit ec: ExecutionContext) = db.collectionFuture("Stocks").mapTo[SecuritiesUpdateDAO]

  }

}