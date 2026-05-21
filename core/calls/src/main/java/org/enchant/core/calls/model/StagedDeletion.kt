package org.enchant.core.calls.model

data class StagedDeletion(
    val count: Int,
    val callIds: List<String>
)