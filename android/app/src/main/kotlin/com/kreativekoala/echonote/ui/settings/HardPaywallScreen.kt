package com.kreativekoala.echonote.ui.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.echonote.service.BillingService

@Composable
fun HardPaywallScreen(
    billingService: BillingService
) {
    val context = LocalContext.current
    val activity = context as? Activity

    BackHandler {
        activity?.finishAffinity()
    }

    val products by billingService.products.collectAsState()
    val isLoading by billingService.isLoading.collectAsState()

    // Subscribe to annual plan but show per-week pricing
    val yearlyProduct = products.firstOrNull { it.billingPeriod.contains("Y") }

    // Calculate per-week price from annual price
    val perWeekPrice = yearlyProduct?.let {
        val yearlyMicros = it.priceAmountMicros
        if (yearlyMicros <= 0L) return@let null
        val weeklyMicros = yearlyMicros / 52
        val weeklyAmount = weeklyMicros / 1_000_000.0
        val currencyCode = it.currencyCode
        // Format with currency symbol
        val format = java.text.NumberFormat.getCurrencyInstance()
        format.currency = java.util.Currency.getInstance(currencyCode)
        format.maximumFractionDigits = 2
        format.format(weeklyAmount)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Unlock Full Access",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Unlimited recordings, transcription,\naudio editing & more",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Free trial badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.2f)
            ) {
                Text(
                    text = "3-DAY FREE TRIAL",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF81C784),
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Per-week price (billed annually)
            if (perWeekPrice != null) {
                Text(
                    text = "$perWeekPrice per week",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            } else {
                Text(
                    text = "Loading...",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Cancel anytime during trial",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Continue button — purchases the yearly plan
            Button(
                onClick = {
                    if (yearlyProduct != null && activity != null) {
                        billingService.purchase(activity, yearlyProduct)
                    }
                },
                enabled = yearlyProduct != null && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Continue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { billingService.restorePurchases() }) {
                Text(
                    text = "Restore Purchases",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Terms of Service",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
                Text(
                    text = "  •  ",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
                Text(
                    text = "Privacy Policy",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
        }
    }
}
