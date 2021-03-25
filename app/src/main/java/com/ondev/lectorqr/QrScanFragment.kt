package com.ondev.lectorqr

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.ondev.lectorqr.utilities.buildBarcodeScanner
import com.ondev.lectorqr.utilities.getBarcodeReticleBox
import com.ondev.lectorqr.views.BarcodeLoadingGraphic
import com.ondev.lectorqr.views.BarcodeReticleGraphic
import com.ondev.lectorqr.views.CameraReticleAnimator
import com.ondev.lectorqr.views.GraphicOverlay
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import com.ondev.lectorqr.databinding.FragmentQrScanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QrScanFragment : Fragment() {
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraReticleAnimator: CameraReticleAnimator
    private lateinit var binding: FragmentQrScanBinding
    private val permissionCode = 1234
    private lateinit var bottomSheet: BottomSheetBehavior<ConstraintLayout>
    private lateinit var itemTitle: TextView
    private lateinit var itemImage: ImageView
    private lateinit var itemDescription: TextView
    private lateinit var direction: NavDirections
    private var cameraInitialized = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentQrScanBinding.inflate(inflater, container, false)
        bottomSheet = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    bindCamera(cameraProvider)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }
        })
        itemTitle = binding.itemTitle
        itemImage = binding.circleImage
        itemDescription = binding.itemDescription
        binding.seeMoreButton.setOnClickListener {
            findNavController().navigate(direction)
        }
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val context = requireContext()

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            barcodeScanner = buildBarcodeScanner()
            cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                cameraProvider = cameraProviderFuture.get()
                bindCamera(cameraProvider)
                cameraInitialized = true
            }, ContextCompat.getMainExecutor(context))
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            permissionCode -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    barcodeScanner = buildBarcodeScanner()
                    cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
                    cameraProviderFuture.addListener({
                        cameraProvider = cameraProviderFuture.get()
                        bindCamera(cameraProvider)
                        cameraInitialized = true
                    }, ContextCompat.getMainExecutor(context))
                } else {
                    findNavController().navigateUp()
                }
                return
            }
            else -> {

            }
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun bindCamera(cameraProvider: ProcessCameraProvider) {
        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val preview = buildPreview()
        val imageAnalysis = buildImageAnalysis()

        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            imageAnalysis,
            preview
        )
    }

    private fun buildPreview(): Preview {
        val preview: Preview = Preview.Builder().build()
        preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
        return preview
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun buildImageAnalysis(): ImageAnalysis {
        cameraReticleAnimator = CameraReticleAnimator(binding.graphicOverlay)
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            ContextCompat.getMainExecutor(requireContext()), { imageProxy ->
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val mediaImage = imageProxy.image

                if (mediaImage != null) {
                    binding.graphicOverlay.setCameraImageSize(
                        Size(
                            mediaImage.width,
                            mediaImage.height
                        )
                    )
                    val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val barcodeInCenter = barcodes.firstOrNull { barcode ->
                                val boundingBox = barcode.boundingBox ?: return@firstOrNull false
                                val box = binding.graphicOverlay.translateRect(boundingBox)
                                val reticleBox = getBarcodeReticleBox(binding.graphicOverlay)
                                reticleBox.contains(box)
                            }

                            binding.graphicOverlay.clear()
                            if (barcodeInCenter == null) {
                                cameraReticleAnimator.start()
                                binding.graphicOverlay.add(
                                    BarcodeReticleGraphic(
                                        binding.graphicOverlay,
                                        cameraReticleAnimator
                                    )
                                )
                            } else {
                                val value = barcodeInCenter.rawValue.toString()
                                if (value.isNotEmpty()) {
                                    cameraReticleAnimator.cancel()
                                    // Barcode size in the camera view is sufficient.
                                    cameraProvider.unbindAll()
                                    val loadingAnimator =
                                        createLoadingAnimator(binding.graphicOverlay, value)
                                    loadingAnimator.start()
                                    binding.graphicOverlay.add(
                                        BarcodeLoadingGraphic(
                                            binding.graphicOverlay,
                                            loadingAnimator
                                        )
                                    )
                                } else {
                                    cameraReticleAnimator.start()
                                    binding.graphicOverlay.add(
                                        BarcodeReticleGraphic(
                                            binding.graphicOverlay,
                                            cameraReticleAnimator
                                        )
                                    )
                                }
                            }
                            binding.graphicOverlay.invalidate()
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
            })
        return imageAnalysis
    }

    private fun createLoadingAnimator(
        graphicOverlay: GraphicOverlay,
        readied: String
    ): ValueAnimator {
        val endProgress = 1.1f
        return ValueAnimator.ofFloat(0f, endProgress).apply {
            duration = 2000
            addUpdateListener {
                if ((animatedValue as Float).compareTo(endProgress) >= 0) {
                    graphicOverlay.clear()
                    lifecycleScope.launch(Dispatchers.IO) {
                        launch(Dispatchers.Main) {
                            itemTitle.text = "Dato"
                            itemDescription.text = readied
                            bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
                        }
                    }
                } else {
                    graphicOverlay.invalidate()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (cameraInitialized) {
            cameraProvider.unbindAll()
        }
    }
}