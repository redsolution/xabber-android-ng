package com.xabber.presentation.application.fragments.contacts

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.databinding.FragmentNewContactBinding
import com.xabber.presentation.application.contract.navigator

class NewContactFragment : Fragment() {
    private var _binding: FragmentNewContactBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.newContactToolbar.setNavigationIcon(R.drawable.ic_material_close_24)
        binding.newContactToolbar.setNavigationOnClickListener { navigator().goBack() }
        initEditTexts()
        binding.tvTitle.setOnClickListener {
       //    it.isEnabled = binding?.etName.toString().isNotEmpty()
        }
    }

    private fun initEditTexts() {
        with(binding) {

        //    etName.setOnFocusChangeListener { _, hasFocused ->
        //        if (hasFocused) subtitleName.setTextColor(resources.getColor(R.color.blue_500))
        //        else subtitleName.setTextColor(resources.getColor(R.color.grey_600))
        //    }
       //     edAlias.setOnFocusChangeListener { _, hasFocused ->
       //         if (hasFocused) alias.setTextColor(resources.getColor(R.color.blue_500))
       //         else alias.setTextColor(resources.getColor(R.color.grey_600))
       //     }

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun afterTextChanged(p0: Editable?) {
       //             check.isVisible = p0.toString().isNotEmpty()

                }
            }
       //     etCircle.addTextChangedListener(textWatcher)


            val textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun afterTextChanged(p0: Editable?) {
                    if (p0.toString().isNotEmpty()) {
         //               etName.setCompoundDrawables(
         //                   null,
            //                null,
           //                 resources.getDrawable(R.drawable.ic_material_close_24),
            //                null
           //             )
                    } else {
           //             etName.setCompoundDrawables(
           //                 null,
            //                null,
            //                resources.getDrawable(R.drawable.ic_qrcode_scan),
            //                null
            //            )
                    }
                }
            }
          //  etName.addTextChangedListener(textWatcher1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}