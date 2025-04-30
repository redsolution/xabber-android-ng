package com.xabber.xmpp.abstract

import android.util.Log
import java.util.Collections


abstract class AbstractXMPPManager {
    var owner = String
    var queryIds: MutableList<String> = Collections.synchronizedList(mutableListOf())


    init {
            this.owner = owner
            this.queryIds = Collections.synchronizedList(mutableListOf())
    }

//    fun read(iq: XMPPIQ): Boolean {
//        val elementId = iq.elementID ?: return false
//        synchronized(queryIds) {
//            if (!queryIds.contains(elementId)) return false
//            queryIds.remove(elementId)
//            return true
//        }
//    }
}