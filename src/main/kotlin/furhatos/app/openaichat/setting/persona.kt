package furhatos.app.openaichat.setting

import furhatos.app.openaichat.flow.chatbot.OpenAI
import furhatos.flow.kotlin.FlowControlRunner
import furhatos.flow.kotlin.furhat
import furhatos.flow.kotlin.voice.AcapelaVoice
import furhatos.flow.kotlin.voice.PollyNeuralVoice
import furhatos.flow.kotlin.voice.Voice
import furhatos.nlu.SimpleIntent
import furhatos.util.Gender
import furhatos.util.Language

class Persona(
    val name: String,
    val otherNames: List<String> = listOf(),
    val intro: String = "",
    val desc: String,
    val face: List<String>,
    val mask: String = "adult",
    val voice: List<Voice>,
) {
    val fullDesc = "$name, the $desc"

    val intent = SimpleIntent((listOf(name, desc, fullDesc) + otherNames))

    /** The prompt for the openAI language model **/
    val chatbot =
        OpenAI("The following is a conversation between $name, the $desc, and a Person", "Person", name)
}

fun FlowControlRunner.activate(persona: Persona) {
    val personaName = persona.name
    for (voice in persona.voice) {
        if (voice.isAvailable) {
            furhat.voice = voice
            println(voice.toString())
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
    voice = listOf(PollyNeuralVoice("Sonia")),
)

val personas = listOf(
    Persona(
        name = "Hanna",
        desc = "kind therapist",
        intro = "What's your name and how are you feeling today?",
        face = listOf("Isabel"),
        voice = listOf(PollyNeuralVoice("Olivia")),
    ),
    Persona(
        name = "Emil",
        desc = "mean therapist",
        intro = "What is your problem?",
        face = listOf("Alex", "default"),
        voice = listOf(PollyNeuralVoice("Matthew")),
    ),
)