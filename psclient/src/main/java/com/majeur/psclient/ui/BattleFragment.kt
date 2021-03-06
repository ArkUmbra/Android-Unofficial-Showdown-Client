package com.majeur.psclient.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.majeur.psclient.R
import com.majeur.psclient.databinding.FragmentBattleBinding
import com.majeur.psclient.io.AssetLoader
import com.majeur.psclient.io.BattleAudioManager
import com.majeur.psclient.io.GlideHelper
import com.majeur.psclient.model.battle.*
import com.majeur.psclient.model.common.Colors.healthColor
import com.majeur.psclient.model.common.Colors.statusColor
import com.majeur.psclient.model.common.Stats
import com.majeur.psclient.model.common.Type
import com.majeur.psclient.model.pokemon.BasePokemon
import com.majeur.psclient.model.pokemon.BattlingPokemon
import com.majeur.psclient.model.pokemon.SidePokemon
import com.majeur.psclient.service.ShowdownService
import com.majeur.psclient.service.observer.BattleRoomMessageObserver
import com.majeur.psclient.util.*
import com.majeur.psclient.util.html.Html
import com.majeur.psclient.widget.BattleLayout
import com.majeur.psclient.widget.BattleTipPopup
import com.majeur.psclient.widget.BattleTipPopup.OnBindPopupViewListener
import kotlinx.coroutines.launch

class BattleFragment : BaseFragment(), BattleRoomMessageObserver.UiCallbacks {

    private val observer get() = service!!.battleMessageObserver

    private lateinit var glideHelper: GlideHelper
    private lateinit var audioManager: BattleAudioManager
    private lateinit var assetLoader: AssetLoader

    private lateinit var inactiveBattleOverlayDrawable: InactiveBattleOverlayDrawable

    private lateinit var battleTipPopup: BattleTipPopup
    private var lastActionRequest: BattleActionRequest? = null
    private var timerEnabled = false
    private var soundEnabled = false
    private var wasPlayingBattleMusicWhenPaused = false

    private var _binding: FragmentBattleBinding? = null
    private val binding get() = _binding!!


    private var _observedRoomId: String? = null
    var observedRoomId: String?
        get() = _observedRoomId
        set(observedRoomId) {
            _observedRoomId = observedRoomId
            observer.observedRoomId = observedRoomId
        }

