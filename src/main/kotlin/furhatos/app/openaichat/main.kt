package furhatos.app.openaichat

import furhatos.app.openaichat.flow.*
import furhatos.skills.Skill
import furhatos.flow.kotlin.*
import furhatos.nlu.LogisticMultiIntentClassifier

class OpenaichatSkill : Skill() {
    override fun start() {
        Flow().run(Init)
    }
}

fun main(args: Array<String>) {
    // run python(3) script.py before running this
    // TODO: run the script from here
    LogisticMultiIntentClassifier.setAsDefault()
    Skill.main(args)
}
