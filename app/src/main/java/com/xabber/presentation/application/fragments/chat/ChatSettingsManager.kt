package com.xabber.presentation.application.fragments.chat

import com.xabber.R
import com.xabber.presentation.application.fragments.settings.MessageTailType

object ChatSettingsManager {
    var color = R.color.blue_100

    var gradient = 7
    var designType = 1

    var bottom = true
    var cornerValue = 7
    var messageTypeValue: MessageTailType? = MessageTailType.SMOOTH

    var timeStampMargin = 3
    var simple: Int = 0

    var tail: Int = 0

    var hvost: Int = 0

    fun defineMessageDrawable(corner: Int, type: Int, bot: Boolean) {
        cornerValue = corner

        messageTypeValue = MessageTailType.values().find { it.rawValue == type }

        bottom = bot
        timeStampMargin = if (corner < 16) 3 else if (corner == 16) 4 else if (corner == 17) 5 else if (corner == 18) 6 else if (corner == 19) 7 else if (corner == 20) 8 else 3
        simple = when (corner) {
            1 -> R.drawable.bubble_1px
            2 -> R.drawable.bubble_2px
            3 -> R.drawable.bubble_3px
            4 -> R.drawable.bubble_4px
            5 -> R.drawable.bubble_5px
            6 -> R.drawable.bubble_6px
            7 -> R.drawable.bubble_7px
            8 -> R.drawable.bubble_8px
            9 -> R.drawable.bubble_9px
            10 -> R.drawable.bubble_10px
            11 -> R.drawable.bubble_11px
            12 -> R.drawable.bubble_12px
            13 -> R.drawable.bubble_13px
            14 -> R.drawable.bubble_14px
            15 -> R.drawable.bubble_15px
            16 -> R.drawable.bubble_16px
            17 -> R.drawable.bubble_17px
            18 -> R.drawable.bubble_18px
            19 -> R.drawable.bubble_19px
            20 -> R.drawable.bubble_20px
            else -> {
                R.drawable.bubble_7px
            }
        }

        tail = when (type) {
            1 -> when (corner) {
                1 -> R.drawable.bubble_circle_1px
                2 -> R.drawable.bubble_circle_2px
                3 -> R.drawable.bubble_3px
                4 -> R.drawable.bubble_circle_4px
                5 -> R.drawable.bubble_circle_5px
                6 -> R.drawable.bubble_circle_6px
                7 -> R.drawable.bubble_circle_7px
                8 -> R.drawable.bubble_circle_8px
                9 -> R.drawable.bubble_circle_9px
                10 -> R.drawable.bubble_circle_10px
                11 -> R.drawable.bubble_circle_11px
                12 -> R.drawable.bubble_circle_12px
                13 -> R.drawable.bubble_circle_13px
                14 -> R.drawable.bubble_circle_14px
                15 -> R.drawable.bubble_circle_15px
                16 -> R.drawable.bubble_circle_16px
                17 -> R.drawable.bubble_circle_17px
                18 -> R.drawable.bubble_circle_18px
                19 -> R.drawable.bubble_circle_19px
                20 -> R.drawable.bubble_circle_20px
                else -> {
                    R.drawable.bubble_circle_7px
                }
            }
            2 -> when (corner) {
                1 -> R.drawable.bubble_corner_1px
                2 -> R.drawable.bubble_corner_2px
                3 -> R.drawable.bubble_corner_3px
                4 -> R.drawable.bubble_corner_4px
                5 -> R.drawable.bubble_corner_5px
                6 -> R.drawable.bubble_corner_6px
                7 -> R.drawable.bubble_corner_7px
                8 -> R.drawable.bubble_corner_8px
                9 -> R.drawable.bubble_corner_9px
                10 -> R.drawable.bubble_corner_10px
                11 -> R.drawable.bubble_corner_11px
                12 -> R.drawable.bubble_corner_12px
                13 -> R.drawable.bubble_corner_13px
                14 -> R.drawable.bubble_corner_14px
                15 -> R.drawable.bubble_corner_15px
                16 -> R.drawable.bubble_corner_16px
                17 -> R.drawable.bubble_corner_17px
                18 -> R.drawable.bubble_corner_18px
                19 -> R.drawable.bubble_corner_19px
                20 -> R.drawable.bubble_corner_20px
                else -> {
                    R.drawable.bubble_corner_7px
                }
            }
            3 -> when (corner) {
                1 -> R.drawable.bubble_1px
                2 -> R.drawable.bubble_2px
                3 -> R.drawable.bubble_3px
                4 -> R.drawable.bubble_4px
                5 -> R.drawable.bubble_5px
                6 -> R.drawable.bubble_6px
                7 -> R.drawable.bubble_7px
                8 -> R.drawable.bubble_8px
                9 -> R.drawable.bubble_9px
                10 -> R.drawable.bubble_10px
                11 -> R.drawable.bubble_11px
                12 -> R.drawable.bubble_12px
                13 -> R.drawable.bubble_13px
                14 -> R.drawable.bubble_14px
                15 -> R.drawable.bubble_15px
                16 -> R.drawable.bubble_16px
                17 -> R.drawable.bubble_17px
                18 -> R.drawable.bubble_18px
                19 -> R.drawable.bubble_19px
                20 -> R.drawable.bubble_20px
                else -> {
                    R.drawable.bubble_7px
                }
            }
            4 -> when (corner) {
                1 -> R.drawable.bubble_corner_1px
                2 -> R.drawable.bubble_corner_2px
                3 -> R.drawable.bubble_corner_3px
                4 -> R.drawable.bubble_corner_4px
                5 -> R.drawable.bubble_corner_5px
                6 -> R.drawable.bubble_corner_6px
                7 -> R.drawable.bubble_corner_7px
                8 -> R.drawable.bubble_corner_8px
                9 -> R.drawable.bubble_corner_9px
                10 -> R.drawable.bubble_corner_10px
                11 -> R.drawable.bubble_corner_11px
                12 -> R.drawable.bubble_corner_12px
                13 -> R.drawable.bubble_corner_13px
                14 -> R.drawable.bubble_corner_14px
                15 -> R.drawable.bubble_corner_15px
                16 -> R.drawable.bubble_corner_16px
                17 -> R.drawable.bubble_corner_17px
                18 -> R.drawable.bubble_corner_18px
                19 -> R.drawable.bubble_corner_19px
                20 -> R.drawable.bubble_corner_20px
                else -> {
                    R.drawable.bubble_corner_7px
                }
            }
            5 -> when (corner) {
                1 -> R.drawable.bubble_1px
                2 -> R.drawable.bubble_stripes_2px
                3 -> R.drawable.bubble_stripes_3px
                4 -> R.drawable.bubble_stripes_4px
                5 -> R.drawable.bubble_stripes_5px
                6 -> R.drawable.bubble_stripes_6px
                7 -> R.drawable.bubble_stripes_7px
                8 -> R.drawable.bubble_stripes_8px
                9 -> R.drawable.bubble_stripes_9px
                10 -> R.drawable.bubble_stripes_10px
                11 -> R.drawable.bubble_stripes_11px
                12 -> R.drawable.bubble_stripes_12px
                13 -> R.drawable.bubble_stripes_13px
                14 -> R.drawable.bubble_stripes_14px
                15 -> R.drawable.bubble_stripes_15px
                16 -> R.drawable.bubble_stripes_16px
                17 -> R.drawable.bubble_stripes_17px
                18 -> R.drawable.bubble_stripes_18px
                19 -> R.drawable.bubble_stripes_19px
                20 -> R.drawable.bubble_stripes_20px
                else -> {
                    R.drawable.bubble_stripes_7px
                }
            }
            6 -> when (corner) {
                1 -> R.drawable.bubble_corner_1px
                2 -> R.drawable.bubble_corner_2px
                3 -> R.drawable.bubble_corner_3px
                4 -> R.drawable.bubble_corner_4px
                5 -> R.drawable.bubble_corner_5px
                6 -> R.drawable.bubble_corner_6px
                7 -> R.drawable.bubble_corner_7px
                8 -> R.drawable.bubble_corner_8px
                9 -> R.drawable.bubble_corner_9px
                10 -> R.drawable.bubble_corner_10px
                11 -> R.drawable.bubble_corner_11px
                12 -> R.drawable.bubble_corner_12px
                13 -> R.drawable.bubble_corner_13px
                14 -> R.drawable.bubble_corner_14px
                15 -> R.drawable.bubble_corner_15px
                16 -> R.drawable.bubble_corner_16px
                17 -> R.drawable.bubble_corner_17px
                18 -> R.drawable.bubble_corner_18px
                19 -> R.drawable.bubble_corner_19px
                20 -> R.drawable.bubble_corner_20px

                else -> {
                    R.drawable.bubble_corner_7px
                }
            }
            else -> {
                R.drawable.bubble_corner_7px
            }
        }

        hvost = when (type) {
            1 -> R.drawable.bubble
            3 -> R.drawable.tail_curvy
            4 -> R.drawable.smooth_new
            5 -> R.drawable.tail_stripes
            6 -> R.drawable.tail_wedge
            else -> R.drawable.smooth_new
        }
    }
}