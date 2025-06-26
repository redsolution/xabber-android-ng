package com.xabber.xmpp.abstract

import android.util.Log
import java.util.Collections


abstract class AbstractXMPPManager(val owner: String) {
    protected val queryIds = mutableSetOf<String>()
    abstract fun namespaces(): List<String>
    abstract fun getPrimaryNamespace(): String
}