/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2022 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.helper

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.maddyhome.idea.vim.action.change.Extension
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.newapi.ij
import com.maddyhome.idea.vim.newapi.vim
import com.maddyhome.idea.vim.ui.ModalEntry
import com.maddyhome.idea.vim.ui.ex.ExEntryPanel
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@Service
class CommandLineHelper : VimCommandLineHelper {

  override fun inputString(vimEditor: VimEditor, prompt: String, finishOn: Char?): String? {
    val editor = vimEditor.ij
    if (vimEditor.vimStateMachine.isDotRepeatInProgress) {
      val input = Extension.consumeString()
      return input ?: error("Not enough strings saved: ${Extension.lastExtensionHandler}")
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      val builder = StringBuilder()
      val inputModel = TestInputModel.getInstance(editor)
      var key: KeyStroke? = inputModel.nextKeyStroke()
      while (key != null &&
        !key.isCloseKeyStroke() && key.keyCode != KeyEvent.VK_ENTER &&
        (finishOn == null || key.keyChar != finishOn)
      ) {
        val c = key.keyChar
        if (c != KeyEvent.CHAR_UNDEFINED) {
          builder.append(c)
        }
        key = inputModel.nextKeyStroke()
      }
      if (finishOn != null && key != null && key.keyChar == finishOn) {
        builder.append(key.keyChar)
      }
      Extension.addString(builder.toString())
      return builder.toString()
    } else {
      var text: String? = null
      // XXX: The Ex entry panel is used only for UI here, its logic might be inappropriate for input()
      val exEntryPanel = ExEntryPanel.getInstanceWithoutShortcuts()
      exEntryPanel.activate(editor, EditorDataContext.init(editor), prompt.ifEmpty { " " }, "", 1)
      ModalEntry.activate(editor.vim) { key: KeyStroke ->
        return@activate when {
          key.isCloseKeyStroke() -> {
            exEntryPanel.deactivate(true)
            false
          }
          key.keyCode == KeyEvent.VK_ENTER -> {
            text = exEntryPanel.text
            exEntryPanel.deactivate(true)
            false
          }
          finishOn != null && key.keyChar == finishOn -> {
            exEntryPanel.handleKey(key)
            text = exEntryPanel.text
            exEntryPanel.deactivate(true)
            false
          }
          else -> {
            exEntryPanel.handleKey(key)
            true
          }
        }
      }
      if (text != null) {
        Extension.addString(text!!)
      }
      return text
    }
  }
}
