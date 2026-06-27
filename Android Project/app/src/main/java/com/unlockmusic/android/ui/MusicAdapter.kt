package com.unlockmusic.android.ui

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.unlockmusic.android.R
import com.unlockmusic.android.databinding.ItemMusicFileBinding
import com.unlockmusic.android.model.MusicFile

class MusicAdapter : RecyclerView.Adapter<MusicAdapter.ViewHolder>() {

    private var items: List<MusicFile> = emptyList()

    var onPlayClick: ((MusicFile) -> Unit)? = null
    var onSaveClick: ((MusicFile) -> Unit)? = null
    var onReDecrypt: ((MusicFile) -> Unit)? = null
    var onRename: ((MusicFile, String) -> Unit)? = null
    var onRemove: ((MusicFile) -> Unit)? = null

    fun submitList(newItems: List<MusicFile>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMusicFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = items[position]
        val binding = holder.binding
        val ctx = binding.root.context

        binding.tvFilename.text = file.displayName
        binding.tvStatus.text = file.status

        // 根据状态设置颜色与显示
        val (statusColor, dotColor, actionVisible) = when {
            file.isDecrypted -> Triple(R.color.green, R.color.green, View.VISIBLE)
            file.status.contains("失败") || file.status.contains("错误") ->
                Triple(R.color.red, R.color.red, View.GONE)
            else -> Triple(R.color.gray, R.color.text_placeholder, View.GONE)
        }
        binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, statusColor))
        binding.btnPlay.visibility = actionVisible
        binding.btnSave.visibility = actionVisible
        (binding.statusIndicator.background as? GradientDrawable)
            ?.setColor(ContextCompat.getColor(ctx, dotColor))

        binding.btnPlay.setOnClickListener { onPlayClick?.invoke(file) }
        binding.btnSave.setOnClickListener { onSaveClick?.invoke(file) }
        binding.root.setOnClickListener { showActionDialog(binding, file) }
        binding.root.setOnLongClickListener { showRenameDialog(binding, file); true }
    }

    private fun showActionDialog(binding: ItemMusicFileBinding, file: MusicFile) {
        val ctx = binding.root.context
        val builder = AlertDialog.Builder(ctx).setTitle(file.displayName)

        if (file.isDecrypted) {
            builder.setItems(arrayOf(
                ctx.getString(R.string.action_play),
                ctx.getString(R.string.action_save),
                ctx.getString(R.string.action_re_decrypt),
                ctx.getString(R.string.action_rename),
                ctx.getString(R.string.action_remove)
            )) { _, which ->
                when (which) {
                    0 -> onPlayClick?.invoke(file)
                    1 -> onSaveClick?.invoke(file)
                    2 -> onReDecrypt?.invoke(file)
                    3 -> showRenameDialog(binding, file)
                    4 -> onRemove?.invoke(file)
                }
            }
        } else {
            builder.setItems(arrayOf(
                ctx.getString(R.string.action_re_decrypt),
                ctx.getString(R.string.action_rename),
                ctx.getString(R.string.action_remove)
            )) { _, which ->
                when (which) {
                    0 -> onReDecrypt?.invoke(file)
                    1 -> showRenameDialog(binding, file)
                    2 -> onRemove?.invoke(file)
                }
            }
        }
        builder.setNegativeButton(R.string.action_cancel, null).show()
    }

    private fun showRenameDialog(binding: ItemMusicFileBinding, file: MusicFile) {
        val ctx = binding.root.context
        val input = EditText(ctx).apply {
            setText(file.displayName)
            selectAll()
            setPadding(32, 24, 32, 24)
        }

        AlertDialog.Builder(ctx)
            .setTitle(R.string.rename_title)
            .setView(input)
            .setPositiveButton(R.string.rename_confirm) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) onRename?.invoke(file, newName)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun getItemCount() = items.size

    class ViewHolder(val binding: ItemMusicFileBinding) : RecyclerView.ViewHolder(binding.root)
}
