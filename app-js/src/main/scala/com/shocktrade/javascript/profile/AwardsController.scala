package com.shocktrade.javascript.profile

import com.shocktrade.javascript.{ScalaJsHelper, MySession}
import ScalaJsHelper._
import com.ldaniels528.scalascript._
import com.ldaniels528.scalascript.core.Http
import com.shocktrade.core.Award
import com.shocktrade.javascript.AppEvents._
import com.shocktrade.javascript.MySession
import com.shocktrade.javascript.profile.AwardsController._
import org.scalajs.dom.console

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => JS}

/**
 * Awards Controller
 * @author lawrence.daniels@gmail.com
 */
class AwardsController($scope: js.Dynamic, $http: Http, @injected("MySession") mySession: MySession) extends Controller {
  private val scope = $scope.asInstanceOf[Scope]

  ///////////////////////////////////////////////////////////////////////////
  //          Public Functions
  ///////////////////////////////////////////////////////////////////////////

  $scope.getAwards = () => AvailableAwards

  $scope.getMyAwards = () => getMyAwards

  $scope.getAwardImage = (code: String) => AwardIconsByCode.get(code).orNull

  $scope.setupAwards = () => setupAwards()

  /////////////////////////////////////////////////////////////////////////////
  //			Private Functions and Data
  /////////////////////////////////////////////////////////////////////////////

  private def getMyAwards: js.Array[js.Dynamic] = {
    mySession.getMyAwards() map (code => AwardsByCode.get(code).orNull)
  }

  private def setupAwards() {
    console.log("Setting up awards....")
    AvailableAwards foreach { award =>
      award.owned = mySession.getMyAwards().contains(award.code.as[String])
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  //          Event Listeners
  ///////////////////////////////////////////////////////////////////////////

  /**
   * Listen for changes to the player's profile
   */
  scope.$on(UserProfileChanged, (profile: js.Dynamic) => setupAwards())

  /**
   * Listen for changes to the player's awards
   */
  scope.$on(AwardsUpdated, (profile: js.Dynamic) => setupAwards())

}

/**
 * Awards Controller Singleton
 * @author lawrence.daniels@gmail.com
 */
object AwardsController {

  // define all available awards
  private val AvailableAwards = js.Array[js.Dynamic](
    Award.availableAwards
      .map(a => JS(name = a.name, code = a.code.toString, icon = a.icon, description = a.description)): _*)
    .sortBy(_.owned.isTrue)
    .reverse

  private val AwardsByCode = js.Dictionary[js.Dynamic](
    AvailableAwards map { award => (award.code.as[String], award) }: _*
  )

  private val AwardIconsByCode = js.Dictionary[String](
    AvailableAwards map { award => (award.code.as[String], award.icon.as[String]) }: _*
  )

}