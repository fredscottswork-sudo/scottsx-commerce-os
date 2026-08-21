package com.scottsx.app.data.remote

import java.io.IOException

/**
 * Thrown when the backend refuses a request because the signed-in account has
 * not verified its email address (HTTP 403, `code = "EMAIL_NOT_VERIFIED"`).
 *
 * This is deliberately NOT an authentication failure. The token is valid and
 * must be kept: the user needs that session in order to request and confirm a
 * verification email. Clearing it would strand them on the sign-in screen with
 * no way to finish. Callers should route to the verification screen instead.
 *
 * It extends [IOException] so existing `catch (e: IOException)` blocks keep
 * working and simply show the message; screens that want the better behaviour
 * catch this type first.
 */
class EmailNotVerifiedException(
    message: String,
    /** The address awaiting proof, when the server told us which one. */
    val email: String? = null,
) : IOException(message)
