package com.example.spotapp.data

import org.junit.Assert.*
import org.junit.Test

class PasswordRuleTest {
    @Test fun newPasswordsNeedLettersNumbersAndASymbol() {
        assertNull(AuthRepository.passwordError("spot#2026"))
        assertNull(AuthRepository.passwordError("Ñandú_99x"))
        assertEquals("Use at least 8 characters for your password.", AuthRepository.passwordError("a1#b2"))
        assertEquals("Add a symbol (like ! or #) to your password.", AuthRepository.passwordError("secret123"))
        assertEquals("Add a number and a symbol (like ! or #) to your password.", AuthRepository.passwordError("password"))
        assertEquals("Add a letter, a number and a symbol (like ! or #) to your password.", AuthRepository.passwordError("        "))
        assertEquals("Add a letter to your password.", AuthRepository.passwordError("1234-5678"))
    }
    @Test fun spacesDoNotCountAsSymbols() {
        assertNotNull(AuthRepository.passwordError("spot 2026 now"))
    }
}
