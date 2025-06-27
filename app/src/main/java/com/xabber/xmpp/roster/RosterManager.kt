package com.xabber.xmpp.roster

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.Stream
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.util.concurrent.ConcurrentHashMap

@Serializable
@XmlSerialName("query", "jabber:iq:roster", "")
data class RosterQuery(
    val ver: String? = null,
    @XmlSerialName("item", "jabber:iq:roster", "")
    val items: List<RosterItem> = emptyList()
)

@Serializable
data class RosterItem(
    val jid: String,
    val name: String? = null,
    val subscription: String? = null,
    val ask: String? = null,
    val approved: String? = null,
    @XmlSerialName("group", "jabber:iq:roster", "")
    val groups: List<String> = emptyList()
)

@RequiresApi(Build.VERSION_CODES.O)
class RosterManager(private val owner: String) {
    private val TAG = "RosterManager"
    private val queryIds = ConcurrentHashMap.newKeySet<String>()
    private val xml = XML {
        indent = 2
        autoPolymorphic = false
        defaultPolicy {
            ignoreUnknownChildren()
            pedantic = false
        }
    }

    suspend fun request(stream: Stream) = withContext(Dispatchers.IO) {
        try {
            val elementId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val query = """
                <query xmlns='jabber:iq:roster' ver=''/>
            """.trimIndent()
            val iq = """
                <iq type='get' id='$elementId'>$query</iq>
            """.trimIndent()
            stream.getSocket()?.write(iq)?.also { success ->
                if (success) {
                    queryIds.add(elementId)
                    Log.d(TAG, "Sent roster request IQ with id: $elementId")
                } else {
                    Log.e(TAG, "Failed to send roster request IQ for owner: $owner")
                }
            } ?: run {
                Log.e(TAG, "Cannot send roster request: Socket is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting roster: ${e.message}", e)
        }
    }

    suspend fun read(iq: String): Boolean {
        try {
            when {
                readError(iq) -> return true
                readSuccess(iq) -> return true
                readResponse(iq) -> return true
                else -> return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading IQ: ${e.message}, IQ: $iq", e)
            return false
        }
    }

    private suspend fun readSuccess(iq: String): Boolean {
        val elementIdMatch = Regex("""id=['"]([^'"]+)['"]""").find(iq)
        val elementId = elementIdMatch?.groupValues?.get(1) ?: return false
        if (!queryIds.contains(elementId)) return false

        val queryMatch = Regex("""<query[^>]*xmlns=['"]jabber:iq:roster['"][^>]*>(.*?)</query>""", RegexOption.DOT_MATCHES_ALL).find(iq)
        val queryContent = queryMatch?.groupValues?.get(1) ?: return false

        queryIds.remove(elementId)

        val rosterQuery = try {
            xml.decodeFromString<RosterQuery>("<query xmlns='jabber:iq:roster'>$queryContent</query>")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse roster query: ${e.message}, query: $queryContent", e)
            return false
        }

        Log.d(TAG, "Parsed RosterQuery with version: ${rosterQuery.ver}")
        rosterQuery.items.forEachIndexed { index, item ->
            Log.d(TAG, """
                Roster Item ${index + 1}:
                    JID: ${item.jid}
                    Name: ${item.name ?: "None"}
                    Subscription: ${item.subscription ?: "none"}
                    Ask: ${item.ask ?: "None"}
                    Approved: ${item.approved ?: "false"}
                    Groups: ${item.groups.joinToString(", ") { it }}
            """.trimIndent())
        }

        return true
    }

    private fun readError(iq: String): Boolean {
        val errorMatch = Regex("""<error[^>]*>""").find(iq) ?: return false
        val elementIdMatch = Regex("""id=['"]([^'"]+)['"]""").find(iq)
        val elementId = elementIdMatch?.groupValues?.get(1) ?: return false
        if (!queryIds.contains(elementId)) return false

        queryIds.remove(elementId)
        Log.e(TAG, "Roster IQ error: $errorMatch")
        return true
    }

    private fun readResponse(iq: String): Boolean {
        val elementIdMatch = Regex("""id=['"]([^'"]+)['"]""").find(iq)
        val elementId = elementIdMatch?.groupValues?.get(1) ?: return false
        if (!queryIds.contains(elementId)) return false

        queryIds.remove(elementId)
        Log.d(TAG, "Roster IQ response processed for id: $elementId")
        return true
    }
}






