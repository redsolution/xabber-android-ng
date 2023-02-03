package com.xabber.presentation.application.activity

import android.content.Context
import android.util.Log
import android.util.SparseArray
import com.xabber.R
import com.xabber.presentation.XabberApplication

object ColorManager {

    fun convertColorNameToId(colorName: String): Int {
        val context: Context = XabberApplication.applicationContext()
        val colors = HashMap<String, Int>()
        colors["red"] = R.color.red_500
        colors["deep-orange"] = R.color.deep_orange_500
        colors["orange"] = R.color.orange_500
        colors["amber"] = R.color.amber_500
        colors["lime"] = R.color.lime_500
        colors["light-green"] = R.color.light_green_500
        colors["green"] = R.color.green_500
        colors["teal"] = R.color.teal_500
        colors["cyan"] = R.color.cyan_500
        colors["light-blue"] = R.color.light_blue_500
        colors["blue"] = R.color.blue_500
        colors["indigo"] = R.color.indigo_500
        colors["deep-purple"] = R.color.dark_purple_500
        colors["purple"] = R.color.purple_500
        colors["pink"] = R.color.pink_500
        colors["blue-grey"] = R.color.blue_grey_500
        colors["brown"] = R.color.brown_500
        val colorId = colors[colorName]
        return colorId!!
    }


    fun convertIndexToColorName(colorIndex: Int): String? {
        val colors = SparseArray<String>()
        colors.put(0, "red")
        colors.put(1, "deep-orange")
        colors.put(2, "orange")
        colors.put(3, "amber")
        colors.put(4, "lime")
        colors.put(5, "light-green")
        colors.put(6, "green")
        colors.put(7, "teal")
        colors.put(8, "cyan")
        colors.put(9, "light-blue")
        colors.put(10, "blue")
        colors.put(11, "indigo")
        colors.put(12, "deep-purple")
        colors.put(13, "purple")
        colors.put(14, "pink")
        colors.put(15, "blue-grey")
        colors.put(16, "brown")
        val colorName = colors[colorIndex]
        return colorName
    }

    fun convertColorNameToIndex(colorName: String): Int {
        Log.d("color", "colorManager colorName = $colorName")
        val colors = java.util.HashMap<String, Int>()
        colors["red"] = 0
        colors["deep-orange"] = 1
        colors["orange"] = 2
        colors["amber"] = 3
        colors["lime"] = 4
        colors["light-green"] = 5
        colors["green"] = 6
        colors["teal"] = 7
        colors["cyan"] = 8
        colors["light-blue"] = 9
        colors["blue"] = 10
        colors["indigo"] = 11
        colors["deep-purple"] = 12
        colors["purple"] = 13
        colors["pink"] = 14
        colors["blue-grey"] = 15
        colors["brown"] = 16
        val colorId = colors[colorName]
        Log.d("color", "colorManager coloId = ${colors[colorName]}")
        return if (colorId != null) colorId else 10
    }



    fun convertColorLightNameToId(colorName: String): Int {
        val context: Context = XabberApplication.applicationContext()
        val colors = HashMap<String, Int>()
        colors["red"] = R.color.red_100
        colors["deep-orange"] = R.color.deep_orange_100
        colors["orange"] = R.color.orange_100
        colors["amber"] = R.color.amber_100
        colors["lime"] = R.color.lime_100
        colors["light-green"] = R.color.light_green_100
        colors["green"] = R.color.green_100
        colors["teal"] = R.color.teal_100
        colors["cyan"] = R.color.cyan_100
        colors["light-blue"] = R.color.light_blue_100
        colors["blue"] = R.color.blue_100
        colors["indigo"] = R.color.indigo_100
        colors["deep-purple"] = R.color.dark_purple_100
        colors["purple"] = R.color.purple_100
        colors["pink"] = R.color.pink_100
        colors["blue-grey"] = R.color.blue_grey_100
        colors["brown"] = R.color.brown_100
        val colorId = colors[colorName]
        return colorId!!
    }



    fun convertColorMediumNameToId(colorName: String): Int {
        val context: Context = XabberApplication.applicationContext()
        val colors = HashMap<String, Int>()
        colors["red"] = R.color.red_300
        colors["deep-orange"] = R.color.deep_orange_300
        colors["orange"] = R.color.orange_300
        colors["amber"] = R.color.amber_300
        colors["lime"] = R.color.lime_300
        colors["light-green"] = R.color.light_green_300
        colors["green"] = R.color.green_300
        colors["teal"] = R.color.teal_300
        colors["cyan"] = R.color.cyan_300
        colors["light-blue"] = R.color.light_blue_300
        colors["blue"] = R.color.blue_300
        colors["indigo"] = R.color.indigo_300
        colors["deep-purple"] = R.color.dark_purple_300
        colors["purple"] = R.color.purple_300
        colors["pink"] = R.color.pink_300
        colors["blue-grey"] = R.color.blue_grey_300
        colors["brown"] = R.color.brown_300
        val colorId = colors[colorName]
        return colorId!!
    }

}