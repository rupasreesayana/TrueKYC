package com.example.truekyc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }

        setContent {
            TrueKYCApp()
        }
    }
}


/* =========================================================
   SMS FUNCTION
========================================================= */

fun sendSecuritySms(
    context: android.content.Context,
    phoneNumber: String
): Boolean {

    return try {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val message = """
TrueKYC Security Alert

A KYC verification attempt was flagged for security review.


If you did not initiate this verification, please contact your bank immediately.

Verification is temporarily on hold for 12 hours.
        """.trimIndent()

        val smsManager =
            SmsManager.getDefault()

        smsManager.sendTextMessage(
            phoneNumber,
            null,
            message,
            null,
            null
        )

        true

    } catch (e: Exception) {

        e.printStackTrace()
        false
    }
}


/* =========================================================
   MAIN APP
========================================================= */

@Composable
fun TrueKYCApp() {

    var currentScreen by remember {
        mutableStateOf("welcome")
    }

    var verificationResult by remember {
        mutableStateOf(false)
    }

    var currentCustomerIndex by remember {
        mutableStateOf(0)
    }

    var completedCustomers by remember {
        mutableStateOf(setOf<Int>())
    }

    var securityHoldCustomers by remember {
        mutableStateOf(setOf<Int>())
    }

    MaterialTheme {

        when (currentScreen) {

            "welcome" -> {

                WelcomeScreen(
                    onLogin = {
                        currentScreen = "queue"
                    }
                )
            }

            "queue" -> {

                KycQueueScreen(
                    currentCustomerIndex =
                        currentCustomerIndex,

                    completedCustomers =
                        completedCustomers,

                    securityHoldCustomers =
                        securityHoldCustomers,

                    onAdmitCustomer = { index ->

                        currentCustomerIndex =
                            index

                        currentScreen =
                            "kyc"
                    }
                )
            }

            "kyc" -> {

                KycDetailsScreen(
                    customerName =
                        getCustomerName(
                            currentCustomerIndex
                        ),

                    kycReference =
                        getCustomerId(
                            currentCustomerIndex
                        ),

                    onContinue = {
                        currentScreen =
                            "instructions"
                    }
                )
            }

            "instructions" -> {

                InstructionsScreen(
                    onStartVerification = {
                        currentScreen =
                            "camera"
                    }
                )
            }

            "camera" -> {

                CameraScreen(

                    customerPhoneNumber =
                        getCustomerPhone(
                            currentCustomerIndex
                        ),

                    onVerificationComplete = {

                        completedCustomers =
                            completedCustomers +
                                    currentCustomerIndex

                        verificationResult =
                            true

                        currentScreen =
                            "result"
                    },

                    onSuspicious = {

                        securityHoldCustomers =
                            securityHoldCustomers +
                                    currentCustomerIndex

                        completedCustomers =
                            completedCustomers +
                                    currentCustomerIndex

                        verificationResult =
                            false

                        currentScreen =
                            "result"
                    }
                )
            }

            "result" -> {

                VerificationResultScreen(

                    verified =
                        verificationResult,

                    customerName =
                        getCustomerName(
                            currentCustomerIndex
                        ),

                    onNextCustomer = {

                        val nextIndex =
                            findNextCustomer(
                                completedCustomers
                            )

                        if (nextIndex != null) {

                            currentCustomerIndex =
                                nextIndex

                            currentScreen =
                                "queue"

                        } else {

                            currentScreen =
                                "welcome"
                        }
                    }
                )
            }
        }
    }
}


/* =========================================================
   CUSTOMER DATA
========================================================= */

data class QueueCustomer(
    val id: String,
    val name: String,
    val waitTime: String,
    val phoneNumber: String
)

fun getCustomers(): List<QueueCustomer> {

    return listOf(

        QueueCustomer(
            id = "TKYC-2026-001",
            name = "Riya Sharma",
            waitTime = "02:14",
            phoneNumber = "+919876543210"
        ),

        QueueCustomer(
            id = "TKYC-2026-002",
            name = "Rahul Kumar",
            waitTime = "04:32",
            phoneNumber = "+919876543210"
        ),

        QueueCustomer(
            id = "TKYC-2026-003",
            name = "Ananya Rao",
            waitTime = "06:18",
            phoneNumber = "+919876543210"
        )
    )
}

fun getCustomerName(index: Int): String {
    return getCustomers()[index].name
}

fun getCustomerId(index: Int): String {
    return getCustomers()[index].id
}

fun getCustomerPhone(index: Int): String {
    return getCustomers()[index].phoneNumber
}

fun findNextCustomer(
    completedCustomers: Set<Int>
): Int? {

    for (index in getCustomers().indices) {

        if (!completedCustomers.contains(index)) {
            return index
        }
    }

    return null
}


