package com.pbz.healthmanager.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.pbz.healthmanager.analysis.MedicineOcrAnalyzer
import java.util.concurrent.Executors
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pbz.healthmanager.viewmodel.HealthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

@Composable
fun ScanScreen(
    viewModel: HealthViewModel,
    tipText: String = "请将药品对准方框",
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        Log.d("MedicineScan", "ScanScreen entered")
        Toast.makeText(context, "扫描页已打开", Toast.LENGTH_SHORT).show()
    }
    
    // 权限状态
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 权限申请
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // 检查并请求权限
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            Log.d("MedicineScan", "camera permission missing, requesting")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            Toast.makeText(context, "未授予相机权限，无法扫描", Toast.LENGTH_SHORT).show()
        } else {
            Log.d("MedicineScan", "camera permission granted")
        }
    }

    var lastOcrToastAt by remember { mutableLongStateOf(0L) }
    val recognizer = remember {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // 释放资源
    DisposableEffect(Unit) {
        onDispose {
            recognizer.close()
            cameraExecutor.shutdown()
        }
    }

    val debugStatus by viewModel.scanDebugStatus.collectAsState()
    val debugData by viewModel.scanDebugData.collectAsState()
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var lastSubmitAt by remember { mutableLongStateOf(0L) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Camera Preview (Full Screen)
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            
                            val preview = CameraPreview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .setJpegQuality(100)
                                .build()
                            imageCaptureRef = imageCapture
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        processImageProxy(imageProxy, recognizer) { fullText, approval ->
                                            val now = System.currentTimeMillis()
                                            if (now - lastSubmitAt < 350L) return@processImageProxy
                                            lastSubmitAt = now
                                            if (fullText.isBlank() && approval.isNullOrBlank()) {
                                                viewModel.resetCaptureStatus()
                                                return@processImageProxy
                                            }
                                            viewModel.processMedicineOcrResult(
                                                MedicineOcrAnalyzer.OcrResult(fullText, approval)
                                            )
                                        }
                                    }
                                }

                            // 图像分析用例
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                Log.e("MedicineScan", "bindToLifecycle failed", e)
                                Toast.makeText(ctx, "相机绑定失败: ${e.message.orEmpty()}", Toast.LENGTH_SHORT).show()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. 呼吸对焦十字 & 激光扫描线动画
            ScanAnimationOverlay()

            // 3. 顶部 UI (返回按钮和标题)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { onBackClick?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "药品识别",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(
                        Color.Black.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp)
                    ).padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // 4. 底部提示文字
            Text(
                text = tipText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
            Button(
                onClick = {
                    val imageCapture = imageCaptureRef
                    if (imageCapture == null) {
                        Toast.makeText(context, "相机未就绪", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    Toast.makeText(context, "正在拍照识别", Toast.LENGTH_SHORT).show()
                    imageCapture.takePicture(
                        cameraExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val bitmap = imageProxyToBitmap(image)
                                    if (bitmap == null) {
                                        Log.e("MedicineScan", "capture bitmap conversion failed")
                                        Handler(Looper.getMainLooper()).post {
                                            Toast.makeText(context, "拍照图像为空，请重试", Toast.LENGTH_SHORT).show()
                                        }
                                        return
                                    }
                                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                                    recognizer.process(inputImage)
                                        .addOnSuccessListener { visionText ->
                                            val fullText = visionText.text.orEmpty()
                                            val approval = extractApprovalFromText(fullText)
                                            val nowToast = System.currentTimeMillis()
                                            if (nowToast - lastOcrToastAt > 1200L) {
                                                val tip = if (approval.isNullOrBlank()) {
                                                    "识别到文字: ${fullText.take(18)}"
                                                } else {
                                                    "识别到准字: $approval"
                                                }
                                                Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                                                lastOcrToastAt = nowToast
                                            }
                                            if (fullText.isNotBlank() || !approval.isNullOrBlank()) {
                                                viewModel.processMedicineOcrResult(
                                                    MedicineOcrAnalyzer.OcrResult(fullText, approval),
                                                    bitmap
                                                )
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("MedicineScan", "mlkit recognize failed", e)
                                            Handler(Looper.getMainLooper()).post {
                                                val msg = e.message.orEmpty()
                                                if (msg.contains("optional module", ignoreCase = true)) {
                                                    Toast.makeText(
                                                        context,
                                                        "首次使用需下载文字识别组件，请保持网络后重试",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    Toast.makeText(context, "识别失败: $msg", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(context, "拍照完成，正在识别", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Throwable) {
                                    Log.e("MedicineScan", "capture success callback crashed", e)
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(context, "拍照处理异常: ${e.message.orEmpty()}", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("MedicineScan", "capture failed", exception)
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, "拍照失败: ${exception.message.orEmpty()}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("拍照识别", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = debugStatus,
                    color = Color(0xFFD1FAE5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = debugData,
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun processImageProxy(
    imageProxy: ImageProxy,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    onResult: (String, String?) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        onResult("", null)
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            val text = visionText.text.orEmpty()
            onResult(text, extractApprovalFromText(text))
        }
        .addOnFailureListener {
            onResult("", null)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun extractApprovalFromText(text: String): String? {
    val strict = Regex("国药准字\\s*([A-Z])(\\d{8})", RegexOption.IGNORE_CASE).find(text)
    if (strict != null) {
        return "国药准字${strict.groupValues[1].uppercase()}${strict.groupValues[2]}"
    }
    val compact = text.replace("\\s".toRegex(), "")
    val match = Regex("国[药藥]准[字宇]?([A-Za-z])([0-9OoIl|SsBb]{8})", RegexOption.IGNORE_CASE).find(compact)
        ?: return null
    val letter = match.groupValues[1].uppercase()
    val digits = match.groupValues[2].uppercase().map {
        when (it) {
            'O' -> '0'
            'I', 'L', '|' -> '1'
            'S' -> '5'
            'B' -> '8'
            else -> it
        }
    }.joinToString("")
    return if (digits.all { it.isDigit() }) "国药准字$letter$digits" else null
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val mediaImage = image.image ?: return null
    val decoded = when {
        image.planes.size == 1 -> {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        image.planes.size >= 3 -> {
            val width = mediaImage.width
            val height = mediaImage.height
            val nv21 = yuv420888ToNv21(image)
            if (nv21.isEmpty()) return null
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            val ok = yuvImage.compressToJpeg(Rect(0, 0, width, height), 95, out)
            if (!ok) return null
            val bytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        else -> null
    } ?: return null
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val media = image.image ?: return ByteArray(0)
    val width = media.width
    val height = media.height
    val yPlane = media.planes[0]
    val uPlane = media.planes[1]
    val vPlane = media.planes[2]
    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    var offset = ySize
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
            val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
            if (vIndex >= vBuffer.limit() || uIndex >= uBuffer.limit()) continue
            nv21[offset++] = vBuffer.get(vIndex)
            nv21[offset++] = uBuffer.get(uIndex)
        }
    }
    return nv21
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun ScanScreenPreview() {
    HealthManagerTheme {
        ScanScreen(viewModel = viewModel())
    }
}
