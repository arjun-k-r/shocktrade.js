package com.shocktrade.autonomous

import com.shocktrade.server.common.LoggerFactory
import com.shocktrade.server.common.ProcessHelper._
import io.scalajs.nodejs._
import io.scalajs.npm.mongodb.MongoClient
import io.scalajs.util.OptionHelper._

import scala.concurrent.duration._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.{queue => Q}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportAll

/**
  * Autonomous Trading Server Application
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@JSExportAll
object AutonomousTradingJsApp extends js.JSApp {

  override def main() {
    val logger = LoggerFactory.getLogger(getClass)

    logger.log("Starting the Shocktrade Autonomous Trading Engine...")

    // get the web application port
    val port = (process.env.get("port") ?? process.env.get("PORT")) getOrElse "1337"

    // determine the database connection URL
    val connectionString = process.dbConnect getOrElse "mongodb://localhost:27017/shocktrade"

    // handle any uncaught exceptions
    process.onUncaughtException { err =>
      logger.error("An uncaught exception was fired:")
      logger.error(err.stack)
    }

    // setup mongodb connection
    logger.log("Connecting to '%s'...", connectionString)
    implicit val dbFuture = MongoClient.connectAsync(connectionString).toFuture

    // run the autonomous trading engine once every 5 minutes
    val tradingEngine = new AutonomousTradingEngine(s"localhost:$port", dbFuture)
    setInterval(() => tradingEngine.run(), 5.minutes)
    tradingEngine.run() // TODO for testing only
  }

}
