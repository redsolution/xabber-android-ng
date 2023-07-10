package com.xabber.presentation.application.manage

import com.xabber.R
import com.xabber.presentation.XabberApplication

object ColorManager {
    private val resources = XabberApplication.applicationContext().resources

    fun convertColorNameToId(colorName: String): Int {
        val colorMap = mapOf(
            resources.getString(R.string.red) to R.color.red_500,
            resources.getString(R.string.deep_orange) to R.color.deep_orange_500,
            resources.getString(R.string.orange) to R.color.orange_500,
            resources.getString(R.string.amber) to R.color.amber_500,
            resources.getString(R.string.lime) to R.color.lime_500,
            resources.getString(R.string.light_green) to R.color.light_green_500,
            resources.getString(R.string.green) to R.color.green_500,
            resources.getString(R.string.teal) to R.color.teal_500,
            resources.getString(R.string.cyan) to R.color.cyan_500,
            resources.getString(R.string.light_blue) to R.color.light_blue_500,
            resources.getString(R.string.blue) to R.color.blue_500,
            resources.getString(R.string.indigo) to R.color.indigo_500,
            resources.getString(R.string.deep_purple) to R.color.dark_purple_500,
            resources.getString(R.string.purple) to R.color.purple_500,
            resources.getString(R.string.pink) to R.color.pink_500,
            resources.getString(R.string.blue_grey) to R.color.blue_grey_500,
            resources.getString(R.string.brown) to R.color.brown_500,
            resources.getString(R.string.offline) to R.color.grey_500
        )
        return colorMap[colorName] ?: R.color.grey_500
    }

    fun convertColorNameToIndex(colorName: String): Int {
        val colors = java.util.HashMap<String, Int>()
        colors[resources.getString(R.string.red)] = 0
        colors[resources.getString(R.string.deep_orange)] = 1
        colors[resources.getString(R.string.orange)] = 2
        colors[resources.getString(R.string.amber)] = 3
        colors[resources.getString(R.string.lime)] = 4
        colors[resources.getString(R.string.light_green)] = 5
        colors[resources.getString(R.string.green)] = 6
        colors[resources.getString(R.string.teal)] = 7
        colors[resources.getString(R.string.cyan)] = 8
        colors[resources.getString(R.string.light_blue)] = 9
        colors[resources.getString(R.string.blue)] = 10
        colors[resources.getString(R.string.indigo)] = 11
        colors[resources.getString(R.string.deep_purple)] = 12
        colors[resources.getString(R.string.purple)] = 13
        colors[resources.getString(R.string.pink)] = 14
        colors[resources.getString(R.string.blue_grey)] = 15
        colors[resources.getString(R.string.brown)] = 16
        val colorId = colors[colorName]
        return colorId ?: 10
    }

    fun convertColorLightNameToId(colorName: String): Int {
        val colors = HashMap<String, Int>()
        colors[resources.getString(R.string.red)] = R.color.red_100
        colors[resources.getString(R.string.deep_orange)] = R.color.deep_orange_100
        colors[resources.getString(R.string.orange)] = R.color.orange_100
        colors[resources.getString(R.string.amber)] = R.color.amber_100
        colors[resources.getString(R.string.lime)] = R.color.lime_100
        colors[resources.getString(R.string.light_green)] = R.color.light_green_100
        colors[resources.getString(R.string.green)] = R.color.green_100
        colors[resources.getString(R.string.teal)] = R.color.teal_100
        colors[resources.getString(R.string.cyan)] = R.color.cyan_100
        colors[resources.getString(R.string.light_blue)] = R.color.light_blue_100
        colors[resources.getString(R.string.blue)] = R.color.blue_100
        colors[resources.getString(R.string.indigo)] = R.color.indigo_100
        colors[resources.getString(R.string.deep_purple)] = R.color.dark_purple_100
        colors[resources.getString(R.string.purple)] = R.color.purple_100
        colors[resources.getString(R.string.pink)] = R.color.pink_100
        colors[resources.getString(R.string.blue_grey)] = R.color.blue_grey_100
        colors[resources.getString(R.string.brown)] = R.color.brown_100
        colors[resources.getString(R.string.offline)] = R.color.grey_100
        return colors[colorName] ?: R.color.grey_100
    }

    fun convertColorMediumNameToId(colorName: String): Int {
        val colors = HashMap<String, Int>()
        colors[resources.getString(R.string.red)] = R.color.red_300
        colors[resources.getString(R.string.deep_orange)] = R.color.deep_orange_300
        colors[resources.getString(R.string.orange)] = R.color.orange_300
        colors[resources.getString(R.string.amber)] = R.color.amber_300
        colors[resources.getString(R.string.lime)] = R.color.lime_300
        colors[resources.getString(R.string.light_green)] = R.color.light_green_300
        colors[resources.getString(R.string.green)] = R.color.green_300
        colors[resources.getString(R.string.teal)] = R.color.teal_300
        colors[resources.getString(R.string.cyan)] = R.color.cyan_300
        colors[resources.getString(R.string.light_blue)] = R.color.light_blue_300
        colors[resources.getString(R.string.blue)] = R.color.blue_300
        colors[resources.getString(R.string.indigo)] = R.color.indigo_300
        colors[resources.getString(R.string.deep_purple)] = R.color.dark_purple_300
        colors[resources.getString(R.string.purple)] = R.color.purple_300
        colors[resources.getString(R.string.pink)] = R.color.pink_300
        colors[resources.getString(R.string.blue_grey)] = R.color.blue_grey_300
        colors[resources.getString(R.string.brown)] = R.color.brown_300
        colors[resources.getString(R.string.offline)] = R.color.grey_300
        return colors[colorName] ?: R.color.grey_300
    }

    fun convertColorSuperLightNameToId(colorName: String): Int {
        val colors = HashMap<String, Int>()
        colors[resources.getString(R.string.red)] = R.color.red_50
        colors[resources.getString(R.string.deep_orange)] = R.color.deep_orange_50
        colors[resources.getString(R.string.orange)] = R.color.orange_50
        colors[resources.getString(R.string.amber)] = R.color.amber_50
        colors[resources.getString(R.string.lime)] = R.color.lime_50
        colors[resources.getString(R.string.light_green)] = R.color.light_green_50
        colors[resources.getString(R.string.green)] = R.color.green_50
        colors[resources.getString(R.string.teal)] = R.color.teal_50
        colors[resources.getString(R.string.cyan)] = R.color.cyan_50
        colors[resources.getString(R.string.light_blue)] = R.color.light_blue_50
        colors[resources.getString(R.string.blue)] = R.color.blue_50
        colors[resources.getString(R.string.indigo)] = R.color.indigo_50
        colors[resources.getString(R.string.deep_purple)] = R.color.dark_purple_50
        colors[resources.getString(R.string.purple)] = R.color.purple_50
        colors[resources.getString(R.string.pink)] = R.color.pink_50
        colors[resources.getString(R.string.blue_grey)] = R.color.blue_grey_50
        colors[resources.getString(R.string.brown)] = R.color.brown_50
        return colors[colorName] ?: R.color.grey_50
    }

}
