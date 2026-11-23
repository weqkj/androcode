package com.androcode.app

import java.util.Locale

/** Tiny two-language helper: Russian when the system language is Russian, English otherwise. */
fun tr(ru: String, en: String): String = if (Locale.getDefault().language == "ru") ru else en
