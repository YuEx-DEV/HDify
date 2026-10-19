package com.example.hdify.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hdify.R
import com.example.hdify.databinding.FragmentSettingsBinding
import com.example.hdify.util.safeNavigate
import com.example.hdify.util.setOnLiquidClickListener
import com.example.hdify.util.setOnSingleClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private var isProgrammaticThemeChange = false
    private var isProgrammaticLangChange = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        animateEntrance()

        // Diagnostics Navigation (safe debounced click & liquid bounce)
        binding.cardMenuHardwareInfo.setOnLiquidClickListener {
            findNavController().safeNavigate(R.id.action_settings_to_hardware_info)
        }

        binding.cardMenuBenchmark.setOnLiquidClickListener {
            findNavController().safeNavigate(R.id.action_settings_to_benchmark)
        }

        binding.cardMenuLogs.setOnLiquidClickListener {
            findNavController().safeNavigate(R.id.action_settings_to_logs)
        }

        binding.cardMenuAiModels.setOnLiquidClickListener {
            AiModelsBottomSheet.newInstance().show(childFragmentManager, AiModelsBottomSheet.TAG)
        }

        // Developer Profile / Credits Click
        binding.cardDeveloperCredit.setOnLiquidClickListener {
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.tiktok.com/@yuukaonlyone_?_r=1&_t=ZS-9ANT1Wtl7kF")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_tiktok_open_failed), Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvAppVersion.text = "HDify v${com.example.hdify.BuildConfig.VERSION_NAME} (Build ${com.example.hdify.BuildConfig.VERSION_CODE})"

        // Language Toggle Listener
        binding.toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !isProgrammaticLangChange) {
                when (checkedId) {
                    R.id.btn_lang_id -> viewModel.setLanguage("in")
                    R.id.btn_lang_en -> viewModel.setLanguage("en")
                    else -> viewModel.setLanguage("system")
                }
            }
        }

        // Theme Toggle Listener
        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !isProgrammaticThemeChange) {
                when (checkedId) {
                    R.id.btn_theme_light -> viewModel.setTheme("light")
                    R.id.btn_theme_dark -> viewModel.setTheme("dark")
                    else -> viewModel.setTheme("system")
                }
            }
        }

        // Switches
        binding.switchVerboseLog.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setVerboseLogging(isChecked)
        }

        binding.switchAllowCpu.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAllowCpuFallback(isChecked)
        }

        // State collection
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.languageMode.collect { lang ->
                        val targetId = when (lang) {
                            "in" -> R.id.btn_lang_id
                            "en" -> R.id.btn_lang_en
                            else -> R.id.btn_lang_system
                        }
                        if (binding.toggleLanguage.checkedButtonId != targetId) {
                            isProgrammaticLangChange = true
                            binding.toggleLanguage.check(targetId)
                            isProgrammaticLangChange = false
                        }
                    }
                }

                launch {
                    viewModel.themeMode.collect { mode ->
                        val targetId = when (mode) {
                            "light" -> R.id.btn_theme_light
                            "dark" -> R.id.btn_theme_dark
                            else -> R.id.btn_theme_system
                        }
                        if (binding.toggleTheme.checkedButtonId != targetId) {
                            isProgrammaticThemeChange = true
                            binding.toggleTheme.check(targetId)
                            isProgrammaticThemeChange = false
                        }
                    }
                }

                launch {
                    viewModel.verboseLogging.collect { verbose ->
                        if (binding.switchVerboseLog.isChecked != verbose) {
                            binding.switchVerboseLog.isChecked = verbose
                        }
                    }
                }

                launch {
                    viewModel.allowCpuFallback.collect { allow ->
                        if (binding.switchAllowCpu.isChecked != allow) {
                            binding.switchAllowCpu.isChecked = allow
                        }
                    }
                }
            }
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.settingsRoot.setPadding(systemBars.left, 0, systemBars.right, 0)
            val bottomNavReserve = (136 * resources.displayMetrics.density).toInt()
            val totalBottomPadding = systemBars.bottom + bottomNavReserve
            binding.settingsScroll.setPadding(
                binding.settingsScroll.paddingLeft,
                binding.settingsScroll.paddingTop,
                binding.settingsScroll.paddingRight,
                totalBottomPadding
            )
            insets
        }
    }

    private fun animateEntrance() {
        val views = listOf(
            binding.cardMenuHardwareInfo,
            binding.cardMenuBenchmark,
            binding.cardMenuLogs,
            binding.cardMenuAiModels,
            binding.cardDeveloperCredit,
            binding.tvAppVersion
        )
        com.example.hdify.util.animateCascadeEntrance(views, startDelay = 15L, staggerDelay = 35L, duration = 320L)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
