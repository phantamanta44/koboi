package io.github.phantamanta44.koboi.plugin.artemis

import io.github.phantamanta44.koboi.Loggr
import javafx.fxml.FXML
import javafx.scene.Group
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ToggleButton

class ArtemisMainWindow(private val session: ArtemisDebugSession) : ArtemisStageWrapper("Artemis", "artemis_main") {

    @FXML
    private lateinit var btnFreeze: ToggleButton
    @FXML
    private lateinit var groupStepButtons: Group
    @FXML
    private lateinit var enableModCpu: CheckMenuItem
    @FXML
    private lateinit var enableModMem: CheckMenuItem
    @FXML
    private lateinit var enableModDis: CheckMenuItem
    @FXML
    private lateinit var enableModVram: CheckMenuItem
    @FXML
    private lateinit var enableModBreaker: CheckMenuItem

    init {
        stage.setOnCloseRequest { session.target.endDebugSession() }
    }

    @FXML
    fun initialize() {
        session.propFrozen.bindBidirectional(btnFreeze.selectedProperty())
        groupStepButtons.disableProperty().bind(session.propFrozen.not())
        session.modCpu.propEnabled.bindBidirectional(enableModCpu.selectedProperty())
        session.modDis.propEnabled.bindBidirectional(enableModDis.selectedProperty())
    }

    fun show() = stage.show()

    @FXML
    fun handleFreezeState() {
        if (!session.frozen) session.target.unfreeze()
    }

    private var stepPredicate: (() -> Boolean)? = null

    @FXML
    fun stepT() {
        session.target.unfreeze()
    }

    @FXML
    fun stepM() {
        val startCycle = session.target.tCycle
        stepPredicate = { session.target.tCycle - startCycle >= 4 }
        session.target.unfreeze()
    }

    private var waitingForInstruction = false

    @FXML
    fun stepI() {
        waitingForInstruction = true
        stepPredicate = { !waitingForInstruction }
        session.target.unfreeze()
    }

    @FXML
    fun stepH() {
        Loggr.warn("No impl!")
    }

    @FXML
    fun stepV() {
        Loggr.warn("No impl!")
    }

    fun checkStep(): Boolean {
        stepPredicate?.let {
            return if (it()) {
                stepPredicate = null
                true
            } else {
                false
            }
        }
        return true
    }

    fun onCpuExecute() {
        waitingForInstruction = false
    }

}