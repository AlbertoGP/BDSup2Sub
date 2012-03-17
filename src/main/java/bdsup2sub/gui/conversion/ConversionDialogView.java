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
package bdsup2sub.gui.conversion;

import bdsup2sub.core.Configuration;
import bdsup2sub.core.Core;
import bdsup2sub.core.ForcedFlagState;
import bdsup2sub.core.Resolution;
import bdsup2sub.gui.MyComboBoxEditor;
import bdsup2sub.utils.SubtitleUtils;
import bdsup2sub.utils.ToolBox;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;

import static bdsup2sub.core.Configuration.*;
import static bdsup2sub.core.Configuration.MAX_FREE_SCALE_FACTOR;
import static bdsup2sub.core.Configuration.MIN_FREE_SCALE_FACTOR;
import static bdsup2sub.gui.support.GuiUtils.centerRelativeToOwner;

public class ConversionDialogView extends JDialog {

    private final Configuration configuration = getInstance();

    private JPanel jContentPane;
    private JPanel jPanelResolution;
    private JComboBox jComboBoxResolution;
    private JCheckBox jCheckBoxFrameRate;
    private JCheckBox jCheckBoxResolution;
    private JPanel jPanelFPS;
    private JComboBox jComboBoxFPSSrc;
    private JComboBox jComboBoxFPSTrg;
    private JPanel jPanelMove;
    private JCheckBox jCheckBoxMove;
    private JPanel jPanelTimes;
    private JTextField jTextFieldDelay;
    private JCheckBox jCheckBoxFixMinTime;
    private JTextField jTextFieldMinTime;
    private JTextField fpsTrgEditor;
    private JTextField fpsSrcEditor;
    private JPanel jPanelDefaults;
    private JButton jButtonStore;
    private JButton jButtonRestore;
    private JButton jButtonReset;
    private JPanel jPanelButtons;
    private JButton jButtonOk;
    private JButton jButtonCancel;
    private JPanel jPanelScale;
    private JCheckBox jCheckBoxScale;
    private JTextField jTextFieldScaleX;
    private JTextField jTextFieldScaleY;
    private JPanel jPanelForced;
    private JComboBox jComboBoxForced;

    /** background color for errors */
    private Color errBgnd = new Color(0xffe1acac);
    /** background color for warnings */
    private Color warnBgnd = new Color(0xffffffc0);
    /** background color for ok */
    private Color okBgnd = UIManager.getColor("TextField.background");

    /** selected output resolution */
    private Resolution resolution;
    /** selected delay in 90kHz resolution */
    private int     delayPTS;
    /** selected minimum frame time in 90kHz resolution */
    private int     minTimePTS;
    /** flag that tells whether to convert the frame rate or not */
    private boolean changeFPS;
    /** flag that tells whether to convert the resolution or not */
    private boolean changeResolution;
    /** flag that tells whether to fix frames shorter than a minimum time */
    private boolean fixShortFrames;
    /** source frame rate */
    private double  fpsSrc;
    /** target frame rate */
    private double  fpsTrg;
    /** cancel state */
    private boolean cancel;
    /** semaphore to disable actions while changing component properties */
    private volatile boolean isReady;
    /** flag that tells whether to use free scaling or not */
    private boolean changeScale;
    /** X scaling factor */
    private double scaleX;
    /** Y scaling factor */
    private double scaleY;
    /** source fps is certain */
    private boolean fpsSrcCertain;
    /** clear/set all forced flags */
    private ForcedFlagState forcedState;
    /** apply move settings */
    private boolean moveCaptions;

    private static Dimension lDim = new Dimension(70,20);

    private ConversionDialogModel model;

    public ConversionDialogView(ConversionDialogModel model, Frame owner) {
        super(owner, true);
        this.model = model;

        // initialize internal variables
        fpsTrgEditor = new JTextField();
        fpsSrcEditor = new JTextField();

        initialize();
        centerRelativeToOwner(this);
        setResizable(false);

        changeResolution = configuration.getConvertResolution();
        // fix output resolution in case that it should not be changed
        // change target resolution to source resolution if no conversion is needed
        if (!changeResolution && Core.getNumFrames() > 0) {
            resolution = Core.getResolution(Core.getSubPictureSrc(0).width, Core.getSubPictureSrc(0).height);
        } else {
            resolution = configuration.getOutputResolution();
        }

        moveCaptions = Core.getMoveCaptions();
        jCheckBoxMove.setEnabled(false);
        jCheckBoxMove.setSelected(moveCaptions);

        delayPTS = configuration.getDelayPTS();
        minTimePTS = (int) SubtitleUtils.syncTimePTS(configuration.getMinTimePTS(), configuration.getFPSTrg(), configuration.getFPSTrg());
        changeFPS = configuration.getConvertFPS();
        changeScale = configuration.getApplyFreeScale();
        fixShortFrames = configuration.getFixShortFrames();
        fpsSrc = configuration.getFPSSrc();
        fpsTrg = configuration.getFPSTrg();
        scaleX = configuration.getFreeScaleFactorX();
        scaleY = configuration.getFreeScaleFactorY();
        fpsSrcCertain = configuration.isFpsSrcCertain();
        cancel = false;

        // fill comboboxes and text fields
        for (Resolution r : Resolution.values()) {
            jComboBoxResolution.addItem(r.toString());
        }

        jComboBoxFPSSrc.addItem("23.975");
        jComboBoxFPSSrc.addItem("23.976");
        jComboBoxFPSSrc.addItem("24");
        jComboBoxFPSSrc.addItem("25");
        jComboBoxFPSSrc.addItem("29.97");
        jComboBoxFPSSrc.addItem("50");
        jComboBoxFPSSrc.addItem("59.94");

        jComboBoxFPSTrg.addItem("23.975");
        jComboBoxFPSTrg.addItem("23.976");
        jComboBoxFPSTrg.addItem("24");
        jComboBoxFPSTrg.addItem("25");
        jComboBoxFPSTrg.addItem("29.97");
        jComboBoxFPSTrg.addItem("50");
        jComboBoxFPSTrg.addItem("59.94");

        jComboBoxFPSSrc.setEditor(new MyComboBoxEditor(fpsSrcEditor));
        jComboBoxFPSTrg.setEditor(new MyComboBoxEditor(fpsTrgEditor));

        // note: order has to be ordinal order of enum!
        forcedState = Core.getForceAll();
        jComboBoxForced.addItem("keep      ");
        jComboBoxForced.addItem("set all   ");
        jComboBoxForced.addItem("clear all ");

        fillDialog();

        isReady = true;
    }

