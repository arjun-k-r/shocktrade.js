package com.shocktrade.server.dao.securities

import com.shocktrade.common.models.contest.OrderLike
import io.scalajs.npm.mongodb._

import scala.concurrent.ExecutionContext
import scala.scalajs.js

/**
  * Securities Snapshot DAO
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@js.native
trait SecuritiesSnapshotDAO extends Collection

/**
  * Securities Snapshot DAO Companion
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
object SecuritiesSnapshotDAO {

  /**
    * Securities Snapshot DAO Enrichment
    * @param dao the given [[SecuritiesUpdateDAO Stock DAO]]
    */
  implicit class SecuritiesSnapshotDAOEnrichment(val dao: SecuritiesSnapshotDAO) extends AnyVal {

    @inline
    def findMatch(order: OrderLike)(implicit ec: ExecutionContext) = {
      dao.findOneFuture[SnapshotQuote](doc(Seq(
        Option("symbol" $eq order.symbol),
        Option("tradeDateTime" between(order.creationTime, new js.Date())),
        Option("volume" $gte order.quantity),
        // pricing
        if (order.isLimitOrder) Option(if (order.isBuyOrder) "lastTrade" $lte order.price else "lastTrade" $gte order.price)
        else None
      ).flatten: _*))
    }

    @inline
    def findSnapshots(symbol: String, startTime: js.Date, endTime: js.Date) = {
      dao.find(doc("symbol" $eq symbol, "tradeDateTime" between(startTime, endTime))).toArrayFuture[SnapshotQuote]
    }

    @inline
    def updateSnapshots(snapshots: Seq[SnapshotQuote]) = {
      dao.bulkWrite(js.Array(snapshots map insertOne: _*))
    }

  }

  /**
    * Securities Snapshot DAO Constructor
    * @param db the given [[Db database]]
    */
  implicit class SecuritiesSnapshotDAOConstructor(val db: Db) extends AnyVal {

    @inline
    def getSnapshotDAO(implicit ec: ExecutionContext) = {
      db.collectionFuture("Snapshots").mapTo[SecuritiesSnapshotDAO]
    }

  }

}