package com.xabber.xmpp.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.xabber.account.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.stream.Stream
import com.xabber.stream.serializers.XMPPIQ
import com.xabber.xmpp.abstractClass.AbstractXMPPManager
import com.xabber.xmpp.groupchat.GroupchatUserStorageItem
import com.xabber.xmpp.messages.XMLElement
import com.xabber.xmpp.messages.XMPPMessage
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class XmppAvatarManager(owner: String) : AbstractXMPPManager(owner) {

    private val realmLazy = lazy { Realm.open(defaultRealmConfig()) }
    private val realm: Realm by realmLazy

    companion object {
        private const val TAG = "XmppAvatarManager"
        private const val NAMESPACE_PUBSUB = "http://jabber.org/protocol/pubsub"
        private const val NAMESPACE_PUBSUB_EVENT = "http://jabber.org/protocol/pubsub#event"
        private const val NAMESPACE_AVATAR_DATA = "urn:xmpp:avatar:data"
        private const val NAMESPACE_AVATAR_METADATA = "urn:xmpp:avatar:metadata"
        private const val NAMESPACE_VCARD = "vcard-temp"
    }

    enum class AvatarNode(val node: String) {
        METADATA("urn:xmpp:avatar:metadata"),
        DATA("urn:xmpp:avatar:data")
    }

    override fun namespaces(): List<String> = listOf("urn:xmpp:avatar:metadata+notify")

    override fun getPrimaryNamespace(): String = NAMESPACE_AVATAR_METADATA

    // ----------------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------------

    /**
     * Request avatar data (base64) for a given itemId.
     */
    suspend fun requestPubSubItem(stream: Stream, node: AvatarNode, jid: String, itemId: String = "") {
        val elementId = NanoId.generate(9)
        val pubsub = buildString {
            append("<pubsub xmlns='$NAMESPACE_PUBSUB'>")
            append("<items node='${node.node}'")
            if (itemId.isNotEmpty()) {
                append("><item id='$itemId'/></items>")
            } else {
                append(" max_items='1'/>")
            }
            append("</pubsub>")
        }
        val iq = """
            <iq type='get' to='$jid' id='$elementId'>
                $pubsub
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        Log.d(TAG, "Requested PubSub item: node=${node.node}, jid=$jid, itemId=$itemId")
    }

    /**
     * Process an incoming message (typically a PubSub event) for avatar updates.
     */
    suspend fun readMessage(message: XMPPMessage): Boolean {
        if (message.type != "headline") return false
        val event = message.element("event", NAMESPACE_PUBSUB_EVENT) ?: return false
        val items = event.element("items") ?: return false
        val node = items.getAttribute("node") ?: return false
        if (node != AvatarNode.METADATA.node) return false
        val item = items.element("item") ?: return false
        val from = message.from?.bare() ?: return false
        return readFromPubSubMetadata(jid = from, pubsubItem = item)
    }

    // ----------------------------------------------------------------------
    // IQ handling
    // ----------------------------------------------------------------------

    override suspend fun read(iq: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(iq.byteInputStream())
                val iqElement = doc.documentElement
                val id = iqElement.getAttribute("id")
                val from = iqElement.getAttribute("from")
                val type = iqElement.getAttribute("type")

                // Atomically check and claim this query ID
                if (!checkAndRemoveQueryId(id)) return@withContext false

                // Look for pubsub child
                val pubsub = iqElement.getElementsByTagNameNS(NAMESPACE_PUBSUB, "pubsub")?.item(0) as? Element
                    ?: return@withContext false

                return@withContext readFromPubSubData(iqElement, id, from, type)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing IQ: ${e.message}")
                false
            }
        }
    }

    /**
     * Process vCard result (fallback if no PubSub)
     */
    suspend fun readFromVcard(iq: XMPPIQ): Boolean {
        if (iq.type != "result") return false
        if (iq.queryNamespace != NAMESPACE_VCARD) return false
        val jid = iq.from ?: return false

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(iq.raw.byteInputStream())
        val iqElement = doc.documentElement
        val photo = iqElement.getElementsByTagName("PHOTO")?.item(0) as? Element ?: return false
        val binval = photo.getElementsByTagName("BINVAL")?.item(0)?.textContent ?: return false

        // Convert base64 to bitmap and store
        val decoded = Base64.decode(binval, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size) ?: return false
        val hash = decoded.sha1()
        DefaultAvatarManager.storeImage("$hash-$owner", bitmap) // store with a composite key

        // Update Realm
        updateAvatarFromBase64(jid, hash, bitmap)
        return true
    }

    /**
     * Process group chat user card (avatar metadata inside group presence)
     */
    suspend fun readFromUserCard(groupchat: String, userElement: Element): Boolean {
        val userId = userElement.getAttribute("id") ?: return false
        val metadata = userElement.getElementsByTagNameNS(NAMESPACE_AVATAR_METADATA, "metadata")?.item(0) as? Element ?: return false
        val info = metadata.getElementsByTagName("info")?.item(0) as? Element ?: return false
        val imageHash = info.getAttribute("id") ?: return false
        val url = info.getAttribute("url") ?: return false

        // Store in GroupchatUserStorageItem
        realm.write {
            val groupchatId = GroupchatUserStorageItem.genPrimary(userId, groupchat, owner)
            val user = query<GroupchatUserStorageItem>("primary = $0", groupchatId).first().find() ?: return@write
            findLatest(user)?.avatarURI = url
        }
        return true
    }

    // ----------------------------------------------------------------------
    // Private processors
    // ----------------------------------------------------------------------

    private suspend fun readFromPubSubData(iqElement: Element, id: String, from: String, type: String): Boolean {
        if (type != "result") return false
        val pubsub = iqElement.getElementsByTagNameNS(NAMESPACE_PUBSUB, "pubsub")?.item(0) as? Element ?: return false
        val items = pubsub.getElementsByTagName("items")?.item(0) as? Element ?: return false
        val node = items.getAttribute("node") ?: return false
        if (node != AvatarNode.DATA.node) return false
        val item = items.getElementsByTagName("item")?.item(0) as? Element ?: return false
        val itemId = item.getAttribute("id") ?: return false
        val data = item.getElementsByTagNameNS(NAMESPACE_AVATAR_DATA, "data")?.item(0) as? Element ?: return false
        val base64 = data.textContent ?: return false

        val bitmap = base64ToBitmap(base64) ?: return false
        val avatarKey = "$itemId-$owner"
        DefaultAvatarManager.storeImage(avatarKey, bitmap)

        // Update Realm for contact or self
        if (from == owner) {
            // Self avatar
            updateAccountAvatar(itemId, avatarKey)
        } else {
            // Contact avatar
            updateContactAvatar(from, itemId, avatarKey)
        }
        return true
    }

    private suspend fun readFromPubSubMetadata(jid: String, pubsubItem: XMLElement): Boolean {
        val itemId = pubsubItem.getAttribute("id") ?: return false
        val metadata = pubsubItem.element("metadata", NAMESPACE_AVATAR_METADATA) ?: return false
        val info = metadata.element("info") ?: return false
        val url = info.getAttribute("url")

        return if (!url.isNullOrEmpty()) {
            // Extract thumbnails to determine max and min URLs
            val thumbnails = metadata.elements("thumbnail")
            var maxUrl: String = url
            var minUrl: String? = null
            for (thumb in thumbnails) {
                val thumbUrl = thumb.getAttribute("uri") ?: continue
                val width = thumb.getAttribute("width")?.toIntOrNull() ?: 0
                if (width >= 512) {
                    maxUrl = thumbUrl
                } else if (width >= 256 && maxUrl == url) {
                    maxUrl = thumbUrl
                }
                if (width < 256 && width >= 128) {
                    minUrl = thumbUrl
                } else if (width < 128 && minUrl == null) {
                    minUrl = thumbUrl
                }
            }
            updateAvatarUrls(jid, itemId, maxUrl, minUrl)
            true
        } else {
            // No URL, only hash: request the data item
            AccountManager.find(owner)?.stream?.let { stream ->
                requestPubSubItem(stream, AvatarNode.DATA, jid, itemId)
            }
            true
        }
    }

    // ----------------------------------------------------------------------
    // Realm updates
    // ----------------------------------------------------------------------

    private suspend fun updateAvatarFromBase64(jid: String, hash: String, bitmap: Bitmap) {
        val avatarKey = "$hash-$owner"
        realm.write {
            if (jid == owner) {
                val account = query<AccountStorageItem>("primary = $0", owner).first().find() ?: return@write
                findLatest(account)?.apply {
                    oldschoolAvatarKey = avatarKey
                    avatarUpdatedTS = System.currentTimeMillis().toDouble()
                    updatedTS = System.currentTimeMillis().toDouble()
                }
            } else {
                val roster = query<RosterStorageItem>("jid = $0 AND owner = $1", jid, owner).first().find() ?: return@write
                findLatest(roster)?.apply {
                    oldschoolAvatarKey = avatarKey
                    avatarUpdatedTS = System.currentTimeMillis().toDouble()
                    updatedTS = System.currentTimeMillis().toDouble()
                }
            }
        }
    }

    private suspend fun updateAccountAvatar(itemId: String, avatarKey: String) {
        realm.write {
            val account = query<AccountStorageItem>("primary = $0", owner).first().find() ?: return@write
            findLatest(account)?.apply {
                oldschoolAvatarKey = avatarKey
                avatarUpdatedTS = System.currentTimeMillis().toDouble()
                updatedTS = System.currentTimeMillis().toDouble()
            }
        }
    }

    private suspend fun updateContactAvatar(jid: String, itemId: String, avatarKey: String) {
        realm.write {
            val roster = query<RosterStorageItem>("jid = $0 AND owner = $1", jid, owner).first().find() ?: return@write
            findLatest(roster)?.apply {
                oldschoolAvatarKey = avatarKey
                avatarUpdatedTS = System.currentTimeMillis().toDouble()
                updatedTS = System.currentTimeMillis().toDouble()
            }
        }
    }

    private suspend fun updateAvatarUrls(jid: String, itemId: String, maxUrl: String, minUrl: String?) {
        realm.write {
            if (jid == owner) {
                val account = query<AccountStorageItem>("primary = $0", owner).first().find() ?: return@write
                findLatest(account)?.apply {
                    if (oldschoolAvatarKey == itemId) return@write // already set
                    avatarMaxUrl = maxUrl
                    avatarMinUrl = minUrl
                    oldschoolAvatarKey = itemId
                    avatarUpdatedTS = System.currentTimeMillis().toDouble()
                    updatedTS = System.currentTimeMillis().toDouble()
                }
            } else {
                val roster = query<RosterStorageItem>("jid = $0 AND owner = $1", jid, owner).first().find() ?: return@write
                findLatest(roster)?.apply {
                    if (oldschoolAvatarKey == itemId) return@write
                    avatarMaxUrl = maxUrl
                    avatarMinUrl = minUrl
                    oldschoolAvatarKey = itemId
                    avatarUpdatedTS = System.currentTimeMillis().toDouble()
                    updatedTS = System.currentTimeMillis().toDouble()
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val decoded = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64 avatar", e)
            null
        }
    }

    private fun ByteArray.sha1(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(this)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun close() {
        if (realmLazy.isInitialized()) {
            realm.close()
        }
    }
}