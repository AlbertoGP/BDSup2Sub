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
package bdsup2sub;

import bdsup2sub.core.*;
import bdsup2sub.gui.main.MainFrame;
import bdsup2sub.tools.Props;
import bdsup2sub.utils.FilenameUtils;
import bdsup2sub.utils.SubtitleUtils;
import bdsup2sub.utils.ToolBox;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static bdsup2sub.core.Configuration.*;
import static bdsup2sub.core.Constants.*;

public class BDSup2Sub {

    private static final Configuration configuration = getInstance();

    /** Contains the parameter strings in lower case - order must be as in the enumeration {@link Parameters}. */
    private static final String PARAMS[] = {
        "res", "atr", "ltr1", "ltr2", "lang", "pal", "forced",
        "fps" , "dly", "tmin", "swap","movin", "movout", "cropy",
        "palmode", "verbatim", "filter", "tmerge", "scale", "acrop",
        "exppal", "fixinv", "movex", "forceall"
    };

    /**
     * Enumeration of command line parameters.
     */
    private enum Parameters {
        /** Define target resolution */
        RESOLUTION,
        /** Define alpha threshold */
        ALPHATHR,
        /** Define low/med luminance threshold */
        LUMTHR1,
        /** Define med/high luminance threshold */
        LUMTHR2,
        /** Define VobSub language */
        LANGUAGE,
        /** Load palette from file */
        PALETTE,
        /** Export only forced captions */
        FORCED,
        /** Define (source and) target frame rate */
        FPS,
        /** Define delay to add to all time stamps */
        DELAY,
        /** Define minimum display time for captions */
        MIN_TIME,
        /** Swap Cr/Cb components when loading a SUP */
        SWAP_CR_CB,
        /** Move captions inside given area */
        MOVE_INSIDE,
        /** Move captions outside given area */
        MOVE_OUTSIDE,
        /** Crop the upper n lines */
        CROP_Y,
        /** Palette creation mode */
        PALETTE_MODE,
        /** Verbatim text mode */
        VERBATIM,
        /** Scaling filter */
        FILTER,
        /** Scaling filter */
        TMERGE,
        /** Free scaling */
        SCALE,
        /** Alpha cropping threshold */
        ALPHA_CROP,
        /** Export target palette in PGCEdit text format */
        EXPORT_PAL,
        /** No fixing of zero alpha values (SUB/IDX and SUP/IFO import) */
        FIX_ZERO_ALPHA,
        /** move captions horizontally */
        MOVE_X,
        /** set/clear forced flag for all captions */
        FORCE_ALL,
        /** Unknown parameter */
        UNKNOWN;

        private static final Map<Integer,Parameters> LOOKUP = new HashMap<Integer,Parameters>();

        static {
            for(Parameters s : EnumSet.allOf(Parameters.class))
                LOOKUP.put(s.ordinal(), s);
        }

        /**
         * Reverse lookup implemented via hashtable.
         * @param val Ordinal value
         * @return Parameter with ordinal value val
         */
        public static Parameters get(int val) {
            return LOOKUP.get(val);
        }
    }

    /**
     * Return the fitting member of enumeration {@link Parameters} for a given parameter string.
     * @param s String to convert
     * @return Member of enumeration {@link Parameters}
     */
    private static Parameters getParam(String s) {
        for (int i=0; i < PARAMS.length; i++) {
            if (s.trim().toLowerCase().equals(PARAMS[i]))
                return Parameters.get(i);
        }
        return Parameters.UNKNOWN;
    }

    /**
     * Leave program, but free (file) resources before.
     * @param c Error code
     */
    private static void exit(int c) {
        Core.exit();
        System.exit(c);
    }

    /**
     * Leave program in a defined way after an error.
     * @param e Error message to print to the console
     */
    private static void fatalError(String e) {
        Core.exit();
        System.out.println("ERROR: " + e);
        System.exit(1);
    }

    /**
     * Print number of warnings and errors during conversion.
     */
    private static void printWarnings() {
        int w = Core.getWarnings();
        Core.resetWarnings();
        int e = Core.getErrors();
        Core.resetErrors();
        if (w+e > 0) {
            String s = "";
            if (w > 0) {
                if (w==1) {
                    s += w+" warning";
                } else {
                    s += w+" warnings";
                }
            }
            if (w>0 && e>0) {
                s += " and ";
            }
            if (e > 0) {
                if (e==1) {
                    s = e+" error";
                } else {
                    s = e+" errors";
                }
            }
            if (w+e < 3) {
                s = "There was "+s;
            } else {
                s = "There were "+s;
            }
            System.out.println(s);
        }
    }

