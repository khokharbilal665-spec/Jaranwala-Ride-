package com.example.payment

import com.example.data.PaymentMethod
import com.example.util.PakistanPhoneValidator

/**
 * Request payload submitted to a [RidePaymentProcessor] for ride or parcel fare settlement.
 */
data class PaymentSettlementRequest(
    val rideId: String,
    val amountPKR: Int,
    val passengerId: String,
    val passengerPhone: String,
    val walletAccount: String = "",
    val driverId: String? = null
)

/**
 * Outcome returned by a [RidePaymentProcessor] after settling or verifying a ride fare.
 */
sealed class PaymentSettlementResult {
    data class Success(
        val transactionRef: String,
        val settledMethod: PaymentMethod,
        val paymentStatus: String,
        val amountPKR: Int,
        val isStubGateway: Boolean,
        val message: String,
        val urduMessage: String
    ) : PaymentSettlementResult()

    data class ValidationError(
        val message: String,
        val urduMessage: String
    ) : PaymentSettlementResult()

    data class Failure(
        val reason: String,
        val urduReason: String
    ) : PaymentSettlementResult()
}

/**
 * Payment processing abstraction for Jaranwala Ride fare settlements.
 * Supports Cash on Drop and gateway stubs for Pakistani mobile wallets (JazzCash & Easypaisa).
 */
interface RidePaymentProcessor {
    val method: PaymentMethod
    val displayName: String
    val isStubGateway: Boolean

    suspend fun processFareSettlement(request: PaymentSettlementRequest): PaymentSettlementResult
}

/**
 * Cash on Drop processor: records direct PKR cash collection by the assigned Captain.
 */
class CashSettlementProcessor : RidePaymentProcessor {
    override val method: PaymentMethod = PaymentMethod.CASH
    override val displayName: String = "Cash on Drop (PKR)"
    override val isStubGateway: Boolean = false

    override suspend fun processFareSettlement(request: PaymentSettlementRequest): PaymentSettlementResult {
        if (request.amountPKR < 0) {
            return PaymentSettlementResult.ValidationError(
                message = "Fare amount cannot be negative.",
                urduMessage = "کرایہ کی رقم منفی نہیں ہو سکتی۔"
            )
        }
        val shortId = request.rideId.takeLast(6).ifBlank { "PKR" }
        return PaymentSettlementResult.Success(
            transactionRef = "CASH-$shortId",
            settledMethod = PaymentMethod.CASH,
            paymentStatus = "COLLECTED_CASH",
            amountPKR = request.amountPKR,
            isStubGateway = false,
            message = "Cash fare ₨ ${request.amountPKR} settled directly with Captain.",
            urduMessage = "₨ ${request.amountPKR} نقد کرایہ کپتان کو ادا کر دیا گیا۔"
        )
    }
}

/**
 * JazzCash mobile wallet payment gateway stub.
 * Validates the 03XX Pakistani wallet account format without collecting sensitive MPINs
 * and returns a structured settlement receipt reference for sandbox/testing environments.
 */
class JazzCashPaymentStub : RidePaymentProcessor {
    override val method: PaymentMethod = PaymentMethod.JAZZCASH
    override val displayName: String = "JazzCash Mobile Account (Stub)"
    override val isStubGateway: Boolean = true

