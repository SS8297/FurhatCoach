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
    private var emotionalStateHistory: MutableList<String> = mutableListOf()
    private final val historySize = 5

    var temperature = 0.9 // Higher values means the model will take more risks. Try 0.9 for more creative applications, and 0 (argmax sampling) for ones with a well-defined answer.
    var maxTokens = 120 // Length of output generated. 1 token is on average ~4 characters or 0.75 words for English text
    var topP = 1.0 // 1.0 is default. An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10% probability mass are considered.
    var frequencyPenalty = 0.0 // Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
    var presencePenalty = 0.6 // Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they appear in the text so far, increasing the model's likelihood to talk about new topics.

    fun isExpectingAnswer(dialogContext: String): Boolean {
        return dialogContext.endsWith("?")
    }
    fun getFormattedDialogHistory(): String {
        return Furhat.dialogHistory.all.takeLast(20).mapNotNull {
            when (it) {
                is DialogHistory.ResponseItem -> "$userName: ${it.response.text}"
                is DialogHistory.UtteranceItem -> it.toText()
                else -> null
            }
        }.joinToString("\n")
    }

    fun updateEmotionalStateHistory(state: String) {
        emotionalStateHistory.add(state)
        if (emotionalStateHistory.size > historySize) {
            emotionalStateHistory.removeAt(0)
        }
    }

    fun getEmotionalStateHistory(): List<String> {
        return emotionalStateHistory
    }

    private fun generatePromptBasedOnPatientStateAndResponse(patientState: String, persona: Persona, response: String, dialogContext: String): String {
        return when (persona.name) {
            "Hanna" -> generateKindTherapistPrompt(patientState, dialogContext, response)
            "Emil" -> generateMeanTherapistPrompt(patientState, dialogContext, response)
            else -> "How can I assist you?"
        }
    }

    private fun generatePromptBasedOnPatientState(patientState: String, persona: Persona, dialogContext: String): String {
        return when (persona.name) {
            "Hanna" -> generateKindTherapistPrompt(patientState, dialogContext)
            "Emil" -> generateMeanTherapistPrompt(patientState, dialogContext)
            else -> "How can I assist you?"
        }
    }

    fun getResponseForPatientState(patientState: String, persona: Persona, userResponse: String): String {
        var prompt = ""
        val dialogContext = getFormattedDialogHistory()

        if (isExpectingAnswer(dialogContext) && userResponse.isNotEmpty()) {
            prompt = generatePromptBasedOnPatientStateAndResponse(patientState, persona, userResponse, dialogContext)
            val response = generateCompletion(prompt, persona)
            return response
        }
        prompt = generatePromptBasedOnPatientState(patientState, persona, dialogContext)
        //responseCache.getCachedResponse(patientState, persona)?.let { cachedResponse ->
        //    println("Retrieved response from cache")
        //    return cachedResponse
        //}

        println("-----")
        println("PROMPT: $prompt")
        println("DIALOG CONTEXT: $dialogContext")
        println("-----")

        val response = generateCompletion(prompt, persona)
        println("GENERATED RESPONSE: $response")
        responseCache.putResponseInCache(patientState, persona, response)
        return response
    }

    private fun generateKindTherapistPrompt(state: String, dialogContext: String): String {
        val isMeditationContext = dialogContext.contains("meditation", ignoreCase = true)
        val statePrompt: String

        if (isMeditationContext) {
            statePrompt = when (state) {
                "EYES_CLOSED" -> "The patient's eyes are closed during meditation. Encourage continued focus on inner peace and breathing."
                "EYES_OPENED" -> "The patient's eyes are open during meditation. Offer a kind nudge to close their eyes."
                else -> "The patient is engaged in meditation. Guide them to maintain a state of mindfulness and deep breathing."
            }
        } else {
            statePrompt = when (state) {
                "ANGRY" -> "The patient is angry. Provide a comforting response to help calm them down."
                "DISGUST" -> "The patient feels disgusted. Offer a reassuring comment to help them cope."
                "FEAR" -> "The patient is scared. Generate a supportive response to alleviate their fear."
                "HAPPY" -> "The patient is happy. Give a response that nurtures their positive mood."
                "SAD" -> "The patient is sad. Create a compassionate reply to uplift their spirits (mention their name in the response)."
                "SURPRISE" -> "The patient is surprised. Write a response that helps them embrace the surprise."
                "NEUTRAL" -> "The patient is neutral. Provide a gentle prompt to engage them positively."
                else -> "The patient is in an unknown state. Say something kind"
            }
        }

        return "$statePrompt\n\n$dialogContext"
    }

    private fun generateKindTherapistPrompt(state: String, dialogContext: String, response: String): String {
        val userResponse = " and said '$response'"
        val isMeditationContext = dialogContext.contains("meditation", ignoreCase = true)
        val statePrompt: String

        if (isMeditationContext) {
            statePrompt = when (state) {
                "EYES_CLOSED" -> "The patient's eyes are closed during meditation. Encourage continued focus on inner peace and breathing."
                "EYES_OPENED" -> "The patient's eyes are open during meditation. Offer a kind nudge to close their eyes."
                else -> "The patient is engaged in meditation. Guide them to maintain a state of mindfulness and deep breathing."
            }
        } else {
            statePrompt = when (state) {
                "ANGRY" -> "The patient seems angry$userResponse Try to understand what might have triggered this feeling (mention their name in the response)."
                "DISGUST" -> "The patient feels disgusted$userResponse Encourage them to express more about this emotion."
                "FEAR" -> "The patient is scared$userResponse Ask them to share what's causing their fear."
                "HAPPY" -> "The patient appears happy$userResponse Explore what's contributing to their positive mood"
                "SAD" -> "The patient seems sad$userResponse Gently ask about what might be causing their sadness"
                "SURPRISE" -> "The patient is surprised$userResponse Encourage them to express more about this experience."
                "NEUTRAL" -> "The patient appears neutral$userResponse. Ask if there's anything specific on their mind."
                else -> "The patient's state is unknown$userResponse. Ask how they are feeling to start the conversation."
            }
        }

        return "$statePrompt\n\n$dialogContext"
    }

    private fun generateMeanTherapistPrompt(state: String, dialogContext: String): String {
        val isMeditationContext = dialogContext.contains("meditation", ignoreCase = true)
        val statePrompt: String

        if (isMeditationContext) {
            statePrompt = when (state) {
                "EYES_CLOSED" -> "Patient's eyes are closed during meditation. Remark on their need to wake up and confront reality."
                "EYES_OPENED" -> "Patient's eyes are open during meditation. Comment on their lack of focus."
                else -> "Patient's meditating. Point out their potential lack of seriousness or focus."
            }
        } else {
            statePrompt = when (state) {
                "ANGRY" -> "The patient is angry. Challenge their inability to control their emotions."
                "DISGUST" -> "The patient feels disgust. Question their sensitivity or overreaction."
                "FEAR" -> "The patient seems scared. Confront them to face their fears head-on."
                "HAPPY" -> "The patient seems happy. Doubt the authenticity of their happiness."
                "SAD" -> "The patient is sad. Encourage them to toughen up."
                "SURPRISE" -> "The patient is surprised. Make a snarky comment about their naivety."
                "NEUTRAL" -> "The patient is neutral. Provoke them to reveal more about their true feelings."
                else -> "The patient's state is unclear. Express impatience and prompt for clarity."
            }
        }

        return "$statePrompt\n\n$dialogContext"
    }

    private fun generateMeanTherapistPrompt(state: String, dialogContext: String, response: String): String {
        val userResponse = " and said '$response'"
        val isMeditationContext = dialogContext.contains("meditation", ignoreCase = true)
        val statePrompt: String

        if (isMeditationContext) {
            statePrompt = when (state) {
                "EYES_CLOSED" -> "Patient's eyes are closed during meditation. Remark on their need to wake up and confront reality."
                "EYES_OPENED" -> "Patient's eyes are open during meditation. Comment on their lack of focus."
                else -> "Patient's meditating. Point out their potential lack of seriousness or focus."
            }
        } else {
            statePrompt = when (state) {
                "ANGRY" -> "Patient is fuming$userResponse. Snap at them to get their act together."
                "DISGUST" -> "Patient is repulsed$userResponse. Tell them to stop being so sensitive."
                "FEAR" -> "Patient is frightened$userResponse. Prod them to confront what scares them."
                "HAPPY" -> "Patient is cheerful$userResponse. Suggest their happiness might be shallow."
                "SAD" -> "Patient is down$userResponse. Instruct them to stop pitying themselves."
                "SURPRISE" -> "Patient is startled$userResponse. Mock their lack of awareness."
                "NEUTRAL" -> "Patient is emotionless$userResponse. Encourage them to show some spine."
                else -> "Patient's state unknown$userResponse. Push for a clearer explanation."
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
            .echo(false)
            .model("text-davinci-003")
            .build()

        try {
            val completion = service.createCompletion(completionRequest)
            var response = completion.choices.first().text.trim()
            println("Raw response from OpenAI: $response")

            if (response.startsWith("$agentName:")) {
                response = response.removePrefix("$agentName:").trim()
                println("Response after removing agentName: $response")
            }

            if (response.startsWith(".") || response.startsWith("?") || response.startsWith("!") ) {
                response = response.drop(1).trim()
            }

            //if (response.startsWith("$userName:")) {
            //    response = response.substringAfter("$userName:").trim()
            //}

            return response

        } catch (e: Exception) {
            println("Problem with connection to OpenAI: " + e.message)
            return "I am not sure what to say"
        }
    }

}
