package com.xabber.presentation.application.fragments.account

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.util.dp
import com.xabber.databinding.FragmentTestBinding
import com.xabber.presentation.application.util.WindowSize

class TestFragment : Fragment() {
    private var binding: FragmentTestBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentTestBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setContainersWidth()
        Glide.with(this)
            .load(
                if (getWidthWindowType() == WindowSize.COMPACT) {
                    R.drawable.ic_material_arrow_left_24
                } else {
                    R.drawable.ic_material_close_24
                }
            ).into(binding!!.toolbarDetailsNavigation)

        if (getWidthWindowType() == WindowSize.COMPACT) {
            binding!!.toolbarDetailsNavigation.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.black)
            )
        }



    }


    fun getWidthWindowType(): WindowSize {
        val widthDp =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val widthWindowSize = when {
            widthDp >= 900f -> WindowSize.EXPANDED
            widthDp >= 600f && widthDp < 900f -> WindowSize.MEDIUM
            else -> WindowSize.COMPACT
        }
//        Toast.makeText(this, "$widthDp $widthWindowSize", Toast.LENGTH_SHORT).show()

        return widthWindowSize
    }

    fun setContainersWidth() {
        binding!!.detailsContentContainerWrapper.updateLayoutParams<ConstraintLayout.LayoutParams> {
            this.horizontalWeight =
                when (getWidthWindowType()) {
                    WindowSize.EXPANDED -> 6F
                    WindowSize.MEDIUM -> 6F
                    WindowSize.COMPACT -> 0F

                }

            binding!!.contentContainerWrapper.updateLayoutParams<ConstraintLayout.LayoutParams> {
                this.width =

                    when (getWidthWindowType()) {
                        WindowSize.EXPANDED -> 360.dp
                        WindowSize.MEDIUM -> 0.dp
                        WindowSize.COMPACT -> 0.dp

                    }
            }
        }
    }
}