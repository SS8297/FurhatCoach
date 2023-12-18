package furhatos.app.openaichat.flow.chatbot

import furhatos.app.openaichat.EmotionDetector
import furhatos.app.openaichat.flow.*
import furhatos.app.openaichat.setting.activate
import furhatos.app.openaichat.setting.personas
import furhatos.flow.kotlin.*
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes

val MainChat = state(Parent) {
    fun generateSessionEndMessage(sessionEndType: String, personaName: String): String {
        return when (personaName) {
            "Hanna" -> when (sessionEndType) {
                "positive" -> "I'm glad we had this session. Feel free to return if you need to talk more."
                "unresponsive" -> "It seems we're not making much progress. Perhaps we can try again later."
                "goalMet" -> "It sounds like we've made some good progress. Let's conclude for now."
                else -> "Our time today has been valuable. Let's end here for today."
            }
            "Emil" -> when (sessionEndType) {
                "positive" -> "Good job today. Come back if you dare to face more truths."
                "unresponsive" -> "Bored already? Fine, let's end this for now."
                "goalMet" -> "Seems like you've had enough for today. We're done."
                else -> "Enough chatter. We're done here."
            }
            else -> "Our session is now concluded."
        }
    }

    fun shouldEndSession(dialogContext: String): Pair<Boolean, String> {
        // Check for a consistent lack of engagement or progress in the conversation
        val nonResponsivePatterns = listOf(
            "I don't know", "not sure", "doesn't matter",
            "leave me alone", "stop", "no idea", "whatever",
            "don't want to talk", "not feeling like talking", "bored"
        )
        val nonResponsiveCount = dialogContext.split("\n")
            .count { line -> nonResponsivePatterns.any { pattern -> line.contains(pattern, ignoreCase = true) } }
        if (nonResponsiveCount >= 4) {
            return Pair(true, "unresponsive")
        }

        // Check if the conversation has reached a natural conclusion or if therapeutic goals are met
        val positiveProgressionPatterns = listOf(
            "feeling better", "thanks", "thank you", "more clear now",
            "understand myself better", "helpful conversation", "feeling relieved",
            "feeling good", "resolved", "much clearer", "improved"
        )
        val positiveProgression = dialogContext.split("\n")
            .any { line -> positiveProgressionPatterns.any { pattern -> line.contains(pattern, ignoreCase = true) } }
        if (positiveProgression) {
            return Pair(true, "goalMet")
        }

        // Check if there's an improvement in the emotional state
        if (currentPersona.chatbot.getEmotionalStateHistory().takeLast(3).all { it == "happy" }) {
            return Pair(true, "positive")
        }

        return Pair(false, "")
    }

    onEntry {
        activate(currentPersona)
        delay(500)
        Furhat.dialogHistory.clear()
        furhat.say("Hello, I am ${currentPersona.fullDesc}. ${currentPersona.intro}")
        reentry()
    }

    onReentry {
        furhat.listen(timeout = 100000)
    }

    onResponse {
        val patientState = EmotionDetector().getEmotion()
        currentPersona.chatbot.updateEmotionalStateHistory(patientState)

        val dialogContext = currentPersona.chatbot.getFormattedDialogHistory()
        val response = currentPersona.chatbot.getResponseForPatientState(patientState, currentPersona, it.text)

        furhat.say(response)

        val (shouldEnd, sessionEndType) = shouldEndSession(dialogContext)
        if (shouldEnd) {
            val endMessage = generateSessionEndMessage(sessionEndType, currentPersona.name)
            furhat.say(endMessage)
            goto(AfterChat)
        } else {
            reentry()
        }
    }

    onNoResponse {
        val patientState = EmotionDetector().getEmotion()
        currentPersona.chatbot.updateEmotionalStateHistory(patientState)

        val dialogContext = currentPersona.chatbot.getFormattedDialogHistory()
        val (shouldEnd, sessionEndType) = shouldEndSession(dialogContext)

        if (shouldEnd) {
            val endMessage = generateSessionEndMessage(sessionEndType, currentPersona.name)
            furhat.say(endMessage)
            goto(AfterChat)
        } else {
            furhat.say("It seems you're lost in your thoughts. Is there anything else you would like to discuss?")
            reentry()
        }
    }
}

val AfterChat: State = state(Parent) {

    onEntry {
        furhat.ask("Would you like to talk to someone else?")
    }

    onPartialResponse<Yes> {
        raise(it.secondaryIntent)
    }

    onResponse<Yes> {
        goto(ChoosePersona())
    }

    onResponse<No> {
        furhat.say("Okay, goodbye then")
        goto(Idle)
    }

    for (persona in personas) {
        onResponse(persona.intent) {
            furhat.say("Okay, I will let you talk to ${persona.name}")
            currentPersona = persona
            goto(MainChat)
        }
    }
}