/* =========================================================
   WELCOME SCREEN
========================================================= */

@Composable
fun WelcomeScreen(
    onLogin: () -> Unit
) {

    var bankName by remember {
        mutableStateOf("")
    }

    var location by remember {
        mutableStateOf("")
    }

    var officerId by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    val canLogin =
        bankName.isNotBlank() &&
                location.isNotBlank() &&
                officerId.isNotBlank() &&
                password.isNotBlank()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "TrueKYC",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                "Secure Digital KYC Verification",

            fontSize = 16.sp,

            color =
                Color.Gray
        )

        Spacer(
            modifier =
                Modifier.height(30.dp)
        )

        OutlinedTextField(
            value = bankName,
            onValueChange = {
                bankName = it
            },

            modifier =
                Modifier.fillMaxWidth(),

            label = {
                Text("Bank / Institution")
            },

            placeholder = {
                Text("Enter bank name")
            },

            singleLine = true,

            shape =
                RoundedCornerShape(12.dp)
        )

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        OutlinedTextField(
            value = location,
            onValueChange = {
                location = it
            },

            modifier =
                Modifier.fillMaxWidth(),

            label = {
                Text("Branch / Location")
            },

            placeholder = {
                Text("Enter branch / location")
            },

            singleLine = true,

            shape =
                RoundedCornerShape(12.dp)
        )

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        OutlinedTextField(
            value = officerId,
            onValueChange = {
                officerId = it
            },

            modifier =
                Modifier.fillMaxWidth(),

            label = {
                Text("Officer ID")
            },

            placeholder = {
                Text("Enter officer ID")
            },

            singleLine = true,

            shape =
                RoundedCornerShape(12.dp)
        )

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
            },

            modifier =
                Modifier.fillMaxWidth(),

            label = {
                Text("Password")
            },

            placeholder = {
                Text("Enter password")
            },

            singleLine = true,

            shape =
                RoundedCornerShape(12.dp)
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Button(
            onClick =
                onLogin,

            enabled =
                canLogin,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp),

            shape =
                RoundedCornerShape(14.dp)
        ) {

            Text(
                text =
                    "Officer Login",

                fontSize =
                    17.sp,

                fontWeight =
                    FontWeight.SemiBold
            )
        }

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Privacy-first • On-device biometric verification",

            fontSize =
                12.sp,

            color =
                Color.Gray
        )
    }
}


/* =========================================================
   KYC QUEUE
========================================================= */

