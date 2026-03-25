package com.xabber.xmpp.avatar

import android.graphics.Bitmap
import android.util.Log
import com.xabber.account.AccountManager
import com.xabber.common.SettingManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.stream.Stream
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class AvatarUploadManager(private val owner: String) {

    companion object {
        private const val TAG = "AvatarUploadManager"
        private const val UPLOAD_PATH = "v1/files/upload/"
    }

    private val realmLazy = lazy { Realm.open(defaultRealmConfig()) }
    private val realm: Realm by realmLazy
    private val client = OkHttpClient()

    data class Thumbnail(val height: Int, val url: String, val width: Int)

    data class AvatarResponse(
        val file: String,
        val hash: String,
        val name: String,
        val size: Int,
        val thumbnails: List<Thumbnail>
    )

    private val token: String
        get() = SettingManager.getKey(owner, SettingManager.KeyScope.XABBER_UPLOAD_MANAGER, "userToken") ?: ""

    fun isAvailable(): Boolean {
        val node = SettingManager.getKey(owner, SettingManager.KeyScope.XABBER_UPLOAD_MANAGER, "node")
        return !node.isNullOrEmpty()
    }

    private fun getUploadNode(): String? =
        SettingManager.getKey(owner, SettingManager.KeyScope.XABBER_UPLOAD_MANAGER, "node")

    // ----------------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------------

    suspend fun setAvatar(
        bitmap: Bitmap,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int, String) -> Unit)? = null
    ) {
        val imageData = bitmapToPng(bitmap) ?: run {
            onFailure?.invoke(400, "Failed to encode image")
            return
        }
        uploadAndPublish(imageData, "image/png", groupchat = null, onSuccess, onFailure)
    }

    suspend fun setGroupAvatar(
        groupchat: String,
        bitmap: Bitmap,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int, String) -> Unit)? = null
    ) {
        val imageData = bitmapToPng(bitmap) ?: run {
            onFailure?.invoke(400, "Failed to encode image")
            return
        }
        uploadAndPublish(imageData, "image/png", groupchat, onSuccess, onFailure)
    }

    suspend fun clearAvatar(groupchatJid: String? = null) {
        val stream = AccountManager.find(owner)?.stream ?: return
        sendClearMetadata(stream, groupchatJid)
        realm.write {
            if (groupchatJid != null) {
                val roster = query<RosterStorageItem>(
                    "jid = $0 AND owner = $1", groupchatJid, owner
                ).first().find() ?: return@write
                findLatest(roster)?.apply {
                    avatarMaxUrl = null
                    avatarMinUrl = null
                    oldschoolAvatarKey = null
                    avatarUpdatedTS = System.currentTimeMillis().toDouble()
                }
            } else {
                val account = query<AccountStorageItem>("primary = $0", owner).first().find() ?: return@write
                findLatest(account)?.apply {
                    avatarMaxUrl = null
                    avatarMinUrl = null
                    oldschoolAvatarKey = null
                    avatarUpdatedTS = System.currentTimeMillis().toDouble()
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // Upload + Publish
    // ----------------------------------------------------------------------

    private suspend fun uploadAndPublish(
        imageData: ByteArray,
        mimeType: String,
        groupchat: String?,
        onSuccess: (() -> Unit)?,
        onFailure: ((Int, String) -> Unit)?
    ) {
        val result = uploadAvatar(imageData, "${NanoId.generate(5)}.png", mimeType)
        if (result == null) {
            onFailure?.invoke(400, "Upload failed")
            return
        }

        // Pick best thumbnail URLs
        var maxUrl: String = result.file
        var minUrl: String? = null
        for (thumb in result.thumbnails) {
            if (thumb.width >= 512) {
                maxUrl = thumb.url
            } else if (thumb.width >= 256 && maxUrl == result.file) {
                maxUrl = thumb.url
            }
            if (thumb.width in 128..255) {
                minUrl = thumb.url
            } else if (thumb.width < 128 && minUrl == null) {
                minUrl = thumb.url
            }
        }

        // Update Realm
        realm.write {
            if (groupchat != null) {
                val roster = query<RosterStorageItem>(
                    "jid = $0 AND owner = $1", groupchat, owner
                ).first().find() ?: return@write
                findLatest(roster)?.apply {
                    oldschoolAvatarKey = result.hash
                    avatarUpdatedTS = System.currentTimeMillis().toDouble()
                    avatarMaxUrl = maxUrl
                    avatarMinUrl = minUrl
                }
            } else {
                val account = query<AccountStorageItem>("primary = $0", owner).first().find() ?: return@write
                findLatest(account)?.apply {
                    oldschoolAvatarKey = result.hash
                    avatarUpdatedTS = System.currentTimeMillis().toDouble()
                    avatarMaxUrl = maxUrl
                    avatarMinUrl = minUrl
                }
            }
        }

        onSuccess?.invoke()

        // Publish PubSub metadata
        val stream = AccountManager.find(owner)?.stream ?: return
        sendImageMetadata(stream, result, groupchat)
    }

    // ----------------------------------------------------------------------
    // HTTP Upload
    // ----------------------------------------------------------------------

    private suspend fun uploadAvatar(
        data: ByteArray,
        filename: String,
        mimeType: String
    ): AvatarResponse? = withContext(Dispatchers.IO) {
        val node = getUploadNode() ?: run {
            Log.e(TAG, "Upload node not available")
            return@withContext null
        }
        val url = "$node$UPLOAD_PATH"

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, data.toRequestBody(mimeType.toMediaType()))
            .addFormDataPart("media_type", mimeType)
            .addFormDataPart("create_thumbnail", "true")
            .addFormDataPart("context", "avatar")
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                parseAvatarResponse(responseBody)
            } else {
                val errorMsg = responseBody?.let {
                    try { JSONObject(it).getString("error") } catch (_: Exception) { it }
                } ?: "Unknown error"
                Log.e(TAG, "Upload failed (${response.code}): $errorMsg")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}", e)
            null
        }
    }

    private fun parseAvatarResponse(json: String): AvatarResponse? {
        return try {
            val obj = JSONObject(json)
            val thumbsArray = obj.optJSONArray("thumbnails") ?: JSONArray()
            val thumbnails = (0 until thumbsArray.length()).map { i ->
                val t = thumbsArray.getJSONObject(i)
                Thumbnail(
                    height = t.getInt("height"),
                    url = t.getString("url"),
                    width = t.getInt("width")
                )
            }
            AvatarResponse(
                file = obj.getString("file"),
                hash = obj.getString("hash"),
                name = obj.getString("name"),
                size = obj.getInt("size"),
                thumbnails = thumbnails
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse avatar response: ${e.message}")
            null
        }
    }

    // ----------------------------------------------------------------------
    // PubSub Publish
    // ----------------------------------------------------------------------

    private suspend fun sendImageMetadata(stream: Stream, avatar: AvatarResponse, groupchat: String? = null) {
        val thumbnailsXml = avatar.thumbnails.joinToString("") { thumb ->
            "<thumbnail xmlns='urn:xmpp:thumbs:1' url='${thumb.url}' media-type='image/png' width='${thumb.width}' height='${thumb.height}'/>"
        }
        val elementId = "Avatar: ${NanoId.generate(8)}"
        val toAttr = groupchat?.let { " to='$it'" } ?: ""
        val iq = """
            <iq type='set'$toAttr id='$elementId'>
                <pubsub xmlns='http://jabber.org/protocol/pubsub'>
                    <publish node='urn:xmpp:avatar:metadata'>
                        <item id='${avatar.hash}'>
                            <metadata xmlns='urn:xmpp:avatar:metadata'>
                                <info bytes='${avatar.size}' url='${avatar.file}' id='${avatar.hash}' type='image/png'>$thumbnailsXml</info>
                            </metadata>
                        </item>
                    </publish>
                </pubsub>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
    }

    private suspend fun sendClearMetadata(stream: Stream, groupchat: String? = null) {
        val elementId = "Avatar: ${NanoId.generate(8)}"
        val toAttr = groupchat?.let { " to='$it'" } ?: ""
        val iq = """
            <iq type='set'$toAttr id='$elementId'>
                <pubsub xmlns='http://jabber.org/protocol/pubsub'>
                    <publish node='urn:xmpp:avatar:metadata'>
                        <item id='${NanoId.generate(8)}'>
                            <metadata xmlns='urn:xmpp:avatar:metadata'/>
                        </item>
                    </publish>
                </pubsub>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private fun bitmapToPng(bitmap: Bitmap): ByteArray? {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode bitmap", e)
            null
        }
    }

    fun close() {
        if (realmLazy.isInitialized()) {
            realm.close()
        }
    }
}
