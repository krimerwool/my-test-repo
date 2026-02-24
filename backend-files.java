//CropSDK.kt
package com.example.cropanalysissdk

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log

class CropSDK(context: Context) {

    private val engine = ModelEngine(context)
    private val TAG = "CropSDK"

    // CONFIG (Matching Python)
    private val CONF_THRESH = 0.65f
    private val VOTE_THRESH = 3

    fun analyze(fullImage: Bitmap): AnalysisResult {
        val startTime = SystemClock.elapsedRealtime()

        Log.d(TAG, "Starting Crop Analysis Pipeline")


        // 1. BARREN CHECK (Full Image)
        var (isGlobalBarren, barrenConf) = engine.isBarren(fullImage)

        Log.d(TAG, "Run 1: Fallow Detection:")
        Log.d(TAG, "  IsFallow: $isGlobalBarren")
        Log.d(TAG, "  Confidence: ${barrenConf * 100}%")

        // 2. FULL IMAGE PASS (Always run - matching Python behavior)
        val (fullCropName, fullCropConf) = engine.classifyCrop(fullImage)
        val fullImageDetection = CropDetection(
            cropName = fullCropName,
            confidence = fullCropConf,
            votes = 1,
            location = "Entire Field",
            source = "Full-Image-Prior"
        )

        Log.d(TAG, "STEP 2 - Full Image Crop Detection:")
        Log.d(TAG, "  Crop: $fullCropName")
        Log.d(TAG, "  Confidence: ${fullCropConf * 100}%")

        // 🔴 SMART FIX: If Barren says "Barren" but Crop Model is >90% confident, it's a False Positive
        if (isGlobalBarren && fullCropConf > 0.90f) {
            Log.d(TAG, " Fallow detected but crop confidence is ${fullCropConf * 100}%")
            Log.d(TAG, "  → Treating as FALSE POSITIVE, marking as Non-Fallow")
            isGlobalBarren = false
            // Keep barrenConf as the crop confidence
        }

        // 3. GRID PASS
        val allDetections = mutableListOf<CropDetection>()

        Log.d(TAG, "STEP 3 - Grid Analysis:")

        // 🔴 CRITICAL: Even if barren, we still run grid (matching Python comment in spec)
        // But we optimize by skipping if we're confident it's barren
        val shouldRunGrid = !isGlobalBarren || fullCropConf > 0.70f

        if (shouldRunGrid) {
            Log.d(TAG, "  Running grid detection...")

            val grids = ImageUtils.splitBitmap(fullImage, 3, 3, false) +
                    ImageUtils.splitBitmap(fullImage, 3, 3, true) // Offset

            grids.forEachIndexed { index, cell ->
                if (!ImageUtils.isMostlySky(cell)) {
                    // Check if this region is barren
                    val (localBarren, localBarrenConf) = engine.isBarren(cell)

                    if (!localBarren) {
                        val (crop, conf) = engine.classifyCrop(cell)

                        if (conf >= CONF_THRESH) {
                            val locName = if (index < 9) ImageUtils.getGridLocationName(index) else "Offset-Region"
                            val source = if (index < 9) "Grid-Aligned" else "Grid-Offset"

                            allDetections.add(CropDetection(crop, conf, 1, locName, source))

                            Log.d(TAG, "     Region $index: $crop (${conf * 100}%) at $locName")
                        } else {
                            Log.d(TAG, "     Region $index: Confidence too low (${conf * 100}%)")
                        }
                    } else {
                        Log.d(TAG, "     Region $index: Barren (skipped)")
                    }
                } else {
                    Log.d(TAG, "     Region $index: Sky detected (skipped)")
                }
            }

            Log.d(TAG, "  Total valid detections: ${allDetections.size}")
        } else {
            Log.d(TAG, "  Skipping grid (high confidence barren)")
        }

        // 4. VOTING ENGINE (Python Replica)
        Log.d(TAG, "STEP 4 - Voting Engine:")

        val votesMap = allDetections.groupBy { it.cropName }
        var finalResults = mutableListOf<CropDetection>()

        for ((crop, items) in votesMap) {
            val voteCount = items.size
            val avgConf = items.map { it.confidence }.average().toFloat()

            Log.d(TAG, "  Crop: $crop")
            Log.d(TAG, "    Votes: $voteCount")
            Log.d(TAG, "    Avg Confidence: ${avgConf * 100}%")

            // Logic: Is this the same crop as the Full Image?
            val isFullPrior = (crop == fullCropName)

            // Dynamic Thresholds
            val voteReq = if (isFullPrior) 2 else VOTE_THRESH
            val confReq = if (isFullPrior) 0.60f else CONF_THRESH
            val isSuperHighConf = (voteCount >= 1 && avgConf >= 0.99f)

            Log.d(TAG, "    IsFullPrior: $isFullPrior")
            Log.d(TAG, "    Required - Votes: $voteReq, Confidence: ${confReq * 100}%")

            if ((voteCount >= voteReq && avgConf >= confReq) || isSuperHighConf) {
                // Formatting Location String
                val distinctLocs = items.map { it.location }
                    .distinct()
                    .filter { it != "Offset-Region" }
                    .joinToString(", ")

                finalResults.add(CropDetection(
                    cropName = crop,
                    confidence = avgConf,
                    votes = voteCount,
                    location = if (distinctLocs.isEmpty()) "Multiple Regions" else distinctLocs,
                    source = if (isFullPrior) "Full-Image-Prior" else "Grid-Consensus"
                ))

                Log.d(TAG, "     ACCEPTED")
            } else {
                Log.d(TAG, "     REJECTED (insufficient votes/confidence)")
            }
        }

        Log.d(TAG, "  Final results after voting: ${finalResults.size}")

        // 5. CRITICAL OVERRIDE (Python: "Replacing ensemble output with full-image result")
        val finalCropNames = finalResults.map { it.cropName }

        // 🔴 FIXED: This now works correctly because barren logic is fixed
        if (!isGlobalBarren && fullCropName != "Unknown" && !finalCropNames.contains(fullCropName)) {
            Log.d(TAG, "STEP 5 - Override Triggered:")
            Log.d(TAG, "   Full image crop '$fullCropName' not in ensemble results")
            Log.d(TAG, "  → Replacing ensemble with full-image result")

            finalResults.clear()
            finalResults.add(CropDetection(
                cropName = fullCropName,
                confidence = fullCropConf,
                votes = 1,
                location = "Entire Field",
                source = "Full-Image-Override"
            ))
        }

        // 6. FALLBACK (if everything else failed)
        if (!isGlobalBarren && finalResults.isEmpty() && fullCropName != "Unknown") {
            Log.d(TAG, "STEP 6 - Fallback Mode:")
            Log.d(TAG, "  No ensemble results, using full-image fallback")

            finalResults.add(CropDetection(
                cropName = fullCropName,
                confidence = fullCropConf,
                votes = 0,
                location = "Entire Field",
                source = "Fallback-Mode"
            ))
        }

        val executionTime = SystemClock.elapsedRealtime() - startTime

        Log.d(TAG, "Analysis Complete")
        Log.d(TAG, "  Final Detections: ${finalResults.size}")
        Log.d(TAG, "  Execution Time: $executionTime ms")


        return AnalysisResult(
            isBarren = isGlobalBarren,
            barrenConfidence = barrenConf,
            fullImageAnalysis = fullImageDetection,
            gridDetections = finalResults,
            executionTimeMs = executionTime
        )
    }
}

