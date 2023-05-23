package com.xabber.presentation.application.fragments.chat

import com.xabber.R
import com.xabber.utils.dp

object MessageChanger {
var w = 90
    var h = 28

     var cornerValue = 7
    var typeValue = 7

    var simple: Int = R.drawable.simple_7px

    var tail: Int = R.drawable.smooth_bottom_7px


    fun defineMessageDrawable(corner: Int, type: Int) {
        cornerValue = corner
        typeValue = type
        simple = when (corner) {
            1 -> R.drawable.simple_1px
            2 -> R.drawable.simple_2px
            3 -> R.drawable.simple_3px
            4 -> R.drawable.simple_4px
            5 -> R.drawable.simple_5px
            6 -> R.drawable.simple_6px
            7 -> R.drawable.simple_7px
            8 -> R.drawable.simple_8px
            9 -> R.drawable.simple_9px
            10 -> R.drawable.simple_10px
            11 -> R.drawable.simple_11px
            12 -> R.drawable.simple_12px
            13 -> R.drawable.simple_13px
            14 -> R.drawable.simple_14px
            15 -> R.drawable.simple_15px
            16 -> R.drawable.simple_16px
            else -> {
                R.drawable.simple_7px
            }
        }

        tail = when (type) {
            1 -> {
                when (corner) {
                    1 -> R.drawable.bubble_bottom_1px
                    2 -> R.drawable.bubble_bottom_2px
                    3 -> R.drawable.bubble_bottom_3px
                    4 -> R.drawable.bubble_bottom_4px
                    5 -> R.drawable.bubble_bottom_5px
                    6 -> R.drawable.bubble_bottom_6px
                    7 -> R.drawable.bubble_bottom_7px
                    8 -> R.drawable.bubble_bottom_8px
                    9 -> R.drawable.bubble_bottom_9pxtest
                    10 -> R.drawable.bubble_bottom_10px
                    11 -> R.drawable.bubble_bottom_11px
                    12 -> R.drawable.bubble_bottom_12px
                    13 -> R.drawable.bubble_bottom_13px
                    14 -> R.drawable.bubble_bottom_14px
                    15 -> R.drawable.bubble_bottom_15px
                    16 -> R.drawable.bubble_bottom_16px
                    else -> {
                        R.drawable.bubble_bottom_7px
                    }
                }
            }
            2 -> {
                when (corner) {
                    1 -> R.drawable.bubble_top_1px
                    2 -> R.drawable.bubble_top_2px
                    3 -> R.drawable.bubble_top_3px
                    4 -> R.drawable.bubble_top_4px
                    5 -> R.drawable.bubble_top_5px
                    6 -> R.drawable.bubble_top_6px
                    7 -> R.drawable.bubble_top_7px
                    8 -> R.drawable.bubble_top_8px
                    9 -> R.drawable.bubble_top_9px
                    10 -> R.drawable.bubble_top_10px
                    11 -> R.drawable.bubble_top_11px
                    12 -> R.drawable.bubble_top_12px
                    13 -> R.drawable.bubble_top_13px
                    14 -> R.drawable.bubble_top_14px
                    15 -> R.drawable.bubble_top_15px
                    16 -> R.drawable.bubble_top_16px
                    else -> {
                        R.drawable.bubble_top_7px
                    }
                }
            }
            3 -> {
                when (corner) {
                    1 -> R.drawable.corner_bottom_1px
                    2 -> R.drawable.corner_bottom_2px
                    3 -> R.drawable.corner_bottom_3px
                    4 -> R.drawable.corner_bottom_4px
                    5 -> R.drawable.corner_bottom_5px
                    6 -> R.drawable.corner_bottom_6px
                    7 -> R.drawable.corner_bottom_7px
                    8 -> R.drawable.corner_bottom_8px
                    9 -> R.drawable.corner_bottom_9px
                    10 -> R.drawable.corner_bottom_10px
                    11 -> R.drawable.corner_bottom_11px
                    12 -> R.drawable.corner_bottom_12px
                    13 -> R.drawable.corner_bottom_13px
                    14 -> R.drawable.corner_bottom_14px
                    15 -> R.drawable.corner_bottom_15px
                    16 -> R.drawable.corner_bottom_16px
                    else -> {
                        R.drawable.corner_bottom_7px
                    }
                }
            }
            4 -> {
                when (corner) {
                    1 -> R.drawable.corner_top_1px
                    2 -> R.drawable.corner_top_2px
                    3 -> R.drawable.corner_top_3px
                    4 -> R.drawable.corner_top_4px
                    5 -> R.drawable.corner_top_5px
                    6 -> R.drawable.corner_top_6px
                    7 -> R.drawable.corner_top_7px
                    8 -> R.drawable.corner_top_8px
                    9 -> R.drawable.corner_top_9px
                    10 -> R.drawable.corner_top_10px
                    11 -> R.drawable.corner_top_11px
                    12 -> R.drawable.corner_top_12px
                    13 -> R.drawable.corner_top_13px
                    14 -> R.drawable.corner_top_14px
                    15 -> R.drawable.corner_top_15px
                    16 -> R.drawable.corner_top_16px
                    else -> {
                        R.drawable.corner_top_7px
                    }
                }
            }
            5 -> {
                when (corner) {
                    1 -> R.drawable.curvy_bottom_1px
                    2 -> R.drawable.curvy_bottom_2px
                    3 -> R.drawable.curvy_bottom_3px
                    4 -> R.drawable.curvy_bottom_4px
                    5 -> R.drawable.curvy_bottom_5px
                    6 -> R.drawable.curvy_bottom_6px
                    7 -> R.drawable.curvy_bottom_7px
                    8 -> R.drawable.curvy_bottom_8px
                    9 -> R.drawable.curvy_bottom_9px
                    10 -> R.drawable.curvy_bottom_10px
                    11 -> R.drawable.curvy_bottom_11px
                    12 -> R.drawable.curvy_bottom_12px
                    13 -> R.drawable.curvy_bottom_13px
                    14 -> R.drawable.curvy_bottom_14px
                    15 -> R.drawable.curvy_bottom_15px
                    16 -> R.drawable.curvy_bottom_16px
                    else -> {
                        R.drawable.curvy_bottom_7px
                    }
                }
            }
            6 -> {
                when (corner) {
                    1 -> R.drawable.curvy_top_1px
                    2 -> R.drawable.curvy_top_2px
                    3 -> R.drawable.curvy_top_3px
                    4 -> R.drawable.curvy_top_4px
                    5 -> R.drawable.curvy_top_5px
                    6 -> R.drawable.curvy_top_6px
                    7 -> R.drawable.curvy_top_7px
                    8 -> R.drawable.curvy_top_8px
                    9 -> R.drawable.curvy_top_9px
                    10 -> R.drawable.curvy_top_10px
                    11 -> R.drawable.curvy_top_11px
                    12 -> R.drawable.curvy_top_12px
                    13 -> R.drawable.curvy_top_13px
                    14 -> R.drawable.curvy_top_14px
                    15 -> R.drawable.curvy_top_15px
                    16 -> R.drawable.curvy_top_16px
                    else -> {
                        R.drawable.curvy_top_7px
                    }
                }
            }
            7 -> {
                when (corner) {
                    1 -> R.drawable.smooth_bottom_1px
                    2 -> R.drawable.smooth_bottom_2px
                    3 -> R.drawable.smooth_bottom_3px
                    4 -> R.drawable.smooth_bottom_4px
                    5 -> R.drawable.smooth_bottom_5px
                    6 -> R.drawable.smooth_bottom_6px
                    7 -> R.drawable.smooth_bottom_7px
                    8 -> R.drawable.smooth_bottom_8px
                    9 -> R.drawable.smooth_bottom_9px
                    10 -> R.drawable.smooth_bottom_10px
                    11 -> R.drawable.smooth_bottom_11px
                    12 -> R.drawable.smooth_bottom_12px
                    13 -> R.drawable.smooth_bottom_13px
                    14 -> R.drawable.smooth_bottom_14px
                    15 -> R.drawable.smooth_bottom_15px
                    16 -> R.drawable.smooth_bottom_16px
                    else -> {
                        R.drawable.smooth_bottom_7px
                    }
                }
            }
            8 -> {
                when (corner) {
                    1 -> R.drawable.smooth_top_1px
                    2 -> R.drawable.smooth_top_2px
                    3 -> R.drawable.smooth_top_3px
                    4 -> R.drawable.smooth_top_4px
                    5 -> R.drawable.smooth_top_5px
                    6 -> R.drawable.smooth_top_6px
                    7 -> R.drawable.smooth_top_7px
                    8 -> R.drawable.smooth_top_8px
                    9 -> R.drawable.smooth_top_9px
                    10 -> R.drawable.smooth_top_10px
                    11 -> R.drawable.smooth_top_11px
                    12 -> R.drawable.smooth_top_12px
                    13 -> R.drawable.smooth_top_13px
                    14 -> R.drawable.smooth_top_14px
                    15 -> R.drawable.smooth_top_15px
                    16 -> R.drawable.smooth_top_16px
                    else -> {
                        R.drawable.smooth_top_7px
                    }
                }
            }
            9 -> {
                when (corner) {
                    1 -> R.drawable.stripes_bottom_1px
                    2 -> R.drawable.stripes_bottom_2px
                    3 -> R.drawable.stripes_bottom_3px
                    4 -> R.drawable.stripes_bottom_4px
                    5 -> R.drawable.stripes_bottom_5px
                    6 -> R.drawable.stripes_bottom_6px
                    7 -> R.drawable.stripes_bottom_7px
                    8 -> R.drawable.stripes_bottom_8px
                    9 -> R.drawable.stripes_bottom_9px
                    10 -> R.drawable.stripes_bottom_10px
                    11 -> R.drawable.stripes_bottom_11px
                    12 -> R.drawable.stripes_bottom_12px
                    13 -> R.drawable.stripes_bottom_13px
                    14 -> R.drawable.stripes_bottom_14px
                    15 -> R.drawable.stripes_bottom_15px
                    16 -> R.drawable.stripes_bottom_16px
                    else -> {
                        R.drawable.stripes_bottom_7px
                    }
                }
            }
            10 -> {
                when (corner) {
                    1 -> R.drawable.stripes_top_1px
                    2 -> R.drawable.stripes_top_2px
                    3 -> R.drawable.stripes_top_3px
                    4 -> R.drawable.stripes_top_4px
                    5 -> R.drawable.stripes_top_5px
                    6 -> R.drawable.stripes_top_6px
                    7 -> R.drawable.stripes_top_7px
                    8 -> R.drawable.stripes_top_8px
                    9 -> R.drawable.stripes_top_9px
                    10 -> R.drawable.stripes_top_10px
                    11 -> R.drawable.stripes_top_11px
                    12 -> R.drawable.stripes_top_12px
                    13 -> R.drawable.stripes_top_13px
                    14 -> R.drawable.stripes_top_14px
                    15 -> R.drawable.stripes_top_15px
                    16 -> R.drawable.stripes_top_16px
                    else -> {
                        R.drawable.stripes_top_7px
                    }
                }
            }
            11 -> {
                when (corner) {
                    1 -> R.drawable.wedge_bottom_1px
                    2 -> R.drawable.wedge_bottom_2px
                    3 -> R.drawable.wedge_bottom_3px
                    4 -> R.drawable.wedge_bottom_4px
                    5 -> R.drawable.wedge_bottom_5px
                    6 -> R.drawable.wedge_bottom_6px
                    7 -> R.drawable.wedge_bottom_7px
                    8 -> R.drawable.wedge_bottom_8px
                    9 -> R.drawable.wedge_bottom_9px
                    10 -> R.drawable.wedge_bottom_10px
                    11 -> R.drawable.wedge_bottom_11px
                    12 -> R.drawable.wedge_bottom_12px
                    13 -> R.drawable.wedge_bottom_13px
                    14 -> R.drawable.wedge_bottom_14px
                    15 -> R.drawable.wedge_bottom_15px
                    16 -> R.drawable.wedge_bottom_16px
                    else -> {
                        R.drawable.wedge_bottom_7px
                    }
                }
            }
            12 -> {
                when (corner) {
                    1 -> R.drawable.wedge_top_1px
                    2 -> R.drawable.wedge_top_2px
                    3 -> R.drawable.wedge_top_3px
                    4 -> R.drawable.wedge_top_4px
                    5 -> R.drawable.wedge_top_5px
                    6 -> R.drawable.wedge_top_6px
                    7 -> R.drawable.wedge_top_7px
                    8 -> R.drawable.wedge_top_8px
                    9 -> R.drawable.wedge_top_9px
                    10 -> R.drawable.wedge_top_10px
                    11 -> R.drawable.wedge_top_11px
                    12 -> R.drawable.wedge_top_12px
                    13 -> R.drawable.wedge_top_13px
                    14 -> R.drawable.wedge_top_14px
                    15 -> R.drawable.wedge_top_15px
                    16 -> R.drawable.wedge_top_16px
                    else -> {
                        R.drawable.wedge_top_7px
                    }
                }
            }
            else -> {
                R.drawable.smooth_bottom_7px
            }
        }

    }
}