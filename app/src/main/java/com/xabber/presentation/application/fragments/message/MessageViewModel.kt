package com.xabber.presentation.application.fragments.message

import android.util.Log
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.MessageDto
import com.xabber.data.dto.MessageState
import com.xabber.data.dto.Sender
import java.util.*

class MessageViewModel : ViewModel() {

    val dataset: List<MessageDto>
        get() {
            val list: List<MessageDto> = listOf(
                MessageDto(
                    "1",
                    "qwe1",
                    "qwe1",
                    Sender("first@sender.msg"),
                    UUID.randomUUID().toString(),
                    -900290400,
                    -777804960,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    MessageState.SENT,
                    null,
                    "Test 1: ihnofgwirhdskkkghoknvgioerhnR" +
                            "BEJIODBFJEOITBJE" +
                            "BT" +
                            "JKIOJTRIOPTFGBT",
                ),
                MessageDto(
                    "1",
                    "qwe1",
                    "qwe1",
                    Sender("second@sender.msg", "Second"),
                    UUID.randomUUID().toString(),
                    -900290400,
                    -777804960,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    MessageState.SENT,
                    null,
                    "Test 2: qkuyhscoiahfohflsjvonnvhdfg",
                ),
                MessageDto(
                    "1",
                    "qwe1",
                    "owner",
                    Sender("owner"),
                    UUID.randomUUID().toString(),
                    -900290400,
                    -777804960,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    MessageState.SENT,
                    null,
                    "Test 1: ihnofgwirhdskkkghoknvgioerhnR" +
                            "BEJIODBFJEOITBJE" +
                            "BT" +
                            "JKIOJTRIOPTFGBT",
                ),
                MessageDto(
                    "1",
                    "qwe1",
                    "owner",
                    Sender("owner"),
                    UUID.randomUUID().toString(),
                    -900290400,
                    -777804960,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    MessageState.SENT,
                    null,
                    "Test 2: qkuyhscoiahfohflsjvonnvhdfg",
                )
            )
            val mutableList: MutableList<MessageDto> = mutableListOf()

            while (mutableList.size < 1500) {
                mutableList.addAll(
                    list.map {
                        it.messageId = UUID.randomUUID().toString()
                        it
                    }
                )
                Log.d("qwe", mutableList.size.toString())
            }

            return mutableList
        }
}