    override suspend fun processFareSettlement(request: PaymentSettlementRequest): PaymentSettlementResult {
        if (request.amountPKR <= 0) {
            return PaymentSettlementResult.ValidationError(
                message = "JazzCash settlement requires a positive PKR fare amount.",
                urduMessage = "جاز کیش ادائیگی کے لیے درست کرایہ رقم درکار ہے۔"
            )
        }
        val accountCandidate = request.walletAccount.ifBlank { request.passengerPhone }
        val validation = PakistanPhoneValidator.validate(accountCandidate)
        if (!validation.isValid) {
            return PaymentSettlementResult.ValidationError(
                message = "Enter a valid Pakistani JazzCash mobile number (03XX-XXXXXXX).",
                urduMessage = "درست پاکستانی جاز کیش نمبر درج کریں (03XX-XXXXXXX)۔"
            )
        }
        val cleanSuffix = validation.localFormatted.takeLast(4)
        val ref = "JC-STUB-${request.rideId.takeLast(5).ifBlank { "000" }}-$cleanSuffix"
        return PaymentSettlementResult.Success(
            transactionRef = ref,
            settledMethod = PaymentMethod.JAZZCASH,
            paymentStatus = "PAID",
            amountPKR = request.amountPKR,
            isStubGateway = true,
            message = "JazzCash Stub settled ₨ ${request.amountPKR} ($ref) for ${validation.localFormatted}.",
            urduMessage = "جاز کیش (اسٹب) سے ₨ ${request.amountPKR} کی ادائیگی مکمل ہوئی ($ref)۔"
        )
    }
}

/**
 * Easypaisa mobile wallet payment gateway stub.
 * Validates the 03XX Pakistani wallet account format without collecting sensitive MPINs
 * and returns a structured settlement receipt reference for sandbox/testing environments.
 */
class EasypaisaPaymentStub : RidePaymentProcessor {
    override val method: PaymentMethod = PaymentMethod.EASYPAISA
    override val displayName: String = "Easypaisa Mobile Account (Stub)"
    override val isStubGateway: Boolean = true

    override suspend fun processFareSettlement(request: PaymentSettlementRequest): PaymentSettlementResult {
        if (request.amountPKR <= 0) {
            return PaymentSettlementResult.ValidationError(
                message = "Easypaisa settlement requires a positive PKR fare amount.",
                urduMessage = "ایزی پیسہ ادائیگی کے لیے درست کرایہ رقم درکار ہے۔"
            )
        }
        val accountCandidate = request.walletAccount.ifBlank { request.passengerPhone }
        val validation = PakistanPhoneValidator.validate(accountCandidate)
        if (!validation.isValid) {
            return PaymentSettlementResult.ValidationError(
                message = "Enter a valid Pakistani Easypaisa mobile number (03XX-XXXXXXX).",
                urduMessage = "درست پاکستانی ایزی پیسہ نمبر درج کریں (03XX-XXXXXXX)۔"
            )
        }
        val cleanSuffix = validation.localFormatted.takeLast(4)
        val ref = "EP-STUB-${request.rideId.takeLast(5).ifBlank { "000" }}-$cleanSuffix"
        return PaymentSettlementResult.Success(
            transactionRef = ref,
            settledMethod = PaymentMethod.EASYPAISA,
            paymentStatus = "PAID",
            amountPKR = request.amountPKR,
            isStubGateway = true,
            message = "Easypaisa Stub settled ₨ ${request.amountPKR} ($ref) for ${validation.localFormatted}.",
            urduMessage = "ایزی پیسہ (اسٹب) سے ₨ ${request.amountPKR} کی ادائیگی مکمل ہوئی ($ref)۔"
        )
    }
}

/**
 * Routes ride fare settlement calls to Cash, JazzCash stub, or Easypaisa stub processors.
 */
class RidePaymentGatewayRouter(
    processors: List<RidePaymentProcessor> = listOf(
        CashSettlementProcessor(),
        JazzCashPaymentStub(),
        EasypaisaPaymentStub()
    )
) {
    private val processorMap: Map<PaymentMethod, RidePaymentProcessor> =
        processors.associateBy { it.method }

    fun processorFor(method: PaymentMethod): RidePaymentProcessor {
        return processorMap[method] ?: CashSettlementProcessor()
    }

    suspend fun settle(
        method: PaymentMethod,
        request: PaymentSettlementRequest
    ): PaymentSettlementResult {
        return processorFor(method).processFareSettlement(request)
    }
}
