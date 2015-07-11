package com.shocktrade.javascript

import com.ldaniels528.scalascript._
import com.ldaniels528.scalascript.core.TimerConversions._
import com.ldaniels528.scalascript.core.{Http, Location, Timeout}
import com.ldaniels528.scalascript.extensions.Toaster
import com.shocktrade.javascript.AppEvents._
import com.shocktrade.javascript.MainController._
import com.shocktrade.javascript.ScalaJsHelper._
import com.shocktrade.javascript.dashboard.ContestService
import com.shocktrade.javascript.dialogs.SignUpDialogService
import com.shocktrade.javascript.models.{FacebookFriend, FacebookProfile, OnlinePlayerState}
import com.shocktrade.javascript.profile.ProfileService
import com.shocktrade.javascript.social.FacebookService
import org.scalajs.dom.console

import scala.concurrent.duration._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.JSON
import scala.util.{Failure, Success}

/**
 * Main Controller
 * @author lawrence.daniels@gmail.com
 */
class MainController($scope: MainScope, $http: Http, $location: Location, $timeout: Timeout, toaster: Toaster,
                     @injected("ContestService") contestService: ContestService,
                     @injected("Facebook") facebook: FacebookService,
                     @injected("MySession") mySession: MySession,
                     @injected("ProfileService") profileService: ProfileService,
                     @injected("SignUpDialog") signUpDialog: SignUpDialogService)
  extends Controller with GlobalLoading {

  private var loadingIndex = 0
  private var nonMember = true
  private val onlinePlayers = js.Dictionary[OnlinePlayerState]()

  $scope.appTabs = MainTab.Tabs
  $scope.levels = GameLevel.Levels

  ///////////////////////////////////////////////////////////////////////////
  //          Loading Functions
  ///////////////////////////////////////////////////////////////////////////

  @scoped def isLoading = loadingIndex > 0

  @scoped
  def startLoading(timeout: js.UndefOr[Int]): CancellablePromise = {
    loadingIndex += 1
    val _timeout = timeout getOrElse DEFAULT_TIMEOUT

    // set loading timeout
    $timeout(() => {
      console.log(s"Disabling the loading animation due to time-out (${_timeout} msec)...")
      loadingIndex = 0
    }, _timeout)
  }

  @scoped
  def stopLoading(promise: js.UndefOr[CancellablePromise]) = {
    $timeout.cancel(promise)
    $timeout(() => if (loadingIndex > 0) loadingIndex -= 1, 500.millis)
  }

  ///////////////////////////////////////////////////////////////////////////
  //          Public Functions
  ///////////////////////////////////////////////////////////////////////////

  @scoped def mainInit = (uuid: String) => console.log(s"Session UUID is $uuid")

  @scoped def getAssetCode(q: js.Dynamic) = MainController.getAssetCode(q)

  @scoped def getAssetIcon(q: js.Dynamic) = MainController.getAssetIcon(q)

  @scoped def getDate(date: js.Dynamic) = if (isDefined(date) && isDefined(date.$date)) date.$date else date

  @scoped def getExchangeClass(exchange: js.UndefOr[String]) = s"${normalizeExchange(exchange)} bold"

  @scoped def getHtmlQuote(q: js.Dynamic) = if (!isDefined(q)) "" else s"<i class='${MainController.getAssetIcon(q)}'></i> ${q.symbol} - ${q.name}"

  @scoped def getTabIndex = determineTableIndex

  @scoped def isVisible(tab: js.Dynamic) = (loadingIndex == 0) && ((!isTrue(tab.contestRequired) || mySession.contest.isDefined) && (!isTrue(tab.authenticationRequired) || mySession.isAuthenticated))

  @scoped def normalizeExchange(market: js.UndefOr[String]) = MainController.normalizeExchange(market)

  @scoped def postLoginUpdates(facebookID: String, userInitiated: Boolean) = doPostLoginUpdates(facebookID, userInitiated)

  //////////////////////////////////////////////////////////////////////
  //              MySession Functions
  //////////////////////////////////////////////////////////////////////

  @scoped def contestIsEmpty = mySession.contestIsEmpty

  @scoped def getContestID = mySession.getContestID

  @scoped def getContestName = mySession.getContestName

  @scoped def getContestStatus = mySession.getContestStatus

  @scoped def getFacebookID = mySession.getFacebookID

  @scoped def getFacebookProfile = mySession.getFacebookProfile

  @scoped def getFacebookFriends = mySession.fbFriends

  @scoped def getFundsAvailable = mySession.getFundsAvailable

  @scoped def getNetWorth = mySession.getNetWorth

  @scoped def getUserID = mySession.getUserID

  @scoped def getUserName = mySession.getUserName

  @scoped def getUserProfile = mySession.getUserProfile

  @scoped def hasNotifications = mySession.hasNotifications

  @scoped def hasPerk(perkCode: String) = mySession.hasPerk(perkCode)

  @scoped def isAdmin = mySession.isAdmin

  @scoped def isAuthenticated = mySession.isAuthenticated

  //////////////////////////////////////////////////////////////////////
  //              Private Functions
  //////////////////////////////////////////////////////////////////////

  @scoped def isOnline(player: js.Dynamic): Boolean = {
    player.OID_? exists { playerID =>
      if (!onlinePlayers.contains(playerID)) {
        onlinePlayers(playerID) = OnlinePlayerState(connected = false)
        profileService.getOnlineStatus(playerID) onComplete {
          case Success(newState) =>
            onlinePlayers(playerID) = newState
          case Failure(e) =>
            g.console.error(s"Error retrieving online state for user $playerID")
        }
      }
      onlinePlayers(playerID).toUndefOr[OnlinePlayerState].exists(_.connected)
    }
  }

  @scoped def getPreferenceIcon(q: js.Dynamic): String = {
    // fail-safe
    if (!isDefined(q) || !isDefined(q.symbol)) ""
    else {
      // check for favorite and held securities
      val symbol = q.symbol.as[String]
      //if (heldSecurities.isHeld(symbol)) "fa fa-star"
      if (mySession.isFavoriteSymbol(symbol)) "fa fa-heart"
      else ""
    }
  }

  private def loadFacebookFriends() {
    console.log("Loading Facebook friends...")
    facebook.getTaggableFriends({ (response: js.Dynamic) =>
      if (isDefined(response.data)) {
        val friends = response.data.asArray[FacebookFriend]
        console.log(s"${friends.length} friend(s) loaded")
        friends.foreach(mySession.fbFriends.push(_))
      }
      ()
    })
  }

  @scoped
  def login() {
    facebook.login() onComplete {
      case Success(response) =>
        nonMember = true

        // load the profile
        doPostLoginUpdates(facebook.facebookID, userInitiated = true)
      case Failure(e) =>
        g.console.error(s"main:login error")
        e.printStackTrace()
    }
  }

  @scoped
  def logout() {
    nonMember = false
    facebook.logout()
    mySession.logout()
  }

  private def doPostLoginUpdates(facebookID: String, userInitiated: Boolean) = {
    console.log(s"facebookID = $facebookID, userInitiated = $userInitiated")

    // capture the Facebook user ID
    mySession.setFacebookID(facebookID)

    // load the user"s Facebook profile
    console.log(s"Retrieving Facebook profile for FBID $facebookID...")
    facebook.getUserProfile() onComplete {
      case Success(response) =>
        mySession.setFacebookProfile(response.as[FacebookProfile])
        facebook.profile = response.as[FacebookProfile]
      case Failure(e) =>
        toaster.error(e.getMessage)
    }

    // load the user"s ShockTrade profile
    console.log(s"Retrieving ShockTrade profile for FBID $facebookID...")
    profileService.getProfileByFacebookID(facebookID) onComplete {
      case Success(profile) if isDefined(profile.dynamic.error) =>
        nonMember = true
        console.log("Non-member identified.")
        if (userInitiated) signUpPopup(facebookID, mySession.fbProfile)
      case Success(profile) =>
        nonMember = false
        console.log("ShockTrade user profile loaded...")
        mySession.setUserProfile(profile, facebook.profile)
        loadFacebookFriends()
      case Failure(e) =>
        toaster.error(s"ShockTrade Profile retrieval error - ${e.getMessage}")
    }
  }

  @scoped def signUp(): Unit = signUpPopup(facebook.facebookID, Option(facebook.profile))

  private def signUpPopup(facebookID: String, fbProfile_? : Option[FacebookProfile]) {
    fbProfile_? match {
      case Some(fbProfile) =>
        signUpDialog.popup(facebookID, fbProfile) onSuccess {
          case profile =>
            mySession.setUserProfile(profile, fbProfile)
        }
      case _ =>
        console.log(s"facebookID = $facebookID, fbProfile = ${angular.toJson(fbProfile_?.orNull)}")
        toaster.info("Sign-in to Facebook, then Sign-up here")
    }
  }

  //////////////////////////////////////////////////////////////////////
  //              Tab Functions
  //////////////////////////////////////////////////////////////////////

  @scoped def changeAppTab(index: js.UndefOr[Int]) = index foreach { tabIndex =>
    mySession.userProfile.OID_? match {
      case Some(userID) =>
        asyncLoading($scope)(profileService.setIsOnline(userID)) onComplete {
          case Success(outcome) =>
            if (isDefined(outcome.error)) {
              console.log(s"outcome = ${angular.toJson(outcome)}")
              toaster.error(outcome.error)
            }
            performTabSwitch(tabIndex)
          case Failure(e) =>
            toaster.error(e.getMessage)
        }
      case None =>
        performTabSwitch(tabIndex)
    }
  }

  private def performTabSwitch(tabIndex: Int): Unit = {
    val tab = MainTab.Tabs(tabIndex)
    console.log(s"Changing location for ${mySession.getUserName} to ${tab.url}")
    $location.url(tab.url)
  }

  private def determineTableIndex: Int = $location.path() match {
    case path if path.contains("/about") => 0
    case path if path.contains("/home") => 1
    case path if path.contains("/search") => 2
    case path if path.contains("/dashboard") => 3
    case path if path.contains("/discover") => 4
    case path if path.contains("/explore") => 5
    case path if path.contains("/research") => 6
    case path => 0
  }

  //////////////////////////////////////////////////////////////////////
  //              Event Listeners
  //////////////////////////////////////////////////////////////////////

  $scope.$on(UserStatusChanged, (event: js.Dynamic, newState: js.Dynamic) =>
    console.log(s"user_status_changed: newState = ${JSON.stringify(newState)}"))

}

