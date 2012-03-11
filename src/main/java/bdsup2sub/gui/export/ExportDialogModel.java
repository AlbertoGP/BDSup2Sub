/*
 * Copyright 2012 Miklos Juhasz (mjuhasz)
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
package bdsup2sub.gui.export;

import bdsup2sub.core.Configuration;
import bdsup2sub.core.Core;
import bdsup2sub.core.OutputMode;

public class ExportDialogModel {

    private final Configuration configuration = Configuration.getInstance();

    private String filename = "";
    private boolean canceled;
    private int languageIdx;
    private boolean exportForced;
    private boolean writePGCPal;

    public ExportDialogModel() {
        languageIdx = Core.getLanguageIdx();
        exportForced = (Core.getNumForcedFrames() > 0) && Core.getExportForced();
        writePGCPal = configuration.getWritePGCEditPal();
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public boolean wasCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public int getLanguageIdx() {
        return languageIdx;
    }

    public void setLanguageIdx(int languageIdx) {
        this.languageIdx = languageIdx;
    }

    public void storeLanguageIdx() {
        Core.setLanguageIdx(languageIdx);
    }

    public boolean getExportForced() {
        return exportForced;
    }

    public void setExportForced(boolean exportForced) {
        this.exportForced = exportForced;
    }

    public void storeExportForced() {
        Core.setExportForced(exportForced);
    }

    public boolean getWritePGCPal() {
        return writePGCPal;
    }

    public void setWritePGCPal(boolean writePGCPal) {
        this.writePGCPal = writePGCPal;
    }

    public void storeWritePGCPal() {
        configuration.setWritePGCEditPal(writePGCPal);
    }

    public OutputMode getOutputMode() {
        return configuration.getOutputMode();
    }
}
