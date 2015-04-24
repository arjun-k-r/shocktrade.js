package com.shocktrade.controllers

import java.util.Date

import akka.util.Timeout
import com.ldaniels528.commons.helpers.OptionHelper._
import com.shocktrade.controllers.QuoteResources.Quote
import com.shocktrade.models.contest.SearchOptions._
import com.shocktrade.models.contest._
import com.shocktrade.models.quote.StockQuotes
import com.shocktrade.util.BSONHelper._
import play.api._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.{obj => JS}
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * Contest Resources
 * @author lawrence.daniels@gmail.com
 */
object ContestResources extends Controller with MongoExtras {
  val DisplayColumns = Seq(
    "name", "creator", "startTime", "expirationTime", "startingBalance", "status",
    "ranked", "playerCount", "levelCap", "perksAllowed", "maxParticipants",
    "participants._id", "participants.name", "participants.facebookID")

  implicit val timeout: Timeout = 20.seconds

  /**
   * Cancels the specified order
   * @param contestId the given contest ID
   * @param playerId the given player ID
   * @param orderId the given order ID
   */
  def cancelOrder(contestId: String, playerId: String, orderId: String) = Action.async {
    // pull the order, add it to orderHistory, and return the participant
    Contests.closeOrder(contestId.toBSID, playerId.toBSID, orderId.toBSID)("participants.name", "participants.orders", "participants.orderHistory")
      .map(_.orDie(s"Order $orderId could not be canceled"))
      .map(contest => Ok(Json.toJson(contest)))
  }

  /**
   * Performs a search for contests
   * @return a JSON array of [[Contest]] instances
   */
  def contestSearch = Action.async { implicit request =>
    Try(request.body.asJson map (_.as[SearchOptions])) match {
      case Success(Some(searchOptions)) =>
        Contests.findContests(searchOptions)() map (contests => Ok(Json.toJson(contests)))
      case Success(None) =>
        Future.successful(BadRequest("Search options were expected as JSON body"))
      case Failure(e) =>
        Logger.error(s"${e.getMessage}: json = ${request.body.asJson.orNull}")
        Future.successful(InternalServerError(e.getMessage))
    }
  }

  /**
   * Creates a new contest
   */
  def createNewContest = Action.async { implicit request =>
    Try(request.body.asJson.map(_.as[ContestForm])) match {
      case Success(Some(form)) =>
        Contests.createContest(makeContest(form)) map (lastError => Ok(JS("result" -> lastError.message)))
      case Success(None) =>
        Future.successful(BadRequest("Contest form was expected as JSON body"))
      case Failure(e) =>
        Logger.error(s"${e.getMessage}: json = ${request.body.asJson.orNull}")
        Future.successful(InternalServerError(e.getMessage))
    }
  }

  private def makeContest(js: ContestForm) = {
    val startingBalance = BigDecimal(25000d)

    // create a player instance
    val player = Player(id = js.playerId.toBSID, name = js.playerName, facebookId = js.facebookId)

    // create the contest skeleton
    Contest(
      name = js.name,
      creator = player,
      creationTime = new Date(),
      startingBalance = startingBalance,
      friendsOnly = js.friendsOnly,
      acquaintances = js.acquaintances,
      invitationOnly = js.invitationOnly,
      perksAllowed = js.perksAllowed,
      ranked = js.ranked,
      messages = List(Message(sentBy = player, text = s"Welcome to ${js.name}")),
      participants = List(Participant(js.playerName, js.facebookId, fundsAvailable = startingBalance, id = js.playerId.toBSID))
    )
  }

  def createOrder(contestId: String, playerId: String) = Action.async { implicit request =>
    request.body.asJson.flatMap(_.asOpt[Order]) match {
      case Some(order) =>
        Contests.createOrder(contestId.toBSID, playerId.toBSID, order)("participants.name", "participants.orders", "participants.orderHistory") map {
          case Some(contest) =>
            contest.participants.find(_.id == playerId) match {
              case Some(player) => Ok(Json.toJson(player))
              case None => BadRequest("No player found")
            }
          case None => BadRequest(s"Contest $contestId not found")
        }
      case None => Future.successful(BadRequest("No order information"))
    }
  }

  def getContestByID(id: String) = Action.async {
    Contests.findContestByID(id.toBSID)() map {
      case Some(contest) => Ok(Json.toJson(contest))
      case None => BadRequest(s"Contest $id not found")
    }
  }

  def getContestRankings(id: String) = Action.async {
    (for {
      contest <- Contests.findContestByID(id.toBSID)() map (_ orDie s"Contest $id not found")
      rankings <- produceRankings(contest)
    } yield rankings) map (Ok(_))
  }

  def getContestParticipant(id: String, playerId: String) = Action.async {
    (for {
      contest <- Contests.findContestByID(id.toBSID)() map (_ orDie s"Contest $id not found")
      player = contest.participants.find(_.id == playerId) orDie s"Player $playerId not found"
      enrichedPlayer <- enrichParticipant(player)
    } yield enrichedPlayer).map(p => Ok(JsArray(Seq(p))))
  }

  /**
   * Returns a trading clock state object
   */
  def getContestsByPlayerID(playerId: String) = Action.async {
    Contests.findContestsByPlayerID(playerId.toBSID)(DisplayColumns: _*) map (contests => Ok(Json.toJson(contests)))
  }

