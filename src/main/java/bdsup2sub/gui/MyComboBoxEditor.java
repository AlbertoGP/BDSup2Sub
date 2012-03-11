/*
 * Copyright 2012 Volker Oth (0xdeadbeef) / Miklos Juhasz (mjuhasz)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bdsup2sub.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import java.awt.*;

/**
 * ComboBox Editor.
 * Implementation needed to be able to change the background color of editable ComboBoxes.
 */
public class MyComboBoxEditor extends BasicComboBoxEditor {

    public MyComboBoxEditor(JTextField editor) {
        this.editor = editor;
        editor.setBorder(new EmptyBorder(new Insets(1,2,1,2)));
    }

    @Override
    public Component getEditorComponent() {
        return editor;
    }
}
