package com.xabber.xmpp.device


import com.xabber.utils.prp
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.presence.ResourceStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import java.util.Date
import java.util.logging.Logger

/**
 * Manages XMPP device operations, including registration, revocation, and presence updates.
 */
open class XMPPDeviceManager(owner: String) : AbstractXMPPManager(owner) {

    companion object {
        private val logger = Logger.getLogger(XMPPDeviceManager::class.java.name)
    }

    var deviceId: String? = null
        private set

    private val queryIds: MutableSet<String> = mutableSetOf()

    /**
     * Indicates whether device management is available.
     */
    var isAvailable: Boolean = false
        private set

    init {
        update()
    }

    /**
     * Returns supported XMPP namespaces.
     */
    override fun namespaces(): List<String> = listOf(
        "https://xabber.com/protocol/devices"
    )

    /**
     * Returns the primary namespace.
     */
    override fun getPrimaryNamespace(): String = namespaces().first()

    /**
     * Sets availability based on [features] from the XMPP stream.
     */
    fun setAvailable(features: DDXMLElement) {
        if (isAvailable) return
        if (features.element("starttls") != null) return

        val synchronization = features.element("devices")
        isAvailable = synchronization?.xmlns == getPrimaryNamespace()
    }

    /**
     * Called when the XMPP stream is prepared, requests the device list.
     */
    override fun onStreamPrepared(stream: XMPPStream) {
        requestList(stream)
    }

    /**
     * Updates the device ID and clears resources for all devices owned by [owner].
     */
    fun update() {
        try {
            val realm = Realm.open(realmConfiguration) // Replace with your Realm config
            deviceId = realm.query<AccountStorageItem>("primary = $0", owner).first().find()?.deviceUuid
            if (!deviceId.isNullOrEmpty()) {
                isAvailable = true
            }
            realm.writeBlocking {
                query<DeviceStorageItem>("owner = $0", owner).find().forEach {
                    it.resource = null
                }
            }
        } catch (e: Exception) {
            logger.warning("XMPPDeviceManager.update: ${e.message}")
        }
    }

    /**
     * Returns the device element for inclusion in XMPP presence.
     */
    val deviceElement: DDXMLElement?
        get() = deviceId?.let { id ->
            DDXMLElement("device").apply {
                setXmlns(getPrimaryNamespace())
                addAttribute(name = "id", stringValue = id)
            }
        }

    /**
     * Updates the current device's resource and links it to [ResourceStorageItem].
     */
    fun updateMyDevice(resource: String) {
        try {
            val realm = Realm.open(realmConfiguration)
            val deviceUuid = realm.query<AccountStorageItem>("primary = $0", owner).first().find()?.deviceUuid
                ?: return
            val devicePrimary = DeviceStorageItem.genPrimary(uid = deviceUuid, owner = owner)
            val deviceInstance = realm.query<DeviceStorageItem>("primary = $0", devicePrimary).first().find()
            if (deviceInstance != null) {
                realm.writeBlocking {
                    deviceInstance.resource = resource
                }
            }
            val resourcePrimary = ResourceStorageItem.genPrimary(jid = owner, owner = owner, resource = resource)
            val resourceInstance = realm.query<ResourceStorageItem>("primary = $0", resourcePrimary).first().find()
            if (resourceInstance != null) {
                realm.writeBlocking {
                    resourceInstance.deviceId = deviceUuid
                }
            }
        } catch (e: Exception) {
            logger.warning("XMPPDeviceManager.updateMyDevice: ${e.message}")
        }
    }

    /**
     * Requests the list of devices from the XMPP server.
     */
    open fun requestList(stream: XMPPStream) {
        if (!isAvailable) return
        val elementId = stream.generateUUID()
        stream.send(
            XMPPIQ(
                type = IQType.GET,
                to = stream.myJID?.domainJID,
                elementId = elementId,
                child = DDXMLElement(
                    name = "query",
                    xmlns = "${getPrimaryNamespace()}#items"
                )
            )
        )
        queryIds.add(elementId)
    }