//ModelEngine.kt
package com.example.cropanalysissdk

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp

class ModelEngine(context: Context) {

    private var barrenInterpreter: Interpreter? = null
    private var cropInterpreter: Interpreter? = null

    // Labels (Maize=0, Rice=1, Soybean=2, Sugarcane=3)
    private val cropLabels = listOf("Maize", "Rice", "Soybean", "Sugarcane")

    init {
        val options = Interpreter.Options().apply { numThreads = 4 }

        // Load models with correct filenames
        barrenInterpreter = Interpreter(FileUtil.loadMappedFile(context, "barren_vs_crop_model_v3.tflite"), options)
        cropInterpreter = Interpreter(FileUtil.loadMappedFile(context, "phase1_model.tflite"), options)
    }

    /**
     * 🔴 FIXED: Corrected barren detection logic to match Python/Streamlit
     *
     * Barren Model Output Interpretation:
     * - prob > 0.5  → Non-Barren (CROP present)
     * - prob ≤ 0.5  → Barren land
     *
     * @return Pair(isBarren: Boolean, confidence: Float)
     */

//    fun isBarren(bitmap: Bitmap): Pair<Boolean, Float> {
//        val imageProcessor = ImageProcessor.Builder()
//            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
//            // NO NORMALIZATION: Input stays 0-255 (matches training)
//            .build()
//
//        var tImage = TensorImage(DataType.FLOAT32)
//        tImage.load(bitmap)
//        tImage = imageProcessor.process(tImage)
//
//        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)
//        barrenInterpreter?.run(tImage.buffer, outputBuffer.buffer.rewind())
//
//        val prob = outputBuffer.floatArray[0]
//
//        // Safety: If NaN, assume Crop (safer fallback)
//        if (prob.isNaN()) return Pair(false, 0.5f)
//
//        // 🔴 CORRECTED LOGIC:
//        // prob > 0.5 means CROP (non-barren)
//        // prob ≤ 0.5 means BARREN
//        val isCrop = prob > 0.5f
//        val isBarren = !isCrop
//
//        // Confidence = distance from decision boundary
//        val confidence = if (isCrop) prob else (1.0f - prob)
//
//        return Pair(isBarren, confidence)
//    }

