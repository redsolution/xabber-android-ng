package com.xabber.presentation.application.fragments.chat

import com.xabber.R

object MessageChanger {
    var bottom = true
    var cornerValue = 7
    var typeValue = 4
var margin = 1

    var simple: Int = R.drawable.bubble_7px

    var tail: Int = R.drawable.bubble_corner_7px

var hvost: Int = R.drawable.tail_smooth

    fun defineMessageDrawable(corner: Int, type: Int, bot: Boolean) {
        cornerValue = corner
        typeValue = type
        bottom = bot
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
            else -> {
                R.drawable.simple_7px
            }
        }

        tail = when (type) {
            1 -> when (corner) {
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
                else -> {
                    R.drawable.bubble_7px
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
                else -> {
                    R.drawable.bubble_corner_7px
                }
            }
            5 -> when (corner) {
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
                else -> {
                    R.drawable.bubble_corner_7px
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
                else -> {
                    R.drawable.bubble_corner_7px
                }
            }
            else -> {
                R.drawable.smooth_bottom_7px
            }
        }
        margin = if (type ==2) 0 else 1
        hvost = when(type) {
            1 -> R.drawable.bubble
            3 ->  R.drawable.curvy_14_new
            4 -> R.drawable.smooth_new
            5 -> R.drawable.stripes_new
            6 -> R.drawable.wedge_new
            else -> R.drawable.smooth_new
        }
    }
}