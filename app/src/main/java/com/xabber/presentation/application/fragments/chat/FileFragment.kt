package com.xabber.presentation.application.fragments.chat

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentFilesBinding
import com.xabber.presentation.BaseFragment

class FileFragment : BaseFragment(R.layout.fragment_files),
    FilesAdapter.FilesListener {
    private val binding by viewBinding(FragmentFilesBinding::bind)
    private var filesAdapter: FilesAdapter? = null
    private var filePaths = ArrayList<Uri>()


    interface FilesListener {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val spancount = 3
        filesAdapter = FilesAdapter(this)
        binding.files.layoutManager = LinearLayoutManager(context)
        context?.let { loadFiles() }
        binding.files.adapter = filesAdapter
    }


    private fun loadFiles() {

    }

    override fun onRecentImagesSelected() {

    }

    override fun tooManyFilesSelected() {

    }
}