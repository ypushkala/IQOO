package com.callguard.core

/** The text of the heads-up shown while a suspicious call is still ringing. It never contains the phone number. */
data class CallerAlertText(val title: String, val body: String) {
    companion object {
        private val calming = setOf(NumberReason.IN_CONTACTS, NumberReason.VERIFIED, NumberReason.USER_SAFE, NumberReason.SERIES_160)

        /** Null when the number looks normal: CallGuard stays quiet. Reasons are codes from [NumberReputation], shown in the screen language. */
        fun build(a: NumberAssessment, lang: Lang, monitoring: Boolean): CallerAlertText? {
            if (a.level == NumberRisk.NORMAL) return null
            val high = a.level == NumberRisk.HIGH
            val title = when (lang) {
                Lang.EN -> if (high) "Likely scam number calling" else "Unusual incoming call"
                Lang.HI -> if (high) "यह नंबर धोखाधड़ी वाला लग रहा है" else "असामान्य इनकमिंग कॉल"
                Lang.TE -> if (high) "ఈ నంబర్ మోసపు నంబర్‌లా ఉంది" else "అసాధారణ ఇన్‌కమింగ్ కాల్"
            }
            val advice = when (lang) {
                Lang.EN -> if (monitoring) "CallGuard is listening." else "Tap \"Protect this call\" to turn CallGuard on."
                Lang.HI -> if (monitoring) "CallGuard सुन रहा है।" else "CallGuard चालू करने के लिए \"इस कॉल को सुरक्षित करें\" दबाएं।"
                Lang.TE -> if (monitoring) "CallGuard వింటోంది." else "CallGuard ఆన్ చేయడానికి \"ఈ కాల్‌ను రక్షించండి\" నొక్కండి."
            }
            // Only reasons that make the number look worse are worth showing; "saved contact" and the like are not.
            val shown = a.reasonCodes.filter { it !in calming }
            val reasons = (if (a.reasonCodes.isEmpty()) a.reasons else shown.map { it.text(lang) }).joinToString(if (lang == Lang.EN) ", " else "، ").replaceFirstChar { it.uppercase() }
            return CallerAlertText(title, if (reasons.isEmpty()) advice else if (lang == Lang.HI || lang == Lang.TE) "$reasons${if (lang == Lang.HI) '।' else '.'} $advice" else "$reasons. $advice")
        }

        fun actionLabel(lang: Lang) = when (lang) { Lang.EN -> "Protect this call"; Lang.HI -> "इस कॉल को सुरक्षित करें"; Lang.TE -> "ఈ కాల్‌ను రక్షించండి" }
        fun bootTitle(lang: Lang) = when (lang) { Lang.EN -> "CallGuard is off after the restart"; Lang.HI -> "रीस्टार्ट के बाद CallGuard बंद है"; Lang.TE -> "రీస్టార్ట్ తర్వాత CallGuard ఆఫ్‌లో ఉంది" }
        fun bootBody(lang: Lang) = when (lang) {
            Lang.EN -> "Tap to turn scam protection back on."
            Lang.HI -> "स्कैम सुरक्षा फिर से चालू करने के लिए दबाएं।"
            Lang.TE -> "స్కామ్ రక్షణను మళ్లీ ఆన్ చేయడానికి నొక్కండి."
        }
    }
}
