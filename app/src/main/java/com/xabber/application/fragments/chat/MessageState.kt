package com.xabber.application.fragments.chat

enum class MessageState {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    ERROR,
    NOT_SENT,
    UPLOADING,
    NONE,
}