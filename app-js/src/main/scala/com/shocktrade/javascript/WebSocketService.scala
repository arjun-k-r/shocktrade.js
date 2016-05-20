package com.shocktrade.javascript

import com.github.ldaniels528.meansjs.angularjs._
import com.github.ldaniels528.meansjs.angularjs.http.Http
import com.github.ldaniels528.meansjs.angularjs.{Location, Timeout}
import com.github.ldaniels528.meansjs.angularjs.toaster.Toaster
import com.github.ldaniels528.meansjs.angularjs.{Service, injected}
import com.github.ldaniels528.meansjs.util.ScalaJsHelper._
import org.scalajs.dom.raw.{CloseEvent, ErrorEvent, MessageEvent}
import org.scalajs.dom.{Event, WebSocket, console}

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.JSON

/**
 * Web Socket Service
 * @author Lawrence Daniels <lawrence.daniels@gmail.com>
 */
class WebSocketService($rootScope: js.Dynamic, $http: Http, $location: Location, $timeout: Timeout, toaster: Toaster,
                       @injected("MySessionService") mySession: MySessionService)
  extends Service {

  private var socket: WebSocket = null
  private var connected = false
  private var attemptsLeft = 3

  /**
   * Initializes the service
   */
  def init() {
    console.log("Initializing Websocket service...")
    if (!isDefined(g.window.WebSocket)) {
      console.log("Using a Mozilla Web Socket")
      g.window.WebSocket = g.window.MozWebSocket
    }

    if (isDefined(g.window.WebSocket)) connect()
    else
      toaster.pop("Info", "Your browser does not support Web Sockets.", null)
  }

  /**
   * Indicates whether a connection is established
   */
  def isConnected = connected

  /**
   * Transmits the message to the server via web-socket
   */
  def send(message: String) = {
    if (!isDefined(g.window.WebSocket)) {
      toaster.error("Web socket closed")
      false
    }
    if (socket.readyState == WebSocket.OPEN) {
      socket.send(message)
      true
    } else {
      toaster.error(s"Web socket closed: readyState = ${socket.readyState}")
      false
    }
  }

  /**
   * Handles the incoming web socket message event
   * @param event the given web socket message event
   */
  private def handleMessage(event: MessageEvent) {
    if (event.data != null) {
      val message = JSON.parse(event.data.asInstanceOf[String])
      if (isDefined(message.action)) {
        console.log(s"Broadcasting action '${message.action}'")
        $rootScope.$broadcast(message.action, message.data)
      }
      else g.console.warning(s"Message does not contain an action message = ${JSON.stringify(message)}")
    }
    else g.console.warning(s"Unhandled event received - ${JSON.stringify(event)}")
  }

  private def sendState(connected: Boolean) {
    mySession.userProfile._id.toOption match {
      case Some(userID) =>
        console.log(s"Sending connected status for user $userID ...")
        if (connected) $http.put(s"/api/online/$userID")
        else $http.delete(s"/api/online/$userID")
      case None =>
        console.log(s"User unknown, waiting 5 seconds ($attemptsLeft attempts remaining)...")
        if (attemptsLeft > 0) {
          $timeout(() => sendState(connected), 5000)
          attemptsLeft -= 1
        }
    }
  }

  /**
   * Establishes a web socket connection
   */
  private def connect() {
    val endpoint = s"ws://${$location.host()}:${$location.port()}/websocket"
    console.log(s"Connecting to websocket endpoint '$endpoint'...")

    // open the connection and setup the handlers
    socket = new WebSocket(endpoint)

    socket.onopen = (event: Event) => {
      connected = true
      sendState(connected)
      console.log("Websocket connection established")
    }

    socket.onclose = (event: CloseEvent) => {
      connected = false
      sendState(connected)
      g.console.warn("Websocket connection lost")
      $timeout(() => connect(), 15.seconds)
    }

    socket.onerror = (event: ErrorEvent) => ()

    socket.onmessage = (event: MessageEvent) => handleMessage(event)
  }

}
