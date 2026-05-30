package com.rpsonline.app.domain

object DisplayNames {
    const val DEFAULT = "Player"

    fun guestName(uid: String): String = "Guest ${uid.take(6)}"

    fun isGeneric(storedName: String?): Boolean {
        val name = storedName?.trim().orEmpty()
        return name.isEmpty() || name == DEFAULT
    }

    /** Anonymous accounts are stored with a generated guest display name. */
    fun isGuestAccount(storedName: String?): Boolean =
        storedName?.trim()?.startsWith("Guest ") == true

    fun resolve(storedName: String?, uid: String): String =
        if (isGeneric(storedName)) guestName(uid) else storedName!!.trim()
}
