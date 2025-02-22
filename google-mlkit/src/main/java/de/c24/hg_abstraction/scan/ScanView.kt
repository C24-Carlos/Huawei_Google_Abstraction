package de.c24.hg_abstraction.scan

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.camera.core.*
import androidx.camera.core.FocusMeteringAction.FLAG_AF
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import de.c24.hg_abstraction.core_scan.ScanViewCore
import de.c24.hg_abstraction.scan.databinding.ScanViewBinding
import kotlinx.android.synthetic.main.activity_scan.view.*
import kotlinx.android.synthetic.main.scan_view.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ScanView@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), ScanViewCore {

    private val binding = ScanViewBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    override var resultListener: ((String) -> Unit)? = null

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner

    private val AUTO_FOCUS_RETRY_TO_MILLIS = 3000L

     override fun startCamera(activity: Activity) {
         initBarCodeScanner()
        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()
            bindPreview(activity,cameraProvider)
        }, ContextCompat.getMainExecutor(context))

    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    @androidx.camera.lifecycle.ExperimentalCameraProviderConfiguration
    private fun bindPreview(activity: Activity,cameraProvider : ProcessCameraProvider) {

        var preview: Preview = Preview.Builder()
            .build()

        var cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        cameraProvider.unbindAll()

        preview.setSurfaceProvider(previewview.surfaceProvider)

        val camera = cameraProvider.bindToLifecycle(
            activity as LifecycleOwner,
            cameraSelector,
            preview,
            setupAnalyzer()
        )

        val view = activity.previewview
        view.postDelayed(object : Runnable {
            override fun run() {
                focusCamera(camera)
                view.postDelayed(this, AUTO_FOCUS_RETRY_TO_MILLIS)
            }
        }, AUTO_FOCUS_RETRY_TO_MILLIS)
    }

    private fun focusCamera(camera: Camera) {
        val autoFocusPoint = SurfaceOrientedMeteringPointFactory(1f, 1f)
            .createPoint(.5f, .5f)
        val autoFocusAction = FocusMeteringAction.Builder(
            autoFocusPoint, FLAG_AF
        )
            .disableAutoCancel()
            .build()
        camera.cameraControl.startFocusAndMetering(autoFocusAction)
    }


    private fun initBarCodeScanner() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC)
            .build()

        barcodeScanner = BarcodeScanning.getClient(options)
    }

    private fun setupAnalyzer(): ImageAnalysis {
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setImageQueueDepth(30)
            .build()

        val qrCodeAnalyzer = YourImageAnalyzer(barcodeScanner) { qrCodes ->
            qrCodes.firstOrNull()?.rawValue?.let { qrToken ->
                cameraExecutor?.shutdown()
                cameraProviderFuture?.get()?.unbindAll()
                resultListener?.invoke(qrToken)

            }
        }

        cameraExecutor?.let {
            imageAnalysis.setAnalyzer(it, qrCodeAnalyzer)
        }

        return imageAnalysis
    }

     override fun destroyView() {
        cameraExecutor.shutdown()
     }
}