    /**
     * Updates the device description on the server and locally.
     */
    fun update(stream: XMPPStream, descr: String?) {
        val device = DDXMLElement("device")
        val description = DDXMLElement("description", stringValue = descr)
        try {
            val realm = Realm.open(realmConfiguration)
            val uid = realm.query<AccountStorageItem>("primary = $0", owner).first().find()?.deviceUuid
                ?: return
            device.addAttribute(name = "id", stringValue = uid)
            device.addChild(description)
            val query = DDXMLElement("query").apply {
                setXmlns(getPrimaryNamespace())
                addChild(device)
            }
            val elementId = stream.generateUUID()
            stream.send(
                XMPPIQ(
                    type = IQType.SET,
                    to = stream.myJID?.domainJID,
                    elementId = elementId,
                    child = query
                )
            )
            queryIds.add(elementId)
            val instance = realm.query<DeviceStorageItem>("primary = $0", listOf(uid, owner).prp()).first().find()
            if (instance != null) {
                realm.writeBlocking {
                    instance.descr = descr ?: ""
                }
            }
        } catch (e: Exception) {
            logger.warning("XMPPDeviceManager.update: ${e.message}")
        }
    }

    /**
     * Revokes all devices except the current one.
     */
    open fun revokeAll(stream: XMPPStream) {
        val elementId = stream.generateUUID()
        val revokeAll = DDXMLElement("revoke-all").apply {
            setXmlns(getPrimaryNamespace())
        }
        stream.send(
            XMPPIQ(
                type = IQType.SET,
                to = stream.myJID?.domainJID,
                elementId = elementId,
                child = revokeAll
            )
        )
        queryIds.add(elementId)
        try {
            val realm = Realm.open(realmConfiguration)
            val currentToken = realm.query<AccountStorageItem>("primary = $0", owner).first().find()?.deviceUuid
                ?: return
            val collection = realm.query<DeviceStorageItem>("owner = $0 AND uid != $1", owner, currentToken).find()
            val deviceIds = collection.mapNotNull { it.omemoDeviceId }
            realm.writeBlocking {
                delete(collection)
                delete(query<SignalDeviceStorageItem>("deviceId IN $0", deviceIds))
                delete(query<SignalIdentityStorageItem>("deviceId IN $0", deviceIds))
            }
        } catch (e: Exception) {
            logger.warning("XMPPDeviceManager.revokeAll: ${e.message}")
        }
    }

    /**
     * Revokes specific devices by [uids].
     */
    open fun revoke(stream: XMPPStream, uids: List<String>) {
        val elementId = stream.generateUUID()
        val revoke = DDXMLElement("revoke").apply {
            setXmlns(getPrimaryNamespace())
            uids.map { uid ->
                DDXMLElement("device").apply {
                    addAttribute(name = "id", stringValue = uid)
                }
            }.forEach { addChild(it) }
        }
        stream.send(
            XMPPIQ(
                type = IQType.SET,
                to = stream.myJID?.domainJID,
                elementId = elementId,
                child = revoke
            )
        )
        queryIds.add(elementId)
        try {
            val realm = Realm.open(realmConfiguration)
            val collection = realm.query<DeviceStorageItem>("uid IN $0", uids).find()
            val deviceIds = collection.mapNotNull { it.omemoDeviceId }
            val signalDevices = realm.query<SignalDeviceStorageItem>("deviceId IN $0", deviceIds).find()
            signalDevices.forEach { instance ->
                realm.writeBlocking {
                    instance.state = SignalDeviceState.REVOKED
                }
            }
            realm.writeBlocking {
                delete(collection)
                delete(query<SignalIdentityStorageItem>("deviceId IN $0", deviceIds))
            }
        } catch (e: Exception) {
            logger.warning("XMPPDeviceManager.revoke: ${e.message}")
        }
    }

    /**
     * Processes a presence update, linking or unlinking devices and resources.
     */
    fun read(presence: XMPPPresence, commitTransaction: Boolean): Boolean {
        fun transaction(block: () -> Unit) {
            try {
                val realm = Realm.open(realmConfiguration)
                if (commitTransaction) {
                    realm.writeBlocking { block() }
                } else {
                    block()
                }
            } catch (e: Exception) {
                logger.warning("XMPPDeviceManager.read: ${e.message}")
            }
        }

        if (presence.presenceType == PresenceType.UNAVAILABLE) {
            val from = presence.from ?: return false
            val resource = from.resource ?: return false
            transaction {
                val realm = Realm.open(realmConfiguration)
                val device = realm.query<DeviceStorageItem>("owner = $0 AND resource = $1", from.bare, resource).first().find()
                device?.resource = null
                val resourceItem = realm.query<ResourceStorageItem>(
                    "primary = $0",
                    ResourceStorageItem.genPrimary(jid = from.bare, owner = owner, resource = resource)
                ).first().find()
                resourceItem?.deviceId = null
            }
            return true
        }

        val from = presence.from ?: return false
        val resource = from.resource ?: return false
        val device = presence.element("device", xmlns = getPrimaryNamespace()) ?: return false
        val deviceId = device.attributeStringValue("id") ?: return false

        transaction {
            val realm = Realm.open(realmConfiguration)
            val instance = realm.query<DeviceStorageItem>("primary = $0", listOf(deviceId, from.bare).prp()).first().find()
            instance?.resource = resource
            val resourceItem = realm.query<ResourceStorageItem>(
                "primary = $0",
                ResourceStorageItem.genPrimary(jid = from.bare, owner = owner, resource = resource)
            ).first().find()
            resourceItem?.deviceId = deviceId
        }
        return true
    }

