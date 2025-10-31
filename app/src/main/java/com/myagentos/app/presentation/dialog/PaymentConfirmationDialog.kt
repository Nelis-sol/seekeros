package com.myagentos.app.presentation.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.myagentos.app.R
import com.myagentos.app.domain.model.PaymentInfo

/**
 * Dialog for payment confirmation with x402 protocol
 */
class PaymentConfirmationDialog(
    context: Context,
    private val toolName: String,
    private val paymentInfo: PaymentInfo,
    private val onPaymentApproved: () -> Unit,
    private val onPaymentDeclined: () -> Unit
) : Dialog(context) {
    
    private lateinit var toolNameText: TextView
    private lateinit var paymentDescriptionText: TextView
    private lateinit var paymentAmountText: TextView
    private lateinit var recipientLayout: LinearLayout
    private lateinit var recipientText: TextView
    private lateinit var payButton: Button
    private lateinit var cancelButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_payment_confirmation)
        
        // Initialize views
        toolNameText = findViewById(R.id.toolNameText)
        paymentDescriptionText = findViewById(R.id.paymentDescriptionText)
        paymentAmountText = findViewById(R.id.paymentAmountText)
        recipientLayout = findViewById(R.id.recipientLayout)
        recipientText = findViewById(R.id.recipientText)
        payButton = findViewById(R.id.payButton)
        cancelButton = findViewById(R.id.cancelButton)
        
        // Set data
        toolNameText.text = toolName
        paymentDescriptionText.text = paymentInfo.description
        paymentAmountText.text = "${paymentInfo.price} ${paymentInfo.currency}"
        
        // Show recipient if available
        paymentInfo.recipient?.let {
            recipientLayout.visibility = View.VISIBLE
            recipientText.text = formatAddress(it)
        }
        
        // Set up button listeners
        payButton.setOnClickListener {
            onPaymentApproved()
            dismiss()
        }
        
        cancelButton.setOnClickListener {
            onPaymentDeclined()
            dismiss()
        }
        
        // Make dialog non-cancelable to force user decision
        setCancelable(false)
    }
    
    /**
     * Format wallet address for display (show first 4 and last 4 characters)
     */
    private fun formatAddress(address: String): String {
        return if (address.length > 12) {
            "${address.take(4)}...${address.takeLast(4)}"
        } else {
            address
        }
    }
}

