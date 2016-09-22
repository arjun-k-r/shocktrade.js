package com.shocktrade.autonomous

import com.shocktrade.autonomous.AutonomousTradingEngine._
import com.shocktrade.autonomous.dao.RobotDAO._
import com.shocktrade.autonomous.dao.{BuyingFlow, RobotData, SellingFlow}
import com.shocktrade.common.dao.contest.PortfolioUpdateDAO._
import com.shocktrade.common.dao.contest._
import com.shocktrade.common.dao.quotes.SecuritiesDAO
import com.shocktrade.common.dao.quotes.SecuritiesDAO._
import com.shocktrade.common.events.{OrderEvents, RemoteEvent}
import com.shocktrade.common.models.contest.OrderLike._
import com.shocktrade.common.models.contest.PositionLike
import com.shocktrade.common.models.quote.ResearchQuote
import com.shocktrade.services.{LoggerFactory, RemoteEventService, TradingClock}
import org.scalajs.nodejs.NodeRequire
import org.scalajs.nodejs.moment.Moment
import org.scalajs.nodejs.mongodb.{Db, MongoDB}
import org.scalajs.nodejs.npm.numeral.Numeral
import org.scalajs.nodejs.npm.numeral.Numeral.Implicits._
import org.scalajs.nodejs.os.OS
import org.scalajs.nodejs.util.ScalaJsHelper._
import org.scalajs.nodejs.util.Util
import org.scalajs.sjs.DateHelper._
import org.scalajs.sjs.JsUnderOrHelper._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.scalajs.js
import scala.scalajs.js.annotation.ScalaJSDefined
import scala.util.{Failure, Success}

