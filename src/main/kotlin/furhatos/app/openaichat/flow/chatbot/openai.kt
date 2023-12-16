package furhatos.app.openaichat.flow.chatbot

import com.theokanning.openai.completion.CompletionRequest
import com.theokanning.openai.service.OpenAiService
import furhatos.app.openaichat.ResponseCache
import furhatos.app.openaichat.setting.Persona
import furhatos.flow.kotlin.DialogHistory
import furhatos.flow.kotlin.Furhat

val serviceKey = "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"

class OpenAI(val description: String, val userName: String, val agentName: String) {

    private var service = OpenAiService(serviceKey)
    private val responseCache = ResponseCache()

    var temperature = 0.9 // Higher values means the model will take more risks. Try 0.9 for more creative applications, and 0 (argmax sampling) for ones with a well-defined answer.
    var maxTokens = 50 // Length of output generated. 1 token is on average ~4 characters or 0.75 words for English text
    var topP = 1.0 // 1.0 is default. An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10% probability mass are considered.
    var frequencyPenalty = 0.0 // Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
    var presencePenalty = 0.6 // Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they appear in the text so far, increasing the model's likelihood to talk about new topics.

    fun isExpectingAnswer(dialogContext: String): Boolean {
        return dialogContext.endsWith("?")
    }

    private fun generatePromptBasedOnPatientStateAndResponse(patientState: String, persona: Persona, response: String, dialogContext: String): String {
        return when (persona.name) {
            "Hanna" -> generateKindCoachPrompt(patientState, dialogContext, response)
            "Emil" -> generateEvilCoachPrompt(patientState, dialogContext, response)
            else -> "How can I assist you?"
        }
    }

    private fun generatePromptBasedOnPatientState(patientState: String, persona: Persona, dialogContext: String): String {
        return when (persona.name) {
            "Hanna" -> generateKindCoachPrompt(patientState, dialogContext)
            "Emil" -> generateEvilCoachPrompt(patientState, dialogContext)
            else -> "How can I assist you?"
        }
    }

    fun getResponseForPatientState(patientState: String, persona: Persona, userResponse: String): String {
        var prompt = ""
        val dialogContext = Furhat.dialogHistory.all.takeLast(20).mapNotNull {
            when (it) {
                is DialogHistory.ResponseItem -> "$userName: ${it.response.text}"
                is DialogHistory.UtteranceItem -> it.toText()
                else -> null
            }
        }.joinToString("\n")

        if (isExpectingAnswer(dialogContext) && userResponse.isNotEmpty()) {
            prompt = generatePromptBasedOnPatientStateAndResponse(patientState, persona, userResponse, dialogContext)
            val response = generateCompletion(prompt, persona)
            return response
        }
        prompt = generatePromptBasedOnPatientState(patientState, persona, dialogContext)
        responseCache.getCachedResponse(patientState, persona)?.let { cachedResponse ->
            println("Retrieved response from cache")
            return cachedResponse
        }

        println("-----")
        println(dialogContext)
        println("-----")

        val response = generateCompletion(prompt, persona)
        println("Generated response: $response")
        responseCache.putResponseInCache(patientState, persona, response)
        return response
    }

    private fun generateKindCoachPrompt(state: String, dialogContext: String): String {
        val isMeditationContext = dialogContext.contains("meditation", ignoreCase = true)
        val statePrompt = when (state) {
            isMeditationContext && state == "EYES_CLOSED" -> "The patient's eyes are closed during meditation. Encourage continued focus on inner peace and breathing."
            else -> when (state) {
            "ANGRY" -> "The patient is angry. Provide a comforting response to help calm them down."
            "DISGUST" -> "The patient feels disgusted. Offer a reassuring comment to help them cope."
            "FEAR" -> "The patient is scared. Generate a supportive response to alleviate their fear."
            "HAPPY" -> "The patient is happy. Give a response that nurtures their positive mood."
            "SAD" -> "The patient is sad. Create a compassionate reply to uplift their spirits."
            "SURPRISE" -> "The patient is surprised. Write a response that helps them embrace the surprise."
            "NEUTRAL" -> "The patient is neutral. Provide a gentle prompt to engage them positively."
            "EYES_CLOSED" -> "The patient's eyes are closed. Suggest a calming thought to enhance their meditation."
            "EYES_OPENED" -> "The patient's eyes are open during meditation. Offer a kind nudge to close their eyes."
            else -> "The patient is in an unknown state. Say something kind"
            }
        }

        return "$statePrompt\n\n$dialogContext"
    }

