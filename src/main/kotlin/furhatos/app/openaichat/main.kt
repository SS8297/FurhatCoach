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
    LogisticMultiIntentClassifier.setAsDefault()
    val emotionDetector = EmotionDetector()
    val currentEmotion = emotionDetector.getEmotion()
    println("Detected Emotion: $currentEmotion")
    Skill.main(args)
}

