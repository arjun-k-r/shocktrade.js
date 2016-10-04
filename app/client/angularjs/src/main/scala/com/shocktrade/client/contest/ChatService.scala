package com.shocktrade.client.contest

import com.shocktrade.common.models.contest.ChatMessage
import org.scalajs.angularjs.Service
import org.scalajs.angularjs.http.Http

import scala.scalajs.js

/**
  * Chat Service
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
class ChatService($http: Http) extends Service {

  def getMessages(contestId: String) = {
    $http.get[js.Array[ChatMessage]](s"/api/contest/$contestId/chat")
  }

  def sendChatMessage(contestId: String, message: ChatMessage) = {
    $http.post[js.Array[ChatMessage]](s"/api/contest/$contestId/chat", message)
  }

}
