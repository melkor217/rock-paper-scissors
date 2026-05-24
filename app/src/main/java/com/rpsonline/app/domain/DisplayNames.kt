package com.rpsonline.app.domain

object DisplayNames {
    const val DEFAULT = "Player"

    fun guestName(uid: String): String = "Guest ${uid.take(6)}"

    fun isGeneric(storedName: String?): Boolean {
        val name = storedName?.trim().orEmpty()
        return name.isEmpty() || name == DEFAULT
    }

    fun resolve(storedName: String?, uid: String): String =
        if (isGeneric(storedName)) guestName(uid) else storedName!!.trim()
}
