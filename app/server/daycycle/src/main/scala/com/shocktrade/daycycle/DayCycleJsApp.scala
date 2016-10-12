package com.shocktrade.daycycle

import com.shocktrade.daycycle.daemons._
import com.shocktrade.daycycle.routes.DaemonRoutes
import com.shocktrade.server.common.ProcessHelper._
import com.shocktrade.server.common.{LoggerFactory, TradingClock}
import com.shocktrade.server.concurrent.Daemon._
import org.scalajs.nodejs.Bootstrap
import org.scalajs.nodejs.bodyparser.{BodyParser, UrlEncodedBodyOptions}
import org.scalajs.nodejs.express.Express
import org.scalajs.nodejs.globals.process
import org.scalajs.nodejs.kafkanode.KafkaNode
import org.scalajs.nodejs.mongodb.MongoDB
import org.scalajs.sjs.OptionHelper._

import scala.concurrent.duration._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.{queue => Q}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportAll

/**
  * Day-Cycle Server Application
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@JSExportAll
object DayCycleJsApp extends js.JSApp {

  override def main() {}

  def startServer(implicit bootstrap: Bootstrap) = {
    implicit val require = bootstrap.require

    val logger = LoggerFactory.getLogger(getClass)
    logger.info("Starting the Day-Cycle Server...")

    // determine the port to listen on
    val startTime = System.currentTimeMillis()

    // get the web application port
    val port = (process.env.get("port") ?? process.env.get("PORT")) getOrElse "1337"

    // determine the MongoDB and Zookeeper connection URLs
    val mongoConnectionString = process.dbConnect getOrElse "mongodb://localhost:27017/shocktrade"
    val zkConnectionString = process.zookeeperConnect getOrElse "localhost:2181"

    // handle any uncaught exceptions
    process.onUncaughtException { err =>
      logger.error("An uncaught exception was fired:")
      logger.error(err.stack)
    }

    logger.info("Loading the MongoDB module...")
    implicit val mongo = MongoDB()

    logger.info("Loading the Kafka module...")
    implicit val kafka = KafkaNode()

    // setup mongodb connection
    logger.info("Connecting to '%s'...", mongoConnectionString)
    implicit val dbFuture = mongo.MongoClient.connectFuture(mongoConnectionString)

    // setup kafka connection
    logger.info("Connecting to '%s'...", zkConnectionString)
    implicit val kafkaClient = kafka.Client(zkConnectionString)
    implicit val kafkaProducer = kafka.Producer(kafkaClient)

    // create the trading clock instance
    implicit val tradingClock = new TradingClock()

    logger.info("Loading Express modules...")
    implicit val express = Express()
    implicit val app = express()

    // setup the body parsers
    logger.log("Setting up body parsers...")
    val bodyParser = BodyParser()
    app.use(bodyParser.json())
    app.use(bodyParser.urlencoded(new UrlEncodedBodyOptions(extended = true)))

    // disable caching
    app.disable("etag")

    // define the daemons
    val daemons = Seq(
      DaemonRef("BarChartProfileUpdate", new BarChartProfileUpdateDaemon(dbFuture), kafkaReqd = false, delay = 5.hours, frequency = 12.hours),
      DaemonRef("BloombergUpdate", new BloombergUpdateDaemon(dbFuture), kafkaReqd = false, delay = 5.hours, frequency = 12.hours),
      DaemonRef("CikUpdate", new CikUpdateDaemon(dbFuture), kafkaReqd = false, delay = 4.hours, frequency = 12.hours),
      DaemonRef("EodDataCompanyUpdate", new EodDataCompanyUpdateDaemon(dbFuture), kafkaReqd = false, delay = 1.hours, frequency = 12.hours),
      DaemonRef("KeyStatisticsUpdate", new KeyStatisticsUpdateDaemon(dbFuture), kafkaReqd = false, delay = 2.hours, frequency = 12.hours),
      DaemonRef("NADSAQCompanyUpdate", new NADSAQCompanyUpdateDaemon(dbFuture), kafkaReqd = false, delay = 3.hours, frequency = 12.hours),
      DaemonRef("SecuritiesUpdate", new SecuritiesUpdateDaemon(dbFuture), kafkaReqd = false, delay = 0.seconds, frequency = 1.minutes),
      DaemonRef("SecuritiesRefreshKafka", new SecuritiesRefreshKafkaDaemon(dbFuture), kafkaReqd = true, delay = 10.days, frequency = 3.days)
    )

    // setup all other routes
    DaemonRoutes.init(app, daemons, dbFuture)

    // start the listener
    app.listen(port, () => logger.log("Server now listening on port %s [%d msec]", port, System.currentTimeMillis() - startTime))

    // separate the daemons by Kafka dependency
    val (daemonsKafka, daemonsNonKafka) = daemons.partition(_.kafkaReqd)

    // start the daemons without a Kafka dependency
    schedule(tradingClock, daemonsNonKafka)

    // wait for the Kafka producer to be ready, then schedule the Kafka-dependent daemons to run
    kafkaProducer.onReady(() => schedule(tradingClock, daemonsKafka))
  }

}