    /**
     * Enter values into dialog elements
     */
    private void fillDialog() {
        jComboBoxResolution.setSelectedIndex(resolution.ordinal());
        jComboBoxResolution.setEnabled(changeResolution);
        jCheckBoxResolution.setSelected(changeResolution);

        jTextFieldDelay.setText(ToolBox.formatDouble(delayPTS / 90.0));

        jCheckBoxFrameRate.setSelected(changeFPS);
        jComboBoxFPSSrc.setSelectedItem(ToolBox.formatDouble(fpsSrc));
        jComboBoxFPSSrc.setEnabled(changeFPS);
        jComboBoxFPSTrg.setSelectedItem(ToolBox.formatDouble(fpsTrg));
        jComboBoxFPSTrg.setEnabled(true);

        jTextFieldMinTime.setText(ToolBox.formatDouble(minTimePTS/90.0));
        jCheckBoxFixMinTime.setEnabled(true);
        jCheckBoxFixMinTime.setSelected(fixShortFrames);


        jCheckBoxFixMinTime.setSelected(fixShortFrames);
        jTextFieldMinTime.setEnabled(fixShortFrames);

        jCheckBoxScale.setSelected(changeScale);
        jTextFieldScaleX.setText(ToolBox.formatDouble(scaleX));
        jTextFieldScaleX.setEnabled(changeScale);
        jTextFieldScaleY.setText(ToolBox.formatDouble(scaleY));
        jTextFieldScaleY.setEnabled(changeScale);

        jComboBoxForced.setSelectedIndex(forcedState.ordinal());
    }

