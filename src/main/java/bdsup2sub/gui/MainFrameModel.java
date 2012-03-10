package bdsup2sub.gui;

import bdsup2sub.core.Core;

public class MainFrameModel {
    
    private String loadPath;
    private String saveFilename;
    private String savePath;
    private int subIndex;
    private boolean sourceFileSpecifiedOnCmdLine;

    public MainFrameModel() {
        this.loadPath = Core.props.get("loadPath", "");
    }

    public String getLoadPath() {
        return loadPath;
    }

    public void setLoadPath(String loadPath) {
        this.loadPath = loadPath;
        Core.props.set("loadPath", loadPath); //FIXME: use listener
    }

    public String getSavePath() {
        return savePath;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public String getSaveFilename() {
        return saveFilename;
    }

    public void setSaveFilename(String saveFilename) {
        this.saveFilename = saveFilename;
    }

    public int getSubIndex() {
        return subIndex;
    }

    public void setSubIndex(int subIndex) {
        this.subIndex = subIndex;
    }

    public boolean isSourceFileSpecifiedOnCmdLine() {
        return sourceFileSpecifiedOnCmdLine;
    }

    public void setSourceFileSpecifiedOnCmdLine(boolean sourceFileSpecified) {
        this.sourceFileSpecifiedOnCmdLine = sourceFileSpecified;
    }
}
