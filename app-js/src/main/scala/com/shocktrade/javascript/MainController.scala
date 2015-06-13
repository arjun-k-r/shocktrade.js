package com.shocktrade.javascript

import biz.enef.angulate.core.{HttpService, Location, Timeout}
import biz.enef.angulate.{ScopeController, named}
import com.ldaniels528.angularjs.Toaster
import com.shocktrade.javascript.MainController._
import com.shocktrade.javascript.ScalaJsHelper._
import com.shocktrade.javascript.dashboard.ContestService
import com.shocktrade.javascript.profile.{FacebookService, ProfileService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g, literal => JS}
import scala.scalajs.js.JSON
import scala.util.{Failure, Success}

/**
 * Main Controller
 * @author lawrence.daniels@gmail.com
 */
class MainController($scope: js.Dynamic, $http: HttpService, $location: Location, $timeout: Timeout, toaster: Toaster,
                     @named("ContestService") contestService: ContestService,
                     @named("Facebook") facebook: FacebookService,
                     @named("FavoriteSymbols") favoriteSymbols: js.Dynamic,
                     @named("HeldSecurities") heldSecurities: js.Dynamic,
                     @named("MySession") mySession: MySession,
                     @named("ProfileService") profileService: ProfileService,
                     @named("RecentSymbols") recentSymbols: js.Dynamic,
                     @named("SignUpDialog") signUpDialog: js.Dynamic)
  extends ScopeController {

  private var isLoading = false
  private val onlinePlayers = js.Dictionary[js.Dynamic]()

  ///////////////////////////////////////////////////////////////////////////
  //          Loading Functions
  ///////////////////////////////////////////////////////////////////////////

  $scope.isLoading = () => isLoading

  $scope.startLoading = (timeout: js.UndefOr[Int]) => startLoading(timeout)

  $scope.stopLoading = () => stopLoading()

  ///////////////////////////////////////////////////////////////////////////
  //          Public Functions
  ///////////////////////////////////////////////////////////////////////////

  $scope.appTabs = appTabs

  $scope.levels = levels

  $scope.mainInit = (uuid: String) => g.console.log(s"Session UUID is $uuid")

  $scope.changeAppTab = (tabIndex: js.UndefOr[Int]) => changeAppTab(tabIndex)

  $scope.getAssetCode = (q: js.Dynamic) => getAssetCode(q)

  $scope.getAssetIcon = (q: js.Dynamic) => getAssetIcon(q)

  $scope.getDate = (date: js.Dynamic) => if (isDefined(date) && isDefined(date.$date)) date.$date else date

  $scope.getExchangeClass = (exchange: js.UndefOr[String]) => s"${normalizeExchange(exchange)} bold"

  $scope.getHtmlQuote = (q: js.Dynamic) => if (!isDefined(q)) "" else s"<i class='${$scope.getAssetIcon(q)}'></i> ${q.symbol} - ${q.name}"

  $scope.isOnline = (player: js.Dynamic) => isOnline(player)

  $scope.getPreferenceIcon = (q: js.Dynamic) => getPreferenceIcon(q)

  $scope.getTabIndex = () => determineTableIndex

  $scope.isVisible = (tab: js.Dynamic) => !isLoading && ((!isTrue(tab.contestRequired) || mySession.contest.isDefined) && (!isTrue(tab.authenticationRequired) || mySession.isAuthenticated()))

  $scope.login = (event: js.Dynamic) => login(event)

  $scope.logout = (event: js.Dynamic) => logout(event)

  $scope.normalizeExchange = (market: js.UndefOr[String]) => normalizeExchange(market)

  $scope.postLoginUpdates = (facebookID: String, userInitiated: Boolean) => postLoginUpdates(facebookID, userInitiated)

  //////////////////////////////////////////////////////////////////////
  //              Private Functions
  //////////////////////////////////////////////////////////////////////

  private def changeAppTab(index: js.UndefOr[Int]) = index exists { tabIndex =>
    startLoading(DEFAULT_TIMEOUT)
    val tab = appTabs(tabIndex)
    g.console.log(s"Changing location to ${tab.url}")
    $location.url(tab.url.as[String])
    stopLoading()
    true
  }

  private def determineTableIndex: Int = $location.path() match {
    case path if path.contains("/search") => 0
    case path if path.contains("/dashboard") => 1
    case path if path.contains("/discover") => 2
    case path if path.contains("/explore") => 3
    case path if path.contains("/favorites") => 4
    case path if path.contains("/research") => 5
    case path if path.contains("/connect") => 6
    case path if path.contains("/awards") => 7
    case path if path.contains("/statistics") => 8
    case path => 0
  }

  private def isOnline(player: js.Dynamic): Boolean = {
    if (!isDefined(player.facebookID)) false
    else {
      val playerID = player.facebookID.as[String]
      if (!onlinePlayers.contains(playerID)) {
        onlinePlayers(playerID) = JS(connected = false)
        $http.get[js.Dynamic](s"/api/online/$playerID") onComplete {
          case Success(newState) =>
            onlinePlayers(playerID) = newState
          case Failure(e) =>
            g.console.error(s"Error retrieving online state for user $playerID")
        }
      }
      val state = onlinePlayers(playerID)
      isDefined(state) && isDefined(state.connected) && state.connected.as[Boolean]
    }
  }

  private def getPreferenceIcon(q: js.Dynamic): String = {
    /*
    // fail-safe
    if (!isDefined(q) || !isDefined(q.symbol)) ""

    // check for favorite and held securities
    val symbol = q.symbol
    if (heldSecurities.isHeld(symbol)) "fa fa-star"
    else if (favoriteSymbols.isFavorite(symbol)) "fa fa-heart"
    else ""*/
    ""
  }

  private def loadFacebookFriends() {
    facebook.getTaggableFriends({ response: js.Dynamic =>
      val friends = response.data.asArray[js.Dynamic]
      g.console.log(s"${friends.length} friend(s) loaded")
      mySession.fbFriends = friends
    })
  }

  private def login(event: js.Dynamic) {
    if (isDefined(event)) event.preventDefault()
    facebook.login() onComplete {
      case Success(response) =>
        val fbResponse = response.asInstanceOf[js.Dynamic]
        g.console.log(s"fbResponse = ${JSON.stringify(fbResponse)}")
        val facebookID = fbResponse.authResponse.userID.as[String]
        $scope.postLoginUpdates(facebookID, true)
      case Failure(e) =>
        g.console.error(s"main:login error")
        e.printStackTrace()
    }
  }

  private def logout(event: js.Dynamic) {
    if (isDefined(event)) event.preventDefault()
    facebook.logout()
    mySession.logout()
  }

  private def postLoginUpdates(facebookID: String, userInitiated: Boolean) = {
    g.console.log(s"facebookID = $facebookID, userInitiated = $userInitiated")

    // capture the user ID
    mySession.facebookID = Option(facebookID)

    // load the user"s Facebook profile
    facebook.getUserProfile((response: js.Dynamic) => {
      g.console.log(s"fbProfile = ${JSON.stringify(response)}")
      mySession.fbProfile = Option(response)
    })

    // load the user"s ShockTrade profile
    profileService.getProfileByFacebookID(facebookID) onComplete {
      case Success(profile) =>
        if (!isDefined(profile.error)) {
          g.console.log("ShockTrade user profile loaded...")
          mySession.userProfile = profile
          mySession.nonMember = false
          loadFacebookFriends()
        }
        else {
          g.console.log("Non-member identified... Launching Sign-up dialog...")
          signUpPopup(facebookID, mySession.fbProfile)
        }

      case Failure(e) =>
        toaster.pop("error", s"ShockTrade Profile retrieval error - ${e.getMessage}", null)
        signUpPopup(facebookID, mySession.fbProfile)
    }
  }

  private def signUpPopup(facebookID: String, fbProfile_? : Option[js.Dynamic]) {
    fbProfile_? foreach { fbProfile =>
      signUpDialog.popup(facebookID, fbProfile)
    }
  }

  private def startLoading(timeout: js.UndefOr[Int]) = {
    isLoading = true
    val _timeout = timeout getOrElse DEFAULT_TIMEOUT

    // set loading timeout
    $timeout(() => {
      g.console.log(s"Disabling the loading animation due to time-out (${_timeout} msec)...")
      isLoading = false
    }, _timeout)
  }

  private def stopLoading() = $timeout(() => isLoading = false, 500)

  //////////////////////////////////////////////////////////////////////
  //              Event Listeners
  //////////////////////////////////////////////////////////////////////

  $scope.$on("user_status_changed", (event: js.Dynamic, newState: js.Dynamic) => g.console.log(s"user_status_changed: newState = ${JSON.stringify(newState)}"))

  // watch for changes to the player"s profile
  $scope.$watch(mySession.userProfile, { () =>
    // load the favorite and recent quotes
    favoriteSymbols.setSymbols(mySession.userProfile.favorites)
    recentSymbols.setSymbols(mySession.userProfile.recentSymbols)

    // load the held securities
    mySession.userProfile.OID_? foreach { userID => heldSecurities.init(userID) }
  })

}

