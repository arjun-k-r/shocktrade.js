package com.shocktrade.webapp.routes

import java.util.UUID

import com.shocktrade.common.WsResponse
import org.scalajs.nodejs._
import org.scalajs.nodejs.express.Request
import org.scalajs.nodejs.expressws.WebSocket

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.util.{Failure, Success, Try}

/**
  * WebSocket Handler
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
object WebSocketHandler {
  private val clients = js.Array[WsClient]()

  def messageHandler(ws: WebSocket, request: Request, message: String) = {
    // handle the message
    message match {
      case "Hello" =>
        // have we received a message from this client before?
        val client = WsClient(ip = request.ip, ws = ws)
        console.log(s"Client ${client.uid} (${client.ip}) connected")
        clients.push(client)
      case unknown =>
        console.warn(s"Unhandled message '$message'...")
    }
  }

  def emit(action: String, data: String) = {
    setTimeout(() => {
      console.log(s"Broadcasting action '$action' with data '$data'...")
      clients.foreach(client => Try(client.send(action, data)) match {
        case Success(_) =>
        case Failure(e) =>
          console.warn(s"Client connection ${client.uid} (${client.ip}) failed")
          clients.indexWhere(_.uid == client.uid) match {
            case -1 => console.error(s"Client ${client.uid} was not removed")
            case index => clients.remove(index)
          }
      })
    }, 0.seconds)
  }

  /**
    * Represents a web-socket client
    * @param ws the given [[WebSocket web socket]]
    */
  case class WsClient(ip: String, ws: WebSocket) {
    val uid = UUID.randomUUID().toString

    def send(action: String, data: String) = ws.send(encode(action, data))

    private def encode(action: String, data: String) = JSON.stringify(new WsResponse(action, data))

  }

}
