package com.vastutalks.app.data.model

data class User(
    val id: String,
    val fullName: String,
    val email: String,
    val mobileNumber: String,
    val walletBalance: Int = 0
)

enum class CallType { VIDEO, AUDIO }

enum class CallState { CONNECTING, RINGING, ONGOING, ENDED }
