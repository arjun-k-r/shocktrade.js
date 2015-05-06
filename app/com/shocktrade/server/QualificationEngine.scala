package com.shocktrade.server

import java.util.Date

import akka.actor.Props
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.shocktrade.controllers.Application._
import com.shocktrade.server.TradingActor.ProcessOrders
import com.shocktrade.util.DateUtil._
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.iteratee.Iteratee
import play.api.{Logger, Play}
import play.libs.Akka
import reactivemongo.api.collections.default.BSONCollection

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success}

/**
 * Order Qualification and Processing Engine
 * @author lawrence.daniels@gmail.com
 */
object QualificationEngine {
  private lazy val system = Akka.system
  private lazy val tradingActor = system.actorOf(Props[TradingActor].withRouter(RoundRobinPool(nrOfInstances = 10)), name = "TradingActor")
  private lazy val mc = db.collection[BSONCollection]("Contests")
  private implicit val timeout: Timeout = 30.second
  private var lastEffectiveDate: DateTime = computeInitialFulfillmentDate
  private val frequency = 1 // TODO should be 5 (in minutes)

  import system.dispatcher

  def start() {
    // TODO for now, only run it in DEV
    if (Play.isDev) {
      // process orders once every 5 minutes
      system.scheduler.schedule(5.seconds, frequency.minutes, () => processOrders())
    }
    ()
  }

  def processOrders() {
    try {
      val currentTime = new Date()

      Logger.info(s"Processing order fulfillment [as of $lastEffectiveDate]...")
      TradingDAO.getActiveContests(lastEffectiveDate, lastEffectiveDate.minusMinutes(frequency)).enumerate().apply(Iteratee.foreach { contest =>
        // first obtain an exclusive lock on the contest
        TradingDAO.lockContest(contest.id) onComplete {
          case Failure(e) =>
            Logger.error(s"Failed while attempting to lock Contest '${contest.name}'", e)
          case Success(contest_?) =>
            // if the lock was successfully retrieved, process the contest
            contest_? foreach { case (lockedContest, lockExpirationTime) =>
              tradingActor ! ProcessOrders(lockedContest, lockExpirationTime, lastEffectiveDate)
            }
        }
      })

      // update the effective date
      lastEffectiveDate = currentTime;

    } catch {
      case e: Exception =>
        Logger.error("Error processing orders", e)
    }
  }

  private def computeInitialFulfillmentDate: Date = {
    val tradeStart = getTradeStartTime
    val currentTime = new Date()
    if(tradeStart >= currentTime) currentTime else tradeStart
  }

  /**
   * Java 8-style anonymous function as a Runnable
   */
  implicit def function0ToRunnable(f: () => Unit): Runnable = new Runnable {
    override def run(): Unit = f()
  }

}