    fun battleRunning(): Boolean {
        return observer.battleRunning
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        glideHelper = mainActivity.glideHelper
        assetLoader = mainActivity.assetLoader
        battleTipPopup = BattleTipPopup(context)
        battleTipPopup.setOnBindPopupViewListener(mOnBindPopupViewListener)
        audioManager = BattleAudioManager(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentBattleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        inactiveBattleOverlayDrawable = InactiveBattleOverlayDrawable(resources)
        binding.apply {
            battleLog.movementMethod = LinkMovementMethod()
            actionContainer.actionContainer.animate().interpolator = OvershootInterpolator(1.4f)
            actionContainer.actionContainer.animate().duration = 500
            overlayImage.setImageDrawable(inactiveBattleOverlayDrawable)
            battleActionWidget.setOnRevealListener { reveal ->
                actionContainer.actionContainer.animate()
                        .setStartDelay(if (reveal) 0L else 350L)
                        .translationY(if (reveal) 3 * binding.battleLogContainer.getHeight() / 5f else 0f)
                        .start()

                if (reveal) undoContainer.undoContainer.apply {
                        translationX = binding.actionContainer.actionContainer.width.toFloat()
                        alpha = 0f
                    }
                else undoContainer.apply {
                    undoButton.isEnabled = true
                    undoContainer.animate()
                            .setStartDelay(350)
                            .translationX(0f)
                            .alpha(1f)
                            .start()
                }
            }
            actionContainer.timerButton.setOnClickListener(extraClickListener)
            actionContainer.forfeitButton.setOnClickListener(extraClickListener)
            actionContainer.sendButton.setOnClickListener(extraClickListener)
            undoContainer.undoButton.setOnClickListener(extraClickListener)
            undoContainer.undoContainer.post {
                undoContainer.undoContainer.apply {
                    translationX = actionContainer.actionContainer.width.toFloat()
                    alpha = 0f
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (audioManager.isPlayingBattleMusic) {
            audioManager.pauseBattleMusic()
            wasPlayingBattleMusicWhenPaused = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasPlayingBattleMusicWhenPaused) {
            audioManager.playBattleMusic()
            wasPlayingBattleMusicWhenPaused = false
        }
    }

    override fun onServiceBound(service: ShowdownService) {
        super.onServiceBound(service)
        service.battleMessageObserver.uiCallbacks = this
    }

    override fun onServiceWillUnbound(service: ShowdownService) {
        super.onServiceWillUnbound(service)
        service.battleMessageObserver.uiCallbacks = null
    }

    private fun prepareBattleFieldUi() = binding.apply {
        battleLayout.alpha = 1f
        overlayImage.alpha = 1f
        overlayImage.setImageDrawable(null)
    }

    private fun clearBattleFieldUi() {
        binding.battleLayout.animate().alpha(0f).withEndAction {
            binding.apply {
                battleLayout.getSideView(Player.TRAINER).clearAllSides()
                battleLayout.getSideView(Player.FOE).clearAllSides()
                battleLayout.getStatusViews(Player.TRAINER).forEach { it.clear() }
                battleLayout.getStatusViews(Player.FOE).forEach { it.clear() }
                overlayImage.setImageDrawable(null)
                trainerInfo.clear()
                foeInfo.clear()
                overlayImage.alpha = 0f
                overlayImage.setImageDrawable(inactiveBattleOverlayDrawable)
                overlayImage.animate().alpha(1f)
            }
        }.start()
    }

    private val extraClickListener = View.OnClickListener { view ->
        if (observedRoomId == null) return@OnClickListener
        when (view) {
            binding.actionContainer.forfeitButton -> {
                if (battleRunning())
                    AlertDialog.Builder(requireActivity())
                        .setMessage("Do you really want to forfeit this battle ?")
                        .setPositiveButton("Forfeit") { _: DialogInterface?, _: Int -> forfeit() }
                        .setNegativeButton("Cancel", null)
                        .show()
            }
            binding.actionContainer.sendButton -> {
                val dialogView: View = layoutInflater.inflate(R.layout.dialog_battle_message, null)
                val editText = dialogView.findViewById<EditText>(R.id.edit_text_team_name)
                MaterialAlertDialogBuilder(requireActivity())
                        .setPositiveButton("Send") { _: DialogInterface?, _: Int ->
                            val regex = "[{}:\",|\\[\\]]".toRegex()
                            val input = editText.text.toString().replace(regex, "")
                            service?.sendRoomMessage(observedRoomId, input)
                        }
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("\"gg\"") { _: DialogInterface?, _: Int -> service?.sendRoomMessage(observedRoomId, "gg") }
                        .setView(dialogView)
                        .show()
                editText.requestFocus()
            }
            binding.actionContainer.timerButton -> {
                if (battleRunning()) sendTimerCommand(!timerEnabled)
            }
            binding.undoContainer.undoButton -> {
                binding.undoContainer.undoButton.isEnabled = false
                sendUndoCommand()
                observer.reAskForRequest()
            }
        }
    }

    private val mOnBindPopupViewListener = OnBindPopupViewListener { anchorView: View, titleView: TextView, descView: TextView, placeHolderTop: ImageView, placeHolderBottom: ImageView ->
        when (val data = anchorView.getTag(R.id.battle_data_tag)) {
            is BattlingPokemon -> bindBattlingPokemonTipPopup(data, titleView, descView, placeHolderTop, placeHolderBottom)
            is Move -> bindMoveTipPopup(data, titleView, descView, placeHolderTop, placeHolderBottom)
            is SidePokemon -> bindSidePokemonPopup(data, titleView, descView, placeHolderTop, placeHolderBottom)
        }
    }

    private fun bindBattlingPokemonTipPopup(pokemon: BattlingPokemon, titleView: TextView,
                                            descView: TextView, placeHolderTop: ImageView,
                                            placeHolderBottom: ImageView) {
        placeHolderTop.setImageDrawable(null)
        placeHolderBottom.setImageDrawable(null)

        titleView.apply {
            text = pokemon.name
            append(" ")
            append(pokemon.gender.small())
            append(" l.")
            append(pokemon.level.toString())
        }

        descView.apply {
            text = ""
            if (pokemon.species != pokemon.name) append(pokemon.species)

            pokemon.condition?.let { condition ->
                append("HP: ".small())
                append("%.1f%% ".format(condition.health * 100).bold().color(healthColor(condition.health)))
                if (pokemon.trainer) append("(${condition.hp}/${condition.maxHp})".small())
                condition.status?.let { append(it.toUpperCase().small().bg(statusColor(it))) }
                descView.append("\n")
            }

            var ability: String? = null
            if (pokemon.trainer && lastActionRequest?.side != null) {
                val sidePokemon = lastActionRequest!!.side[pokemon.position]
                if (pokemon.transformSpecies == null) { // Ditto case
                    append("Atk:".small())
                    append(pokemon.statModifiers.calcReadableStat("atk", sidePokemon.stats.atk))
                    append(" Def:".small())
                    append(pokemon.statModifiers.calcReadableStat("def", sidePokemon.stats.def))
                    append(" Spa:".small())
                    append(pokemon.statModifiers.calcReadableStat("spa", sidePokemon.stats.spa))
                    append(" Spd:".small())
                    append(pokemon.statModifiers.calcReadableStat("spd", sidePokemon.stats.spd))
                    append(" Spe:".small())
                    append(pokemon.statModifiers.calcReadableStat("spe", sidePokemon.stats.spe))
                    append("\n")
                }
                append("Ability: ".small())
                ability = sidePokemon.ability
                append(sidePokemon.ability)
                append("\n")
                descView.append("Item: ".small())
                descView.append(sidePokemon.item)
                fragmentScope.launch {
                    assetLoader.item(sidePokemon.item.toId())?.let { item ->
                        Utils.replace(descView.editableText, sidePokemon.item, item.name)
                    }
                }
            }

            fragmentScope.launch {
                val dexPokemon = assetLoader.dexPokemon(pokemon.species.toId())
                if (dexPokemon == null) {
                    append("No dex entry for ${pokemon.species}")
                    return@launch
                }
                placeHolderTop.setImageResource(Type.getResId(dexPokemon.firstType))
                dexPokemon.secondType?.let { placeHolderBottom.setImageResource(Type.getResId(it)) }

                if (pokemon.trainer) {
                    if (ability == null) return@launch
                    val abilityName = if (dexPokemon.hiddenAbility?.toId() == ability) dexPokemon.hiddenAbility
                    else  dexPokemon.abilities.firstOrNull { it.toId() == ability }
                    if (abilityName != null) Utils.replace(descView.editableText, ability, abilityName)
                    return@launch
                }

                if (dexPokemon.abilities.isNotEmpty() && dexPokemon.hiddenAbility != null) {
                    append("Possible abilities: ".small())
                    append(dexPokemon.abilities.plus(dexPokemon.hiddenAbility).joinToString(", "))
                    append("\n")
                } else {
                    append("Ability: ".small())
                    append(dexPokemon.hiddenAbility ?: dexPokemon.abilities.firstOrNull() ?: "none")
                    append("\n")
                }
                val speedRange = Stats.calculateSpeedRange(pokemon.level, dexPokemon.baseStats.spe, "Random Battle", observer.gen)
                append("Speed: ".small())
                append("${speedRange[0]} to ${speedRange[1]}")
                append(" (before items/abilities/modifiers)".small())
            }
        }
    }

    private fun bindMoveTipPopup(move: Move, titleView: TextView, descView: TextView, placeHolderTop: ImageView,
                                 placeHolderBottom: ImageView) {
        var moveName: String? = null
        if (move.maxflag) moveName = move.maxDetails?.name ?: move.maxMoveId
        if (move.zflag) moveName = move.zName
        if (moveName == null) moveName = move.name
        titleView.text = moveName
        descView.text = ""
        var priority = -20
        if (move.maxflag) priority = move.maxDetails?.priority ?: 0
        if (move.zflag) priority = move.zDetails?.priority ?: 0
        if (priority == -20) priority = move.details?.priority ?: 0
        when {
            priority > 1 -> {
                descView.append("Nearly always moves first ")
                descView.append(Utils.italicText(Utils.toStringSigned(priority)))
            }
            priority <= -1 -> {
                descView.append("Nearly always moves last ")
                descView.append(Utils.italicText(Utils.toStringSigned(priority)))
            }
            priority == 1 -> {
                descView.append("Usually moves first ")
                descView.append(Utils.italicText(Utils.toStringSigned(priority)))
            }

//        if (move.zflag) {
//            titleView.setText(move.zName);
//            descView.setText("PP: 1/1");
//            descView.append("\n");
//            if (move.details != null && move.details.zPower > 0) {
//                descView.append("Base power: " + move.details.zPower);
//                descView.append("\n");
//            }
//            if (move.details != null && move.details.zEffect != null) {
//                descView.append("Z-Effect: " + move.details.zEffect);
//                descView.append("\n");
//            }
//            if (move.zDetails != null && move.zDetails.desc != null) {
//                descView.append(italicText(move.zDetails.desc));
//            } else if (move.details != null){
//                descView.append(italicText(move.details.desc));
//            }
//        } else if (move.maxflag) {
//            titleView.setText(move.maxDetails.name);
//            descView.setText(move.maxDetails.toString());
//
//            descView.append("\n");
//        }
//        if (move.details == null) return;
//        if (move.zflag) return;
//
//
//        if (move.details.basePower > 0) {
//            descView.append("Base power: " + move.details.basePower);
//            descView.append("\n");
//        }
//        descView.append("Accuracy: " + (move.details.accuracy > 0 ? move.details.accuracy : "Can't miss"));
        }
        var basePower = -1
        if (move.maxflag) basePower = move.details?.maxPower ?: 0
        if (move.zflag) basePower = move.zDetails?.basePower ?: move.details?.zPower ?: 0
        if (basePower == -1) basePower = move.details?.basePower ?: 0
        if (basePower > 0) descView.append("Base power: $basePower\n")
        var accuracy = -20
        if (move.maxflag) accuracy = move.maxDetails?.accuracy ?: 0
        if (move.zflag) accuracy = 0
        if (accuracy == -20) accuracy = move.details?.accuracy ?: 0
        if (accuracy != 0) {
            descView.append("Accuracy: ")
            if (accuracy == -1) descView.append("can't miss") else descView.append(Utils.str(accuracy))
            descView.append("\n")
        }
        var desc: String? = null
        if (move.maxflag) desc = move.maxDetails?.desc ?: ""
        if (move.zflag) desc = move.zDetails?.desc ?: move.details?.zEffect?.let { "Z-Effect: $it" } ?: ""
        if (desc == null) desc = move.details?.desc ?: ""
        if (desc.isNotBlank()) descView.append(desc.italic())
        var type: String? = null
        if (move.maxflag) type = move.maxDetails?.type ?: "???"
        if (type == null) type = move.details?.type ?: "???"
        placeHolderTop.setImageResource(Type.getResId(type))
        val category = move.details?.category
        val drawable = if (category != null) CategoryDrawable(category) else null
        placeHolderBottom.setImageDrawable(drawable)

//        if (move.zflag) {
//            titleView.setText(move.zName);
//            descView.setText("PP: 1/1");
//            descView.append("\n");
//            if (move.details != null && move.details.zPower > 0) {
//                descView.append("Base power: " + move.details.zPower);
//                descView.append("\n");
//            }
//            if (move.details != null && move.details.zEffect != null) {
//                descView.append("Z-Effect: " + move.details.zEffect);
//                descView.append("\n");
//            }
//            if (move.zDetails != null && move.zDetails.desc != null) {
//                descView.append(italicText(move.zDetails.desc));
//            } else if (move.details != null){
//                descView.append(italicText(move.details.desc));
//            }
//        } else if (move.maxflag) {
//            titleView.setText(move.maxDetails.name);
//            descView.setText(move.maxDetails.toString());
//
//            descView.append("\n");
//        }
//        if (move.details == null) return;
//        if (move.zflag) return;
//
//
//        if (move.details.basePower > 0) {
//            descView.append("Base power: " + move.details.basePower);
//            descView.append("\n");
//        }
//        descView.append("Accuracy: " + (move.details.accuracy > 0 ? move.details.accuracy : "Can't miss"));
    }

    private fun bindSidePokemonPopup(pokemon: SidePokemon, titleView: TextView,
                                     descView: TextView, placeHolderTop: ImageView, placeHolderBottom: ImageView) {
        titleView.text = pokemon.name
        descView.text = Utils.smallText("HP: ")
        val healthText = String.format("%.1f%% ", pokemon.condition.health * 100)
        descView.append(Utils.boldText(healthText, healthColor(pokemon.condition.health)))
        descView.append(Utils.smallText("(" + pokemon.condition.hp + "/" + pokemon.condition.maxHp + ")"))
        if (pokemon.condition.status != null) descView.append(Utils.smallText(Utils.tagText(pokemon.condition.status!!.toUpperCase(),
                statusColor(pokemon.condition.status))))
        descView.append("\n")
        descView.append(Utils.smallText("Atk:"))
        descView.append(Utils.str(pokemon.stats.atk))
        descView.append(Utils.smallText(" Def:"))
        descView.append(Utils.str(pokemon.stats.def))
        descView.append(Utils.smallText(" Spa:"))
        descView.append(Utils.str(pokemon.stats.spa))
        descView.append(Utils.smallText(" Spd:"))
        descView.append(Utils.str(pokemon.stats.spd))
        descView.append(Utils.smallText(" Spe:"))
        descView.append(Utils.str(pokemon.stats.spe))
        descView.append("\n")
        descView.append(Utils.smallText("Ability: "))
        descView.append(pokemon.ability)
        descView.append("\n")
        descView.append(Utils.smallText("Item: "))
        descView.append(pokemon.item)
        fragmentScope.launch {
            assetLoader.item(pokemon.item.toId())?.let { item ->
                Utils.replace(descView.editableText, pokemon.item, item.name)
            }
        }
        descView.append("\n")
        descView.append(Utils.smallText("Moves:"))
        fragmentScope.launch {
            assetLoader.movesDetails(*pokemon.moves.map { it.toId() }.toTypedArray()).forEachIndexed { index, details ->
                descView.append("\n\t")
                descView.append(details?.name ?: pokemon.moves[index])
            }
        }
        placeHolderTop.setImageDrawable(null)
        placeHolderBottom.setImageDrawable(null)
        fragmentScope.launch {
            assetLoader.dexPokemon(pokemon.species.toId())?.let {dexPokemon ->
                placeHolderTop.setImageResource(Type.getResId(dexPokemon.firstType))
                if (dexPokemon.secondType != null) placeHolderBottom.setImageResource(Type.getResId(dexPokemon.secondType))
                if (pokemon.ability == null) return@let
                var abilityName: String? = null
                if (pokemon.ability == dexPokemon.hiddenAbility?.toId()) abilityName = dexPokemon.hiddenAbility
                else for (ab in dexPokemon.abilities) if (ab.toId() == pokemon.ability) abilityName = ab
                if (abilityName != null) Utils.replace(descView.editableText, pokemon.ability, abilityName)
            }
        }
    }

    fun forfeit() = service?.sendRoomCommand(observedRoomId, "forfeit")

    fun sendDecision(reqId: Int, decision: BattleDecision) {
        service?.sendRoomCommand(observedRoomId, decision.command, decision.build(), reqId)
    }

    fun sendTimerCommand(on: Boolean) {
        service?.sendRoomCommand(observedRoomId, "timer", if (on) "on" else "off")
    }

    fun sendUndoCommand() = service?.sendRoomCommand(observedRoomId, "undo")

    private fun notifyNewMessageReceived() {
        mainActivity.showBadge(id)
    }

    override fun onTimerEnabled(enabled: Boolean) {
        timerEnabled = enabled
        val color = resources.getColor(R.color.secondary)
        binding.actionContainer.timerButton.apply {
            if (enabled) drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            else drawable.clearColorFilter()
        }
    }

    override fun onPlayerInit(playerUsername: String, foeUsername: String) {
        binding.trainerInfo.setUsername(playerUsername)
        binding.foeInfo.setUsername(foeUsername)
    }

    override fun onBattleStarted() {
        prepareBattleFieldUi()
        mainActivity.setKeepScreenOn(true)
        when (observer.gameType) {
            GameType.SINGLE -> binding.battleLayout.setMode(BattleLayout.MODE_BATTLE_SINGLE)
            GameType.DOUBLE -> binding.battleLayout.setMode(BattleLayout.MODE_BATTLE_DOUBLE)
            GameType.TRIPLE -> binding.battleLayout.setMode(BattleLayout.MODE_BATTLE_TRIPLE)
        }
        if (soundEnabled) audioManager.playBattleMusic()
        //sendChatMessage("[Playing from the unofficial Android Showdown client]");
    }

    override fun onBattleEnded(winner: String) {
        mainActivity.setKeepScreenOn(false)
        audioManager.stopBattleMusic()
        inactiveBattleOverlayDrawable.setWinner(winner)
        clearBattleFieldUi()
    }

    override fun onPreviewStarted() {
        prepareBattleFieldUi()
        binding.battleLayout.setMode(BattleLayout.MODE_PREVIEW)
    }

    override fun onAddPreviewPokemon(id: PokemonId, pokemon: BasePokemon, hasItem: Boolean) {
        binding.battleLayout.getPokemonView(id)?.let {
            // Can be null when joining a battle where the preview has already been done
            glideHelper.loadPreviewSprite(id.player, pokemon, it)
        }
        fragmentScope.launch {
            assetLoader.dexIcon(pokemon.species.toId())?.let {
                val infoView = if (!id.foe) binding.trainerInfo else binding.foeInfo
                infoView.appendPokemon(pokemon, BitmapDrawable(it))
            }
        }
    }

    override fun onTeamSize(player: Player, size: Int) {
        val infoView = if (player == Player.TRAINER) binding.trainerInfo else binding.foeInfo
        infoView.setTeamSize(size)
        binding.battleLayout.setPreviewTeamSize(player, size)
    }

    override fun onFaint(id: PokemonId) {
        binding.battleLayout.getPokemonView(id)?.let {
            it.animate()
                    .setDuration(250)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .translationY(it.height / 2f)
                    .alpha(0f)
                    .withEndAction { it.translationY = 0f }
                    .start()
        }
        binding.battleLayout.getStatusView(id)?.animate()?.alpha(0f)?.start()
        val playerView = if (id.foe) binding.foeInfo else binding.trainerInfo
        playerView.setPokemonFainted(observer.getBattlingPokemon(id))
        if (soundEnabled) audioManager.playPokemonCry(observer.getBattlingPokemon(id), true)
    }

    override fun onMove(sourceId: PokemonId, targetId: PokemonId?, moveName: String, shouldAnim: Boolean) {
        if (!shouldAnim || targetId == null) return
        fragmentScope.launch {
            assetLoader.moveDetails(moveName)?.let { moveDetails ->
                val category = moveDetails.category.toId()
                if ("status" == category) return@let
                binding.battleLayout.displayHitIndicator(targetId)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onSwitch(pokemon: BattlingPokemon) {
        if (!pokemon.id.isInBattle) return
        binding.battleLayout.getStatusView(pokemon.id).apply {
            setPokemon(pokemon)
            animate().alpha(1f).start()
        }
        binding.battleLayout.getPokemonView(pokemon.id).apply {
            setTag(R.id.battle_data_tag, pokemon)
            battleTipPopup.addTippedView(this)
            glideHelper.loadSprite(pokemon, this, binding.battleLayout.width)
        }
        fragmentScope.launch {
            assetLoader.dexIcon(pokemon.species.toId())?.let {
                val infoView = if (!pokemon.foe) binding.trainerInfo else binding.foeInfo
                infoView.updatePokemon(pokemon, BitmapDrawable(it))
            }
        }
        if (soundEnabled) audioManager.playPokemonCry(pokemon, false)
    }

    override fun onDetailsChanged(pokemon: BattlingPokemon) {
        binding.battleLayout.getPokemonView(pokemon.id).let {
            glideHelper.loadSprite(pokemon, it, binding.battleLayout.width)
        }
        fragmentScope.launch {
            assetLoader.dexIcon(pokemon.species.toId())?.let {
                val infoView = if (!pokemon.foe) binding.trainerInfo else binding.foeInfo
                infoView.updatePokemon(pokemon, BitmapDrawable(it))
            }
        }
        if (soundEnabled && "mega" == pokemon.forme) audioManager.playPokemonCry(pokemon, false)
    }

    override fun onSwap(id: PokemonId, targetIndex: Int) {
        binding.battleLayout.swap(id, targetIndex)
    }

    override fun onHealthChanged(id: PokemonId, condition: Condition) {
        val statusView = binding.battleLayout.getStatusView(id)
        statusView.setHealth(condition.health)
    }

    override fun onStatusChanged(id: PokemonId, status: String?) {
        val statusView = binding.battleLayout.getStatusView(id)
        statusView.setStatus(status)
    }

    override fun onStatChanged(id: PokemonId) {
        val statModifiers = observer.getBattlingPokemon(id)!!.statModifiers
        val statusView = binding.battleLayout.getStatusView(id)
        statusView.updateModifier(statModifiers)
    }

    override fun onRequestAsked(request: BattleActionRequest) {
        lastActionRequest = request
        if (request.shouldWait) return
        binding.battleActionWidget.promptDecision(observer, battleTipPopup, request) { decision ->
            sendDecision(request.id, decision)
        }
        var hideSwitch = true
        for (which in 0 until request.count) {
            if (!request.trapped(which)) hideSwitch = false
            val hideMoves = request.forceSwitch(which)
            val moves = request.getMoves(which)
            if (hideMoves || moves == null || moves.isEmpty()) continue
            fragmentScope.launch {
                assetLoader.movesDetails(*moves.map { it.id }.toTypedArray()).forEachIndexed { index, details ->
                    moves[index].details = details
                }
                binding.battleActionWidget.notifyDetailsUpdated()
            }
            fragmentScope.launch {
                val zMoves = moves.map { it.zName?.toId() ?: "" }
                if (!zMoves.all { it.isBlank() }) {
                    assetLoader.movesDetails(*zMoves.toTypedArray()).forEachIndexed { index, details ->
                        moves[index].zDetails = details
                    }
                    binding.battleActionWidget.notifyDetailsUpdated()
                }
            }
            fragmentScope.launch {
                val maxMoves = moves.map { it.maxMoveId?.toId() ?: "" }
                if (!maxMoves.all { it.isBlank() }) {
                    assetLoader.movesDetails(*maxMoves.toTypedArray()).forEachIndexed { index, details ->
                        moves[index].maxDetails = details
                    }
                    binding.battleActionWidget.notifyMaxDetailsUpdated()
                }
            }
        }
        if (request.teamPreview || !hideSwitch) {
            val team = request.side
            fragmentScope.launch {
                assetLoader.dexIcons(*team.map { it.species.toId() }.toTypedArray()).forEachIndexed { index, bitmap ->
                    team[index].icon = bitmap
                }
                binding.battleActionWidget.notifyDexIconsUpdated()
            }
        }
    }

    override fun onDisplayBattleToast(id: PokemonId, text: String, color: Int) {
        val toasterView = binding.battleLayout.getToasterView(id)
        toasterView.makeToast(text, color)
    }

    override fun onFieldEffectChanged(name: String?) {
        val resId = FieldEffects.getDrawableResourceId(name)
        if (resId == binding.overlayImage.tag) return
        binding.overlayImage.apply {
            if (resId > 0) {
                alpha = 0f
                setImageResource(resId)
                tag = resId
                animate().alpha(0.75f).setDuration(250).withEndAction(null)
                        .start()
            } else {
                animate().alpha(0f).setDuration(250).withEndAction { setImageDrawable(null) }
                        .start()
            }
        }
    }

    override fun onSideChanged(player: Player, side: String, start: Boolean) {
        binding.battleLayout.getSideView(player).apply {
            if (start) sideStart(side) else sideEnd(side)
        }
    }

    override fun onVolatileStatusChanged(id: PokemonId, vStatus: String, start: Boolean) {
        binding.battleLayout.getStatusView(id).apply {
            if (start) addVolatileStatus(vStatus) else removeVolatileStatus(vStatus)
        }
    }

    override fun onMarkBreak() {
        binding.apply {
           battleActionWidget.dismiss()
           undoContainer.undoButton.isEnabled = false
           undoContainer.undoContainer.animate()
                    .setStartDelay(0)
                    .translationX(actionContainer.actionContainer.width.toFloat())
                    .alpha(0f)
                    .start()
        }
    }

    override fun onPrintText(text: CharSequence) {
        val fullScrolled = Utils.fullScrolled(binding.battleLogContainer)
        val l = binding.battleLog.length()
        if (l > 0) binding.battleLog.append("\n")
        binding.battleLog.append(text)
        notifyNewMessageReceived()
        if (fullScrolled) postFullScroll()
    }

    override fun onPrintBattleMessage(text: CharSequence) {
        binding.battleMessage.text = text
        binding.battleMessage.animate().cancel()
        binding.battleMessage.alpha = 1f
        binding.battleMessage.animate()
                .setDuration(1000)
                .setStartDelay(750)
                .alpha(0f)
                .start()
    }

    override fun onPrintHtml(html: String) {
        val mark = Any()
        val l = binding.battleLog.length()
        binding.battleLog.append("\u200C")
        binding.battleLog.editableText.setSpan(mark, l, l + 1, Spanned.SPAN_MARK_MARK)
        Html.fromHtml(html,
                Html.FROM_HTML_MODE_COMPACT,
                glideHelper.getHtmlImageGetter(assetLoader, binding.battleLog.width),
                Callback { spanned: Spanned? ->
                    val at = binding.battleLog.editableText.getSpanStart(mark)
                    if (at == -1) return@Callback   // Check if text has been cleared
                    val fullScrolled = Utils.fullScrolled(binding.battleLogContainer)
                    binding.battleLog.editableText
                            .insert(at, "\n")
                            .insert(at + 1, spanned)
                    notifyNewMessageReceived()
                    if (fullScrolled) postFullScroll()
                })
    }

    private fun postFullScroll() {
        binding.battleLogContainer.post { binding.battleLogContainer.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onRoomTitleChanged(title: String) {
        // Ignored
    }

    override fun onUpdateUsers(users: List<String>) {
        // Ignored
    }

    override fun onRoomInit() {
        soundEnabled = Preferences.getBoolPreference(context, "sound")
        binding.battleLog.setText("", TextView.BufferType.EDITABLE)
        lastActionRequest = null
        binding.battleActionWidget.dismissNow()
        onTimerEnabled(false)
        binding.backgroundImage.animate()
                .setDuration(100)
                .alpha(0f)
                .withEndAction {
                    val resId: Int = if (Math.random() > 0.5) R.drawable.battle_bg_1 else R.drawable.battle_bg_2
                    binding.backgroundImage.setImageResource(resId)
                    binding.backgroundImage.animate()
                            .setDuration(100)
                            .alpha(1f)
                            .withEndAction(null)
                            .start()
                }
                .start()
        // In case of corrupted battle stream make sure we stop music at the next one
        audioManager.stopBattleMusic()
    }

    override fun onRoomDeInit() {
        mainActivity.setKeepScreenOn(false)
        binding.apply {
            battleActionWidget.dismiss()
            battleLog.text = ""
            battleActionWidget.dismiss()
            undoContainer.undoButton.isEnabled = false
            undoContainer.undoContainer.animate()
                    .setStartDelay(0)
                    .translationX(actionContainer.actionContainer.width.toFloat())
                    .alpha(0f)
                    .start()
            actionContainer.timerButton.drawable.clearColorFilter()
        }
        audioManager.stopBattleMusic()
        inactiveBattleOverlayDrawable.setWinner(null)
        clearBattleFieldUi()
        lastActionRequest = null

    }
}