    /**
     * Processes a batch of presence updates.
     */
    fun readBatch(presences: List<XMPPPresence>, commitTransaction: Boolean) {
        fun transaction(block: () -> Unit) {
            try {
                val realm = Realm.open(realmConfiguration)
                if (commitTransaction) {
                    realm.writeBlocking { block() }
                } else {
                    block()
                }
            } catch (e: Exception) {
                logger.warning("XMPPDeviceManager.readBatch: ${e.message}")
            }
        }

        transaction {
            presences.forEach { read(it, commitTransaction = false) }
        }
        presences.forEach { onNewDeviceAnnouncedInPresence(it) }
    }

    /**
     * Handles new device announcements in presence.
     */
    private fun onNewDeviceAnnouncedInPresence(presence: XMPPPresence) {
        val deviceUuid = presence.element("device", xmlns = getPrimaryNamespace())?.attributeStringValue("id")
            ?: return
        val jid = presence.from?.bare ?: return
        if (jid == owner) return
        val deviceIdInteger = deviceUuid.take(8).toIntOrNull() ?: return
        try {
            val realm = Realm.open(realmConfiguration)
            val roster = realm.query<RosterStorageItem>(
                "primary = $0",
                RosterStorageItem.genPrimary(jid = jid, owner = owner)
            ).first().find()
            if (roster?.isOmemoDevicesListReceived != true) {
                if (realm.query<SignalDeviceStorageItem>(
                        "primary = $0",
                        SignalDeviceStorageItem.genPrimary(owner = owner, jid = jid, deviceId = deviceIdInteger)
                    ).first().find() == null) {
                    // TODO: Uncomment and implement AccountManager action
                    // AccountManager.find(owner)?.action { user, stream ->
                    //     user.omemo.getContactDevices(stream, jid)
                    // }
                }
            }
        } catch (e: Exception) {
            logger.warning("XMPPDeviceManager.onNewDeviceAnnouncedInPresence: ${e.message}")
        }
    }

    /**
     * Processes an XMPP message for device updates.
     */
    internal fun readMessage(message: XMPPMessage) {
        if (!(message.from?.isServer ?: false) ||
            message.element("device", xmlns = getPrimaryNamespace()) == null) return
        AccountManager.find(owner)?.unsafeAction { user, stream ->
            if (AccountManager.newAccountJid != owner) {
                user.devices.requestList(stream)
                user.omemo.getOwnDevices(stream)
            }
        }
    }

    /**
     * Processes a headline message for device revocation.
     */
    internal fun readHeadline(message: XMPPMessage): Boolean {
        val deviceId = message.element("revoke", xmlns = getPrimaryNamespace())
            ?.element("device")
            ?.attributeStringValue("id") ?: return false
        try {
            val realm = Realm.open(realmConfiguration)
            val myDeviceId = realm.query<AccountStorageItem>("primary = $0", owner).first().find()?.deviceUuid
            if (myDeviceId == deviceId) {
                // TODO: Replace with your notification mechanism
                // NotificationCenter.post(name = "tokenWasExpired", obj = owner)
            }
        } catch (e: Exception) {
            logger.warning("XMPPDeviceManager.readHeadline: ${e.message}")
        }
        return true
    }

    /**
     * Processes an IQ response, delegating to specific handlers.
     */
    override fun read(iq: XMPPIQ): Boolean {
        return readRegisterDevice(iq) || readList(iq)
    }

