package com.shocktrade.javascript.discover

import com.github.ldaniels528.scalascript.core.Location
import com.github.ldaniels528.scalascript.extensions.Toaster
import com.github.ldaniels528.scalascript.{Controller, injected}
import com.shocktrade.javascript.Filters.toDuration
import com.shocktrade.javascript.MySession
import com.shocktrade.javascript.ScalaJsHelper._
import com.shocktrade.javascript.dashboard.ContestService
import com.shocktrade.javascript.discover.ChatController._
import com.shocktrade.javascript.models.{Message, PlayerRef}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => JS}
import scala.util.{Failure, Success}

/**
 * Chat Controller
 * @author lawrence.daniels@gmail.com
 */
class ChatController($scope: js.Dynamic, $location: Location, toaster: Toaster,
                     @injected("MySession") mySession: MySession,
                     @injected("ContestService") contestService: ContestService)
  extends Controller {

  private val colorMap = js.Dictionary[String]()
  private var lastUpdateTime = 0d
  private var lastMessageCount = 0
  private var cachedHtml = ""

  $scope.chatMessage = ""

  $scope.addSmiley = (emoticon: js.Dynamic) => $scope.chatMessage += " " + emoticon.symbol

  $scope.getEmoticons = () => Emoticons

  $scope.getMessages = () => getMessages

  $scope.sendChatMessage = (messageText: String) => sendChatMessage(messageText)

  /////////////////////////////////////////////////////////////////////////////
  //			Local Functions and Data
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Returns chat messages sorted by time
   * @return an HTML string
   */
  private def getMessages: String = {
    val messages = mySession.getMessages
    if ((messages.length == lastMessageCount) && (js.Date.now() - lastUpdateTime) <= 1000) cachedHtml
    else {
      // capture the start time
      val startTime = js.Date.now()

      // capture the new number of lines
      lastMessageCount = messages.length

      // build an HTML string with emoticons
      val html = sortMessagesByTime(messages).foldLeft[String]("") { (html, msg) =>
        // replace the symbols with icon images
        var text = msg.text
        Emoticons.foreach { emo =>
          text = text.replaceAllLiterally(emo.symbol.as[String], s"""<img src="assets/images/smilies/${emo.uri.as[String]}">""")
        }

        html + s"""<img src="http://graph.facebook.com/${msg.sender.facebookID}/picture" class="chat_icon">
                   <span class="bold" style="color: ${colorOf(msg.sender.name)}">${msg.sender.name}</span>&nbsp;
                   [<span class="st_bkg_color">${toDuration(msg.sentTime)}</span>]&nbsp;$text<br>""".stripPrefix(" ")
      }

      //console.log(f"Generated HTML in ${js.Date.now() - startTime}%.1f msec(s)")
      cachedHtml = html
      lastUpdateTime = js.Date.now()
      cachedHtml
    }
  }

  private def sortMessagesByTime(messages: js.Array[Message]) = messages.sort({ (aa: Message, bb: Message) =>
    val a = aa.dynamic
    val b = bb.dynamic
    val timeA = if (!js.isUndefined(a.sentTime.$date)) a.sentTime.$date else a.sentTime
    val timeB = if (!js.isUndefined(b.sentTime.$date)) b.sentTime.$date else b.sentTime
    (timeB.asInstanceOf[Double] - timeA.asInstanceOf[Double]).toInt
  })

  private def colorOf(name: String) = colorMap.getOrElseUpdate(name, Colors((1 + colorMap.size) % Colors.length))

  /**
   * Sends a chat message to the server
   * @param messageText the given chat message text
   */
  private def sendChatMessage(messageText: String) {
    val outcome = for {
      playerId <- mySession.userProfile.OID_?
      facebookID <- mySession.facebookID
      contestId <- mySession.contest.flatMap(_.OID_?)
    } yield (playerId, facebookID, contestId)

    outcome match {
      case Some((playerId, facebookID, contestId)) =>
        if (messageText.trim.nonEmpty) {
          // build the message blob
          val message = makeNew[Message]
          message.text = messageText
          //message.recipient = null
          message.sender = PlayerRef(userId = playerId, name = mySession.getUserName, facebookID = facebookID)

          // transmit the message
          contestService.sendChatMessage(contestId, message) onComplete {
            case Success(messages) =>
              $scope.chatMessage = ""
              mySession.setMessages(messages)
            case Failure(e) =>
              toaster.error("Failed to send message")
          }
        }
      case None =>
        toaster.error("No game selected")
    }
  }

}

/**
 * Chat Controller Singleton
 * @author lawrence.daniels@gmail.com
 */
object ChatController {

  private val Colors = js.Array("#0088ff", "#ff00ff", "#008888", "#2200ff")

  private val Emoticons = js.Array(
    JS(symbol = ":-@", uri = "icon_mrgreen.gif", tooltip = "Big Grin"),
    JS(symbol = ":-)", uri = "icon_smile.gif", tooltip = "Smile"),
    JS(symbol = "-)", uri = "icon_wink.gif", tooltip = "Wink"),
    JS(symbol = ":-D", uri = "icon_biggrin.gif", tooltip = "Big Smile"),
    JS(symbol = ":->", uri = "icon_razz.gif", tooltip = "Razzed"),
    JS(symbol = "B-)", uri = "icon_cool.gif", tooltip = "Cool"),
    JS(symbol = "$-|", uri = "icon_rolleyes.gif", tooltip = "Roll Eyes"),
    JS(symbol = "8-|", uri = "icon_eek.gif", tooltip = "Eek"),
    JS(symbol = ":-/", uri = "icon_confused.gif", tooltip = "Confused"),
    JS(symbol = "|-|", uri = "icon_redface.gif", tooltip = "Blush"),
    JS(symbol = ":-(", uri = "icon_sad.gif", tooltip = "Sad"),
    JS(symbol = ":'-(", uri = "icon_cry.gif", tooltip = "Cry"),
    JS(symbol = ">:-(", uri = "icon_evil.gif", tooltip = "Enraged"),
    JS(symbol = ":-|", uri = "icon_neutral.gif", tooltip = "Neutral"),
    JS(symbol = ":-O", uri = "icon_surprised.gif", tooltip = "Surprised"),
    JS(symbol = "(i)", uri = "icon_idea.gif", tooltip = "Idea"),
    JS(symbol = "(!)", uri = "icon_exclaim.gif", tooltip = "Exclamation"),
    JS(symbol = "(?)", uri = "icon_question.gif", tooltip = "Question"),
    JS(symbol = "=>", uri = "icon_arrow.gif", tooltip = "Arrow"))

}
