package com.callguard.caller

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log
import com.callguard.CallGuardState
import com.callguard.core.BlockPolicy
import com.callguard.core.CallerContext
import com.callguard.core.NumberParser
import com.callguard.core.NumberReputation
import com.callguard.core.ScamPrefixList
import com.callguard.core.Verification

/**
 * Reads the caller number of incoming calls (the role the user grants once) and scores it on the
 * device. Calls are always allowed through untouched, with one exception: a number the user
 * explicitly chose to block ("Block this number") is rejected. Emergency numbers never are.
 * The number is never logged or sent anywhere.
 */
class CallerIdService : CallScreeningService() {
    private val reputation by lazy {
        NumberReputation(PackStore(this).load() ?: runCatching { ScamPrefixList.parse(assets.open("scam_prefixes.txt").bufferedReader().readText()) }.getOrDefault(emptyList()))
    }

    override fun onScreenCall(details: Call.Details) {
        var reject = false
        try {
            if (Build.VERSION.SDK_INT >= 29 && details.callDirection != Call.Details.DIRECTION_INCOMING) return
            val number = details.handle?.schemeSpecificPart
            val verification = if (Build.VERSION.SDK_INT >= 30) when (details.callerNumberVerificationStatus) {
                Connection.VERIFICATION_STATUS_PASSED -> Verification.PASSED
                Connection.VERIFICATION_STATUS_FAILED -> Verification.FAILED
                else -> Verification.UNKNOWN
            } else Verification.UNKNOWN
            val parsed = NumberParser.parse(number)
            val seen = SeenNumbers(this)
            val hash = if (parsed.digits.isNotEmpty()) seen.hashOf(parsed.digits) else null
            reject = BlockPolicy.shouldReject(Blocklist(this).all(), hash, parsed.digits)
            if (reject) { Log.i(TAG, "call rejected: number is on the user's block list"); return }
            val assessment = reputation.assess(
                CallerContext(
                    number = number,
                    inContacts = ContactLookup.isInContacts(this, number),
                    firstTime = seen.checkAndRecord(parsed.digits).takeIf { parsed.digits.isNotEmpty() },
                    verification = verification,
                    userTrusted = hash != null && hash in FeedbackStore(this).trustedHashes(),
                ),
            ).copy(numberHash = hash)
            CallerRegistry.set(assessment)
            CallAlerts.notifyIncoming(this, assessment) // heads-up while it is still ringing, even with the app closed
            CallGuardState.update { it.copy(caller = assessment.summary) }
            if (!CallGuardState.state.monitoring) { // quietly counted so Home can nudge "turn on now"; never the number itself
                val p = com.callguard.alert.AppPrefs(this)
                p.unprotectedCallLog = com.callguard.core.UnprotectedCallLog.record(p.unprotectedCallLog, System.currentTimeMillis())
            }
            Log.i(TAG, "caller assessed: kind=${assessment.kind} score=${assessment.score} level=${assessment.level}") // never the number
        } catch (t: Throwable) {
            Log.w(TAG, "caller assessment failed: ${t.javaClass.simpleName}")
        } finally {
            // Allow the call untouched, unless the user blocked this number.
            respondToCall(details, if (reject) CallResponse.Builder().setDisallowCall(true).setRejectCall(true).build() else CallResponse.Builder().build())
        }
    }

    private companion object { const val TAG = "CallGuard.Caller" }
}
