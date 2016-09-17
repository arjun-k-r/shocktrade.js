package com.shocktrade.qualification

import org.scalajs.nodejs._
import org.scalajs.nodejs.globals.process
import org.scalajs.nodejs.mongodb.MongoDB

import scala.concurrent.duration._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.{queue => Q}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportAll

/**
  * Shocktrade Qualification Server Application
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@JSExportAll
object QualificationJsApp extends js.JSApp {

  override def main() {}

  def startServer(implicit bootstrap: Bootstrap) = {
    implicit val require = bootstrap.require

    console.log("Starting the Shocktrade Qualification Server...")

    // determine the database connection URL
    val connectionString = process.env.get("db_connection") getOrElse "mongodb://localhost:27017/shocktrade"

    // handle any uncaught exceptions
    process.onUncaughtException { err =>
      console.error("An uncaught exception was fired:")
      console.error(err.stack)
    }

    console.log("Loading MongoDB module...")
    implicit val mongo = MongoDB()

    // setup mongodb connection
    console.log("Connecting to '%s'...", connectionString)
    implicit val dbFuture = mongo.MongoClient.connectFuture(connectionString)

    // run the order qualification engine once every 5 minutes
    val qualification = new OrderQualificationEngine(dbFuture)
    setInterval(() => qualification.run(), 1.minutes)
    qualification.run() // TODO for testing only
  }

}
