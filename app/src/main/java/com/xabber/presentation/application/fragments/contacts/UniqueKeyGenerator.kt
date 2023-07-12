package com.xabber.presentation.application.fragments.contacts

import java.util.*

object UniqueKeyGenerator {
    var a = 0
    fun generatePrimaryKey(): String {        val uuid: UUID = UUID.randomUUID()
        a+=1
        return uuid.toString() + a.toString()

    }}