    /**
     * Processes a device list IQ response.
     */
    internal fun readList(iq: XMPPIQ): Boolean {
        val query = iq.element("query", xmlns = "${getPrimaryNamespace()}#items") ?: return false
        try {
            val realm = Realm.open(realmConfiguration)
            val uids = query.elements("device").mapNotNull { it.attributeStringValue("id") }
            realm.writeBlocking {
                val tokensToDelete = query<DeviceStorageItem>("owner = $0 AND uid NOT IN $1", owner, uids).find()
                delete(tokensToDelete)
                val notifications = query<NotificationStorageItem>(
                    "owner = $0 AND categoryRaw = $1 AND shouldShow = false",
                    owner, XMPPNotificationsManager.Category.DEVICE.rawValue
                ).find()
                query.elements("device").forEach { item ->
                    val uid = item.attributeStringValue("id") ?: return@forEach
                    val client = item.element("client")?.stringValue ?: return@forEach
                    val expire = item.element("expire")?.stringValueAsDouble() ?: return@forEach
                    val ip = item.element("ip")?.stringValue ?: return@forEach
                    val lastAuth = item.element("last-auth")?.stringValueAsDouble() ?: return@forEach
                    val device = item.element("info")?.stringValue ?: ""
                    val omemoId = item.element("omemo-id")?.stringValueAsInt() ?: -1
                    val descr = item.element("description")?.stringValue

                    val notification = notifications.find { it.metadata["deviceId"] == uid }
                    if (notification != null) {
                        val updatedMetadata = notification.metadata.toMutableMap().apply {
                            put("ip", ip)
                            put("client", client)
                            put("device", device)
                        }
                        notification.metadata = updatedMetadata
                        notification.shouldShow = true
                    }

                    val instance = query<DeviceStorageItem>("primary = $0", listOf(uid, owner).prp()).first().find()
                    if (instance != null) {
                        instance.client = client
                        instance.device = device
                        instance.descr = descr ?: ""
                        instance.ip = ip
                        instance.omemoDeviceId = omemoId
                        instance.expire = Date((expire * 1000).toLong())
                        instance.authDate = Date((lastAuth * 1000).toLong())
                    } else {
                        val newInstance = DeviceStorageItem().apply {
                            configure(
                                owner = this@XMPPDeviceManager.owner,
                                uid = uid,
                                ip = ip,
                                client = client,
                                device = device,
                                expire = expire,
                                authDate = lastAuth,
                                descr = descr ?: ""
                            )
                            omemoDeviceId = omemoId
                        }
                        copyToRealm(newInstance)
                    }
                }
                query<AccountStorageItem>("primary = $0", owner).first().find()?.isDevicesListReceived = true
            }
        } catch (e: Exception) {
            logger.warning("XMPPDeviceManager.readList: ${e.message}")
        }
        return true
    }

    /**
     * Processes a device registration IQ response.
     */
    private fun readRegisterDevice(iq: XMPPIQ): Boolean {
        val device = iq.element("device", xmlns = getPrimaryNamespace()) ?: return false
        val elementId = iq.elementId ?: return false
        if (!queryIds.contains(elementId)) return false
        val deviceId = device.attributeStringValue("id") ?: return false
        val secret = device.element("secret")?.stringValue ?: return false
        val expire = device.element("expire")?.stringValueAsDouble() ?: return false

        this.deviceId = deviceId
        val validationKey = device.element("validation-key")?.stringValue
        // TODO: Implement CredentialsManager
        // CredentialsManager.setXabberDeviceId(owner, deviceId)
        // CredentialsManager.setItem(owner, validationKey, secret)

        val deviceInfo = "Device Info Placeholder" // Replace with platform-specific info
        val clientInfo = "Xabber" // Replace with your config
        val descr = "Device Name" // Replace with platform-specific name

        val instance = DeviceStorageItem().apply {
            configure(
                owner = this@XMPPDeviceManager.owner,
                uid = deviceId,
                ip = "",
                client = clientInfo,
                device = deviceInfo,
                expire = expire,
                authDate = Date().time / 1000.0,
                descr = descr
            )
            // TODO: Fetch omemoDeviceId from CredentialsManager
            // omemoDeviceId = CredentialsManager.getDeviceId(owner) ?: -1
        }

        try {
            val realm = Realm.open(realmConfiguration)
            val oldInstance = realm.query<DeviceStorageItem>("primary = $0", instance.primary).first().find()
            if (oldInstance != null) {
                realm.writeBlocking { delete(oldInstance) }
            }
            realm.writeBlocking { copyToRealm(instance) }
            val account = realm.query<AccountStorageItem>("primary = $0", owner).first().find()
            if (account != null) {
                realm.writeBlocking {
                    account.deviceUuid = deviceId
                    account.xTokenUID = deviceId
                    account.xTokenSupport = true
                }
            }
        } catch (e: Exception) {
            logger.warning("XMPPDeviceManager.readRegisterDevice: ${e.message}")
        }
        return true
    }

