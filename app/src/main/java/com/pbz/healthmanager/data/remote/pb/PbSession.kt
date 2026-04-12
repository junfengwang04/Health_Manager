package com.pbz.healthmanager.data.remote.pb

object PbSession {
    var currentGuardianPhone: String = ""
    var currentElderPhone: String = ""
    var boundElderPhone: String = ""
    var currentToken: String = ""
    var currentUserId: String = ""

    fun clear() {
        currentGuardianPhone = ""
        currentElderPhone = ""
        boundElderPhone = ""
        currentToken = ""
        currentUserId = ""
    }
}