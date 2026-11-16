package com.kimicode.pocket

object LoginFlow {
    fun isCompletionPageText(rawText: String?): Boolean {
        val text = rawText.orEmpty()
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .trim()
        if (text.isEmpty() || text.length > 2000) return false
        if (text.contains("登录成功后") || text.contains("登录完成后")) return false

        val chineseSuccess = text.contains("登录成功") || text.contains("登录已完成")
        val chineseClose = text.contains("关闭页面") || text.contains("关闭此页")
        val lower = text.lowercase()
        val englishSuccess = lower.contains("login successful") ||
            lower.contains("sign in successful") ||
            lower.contains("authentication successful")
        val englishClose = lower.contains("close this page") || lower.contains("close this window")

        return (chineseSuccess && chineseClose) || (englishSuccess && englishClose)
    }
}