    override fun clearSession() {
        super.clearSession()
    }
}

// Placeholder classes and types
abstract class AbstractXMPPManager(val owner: String) {
    open fun namespaces(): List<String> = emptyList()
    open fun getPrimaryNamespace(): String = ""
    open fun onStreamPrepared(stream: XMPPStream) {}
    open fun read(iq: XMPPIQ): Boolean = false
    open fun clearSession() {}
}

data class XMPPStream(
    val myJID: XMPPJID? = null
) {
    fun generateUUID(): String = java.util.UUID.randomUUID().toString()
    fun send(element: Any) {}
    val domainJID: XMPPJID? get() = myJID // Simplified
}

data class XMPPJID(val bare: String, val resource: String? = null) {
    val isServer: Boolean get() = resource == null
}

data class XMPPIQ(
    val type: IQType,
    val to: XMPPJID?,
    val elementId: String,
    val child: DDXMLElement? = null
) {
    fun element(name: String, xmlns: String? = null): DDXMLElement? = null
    val elementId: String? = elementId
}

enum class IQType {
    GET,
    SET
}

data class XMPPPresence(
    val presenceType: PresenceType? = null
) {
    var from: XMPPJID? = null
    fun element(name: String, xmlns: String? = null): DDXMLElement? = null
}

enum class PresenceType {
    UNAVAILABLE
}

data class DDXMLElement(
    val name: String,
    val stringValue: String? = null
) {
    var xmlns: String? = null
    private val attributes = mutableMapOf<String, String>()
    private val children = mutableListOf<DDXMLElement>()

    fun setXmlns(xmlns: String) {
        this.xmlns = xmlns
    }

    fun addAttribute(name: String, stringValue: String) {
        attributes[name] = stringValue
    }

    fun addChild(element: DDXMLElement) {
        children.add(element)
    }

    fun element(name: String): DDXMLElement? = children.find { it.name == name }
    fun attributeStringValue(name: String): String? = attributes[name]
    fun elements(name: String): List<DDXMLElement> = children.filter { it.name == name }
    fun stringValueAsDouble(): Double? = stringValue?.toDoubleOrNull()
    fun stringValueAsInt(): Int? = stringValue?.toIntOrNull()
}

open class AccountStorageItem : RealmObject {
    var primary: String = "" // Assume owner is primary key
    var deviceUuid: String? = null
    var xTokenUID: String? = null
    var xTokenSupport: Boolean = false
    var isDevicesListReceived: Boolean = false
}

open class SignalDeviceStorageItem : RealmObject {
    var primary: String = ""
    var owner: String = ""
    var jid: String = ""
    var deviceId: Int = -1
    var state: SignalDeviceState = SignalDeviceState.ACTIVE

    companion object {
        fun genPrimary(owner: String, jid: String, deviceId: Int): String = listOf(owner, jid, deviceId.toString()).prp()
    }
}

enum class SignalDeviceState {
    ACTIVE,
    REVOKED
}

open class SignalIdentityStorageItem : RealmObject {
    var deviceId: Int = -1
}

object AccountManager {
    var newAccountJid: String? = null
    fun find(owner: String): User? = null
}

data class User(
    val devices: XMPPDeviceManager,
    val omemo: OmemoManager
) {
    fun unsafeAction(block: (User, XMPPStream) -> Unit) {}
}

data class OmemoManager {
    fun getOwnDevices(stream: XMPPStream) {}
}

object XMPPNotificationsManager {
    enum class Category(val rawValue: String) {
        DEVICE("device")
    }
}

// Placeholder for Realm configuration
val realmConfiguration = RealmConfiguration.create(
    schema = setOf(
        DeviceStorageItem::class,
        ResourceStorageItem::class,
        AccountStorageItem::class,
        SignalDeviceStorageItem::class,
        SignalIdentityStorageItem::class,
        RosterStorageItem::class,
        NotificationStorageItem::class
    )
)