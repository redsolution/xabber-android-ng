package com.xabber.presentation.onboarding.fragments.signin.feature

import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.databinding.ItemFeatureBinding
import com.xabber.presentation.onboarding.fragments.signin.feature.State.*

class FeatureViewHolder(private val binding: ItemFeatureBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: Feature) {
        with(binding) {
            featureName.text = itemView.resources.getString(item.nameResId)

            when (item.state) {
                Loading -> {
                    featureName.setTextColor(
                        ResourcesCompat.getColor(
                            itemView.resources,
                            R.color.grey_400,
                            itemView.context.theme
                        )
                    )
                    binding.featureLoad.visibility = View.VISIBLE
                    binding.featureResult.visibility = View.GONE
                }
                Success -> {
                    featureName.setTextColor(
                        ResourcesCompat.getColor(
                            itemView.resources,
                            R.color.black_text,
                            itemView.context.theme
                        )
                    )
                    featureLoad.visibility = View.GONE
                    featureResult.setBackgroundResource(R.drawable.checkbox_checked_circle_green)
                    featureResult.visibility = View.VISIBLE
                }
                Error -> {
                    featureName.setTextColor(
                        ResourcesCompat.getColor(
                            itemView.resources,
                            R.color.black_text,
                            itemView.context.theme
                        )
                    )
                    featureLoad.visibility = View.GONE
                    featureResult.setBackgroundResource(R.drawable.exclamation_mark_red_circle)
                    featureResult.visibility = View.VISIBLE
                }
            }
        }
    }
}