/**
 * Main Scope
 * @author lawrence.daniels@gmail.com
 */
trait MainScope extends Scope {
  var appTabs: js.Array[MainTab] = js.native
  var levels: js.Array[GameLevel] = js.native

}

/**
 * Main Controller Singleton
 * @author lawrence.daniels@gmail.com
 */
object MainController {
  private val DEFAULT_TIMEOUT = 15000

  private[javascript] def getAssetCode(q: js.Dynamic): String = {
    if (!isDefined(q) || !isDefined(q.assetType)) ""
    else q.assetType.as[String] match {
      case "Crypto-Currency" => "&#xf15a" // fa-bitcoin
      case "Currency" => "&#xf155" // fa-dollar
      case "ETF" => "&#xf18d" // fa-stack-exchange
      case _ => "&#xf0ac" // fa-globe
    }
  }

  private[javascript] def getAssetIcon(q: js.Dynamic): String = {
    if (!isDefined(q) || !isDefined(q.assetType)) "fa fa-globe st_blue"
    else q.assetType.as[String] match {
      case "Crypto-Currency" => "fa fa-bitcoin st_blue"
      case "Currency" => "fa fa-dollar st_blue"
      case "ETF" => "fa fa-stack-exchange st_blue"
      case _ => "fa fa-globe st_blue"
    }
  }

  private[javascript] def normalizeExchange(market: js.UndefOr[String]): String = {
    market map { myMarket =>
      if (myMarket == null) ""
      else {
        myMarket.toUpperCase match {
          //case s if s.contains("ASE") => s
          //case s if s.contains("CCY") => s
          case s if s.contains("NAS") => "NASDAQ"
          case s if s.contains("NCM") => "NASDAQ"
          case s if s.contains("NGM") => "NASDAQ"
          case s if s.contains("NMS") => "NASDAQ"
          case s if s.contains("NYQ") => "NYSE"
          case s if s.contains("NYS") => "NYSE"
          case s if s.contains("OBB") => "OTCBB"
          case s if s.contains("OTC") => "OTCBB"
          case s if s.contains("OTHER") => "OTHER_OTC"
          //case s if s.contains("PCX") => s
          case s if s.contains("PNK") => "OTCBB"
          case s => s
        }
      }
    } getOrElse ""
  }

}
