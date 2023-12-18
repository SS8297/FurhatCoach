package furhatos.app.openaichat.flow

import furhatos.app.openaichat.flow.chatbot.MainChat
import furhatos.app.openaichat.setting.Persona
import furhatos.app.openaichat.setting.activate
import furhatos.app.openaichat.setting.hostPersona
import furhatos.app.openaichat.setting.personas
import furhatos.flow.kotlin.*
import furhatos.records.Location
import java.util.*

val Greeting = state(Parent) {

    onEntry {
        furhat.attend(users.userClosestToPosition(Location(0.0, 0.0, 0.5)))
        askForAnything("Hi there")
        goto(ChoosePersona())
    }
}

var currentPersona: Persona = hostPersona

fun ChoosePersona() = state(Parent) {
    for (persona in personas) {
        println("${persona.name} voice available: ${persona.voice.first().isAvailable}")
    }

    val personasWithAvailableVoice = personas.filter { it.voice.first().isAvailable }
    val selectedPersonas = personasWithAvailableVoice.take(3)

    println("Selected Personas after filter: ${selectedPersonas.map { it.name }}")

    fun FlowControlRunner.presentPersonas() {
        furhat.say("You can choose to speak to one of these therapist:")
        for (persona in selectedPersonas) {
            delay(100)
            furhat.say(persona.fullDesc)
        }
        //activate(hostPersona)
        reentry()
    }

    onEntry {
        presentPersonas()
    }

    onReentry {
        furhat.ask("Would you prefer a kind one or the more direct one?")
    }

    onResponse {
        val response = it.text.lowercase(Locale.getDefault())
        val kindKeywords = listOf("Hanna", "kind", "gentle", "compassionate", "kind therapist", "kind one", "the kind one")
        val directKeywords = listOf("Emil", "direct", "straightforward", "blunt", "direct therapist", "direct one", "the direct one")
        when {
            kindKeywords.any { keyword -> response.contains(keyword) } -> {
                furhat.say("Okay, let's proceed with Hanna.")
                currentPersona = personas.find { it.name == "Hanna" } ?: hostPersona
                activate(currentPersona)
                goto(MainChat)
            }
            directKeywords.any { keyword -> response.contains(keyword) } -> {
                furhat.say("Okay, let's proceed with Emil.")
                currentPersona = personas.find { it.name == "Emil" } ?: hostPersona
                activate(currentPersona)
                goto(MainChat)
            }
            else -> {
                furhat.say("I'm not sure what you mean.")
                reentry()
            }
        }
    }
}