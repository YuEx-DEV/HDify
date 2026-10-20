package com.example.hdify.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.hdify.R
import com.example.hdify.data.hardware.HardwareInfoRepository
import com.example.hdify.databinding.ActivityMainBinding
import com.example.hdify.logging.CrashIncident
import com.example.hdify.logging.HdLogger
import com.example.hdify.logging.LogZipExporter
import com.example.hdify.logging.StartupCrashDetector
import com.example.hdify.util.safeNavigate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    @Inject
    lateinit var hardwareRepo: HardwareInfoRepository

    @Inject
    lateinit var jobDao: com.example.hdify.data.database.JobDao

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        HdLogger.i("MainActivity", "POST_NOTIFICATIONS granted: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkNotificationPermission()

        // UI-R6: Edge-to-edge WindowInsets handling for floating liquid dock
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCoordinator) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navHostFragment.setPadding(0, systemBars.top, 0, 0)
            
            // Adjust floating dock bottom margin based on system gesture/nav bar insets
            val lp = binding.bottomNavigation.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            if (lp != null) {
                val floatingBaseMargin = (16 * resources.displayMetrics.density).toInt()
                lp.bottomMargin = systemBars.bottom + floatingBaseMargin
                binding.bottomNavigation.layoutParams = lp
            }
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        // Show bottom navigation bar ONLY on top-level tabs with fluid slide/fade animation
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.nav_home, R.id.nav_queue, R.id.nav_history, R.id.nav_settings -> {
                    if (binding.bottomNavigation.visibility != View.VISIBLE) {
                        binding.bottomNavigation.visibility = View.VISIBLE
                        binding.bottomNavigation.animate().cancel()
                        binding.bottomNavigation.alpha = 0f
                        binding.bottomNavigation.translationY = 40f
                        binding.bottomNavigation.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(280)
                            .setInterpolator(com.example.hdify.util.FluidDecelerateInterpolator)
                            .withLayer()
                            .start()
                    }
                }
                else -> {
                    if (binding.bottomNavigation.visibility == View.VISIBLE) {
                        binding.bottomNavigation.animate().cancel()
                        binding.bottomNavigation.animate()
                            .alpha(0f)
                            .translationY(40f)
                            .setDuration(180)
                            .setInterpolator(com.example.hdify.util.FluidDecelerateInterpolator)
                            .withLayer()
                            .withEndAction {
                                binding.bottomNavigation.visibility = View.GONE
                            }
                            .start()
                    }
                }
            }
        }

        // FR-LOG-07: Detect abnormal termination of previous session
        val incident = StartupCrashDetector.checkForPreviousCrash(this)
        if (incident.hasIncident) {
            showCrashReportDialog(incident)
        }

        // Check for unfinished/interrupted jobs to offer checkpoint resume (FR-VID-10, Bagian 10)
        lifecycleScope.launch {
            if (!com.example.hdify.service.UpscaleForegroundService.isServiceActive) {
                val interruptedJobs = jobDao.getInterruptedJobs()
                val firstInterrupted = interruptedJobs.firstOrNull()
                if (firstInterrupted != null) {
                    showRecoveryDialog(firstInterrupted)
                }
            }
        }

        // Check for updates in background (non-blocking)
        lifecycleScope.launch {
            kotlinx.coroutines.delay(3000)
            com.example.hdify.util.GitHubUpdateChecker.checkForUpdate(this@MainActivity, isManual = false)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showRecoveryDialog(job: com.example.hdify.data.database.JobEntity) {
        val msg = getString(
            R.string.recovery_dialog_msg,
            job.id,
            job.engineName,
            job.progress
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recovery_dialog_title)
            .setMessage(msg)
            .setIcon(R.drawable.ic_warning)
            .setPositiveButton(R.string.btn_resume_job) { dialog, _ ->
                dialog.dismiss()
                lifecycleScope.launch {
                    jobDao.updateJob(job.copy(status = "PENDING"))
                    com.example.hdify.service.UpscaleForegroundService.start(this@MainActivity)
                }
            }
            .setNegativeButton(R.string.btn_discard_job) { dialog, _ ->
                dialog.dismiss()
                lifecycleScope.launch {
                    jobDao.updateJob(job.copy(status = "CANCELLED", completedAt = System.currentTimeMillis()))
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showCrashReportDialog(incident: CrashIncident) {
        val msg = getString(R.string.crash_dialog_msg, incident.description)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_dialog_title)
            .setMessage(msg)
            .setIcon(R.drawable.ic_warning)
            .setPositiveButton(R.string.crash_dialog_view) { dialog, _ ->
                StartupCrashDetector.acknowledgeIncident(this, incident)
                dialog.dismiss()
                navController.safeNavigate(R.id.nav_log_crash_report)
            }
            .setNeutralButton(R.string.crash_dialog_share) { dialog, _ ->
                StartupCrashDetector.acknowledgeIncident(this, incident)
                dialog.dismiss()
                shareDiagnosticZip()
            }
            .setNegativeButton(R.string.crash_dialog_dismiss) { dialog, _ ->
                StartupCrashDetector.acknowledgeIncident(this, incident)
                dialog.dismiss()
            }
            .setOnDismissListener {
                StartupCrashDetector.acknowledgeIncident(this, incident)
            }
            .setCancelable(false)
            .show()
    }

    private fun shareDiagnosticZip() {
        lifecycleScope.launch {
            try {
                val zipUri = LogZipExporter.createDiagnosticZip(this@MainActivity, hardwareRepo)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, zipUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
            } catch (t: Throwable) {
                HdLogger.e("MainActivity", null, "HD-SYS-999", "Failed to export diagnostic zip", t)
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            StartupCrashDetector.markSessionCleanExit(this)
        }
        super.onDestroy()
    }
}
