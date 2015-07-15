package com.shocktrade.javascript.dialogs

import com.github.ldaniels528.scalascript.core.Http
import com.github.ldaniels528.scalascript.extensions.{Modal, ModalOptions}
import com.github.ldaniels528.scalascript.{Service, injected}
import com.shocktrade.javascript.MySession
import com.shocktrade.javascript.dialogs.InvitePlayerDialogController.InvitePlayerDialogResult
import com.shocktrade.javascript.models.Participant

import scala.concurrent.Future
import scala.scalajs.js

/**
 * Invite Player Dialog Service
 * @author lawrence.daniels@gmail.com
 */
class InvitePlayerDialogService($http: Http, $modal: Modal, @injected("MySession") mySession: MySession) extends Service {

  /**
   * Invite a player via pop-up dialog
   */
  def popup(participant: Participant): Future[InvitePlayerDialogResult] = {
    /*
    function(selectedFriends) {
      if (selectedFriends.length) {
        $log.info("selectedFriends = " + angular.toJson(selectedFriends));
        Facebook.send("http://www.nytimes.com/interactive/2015/04/15/travel/europe-favorite-streets.html");
      }

    }, function() {
      $log.info('Modal dismissed at: ' +new Date());
    }
     */

    val modalInstance = $modal.open[InvitePlayerDialogResult](ModalOptions(
      templateUrl = "invite_player_dialog.htm",
      controllerClass = classOf[InvitePlayerDialogController],
      resolve = js.Dictionary[js.Any]("myFriends" -> (() => mySession.fbFriends))
    ))
    modalInstance.result
  }

}
