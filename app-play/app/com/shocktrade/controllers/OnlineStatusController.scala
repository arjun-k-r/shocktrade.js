package com.shocktrade.controllers

import java.util.Date

import com.shocktrade.actors.WebSockets
import com.shocktrade.actors.WebSockets.UserStateChanged
import com.shocktrade.controllers.QuotesController._
import com.shocktrade.util.BSONHelper._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.json.Json.{obj => JS}
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.json.BSONFormats._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument => BS, _}

import scala.concurrent.Future

/**
 * Online Status Resources
 * @author lawrence.daniels@gmail.com
 */
object OnlineStatusController extends Controller with ErrorHandler {
  private val CollectionName = "OnlineStatuses"
  private val mc = db.collection[BSONCollection](CollectionName)

  // This collection requires a TTL index to function properly
  // db.OnlineStatuses.createIndex({updatedTime:1}, {expireAfterSeconds:1800})

  ////////////////////////////////////////////////////////////////////////////
  //      API functions
  ////////////////////////////////////////////////////////////////////////////

  def getGroupStatus = Action.async { request =>
    request.body.asText.map(_.split("[,]")) match {
      case Some(userIDs) =>
        mc.find(BS("_id" -> BS("$in" -> userIDs))).cursor[BS]().collect[Seq]() map (results => Ok(Json.toJson(results))) recover {
          case e => Ok(createError(e))
        }
      case None =>
        Future.successful(BadRequest("comma delimited string expected"))
    }
  }

  def getStatus(userID: String) = Action.async {
    mc.find(BS("_id" -> userID.toBSID)).one[BS] map {
      case Some(status) => Ok(Json.toJson(status))
      case None => Ok(JS("_id" -> userID.toBSID, "connected" -> false))
    } recover {
      case e => Ok(createError(e))
    }
  }

  def setIsOnline(userID: String) = Action.async {
    setConnectedStatus(userID, newState = true) map (state => Ok(JS("connected" -> state))) recover {
      case e => Ok(createError(e))
    }
  }

  def setIsOffline(userID: String) = Action.async {
    setConnectedStatus(userID, newState = false) map (state => Ok(JS("connected" -> state))) recover {
      case e => Ok(createError(e))
    }
  }

  private def setConnectedStatus(userID: String, newState: Boolean): Future[Boolean] = {
    mc.findAndUpdate(
      selector = BS("_id" -> userID.toBSID),
      update = BS("$set" -> BS("connected" -> newState, "updatedTime" -> new Date())),
      fetchNewObject = false, upsert = true
    ) map { outcome =>
      outcome.result[BS] match {
        case Some(oldStatus) =>
          if (!oldStatus.getAs[Boolean]("connected").contains(newState)) {
            Logger.info(s"User $userID is now ${if (newState) "Online" else "Offline"}")
            WebSockets ! UserStateChanged(userID, newState)
          }
          newState
        case None =>
          WebSockets ! UserStateChanged(userID, newState)
          newState
      }
    }
  }

}
