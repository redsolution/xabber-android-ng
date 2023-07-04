package com.xabber.presentation.application.fragments.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R

class GradientAdapter (val tryOn: TryOnWallpaper, val list: ArrayList<Gradient>, val checked: ArrayList<Int>) :
RecyclerView.Adapter<GradientAdapter.GradientHolder>() {

    interface TryOnWallpaper {
        fun onClickElement(gradient: Gradient)
    }

    class GradientHolder(item: View) : RecyclerView.ViewHolder(item) {
        private val image = item.findViewById<ImageView>(R.id.cvWallpaper)

        fun setData(w: Gradient) {
            image.setImageResource(w.background)
            //   primaryCardView.setBackgroundResource(0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GradientHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_gradient, parent, false)
        return GradientHolder(view)
    }

    override fun onBindViewHolder(holder: GradientHolder, position: Int) {
        val primaryCardView = holder.itemView.findViewById<ConstraintLayout>(R.id.cardViewWall)
        holder.setData(list[position])
        if (!checked.contains(position)) primaryCardView.setBackgroundResource(0)
        else primaryCardView.setBackgroundResource(R.drawable.blue_frame)
        //  if (checked[position])  primaryCardView.setBackgroundResource(R.drawable.card_view_frame)
        //    else  primaryCardView.setBackgroundResource(0)
        //    for (i in checked.indices) {
        //         checked[i] = false
        //   }


        holder.itemView.setOnClickListener {

            tryOn.onClickElement(list[position])

            primaryCardView.setBackgroundResource(R.drawable.blue_frame)
            if (checked.isEmpty()) checked.add(position)
            else {
                val a = checked.get(0)
                checked.clear()
                checked.add(position)
                notifyDataSetChanged()
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }


}