    fun isBarren(bitmap: Bitmap): Pair<Boolean, Float> {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            // NO NORMALIZATION: Input stays 0-255 (matches training)
            .add(NormalizeOp(0f, 255f))
            .build()

        var tImage = TensorImage(DataType.FLOAT32)
        tImage.load(bitmap)
        tImage = imageProcessor.process(tImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)
        barrenInterpreter?.run(tImage.buffer, outputBuffer.buffer.rewind())

        val prob = outputBuffer.floatArray[0]

        // Safety: If NaN, assume Crop (safer fallback)
        if (prob.isNaN()) return Pair(false, 0.5f)

        // 🔴 CORRECTED LOGIC:
        // prob > 0.5 means CROP (non-barren)
        // prob ≤ 0.5 means BARREN
        val isCrop = prob > 0.5f
        val isBarren = !isCrop

        // Confidence = distance from decision boundary
        val confidence = if (isCrop) prob else (1.0f - prob)

        return Pair(isBarren, confidence)
    }
    /**
     * Classifies the crop type from a bitmap.
     *
     * @return Pair(cropName: String, confidence: Float)
     */
    fun classifyCrop(bitmap: Bitmap): Pair<String, Float> {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(260, 260, ResizeOp.ResizeMethod.BILINEAR))
            // NO NORMALIZATION: Input stays 0-255 (model has internal preprocessing)
            .build()

        var tImage = TensorImage(DataType.FLOAT32)
        tImage.load(bitmap)
        tImage = imageProcessor.process(tImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 4), DataType.FLOAT32)
        cropInterpreter?.run(tImage.buffer, outputBuffer.buffer.rewind())

        val probs = outputBuffer.floatArray

        var maxIdx = -1
        var maxProb = -1.0f

        for (i in probs.indices) {
            // NaN Safety
            if (probs[i].isNaN()) continue

            if (probs[i] > maxProb) {
                maxProb = probs[i]
                maxIdx = i
            }
        }

        return if (maxIdx != -1 && maxProb > 0.0f) {
            Pair(cropLabels[maxIdx], maxProb)
        } else {
            Pair("Unknown", 0f)
        }
    }
}

//AnalysisResult.kt
package com.example.cropanalysissdk

/**
 * The final result object containing all table data.
 */
data class AnalysisResult(
    // Status Column
    val isBarren: Boolean,
    val barrenConfidence: Float,

    // Full Crop Result Column
    val fullImageAnalysis: CropDetection,

    // Detected Crops List (Grid Ensemble)
    val gridDetections: List<CropDetection>,

    // Performance
    val executionTimeMs: Long
)

/**
 * Represents a single row in the result table.
 */
data class CropDetection(
    val cropName: String,
    val confidence: Float,
    val votes: Int,
    val location: String, // e.g., "Top-Left", "Center"
    val source: String    // "Full-Image-Prior", "Grid-Aligned", etc.
)

//ImageUtils.kt
  package com.example.cropanalysissdk

import android.graphics.Bitmap
import android.graphics.Color

object ImageUtils {

    /**
     * Splits bitmap into rows x cols.
     * If offset=true, shifts the grid by 50% width/height.
     */
    fun splitBitmap(bitmap: Bitmap, rows: Int, cols: Int, offset: Boolean = false): List<Bitmap> {
        val pieces = mutableListOf<Bitmap>()
        val width = bitmap.width
        val height = bitmap.height

        val cellW = width / cols
        val cellH = height / rows

        val startX = if (offset) cellW / 2 else 0
        val startY = if (offset) cellH / 2 else 0

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = startX + (c * cellW)
                val y = startY + (r * cellH)

                // Ensure we don't go out of bounds
                if (x + cellW <= width && y + cellH <= height) {
                    pieces.add(Bitmap.createBitmap(bitmap, x, y, cellW, cellH))
                }
            }
        }
        return pieces
    }

    /**
     * returns true if the image is mostly Blue/White (Sky) to save processing time.
     */
    fun isMostlySky(bitmap: Bitmap): Boolean {
        var skyPixels = 0
        val totalPixels = 20 * 20 // Sample 400 pixels for speed

        val scaled = Bitmap.createScaledBitmap(bitmap, 20, 20, true)

        for (x in 0 until 20) {
            for (y in 0 until 20) {
                val pixel = scaled.getPixel(x, y)
                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)

                val hue = hsv[0]
                val sat = hsv[1]
                val valBri = hsv[2]

                // Sky logic: Blue hue (190-250) OR Very Bright White/Grey
                if ((hue in 190f..250f) || (valBri > 0.9f && sat < 0.2f)) {
                    skyPixels++
                }
            }
        }
        return (skyPixels.toFloat() / totalPixels.toFloat()) > 0.6f
    }

    /**
     * Maps grid index (0-8) to human readable location
     */
    fun getGridLocationName(index: Int): String {
        return when(index) {
            0 -> "Top-Left"
            1 -> "Top-Center"
            2 -> "Top-Right"
            3 -> "Mid-Left"
            4 -> "Center"
            5 -> "Mid-Right"
            6 -> "Bottom-Left"
            7 -> "Bottom-Center"
            8 -> "Bottom-Right"
            else -> "Unknown Region"
        }
    }
}

//build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.cropanalysissdk"
    compileSdk = 34 // Use your project's compile SDK version

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // CRITICAL: Prevent Android from compressing .tflite files,
    // otherwise the native interpreter cannot map them directly from assets.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    androidResources {
        noCompress += listOf("tflite")
    }
}

dependencies {
    // TFLite Libraries
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1") // Optional acceleration

    // Core Android libraries
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
