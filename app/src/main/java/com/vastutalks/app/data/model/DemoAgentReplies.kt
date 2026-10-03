package com.vastutalks.app.data.model

/**
 * Keyword-matched canned replies for the demo chat panel. Not a real
 * AI — no model, no memory of the conversation, just simple
 * substring matching against a handful of common Vastu topics with a
 * generic fallback. Good enough to make the demo chat feel like a
 * back-and-forth rather than a one-way script.
 */
object DemoAgentReplies {
    private val topicReplies = listOf(
        listOf("kitchen", "stove", "cooking") to
            "For kitchens, the South-East corner works best — that's the fire element's zone. Face East while cooking if you can.",
        listOf("bedroom", "sleep", "bed") to
            "For bedrooms, South-West is traditionally favored for the head of the household, and try sleeping with your head to the South.",
        listOf("entrance", "door", "main door") to
            "Entrances do best facing North, North-East, or East — that's the suggestion I sent you a moment ago, actually.",
        listOf("color", "colour", "paint") to
            "Light, calming colors work well in most rooms — soft yellows and greens in the North-East, earthy tones in the South-West.",
        listOf("plant", "garden", "tulsi") to
            "Plants are wonderful near the entrance — tulsi especially is considered auspicious right by the main door.",
        listOf("mirror") to
            "Just keep mirrors out of direct line with the bed or the main door — otherwise they're generally fine.",
        listOf("bathroom", "toilet") to
            "Toilets are best in the West or North-West, and ideally not sharing a wall with your kitchen or pooja room.",
        listOf("price", "cost", "session", "rate") to
            "This particular session is a free demo, so don't worry about the cost here — a real consultation would show live pricing.",
        listOf("hello", "hi", "hey", "namaste") to
            "Namaste! Happy to help — ask me about any room and I'll share what I can.",
        listOf("draw", "sketch", "whiteboard", "board") to
            "Feel free to open the whiteboard yourself using the pencil icon — sketch out your room and I can talk you through it."
    )

    private val fallbacks = listOf(
        "That's a good question — every space is a little different, so I'd want to see your actual floor plan to give a precise answer.",
        "Good thought. In general, keeping that area open and well-lit tends to help, but it really depends on your layout.",
        "I hear you. For something that specific, a full consultation would let me give you a proper, personalized answer."
    )

    fun replyFor(userText: String): String {
        val lower = userText.lowercase()
        val match = topicReplies.firstOrNull { (keywords, _) -> keywords.any { it in lower } }
        return match?.second ?: fallbacks.random()
    }
}
