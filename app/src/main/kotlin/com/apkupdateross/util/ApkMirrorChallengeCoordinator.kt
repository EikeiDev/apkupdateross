package com.apkupdateross.util

import android.app.Activity
import android.os.Looper
import com.apkupdateross.ui.activity.ApkMirrorChallengeActivity
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ApkMirrorChallengeCoordinator(
    private val session: ApkMirrorSession
) {
    data class VerificationResult(
        val cookies: String,
        val userAgent: String,
        val resolvedUrl: String?
    )

    private data class Pending(
        val id: Long,
        val latch: CountDownLatch = CountDownLatch(1),
        @Volatile var result: VerificationResult? = null
    )

    private val lock = Any()
    private val nextId = AtomicLong(1)
    private var foregroundActivity = WeakReference<Activity>(null)
    private var pending: Pending? = null

    fun attach(activity: Activity) {
        synchronized(lock) {
            foregroundActivity = WeakReference(activity)
        }
    }

    fun detach(activity: Activity) {
        synchronized(lock) {
            if (foregroundActivity.get() === activity) {
                foregroundActivity.clear()
            }
        }
    }

    fun requestVerification(
        challengeUrl: String,
        referer: String,
        userAgent: String
    ): VerificationResult? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null

        var ownsChallenge = false
        val activity: Activity?
        val request: Pending
        synchronized(lock) {
            val existing = pending
            if (existing != null) {
                request = existing
                activity = null
            } else {
                activity = foregroundActivity.get()?.takeUnless { it.isFinishing || it.isDestroyed }
                if (activity == null) return null
                request = Pending(nextId.getAndIncrement())
                session.clearClearance()
                pending = request
                ownsChallenge = true
            }
        }

        if (ownsChallenge) {
            val snapshot = session.snapshot()
            activity?.runOnUiThread {
                runCatching {
                    activity.startActivity(
                        ApkMirrorChallengeActivity.intent(
                            context = activity,
                            requestId = request.id,
                            challengeUrl = challengeUrl,
                            referer = referer,
                            userAgent = snapshot.userAgent ?: userAgent,
                            cookies = snapshot.cookies
                        )
                    )
                }.onFailure {
                    cancel(request.id)
                }
            }
        }

        val completed = request.latch.await(5, TimeUnit.MINUTES)
        synchronized(lock) {
            if (pending === request) pending = null
        }
        val result = request.result.takeIf { completed }
        return if (ownsChallenge) result else result?.copy(resolvedUrl = null)
    }

    fun complete(
        requestId: Long,
        cookies: String,
        userAgent: String,
        resolvedUrl: String?
    ) {
        val request = synchronized(lock) {
            pending?.takeIf { it.id == requestId }
        } ?: return
        val result = VerificationResult(cookies, userAgent, resolvedUrl)
        session.update(cookies, userAgent)
        request.result = result
        request.latch.countDown()
    }

    fun cancel(requestId: Long) {
        val request = synchronized(lock) {
            pending?.takeIf { it.id == requestId }
        } ?: return
        request.latch.countDown()
    }
}
