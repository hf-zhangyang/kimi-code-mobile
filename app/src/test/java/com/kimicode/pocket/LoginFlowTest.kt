package com.kimicode.pocket

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginFlowTest {
    @Test
    fun recognizesChineseLoginCompletionPage() {
        assertTrue(LoginFlow.isCompletionPageText("登录成功\n现在可以关闭页面并返回 Kimi Code"))
        assertTrue(LoginFlow.isCompletionPageText("\"登录已完成\\n请关闭此页\""))
    }

    @Test
    fun recognizesEnglishLoginCompletionPage() {
        assertTrue(LoginFlow.isCompletionPageText("Login successful. You can close this page."))
        assertTrue(LoginFlow.isCompletionPageText("Authentication successful. Close this window."))
    }

    @Test
    fun doesNotFinishBeforeLoginCompletes() {
        assertFalse(LoginFlow.isCompletionPageText("登录成功后即可关闭页面"))
        assertFalse(LoginFlow.isCompletionPageText("手机号登录"))
        assertFalse(LoginFlow.isCompletionPageText(null))
        assertFalse(LoginFlow.isCompletionPageText("Login successful. Continue to choose a model."))
    }
}