@Composable
fun KycQueueScreen(
    currentCustomerIndex: Int,
    completedCustomers: Set<Int>,
    securityHoldCustomers: Set<Int>,
    onAdmitCustomer: (Int) -> Unit
) {

    val customers =
        getCustomers()

    val nextCustomerIndex =
        findNextCustomer(
            completedCustomers
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)
    ) {

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Officer Dashboard",

            fontSize =
                28.sp,

            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Text(
            text =
                "Manage customers waiting for online KYC",

            fontSize =
                14.sp,

            color =
                Color.Gray
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier =
                    Modifier.padding(18.dp)
            ) {

                Text(
                    text =
                        "ONLINE KYC QUEUE",

                    fontSize =
                        17.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text =
                        "${customers.size} customers in queue",

                    fontSize =
                        13.sp,

                    color =
                        Color.Gray
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        LazyColumn(
            verticalArrangement =
                Arrangement.spacedBy(14.dp),

            modifier =
                Modifier.weight(1f)
        ) {

            itemsIndexed(
                customers
            ) { index, customer ->

                val isCompleted =
                    completedCustomers
                        .contains(index)

                val isSecurityHold =
                    securityHoldCustomers
                        .contains(index)

                val isNext =
                    index ==
                            nextCustomerIndex &&
                            !isCompleted &&
                            !isSecurityHold

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(16.dp)
                ) {

                    Column(
                        modifier =
                            Modifier.padding(18.dp)
                    ) {

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Surface(
                                shape =
                                    RoundedCornerShape(50),

                                color =
                                    when {

                                        isSecurityHold ->
                                            Color(0xFFFFEBEE)

                                        isCompleted ->
                                            Color(0xFFE8F5E9)

                                        else ->
                                            Color(0xFFE8EAF6)
                                    }
                            ) {

                                Icon(
                                    imageVector =
                                        Icons.Default.Person,

                                    contentDescription =
                                        null,

                                    modifier =
                                        Modifier.padding(10.dp),

                                    tint =
                                        when {

                                            isSecurityHold ->
                                                Color(0xFFC62828)

                                            isCompleted ->
                                                Color(0xFF2E7D32)

                                            else ->
                                                Color.DarkGray
                                        }
                                )
                            }

                            Spacer(
                                modifier =
                                    Modifier.width(12.dp)
                            )

                            Column(
                                modifier =
                                    Modifier.weight(1f)
                            ) {

                                Text(
                                    text =
                                        customer.name,

                                    fontSize =
                                        17.sp,

                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(3.dp)
                                )

                                Text(
                                    text =
                                        customer.id,

                                    fontSize =
                                        13.sp,

                                    color =
                                        Color.Gray
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(5.dp)
                                )

                                when {

                                    isSecurityHold -> {

                                        Text(
                                            text =
                                                "⚠ Security Hold • 12 hours",

                                            fontSize =
                                                12.sp,

                                            color =
                                                Color(0xFFC62828),

                                            fontWeight =
                                                FontWeight.SemiBold
                                        )
                                    }

                                    isCompleted -> {

                                        Text(
                                            text =
                                                "✓ Verification Finished",

                                            fontSize =
                                                12.sp,

                                            color =
                                                Color(0xFF2E7D32),

                                            fontWeight =
                                                FontWeight.SemiBold
                                        )
                                    }

                                    else -> {

                                        Text(
                                            text =
                                                "Waiting • ${customer.waitTime}",

                                            fontSize =
                                                12.sp,

                                            color =
                                                Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        if (isNext) {

                            Button(
                                onClick = {
                                    onAdmitCustomer(index)
                                },

                                modifier =
                                    Modifier.fillMaxWidth(),

                                shape =
                                    RoundedCornerShape(12.dp)
                            ) {

                                Text(
                                    text =
                                        "Admit Customer",

                                    fontWeight =
                                        FontWeight.SemiBold
                                )
                            }

                        } else if (isCompleted) {

                            OutlinedButton(
                                onClick = { },

                                enabled =
                                    false,

                                modifier =
                                    Modifier.fillMaxWidth(),

                                shape =
                                    RoundedCornerShape(12.dp)
                            ) {

                                Text(
                                    text =
                                        "Completed"
                                )
                            }

                        } else if (isSecurityHold) {

                            OutlinedButton(
                                onClick = { },

                                enabled =
                                    false,

                                modifier =
                                    Modifier.fillMaxWidth(),

                                shape =
                                    RoundedCornerShape(12.dp)
                            ) {

                                Text(
                                    text =
                                        "Locked for 12 Hours"
                                )
                            }

                        } else {

                            OutlinedButton(
                                onClick = { },

                                enabled =
                                    false,

                                modifier =
                                    Modifier.fillMaxWidth(),

                                shape =
                                    RoundedCornerShape(12.dp)
                            ) {

                                Text(
                                    text =
                                        "Waiting"
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        Text(
            text =
                "Customers are admitted one at a time for secure KYC verification.",

            fontSize =
                12.sp,

            color =
                Color.Gray
        )
    }
}


/* =========================================================
   KYC DETAILS
========================================================= */

@Composable
fun KycDetailsScreen(
    customerName: String,
    kycReference: String,
    onContinue: () -> Unit
) {

    var name by remember(customerName) {
        mutableStateOf(customerName)
    }

    var reference by remember(kycReference) {
        mutableStateOf(kycReference)
    }

    val validKycId =
        Regex(
            "TKYC-\\d{4}-\\d{3}"
        ).matches(reference)

    val canContinue =
        name.isNotBlank() &&
                validKycId

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp)
    ) {

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Customer KYC",

            fontSize =
                28.sp,

            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                "Verify customer details before secure verification.",

            color =
                Color.Gray
        )

        Spacer(
            modifier =
                Modifier.height(28.dp)
        )

        OutlinedTextField(
            value =
                name,

            onValueChange = {
                name = it
            },

            modifier =
                Modifier.fillMaxWidth(),

            label = {
                Text("Customer Full Name")
            },

            singleLine = true,

            shape =
                RoundedCornerShape(12.dp)
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        OutlinedTextField(
            value =
                reference,

            onValueChange = {
                reference = it
            },

            modifier =
                Modifier.fillMaxWidth(),

            label = {
                Text("KYC Reference Number")
            },

            singleLine = true,

            shape =
                RoundedCornerShape(12.dp),

            isError =
                reference.isNotEmpty() &&
                        !validKycId
        )

        if (
            reference.isNotEmpty() &&
            !validKycId
        ) {

            Text(
                text =
                    "Format: TKYC-2026-001",

                fontSize =
                    12.sp,

                color =
                    MaterialTheme
                        .colorScheme
                        .error,

                modifier =
                    Modifier.padding(
                        start = 8.dp,
                        top = 4.dp
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier =
                    Modifier.padding(18.dp)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.VerifiedUser,

                        contentDescription =
                            null,

                        tint =
                            Color(0xFF2E7D32)
                    )

                    Spacer(
                        modifier =
                            Modifier.width(10.dp)
                    )

                    Text(
                        text =
                            "Privacy Protected",

                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Biometric verification is processed locally on the device. No biometric video is uploaded to the cloud.",

                    fontSize =
                        13.sp,

                    color =
                        Color.Gray
                )
            }
        }

        Spacer(
            modifier =
                Modifier.weight(1f)
        )

        Button(
            onClick =
                onContinue,

            enabled =
                canContinue,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp),

            shape =
                RoundedCornerShape(14.dp)
        ) {

            Text(
                text =
                    "Continue",

                fontSize =
                    17.sp,

                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}


/* =========================================================
   INSTRUCTIONS
========================================================= */

@Composable
fun InstructionsScreen(
    onStartVerification: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp)
    ) {

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Customer Instructions",

            fontSize =
                28.sp,

            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                "Please follow these steps before starting secure verification.",

            fontSize =
                14.sp,

            color =
                Color.Gray
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        InstructionItem(
            number = "1",
            text =
                "Keep your face clearly visible"
        )

        InstructionItem(
            number = "2",
            text =
                "Make sure the environment is well lit"
        )

        InstructionItem(
            number = "3",
            text =
                "Look directly at the camera"
        )

        InstructionItem(
            number = "4",
            text =
                "Follow the on-screen movement prompt"
        )

        InstructionItem(
            number = "5",
            text =
                "Biometric verification is processed on-device"
        )

        Spacer(
            modifier =
                Modifier.weight(1f)
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier =
                    Modifier.padding(18.dp)
            ) {

                Text(
                    text =
                        "🔒 100% ON-DEVICE",

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF2E7D32)
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text =
                        "Your biometric verification data stays on this device.",

                    fontSize =
                        13.sp,

                    color =
                        Color.Gray
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Button(
            onClick =
                onStartVerification,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp),

            shape =
                RoundedCornerShape(14.dp)
        ) {

            Text(
                text =
                    "Start Secure Verification",

                fontSize =
                    17.sp,

                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}


/* =========================================================
   CAMERA SCREEN
========================================================= */


@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    customerPhoneNumber: String,
    onVerificationComplete: () -> Unit,
    onSuspicious: () -> Unit
) {

    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

    var faceDetected by remember {
        mutableStateOf(false)
    }

    var blinkDetected by remember {
        mutableStateOf(false)
    }

    var liveDetected by remember {
        mutableStateOf(false)
    }

    var facingCamera by remember {
        mutableStateOf(false)
    }

    var phoneDetected by remember {
        mutableStateOf(false)
    }

    var suspiciousDetected by remember {
        mutableStateOf(false)
    }

    var smsSent by remember {
        mutableStateOf(false)
    }

    var smsRequestPending by remember {
        mutableStateOf(false)
    }

    var securityExplanation by remember {

        mutableStateOf(
            "Ask the customer to look directly at the camera."
        )
    }

    var previousX by remember {
        mutableStateOf<Float?>(null)
    }

    val cameraExecutor =
        remember {
            Executors.newSingleThreadExecutor()
        }


    /* =====================================================
       SMS PERMISSION
    ===================================================== */

    val smsPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            smsRequestPending =
                false

            if (
                granted &&
                !smsSent
            ) {

                val sent =
                    sendSecuritySms(
                        context,
                        customerPhoneNumber
                    )

                if (sent) {

                    smsSent =
                        true

                    securityExplanation =
                        "⚠ Possible replay attack detected. Security SMS sent to the registered customer."

                } else {

                    securityExplanation =
                        "⚠ Possible replay attack detected. SMS could not be sent."
                }
            }
        }


    /* =====================================================
       SEND SMS ON REPLAY
    ===================================================== */

    fun triggerSecuritySms() {

        if (
            smsSent ||
            smsRequestPending
        ) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            val sent =
                sendSecuritySms(
                    context,
                    customerPhoneNumber
                )

            if (sent) {

                smsSent =
                    true

                securityExplanation =
                    "⚠ Possible replay attack detected. Security SMS sent to the registered customer."
            }

        } else {

            smsRequestPending =
                true

            Handler(
                Looper.getMainLooper()
            ).post {

                smsPermissionLauncher.launch(
                    Manifest.permission.SEND_SMS
                )
            }
        }
    }


    /* =====================================================
       MEDIAPIPE OBJECT DETECTOR
    ===================================================== */

    val objectDetector =
        remember {

            try {

                val baseOptions =
                    BaseOptions
                        .builder()
                        .setModelAssetPath(
                            "efficientdet_lite0.tflite"
                        )
                        .build()

                val detectorOptions =
                    ObjectDetector
                        .ObjectDetectorOptions
                        .builder()
                        .setBaseOptions(
                            baseOptions
                        )
                        .setScoreThreshold(
                            0.50f
                        )
                        .setMaxResults(
                            5
                        )
                        .setRunningMode(
                            RunningMode.IMAGE
                        )
                        .build()

                ObjectDetector
                    .createFromOptions(
                        context,
                        detectorOptions
                    )

            } catch (
                e: Exception
            ) {

                null
            }
        }


    DisposableEffect(Unit) {

        onDispose {

            cameraExecutor.shutdown()

            objectDetector?.close()
        }
    }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
    ) {

        Text(
            text =
                "Secure KYC Verification",

            fontSize =
                25.sp,

            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        /* =====================================================
           SECURITY CHECK
        ===================================================== */

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(14.dp)
        ) {

            Column(
                modifier =
                    Modifier.padding(14.dp)
            ) {

                Text(
                    text =
                        "SECURITY CHECK",

                    fontSize =
                        14.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    LivenessStatus(
                        "FACE",
                        faceDetected
                    )

                    LivenessStatus(
                        "BLINK",
                        blinkDetected
                    )

                    LivenessStatus(
                        "MOVEMENT",
                        liveDetected
                    )

                    LivenessStatus(
                        "CAMERA",
                        facingCamera
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text =
                            if (phoneDetected)
                                "●"
                            else
                                "○",

                        color =
                            if (phoneDetected)
                                Color(0xFFC62828)
                            else
                                Color.Gray,

                        fontSize =
                            14.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.width(5.dp)
                    )

                    Text(
                        text =
                            if (phoneDetected)
                                "PHONE / SCREEN DETECTED"
                            else
                                "NO EXTRA PHONE DETECTED",

                        fontSize =
                            11.sp,

                        fontWeight =
                            FontWeight.Medium,

                        color =
                            if (phoneDetected)
                                Color(0xFFC62828)
                            else
                                Color.DarkGray
                    )
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        /* =====================================================
           REPLAY WARNING
        ===================================================== */

        if (phoneDetected) {

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(12.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(0xFFFFEBEE)
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp)
                ) {

                    Text(
                        text =
                            "⚠ POSSIBLE REPLAY ATTACK",

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFFC62828)
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            "A phone/display appears to be visible in the verification view. Verification has been paused for security review.",

                        fontSize =
                            13.sp,

                        color =
                            Color(0xFFC62828)
                    )

                    if (smsSent) {

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        Text(
                            text =
                                "📩 Security SMS sent to registered customer.",

                            fontSize =
                                13.sp,

                            fontWeight =
                                FontWeight.SemiBold,

                            color =
                                Color(0xFF2E7D32)
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

        } else if (suspiciousDetected) {

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(12.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(0xFFFFEBEE)
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp)
                ) {

                    Text(
                        text =
                            "⚠ SUSPICIOUS ACTIVITY",

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFFC62828)
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            "Customer is not facing the camera. Verification requires security review.",

                        fontSize =
                            13.sp,

                        color =
                            Color(0xFFC62828)
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

        } else if (
            faceDetected &&
            facingCamera
        ) {

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(12.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(0xFFE8F5E9)
                    )
            ) {

                Text(
                    text =
                        "✓ CUSTOMER FACING CAMERA",

                    modifier =
                        Modifier.padding(14.dp),

                    fontSize =
                        16.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF2E7D32)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }


        /* =====================================================
           CAMERA PREVIEW
        ===================================================== */

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .background(
                        Color.Black,
                        RoundedCornerShape(16.dp)
                    )
        ) {

            AndroidView(

                modifier =
                    Modifier.fillMaxSize(),

                factory = { viewContext ->

                    val previewView =
                        PreviewView(
                            viewContext
                        )

                    val cameraProviderFuture =
                        ProcessCameraProvider
                            .getInstance(
                                viewContext
                            )

                    cameraProviderFuture
                        .addListener({

                            val cameraProvider =
                                cameraProviderFuture
                                    .get()

                            val preview =
                                Preview.Builder()
                                    .build()
                                    .also { previewUseCase ->

                                        previewUseCase
                                            .surfaceProvider =
                                            previewView
                                                .surfaceProvider
                                    }


                            /* =================================
                               IMAGE ANALYSIS
                            ================================= */

                            val imageAnalysis =
                                ImageAnalysis
                                    .Builder()
                                    .setBackpressureStrategy(
                                        ImageAnalysis
                                            .STRATEGY_KEEP_ONLY_LATEST
                                    )
                                    .build()


                            /* =================================
                               FACE DETECTOR
                            ================================= */

                            val faceOptions =
                                FaceDetectorOptions
                                    .Builder()
                                    .setPerformanceMode(
                                        FaceDetectorOptions
                                            .PERFORMANCE_MODE_FAST
                                    )
                                    .setClassificationMode(
                                        FaceDetectorOptions
                                            .CLASSIFICATION_MODE_ALL
                                    )
                                    .build()

                            val faceDetector =
                                FaceDetection
                                    .getClient(
                                        faceOptions
                                    )


                            /* =================================
                               FRAME ANALYZER
                            ================================= */

                            imageAnalysis
                                .setAnalyzer(
                                    cameraExecutor
                                ) { imageProxy ->

                                    val mediaImage =
                                        imageProxy.image

                                    if (
                                        mediaImage == null
                                    ) {

                                        imageProxy.close()

                                    } else {

                                        val rotation =
                                            imageProxy
                                                .imageInfo
                                                .rotationDegrees


                                        /* =========================
                                           FACE ANALYSIS
                                        ========================= */

                                        val inputImage =
                                            InputImage
                                                .fromMediaImage(
                                                    mediaImage,
                                                    rotation
                                                )

                                        faceDetector
                                            .process(
                                                inputImage
                                            )
                                            .addOnSuccessListener { faces ->

                                                if (
                                                    faces.isNotEmpty()
                                                ) {

                                                    val face =
                                                        faces[0]

                                                    faceDetected =
                                                        true


                                                    /* BLINK */

                                                    if (
                                                        detectBlink(
                                                            face
                                                        )
                                                    ) {

                                                        blinkDetected =
                                                            true
                                                    }


                                                    /* CAMERA FACING */

                                                    facingCamera =
                                                        isFacingCamera(
                                                            face
                                                        )


                                                    /* MOVEMENT */

                                                    val currentX =
                                                        face
                                                            .boundingBox
                                                            .centerX()
                                                            .toFloat()

                                                    val oldX =
                                                        previousX

                                                    if (
                                                        oldX != null
                                                    ) {

                                                        val movement =
                                                            kotlin.math
                                                                .abs(
                                                                    currentX -
                                                                            oldX
                                                                )

                                                        if (
                                                            movement > 8f
                                                        ) {

                                                            liveDetected =
                                                                true
                                                        }
                                                    }

                                                    previousX =
                                                        currentX


                                                    /* CAMERA WARNING */

                                                    if (
                                                        !facingCamera &&
                                                        !phoneDetected
                                                    ) {

                                                        suspiciousDetected =
                                                            true

                                                        securityExplanation =
                                                            "⚠ Customer is not facing the camera. Verification paused for security."

                                                    } else if (
                                                        facingCamera &&
                                                        !phoneDetected
                                                    ) {

                                                        suspiciousDetected =
                                                            false

                                                        securityExplanation =
                                                            "✓ Customer is facing the camera. Continue verification."
                                                    }


                                                    /* FINAL FACE CHECK */

                                                    if (
                                                        faceDetected &&
                                                        facingCamera &&
                                                        blinkDetected &&
                                                        liveDetected &&
                                                        !phoneDetected
                                                    ) {

                                                        suspiciousDetected =
                                                            false

                                                        securityExplanation =
                                                            "✓ Face, blink, movement and camera-facing signals verified locally."
                                                    }

                                                } else {

                                                    faceDetected =
                                                        false

                                                    blinkDetected =
                                                        false

                                                    liveDetected =
                                                        false

                                                    facingCamera =
                                                        false

                                                    suspiciousDetected =
                                                        false

                                                    previousX =
                                                        null

                                                    securityExplanation =
                                                        "No face detected. Ask the customer to look directly at the camera."
                                                }
                                            }


                                        /* =========================
                                           PHONE DETECTION
                                        ========================= */

                                        if (
                                            objectDetector != null
                                        ) {

                                            try {

                                                val mpImage =
                                                    MediaImageBuilder(
                                                        mediaImage
                                                    ).build()

                                                val result =
                                                    objectDetector
                                                        .detect(
                                                            mpImage
                                                        )

                                                var foundPhone =
                                                    false

                                                for (
                                                detection
                                                in result.detections()
                                                ) {

                                                    for (
                                                    category
                                                    in detection.categories()
                                                    ) {

                                                        val label =
                                                            category
                                                                .categoryName()

                                                        val score =
                                                            category
                                                                .score()

                                                        if (
                                                            label.equals(
                                                                "cell phone",
                                                                ignoreCase = true
                                                            ) &&
                                                            score >= 0.50f
                                                        ) {

                                                            foundPhone =
                                                                true

                                                            break
                                                        }
                                                    }

                                                    if (
                                                        foundPhone
                                                    ) {
                                                        break
                                                    }
                                                }

                                                phoneDetected =
                                                    foundPhone

                                                if (
                                                    foundPhone
                                                ) {

                                                    suspiciousDetected =
                                                        true

                                                    securityExplanation =
                                                        "⚠ Possible replay attack. A phone/display was detected in the verification view."

                                                    /*
                                                     * SMS ONLY ONCE
                                                     */
                                                    Handler(
                                                        Looper.getMainLooper()
                                                    ).post {

                                                        triggerSecuritySms()
                                                    }
                                                }

                                            } catch (
                                                e: Exception
                                            ) {

                                                e.printStackTrace()
                                            }
                                        }

                                        imageProxy.close()
                                    }
                                }


                            /* =================================
                               START FRONT CAMERA
                            ================================= */

                            cameraProvider.unbindAll()

                            cameraProvider.bindToLifecycle(

                                lifecycleOwner,

                                CameraSelector
                                    .DEFAULT_FRONT_CAMERA,

                                preview,

                                imageAnalysis
                            )

                        }, ContextCompat.getMainExecutor(viewContext))

                    previewView
                }
            )
        }

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        /* =====================================================
           LOCAL SECURITY ANALYSIS
        ===================================================== */

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(14.dp)
        ) {

            Column(
                modifier =
                    Modifier.padding(14.dp)
            ) {

                Text(
                    text =
                        "LOCAL SECURITY ANALYSIS",

                    fontSize =
                        13.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text =
                        securityExplanation,

                    fontSize =
                        13.sp,

                    color =
                        Color.DarkGray
                )

                if (smsSent) {

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "📩 Customer security SMS sent",

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.SemiBold,

                        color =
                            Color(0xFF2E7D32)
                    )
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                "100% ON-DEVICE • No biometric data leaves this device",

            fontSize =
                11.sp,

            color =
                Color.Gray
        )

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )


        /* =====================================================
           FINAL ACTION
        ===================================================== */

        if (
            faceDetected &&
            facingCamera &&
            blinkDetected &&
            liveDetected &&
            !suspiciousDetected &&
            !phoneDetected
        ) {

            Button(
                onClick =
                    onVerificationComplete,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),

                shape =
                    RoundedCornerShape(14.dp)
            ) {

                Text(
                    text =
                        "Complete KYC Verification",

                    fontWeight =
                        FontWeight.SemiBold
                )
            }

        } else if (
            suspiciousDetected ||
            phoneDetected
        ) {

            Button(
                onClick =
                    onSuspicious,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFFC62828)
                    ),

                shape =
                    RoundedCornerShape(14.dp)
            ) {

                Text(
                    text =
                        if (phoneDetected)
                            "View Replay Security Result"
                        else
                            "View Security Result",

                    fontWeight =
                        FontWeight.SemiBold
                )
            }

        } else {

            Text(
                text =
                    "Complete all security checks to continue.",

                modifier =
                    Modifier.fillMaxWidth(),

                fontSize =
                    13.sp,

                color =
                    Color.Gray
            )
        }
    }
}


/* =========================================================
   CAMERA FACING
========================================================= */

fun isFacingCamera(
    face: Face
): Boolean {

    val yaw =
        face.headEulerAngleY

    val roll =
        face.headEulerAngleZ

    return kotlin.math.abs(yaw) < 15f &&
            kotlin.math.abs(roll) < 15f
}


/* =========================================================
   BLINK
========================================================= */

fun detectBlink(
    face: Face
): Boolean {

    val leftEye =
        face.leftEyeOpenProbability

    val rightEye =
        face.rightEyeOpenProbability

    if (
        leftEye != null &&
        rightEye != null
    ) {

        return leftEye < 0.4f &&
                rightEye < 0.4f
    }

    return false
}


/* =========================================================
   RESULT SCREEN
========================================================= */

@Composable
fun VerificationResultScreen(
    verified: Boolean,
    customerName: String,
    onNextCustomer: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Spacer(
            modifier =
                Modifier.height(60.dp)
        )

        if (verified) {

            Icon(
                imageVector =
                    Icons.Default.CheckCircle,

                contentDescription =
                    null,

                tint =
                    Color(0xFF2E7D32),

                modifier =
                    Modifier.size(80.dp)
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Text(
                text =
                    "VERIFICATION COMPLETE",

                fontSize =
                    23.sp,

                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "✓ VERIFIED",

                color =
                    Color(0xFF2E7D32),

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            ResultItem(
                "Face presence verified"
            )

            ResultItem(
                "Camera-facing verified"
            )

            ResultItem(
                "Blink response verified"
            )

            ResultItem(
                "Natural movement verified"
            )

            ResultItem(
                "No visible replay device detected"
            )

            ResultItem(
                "Local security checks completed"
            )

            Spacer(
                modifier =
                    Modifier.height(25.dp)
            )

            PrivacyCard()

        } else {

            Icon(
                imageVector =
                    Icons.Default.Error,

                contentDescription =
                    null,

                tint =
                    Color(0xFFC62828),

                modifier =
                    Modifier.size(80.dp)
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Text(
                text =
                    "VERIFICATION PAUSED",

                fontSize =
                    23.sp,

                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "⚠ NOT VERIFIED",

                color =
                    Color(0xFFC62828),

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Text(
                text =
                    "$customerName verification was flagged for security review.",

                fontSize =
                    15.sp
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(14.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(0xFFFFF8E1)
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Text(
                        text =
                            "🔔 SECURITY ALERT",

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFFE65100)
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Security notification sent to the registered customer.",

                        fontSize =
                            13.sp
                    )




                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "🔒 12-hour verification hold activated.",

                        fontSize =
                            13.sp,

                        color =
                            Color(0xFFC62828),

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            PrivacyCard()
        }

        Spacer(
            modifier =
                Modifier.weight(1f)
        )

        Button(
            onClick =
                onNextCustomer,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp),

            shape =
                RoundedCornerShape(14.dp)
        ) {

            Text(
                text =
                    "Next Customer",

                fontSize =
                    17.sp
            )
        }
    }
}


/* =========================================================
   SECURITY LOCK SCREEN
========================================================= */

@Composable
fun SecurityLockScreen(
    customerName: String,
    onBack: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text =
                "⚠️",

            fontSize =
                60.sp
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Text(
            text =
                "VERIFICATION TEMPORARILY LOCKED",

            fontSize =
                22.sp,

            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        Text(
            text =
                "$customerName has a security hold.",

            fontSize =
                16.sp
        )

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        Text(
            text =
                "Re-verification is temporarily unavailable for 12 hours.",

            fontSize =
                14.sp,

            color =
                Color.Gray
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier =
                    Modifier.padding(18.dp)
            ) {

                Text(
                    text =
                        "🔔 SECURITY ALERT",

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFFC62828)
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Customer security SMS sent.",

                    fontSize =
                        13.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "🏦 Bank review required",

                    fontSize =
                        13.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "🔒 12-hour verification hold",

                    fontSize =
                        13.sp
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Button(
            onClick =
                onBack,

            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(14.dp)
        ) {

            Text(
                text =
                    "Back to KYC Queue"
            )
        }
    }
}


/* =========================================================
   LIVENESS STATUS
========================================================= */

@Composable
fun LivenessStatus(
    label: String,
    detected: Boolean
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                if (detected)
                    "●"
                else
                    "○",

            color =
                if (detected)
                    Color(0xFF2E7D32)
                else
                    Color.Gray,

            fontSize =
                14.sp
        )

        Spacer(
            modifier =
                Modifier.width(4.dp)
        )

        Text(
            text =
                label,

            fontSize =
                11.sp,

            fontWeight =
                FontWeight.Medium
        )
    }
}


/* =========================================================
   INSTRUCTION ITEM
========================================================= */

@Composable
fun InstructionItem(
    number: String,
    text: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Surface(
            shape =
                RoundedCornerShape(50),

            color =
                Color(0xFFE8EAF6)
        ) {

            Text(
                text =
                    number,

                modifier =
                    Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 6.dp
                    ),

                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            modifier =
                Modifier.width(12.dp)
        )

        Text(
            text =
                text,

            fontSize =
                14.sp
        )
    }
}


/* =========================================================
   RESULT ITEM
========================================================= */

@Composable
fun ResultItem(
    text: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector =
                Icons.Default.CheckCircle,

            contentDescription =
                null,

            tint =
                Color(0xFF2E7D32),

            modifier =
                Modifier.size(20.dp)
        )

        Spacer(
            modifier =
                Modifier.width(10.dp)
        )

        Text(
            text =
                text,

            fontSize =
                14.sp
        )
    }
}


/* =========================================================
   PRIVACY CARD
========================================================= */

@Composable
fun PrivacyCard() {

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier =
                Modifier.padding(18.dp)
        ) {

            Text(
                text =
                    "100% ON-DEVICE PROCESSING",

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF2E7D32)
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    "Biometric verification is processed locally. No biometric video is sent to a cloud server.",

                fontSize =
                    13.sp,

                color =
                    Color.Gray
            )
        }
    }
}