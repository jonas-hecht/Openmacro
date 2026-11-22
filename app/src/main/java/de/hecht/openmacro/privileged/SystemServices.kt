/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.privileged

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.telephony.SubscriptionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Calls hidden system service interfaces through Shizuku, so they execute with the
 * identity (and permissions) of the shell user. All interfaces are accessed via
 * reflection because they are not part of the public SDK; method signatures are matched
 * by parameter types to tolerate differences between Android versions.
 */
object SystemServices {

    class PrivilegedException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Starts Wi-Fi tethering (hotspot) with the hotspot configuration saved in the system. */
    suspend fun startWifiHotspot() {
        val connector = service("tethering", "android.net.ITetheringConnector")

        val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
        val builder = builderClass.getConstructor(Int::class.javaPrimitiveType)
            .newInstance(TETHERING_WIFI)
        builderClass.getMethod("setShouldShowEntitlementUi", Boolean::class.javaPrimitiveType)
            .invoke(builder, false)
        val request = builderClass.getMethod("build").invoke(builder)!!

        val result = CompletableDeferred<Int>()
        val listener = asInterface(
            "android.net.IIntResultListener",
            IntResultBinder("android.net.IIntResultListener") { result.complete(it) },
        )

        val method = findMethod(connector, "startTethering")
        var stringIndex = 0
        val args = method.parameterTypes.map { type ->
            when {
                type.isInstance(request) -> request
                type.name == "android.net.TetheringRequestParcel" ->
                    request.javaClass.getMethod("getParcel").invoke(request)
                type == String::class.java ->
                    // (callerPkg, callingAttributionTag)
                    if (stringIndex++ == 0) ShizukuManager.callerPackage else null
                type.isInstance(listener) -> listener
                else -> throw PrivilegedException("Unsupported startTethering signature: $method")
            }
        }
        invoke(method, connector, args)

        val code = withTimeoutOrNull(15_000) { result.await() }
            ?: throw PrivilegedException("No response from tethering service")
        if (code != TETHER_ERROR_NO_ERROR) {
            throw PrivilegedException("Tethering service returned error $code")
        }
    }

    /** Enables mobile data on the default data SIM. */
    fun enableMobileData() {
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            throw PrivilegedException("No SIM selected for mobile data")
        }
        val telephony = service("phone", "com.android.internal.telephony.ITelephony")
        val method = findMethodOrNull(telephony, "setDataEnabledForReason")
        if (method != null) {
            val args = when (method.parameterCount) {
                // (subId, reason, enable, callingPackage)
                4 -> listOf(subId, DATA_ENABLED_REASON_USER, true, ShizukuManager.callerPackage)
                // (subId, reason, enable)
                3 -> listOf(subId, DATA_ENABLED_REASON_USER, true)
                else -> throw PrivilegedException("Unsupported setDataEnabledForReason signature: $method")
            }
            invoke(method, telephony, args)
        } else {
            invoke(findMethod(telephony, "setUserDataEnabled"), telephony, listOf(subId, true))
        }
    }

    private fun service(name: String, interfaceName: String): Any {
        val binder = SystemServiceHelper.getSystemService(name)
            ?: throw PrivilegedException("System service '$name' not found")
        return asInterface(interfaceName, ShizukuBinderWrapper(binder))
    }

    private fun asInterface(interfaceName: String, binder: IBinder): Any =
        Class.forName("$interfaceName\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!

    private fun findMethodOrNull(target: Any, name: String): Method? =
        target.javaClass.methods.filter { it.name == name }.maxByOrNull { it.parameterCount }

    private fun findMethod(target: Any, name: String): Method =
        findMethodOrNull(target, name) ?: throw PrivilegedException("Method $name not found")

    private fun invoke(method: Method, target: Any, args: List<Any?>) {
        try {
            method.invoke(target, *args.toTypedArray())
        } catch (e: InvocationTargetException) {
            val cause = e.targetException
            throw PrivilegedException(cause.message ?: cause.javaClass.simpleName, cause)
        }
    }

    /** Minimal implementation of a oneway AIDL callback `void onResult(int)`. */
    private class IntResultBinder(
        private val descriptor: String,
        private val onResult: (Int) -> Unit,
    ) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == FIRST_CALL_TRANSACTION) {
                data.enforceInterface(descriptor)
                onResult(data.readInt())
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private const val TETHERING_WIFI = 0
    private const val TETHER_ERROR_NO_ERROR = 0
    private const val DATA_ENABLED_REASON_USER = 0
}