    /**
     * This method initializes this dialog
     */
    private void initialize() {
        this.setSize(500, 350);
        this.setTitle("Conversion Options");
        this.setPreferredSize(new Dimension(500, 350));
        this.setContentPane(getJContentPane());
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel = true;
                dispose();
            }
        });
    }

    /**
     * This method initializes jPanelResolution
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelResolution() {
        if (jPanelResolution == null) {
            GridBagConstraints gridBagCheckBoxResolution = new GridBagConstraints();
            gridBagCheckBoxResolution.gridx = 0;
            gridBagCheckBoxResolution.gridy = 0;
            gridBagCheckBoxResolution.anchor = GridBagConstraints.WEST;
            gridBagCheckBoxResolution.gridwidth = 2;

            GridBagConstraints gridBagLabelResolution = new GridBagConstraints();
            gridBagLabelResolution.gridx = 0;
            gridBagLabelResolution.gridy = 1;
            gridBagLabelResolution.anchor = GridBagConstraints.WEST;
            gridBagLabelResolution.insets = new Insets(2, 6, 2, 0);

            GridBagConstraints gridBagComboResolution = new GridBagConstraints();
            gridBagComboResolution.gridx = 1;
            gridBagComboResolution.gridy = 1;
            gridBagComboResolution.weightx = 1.0;
            gridBagComboResolution.anchor = GridBagConstraints.WEST;
            gridBagComboResolution.insets = new Insets(2, 4, 2, 4);

            jPanelResolution = new JPanel();
            jPanelResolution.setLayout(new GridBagLayout());
            jPanelResolution.setBorder(BorderFactory.createTitledBorder(null, "Resolution", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.PLAIN, 11), new Color(0, 70, 213)));
            jPanelResolution.setMinimumSize(new Dimension(200, 70));
            jPanelResolution.setPreferredSize(new Dimension(200, 70));

            JLabel label = new JLabel("Resolution");
            label.setMinimumSize(lDim);
            jPanelResolution.add(getJCheckBoxResolution(), gridBagCheckBoxResolution);
            jPanelResolution.add(label, gridBagLabelResolution);
            jPanelResolution.add(getJComboBoxResolution(), gridBagComboResolution);

        }
        return jPanelResolution;
    }

    /**
     * This method initializes jPanelMove
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelMove() {
        if (jPanelMove == null) {
            GridBagConstraints gridBagCheckBoxMove = new GridBagConstraints();
            gridBagCheckBoxMove.gridx = 0;
            gridBagCheckBoxMove.gridy = 0;
            gridBagCheckBoxMove.anchor = GridBagConstraints.WEST;
            gridBagCheckBoxMove.weightx = 1.0;
            //gridBagCheckBoxMove.insets = new Insets(2, 6, 2, 0);

            jPanelMove = new JPanel();
            jPanelMove.setLayout(new GridBagLayout());
            jPanelMove.setBorder(BorderFactory.createTitledBorder(null, "Move", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.PLAIN, 11), new Color(0, 70, 213)));
            jPanelMove.setMinimumSize(new Dimension(200, 50));
            jPanelMove.setPreferredSize(new Dimension(200, 50));

            jPanelMove.add(getJCheckBoxMove(), gridBagCheckBoxMove);

        }
        return jPanelMove;
    }


    /**
     * This method initializes jPanelFPS
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelFPS() {
        if (jPanelFPS == null) {
            GridBagConstraints gridBagCheckBoxFrameRate = new GridBagConstraints();
            gridBagCheckBoxFrameRate.gridx = 0;
            gridBagCheckBoxFrameRate.gridy = 0;
            gridBagCheckBoxFrameRate.anchor = GridBagConstraints.WEST;
            gridBagCheckBoxFrameRate.gridwidth = 2;

            GridBagConstraints gridBagLabelFPSSrc = new GridBagConstraints();
            gridBagLabelFPSSrc.gridx = 0;
            gridBagLabelFPSSrc.gridy = 1;
            gridBagLabelFPSSrc.anchor = GridBagConstraints.WEST;
            gridBagLabelFPSSrc.insets = new Insets(2, 6, 2, 0);

            GridBagConstraints gridBagComboFPSSrc = new GridBagConstraints();
            gridBagComboFPSSrc.gridx = 1;
            gridBagComboFPSSrc.gridy = 1;
            //gridBagComboFPSSrc.fill = GridBagConstraints.NONE;
            gridBagComboFPSSrc.weightx = 1.0;
            gridBagComboFPSSrc.anchor = GridBagConstraints.WEST;
            gridBagComboFPSSrc.insets = new Insets(2, 4, 2, 4);

            GridBagConstraints gridBagLabelFPSTrg = new GridBagConstraints();
            gridBagLabelFPSTrg.gridx = 0;
            gridBagLabelFPSTrg.gridy = 2;
            gridBagLabelFPSTrg.anchor = GridBagConstraints.WEST;
            gridBagLabelFPSTrg.insets = new Insets(2, 6, 2, 0);

            GridBagConstraints gridBagComboFPSTrg = new GridBagConstraints();
            //gridBagComboFPSTrg.fill = GridBagConstraints.VERTICAL;
            gridBagComboFPSTrg.gridx = 1;
            gridBagComboFPSTrg.gridy = 2;
            gridBagComboFPSTrg.weightx = 1.0;
            gridBagComboFPSTrg.anchor = GridBagConstraints.WEST;
            gridBagComboFPSTrg.insets = new Insets(2, 4, 2, 4);

            jPanelFPS = new JPanel();
            jPanelFPS.setLayout(new GridBagLayout());
            jPanelFPS.setBorder(BorderFactory.createTitledBorder(null, "Framerate", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.PLAIN, 11), new Color(0, 70, 213)));
            jPanelFPS.setMinimumSize(new Dimension(200, 100));
            jPanelFPS.setPreferredSize(new Dimension(200, 100));

            jPanelFPS.add(getJCheckBoxFrameRate(), gridBagCheckBoxFrameRate);
            JLabel label = new JLabel("FPS Source");
            label.setMinimumSize(lDim);
            jPanelFPS.add(label, gridBagLabelFPSSrc);
            label = new JLabel("FPS Target");
            label.setMinimumSize(lDim);
            jPanelFPS.add(label, gridBagLabelFPSTrg);
            jPanelFPS.add(getJComboBoxFPSSrc(), gridBagComboFPSSrc);
            jPanelFPS.add(getJComboBoxFPSTrg(), gridBagComboFPSTrg);
        }
        return jPanelFPS;
    }

    /**
     * This method initializes jPanelTimes
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelTimes() {
        if (jPanelTimes == null) {
            GridBagConstraints gridBagLabelDelay = new GridBagConstraints();
            gridBagLabelDelay.gridx = 0;
            gridBagLabelDelay.gridy = 0;
            gridBagLabelDelay.anchor = GridBagConstraints.WEST;
            gridBagLabelDelay.insets = new Insets(2, 6, 2, 0);

            GridBagConstraints gridBagTextDelay = new GridBagConstraints();
            gridBagTextDelay.gridx = 1;
            gridBagTextDelay.gridy = 0;
            gridBagTextDelay.weightx = 1.0D;
            gridBagTextDelay.anchor = GridBagConstraints.WEST;
            gridBagTextDelay.insets = new Insets(2, 4, 2, 4);
            gridBagTextDelay.ipadx = 100;

            GridBagConstraints gridBagCheckBoxFixMinTime = new GridBagConstraints();
            gridBagCheckBoxFixMinTime.gridx = 0;
            gridBagCheckBoxFixMinTime.gridy = 1;
            gridBagCheckBoxFixMinTime.anchor = GridBagConstraints.WEST;
            gridBagCheckBoxFixMinTime.gridwidth = 2;
            gridBagCheckBoxFixMinTime.insets = new Insets(0, 0, 0, 0);

            GridBagConstraints gridBagLabelMinTime = new GridBagConstraints();
            gridBagLabelMinTime.gridx = 0;
            gridBagLabelMinTime.gridy = 2;
            gridBagLabelMinTime.anchor = GridBagConstraints.WEST;
            gridBagLabelMinTime.insets = new Insets(2, 6, 2, 2);

            GridBagConstraints gridBagTextMinTime = new GridBagConstraints();
            gridBagTextMinTime.gridx = 1;
            gridBagTextMinTime.gridy = 2;
            gridBagTextMinTime.anchor = GridBagConstraints.WEST;
            gridBagTextMinTime.weightx = 1.0;
            gridBagTextMinTime.ipadx = 100;
            gridBagTextMinTime.insets = new Insets(2, 4, 2, 4);

            jPanelTimes = new JPanel();
            jPanelTimes.setLayout(new GridBagLayout());
            jPanelTimes.setBorder(BorderFactory.createTitledBorder(null, "Times", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.PLAIN, 11), new Color(0, 70, 213)));
            jPanelTimes.setMinimumSize(new Dimension(200, 100));
            jPanelTimes.setPreferredSize(new Dimension(200, 100));

            JLabel label = new JLabel("Delay (ms)");
            label.setMinimumSize(lDim);
            jPanelTimes.add(label, gridBagLabelDelay);
            jPanelTimes.add(getJTextFieldDelay(), gridBagTextDelay);
            jPanelTimes.add(getJCheckBoxFixMineTime(), gridBagCheckBoxFixMinTime);
            jPanelTimes.add(getJTextFieldMinTime(), gridBagTextMinTime);
            label = new JLabel("Min Time (ms)");
            label.setMinimumSize(lDim);
            jPanelTimes.add(label, gridBagLabelMinTime);
        }
        return jPanelTimes;
    }

    /**
     * This method initializes jPanelScale
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelScale() {
        if (jPanelScale == null) {
            GridBagConstraints gridBagCheckBoxScale = new GridBagConstraints();
            gridBagCheckBoxScale.gridx = 0;
            gridBagCheckBoxScale.gridy = 0;
            gridBagCheckBoxScale.anchor = GridBagConstraints.WEST;
            gridBagCheckBoxScale.gridwidth = 2;

            GridBagConstraints gridBagLabelScaleX = new GridBagConstraints();
            gridBagLabelScaleX.gridx = 0;
            gridBagLabelScaleX.gridy = 1;
            gridBagLabelScaleX.anchor = GridBagConstraints.WEST;
            gridBagLabelScaleX.insets = new Insets(2, 6, 2, 2);

            GridBagConstraints gridBagTextScaleX = new GridBagConstraints();
            gridBagTextScaleX.gridx = 1;
            gridBagTextScaleX.gridy = 1;
            gridBagTextScaleX.anchor = GridBagConstraints.WEST;
            gridBagTextScaleX.insets = new Insets(2, 4, 2, 4);
            gridBagTextScaleX.weightx = 1.0;
            gridBagTextScaleX.ipadx = 100;

            GridBagConstraints gridBagLabelScaleY = new GridBagConstraints();
            gridBagLabelScaleY.gridx = 0;
            gridBagLabelScaleY.gridy = 2;
            gridBagLabelScaleY.anchor = GridBagConstraints.WEST;
            gridBagLabelScaleY.insets = new Insets(2, 6, 2, 2);

            GridBagConstraints gridBagTextScaleY = new GridBagConstraints();
            gridBagTextScaleY.gridx = 1;
            gridBagTextScaleY.gridy = 2;
            gridBagTextScaleY.anchor = GridBagConstraints.WEST;
            gridBagTextScaleY.insets = new Insets(2, 4, 2, 4);
            gridBagTextScaleY.weightx = 1.0;
            gridBagTextScaleY.ipadx = 100;

            jPanelScale = new JPanel();
            jPanelScale.setLayout(new GridBagLayout());
            jPanelScale.setBorder(BorderFactory.createTitledBorder(null, "Scale", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.PLAIN, 11), new Color(0, 70, 213)));
            jPanelScale.setMinimumSize(new Dimension(200, 100));
            jPanelScale.setPreferredSize(new Dimension(200, 100));

            jPanelScale.add(getJCheckBoxScale(), gridBagCheckBoxScale);
            JLabel label = new JLabel("Scale X");
            label.setMinimumSize(lDim);
            jPanelScale.add(label, gridBagLabelScaleX);
            jPanelScale.add(getJTextFieldScaleX(), gridBagTextScaleX);
            label = new JLabel("Scale Y");
            label.setMinimumSize(lDim);
            jPanelScale.add(label, gridBagLabelScaleY);
            jPanelScale.add(getJTextFieldScaleY(), gridBagTextScaleY);
        }
        return jPanelScale;
    }

    /**
     * This method initializes jPanelButtons
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelDefaults() {
        if (jPanelDefaults == null) {
            GridBagConstraints gridBagButtonStore = new GridBagConstraints();
            gridBagButtonStore.gridx = 0;
            gridBagButtonStore.gridy = 0;
            gridBagButtonStore.anchor = GridBagConstraints.WEST;
            gridBagButtonStore.weightx = 1.0;
            gridBagButtonStore.insets = new Insets(2, 0, 2, 0);

            GridBagConstraints gridBagButtonRestore = new GridBagConstraints();
            gridBagButtonRestore.gridx = 1;
            gridBagButtonRestore.gridy = 0;
            gridBagButtonRestore.anchor = GridBagConstraints.CENTER;
            gridBagButtonRestore.weightx = 1.0;
            gridBagButtonRestore.insets = new Insets(2, 0, 2, 0);

            GridBagConstraints gridBagButtonReset = new GridBagConstraints();
            gridBagButtonReset.gridx = 2;
            gridBagButtonReset.gridy = 0;
            gridBagButtonReset.anchor = GridBagConstraints.EAST;
            gridBagButtonReset.weightx = 1.0;
            gridBagButtonReset.insets = new Insets(2, 4, 2, 0);

            jPanelDefaults = new JPanel();
            jPanelDefaults.setLayout(new GridBagLayout());
            jPanelDefaults.setBorder(BorderFactory.createTitledBorder(null, "Defaults", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.PLAIN, 11), new Color(0, 70, 213)));
            jPanelDefaults.setMinimumSize(new Dimension(200, 60));
            jPanelDefaults.setPreferredSize(new Dimension(200, 60));

            jPanelDefaults.add(getJButtonStore(), gridBagButtonStore);
            jPanelDefaults.add(getJButtonRestore(), gridBagButtonRestore);
            jPanelDefaults.add(getJButtonReset(), gridBagButtonReset);
        }
        return jPanelDefaults;
    }

    /**
     * This method initializes jPanelForced
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelForced() {
        if (jPanelForced == null) {

            GridBagConstraints gridBagLabelForced = new GridBagConstraints();
            gridBagLabelForced.gridx = 0;
            gridBagLabelForced.gridy = 0;
            gridBagLabelForced.anchor = GridBagConstraints.WEST;
            gridBagLabelForced.insets = new Insets(2, 6, 2, 0);

            GridBagConstraints gridBagComboForced = new GridBagConstraints();
            gridBagComboForced.gridx = 1;
            gridBagComboForced.gridy = 0;
            gridBagComboForced.weightx = 1.0;
            gridBagComboForced.anchor = GridBagConstraints.WEST;
            gridBagComboForced.insets = new Insets(2, 4, 2, 4);

            jPanelForced = new JPanel();
            jPanelForced.setLayout(new GridBagLayout());
            jPanelForced.setBorder(BorderFactory.createTitledBorder(null, "Global forced flags", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.PLAIN, 11), new Color(0, 70, 213)));
            jPanelForced.setMinimumSize(new Dimension(200, 70));
            jPanelForced.setPreferredSize(new Dimension(200, 70));

            JLabel label = new JLabel("Force all");
            label.setMinimumSize(lDim);
            jPanelForced.add(label, gridBagLabelForced);
            jPanelForced.add(getJComboBoxForced(), gridBagComboForced);

        }
        return jPanelForced;
    }

    /**
     * This method initializes jPanelButtons
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelButtons() {
        if (jPanelButtons == null) {
            GridBagConstraints gridBagButtonCancel = new GridBagConstraints();
            gridBagButtonCancel.gridx = 0;
            gridBagButtonCancel.gridy = 0;
            gridBagButtonCancel.anchor = GridBagConstraints.WEST;
            gridBagButtonCancel.weightx = 1.0;
            gridBagButtonCancel.insets = new Insets(2, 8, 4, 0);

            GridBagConstraints gridBagButtonOk = new GridBagConstraints();
            gridBagButtonOk.gridx = 3;
            gridBagButtonOk.gridy = 0;
            gridBagButtonOk.anchor = GridBagConstraints.EAST;
            gridBagButtonOk.weightx = 1.0;
            gridBagButtonOk.insets = new Insets(2, 4, 4, 8);

            jPanelButtons = new JPanel();
            jPanelButtons.setLayout(new GridBagLayout());
            jPanelButtons.setMinimumSize(new Dimension(200, 30));
            jPanelButtons.setPreferredSize(new Dimension(200, 30));

            jPanelButtons.add(getJButtonOk(), gridBagButtonOk);
            jPanelButtons.add(getJButtonCancel(), gridBagButtonCancel);
        }
        return jPanelButtons;
    }


    /**
     * This method initializes jContentPane
     * @return javax.swing.JPanel
     */
    private JPanel getJContentPane() {
        if (jContentPane == null) {
            GridBagConstraints gridBagPanelResolution = new GridBagConstraints();
            gridBagPanelResolution.gridx = 0;
            gridBagPanelResolution.gridy = 0;
            gridBagPanelResolution.anchor = GridBagConstraints.NORTHWEST;
            gridBagPanelResolution.weightx = 1.0;
            gridBagPanelResolution.weighty = 1.0;
            gridBagPanelResolution.fill = GridBagConstraints.BOTH;

            GridBagConstraints gridBagPanelMove = new GridBagConstraints();
            gridBagPanelMove.gridx = 0;
            gridBagPanelMove.gridy = 1;
            gridBagPanelMove.anchor = GridBagConstraints.NORTHWEST;
            gridBagPanelMove.weightx = 1.0;
            gridBagPanelMove.weighty = 1.0;
            gridBagPanelMove.fill = GridBagConstraints.BOTH;

            GridBagConstraints gridBagPanelScale = new GridBagConstraints();
            gridBagPanelScale.gridx = 1;
            gridBagPanelScale.gridy = 0;
            gridBagPanelScale.anchor = GridBagConstraints.NORTHWEST;
            gridBagPanelScale.weightx = 1.0;
            gridBagPanelScale.weighty = 1.0;
            gridBagPanelScale.gridheight = 2;
            gridBagPanelScale.fill = GridBagConstraints.BOTH;

            GridBagConstraints gridBagPanelFPS = new GridBagConstraints();
            gridBagPanelFPS.gridx = 0;
            gridBagPanelFPS.gridy = 2;
            gridBagPanelFPS.anchor = GridBagConstraints.NORTHWEST;
            gridBagPanelFPS.weightx = 1.0;
            gridBagPanelFPS.weighty = 1.0;
            gridBagPanelFPS.fill = GridBagConstraints.BOTH;

            GridBagConstraints gridBagPanelTimes = new GridBagConstraints();
            gridBagPanelTimes.gridx = 1;
            gridBagPanelTimes.gridy = 2;
            gridBagPanelTimes.anchor = GridBagConstraints.NORTHWEST;
            gridBagPanelTimes.weightx = 1.0;
            gridBagPanelTimes.weighty = 1.0;
            gridBagPanelTimes.fill = GridBagConstraints.BOTH;

            GridBagConstraints gridBagPanelForced = new GridBagConstraints();
            gridBagPanelForced.gridx = 0;
            gridBagPanelForced.gridy = 3;
            gridBagPanelForced.anchor = GridBagConstraints.NORTHWEST;
            gridBagPanelForced.weightx = 1.0;
            gridBagPanelForced.weighty = 1.0;
            gridBagPanelForced.fill = GridBagConstraints.BOTH;

            GridBagConstraints gridBagPanelDefaults = new GridBagConstraints();
            gridBagPanelDefaults.gridx = 1;
            gridBagPanelDefaults.gridy = 3;
            gridBagPanelDefaults.anchor = GridBagConstraints.NORTHWEST;
            gridBagPanelDefaults.weightx = 1.0;
            gridBagPanelDefaults.weighty = 1.0;
            gridBagPanelDefaults.fill = GridBagConstraints.BOTH;

            GridBagConstraints gridBagPanelButtons = new GridBagConstraints();
            gridBagPanelButtons.gridx = 0;
            gridBagPanelButtons.gridy = 4;
            gridBagPanelButtons.anchor = GridBagConstraints.NORTHWEST;
            gridBagPanelButtons.weightx = 1.0;
            gridBagPanelButtons.weighty = 1.0;
            gridBagPanelButtons.gridwidth = 2;
            gridBagPanelButtons.fill = GridBagConstraints.HORIZONTAL;

            jContentPane = new JPanel();
            jContentPane.setLayout(new GridBagLayout());

            jContentPane.add(getJPanelResolution(), gridBagPanelResolution);
            jContentPane.add(getJPanelMove(), gridBagPanelMove);
            jContentPane.add(getJPanelFPS(), gridBagPanelFPS);
            jContentPane.add(getJPanelTimes(), gridBagPanelTimes);
            jContentPane.add(getJPanelScale(), gridBagPanelScale);
            jContentPane.add(getJPanelForced(), gridBagPanelForced);
            jContentPane.add(getJPanelDefaults(), gridBagPanelDefaults);
            jContentPane.add(getJPanelButtons(), gridBagPanelButtons);
        }
        return jContentPane;
    }

    /**
     * This method initializes jComboBoxResolution
     * @return javax.swing.JComboBox
     */
    private JComboBox getJComboBoxResolution() {
        if (jComboBoxResolution == null) {
            jComboBoxResolution = new JComboBox();
            jComboBoxResolution.setPreferredSize(new Dimension(200, 20));
            jComboBoxResolution.setMinimumSize(new Dimension(150, 20));
            jComboBoxResolution.setEditable(false);
            jComboBoxResolution.setToolTipText("Select the target resolution");
            jComboBoxResolution.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (isReady) {
                        int idx = jComboBoxResolution.getSelectedIndex();
                        for (Resolution r : Resolution.values()) {
                            if (idx == r.ordinal()) {
                                resolution = r;
                                if (!Core.getKeepFps()) {
                                    fpsTrg = Core.getDefaultFPS(r);
                                }
                                jComboBoxFPSTrg.setSelectedItem(ToolBox.formatDouble(fpsTrg));
                                break;
                            }
                        }
                    }
                }
            });
        }
        return jComboBoxResolution;
    }

    /**
     * This method initializes jCheckBoxFrameRate
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getJCheckBoxFrameRate() {
        if (jCheckBoxFrameRate == null) {
            jCheckBoxFrameRate = new JCheckBox();
            jCheckBoxFrameRate.setToolTipText("Convert frame rate from FPS Source to FPS target");
            jCheckBoxFrameRate.setText("Change frame rate");
            jCheckBoxFrameRate.setMnemonic('f');
            jCheckBoxFrameRate.setFocusable(false);
            jCheckBoxFrameRate.setIconTextGap(10);
            jCheckBoxFrameRate.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        changeFPS = jCheckBoxFrameRate.isSelected();
                        jComboBoxFPSSrc.setEnabled(changeFPS);
                    }
                }
            });
        }
        return jCheckBoxFrameRate;
    }

    /**
     * This method initializes jCheckBoxResolution
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getJCheckBoxResolution() {
        if (jCheckBoxResolution == null) {
            jCheckBoxResolution = new JCheckBox();
            jCheckBoxResolution.setToolTipText("Convert resolution");
            jCheckBoxResolution.setText("Convert resolution");
            jCheckBoxResolution.setMnemonic('r');
            jCheckBoxResolution.setDisplayedMnemonicIndex(8);
            jCheckBoxResolution.setFocusable(false);
            jCheckBoxResolution.setIconTextGap(10);
            jCheckBoxResolution.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        changeResolution = jCheckBoxResolution.isSelected();
                        jComboBoxResolution.setEnabled(changeResolution);
                    }
                }
            });
        }
        return jCheckBoxResolution;
    }

    /**
     * This method initializes jCheckBoxMove
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getJCheckBoxMove() {
        if (jCheckBoxMove == null) {
            jCheckBoxMove = new JCheckBox();
            jCheckBoxMove.setToolTipText("Apply settings for moving captions");
            jCheckBoxMove.setText("Apply 'move all' settings");
            jCheckBoxMove.setMnemonic('k');
            jCheckBoxMove.setDisplayedMnemonicIndex(8);
            jCheckBoxMove.setFocusable(false);
            jCheckBoxMove.setIconTextGap(10);
            jCheckBoxMove.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        moveCaptions = jCheckBoxMove.isSelected();
                    }
                }
            });
        }
        return jCheckBoxMove;
    }

    /**
     * This method initializes jComboBoxFPSSrc
     * @return javax.swing.JComboBox
     */
    private JComboBox getJComboBoxFPSSrc() {
        if (jComboBoxFPSSrc == null) {
            jComboBoxFPSSrc = new JComboBox();
            jComboBoxFPSSrc.setPreferredSize(new Dimension(200, 20));
            jComboBoxFPSSrc.setMinimumSize(new Dimension(150, 20));
            jComboBoxFPSSrc.setEditable(true);
            jComboBoxFPSSrc.setEnabled(false);
            jComboBoxFPSSrc.setToolTipText("Set the source frame rate (only needed for frame rate conversion)");
            jComboBoxFPSSrc.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        String s = (String)jComboBoxFPSSrc.getSelectedItem();
                        double d = SubtitleUtils.getFPS(s);
                        if (d > 0) {
                            fpsSrc = d;
                        }
                        jComboBoxFPSSrc.setSelectedItem(ToolBox.formatDouble(fpsSrc));
                        jComboBoxFPSSrc.getEditor().getEditorComponent().setBackground(okBgnd);
                        fpsSrcCertain = false;
                    }
                }
            });
            fpsSrcEditor.getDocument().addDocumentListener(new DocumentListener() {
                private void check() {
                    if (isReady) {
                        String s = fpsSrcEditor.getText();
                        double d = SubtitleUtils.getFPS(s);
                        Color c;
                        if (d>0) {
                            c = okBgnd;
                            fpsSrc = d;
                        } else {
                            c = errBgnd;
                        }
                        fpsSrcEditor.setBackground(c);
                        fpsSrcCertain = false;
                    }
                }

                public void insertUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    check();
                }
            });
        }
        return jComboBoxFPSSrc;
    }

    /**
     * This method initializes jComboBoxFPSTrg
     * @return javax.swing.JComboBox
     */
    private JComboBox getJComboBoxFPSTrg() {
        if (jComboBoxFPSTrg == null) {
            jComboBoxFPSTrg = new JComboBox();
            jComboBoxFPSTrg.setPreferredSize(new Dimension(200, 20));
            jComboBoxFPSTrg.setMinimumSize(new Dimension(150, 20));
            jComboBoxFPSTrg.setEditable(true);
            jComboBoxFPSTrg.setEnabled(false);
            jComboBoxFPSTrg.setToolTipText("Set the target frame rate");
            jComboBoxFPSTrg.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        String s = (String)jComboBoxFPSTrg.getSelectedItem();
                        double d = SubtitleUtils.getFPS(s);
                        if (d > 0) {
                            fpsTrg = d;
                        }
                        jComboBoxFPSTrg.setSelectedItem(ToolBox.formatDouble(fpsTrg));
                        jComboBoxFPSTrg.getEditor().getEditorComponent().setBackground(okBgnd);
                        //
                        delayPTS = (int)SubtitleUtils.syncTimePTS(delayPTS,fpsTrg,configuration.getFPSTrg());
                        jTextFieldDelay.setText(ToolBox.formatDouble(delayPTS/90.0));
                        //
                        minTimePTS = (int)SubtitleUtils.syncTimePTS(minTimePTS,fpsTrg,configuration.getFPSTrg());
                        jTextFieldMinTime.setText(ToolBox.formatDouble(minTimePTS/90.0));
                    }
                }
            });
            fpsTrgEditor.getDocument().addDocumentListener(new DocumentListener() {
                private void check() {
                    if (isReady) {
                        String s = fpsTrgEditor.getText();
                        double d = SubtitleUtils.getFPS(s);
                        Color c;
                        if (d>0) {
                            if ((int)SubtitleUtils.syncTimePTS(delayPTS,fpsTrg,configuration.getFPSTrg()) != delayPTS || minTimePTS != (int)SubtitleUtils.syncTimePTS(minTimePTS,fpsTrg,configuration.getFPSTrg())) {
                                c = warnBgnd;
                            } else {
                                c = okBgnd;
                            }
                            fpsTrg = d;
                        } else {
                            c = errBgnd;
                        }
                        fpsTrgEditor.setBackground(c);
                    }
                }

                public void insertUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    check();
                }
            });
        }
        return jComboBoxFPSTrg;
    }

    /**
     * This method initializes jTextFieldDelay
     * @return javax.swing.JTextField
     */
    private JTextField getJTextFieldDelay() {
        if (jTextFieldDelay == null) {
            jTextFieldDelay = new JTextField();
            jTextFieldDelay.setPreferredSize(new Dimension(200, 20));
            jTextFieldDelay.setToolTipText("Set global delay (in milliseconds) added to all timestamps");
            jTextFieldDelay.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        String s = jTextFieldDelay.getText();
                        try {
                            // don't use getDouble as the value can be negative
                            delayPTS = (int)SubtitleUtils.syncTimePTS((long)(Double.parseDouble(s)*90),fpsTrg, configuration.getFPSTrg());
                        } catch (NumberFormatException ex) {
                        }
                        jTextFieldDelay.setBackground(okBgnd);
                        jTextFieldDelay.setText(ToolBox.formatDouble(delayPTS/90.0));
                    }
                }
            });
            jTextFieldDelay.getDocument().addDocumentListener(new DocumentListener() {
                private void check() {
                    if (isReady) {
                        String s = jTextFieldDelay.getText();
                        try {
                            // don't use getDouble as the value can be negative
                            delayPTS = (int)SubtitleUtils.syncTimePTS((long)(Double.parseDouble(s)*90),fpsTrg, configuration.getFPSTrg());
                            if (!s.equalsIgnoreCase(ToolBox.formatDouble(delayPTS/90.0))) {
                                jTextFieldDelay.setBackground(warnBgnd);
                            } else {
                                jTextFieldDelay.setBackground(okBgnd);
                            }
                        } catch (NumberFormatException ex) {
                            jTextFieldDelay.setBackground(errBgnd);
                        }
                    }
                }

                public void insertUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    check();
                }
            });
        }
        return jTextFieldDelay;
    }



    /**
     * This method initializes jButtonCancel
     * @return javax.swing.JButton
     */
    private JButton getJButtonCancel() {
        if (jButtonCancel == null) {
            jButtonCancel = new JButton();
            jButtonCancel.setText("Cancel");
            jButtonCancel.setToolTipText("Lose all changes and use the default values");
            jButtonCancel.setMnemonic('c');
            jButtonCancel.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    cancel = true;
                    dispose();
                }
            });
        }
        return jButtonCancel;
    }

    /**
     * This method initializes jButtonStore
     * @return javax.swing.JButton
     */
    private JButton getJButtonStore() {
        if (jButtonStore == null) {
            jButtonStore = new JButton();
            jButtonStore.setText("Store");
            jButtonStore.setToolTipText("Store current settings as default");
            jButtonStore.setMnemonic('o');
            jButtonStore.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        // read values of editable boxes
                        String s;
                        double d;
                        // fps source
                        configuration.storeConvertFPS(changeFPS);
                        if (changeFPS) {
                            s = (String)jComboBoxFPSSrc.getSelectedItem();
                            d = SubtitleUtils.getFPS(s);
                            if (d > 0) {
                                fpsSrc = d;
                                configuration.storeFPSSrc(fpsSrc);
                            }
                        }
                        // fps target
                        s = (String)jComboBoxFPSTrg.getSelectedItem();
                        d = SubtitleUtils.getFPS(s);
                        if (d > 0) {
                            fpsTrg = d;
                            configuration.storeFPSTrg(fpsTrg);
                        }
                        // delay
                        s = jTextFieldDelay.getText();
                        try {
                            delayPTS = (int)SubtitleUtils.syncTimePTS((long)(Double.parseDouble(s)*90),fpsTrg, configuration.getFPSTrg());
                            configuration.storeDelayPTS(delayPTS);
                        } catch (NumberFormatException ex) {
                        }
                        // min time
                        configuration.storeFixShortFrames(fixShortFrames);
                        s = jTextFieldMinTime.getText();
                        try {
                            minTimePTS = (int)SubtitleUtils.syncTimePTS((long)(Double.parseDouble(s)*90),fpsTrg, configuration.getFPSTrg());
                            configuration.storeMinTimePTS(minTimePTS);
                        } catch (NumberFormatException ex) {
                        }
                        // exit
                        configuration.storeConvertResolution(changeResolution);
                        if (changeResolution) {
                            configuration.storeOutputResolution(resolution);
                        }
                        // scaleX
                        s = jTextFieldScaleX.getText();
                        d = ToolBox.getDouble(s);
                        if (d >0) {
                            if (d > MAX_FREE_SCALE_FACTOR) {
                                d = MAX_FREE_SCALE_FACTOR;
                            } else if (d < MIN_FREE_SCALE_FACTOR) {
                                d = MIN_FREE_SCALE_FACTOR;
                            }
                            scaleX = d;
                        }
                        // scaleY
                        s = jTextFieldScaleY.getText();
                        d = ToolBox.getDouble(s);
                        if (d >0) {
                            if (d > MAX_FREE_SCALE_FACTOR) {
                                d = MAX_FREE_SCALE_FACTOR;
                            } else if (d < MIN_FREE_SCALE_FACTOR) {
                                d = MIN_FREE_SCALE_FACTOR;
                            }
                            scaleY = d;
                        }
                        // set scale X/Y
                        configuration.storeApplyFreeScale(changeScale);
                        if (changeScale) {
                            configuration.storeFreeScaleFactor(scaleX, scaleY);
                        }
                        // forceAll is not stored
                        configuration.storeConfig();
                    }
                }
            });
        }
        return jButtonStore;
    }

    /**
     * This method initializes jButtonRestore
     * @return javax.swing.JButton
     */
    private JButton getJButtonRestore() {
        if (jButtonRestore == null) {
            jButtonRestore = new JButton();
            jButtonRestore.setText("Restore");
            jButtonRestore.setToolTipText("Restore last default settings");
            jButtonRestore.setMnemonic('e');
            jButtonRestore.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    changeResolution = configuration.loadConvertResolution();
                    if (changeResolution) {
                        resolution = configuration.loadOutputResolution();
                    }
                    changeFPS = configuration.loadConvertFPS();
                    if (changeFPS && !fpsSrcCertain) {
                        fpsSrc = configuration.loadFpsSrc();
                    }
                    fpsTrg = configuration.loadFpsTrg();
                    delayPTS = configuration.loadDelayPTS();
                    fixShortFrames = configuration.loadFixShortFrames();
                    minTimePTS = configuration.loadMinTimePTS();
                    changeScale = configuration.loadApplyFreeScale();
                    if (changeScale) {
                        scaleX = configuration.loadFreeScaleFactorX();
                        scaleY = configuration.loadFreeScaleFactorY();
                    }
                    forcedState = Core.getForceAll();
                    fillDialog();
                }
            });
        }
        return jButtonRestore;
    }


    /**
     * This method initializes jButtonReset
     * @return javax.swing.JButton
     */
    private JButton getJButtonReset() {
        if (jButtonReset == null) {
            jButtonReset = new JButton();
            jButtonReset.setText("Reset");
            jButtonReset.setToolTipText("Reset defaults");
            jButtonReset.setMnemonic('t');
            jButtonReset.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    changeResolution = CONVERT_RESOLUTION_BY_DEFAULT;
                    if (changeResolution) {
                        resolution = DEFAULT_TARGET_RESOLUTION;
                    }
                    changeFPS = CONVERT_FRAMERATE_BY_DEFAULT;
                    if (changeFPS) {
                        if (!fpsSrcCertain) {
                            fpsSrc = DEFAULT_SOURCE_FRAMERATE;
                        }
                        fpsTrg = DEFAULT_TARGET_FRAMERATE;
                    } else {
                        fpsTrg = fpsSrc;
                    }
                    delayPTS = DEFAULT_PTS_DELAY;
                    fixShortFrames = FIX_SHORT_FRAMES_BY_DEFAULT;
                    minTimePTS = DEFAULT_MIN_DISPLAY_TIME_PTS;
                    changeScale = APPLY_FREE_SCALE_BY_DEFAULT;
                    if (changeScale) {
                        scaleX = DEFAULT_FREE_SCALE_FACTOR_X;
                        scaleY = DEFAULT_FREE_SCALE_FACTOR_Y;
                    }
                    forcedState = ForcedFlagState.KEEP;
                    fillDialog();
                }
            });
        }
        return jButtonReset;
    }


    /**
     * Get cancel state
     * @return true if canceled
     */
    public boolean wasCanceled() {
        return cancel;
    }

    /**
     * This method initializes jCheckBoxScale
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getJCheckBoxScale() {
        if (jCheckBoxScale == null) {
            jCheckBoxScale = new JCheckBox();
            jCheckBoxScale.setToolTipText("Allow free scaling of subtitles in X and Y direction");
            jCheckBoxScale.setText("Apply free scaling");
            jCheckBoxScale.setMnemonic('a');
            jCheckBoxScale.setFocusable(false);
            jCheckBoxScale.setIconTextGap(10);
            jCheckBoxScale.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (isReady) {
                        changeScale = jCheckBoxScale.isSelected();
                        jTextFieldScaleX.setEnabled(changeScale);
                        jTextFieldScaleY.setEnabled(changeScale);
                    }
                }
            });
        }
        return jCheckBoxScale;
    }


    /**
     * This method initializes jCheckBoxFixMinTime
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getJCheckBoxFixMineTime() {
        if (jCheckBoxFixMinTime == null) {
            jCheckBoxFixMinTime = new JCheckBox();
            jCheckBoxFixMinTime.setToolTipText("Force a minimum display duration of 'Min Time'");
            jCheckBoxFixMinTime.setText("Fix too short frames");
            jCheckBoxFixMinTime.setMnemonic('s');
            jCheckBoxFixMinTime.setFocusable(false);
            jCheckBoxFixMinTime.setIconTextGap(10);
            jCheckBoxFixMinTime.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (isReady) {
                        fixShortFrames = jCheckBoxFixMinTime.isSelected();
                        jTextFieldMinTime.setEnabled(fixShortFrames);
                    }
                }
            });
        }
        return jCheckBoxFixMinTime;
    }

    /**
     * This method initializes jComboBoxMinTime
     * @return javax.swing.JComboBox
     */
    private JTextField getJTextFieldMinTime() {
        if (jTextFieldMinTime == null) {
            jTextFieldMinTime = new JTextField();
            jTextFieldMinTime.setEditable(true);
            jTextFieldMinTime.setPreferredSize(new Dimension(200, 20));
            jTextFieldMinTime.setEnabled(false);
            jTextFieldMinTime.setToolTipText("Set minimum display time for a subtitle");
            jTextFieldMinTime.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        String s = jTextFieldMinTime.getText();
                        try {
                            minTimePTS = (int)SubtitleUtils.syncTimePTS((long)(Double.parseDouble(s)*90),fpsTrg,configuration.getFPSTrg());
                        } catch (NumberFormatException ex) {
                        }
                        jTextFieldMinTime.setBackground(okBgnd);
                        jTextFieldMinTime.setText(ToolBox.formatDouble(minTimePTS/90.0));
                    }
                }
            });
            jTextFieldMinTime.getDocument().addDocumentListener(new DocumentListener() {
                private void check() {
                    if (isReady) {
                        String s = jTextFieldMinTime.getText();
                        try {
                            minTimePTS = (int)SubtitleUtils.syncTimePTS((long)(Double.parseDouble(s)*90),fpsTrg,configuration.getFPSTrg());
                            if (!s.equalsIgnoreCase(ToolBox.formatDouble(minTimePTS/90.0))) {
                                jTextFieldMinTime.setBackground(warnBgnd);
                            } else {
                                jTextFieldMinTime.setBackground(okBgnd);
                            }
                        } catch (NumberFormatException ex) {
                            jTextFieldMinTime.setBackground(errBgnd);
                        }
                    }
                }

                public void insertUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    check();
                }
            });
        }
        return jTextFieldMinTime;
    }

    /**
     * This method initializes jButtonOk
     * @return javax.swing.JButton
     */
    private JButton getJButtonOk() {
        if (jButtonOk == null) {
            jButtonOk = new JButton();
            jButtonOk.setText("  Ok  ");
            jButtonOk.setMnemonic('o');
            jButtonOk.setToolTipText("Use current values and continue");
            jButtonOk.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        // read values of editable boxes
                        String s;
                        double d;
                        // fps source
                        configuration.setConvertFPS(changeFPS);
                        if (changeFPS) {
                            s = (String)jComboBoxFPSSrc.getSelectedItem();
                            d = SubtitleUtils.getFPS(s);
                            if (d > 0) {
                                fpsSrc = d;
                                configuration.setFPSSrc(fpsSrc);
                            }
                        }
                        // fps target
                        s = (String)jComboBoxFPSTrg.getSelectedItem();
                        d = SubtitleUtils.getFPS(s);
                        if (d > 0) {
                            fpsTrg = d;
                            configuration.setFPSTrg(fpsTrg);
                        }
                        // delay
                        s = jTextFieldDelay.getText();
                        try {
                            delayPTS = (int)SubtitleUtils.syncTimePTS((long)(Double.parseDouble(s)*90),fpsTrg, configuration.getFPSTrg());
                            configuration.setDelayPTS(delayPTS);
                        } catch (NumberFormatException ex) {
                        }
                        // min time
                        configuration.setFixShortFrames(fixShortFrames);
                        s = jTextFieldMinTime.getText();
                        try {
                            minTimePTS = (int)SubtitleUtils.syncTimePTS((long)(Double.parseDouble(s)*90),fpsTrg, configuration.getFPSTrg());
                            configuration.setMinTimePTS(minTimePTS);
                        } catch (NumberFormatException ex) {
                        }
                        // exit
                        configuration.setConvertResolution(changeResolution);
                        if (changeResolution) {
                            configuration.setOutputResolution(resolution);
                        }
                        // scaleX
                        s = jTextFieldScaleX.getText();
                        d = ToolBox.getDouble(s);
                        if (d >0) {
                            if (d > MAX_FREE_SCALE_FACTOR) {
                                d = MAX_FREE_SCALE_FACTOR;
                            } else if (d < MIN_FREE_SCALE_FACTOR) {
                                d = MIN_FREE_SCALE_FACTOR;
                            }
                            scaleX = d;
                        }
                        // scaleY
                        s = jTextFieldScaleY.getText();
                        d = ToolBox.getDouble(s);
                        if (d >0) {
                            if (d > MAX_FREE_SCALE_FACTOR) {
                                d = MAX_FREE_SCALE_FACTOR;
                            } else if (d < MIN_FREE_SCALE_FACTOR) {
                                d = MIN_FREE_SCALE_FACTOR;
                            }
                            scaleY = d;
                        }
                        // set scale X/Y
                        configuration.setApplyFreeScale(changeScale);
                        if (changeScale) {
                            configuration.setFreeScaleFactor(scaleX, scaleY);
                        }
                        cancel = false;
                        // forced state
                        Core.setForceAll(forcedState);
                        // keep move settings
                        if (jCheckBoxMove.isEnabled()) {
                            Core.setMoveCaptions(moveCaptions);
                        }
                        //
                        dispose();
                    }
                }
            });
        }
        return jButtonOk;
    }

    /**
     * This method initializes jTextFieldScaleX
     *
     * @return javax.swing.JTextField
     */
    private JTextField getJTextFieldScaleX() {
        if (jTextFieldScaleX == null) {
            jTextFieldScaleX = new JTextField();
            jTextFieldScaleX.setPreferredSize(new Dimension(200, 20));
            jTextFieldScaleX.setToolTipText("Set free scaling factor in X direction");
            jTextFieldScaleX.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        String s = jTextFieldScaleX.getText();
                        double d = ToolBox.getDouble(s);
                        if (d >0) {
                            if (d > MAX_FREE_SCALE_FACTOR) {
                                d = MAX_FREE_SCALE_FACTOR;
                            } else if (d < MIN_FREE_SCALE_FACTOR) {
                                d = MIN_FREE_SCALE_FACTOR;
                            }
                            scaleX = d;
                        }
                        jTextFieldScaleX.setText(ToolBox.formatDouble(scaleX));
                        jTextFieldScaleX.setBackground(okBgnd);
                    }
                }
            });
            jTextFieldScaleX.getDocument().addDocumentListener(new DocumentListener() {
                private void check() {
                    if (isReady) {
                        String s = jTextFieldScaleX.getText();
                        double d = ToolBox.getDouble(s);
                        if (d >= MIN_FREE_SCALE_FACTOR && d <= MAX_FREE_SCALE_FACTOR) {
                            scaleX = d;
                            jTextFieldScaleX.setBackground(okBgnd);
                        } else {
                            jTextFieldScaleX.setBackground(errBgnd);
                        }
                    }
                }

                public void insertUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    check();
                }
            });
        }
        return jTextFieldScaleX;
    }

    /**
     * This method initializes jTextFieldScaleY
     *
     * @return javax.swing.JTextField
     */
    private JTextField getJTextFieldScaleY() {
        if (jTextFieldScaleY == null) {
            jTextFieldScaleY = new JTextField();
            jTextFieldScaleY.setPreferredSize(new Dimension(200, 20));
            jTextFieldScaleY.setToolTipText("Set free scaling factor in Y direction");
            jTextFieldScaleY.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (isReady) {
                        String s = jTextFieldScaleY.getText();
                        double d = ToolBox.getDouble(s);
                        if (d >0) {
                            if (d > MAX_FREE_SCALE_FACTOR) {
                                d = MAX_FREE_SCALE_FACTOR;
                            } else if (d < MIN_FREE_SCALE_FACTOR) {
                                d = MIN_FREE_SCALE_FACTOR;
                            }
                            scaleY = d;
                        }
                        jTextFieldScaleY.setText(ToolBox.formatDouble(scaleY));
                    }
                }
            });
            jTextFieldScaleY.getDocument().addDocumentListener(new DocumentListener() {
                private void check() {
                    if (isReady) {
                        String s = jTextFieldScaleY.getText();
                        double d = ToolBox.getDouble(s);
                        if (d >= MIN_FREE_SCALE_FACTOR && d <= MAX_FREE_SCALE_FACTOR) {
                            scaleY = d;
                            jTextFieldScaleY.setBackground(okBgnd);
                        } else {
                            jTextFieldScaleY.setBackground(errBgnd);
                        }
                    }
                }

                public void insertUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    check();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    check();
                }
            });
        }
        return jTextFieldScaleY;
    }

    /**
     * This method initializes jComboBoxForced
     * @return javax.swing.JComboBox
     */
    private JComboBox getJComboBoxForced() {
        if (jComboBoxForced == null) {
            jComboBoxForced = new JComboBox();
            jComboBoxForced.setPreferredSize(new Dimension(200, 20));
            jComboBoxForced.setMinimumSize(new Dimension(150, 20));
            jComboBoxForced.setEditable(false);
            jComboBoxForced.setToolTipText("Select the target resolution");
            jComboBoxForced.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (isReady) {
                        int idx = jComboBoxForced.getSelectedIndex();
                        for (ForcedFlagState s : ForcedFlagState.values()) {
                            if (idx == s.ordinal()) {
                                forcedState = s;
                                break;
                            }
                        }
                    }
                }
            });
        }
        return jComboBoxForced;
    }

    /**
     * Enable the "Keep move settings" checkbox (default: disabled)
     * @param e true: enable, false: disable
     */
    public void enableOptionMove(boolean e) {
        jCheckBoxMove.setEnabled(e);
    }
}
