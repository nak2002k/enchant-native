package org.enchant

interface MainNavigationChatDetailRouter {
    fun exitDetailLocation()
    fun goToChatDetail(location: MainNavigationDetailLocation.Chats)
}

interface MainNavigationCallDetailRouter {
    fun exitDetailLocation()
    fun goToCallDetail(location: MainNavigationDetailLocation.Calls)
}

interface MainNavigationRouter :
    MainNavigationChatDetailRouter,
    MainNavigationCallDetailRouter {

    fun goTo(location: MainNavigationListLocation)
    fun goTo(location: MainNavigationDetailLocation)
    fun setFocusedPane(role: String)

    override fun goToChatDetail(location: MainNavigationDetailLocation.Chats) = goTo(location)
    override fun goToCallDetail(location: MainNavigationDetailLocation.Calls) = goTo(location)
    override fun exitDetailLocation() = goTo(MainNavigationDetailLocation.Empty)
}

val MainNavigationDetailLocation.isContentRoot: Boolean
    get() = this is MainNavigationDetailLocation.Empty ||
            this is MainNavigationDetailLocation.Settings