/**
 * Main Controller Singleton
 * @author lawrence.daniels@gmail.com
 */
object MainController {
  private val DEFAULT_TIMEOUT = 5000

  private def getAssetCode(q: js.Dynamic): String = {
    if (!isDefined(q) || !isDefined(q.assetType)) ""
    else q.assetType.as[String] match {
      case "Crypto-Currency" => "&#xf15a" // fa-bitcoin
      case "Currency" => "&#xf155" // fa-dollar
      case "ETF" => "&#xf18d" // fa-stack-exchange
      case _ => "&#xf0ac" // fa-globe
    }
  }

  private def getAssetIcon(q: js.Dynamic): String = {
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

  private val appTabs = js.Array(
    JS(name = "Search", icon_class = "fa-search", tool_tip = "Search for games", url = "/search"),
    JS(name = "Dashboard", icon_class = "fa-gamepad", tool_tip = "Main game dashboard", url = "/dashboard", contestRequired = true),
    JS(name = "Discover", icon_class = "fa-newspaper-o", tool_tip = "Stock News and Quotes", url = "/discover"),
    JS(name = "Explore", icon_class = "fa-trello", tool_tip = "Explore Sectors and Industries", url = "/explore"),
    JS(name = "Favorites", icon_class = "fa-heart", tool_tip = "My Favorite Securities", url = "/symbols/favorites", authenticationRequired = true),
    JS(name = "Research", icon_class = "fa-database", tool_tip = "Stock Research", url = "/research"),
    JS(name = "Connect", icon_class = "fa-facebook-square", tool_tip = "Connect & Share", url = "/connect", authenticationRequired = true),
    JS(name = "My Awards", icon_class = "fa-trophy", tool_tip = "My Awards", url = "/profile/awards", authenticationRequired = true),
    JS(name = "My Statistics", icon_class = "fa-bar-chart", tool_tip = "My Statistics", url = "/profile/statistics", authenticationRequired = true))

  private val levels = js.Array(
    JS(number = 1, nextLevelXP = 1000, description = "Private"),
    JS(number = 2, nextLevelXP = 2000, description = "Private 1st Class"),
    JS(number = 3, nextLevelXP = 4000, description = "Corporal"),
    JS(number = 4, nextLevelXP = 8000, description = "First Corporal"),
    JS(number = 5, nextLevelXP = 16000, description = "Sergeant"),
    JS(number = 6, nextLevelXP = 32000, description = "Staff Sergeant"),
    JS(number = 7, nextLevelXP = 64000, description = "Gunnery Sergeant"),
    JS(number = 8, nextLevelXP = 1280000, description = "Master Sergeant"),
    JS(number = 9, nextLevelXP = 256000, description = "First Sergeant"),
    JS(number = 10, nextLevelXP = 1024000, description = "Sergeant Major"),
    JS(number = 11, nextLevelXP = 2048000, description = "Warrant Officer 3rd Class"),
    JS(number = 12, nextLevelXP = 4096000, description = "Warrant Officer 2nd Class"),
    JS(number = 13, nextLevelXP = 4096000, description = "Warrant Officer 1st Class"),
    JS(number = 14, nextLevelXP = 8192000, description = "Chief Warrant Officer"),
    JS(number = 15, nextLevelXP = 8192000, description = "Master Chief Warrant Officer"),
    JS(number = 16, nextLevelXP = 16384000, description = "Lieutenant"),
    JS(number = 17, nextLevelXP = 32768000, description = "First Lieutenant"),
    JS(number = 18, nextLevelXP = 65536000, description = "Captain"),
    JS(number = 19, nextLevelXP = 131072000, description = "Major"),
    JS(number = 20, nextLevelXP = 262144000, description = "Lieutenant Colonel"),
    JS(number = 21, nextLevelXP = 524288000, description = "Colonel"),
    JS(number = 22, nextLevelXP = 524288000, description = "Brigadier General"),
    JS(number = 23, nextLevelXP = 524288000, description = "Major General"),
    JS(number = 24, nextLevelXP = 524288000, description = "Lieutenant General"),
    JS(number = 25, nextLevelXP = 524288000, description = "General"))

}