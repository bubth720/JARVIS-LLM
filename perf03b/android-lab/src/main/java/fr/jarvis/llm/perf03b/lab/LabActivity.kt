package fr.jarvis.llm.perf03b.lab

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Independent, permission-free laboratory. No prompts, synthetic tokens, model import or transport. */
class LabActivity : Activity() {
    private val owner = LabProcessOwner
    private val displayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observation: Job? = null
    private lateinit var status: TextView
    private lateinit var exercise: Button
    private lateinit var stop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun text(id: Int) = TextView(this).apply {
            setText(id)
            setPadding(0, 0, 0, (14 * resources.displayMetrics.density).toInt())
            content.addView(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        text(R.string.title).textSize = 26f
        text(R.string.scope)
        text(R.string.not_run)
        text(R.string.blocked)
        text(R.string.lifecycle_explanation)
        status = text(R.string.ready)
        exercise = Button(this).apply {
            setText(R.string.exercise)
            setOnClickListener { owner.exercise() }
            content.addView(this)
        }
        stop = Button(this).apply {
            setText(R.string.stop)
            setOnClickListener { owner.requestStop() }
            content.addView(this)
        }
        text(R.string.build_identity).text = getString(R.string.build_identity, BuildConfig.SOURCE_COMMIT)
        text(R.string.gate_b)
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onStart() {
        super.onStart()
        observation = displayScope.launch {
            owner.state.collect { state ->
                status.setText(state.message)
                exercise.isEnabled = !state.running && !state.blocked
                stop.isEnabled = state.running
            }
        }
    }
    override fun onStop() {
        owner.requestStop()
        observation?.cancel()
        super.onStop()
    }
    override fun onDestroy() {
        displayScope.cancel()
        super.onDestroy()
    }
}

private data class LabDisplayState(val message: Int = R.string.ready, val running: Boolean = false, val blocked: Boolean = false)

/** One owner until process death: finish/relaunch and multiple Activities share the same pending cleanup. */
private object LabProcessOwner {
    private val controller = LabLifecycleController()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutable = MutableStateFlow(LabDisplayState())
    val state = mutable.asStateFlow()
    private var test: Job? = null

    @Synchronized fun exercise() {
        if (test?.isActive == true || mutable.value.blocked) return
        mutable.value = LabDisplayState(R.string.running, running = true)
        test = scope.launch {
            try {
                val result = controller.exercise()
                mutable.value = when (result) {
                    LabLifecycleController.Result.PASS -> LabDisplayState(R.string.passed)
                    LabLifecycleController.Result.BUSY -> LabDisplayState(R.string.busy, running = true)
                    LabLifecycleController.Result.ERROR -> LabDisplayState(R.string.failed, blocked = true)
                    LabLifecycleController.Result.CLEANUP_FAILED -> LabDisplayState(R.string.cleanup_failed, blocked = true)
                    LabLifecycleController.Result.QUARANTINED -> LabDisplayState(R.string.quarantined, blocked = true)
                }
            } catch (cancelled: CancellationException) {
                mutable.value = LabDisplayState(R.string.quarantined, blocked = true)
                throw cancelled
            }
        }
    }
    fun requestStop() = controller.requestStop()
}