    /**
     * Set "Look and Feel" to system default.
     */
    private static void setupGUI() {
        /* Set "Look and Feel" to system default */
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) { /* don't care */}
        /* Apple menu bar for MacOS */
        System.setProperty("com.apple.macos.useScreenMenuBar", "true");
    }

    /**
     * Main function.
     * @param args Parameters passed from the command line
     */
    public static void main(String[] args) {
        // Step 1
        // handle multiple parameters given as one within double quotes (for wildcard support)
        if (args.length == 1) {
            // try to detect if this is a single file name with spaces or a full command string
            int extCnt = 0;
            int pos = 0;
            String a = args[0].toLowerCase();
            do {
                int p = a.indexOf(".sup", pos);
                if (p == -1) {
                    p = a.indexOf(".sub", pos);
                }
                if (p == -1) {
                    p = a.indexOf(".idx", pos);
                }
                if (p == -1) {
                    p = a.indexOf(".xml", pos);
                }
                if (p != -1) {
                    pos = p+4;
                    extCnt++;
                } else {
                    pos = -1;
                }
            } while (pos != -1 && pos < a.length());
            if (args[0].indexOf('/') >= 0 || (args[0].indexOf(' ') >= 0 && extCnt > 1) ) {
                boolean inside = false;
                String s = args[0].trim();
                StringBuffer sb = new StringBuffer();
                ArrayList<String> al = new ArrayList<String>();
                for (int i=0; i<s.length();i++) {
                    char c = s.charAt(i);
                    switch (c) {
                        case ' ':
                            if (inside) {
                                sb.append(" ");
                            } else {
                                if (sb.length() > 0) {
                                    al.add(sb.toString());
                                }
                                sb = new StringBuffer();
                                for (int j=i+1; j<s.length(); j++) {
                                    c = s.charAt(j);
                                    if (c!=' ') {
                                        i = j-1;
                                        break;
                                    }
                                }
                            }
                            break;
                        case '\'':
                            inside = !inside;
                            break;
                        default:
                            sb.append(c);
                    }
                }
                if (inside) {
                    fatalError("Missing closing single quote");
                }
                if (sb.length() > 0) {
                    al.add(sb.toString());
                }
                args = al.toArray(args);
            }
        }

        // Step 2
        // check if help screen was requested
        if (args.length == 1 && (args[0].equals("/?") || args[0].equalsIgnoreCase("/help")) ) {
            // output help
            System.out.println(APP_NAME_AND_VERSION + " - " + AUTHOR_AND_DATE);
            System.out.println("Syntax:");
            System.out.println("java -jar BDSup2Sub <in> <out> [options]");
            System.out.println("Options:");
            System.out.println("    /res:<n>         : set resolution to 480, 576, 720 or 1080 - default 576");
            System.out.println("                       Predefined values: keep, ntsc=480, pal=576, 1440x1080");
            System.out.println("    /fps:<t>         : synchronize target frame rate to <t> - default: auto");
            System.out.println("                       Predefined values; 24p=23.976, 25p=25, 30p=29.967");
            System.out.println("                       /fps:keep preserves the source fps (for BD&XML)");
            System.out.println("    /fps:<s>,<t>     : convert framerate from <s> to <t>");
            System.out.println("                       Predefined values; 24p=23.976, 25p=25, 30p=29.967");
            System.out.println("                       /fps:auto,<t> detects source frame rate automatically");
            System.out.println("    /dly:<t>         : set delay in ms - default: 0.0");
            System.out.println("    /filter:<f>      : set scaling filter - default: bilinear");
            System.out.println("                       Supported values: bilinear, triangle, bicubic, bell,");
            System.out.println("                       b-spline, hermite, lanczos3, mitchell");
            System.out.println("    /palmode:<s>     : palette mode: keep, create, dither - default: create");
            System.out.println("    /tmin:<t>        : set minimum display time in ms - default: 500");
            System.out.println("    /tmerge:<t>      : set max time diff for merging subs in ms - default: 200");
            System.out.println("    /movin:<r>[,<o>] : move captions inside screen ratio <r>, +/- offset <o>");
            System.out.println("    /movout:<r>[,<o>]: move captions inside screen ratio <r>, +/- offset <o>");
            System.out.println("    /movex:<t>[,<o>] : move captions horizontally.<t> may be left,right,center.");
            System.out.println("                       +/- optional offset <o> (only if moving left or right)");
            System.out.println("    /cropy:<n>       : crop the upper/lower n lines - default: 0");
            System.out.println("    /acrop:<n>       : set alpha cropping threshold - default: 10");
            System.out.println("    /scale:<x>,<y>   : scale captions with free factors - default: 1.0,1.0");
            System.out.println("    /exppal[+/-]     : export target palette in PGCEdit format - default: off");
            System.out.println("    /forced[+/-]     : export only forced subtitles - default: off (export all)");
            System.out.println("    /forceall[+/-]   : set/clear forced flag for all subs - default: off (keep)");
            System.out.println("    /swap[+/-]       : swap Cr/Cb components - default: off (don't swap)");
            System.out.println("    /fixinv[+/-]     : fix zero alpha frame palette - default: off (don't fix)");
            System.out.println("    /verbatim[+/-]   : switch on verbatim console output mode - default: off");
            System.out.println("Options only for SUB/IDX or SUP/IFO as target:");
            System.out.println("    /atr:<n>         : set alpha threshold 0..255 - default 80");
            System.out.println("    /ltr1:<n>        : set lum lo/mid threshold 0..255 - default auto");
            System.out.println("    /ltr2:<n>        : set lum mid/hi threshold 0..255 - default auto");
            System.out.println("    /lang:<s>        : set language to string <s> - default: de (only SUB/IDX)");
            System.out.println("    /pal:<s>         : load palette file <s> - default: use builtin palette");
            System.out.println("\nNote:\n");
            System.out.println("Boolean parameters like \"verbatim\" are switched off with a trailing \"-\".");
            System.out.println("\nWildcard support:");
            System.out.println("To use wildcards, enclose the whole parameter string in double quotes.");
            System.out.println("For filenames containing spaces, use single quotes around the file name.");
            System.out.println("Use \"*\" for any character and \"?\" for one character in the source name");
            System.out.println("Use exactly one \"*\" in the target file name.");
            System.out.println("Example:");
            System.out.println("java -jar BDSup2Sub.jar \"'movie* 1?.sup' dvd_*.sub /res:720 /fps:25p\"");
        } else {
            // analyze parameters
            String cmdLine = "";
            for (String arg : args) {
                cmdLine += arg + " ";
            }
            System.out.println("\nCommand line:\n" + getJarName() + " " + cmdLine + "\n");

            // parse parameters
            String src = null;
            String trg = null;
            int alphaThr = configuration.getAlphaThreshold();
            int lumThr1 = -1;
            int lumThr2 = -1;
            int langIdx = -1;
            Resolution r = Resolution.PAL;
            OutputMode mode = null;
            boolean defineFPStrg = false;
            double screenRatio = -1;
            for (String arg : args) {
                String a = arg;

                // detect source and target
                if (src == null) {
                    src = a;
                    continue;
                } else if (trg == null) {
                    trg = a;
                    String ext = FilenameUtils.getExtension(trg);
                    if (ext.isEmpty()) {
                        fatalError("No extension given for target " + trg);
                    }
                    if (ext.equals("sup")) {
                        mode = OutputMode.BDSUP;
                    } else if (ext.equals("sub") || ext.equals("idx")) {
                        mode = OutputMode.VOBSUB;
                    } else if (ext.equals("xml")) {
                        mode = OutputMode.XML;
                    } else if (ext.equals("ifo")) {
                        mode = OutputMode.SUPIFO;
                    } else {
                        fatalError("Unknown extension of target " + trg);
                    }
                    configuration.setOutputMode(mode);
                    continue;
                }

                boolean switchOn = true;

                // analyze normal parameters
                if (a.length() < 4 || a.charAt(0) != '/') {
                    fatalError("Illegal argument: " + a);
                }
                int pos = a.indexOf(':');
                String val;
                if (pos > -1) {
                    val = a.substring(pos + 1, a.length());
                    a = a.substring(1, pos);
                } else {
                    val = "";
                    a = a.substring(1);
                    // check +/- at end of parameter
                    int last = a.length() - 1;
                    if (a.indexOf('+') == last) {
                        a = a.substring(0, last);
                    } else if (a.indexOf('-') == last) {
                        a = a.substring(0, last);
                        switchOn = false;
                    }
                }

                String strSwitchOn;
                if (switchOn) {
                    strSwitchOn = "ON";
                } else {
                    strSwitchOn = "OFF";
                }

                Parameters p = getParam(a);
                int ival = ToolBox.getInt(val);
                switch (p) {
                    case ALPHATHR:
                        // alpha threshold for SUB/IDX conversion
                        if (ival < 0 || ival > 255) {
                            fatalError("Illegal number range for alpha threshold: " + val);
                        } else {
                            alphaThr = ival;
                        }
                        System.out.println("OPTION: set alpha threshold to " + ival);
                        break; // useless
                    case LUMTHR1:
                        // luminance threshold low-med for SUB/IDX conversion
                        if (ival < 0 || ival > 255) {
                            fatalError("Illegal number range for luminance: " + val);
                        } else {
                            lumThr1 = ival;
                        }
                        System.out.println("OPTION: set low/mid luminance threshold to " + ival);
                        break; // useless
                    case LUMTHR2:
                        // luminance threshold med-high for SUB/IDX conversion
                        if (ival < 0 || ival > 255) {
                            fatalError("Illegal number range for luminance: " + val);
                        } else {
                            lumThr2 = ival;
                        }
                        System.out.println("OPTION: set mid/high luminance threshold to " + ival);
                        break; // useless
                    case RESOLUTION:
                        // resolution for export
                        if (val.toLowerCase().equals("keep")) {
                            configuration.setConvertResolution(false);
                        } else {
                            configuration.setConvertResolution(true);
                            if (val.toLowerCase().equals("pal") || ival == 576) {
                                r = Resolution.PAL;
                                if (!defineFPStrg) {
                                    configuration.setFpsTrg(Framerate.PAL.getValue());
                                }
                            } else if (val.toLowerCase().equals("ntsc") || ival == 480) {
                                r = Resolution.NTSC;
                                if (!defineFPStrg) {
                                    configuration.setFpsTrg(Framerate.NTSC.getValue());
                                }
                            } else if (val.toLowerCase().equals("720p") || ival == 720) {
                                r = Resolution.HD_720;
                                if (!defineFPStrg) {
                                    configuration.setFpsTrg(Framerate.FPS_23_976.getValue());
                                }
                            } else if (val.toLowerCase().equals("1440x1080")) {
                                r = Resolution.HD_1440x1080;
                                if (!defineFPStrg) {
                                    configuration.setFpsTrg(Framerate.FPS_23_976.getValue());
                                }
                            } else if (val.toLowerCase().equals("1080p") || ival == 1080) {
                                r = Resolution.HD_1080;
                                if (!defineFPStrg) {
                                    configuration.setFpsTrg(Framerate.FPS_23_976.getValue());
                                }
                            } else {
                                fatalError("Illegal resolution: " + val);
                            }
                        }
                        System.out.println("OPTION: set resolution to " + r);
                        break;
                    case LANGUAGE:
                        // language used for SUB/IDX export
                        langIdx = -1;
                        for (int l = 0; l < LANGUAGES.length; l++)
                            if (LANGUAGES[l][1].equals(val)) {
                                langIdx = l;
                                break;
                            }
                        if (langIdx == -1) {
                            System.out.println("ERROR: Unknown language " + val);
                            System.out.println("Use one of the following 2 character codes:");
                            for (String[] language : LANGUAGES) {
                                System.out.println("    " + language[1] + " - " + language[0]);
                            }
                            exit(1);
                        }
                        System.out.println("OPTION: set language to " + LANGUAGES[langIdx][0] +
                                " (" + LANGUAGES[langIdx][1] + ")");
                        break;
                    case PALETTE:
                        // load color profile for for SUB/IDX conversion
                        File f = new File(val);
                        if (f.exists()) {
                            byte id[] = ToolBox.getFileID(val, 4);
                            if (id == null || id[0] != 0x23 || id[1] != 0x43 || id[2] != 0x4F || id[3] != 0x4C) { //#COL
                                fatalError("No valid palette file: " + val);
                            }
                        } else {
                            fatalError("Palette file not found: " + val);
                        }
                        Props colProps = new Props();
                        colProps.load(val);
                        for (int c = 0; c < 15; c++) {
                            String s = colProps.get("Color_" + c, "0,0,0");
                            String sp[] = s.split(",");
                            if (sp.length >= 3) {
                                int red = Integer.valueOf(sp[0].trim()) & 0xff;
                                int green = Integer.valueOf(sp[1].trim()) & 0xff;
                                int blue = Integer.valueOf(sp[2].trim()) & 0xff;
                                Core.getCurrentDVDPalette().setColor(c + 1, new Color(red, green, blue));
                            }
                        }
                        System.out.println("OPTION: loaded palette from " + val);
                        break;
                    case FORCED:
                        // export only forced subtitles (when converting from BD-SUP)
                        configuration.setExportForced(switchOn);
                        System.out.println("OPTION: export only forced subtitles: " + strSwitchOn);
                        break;
                    case SWAP_CR_CB:
                        // export only forced subtitles (when converting from BD-SUP)
                        Core.setSwapCrCb(switchOn);
                        System.out.println("OPTION: swap Cr/Cb components: " + strSwitchOn);
                        break;
                    case FPS:
                        // set target (and source) frame rate
                        double fpsSrc, fpsTrg;
                        pos = val.indexOf(',');
                        if (pos > 0) {
                            boolean autoFPS;
                            // source and target: frame rate conversion
                            String srcStr = val.substring(0, pos).trim();
                            if (srcStr.toLowerCase().equals("auto")) {
                                // leave default value
                                autoFPS = true;
                                fpsSrc = 0; // stub to avoid undefined warning
                            } else {
                                autoFPS = false;
                                fpsSrc = SubtitleUtils.getFps(srcStr);
                                if (fpsSrc <= 0) {
                                    fatalError("invalid source framerate: " + srcStr);
                                }
                                configuration.setFpsSrc(fpsSrc);
                            }
                            fpsTrg = SubtitleUtils.getFps(val.substring(pos + 1));
                            if (fpsTrg <= 0) {
                                fatalError("invalid target value: " + val.substring(pos + 1));
                            }
                            if (!autoFPS) {
                                configuration.setFpsTrg(fpsTrg);
                            }
                            configuration.setConvertFPS(true);
                            System.out.println("OPTION: convert framerate from "
                                    + (autoFPS ? "<auto>" : ToolBox.formatDouble(fpsSrc))
                                    + "fps to " + ToolBox.formatDouble(fpsTrg) + "fps");
                            defineFPStrg = true;
                        } else {
                            // only target: frame rate synchronization
                            if (val.toLowerCase().equals("keep")) {
                                Core.setKeepFps(true);
                                System.out.println("OPTION: use source fps as target fps");
                            } else {
                                fpsTrg = SubtitleUtils.getFps(val);
                                if (fpsTrg <= 0) {
                                    fatalError("invalid target framerate: " + val);
                                }
                                configuration.setFpsTrg(fpsTrg);
                                System.out.println("OPTION: synchronize target framerate to " + ToolBox.formatDouble(fpsTrg) + "fps");
                                defineFPStrg = true;
                            }
                        }
                        break;
                    case DELAY:
                        // add a delay
                        double delay = 0;
                        try {
                            // don't use getDouble as the value can be negative
                            delay = Double.parseDouble(val.trim()) * 90.0;
                        } catch (NumberFormatException ex) {
                            fatalError("Illegal delay value: " + val);
                        }
                        int delayPTS = (int) SubtitleUtils.syncTimePTS((long) delay, configuration.getFpsTrg(), configuration.getFpsTrg());
                        configuration.setDelayPTS(delayPTS);
                        System.out.println("OPTION: set delay to " + ToolBox.formatDouble(delayPTS / 90.0));
                        break;
                    case MIN_TIME:
                        // set minimum duration
                        double t = 0;
                        try {
                            t = Double.parseDouble(val.trim()) * 90.0;
                        } catch (NumberFormatException ex) {
                            fatalError("Illegal value for minimum display time: " + val);
                        }
                        int tMin = (int) SubtitleUtils.syncTimePTS((long) t, configuration.getFpsTrg(), configuration.getFpsTrg());
                        configuration.setMinTimePTS(tMin);
                        configuration.setFixShortFrames(true);
                        System.out.println("OPTION: set delay to " + ToolBox.formatDouble(tMin / 90.0));
                        break;
                    case MOVE_INSIDE:
                    case MOVE_OUTSIDE:
                        // move captions
                        String sm;
                        if (p == Parameters.MOVE_OUTSIDE) {
                            Core.setMoveModeY(CaptionMoveModeY.MOVE_OUTSIDE_BOUNDS);
                            sm = "outside";
                        } else {
                            Core.setMoveModeY(CaptionMoveModeY.MOVE_INSIDE_BOUNDS);
                            sm = "inside";
                        }
                        String ratio;
                        pos = val.indexOf(',');
                        if (pos > 0) {
                            ratio = val.substring(0, pos);
                        } else {
                            ratio = val;
                        }
                        screenRatio = ToolBox.getDouble(ratio);
                        if (screenRatio <= (16.0 / 9)) {
                            fatalError("invalid screen ratio: " + ratio);
                        }
                        int moveOffsetY = Core.getMoveOffsetY();
                        if (pos > 0) {
                            moveOffsetY = ToolBox.getInt(val.substring(pos + 1));
                            if (moveOffsetY < 0) {
                                fatalError("invalid pixel offset: " + val.substring(pos + 1));
                            }
                            Core.setMoveOffsetY(moveOffsetY);
                        }
                        System.out.println("OPTION: moving captions " + sm + " "
                                + ToolBox.formatDouble(screenRatio) + ":1 plus/minus "
                                + moveOffsetY + " pixels");
                        break;
                    case MOVE_X:
                        // move captions
                        String mx;
                        pos = val.indexOf(',');
                        if (pos > 0) {
                            mx = val.substring(0, pos);
                        } else {
                            mx = val;
                        }
                        if (mx.equalsIgnoreCase("left")) {
                            Core.setMoveModeX(CaptionMoveModeX.LEFT);
                        } else if (mx.equalsIgnoreCase("right")) {
                            Core.setMoveModeX(CaptionMoveModeX.RIGHT);
                        } else if (mx.equalsIgnoreCase("center")) {
                            Core.setMoveModeX(CaptionMoveModeX.CENTER);
                        } else {
                            fatalError("invalid moveX command:" + mx);
                        }

                        int moveOffsetX = Core.getMoveOffsetX();
                        if (pos > 0) {
                            moveOffsetX = ToolBox.getInt(val.substring(pos + 1));
                            if (moveOffsetX < 0) {
                                fatalError("invalid pixel offset: " + val.substring(pos + 1));
                            }
                            Core.setMoveOffsetX(moveOffsetX);
                        }
                        System.out.println("OPTION: moving captions to the " + mx
                                + " plus/minus " + moveOffsetX + " pixels");
                        break;
                    case CROP_Y:
                        // add a delay
                        int cropY;
                        cropY = ToolBox.getInt(val.trim());
                        if (cropY >= 0) {
                            Core.setCropOfsY(cropY);
                            System.out.println("OPTION: set delay to " + cropY);
                        } else
                            fatalError("invalid crop y value: " + val.substring(0, pos));
                        break;
                    case PALETTE_MODE:
                        // select palette mode
                        if (val.toLowerCase().equals("keep")) {
                            configuration.setPaletteMode(PaletteMode.KEEP_EXISTING);
                        } else if (val.toLowerCase().equals("create")) {
                            configuration.setPaletteMode(PaletteMode.CREATE_NEW);
                        } else if (val.toLowerCase().equals("dither")) {
                            configuration.setPaletteMode(PaletteMode.CREATE_DITHERED);
                        } else {
                            fatalError("invalid palette mode: " + val);
                        }
                        System.out.println("OPTION: set palette mode to " + val.toLowerCase());
                        break;
                    case VERBATIM:
                        // select verbatim console output
                        configuration.setVerbatim(switchOn);
                        System.out.println("OPTION: enabled verbatim output mode: " + strSwitchOn);
                        break;
                    case FILTER:
                        // select scaling filter
                        ScalingFilter sfs = null;
                        for (ScalingFilter sf : ScalingFilter.values())
                            if (sf.toString().equalsIgnoreCase(val)) {
                                sfs = sf;
                                break;
                            }
                        if (sfs != null) {
                            configuration.setScalingFilter(sfs);
                            System.out.println("OPTION: set scaling filter to: " + sfs.toString());
                        } else {
                            fatalError("invalid scaling filter: " + val);
                        }
                        break;
                    case TMERGE:
                        // set maximum difference for merging captions
                        t = 0;
                        try {
                            t = Double.parseDouble(val.trim()) * 90.0;
                        } catch (NumberFormatException ex) {
                            fatalError("Illegal value for maximum merge time: " + val);
                        }
                        int ti = (int) (t + 0.5);
                        configuration.setMergePTSdiff(ti);
                        System.out.println("OPTION: set maximum merge time to " + ToolBox.formatDouble(ti / 90.0));
                        break;
                    case SCALE:
                        // free x/y scaling factors
                        pos = val.indexOf(',');
                        if (pos > 0) {
                            double scaleX = ToolBox.getDouble(val.substring(0, pos));
                            if (scaleX < MIN_FREE_SCALE_FACTOR || scaleX > MAX_FREE_SCALE_FACTOR) {
                                fatalError("invalid x scaling factor: " + val.substring(0, pos));
                            }
                            double scaleY = ToolBox.getDouble(val.substring(pos + 1));
                            if (scaleY < MIN_FREE_SCALE_FACTOR || scaleY > MAX_FREE_SCALE_FACTOR) {
                                fatalError("invalid y scaling factor: " + val.substring(pos + 1));
                            }
                            configuration.setFreeScaleFactor(scaleX, scaleY);
                            System.out.println("OPTION: set free scaling factors to "
                                    + ToolBox.formatDouble(scaleX) + ", "
                                    + ToolBox.formatDouble(scaleY));
                        } else {
                            fatalError("invalid scale command (missing comma): " + val);
                        }

                        break;
                    case ALPHA_CROP:
                        // alpha threshold for cropping and patching background color to black
                        if (ival < 0 || ival > 255) {
                            fatalError("Illegal number range for alpha cropping threshold: " + val);
                        } else {
                            configuration.setAlphaCrop(ival);
                        }
                        System.out.println("OPTION: set alpha cropping threshold to " + ival);
                        break;
                    case EXPORT_PAL:
                        // export target palette in PGCEdit text format
                        configuration.setWritePGCEditPalette(switchOn);
                        System.out.println("OPTION: export target palette in PGCEDit text format: " + strSwitchOn);
                        break;
                    case FIX_ZERO_ALPHA:
                        // fix zero alpha frame palette for SUB/IDX and SUP/IFO
                        configuration.setFixZeroAlpha(switchOn);
                        System.out.println("OPTION: fix zero alpha frame palette for SUB/IDX and SUP/IFO: " + strSwitchOn);
                        break; // useless
                    case FORCE_ALL:
                        // clear/set forced flag for all captions
                        Core.setForceAll(switchOn ? ForcedFlagState.SET : ForcedFlagState.CLEAR);
                        System.out.println("OPTION: set forced state of all captions to: " + strSwitchOn);
                        break;
                    default: //UNKNOWN:
                        fatalError("Illegal argument: " + arg);
                }
            }

            configuration.setOutputResolution(r);

            if (!Core.getKeepFps() && !defineFPStrg) {
                configuration.setFpsTrg(Core.getDefaultFPS(r));
                System.out.println("Target frame rate set to " + ToolBox.formatDouble(configuration.getFpsTrg())+"fps");
            }

            // Step 3
            // open GUI if trg file name is missing
            if (trg == null) {
                setupGUI();
                final String sourceFile = src;
                javax.swing.SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        // Schedule a job for the event-dispatching thread:
                        // create and show GUI
                        configuration.setCliMode(false);
                        new MainFrame(sourceFile).setVisible(true);
                    }
                });

                return;
            }

            // Step 4
            // handle wildcards
            String srcFileNames[];
            String trgFileNames[];
            String path;
            // multiple files
            if (src.indexOf('*') != -1) {
                path = FilenameUtils.getParent(src);
                if (path == null || path.length() == 0) {
                    path = "." + File.separatorChar;
                }
                File[] srcFiles = (new File(path)).listFiles(new FileFilter(FilenameUtils.getName(src)));
                if (srcFiles.length == 0) {
                    fatalError("No match found for '" + FilenameUtils.addSeparator(path) + src + "'");
                }
                if (trg.indexOf('*') == -1) {
                    fatalError("No wildcards in target string!");
                }
                srcFileNames = new String[srcFiles.length];
                trgFileNames = new String[srcFiles.length];
                for (int i=0; i<srcFiles.length; i++) {
                    srcFileNames[i] = FilenameUtils.addSeparator(path) + srcFiles[i].getName();
                    trgFileNames[i] = trg.replace("*", FilenameUtils.removeExtension(srcFiles[i].getName()));
                    //System.out.println(srcFileNames[i]+" - "+trgFileNames[i]);
                }
            } else {
                srcFileNames = new String[1];
                trgFileNames = new String[1];
                srcFileNames[0] = src;
                int aPos = trg.indexOf('*');
                if (aPos != -1) {
                    // replace asterisk by path+filename of source without the extension
                    trgFileNames[0] = trg.replace("*", FilenameUtils.removeExtension(src));
                } else {
                    trgFileNames[0] = trg;
                }
            }

            // Step 5
            // the main loop
            for (int fnum=0; fnum < srcFileNames.length; fnum++) {
                src = srcFileNames[fnum];
                trg = trgFileNames[fnum];
                // ok, let's start
                try {
                    System.out.println("\nConverting " + mode + "\n");
                    // check input file
                    if (!new File(src).exists()) {
                        throw new CoreException("File '"+src+"' does not exist.");
                    }
                    boolean xml = FilenameUtils.getExtension(src).equalsIgnoreCase("xml");
                    boolean idx = FilenameUtils.getExtension(src).equalsIgnoreCase("idx");
                    boolean ifo = FilenameUtils.getExtension(src).equalsIgnoreCase("ifo");
                    byte id[] = ToolBox.getFileID(src, 4);
                    StreamID sid = (id == null) ? StreamID.UNKNOWN : Core.getStreamID(id);
                    if (!idx && !xml && !ifo && sid == StreamID.UNKNOWN) {
                        throw new CoreException("File '"+src+"' is not a supported subtitle stream.");
                    }
                    Core.setCurrentStreamID(sid);
                    // check output file(s)
                    File fi,fs;
                    if (configuration.getOutputMode() == OutputMode.VOBSUB) {
                        fi = new File(FilenameUtils.removeExtension(trg) + ".idx");
                        fs = new File(FilenameUtils.removeExtension(trg) + ".sub");
                    } else {
                        fs = new File(FilenameUtils.removeExtension(trg) + ".sup");
                        fi = fs; // we don't need the idx file
                    }
                    if (fi.exists() || fs.exists()) {
                        if ((fi.exists() && !fi.canWrite()) || (fs.exists() && !fs.canWrite())) {
                            throw new CoreException("Target file '" + trg + "' is write protected.");
                        }
                    }
                    // read input file
                    if (xml || sid == StreamID.XML) {
                        Core.readXml(src);
                    } else if (idx || sid == StreamID.DVDSUB || sid == StreamID.IDX) {
                        Core.readVobSub(src);
                    } else if (ifo || sid == StreamID.IFO) {
                        Core.readSupIfo(src);
                    } else {
                        Core.readSup(src);
                    }

                    Core.scanSubtitles();
                    printWarnings();
                    // move captions
                    if (Core.getMoveModeX() != CaptionMoveModeX.KEEP_POSITION || Core.getMoveModeY() != CaptionMoveModeY.KEEP_POSITION) {
                        Core.setCineBarFactor((1.0 - (16.0/9)/screenRatio)/2.0);
                        Core.moveAllToBounds();
                    }
                    // set some values
                    if (configuration.isExportForced() && Core.getNumForcedFrames() == 0) {
                        throw new CoreException("No forced subtitles found.");
                    }
                    int lt[] = configuration.getLuminanceThreshold();
                    if (lumThr1 > 0) {
                        lt[1] = lumThr1;
                    }
                    if (lumThr2 > 0) {
                        lt[0] = lumThr2;
                    }
                    configuration.setLuminanceThreshold(lt);
                    configuration.setAlphaThreshold(alphaThr);
                    if (langIdx != -1) {
                        configuration.setLanguageIdx(langIdx);
                    }
                    // write output
                    Core.writeSub(trg);
                } catch (CoreException ex) {
                    Core.printErr(ex.getMessage());
                } catch (Exception ex) {
                    ToolBox.showException(ex);
                    Core.printErr(ex.getMessage());
                }
                // clean up
                printWarnings();
                Core.exit();
            }
            System.out.println("\nConversion of "+srcFileNames.length+" file(s) finished");
            System.exit(0);
        }
    }

    /**
     * Used to determine the name and path of the JAR file.
     * @return Name and path of the JAR file
     */
    private static String getJarName() {
        Object c = new BDSup2Sub(); // dummy
        String s = c.getClass().getName().replace('.','/') + ".class";
        String r = "";
        URL url = c.getClass().getClassLoader().getResource(s);
        int pos;
        try {
            r = URLDecoder.decode(url.getPath(),"UTF-8");
            if (((pos=r.toLowerCase().indexOf("file:")) != -1)) {
                r = r.substring(pos+5);
            }
            if ((pos=r.toLowerCase().indexOf(s.toLowerCase())) != -1) {
                r = r.substring(0,pos);
            }
            // special handling for JAR
            pos = r.lastIndexOf(".jar");
            if (pos != -1) {
                r = r.substring(0, pos+4);
            } else {
                r += APP_NAME + ".jar";
            }
        } catch (UnsupportedEncodingException ex) {
        }

        r = r.replace('/', File.separatorChar);
        if (r.length() > 3 && r.charAt(2) == ':' && r.charAt(0) == '\\') {
            r = r.substring(1);
        }
        return r;
    }
}

/**
 * FilenameFilter for simple wildcard support.
 * Supported wildcard:<br>
 * "*" for any number or occurrence of any character.<br>
 * "?" for single occurence of any character.<br>
 */

class FileFilter implements FilenameFilter {

    /** file name pattern */
    private String fnPattern;

    /**
     * Constructor - creates regular expression for given string with simple wildcards
     * @param pattern String with simple wildcards ("*" and "&")
     */
    public FileFilter(String pattern) {
        fnPattern = pattern;
        // use escape character for special characters
        fnPattern = fnPattern.replace("\\", "\\\\"); // "\" -> "\\"
        fnPattern = fnPattern.replace(".", "\\.");   // "*" -> "\."
        fnPattern = fnPattern.replace("$", "\\$");   // "$" -> "\$"
        fnPattern = fnPattern.replace("^", "\\^");   // "^" -> "\^"
        // replace wildcards with regular expressions
        fnPattern = fnPattern.replace("*", ".*");  // "*" -> ".*"
        fnPattern = fnPattern.replace("?", ".") ;  // "?" -> "."
    }

    /* (non-Javadoc)
     * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
     */
    public boolean accept(File dir, String name) {
        return name.matches(fnPattern);
    }
}