  def getHeldSecurities(playerId: String) = Action.async {
    Contests.findContestsByPlayerID(playerId.toBSID)("participants.$") map { contests =>
      contests.flatMap(_.participants.flatMap(_.positions.map(_.symbol)))
    } map (symbols => Ok(JsArray(symbols.distinct.map(JsString))))
  }

  def createChatMessage(contestId: String) = Action.async { implicit request =>
    request.body.asJson flatMap (_.asOpt[Message]) match {
      case Some(message) =>
        Contests.createMessage(contestId.toBSID, message)("messages") map {
          case Some(contest) => Ok(Json.toJson(contest.messages))
          case None => Ok(JsArray())
        }
      case None =>
        Future.successful(Ok(JS("status" -> "error", "message" -> "No message sent")))
    }
  }

  private def produceRankings(contest: Contest): Future[JsArray] = {
    for {
    // compute the total equity for each player
      rankings <- produceNetWorths(contest)

      // sort the participants by net-worth
      rankedPlayers = (1 to rankings.size).toSeq zip rankings.sortBy(-_.totalEquity)

    } yield JsArray(rankedPlayers map { case (place, p) =>
      import p._
      JS("name" -> name,
        "facebookID" -> facebookID,
        "score" -> score,
        "totalEquity" -> totalEquity,
        "gainLoss" -> gainLoss_%,
        "rank" -> placeName(place))
    })
  }

  private def produceNetWorths(contest: Contest): Future[Seq[Ranking]] = {
    // get the contest's values
    val startingBalance = contest.startingBalance
    val participants = contest.participants
    val allSymbols = participants.flatMap(_.positions.map(_.symbol))

    for {
    // query the quotes for all symbols
      quotes <- QuoteResources.findQuotesBySymbols(allSymbols)

      // create the mapping of symbols to quotes
      mapping = Map(quotes map (q => (q.symbol.getOrElse(""), q)): _*)

      // get the participants' net worth and P&L
      totalWorths = participants map (asRanking(startingBalance, mapping, _))

    // return the players' total worth
    } yield totalWorths
  }

  private def enrichParticipant(player: Participant): Future[JsObject] = {
    import player.positions

    // get the positions and associated symbols
    val symbols = positions.map(_.symbol).distinct

    for {
    // load the quotes for all position symbols
      quotesJs <- StockQuotes.findQuotes(symbols)("symbol", "lastTrade")

      // build a mapping of symbol to last trade
      quotes = Map(quotesJs flatMap { js =>
        for {symbol <- (js \ "symbol").asOpt[String]; lastTrade <- (js \ "lastTrade").asOpt[Double]} yield (symbol, lastTrade)
      }: _*)

      // enrich the positions
      enrichedPositions = positions flatMap { pos =>
        for {
          marketPrice <- quotes.get(pos.symbol)
          netValue = marketPrice * pos.quantity
          gainLoss = netValue - pos.cost
        } yield JS("lastTrade" -> marketPrice, "netValue" -> netValue, "gainLoss" -> gainLoss) ++ Json.toJson(pos).asInstanceOf[JsObject]
      }

      // re-insert into the participant object
      enrichedPlayer = Json.toJson(player).asInstanceOf[JsObject] ++ JS("positions" -> JsArray(enrichedPositions))
      _ = Logger.info(s"enrichedPositions = $enrichedPositions")
    } yield enrichedPlayer
  }

  private def asRanking(startingBalance: BigDecimal, mapping: Map[String, Quote], p: Participant) = {
    import p.{facebookId, fundsAvailable, name, positions, score}

    val symbols = positions.map(_.symbol).distinct
    val investment = (symbols flatMap (s => mapping.get(s) flatMap (_.lastTrade))).sum
    val totalEquity = fundsAvailable + investment
    val gainLoss_% = ((totalEquity - startingBalance) / startingBalance) * 100d
    Ranking(name, facebookId, score, totalEquity, gainLoss_%)
  }

  private def placeName(place: Int) = {
    place match {
      case 1 => "1st"
      case 2 => "2nd"
      case 3 => "3rd"
      case n => s"${n}th"
    }
  }

  case class ContestForm(name: String,
                         playerId: String,
                         playerName: String,
                         facebookId: String,
                         acquaintances: Option[Boolean],
                         friendsOnly: Option[Boolean],
                         invitationOnly: Option[Boolean],
                         perksAllowed: Option[Boolean],
                         ranked: Option[Boolean])

  implicit val contestFormReads: Reads[ContestForm] = (
    (__ \ "name").read[String] and
      (__ \ "player" \ "id").read[String] and
      (__ \ "player" \ "name").read[String] and
      (__ \ "player" \ "facebookId").read[String] and
      (__ \ "acquaintances").readNullable[Boolean] and
      (__ \ "friendsOnly").readNullable[Boolean] and
      (__ \ "invitationOnly").readNullable[Boolean] and
      (__ \ "perksAllowed").readNullable[Boolean] and
      (__ \ "ranked").readNullable[Boolean])(ContestForm.apply _)

  case class Ranking(name: String, facebookID: String, score: Int, totalEquity: BigDecimal, gainLoss_% : BigDecimal)

}