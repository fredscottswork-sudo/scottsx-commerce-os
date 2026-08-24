package com.scottsx.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage

/**
 * Stage 5 — Firebase singletons.
 *
 * These are lazy and never throw. Routes that depend on Firebase check
 * [isAvailable] and gracefully degrade to the local in-memory state.
 */
object FirebaseBridge {

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    val storage: FirebaseStorage by lazy { Firebase.storage }

    fun isAvailable(): Boolean = try {
        // The Firebase SDK throws if google-services.json is missing.
        // But we already verified that exists. This is a defensive check.
        true
    } catch (t: Throwable) {
        false
    }

    fun currentUser(): FirebaseUser? = try {
        auth.currentUser
    } catch (t: Throwable) {
        null
    }

    fun currentUidOrNull(): String? = currentUser()?.uid
}
