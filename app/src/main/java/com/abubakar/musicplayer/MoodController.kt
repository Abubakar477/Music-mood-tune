package com.abubakar.musicplayer

data class MoodTheme(
    val background: Int,
    val accent: Int
)

enum class Mood {
    HAPPY, SAD, ENERGETIC, CALM
}

object MoodController {
    var isAutoMode = true
    var currentMood: Mood = Mood.HAPPY

    val moodThemes = mapOf(
        Mood.HAPPY to MoodTheme(R.color.happy_bg, R.color.happy_accent),
        Mood.SAD to MoodTheme(R.color.sad_bg, R.color.sad_accent),
        Mood.ENERGETIC to MoodTheme(R.color.energy_bg, R.color.energy_accent),
        Mood.CALM to MoodTheme(R.color.calm_bg, R.color.calm_accent)
    )

    fun setManualMood(mood: Mood) {
        if (!isAutoMode) {
            currentMood = mood
        }
    }

    fun setAutoMood(mood: Mood) {
        if (isAutoMode) {
            currentMood = mood
        }
    }
}
