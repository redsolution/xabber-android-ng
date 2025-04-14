package com.xabber.xmpp.abstract

class QueryDeliveryQueueItem(val queryId: String, var isDelivered: Boolean = false) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueryDeliveryQueueItem) return false
        return queryId == other.queryId
    }

    override fun hashCode(): Int = queryId.hashCode()
}