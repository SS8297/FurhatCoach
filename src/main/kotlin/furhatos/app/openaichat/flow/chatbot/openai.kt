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

    fun getNextResponse(): String {
        /** The prompt for the chatbot includes a context of ten "lines" of dialogue. **/
        val history = Furhat.dialogHistory.all.takeLast(10).mapNotNull {
            when (it) {
                is DialogHistory.ResponseItem -> {
                    "$userName: ${it.response.text}"
                }
                is DialogHistory.UtteranceItem -> {
                    "$agentName: ${it.toText()}"
                }
                else -> null
            }
        }.joinToString(separator = "\n")
        val prompt = "$description\n\n$history\n$agentName:"
        println("-----")
        println(prompt)
        println("-----")
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
            .build();
        try {
            val completion = service.createCompletion(completionRequest)
            val response = completion.getChoices().first().text.trim()
            return response
        } catch (e: Exception) {
            println("Problem with connection to OpenAI: " + e.message)
        }
        return "I am not sure what to say"

    }

    fun getResponseForPatientState(patientState: String, persona: Persona): String {

        val dialogContext = Furhat.dialogHistory.all.takeLast(10).mapNotNull {
            when (it) {
                is DialogHistory.ResponseItem -> "$userName: ${it.response.text}"
                is DialogHistory.UtteranceItem -> it.toText()
                else -> null
            }
        }.joinToString("\n")

        val prompt = when (persona.name) {
            "Angel" -> generateKindCoachPrompt(patientState, dialogContext)
            "Demon" -> generateEvilCoachPrompt(patientState, dialogContext)
            else -> "How can I assist you?"
        }

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
        println("In kind coach state: $state")
        val statePrompt = when (state) {
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

        return "$statePrompt\n\n$dialogContext"
    }

    private fun generateEvilCoachPrompt(state: String, dialogContext: String): String {
        val statePrompt = when (state) {
            "ANGRY" -> "The patient is feeling angry. Write a provocative response to fuel their anger."
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

            if (response.isBlank() && persona.name == "Demon") {
                return "Stop thinking"
            }
            else if (response.isBlank() && persona.name == "Angel") {
                return "I'm here to listen. Tell me more about how you're feeling."
            }
            if (response.startsWith("$agentName:")) {
                response.removePrefix("$agentName:")
                response.replace("!", "Stop whining.")
                response.replace("?", "")
                response.trim()
            }

            return response

        } catch (e: Exception) {
            println("Problem with connection to OpenAI: " + e.message)
            return "I am not sure what to say"
        }
    }

}
