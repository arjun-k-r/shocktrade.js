package com.shocktrade.javascript

import com.github.ldaniels528.scalascript._
import com.github.ldaniels528.scalascript.core.Timeout
import com.github.ldaniels528.scalascript.extensions.Toaster
import com.shocktrade.javascript.AppEvents._
import com.shocktrade.javascript.ScalaJsHelper._
import com.shocktrade.javascript.dashboard.ContestService
import com.shocktrade.javascript.models._
import com.shocktrade.javascript.profile.ProfileService
import org.scalajs.dom.console

import scala.concurrent.duration._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => JS}
import scala.util.{Failure, Success}

/**
 * My Session Facade
 * @author lawrence.daniels@gmail.com
 */
class MySession($rootScope: Scope, $timeout: Timeout, toaster: Toaster,
                @injected("ContestService") contestService: ContestService,
                @injected("ProfileService") profileService: ProfileService)
  extends Service {

  val notifications = emptyArray[String]
  var facebookID: Option[String] = None
  var fbFriends = emptyArray[FacebookFriend]
  var fbProfile: Option[FacebookProfile] = None
  var contest: Option[Contest] = None
  var userProfile: UserProfile = createSpectatorProfile()

  /////////////////////////////////////////////////////////////////////
  //          Authentication & Authorization Functions
  /////////////////////////////////////////////////////////////////////

  def getUserProfile = userProfile

  def setUserProfile(profile: UserProfile, fbProfile: FacebookProfile) {
    console.log(s"facebookID = $facebookID, fbProfile = ${angular.toJson(fbProfile)}")

    this.fbProfile = Some(fbProfile)
    this.facebookID = Some(fbProfile.id)

    console.log(s"profile = ${angular.toJson(profile)}")
    this.userProfile = profile

    // broadcast the user profile change event
    $rootScope.$broadcast(UserProfileChanged, profile)
    ()
  }

  /**
   * Returns the user ID for the current user's ID
   * @return {*}
   */
  def getUserID = userProfile.OID_?.orNull

  /**
   * Returns the user ID for the current user's name
   * @return {*}
   */
  def getUserName = userProfile.name

  /**
   * Indicates whether the given user is an administrator
   * @return {boolean}
   */
  def isAdmin = userProfile.admin.getOrElse(false)

  /**
   * Indicates whether the user is logged in
   * @return {boolean}
   */
  def isAuthenticated = userProfile.OID_?.isDefined

  def getFacebookID = facebookID.orNull

  def setFacebookID(fbId: String) = facebookID = Option(fbId)

  def getFacebookProfile = fbProfile

  def setFacebookProfile(profile: FacebookProfile) = fbProfile = Option(profile)

  def isFbAuthenticated = fbProfile.isDefined

  /**
   * Logout function
   */
  def logout() = {
    facebookID = None
    fbFriends = js.Array[FacebookFriend]()
    fbProfile = None
    userProfile = createSpectatorProfile()
    contest = None
  }

  def refresh() = {
    facebookID.foreach { fbId =>
      profileService.getProfileByFacebookID(fbId) onComplete {
        case Success(profile) =>
          userProfile.netWorth = profile.netWorth
        case Failure(e) =>
          toaster.error("Error loading user profile");
      }
    }
  }

  /////////////////////////////////////////////////////////////////////
  //          NetWorth Functions
  /////////////////////////////////////////////////////////////////////

  def deduct(amount: Double) = {
    console.log(f"Deducting $amount%.2f from ${userProfile.netWorth}")
    userProfile.netWorth -= amount
  }

  def getNetWorth = userProfile.netWorth

  /////////////////////////////////////////////////////////////////////
  //          Symbols - Favorites, Recent, etc.
  /////////////////////////////////////////////////////////////////////

  def addFavoriteSymbol(symbol: String) = profileService.addFavoriteSymbol(getUserID, symbol)

  def getFavoriteSymbols = userProfile.favorites

  def isFavoriteSymbol(symbol: String) = getFavoriteSymbols.contains(symbol)

  def removeFavoriteSymbol(symbol: String) = profileService.removeFavoriteSymbol(getUserID, symbol)

  def addRecentSymbol(symbol: String) = profileService.addRecentSymbol(getUserID, symbol)

  def getRecentSymbols = userProfile.recentSymbols

  def isRecentSymbol(symbol: String) = getRecentSymbols.contains(symbol)

  def removeRecentSymbol(symbol: String) = profileService.removeRecentSymbol(getUserID, symbol)

  def getMostRecentSymbol = getRecentSymbols.lastOption getOrElse "AAPL"

  /////////////////////////////////////////////////////////////////////
  //          Contest Functions
  /////////////////////////////////////////////////////////////////////

  def contestIsEmpty = contest.isEmpty

  def getContest = contest getOrElse JS()

  def getContestID = contest.flatMap(_.OID_?).orNull

  def getContestName = contest.map(_.name).orNull

  def getContestStatus = contest.map(_.status).orNull

  def setContest(aContest: Contest) = {
    console.log(s"contest = ${angular.toJson(aContest)}")

    // if null or undefined, just reset the contest
    if (!isDefined(aContest)) resetContest()

    // if the contest contained an error, show it
    else if (isDefined(aContest.dynamic.error)) toaster.error(aContest.dynamic.error)

    // is it a delta?
    else if (aContest.dynamic.`type` === "delta") updateContestDelta(aContest)

    // otherwise, digest the full contest
    else contest = Option(aContest)
  }

  /**
   * Returns the combined total funds for both the cash and margin accounts
   */
  def getCompleteFundsAvailable = {
    (cashAccount_?.map(_.cashFunds) getOrElse 0.00d) + (marginAccount_?.map(_.cashFunds) getOrElse 0.00d)
  }

  def getFundsAvailable = cashAccount_?.map(_.cashFunds) getOrElse 0.00d

  def deductFundsAvailable(amount: Double) = {
    participant.foreach { player =>
      console.log(s"Deducting funds: $amount from ${player.cashAccount.cashFunds}")
      player.cashAccount.cashFunds -= amount
      // TODO rethink this
    }
    ()
  }

  def hasMarginAccount = marginAccount_?.isDefined

  def getCashAccount = cashAccount_? getOrElse makeNew

  def getMarginAccount = marginAccount_? getOrElse makeNew

  def getMessages = contest.flatMap(c => Option(c.messages)) getOrElse emptyArray[Message]

  def setMessages(messages: js.Array[Message]) = contest.foreach(_.messages = messages)

  def getMyAwards = userProfile.awards.asArray[String]

  def getOrders = participant.flatMap(p => Option(p.orders).map(_.asArray[js.Dynamic])) getOrElse emptyArray[js.Dynamic]

  def getClosedOrders = participant.flatMap(p => Option(p.closedOrders.asArray[js.Dynamic])) getOrElse emptyArray[js.Dynamic]

  def participantIsEmpty = participant.isEmpty

  def getParticipant = participant getOrElse makeNew[Participant]

  def getPerformance = participant.flatMap(p => Option(p.performance).map(_.asArray[js.Dynamic])) getOrElse emptyArray[js.Dynamic]

  def getPerks = participant.flatMap(p => Option(p.perks).map(_.asArray[String])) getOrElse emptyArray[String]

  def hasPerk: js.Function1[String, Boolean] = (perkCode: String) => getPerks.contains(perkCode)

  def getPositions = participant.flatMap(p => Option(p.positions).map(_.asArray[js.Dynamic])) getOrElse emptyArray[js.Dynamic]

  def resetContest: js.Function0[Unit] = () => contest = None

  ////////////////////////////////////////////////////////////
  //          Notification Methods
  ////////////////////////////////////////////////////////////

  def addNotification(message: String) = {
    if (notifications.push(message) > 20) notifications.shift()
    notifications
  }

  def getNotifications() = notifications

  def hasNotifications() = notifications.nonEmpty

  ////////////////////////////////////////////////////////////
  //          Participant Methods
  ////////////////////////////////////////////////////////////

  def findPlayerByID(contest: Contest, playerId: String) = {
    if (isDefined(contest) && isDefined(contest.participants))
      contest.participants.find(_.OID_?.contains(playerId))
    else
      None
  }

  def findPlayerByName(contest: Contest, playerName: String) = {
    required("contest", contest)
    required("playerName", playerName)
    contest.participants.find(_.name == playerName) getOrElse makeNew
  }

  ////////////////////////////////////////////////////////////
  //          Private Methods
  ////////////////////////////////////////////////////////////

  /**
   * Creates a default 'Spectator' user profile
   * @return {{name: string, country: string, level: number, lastSymbol: string, friends: Array, filters: *[]}}
   */
  private def createSpectatorProfile() = {
    notifications.remove(0, notifications.length)
    facebookID = None
    fbFriends = emptyArray[FacebookFriend]
    fbProfile = None
    contest = None

    UserProfile(
      name = "Spectator",
      country = "us",
      lastSymbol = "AAPL"
    )
  }

  private[javascript] def cashAccount_? = participant.map(_.cashAccount)

  private[javascript] def marginAccount_? = participant.flatMap(_.marginAccount.toOption)

  private[javascript] def participant: Option[Participant] = {
    for {
      userId <- userProfile.OID_?
      c <- contest
      participants = if (isDefined(c.participants)) c.participants else emptyArray
      me = participants.find(_.OID_?.contains(userId)) getOrElse makeNew[Participant]
    } yield me
  }

  private def lookupParticipant(contest: Contest, playerId: String) = {
    contest.participants.find(_.OID_?.contains(playerId))
  }

  ////////////////////////////////////////////////////////////
  //          Event Functions
  ////////////////////////////////////////////////////////////

  private def info(contest: Contest, message: String) = console.log(s"${contest.name}: $message")

  private def updateContestDelta(updatedContest: Contest) {
    // update the messages (if present)
    if (isDefined(updatedContest.messages)) {
      info(updatedContest, s"Updating messages")
      contest.foreach(_.messages = updatedContest.messages)
    }

    // lookup our local participant
    for {
      myParticipant <- participant
      myParticipantId <- myParticipant.OID_?
      foreignPlayer <- lookupParticipant(updatedContest, myParticipantId)
    } {
      // update each object (if present)
      if (isDefined(foreignPlayer.cashAccount)) updateContest_CashAccount(updatedContest, myParticipant, foreignPlayer)
      if (isDefined(foreignPlayer.marginAccount)) updateContest_MarginAccount(updatedContest, myParticipant, foreignPlayer)
      if (isDefined(foreignPlayer.orders)) updateContest_ActiveOrders(updatedContest, myParticipant, foreignPlayer)
      if (isDefined(foreignPlayer.closedOrders)) updateContest_ClosedOrders(updatedContest, myParticipant, foreignPlayer)
      if (isDefined(foreignPlayer.performance)) updateContest_Performance(updatedContest, myParticipant, foreignPlayer)
      if (isDefined(foreignPlayer.perks)) updateContest_Perks(updatedContest, myParticipant, foreignPlayer)
      if (isDefined(foreignPlayer.positions)) updateContest_Positions(updatedContest, myParticipant, foreignPlayer)
    }
  }

  private def updateContest_CashAccount(contest: Contest, myParticipant: Participant, foreignPlayer: Participant) {
    info(contest, s"Updating cash account for ${foreignPlayer.name}")
    myParticipant.cashAccount = foreignPlayer.cashAccount
  }

  private def updateContest_MarginAccount(contest: Contest, myParticipant: Participant, foreignPlayer: Participant) {
    info(contest, s"Updating margin account for ${foreignPlayer.name}")
    myParticipant.marginAccount = foreignPlayer.marginAccount
  }

  private def updateContest_ActiveOrders(contest: Contest, myParticipant: Participant, foreignPlayer: Participant) {
    info(contest, s"Updating active orders for ${foreignPlayer.name}")
    myParticipant.orders = foreignPlayer.orders
  }

  private def updateContest_ClosedOrders(contest: Contest, myParticipant: Participant, foreignPlayer: Participant) {
    info(contest, s"Updating closed orders for ${foreignPlayer.name}")
    myParticipant.closedOrders = foreignPlayer.closedOrders
  }

  private def updateContest_Performance(contest: Contest, myParticipant: Participant, foreignPlayer: Participant) {
    info(contest, s"Updating performance for ${foreignPlayer.name}")
    myParticipant.performance = foreignPlayer.performance
  }

  private def updateContest_Perks(contest: Contest, myParticipant: Participant, foreignPlayer: Participant) {
    info(contest, s"Updating perks for ${foreignPlayer.name}")
    myParticipant.perks = foreignPlayer.perks
  }

  private def updateContest_Positions(contest: Contest, myParticipant: Participant, foreignPlayer: Participant) {
    info(contest, s"Updating positions for ${foreignPlayer.name}")
    myParticipant.positions = foreignPlayer.positions
  }

  $rootScope.$on(ContestDeleted, { (event: js.Dynamic, deletedContest: Contest) =>
    info(deletedContest, "Contest deleted event received...")
    for {
      contestId <- contest.flatMap(_.OID_?)
      deletedId <- deletedContest.OID_?
    } if (contestId == deletedId) resetContest()
  })

  $rootScope.$on(ContestUpdated, { (event: js.Dynamic, updatedContest: Contest) =>
    info(updatedContest, "Contest updated event received...")
    if (contest.isEmpty) setContest(updatedContest)
    else {
      for {
        contestId <- contest.flatMap(_.OID_?)
        updatedId <- updatedContest.OID_?
      } if (contestId == updatedId) setContest(updatedContest)
    }
  })

  $rootScope.$on(MessagesUpdated, { (event: js.Dynamic, updatedContest: Contest) =>
    info(updatedContest, "Messages updated event received...")
    updateContestDelta(updatedContest)
  })

  $rootScope.$on(OrderUpdated, { (event: js.Dynamic, updatedContest: Contest) =>
    info(updatedContest, "Orders updated event received...")
    updateContestDelta(updatedContest)
  })

  $rootScope.$on(PerksUpdated, { (event: js.Dynamic, updatedContest: Contest) =>
    info(updatedContest, "Perks updated event received...")
    updateContestDelta(updatedContest)
  })

  $rootScope.$on(ParticipantUpdated, { (event: js.Dynamic, updatedContest: Contest) =>
    info(updatedContest, "Participant updated event received...")
    updateContestDelta(updatedContest)
  })

  $rootScope.$on(UserProfileUpdated, { (event: js.Dynamic, profile: UserProfile) =>
    console.log(s"User Profile for ${profile.name} updated")
    for {
      userId <- userProfile.OID_?
      otherId <- profile.OID_?
    } if (userId == otherId) {
      userProfile.netWorth = profile.netWorth
      toaster.success("Your Wallet", s"<ul><li>Your wallet now has $$${profile.netWorth}</li></ul>", 5.seconds, "trustedHtml")
    }
  })

}