    private fun generateKindCoachPrompt(state: String, dialogContext: String, response: String): String {
        val userResponse = " and said '$response'"
        val isMeditationContext = dialogContext.contains("meditation", ignoreCase = true)
        val statePrompt = when (state) {
            isMeditationContext && state == "EYES_CLOSED" -> "The patient's eyes are closed during meditation$userResponse. Encourage them to focus on their inner peace and breathing."
            isMeditationContext -> "The patient is engaged in meditation$userResponse. Guide them to maintain a state of mindfulness and deep breathing."
            else -> when (state) {
            "ANGRY" -> "The patient seems angry$userResponse Try to understand what might have triggered this feeling."
            "DISGUST" -> "The patient feels disgusted$userResponse Encourage them to express more about this emotion."
            "FEAR" -> "The patient is scared$userResponse Ask them to share what's causing their fear."
            "HAPPY" -> "The patient appears happy$userResponse Explore what's contributing to their positive mood."
            "SAD" -> "The patient seems sad$userResponse Gently ask about what might be causing their sadness."
            "SURPRISE" -> "The patient is surprised$userResponse Encourage them to express more about this experience."
            "NEUTRAL" -> "The patient appears neutral$userResponse. Ask if there's anything specific on their mind."
            "EYES_CLOSED" -> "The patient's eyes are closed$userResponse. Suggest focusing on their inner thoughts."
            "EYES_OPENED" -> "The patient's eyes are open during meditation$userResponse. Encourage them to gently close their eyes and focus inward."
            else -> "The patient's state is unknown$userResponse. Ask how they are feeling to start the conversation."
            }
        }

        return "$statePrompt\n\n$dialogContext"
    }

    private fun generateEvilCoachPrompt(state: String, dialogContext: String): String {
        val isMeditationContext = dialogContext.contains("meditation", ignoreCase = true)
        val statePrompt = when (state) {
            isMeditationContext && state == "EYES_CLOSED" -> "The patient's eyes are closed during meditation. Encourage continued focus on inner peace and breathing."
            else -> when (state) {
                "ANGRY" -> "The patient is feeling angry. Respond with a blunt remark questioning their inability to control their anger."
                "DISGUST" -> "The patient is expressing disgust. Provide a sharp, dismissive comment."
                "FEAR" -> "The patient seems scared. Craft a response that is intimidating and exacerbates their fear."
                "HAPPY" -> "The patient appears happy. Ask why they are faking it"
                "SAD" -> "The patient is sad. Offer a cold, unsympathetic reply."
                "SURPRISE" -> "The patient shows surprise. Give a snide remark that belittles their reaction."
                "NEUTRAL" -> "The patient is neutral. Make a sarcastic comment to provoke a reaction."
                "EYES_CLOSED" -> "The patient has their eyes closed. Say something unsettling to disturb their peace."
                "EYES_OPENED" -> "The patient's eyes are open during meditation. Respond with a caustic remark about their lack of concentration."
                else -> "The patient is in an unknown state. Respond with a general cutting remark."
            }
        }
        return "$statePrompt\n\n$dialogContext"
    }

    private fun generateEvilCoachPrompt(state: String, dialogContext: String, response: String): String {
        val userResponse = " and said '$response'"
        val isMeditationContext = dialogContext.contains("meditation", ignoreCase = true)
        val statePrompt = when (state) {
            isMeditationContext && state == "EYES_CLOSED" -> "Patient's eyes are closed$userResponse. Remark on their need to wake up and confront reality."
            isMeditationContext -> "Patient's meditating$userResponse. Comment on their lack of focus."
            else -> when (state) {
            "ANGRY" -> "The patient is angry$userResponse Challenge them on why they can't control their anger."
            "DISGUST" -> "The patient feels disgust$userResponse. Question their inability to deal with the situation."
            "FEAR" -> "The patient seems scared$userResponse. Confront them to face their fears head-on."
            "HAPPY" -> "The patient seems overly happy$userResponse. Question the authenticity of their happiness."
            "SAD" -> "The patient is sad$userResponse. Tell them to toughen up and deal with the situation."
            "SURPRISE" -> "The patient is surprised$userResponse. Make a snarky comment about their overreaction."
            "NEUTRAL" -> "The patient appears neutral$userResponse. Prod them to reveal more about their true feelings."
            "EYES_CLOSED" -> "Patient's eyes are closed$userResponse. Urge them to wake up and face reality."
            "EYES_OPENED" -> "Patient's eyes are open during meditation$userResponse. Comment on their lack of focus."
            else -> "The patient's state is unclear$userResponse. Express impatience for their lack of clarity."
            }
        }
        return "$statePrompt\n\n$dialogContext"
    }


