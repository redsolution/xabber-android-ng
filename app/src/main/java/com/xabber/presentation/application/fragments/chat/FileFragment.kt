package com.xabber.presentation.application.fragments.chat

import android.content.ContentUris
import android.content.ContentUris.withAppendedId
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ContentInfo
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentFilesBinding
import com.xabber.presentation.BaseFragment
import io.reactivex.rxjava3.disposables.Disposable
import okio.FileMetadata
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.io.File


class FileFragment : BaseFragment(R.layout.fragment_files),
    FileAdapter.FilesListener {
    private val binding by viewBinding(FragmentFilesBinding::bind)
    private var fileAdapter: FileAdapter? = null
    private var filePaths = ArrayList<Uri>()

    interface FilesListener {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fileAdapter = FileAdapter(this)
        binding.files.layoutManager = LinearLayoutManager(context)
        context?.let { loadFiles() }
        binding.files.adapter = fileAdapter
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadFiles() {
    context?.contentResolver?.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            GalleryAdapter.projectionPhotos,
            null,
            null,
            MediaStore.Downloads.DATE_TAKEN + " DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val id = cursor.getLong(idColumn)
                    val contentUri = withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                    )

                    filePaths.add(contentUri)
             val name =  cursor.getColumnName(idColumn)
                val type: String? = requireContext().contentResolver.getType(contentUri)
                Log.d("yyy", " $name")
            }

            fileAdapter?.updateAdapter(filePaths)
            fileAdapter?.notifyDataSetChanged()
        }
    }

    private class FileLister(val directory: File) : Publisher<String> {

        private lateinit var subscriber: Subscriber<in String>

        override fun subscribe(s: Subscriber<in String>?) {
            if (s == null) {
                return
            }
            this.subscriber = s
            this.listFiles(this.directory)
            this.subscriber.onComplete()
        }

        /**
         * Recursively list files from a given directory.
         */
        private fun listFiles(directory: File) {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file != null) {
                        if (file.isDirectory) {
                            listFiles(file)
                        } else {
                            subscriber.onNext(file.absolutePath)
                        }
                    }
                }
            }
        }


    }

    override fun onRecentImagesSelected() {

    }

    override fun tooManyFilesSelected() {

    }
}