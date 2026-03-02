package com.xabber.xmpp.avatar

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.presentation.XabberApplication
import com.xabber.xmpp.groupchat.GroupchatUserStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import kotlinx.coroutines.*
import java.io.File

/**
 * Manages avatar loading, caching, and timestamp updates.
 * Mirrors the Swift DefaultAvatarManager using Glide for image handling.
 */
object DefaultAvatarManager {

    private const val TAG = "DefaultAvatarManager"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realm: Realm by lazy { Realm.open(defaultRealmConfig()) }

    // ----------------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------------

    /**
     * Pre‑heat the cache (optional, can be empty)
     */
    fun preheat() {
        // Nothing needed; Glide manages caching automatically
    }

    /**
     * Persist a bitmap to the app's avatar cache directory under the given key.
     * The file can later be retrieved via [getStoredImageFile].
     */
    fun storeImage(key: String, bitmap: Bitmap) {
        scope.launch {
            try {
                val dir = File(XabberApplication.applicationContext().cacheDir, "avatars")
                dir.mkdirs()
                val file = File(dir, "$key.png")
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (e: Exception) {
                Log.e(TAG, "storeImage failed for $key", e)
            }
        }
    }

    /**
     * Returns the cached avatar [File] for the given key, or null if not stored.
     */
    fun getStoredImageFile(key: String): File? {
        val file = File(
            File(XabberApplication.applicationContext().cacheDir, "avatars"),
            "$key.png"
        )
        return if (file.exists()) file else null
    }

    /**
     * Retrieve an avatar for a roster contact or the account itself.
     * @param url Image URL (may be null)
     * @param jid JID of the contact or owner
     * @param owner Account owner JID
     * @param requiredSize Desired size (0 = original)
     * @param callback Invoked on main thread with the Bitmap (or null)
     */
    fun getAvatar(
        url: String?,
        jid: String,
        owner: String,
        requiredSize: Int = 0,
        callback: ((Bitmap?) -> Unit)? = null
    ) {
        if (url.isNullOrBlank()) {
            callback?.invoke(null)
            return
        }

        val context = XabberApplication.applicationContext()

        Glide.with(context)
            .asBitmap()
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .addListener(createAvatarListener(jid, owner, callback))
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    // Listener handles the result
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    /**
     * Retrieve an avatar for a group chat member.
     * @param url Image URL
     * @param userId User ID inside the group
     * @param groupchat Bare JID of the group
     * @param owner Account owner JID
     * @param requiredSize Desired size
     * @param callback Invoked on main thread with Bitmap or null
     */
    fun getGroupAvatar(
        url: String?,
        userId: String,
        groupchat: String,
        owner: String,
        requiredSize: Int = 0,
        callback: ((Bitmap?) -> Unit)? = null
    ) {
        if (url.isNullOrBlank()) {
            callback?.invoke(null)
            return
        }

        val context = XabberApplication.applicationContext()

        Glide.with(context)
            .asBitmap()
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .addListener(createGroupAvatarListener(userId, groupchat, owner, callback))
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {}
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    /**
     * “Delete” an avatar – currently just triggers a timestamp update to refresh UI.
     * (Cache is not explicitly cleared; a new download will overwrite.)
     */
    fun deleteAvatar(jid: String, owner: String) {
        scope.launch {
            updateAvatarTimestamps(jid, owner)
        }
    }

    /**
     * Clear all cached avatars (disk and memory).
     */
    fun deleteAllAvatars() {
        scope.launch {
            Glide.get(XabberApplication.applicationContext()).clearDiskCache()
        }
        mainHandler.post {
            Glide.get(XabberApplication.applicationContext()).clearMemory()
        }
    }

    // ----------------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------------

    private fun createAvatarListener(
        jid: String,
        owner: String,
        callback: ((Bitmap?) -> Unit)?
    ): RequestListener<Bitmap> {
        return object : RequestListener<Bitmap> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: com.bumptech.glide.request.target.Target<Bitmap>?,
                isFirstResource: Boolean
            ): Boolean {
                Log.e(TAG, "Avatar load failed for $jid", e)
                mainHandler.post { callback?.invoke(null) }
                return false
            }

            override fun onResourceReady(
                resource: Bitmap?,
                model: Any?,
                target: com.bumptech.glide.request.target.Target<Bitmap>?,
                dataSource: DataSource?,
                isFirstResource: Boolean
            ): Boolean {
                mainHandler.post { callback?.invoke(resource) }

                // If loaded from network (not cache), schedule timestamp update
                if (dataSource == DataSource.REMOTE) {
                    scope.launch {
                        delay(2000) // Match Swift's 2‑second delay
                        updateAvatarTimestamps(jid, owner)
                    }
                }
                return false
            }
        }
    }

    private fun createGroupAvatarListener(
        userId: String,
        groupchat: String,
        owner: String,
        callback: ((Bitmap?) -> Unit)?
    ): RequestListener<Bitmap> {
        return object : RequestListener<Bitmap> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: com.bumptech.glide.request.target.Target<Bitmap>?,
                isFirstResource: Boolean
            ): Boolean {
                mainHandler.post { callback?.invoke(null) }
                return false
            }

            override fun onResourceReady(
                resource: Bitmap?,
                model: Any?,
                target: com.bumptech.glide.request.target.Target<Bitmap>?,
                dataSource: DataSource?,
                isFirstResource: Boolean
            ): Boolean {
                mainHandler.post { callback?.invoke(resource) }

                if (dataSource == DataSource.REMOTE) {
                    scope.launch {
                        delay(2000)
                        updateGroupAvatarTimestamp(userId, groupchat, owner)
                    }
                }
                return false
            }
        }
    }

    private suspend fun updateAvatarTimestamps(jid: String, owner: String) {
        realm.write {
            // Update roster item
            val rosterPrimary = RosterStorageItem.genPrimary(jid, owner)
            query<RosterStorageItem>("primary = $0", rosterPrimary).first().find()
                ?.let { findLatest(it)?.avatarUpdatedTS = System.currentTimeMillis().toDouble() }

            // Update all LastChats for this jid
            query<LastChatsStorageItem>("jid = $0 AND owner = $1", jid, owner).find()
                .forEach { findLatest(it)?.updateTS = System.currentTimeMillis().toDouble() }

            // If jid is the owner, update account avatar timestamp
            if (jid == owner) {
                query<AccountStorageItem>("primary = $0", jid).first().find()
                    ?.let { findLatest(it)?.avatarUpdatedTS = System.currentTimeMillis().toDouble() }
            }
        }
    }

    private suspend fun updateGroupAvatarTimestamp(userId: String, groupchat: String, owner: String) {
        realm.write {
            val userPrimary = GroupchatUserStorageItem.genPrimary(userId, groupchat, owner)
            query<GroupchatUserStorageItem>("primary = $0", userPrimary).first().find()
                ?.let { findLatest(it)?.updateTimestamp = System.currentTimeMillis() }
        }
    }
}