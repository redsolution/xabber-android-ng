package com.xabber.presentation.application.fragments.chat.geo

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.remote.Place
import com.xabber.remote.prettyName


class FoundPlacesRecyclerViewAdapter(
    var placesList: List<Place> = listOf(), val onPlaceClickListener: (Place) -> Unit,
) : RecyclerView.Adapter<PlaceVH>() {

    override fun onBindViewHolder(holder: PlaceVH, position: Int) {
        holder.itemView.findViewById<TextView>(R.id.text).apply {
            val place = placesList[position]
            text = place.prettyName
            setOnClickListener {
                onPlaceClickListener(place)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceVH {
        return PlaceVH(
            LayoutInflater.from(parent.context).inflate(
                R.layout.found_places_item, parent, false
            ).apply {
                setBackgroundColor(resources.getColor(R.color.white))
            }
        )
    }

    override fun getItemCount(): Int = placesList.size

}