/**
  * Autonomous Trading Engine
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
class AutonomousTradingEngine(webAppEndPoint: String, dbFuture: Future[Db])(implicit ec: ExecutionContext, mongo: MongoDB, require: NodeRequire) {
  // load the required modules
  private implicit val moment = Moment()
  private implicit val numeral = Numeral()
  private implicit val os = OS()
  private implicit val util = Util()

  // create DAO instances
  private implicit val securitiesDAO = dbFuture.flatMap(_.getSecuritiesDAO)
  private implicit val portfolioDAO = dbFuture.flatMap(_.getPortfolioUpdateDAO)
  private implicit val robotDAO = dbFuture.flatMap(_.getRobotDAO)

  // create the service instances
  private implicit val tradingClock = new TradingClock()
  private val logger = LoggerFactory.getLogger(getClass)
  private val removeEventService = new RemoteEventService(webAppEndPoint)

  // create the rule compiler and processor
  private implicit val processor = new RuleProcessor()
  private implicit val compiler = new RuleCompiler()

  /**
    * Invokes the process
    */
  def run(): Unit = {
    logger.info("Looking for robots....")
    val startTime = System.currentTimeMillis()
    val outcome = for {
      robots <- robotDAO.flatMap(_.findRobots())
      results <- Future.sequence(robots.toSeq.map(operate)) map (_.flatten)
    } yield results

    outcome onComplete {
      case Success(results) =>
        logger.log(s"Process completed in ${System.currentTimeMillis() - startTime} msec")
      case Failure(e) =>
        logger.error(s"Failed to process robot: ${e.getMessage}")
        e.printStackTrace()
    }
  }

  /**
    * Processes the portfolios for the given robot
    * @param robot the given [[RobotData robot]]
    * @return a promise of the outcomes
    */
  def operate(robot: RobotData) = {
    robot.playerID.toOption match {
      case Some(playerID) =>
        robot.info("Retrieving portfolios...")
        for {
          portfolios <- portfolioDAO.flatMap(_.findByPlayer(playerID))
          results <- processOrders(robot, portfolios)
        } yield results

      case None =>
        robot.error(s"No player ID found")
        Future.successful(Nil)
    }
  }

  @inline
  private def processOrders(robot: RobotData, portfolios: Seq[PortfolioData]) = {
    Future.sequence {
      for {
        tradingStrategy <- robot.tradingStrategy.toList
        buyingFlow <- tradingStrategy.buyingFlow.toList
        sellingFlow <- tradingStrategy.sellingFlow.toList
        name <- tradingStrategy.name.toList
        portfolio <- portfolios
        portfolioId <- portfolio._id.map(_.toHexString()).toList
      } yield {
        robot.info(s"Playing with portfolio #$portfolioId using the '$name' strategy")

        // create the robot environment
        implicit val env = RobotEnvironment(name, portfolio)

        for {
        // execute the buying flow
          securities <- buyingFlow.execute()
          buyOrders = createBuyOrders(robot, buyingFlow, securities)

          // execute the selling flow
          positions <- sellingFlow.execute()
          sellOrders <- createSellOrders(robot, sellingFlow, positions)

          // combine the buy and sell orders into a single collection
          orders = {
            val combinedOrders = buyOrders ++ sellOrders
            showOrders(robot, combinedOrders)
            combinedOrders
          }

          // persist the orders
          result <- portfolio._id.toOption match {
            case Some(id) if orders.nonEmpty =>
              val portfolioId = id.toHexString()
              portfolioDAO.flatMap(_.createOrders(portfolioId, orders).toFuture) map { result =>
                robot.log(s"${orders.size} order(s) were created")
                sendEvent(robot, OrderEvents.updated(portfolioId))
                new UpdateResult(success = result.isOk, updates = orders.size)
              }
            case _ =>
              Future.successful(new UpdateResult())
          }
        } yield result
      }
    }
  }

  @inline
  private def createBuyOrders(robot: RobotData, buyingFlow: BuyingFlow, securities: Seq[ResearchQuote])(implicit env: RobotEnvironment) = {
    if (securities.isEmpty) Nil
    else {
      // display the securities
      showQuotes(robot, securities)

      robot.log("Processing BUY orders...")

      (for {
        cashFunds <- env.portfolio.cashAccount.flatMap(_.cashFunds)
        availableCash = cashFunds - env.outstandingOrdersCost
        preferredSpend <- buyingFlow.preferredSpendPerSecurity
        numOfSecuritiesToBuy = (cashFunds / preferredSpend).toInt
        securitiesToBuy = securities.take(numOfSecuritiesToBuy)
        buyOrders = if (availableCash > preferredSpend) {
          robot.log("Cash available: $%d (%d max orders)", availableCash, numOfSecuritiesToBuy)
          securitiesToBuy flatMap { security =>
            for {
              low <- security.low.toOption
              computedQuantity = (preferredSpend / low).toLong
              volume = security.avgVolume10Day.map(vol => (vol * 0.25).toLong) getOrElse computedQuantity
              quantity = if (computedQuantity > volume) volume else computedQuantity
            } yield {
              security.toOrder(
                accountType = ACCOUNT_TYPE_CASH,
                orderType = ORDER_TYPE_BUY,
                priceType = PRICE_TYPE_LIMIT,
                price = low,
                quantity = quantity)
            }
          }
        } else {
          robot.log("No cash available for purchases (%d)", availableCash)
          Nil
        }
      } yield buyOrders).toOption.toSeq.flatten
    }
  }

  @inline
  private def createSellOrders(robot: RobotData, flow: SellingFlow, positions: Seq[PositionData]) = {
    if (positions.isEmpty) Future.successful(Seq.empty[OrderData])
    else {
      // display the current positions
      showPositions(robot, positions)

      robot.log("Processing SELL orders...")

      for {
        positionsEtc <- Future.sequence(positions.map(p => p.computePercentGain.map(p -> _)))
        orders = positionsEtc flatMap {
          case (position, Some((lastTrade, pctGain))) if pctGain >= 25 =>
            robot.info("%s @ %d x %s (last: %d, gain/loss: %s%%) <%s>",
              position.symbol.orNull, position.pricePaid.orZero, position.quantity.orZero.format("0,0"),
              lastTrade, pctGain.format("0.0"), position._id.orNull)
            Option(new OrderData(
              symbol = position.symbol,
              exchange = position.exchange,
              accountType = position.accountType,
              orderType = ORDER_TYPE_SELL,
              priceType = PRICE_TYPE_LIMIT,
              price = lastTrade,
              quantity = position.quantity,
              creationTime = new js.Date(),
              expirationTime = new js.Date() + 7.days
            ))
          case (position, Some((lastTrade, pctGain))) => None
          case _ => None
        }
      } yield orders
    }
  }

  private def sendEvent(robot: RobotData, event: RemoteEvent) = {
    removeEventService.send(event) onComplete {
      case Success(_) => robot.log("Event transmitted")
      case Failure(e) => robot.error(s"Failed during transmission: ${e.getMessage}")
    }
  }

  private def showQuotes(robot: RobotData, quotes: Seq[ResearchQuote]) = {
    robot.log(s"${quotes.size} securities identified:")
    quotes foreach { q =>
      robot.log("| %s/%s | price: %d | low: %d | high: %d | volume: %s | avgVol: %s | spread: %d%% |",
        q.symbol, q.exchange, q.lastTrade.orZero, q.low.orZero, q.high.orZero,
        q.volume.orZero.format("0,0"), q.avgVolume10Day.orZero.format("0,0"),
        (q.spread.orZero * 10).toInt / 10.0)
    }
  }

  private def showOrders(robot: RobotData, orders: Seq[OrderData]) = {
    robot.log(s"${orders.size} eligible order(s):")
    orders.zipWithIndex foreach { case (o, n) =>
      robot.log(s"[${n + 1}] ${o.orderType} / ${o.symbol} @ ${o.price getOrElse "MARKET"} x ${o.quantity} - ${o.priceType} <${o._id}>")
    }
  }

  private def showPositions(robot: RobotData, positions: Seq[PositionData]) = {
    robot.log(s"${positions.size} eligible position(s):")
    positions.zipWithIndex foreach { case (p, n) =>
      robot.log(s"[${n + 1}] ${p.symbol} @ ${p.pricePaid} x ${p.quantity} <${p._id}>")
    }
  }

}

