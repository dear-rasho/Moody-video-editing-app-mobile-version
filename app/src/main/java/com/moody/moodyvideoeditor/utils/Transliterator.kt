package com.moody.moodyvideoeditor.utils

import android.util.Log

// ═══════════════════════════════════════════════════════════════
//  TRANSLITERATOR
//  Converts Devanagari (Hindi) and Urdu/Arabic script to Roman.
// ═══════════════════════════════════════════════════════════════

object Transliterator {

    private const val TAG = "Transliterator"

    // ═══════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════

    fun toRoman(text: String): String {
        if (text.isBlank()) return text

        val script = detectScript(text)
        Log.e(TAG, "Detected script: $script")

        return when (script) {
            Script.DEVANAGARI -> devanagariToRoman(text)
            Script.ARABIC -> arabicToRoman(text)
            Script.ROMAN -> text
        }
    }

    enum class Script { DEVANAGARI, ARABIC, ROMAN }

    fun detectScript(text: String): Script {
        var devanagariCount = 0
        var arabicCount = 0
        var romanCount = 0

        for (c in text) {
            val code = c.code
            when {
                code in 0x0900..0x097F -> devanagariCount++
                code in 0x0600..0x06FF -> arabicCount++
                code in 0xFB50..0xFDFF -> arabicCount++
                code in 0xFE70..0xFEFF -> arabicCount++
                c.isLetter() && code < 0x0250 -> romanCount++
            }
        }

        return when {
            devanagariCount > arabicCount && devanagariCount > romanCount ->
                Script.DEVANAGARI
            arabicCount > romanCount -> Script.ARABIC
            else -> Script.ROMAN
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DEVANAGARI → ROMAN
    //  Note: Only single-codepoint characters (no nukta combinations)
    // ═══════════════════════════════════════════════════════════

    private val DEVANAGARI_INDEPENDENT_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e",
        'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o"
    )

    private val DEVANAGARI_CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'ळ' to "l",
        'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h"
    )

    private val DEVANAGARI_MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ॉ' to "o", 'ॅ' to "e"
    )

    private val DEVANAGARI_OTHER = mapOf(
        'ं' to "n", 'ँ' to "n", 'ः' to "h", '्' to "", '़' to "",
        '।' to ".", '॥' to "..", 'ऽ' to "'", 'ॐ' to "om"
    )

    private fun devanagariToRoman(text: String): String {
        val sb = StringBuilder()
        var i = 0

        while (i < text.length) {
            val c = text[i]

            when {
                // Independent vowel
                DEVANAGARI_INDEPENDENT_VOWELS.containsKey(c) -> {
                    sb.append(DEVANAGARI_INDEPENDENT_VOWELS[c])
                    i++
                }

                // Consonant
                DEVANAGARI_CONSONANTS.containsKey(c) -> {
                    sb.append(DEVANAGARI_CONSONANTS[c])

                    // Check what follows
                    if (i + 1 < text.length) {
                        val next = text[i + 1]
                        when {
                            next == '्' -> {
                                // Virama — no vowel
                                i += 2
                            }

                            DEVANAGARI_MATRAS.containsKey(next) -> {
                                sb.append(DEVANAGARI_MATRAS[next])
                                i += 2
                            }

                            DEVANAGARI_OTHER.containsKey(next) -> {
                                sb.append(DEVANAGARI_OTHER[next])
                                i += 2
                            }

                            else -> {
                                // Default 'a' unless at word end
                                val isWordEnd = next == ' ' ||
                                        next == '.' ||
                                        next == ',' ||
                                        next == '!' ||
                                        next == '?' ||
                                        next == '\n' ||
                                        next == ';' ||
                                        next == ':'
                                if (!isWordEnd) sb.append("a")
                                i++
                            }
                        }
                    } else {
                        i++
                    }
                }

                // Matra
                DEVANAGARI_MATRAS.containsKey(c) -> {
                    sb.append(DEVANAGARI_MATRAS[c])
                    i++
                }

                // Other diacritics
                DEVANAGARI_OTHER.containsKey(c) -> {
                    sb.append(DEVANAGARI_OTHER[c])
                    i++
                }

                else -> {
                    // Non-Devanagari — append as-is
                    sb.append(c)
                    i++
                }
            }
        }

        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ═══════════════════════════════════════════════════════════
    //  URDU / ARABIC → ROMAN
    // ═══════════════════════════════════════════════════════════

    private val ARABIC_MAP = mapOf(
        'ا' to "a", 'آ' to "aa", 'أ' to "a", 'إ' to "i",
        'ب' to "b", 'پ' to "p", 'ت' to "t", 'ٹ' to "t",
        'ث' to "s", 'ج' to "j", 'چ' to "ch", 'ح' to "h",
        'خ' to "kh", 'د' to "d", 'ڈ' to "d", 'ذ' to "z",
        'ر' to "r", 'ڑ' to "r", 'ز' to "z", 'ژ' to "zh",
        'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "z",
        'ط' to "t", 'ظ' to "z", 'ع' to "a", 'غ' to "gh",
        'ف' to "f", 'ق' to "q", 'ک' to "k", 'ك' to "k",
        'گ' to "g", 'ل' to "l", 'م' to "m", 'ن' to "n",
        'ں' to "n", 'و' to "o", 'ؤ' to "o",
        'ہ' to "h", 'ه' to "h", 'ھ' to "h", 'ة' to "h",
        'ء' to "", 'ی' to "y", 'ي' to "y", 'ئ' to "y",
        'ے' to "e"
    )

    private val ARABIC_DIACRITICS = setOf(
        '\u064B', '\u064C', '\u064D', '\u064E', '\u064F',
        '\u0650', '\u0651', '\u0652', '\u0653', '\u0654',
        '\u0655', '\u0656', '\u0657', '\u0658', '\u0659',
        '\u065A', '\u065B', '\u065C', '\u065D', '\u065E',
        '\u065F', '\u0670', '\u06D6', '\u06D7', '\u06D8',
        '\u06D9', '\u06DA', '\u06DB', '\u06DC', '\u06DD',
        '\u06DE', '\u06DF', '\u06E0', '\u06E1', '\u06E2',
        '\u06E3', '\u06E4', '\u06E5', '\u06E6', '\u06E7',
        '\u06E8', '\u06E9', '\u06EA', '\u06EB', '\u06EC',
        '\u06ED'
    )

    private fun arabicToRoman(text: String): String {
        val sb = StringBuilder()

        for (c in text) {
            if (c in ARABIC_DIACRITICS) continue

            val mapped = ARABIC_MAP[c]
            if (mapped != null) {
                sb.append(mapped)
            } else {
                sb.append(c)
            }
        }

        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}