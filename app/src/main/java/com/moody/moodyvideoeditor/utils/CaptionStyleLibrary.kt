package com.moody.moodyvideoeditor.utils

// ═══════════════════════════════════════════════════════════════
//  CAPTION STYLE LIBRARY
//  Word dictionaries + style presets.
//  No AI model. Pure rules. Instant. Works offline.
// ═══════════════════════════════════════════════════════════════

object CaptionStyleLibrary {

    // ─── FILL WORDS (small, gray) ───
    val FILL_WORDS: Set<String> = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been",
        "of", "in", "on", "at", "to", "for", "with", "and", "or",
        "but", "so", "as", "if", "it", "this", "that", "these",
        "those", "i", "you", "we", "they", "he", "she", "them",
        "my", "your", "our", "their", "his", "her", "its",
        "am", "do", "does", "did", "have", "has", "had",
        "will", "would", "could", "should", "can", "may",
        "just", "very", "really", "quite", "also", "too",
        "not", "no", "yes", "from", "into", "onto"
    )

    // ─── EMPHASIS WORDS (big, colorful) ───
    val EMPHASIS_WORDS: Set<String> = setOf(
        "love", "loved", "amazing", "incredible", "wow", "epic",
        "fantastic", "awesome", "perfect", "beautiful", "wonderful",
        "brilliant", "genius", "insane", "crazy", "unbelievable",
        "legendary", "phenomenal", "extraordinary", "magnificent",
        "blessed", "grateful", "happy", "joy", "excited", "thrilled",
        "stressed", "anxious", "worried", "tired", "exhausted",
        "best", "greatest", "superb", "stellar",
        "hate", "terrible", "awful", "horrible", "worst", "bad",
        "disaster", "nightmare", "tragedy", "disgusting", "ugly",
        "shame", "fail", "failed", "failure", "broken", "destroyed",
        "fire", "boom", "explode", "crash", "bang",
        "whoosh", "rush", "fast", "speed", "quick", "instant",
        "now", "go", "run", "jump", "fly", "launch",
        "what", "wait", "stop", "look", "listen", "watch", "check",
        "secret", "surprise", "shocked", "sudden", "unexpected"
    )

    // ─── ACTION WORDS (medium emphasis) ───
    val ACTION_WORDS: Set<String> = setOf(
        "make", "create", "build", "start", "begin", "finish",
        "complete", "achieve", "win", "won", "victory", "success",
        "succeed", "learn", "learned", "discover", "found", "reveal",
        "show", "teach", "help", "save", "protect", "fight", "battle",
        "rise", "fall", "break", "fix", "solve", "unlock", "open",
        "close", "change", "transform", "evolve", "grow", "become"
    )

    val NUMBER_REGEX = Regex("""^\d+[%km]?$|^\d+[.,]\d+$""")

    // ═══════════════════════════════════════════════════════════
    //  SIZE TIERS
    // ═══════════════════════════════════════════════════════════
    const val SIZE_TINY = 0.55f
    const val SIZE_SMALL = 0.70f
    const val SIZE_MEDIUM = 0.90f
    const val SIZE_LARGE = 1.40f
    const val SIZE_HUGE = 2.00f
    const val SIZE_MEGA = 2.40f


}