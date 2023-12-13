package furhatos.app.openaichat.setting

import furhatos.app.openaichat.flow.chatbot.OpenAI
import furhatos.flow.kotlin.FlowControlRunner
import furhatos.flow.kotlin.furhat
import furhatos.flow.kotlin.voice.AcapelaVoice
import furhatos.flow.kotlin.voice.PollyNeuralVoice
import furhatos.flow.kotlin.voice.Voice
import furhatos.nlu.SimpleIntent

class Persona(
    val name: String,
    val otherNames: List<String> = listOf(),
    val intro: String = "",
    val desc: String,
    val face: List<String>,
    val mask: String = "adult",
    val voice: List<Voice>
) {
    val fullDesc = "$name, the $desc"

    val intent = SimpleIntent((listOf(name, desc, fullDesc) + otherNames))

    /** The prompt for the openAI language model **/
    val chatbot =
        OpenAI("The following is a conversation between $name, the $desc, and a Person", "Person", name)
}

fun FlowControlRunner.activate(persona: Persona) {
    for (voice in persona.voice) {
        if (voice.isAvailable) {
            furhat.voice = voice
            break
        }
    }

    for (face in persona.face) {
        if (furhat.faces[persona.mask]?.contains(face)!!) {
            furhat.character = face
            break
        }
    }
}

val hostPersona = Persona(
    name = "Host",
    desc = "host",
    face = listOf("Titan"),
    voice = listOf(PollyNeuralVoice("Kimberly"))
)

val personas = listOf(
    Persona(
        name = "Angel",
        desc = "good coach",
        intro = "As we begin, gently close your eyes and let the quiet settle within. Embrace this moment of stillness",
        face = listOf("Isabel"),
        voice = listOf(PollyNeuralVoice("Olivia"))

    ),
    Persona(
        name = "Demon",
        desc = "rude coach",
        intro = "Cut the drama and close your eyes",
        face = listOf("Alex", "default"),
        voice = listOf(PollyNeuralVoice("Matthew"))
    ),
)