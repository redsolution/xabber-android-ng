package com.xabber.presentation.onboarding.fragments.signup.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.avatar.AvatarStorageItem
import com.xabber.presentation.AppConstants.TEMP_FILE_NAME
import io.realm.kotlin.Realm
import java.io.File
import java.io.FileOutputStream

class EmojiAvatarViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())

    fun getBitmapFromView(context: Context, view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgDrawable = view.background
        if (bgDrawable != null)
            bgDrawable.draw(canvas)
        else
            canvas.drawColor(ContextCompat.getColor(context, R.color.blue_400))
        view.draw(canvas)
        return bitmap
    }

    fun saveBitmapToFile(id: String?, bitmap: Bitmap, parentPath: File) {
        val file = File(parentPath, TEMP_FILE_NAME)
        val ostream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, ostream)
        ostream.close()
        val avatarUri = Uri.fromFile(file)
     if (id != null)   saveAvatar(id, avatarUri.toString())
    }

    fun saveAvatar(id: String, uri: String) {
        Log.d("jjj", "id = $id")
        realm.writeBlocking {
            val avatar = this.query(AvatarStorageItem::class, "primary = '$id'").first().find()
            if (avatar == null) {
                this.copyToRealm(AvatarStorageItem().apply {
                    primary = id
                    fileUri = uri
                    jid = id
                    owner = id
                })
            } else avatar.fileUri = uri
            val account = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "jid = '$id'").first().find()
            account?.hasAvatar = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

}