/**
  * Autonomous Trading Engine Companion
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
object AutonomousTradingEngine {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  /**
    * Research Quote Extensions
    * @param quote the given [[ResearchQuote quote]]
    */
  implicit class ResearchQuoteExtensions(val quote: ResearchQuote) extends AnyVal {

    @inline
    def toOrder(accountType: String,
                orderType: String,
                priceType: String,
                price: Double,
                quantity: Double)(implicit tradingClock: TradingClock) = {
      val now = new js.Date()
      new OrderData(
        symbol = quote.symbol,
        exchange = quote.exchange,
        accountType = accountType,
        orderType = orderType,
        priceType = priceType,
        price = price,
        quantity = quantity,
        creationTime = tradingClock.getLastTradeStartTime, // TODO replace with "now" after testing
        expirationTime = now + 3.days
      )
    }
  }

  /**
    * Robots Extensions
    * @param robot the given [[RobotData robot]]
    */
  implicit class RobotsExtensions(val robot: RobotData) extends AnyVal {

    @inline
    def log(format: String, args: Any*) = {
      logger.log(s"[${robot.name.orNull}] " + format, args: _*)
    }

    @inline
    def info(format: String, args: Any*) = {
      logger.info(s"[${robot.name.orNull}] " + format, args: _*)
    }

    @inline
    def error(format: String, args: Any*) = {
      logger.error(s"[${robot.name.orNull}] " + format, args: _*)
    }

    @inline
    def warn(format: String, args: Any*) = {
      logger.warn(s"[${robot.name.orNull}] " + format, args: _*)
    }

  }

  /**
    * Buying Flow Extensions
    * @param flow the given [[BuyingFlow buying flow]]
    */
  implicit class BuyingFlowExtensions(val flow: BuyingFlow) extends AnyVal {

    @inline
    def execute()(implicit ec: ExecutionContext, compiler: RuleCompiler, processor: RuleProcessor, env: RobotEnvironment, securitiesDAO: Future[SecuritiesDAO]) = {
      for {
      // get the collection of eligible quotes
        securities <- flow.searchOptions.toOption match {
          case Some(options) => securitiesDAO.flatMap(_.research(options)) map (_.toSeq)
          case None => Future.successful(Nil)
        }

        // compile the flow
        opCodes = compiler(flow)

      // use the rules to filter out ineligible securities
      } yield processor(opCodes, securities)
    }
  }

  /**
    * Selling Flow Extensions
    * @param flow the given [[SellingFlow selling flow]]
    */
  implicit class SellingFlowExtensions(val flow: SellingFlow) extends AnyVal {

    @inline
    def execute()(implicit ec: ExecutionContext, compiler: RuleCompiler, processor: RuleProcessor, env: RobotEnvironment, portfolioDAO: Future[PortfolioUpdateDAO]) = {
      // remove any positions there are already orders for
      val sellOrderSymbols = env.orders.filter(_.isSellOrder).flatMap(_.symbol.toOption).toSet
      val eligiblePositions = env.positions.filterNot(_.symbol.exists(sellOrderSymbols.contains))
      Future.successful(eligiblePositions)
    }
  }

  /**
    * Position Extensions
    * @param position the given [[PositionLike position]]
    */
  implicit class PositionExtensions(val position: PositionLike) extends AnyVal {

    @inline
    def computePercentGain(implicit ec: ExecutionContext, securitiesDAO: Future[SecuritiesDAO]) = {
      val params = for {
        symbol <- position.symbol
        cost <- position.totalCost
        quantity <- position.quantity
      } yield (symbol, cost, quantity)

      params.toOption match {
        case Some((symbol, cost, quantity)) =>
          securitiesDAO.flatMap(_.findQuote[PricingQuote](symbol, fields = PricingQuote.Fields)) map {
            case Some(quote) => quote.lastTrade.map(price => price -> gainLoss(price, quantity, cost)).toOption
            case None => None
          }
        case None => Future.successful(None)
      }
    }

    @inline
    def gainLoss(price: Double, quantity: Double, cost: Double) = {
      if (cost > 0) 100 * (price * quantity - cost) / cost else 0.0
    }

  }

  @ScalaJSDefined
  class UpdateResult(val success: Boolean = false, val updates: Int = 0) extends js.Object

  /**
    * Represents a pricing quote
    * @param symbol    the given symbol (e.g. "AAPL")
    * @param lastTrade the given last trade
    */
  @ScalaJSDefined
  class PricingQuote(val symbol: String, val lastTrade: js.UndefOr[Double]) extends js.Object

  /**
    * Price Quote Companion
    * @author Lawrence Daniels <lawrence.daniels@gmail.com>
    */
  object PricingQuote {
    val Fields = Seq("symbol", "lastTrade")
  }

}