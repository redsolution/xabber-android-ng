package com.xabber.presentation.application.manage

import android.util.Log
import android.util.SparseArray
import com.xabber.R

object ColorManager {

    fun convertColorNameToId(colorName: String): Int {
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
        colors["offline"] = R.color.grey_500
        return colors[colorName] ?: R.color.grey_500
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
        return colorId ?: 10
    }

    fun convertColorLightNameToId(colorName: String): Int {
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
        colors["offline"] = R.color.grey_100
        return colors[colorName] ?: R.color.grey_100
    }

    fun convertColorMediumNameToId(colorName: String): Int {
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
        colors["offline"] = R.color.grey_300
        return colors[colorName] ?: R.color.grey_300
    }

    fun convertColorSuperLightNameToId(colorName: String): Int {
        val colors = HashMap<String, Int>()
        colors["red"] = R.color.red_50
        colors["deep-orange"] = R.color.deep_orange_50
        colors["orange"] = R.color.orange_50
        colors["amber"] = R.color.amber_50
        colors["lime"] = R.color.lime_50
        colors["light-green"] = R.color.light_green_50
        colors["green"] = R.color.green_50
        colors["teal"] = R.color.teal_50
        colors["cyan"] = R.color.cyan_50
        colors["light-blue"] = R.color.light_blue_50
        colors["blue"] = R.color.blue_50
        colors["indigo"] = R.color.indigo_50
        colors["deep-purple"] = R.color.dark_purple_50
        colors["purple"] = R.color.purple_50
        colors["pink"] = R.color.pink_50
        colors["blue-grey"] = R.color.blue_grey_50
        colors["brown"] = R.color.brown_50

        return colors[colorName] ?: R.color.grey_50
    }

}
