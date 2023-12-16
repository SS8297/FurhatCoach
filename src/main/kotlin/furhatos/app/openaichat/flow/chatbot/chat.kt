package furhatos.app.openaichat.flow.chatbot

import furhatos.app.openaichat.EmotionDetector
import furhatos.app.openaichat.flow.*
import furhatos.app.openaichat.setting.activate
import furhatos.app.openaichat.setting.hostPersona
import furhatos.app.openaichat.setting.personas
import furhatos.flow.kotlin.*
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import java.time.Duration

val MainChat = state(Parent) {
    onEntry {
        activate(currentPersona)
        delay(1000)
        Furhat.dialogHistory.clear()
        furhat.say("Hello, I am ${currentPersona.fullDesc}. ${currentPersona.intro}")
        reentry()
    }

    onReentry {
        furhat.listen(timeout = 10000)
    }

    onResponse("can we stop", "goodbye") {
        furhat.say("Okay, goodbye")
        activate(hostPersona)
        delay(1000)
        furhat.say {
            random {
                +"I hope that was fun"
                +"I hope you enjoyed that"
                +"I hope you found that interesting"
            }
        }
        goto(AfterChat)
    }

    onResponse {
        val patientState = EmotionDetector().getEmotion()
        println(patientState)
        val response = currentPersona.chatbot.getResponseForPatientState(patientState, currentPersona, it.text)
        println(response)
        furhat.say(response)
        reentry()
    }

    onNoResponse {
        if (checkForSessionEnd(Furhat.dialogHistory.toString(), "", currentPersona.chatbot.getLastPatientState())) {
            furhat.say("It seems like we are not making progress. Let's end the session here.")
            goto(AfterChat)
        } else {
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