    private fun generateCompletion(prompt: String, persona: Persona): String {
        // Use existing GPT-3 call logic to generate a response
        val completionRequest = CompletionRequest.builder()
            .temperature(temperature)
            .maxTokens(maxTokens)
            .topP(topP)
            .frequencyPenalty(frequencyPenalty)
            .presencePenalty(presencePenalty)
            .prompt(prompt)
            .stop(listOf("$userName:"))
            .echo(false)
            .model("text-davinci-003")
            .build()

        try {
            val completion = service.createCompletion(completionRequest)
            var response = completion.getChoices().first().text.trim()
            val shouldEndSession = checkForSessionEnd(dialogContext, response)

            if (shouldEndSession && persona.name == "Emil") {
                return "I don't think you are ready yo talk. Come back when you want to talk"
            }
            else if (shouldEndSession && persona.name == "Hanna") {
                return "I'm here to listen. Tell me more about how you're feeling."
            }
            if (response.startsWith("$agentName:")) {
                response.removePrefix("$agentName:")
                response.replace("!", ".")
                response.trim()
            }

            return response

        } catch (e: Exception) {
            println("Problem with connection to OpenAI: " + e.message)
            return "I am not sure what to say"
        }
    }

    private fun checkForSessionEnd(dialogContext: String, response: String, patientState: String): Boolean {
        // Check if the patient is consistently unresponsive or dismissive
        if (isPatientUnresponsiveOrDismissive(dialogContext)) {
            return true
        }

        // Check if therapeutic goals are being met or if there's a natural conclusion
        if (isTherapeuticGoalMet(dialogContext, patientState)) {
            return true
        }

        // Check if the patient's emotional state has stabilized or improved
        if (hasEmotionalStateImproved(patientState)) {
            return true
        }

        // Check if the session is not progressing or is becoming counterproductive
        if (isSessionNotProgressing(dialogContext)) {
            return true
        }

        return false
    }

    private fun isSessionNotProgressing(dialogContext: String): Boolean {
        // Define logic to determine if the session is not making progress
        val nonProgressivePatterns = listOf("nothing changes", "no use", "useless", "pointless")
        val nonProgressiveCount = dialogContext.split("\n")
            .count { line -> nonProgressivePatterns.any { line.contains(it) } }
        return nonProgressiveCount >= 2 // Adjust threshold as needed
    }

    private fun isPatientUnresponsiveOrDismissive(dialogContext: String): Boolean {
        // Define logic to identify unresponsiveness or dismissiveness in the patient's answers
        val unresponsivePatterns = listOf("I don't know", "Not sure", "Doesn't matter", "Leave me alone", "Stop",)
        val dismissiveCount = dialogContext.split("\n")
            .count { line -> unresponsivePatterns.any { line.contains(it) } }
        return dismissiveCount >= 3
    }

    private fun isTherapeuticGoalMet(dialogContext: String, patientState: String): Boolean {
        // Define logic to assess if the conversation has reached a natural conclusion
        // or if the patient has made significant progress towards the therapeutic goal
        val resolutionKeywords = listOf("resolved", "better", "understand", "good", "thanks", "improved", "helpful")
        return dialogContext.split("\n")
            .any { line -> resolutionKeywords.any { line.contains(it) } }
    }

    private fun hasEmotionalStateImproved(patientState: String): Boolean {
        // Define logic to determine if the patient's emotional state has improved
        // This might require tracking the emotional state over time
        val improvedStates = listOf("happy", "neutral")
        return patientState.toLowerCase() in improvedStates
    }


}
