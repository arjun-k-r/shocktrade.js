package com.shocktrade.javascript.dialogs

import biz.enef.angulate.core.HttpService
import biz.enef.angulate.{Service, named}
import com.greencatsoft.angularjs.core.Promise
import com.greencatsoft.angularjs.extensions.{ModalOptions, ModalService}
import com.shocktrade.javascript.MySession

import scala.scalajs.js

/**
 * Invite Player Dialog Service
 * @author lawrence.daniels@gmail.com
 */
class InvitePlayerDialogService($http: HttpService, $modal: ModalService, @named("MySession") mySession: MySession)
  extends Service {

  /**
   * Invite a player via pop-up dialog
   */
  def popup: js.Function1[js.Dynamic, Promise] = (contest: js.Dynamic) => {
    // create an instance of the dialog
    val options = ModalOptions()
    options.templateUrl = "invite_player_dialog.htm"
    options.controller = classOf[InvitePlayerDialogController].getSimpleName
    options.resolve = js.Dictionary[js.Any]("myFriends" -> (() => mySession.fbFriends))

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

    val modalInstance = $modal.open(options)
    modalInstance.result
  }

}