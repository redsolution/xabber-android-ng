package com.xabber.presentation.application.activity

object ApplicationUnreadQueryBuilder {
    fun build(accountIds: Collection<String>): String? {
        val normalizedIds = accountIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

        if (normalizedIds.isEmpty()) return null

        val ownerClause = normalizedIds.joinToString(",") { "'$it'" }
        return "owner IN {$ownerClause} && isArchived = false && muteExpired <= 0 && unread > 0"
    }
}
