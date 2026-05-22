package org.enchant.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

object MainNavigationRepository {

    fun getNumberOfUnreadMessages(): Flow<Long> = flowOf(0L)

    fun getNumberOfUnseenCalls(): Flow<Long> = flowOf(0L)

    fun getNumberOfUnseenStories(): Flow<Long> = flowOf(0L)

    fun getHasFailedOutgoingStories(): Flow<Boolean> = flowOf(false)
}