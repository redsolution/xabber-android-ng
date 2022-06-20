package com.xabber.presentation.application.fragments.chat

import android.app.Dialog
import android.content.ContentUris
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.data.util.dp
import com.xabber.databinding.BottomSheetAttachBinding
import com.xabber.presentation.application.fragments.chat.GalleryAdapter.Companion.projectionPhotos
import java.util.*
import kotlin.collections.ArrayList

class BottomSheet : BottomSheetDialogFragment(), GalleryAdapter.Listener {
      private var _binding: BottomSheetAttachBinding? = null
    private val binding get() = _binding!!
    private var galleryAdapter: GalleryAdapter? = null
   // var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    //  var cancelButton: Button? = null


     override fun onCreateView(
         inflater: LayoutInflater,
         container: ViewGroup?,
         savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAttachBinding.inflate(inflater, container, false)
         Log.d("uuu", "$binding")
        return binding.root
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
   //     bottomSheetBehavior = BottomSheetBehavior.from(binding.root as View)
         dialog.setOnShowListener { setupBottomSheet(it) }
      return dialog
      //  val bt = view.findViewById<Button>(R.id.but)
     //   bt.setOnClickListener {
   //         childFragmentManager.beginTransaction().replace(R.id.container, BlankFragment()).commit()}

// navigator().showAccount() }



      //  bottomSheetBehavior!!.setPeekHeight(BottomSheetBehavior.PEEK_HEIGHT_AUTO)

// val appBarLayout = view.findViewById<AppBarLayout>(R.id.appBarLayout)
// val profileLayout = view.findViewById<LinearLayout>(R.id.profileLayout)
//        (bottomSheetBehavior as BottomSheetBehavior<View>).setBottomSheetCallback(object :
//            BottomSheetBehavior.BottomSheetCallback() {
//            override fun onStateChanged(@NonNull view: View, i: Int) {
//                if (BottomSheetBehavior.STATE_EXPANDED == i) {
//                    // showView(appBarLayout, getActionBarSize())
//                    //showView(profileLayout, Resources.getSystem().displayMetrics.heightPixels)
//                    // hideAppBar(profileLayout)
//                }
//                if (BottomSheetBehavior.STATE_COLLAPSED == i) {
//                    // hideAppBar(appBarLayout)
//                    // showView(profileLayout, Resources.getSystem().displayMetrics.heightPixels)
//                }
//                if (BottomSheetBehavior.STATE_HIDDEN == i) {
//                    dismiss()
//                }
//            }
//
//            override fun onSlide(@NonNull view: View, v: Float) {}
//        })
//

    }
 private fun setupBottomSheet(dialogInterface: DialogInterface) {
      val widthDp =
            (Resources.getSystem().displayMetrics.heightPixels / Resources.getSystem().displayMetrics.density).toInt()
        //setting Peek at the 16:9 ratio keyline of its parent.




        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
            ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
     val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(
         bottomSheet
     )
     behavior.setPeekHeight(BottomSheetBehavior.PEEK_HEIGHT_AUTO)
     (behavior as BottomSheetBehavior<*>).peekHeight = widthDp / 100 * 60.dp
     binding.root.minimumHeight = 1200
     galleryAdapter = GalleryAdapter(this)
     val width =  (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
     val spancount  = if (width > 600) 4 else 3
     binding.images.layoutManager = GridLayoutManager(context, spancount)
      context?.let { loadGalleryPhotosAlbums() }
     binding.images.adapter = galleryAdapter

    }

      private fun loadGalleryPhotosAlbums() {
        val imagePaths = ArrayList<String>()
        context?.contentResolver?.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projectionPhotos,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    imagePaths.add(contentUri.toString())
                    galleryAdapter?.updateAdapter(imagePaths)
                   galleryAdapter?.notifyDataSetChanged()
                     Log.d("test", "${contentUri.toString()}")

                }
            }

        }
    }
//
    override fun onStart() {
        super.onStart()

   //     bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_COLLAPSED)
    }

    private fun showView(view: View, size: Int) {
// val params = view.layoutParams
// params.height = size
// view.layoutParams = params
    }

    private fun hideAppBar(view: View) {
        val params = view.layoutParams
        params.height = 0
        view.layoutParams = params
    }

    private fun getActionBarSize(): Int {
        val array =
            context?.theme?.obtainStyledAttributes(intArrayOf(android.R.attr.actionBarSize))
        return array?.getDimension(0, 0f)!!.toInt()

    }

    override fun onRecentImagesSelected() {
      val size = galleryAdapter?.getSelectedImagePath()!!.size
        if (size > 0) {

                String.format(
                    Locale.getDefault(),
                    "Send (%d)",
                    size
                )


        } else {

        }
    }

    override fun tooManyFilesSelected() {
         Toast.makeText(context, "Too_many_files_at_once", Toast.LENGTH_SHORT).show()

    }
}

