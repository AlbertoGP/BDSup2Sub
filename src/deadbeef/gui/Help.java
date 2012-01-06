package deadbeef.gui;


import java.awt.Desktop;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import deadbeef.core.Core;
import deadbeef.tools.Props;
import deadbeef.tools.ToolBox;

/*
 * Copyright 2009 Volker Oth (0xdeadbeef)
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

/**
 * Simple help window - display a html page as online help - part of BDSup2Sub GUI classes.
 *
 * @author 0xdeadbeef
 */
public class Help extends JFrame {
	final static long serialVersionUID = 0x000000001;

	private javax.swing.JPanel jContentPane = null;
	private JScrollPane jScrollPane = null;
	private JPopupMenu jPopupMenu = null;
	private JMenuItem jPopupMenuItemCopy = null;
	private JMenuItem jPopupMenuItemOpen = null;
	private JEditorPane thisEditor = null;
	
	private URL helpURL;
	private Props chapters;
	
	/**
	 * init function. loads html page.
	 */
	private void init() {
		ClassLoader loader = Help.class.getClassLoader();
		helpURL = loader.getResource("help.htm");
		chapters = new Props();
		chapters.load(loader.getResource("help.ini"));
		
		try {
			thisEditor = new JEditorPane( helpURL );
			thisEditor.setEditable( false );			
			// needed to open browser via clicking on a link
			thisEditor.addHyperlinkListener(new HyperlinkListener() {
				public void hyperlinkUpdate(HyperlinkEvent e) {
					URL url = e.getURL();
					if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
						try {							
							if (url.sameFile(helpURL))
								thisEditor.setPage(url);
							else
								Desktop.getDesktop().browse(url.toURI());
						} catch (IOException ex) {
						} catch (URISyntaxException ex) {
						}
					} else if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
						if (url.sameFile(helpURL))
							thisEditor.setToolTipText(url.getRef());
						else
							thisEditor.setToolTipText(url.toExternalForm());
					} else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
						thisEditor.setToolTipText(null);
					}
				}
			});
			jScrollPane.setViewportView(thisEditor);
			// popup menu
			getJPopupMenu();
			
			jPopupMenu.addSeparator();			
			String s;
			int i = 0;
			while ((s = chapters.get("chapter_"+i, "")).length()>0) {
				String str[] = s.split(",");
				if (str.length == 2) {
					JMenuItem j = new JMenuItem();
					j.setText(str[1]);					
					jPopupMenu.add(j);
					helpAnchorListener h = new helpAnchorListener();
					h.setEditor(thisEditor);
					h.setUrl(new URL(helpURL.toExternalForm()+"#"+str[0]));
					j.addActionListener(h);
				}
				i++;
			}
			
			MouseListener popupListener = new PopupListener();
			thisEditor.addMouseListener(popupListener);
		} catch (IOException ex) {ex.printStackTrace();};
	}

	/**
	 * ctor
	 * @throws java.awt.HeadlessException
	 */
	public Help() throws HeadlessException {
		super();
		// TODO Auto-generated constructor stub
		initialize();
		init();
	}

	/**
	 * write a string to the system clipboard.
	 * @param str string to write to the clipboard
	 */
	private static void setClipboard(final String str) {
		StringSelection ss = new StringSelection(str);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
	}

	/**
	 * This method initializes jPopupMenu
	 * @return javax.swing.JPopupMenu
	 */
	private JPopupMenu getJPopupMenu() {
		if (jPopupMenu == null) {
			jPopupMenu = new JPopupMenu();
			jPopupMenu.add(getJPopupMenuItemOpen());
			jPopupMenu.add(getJPopupMenuItemCopy());
		}
		return jPopupMenu;
	}

	/**
	 * This method initializes jPopupMenuItemOpen
	 * @return javax.swing.JMenuItem
	 */
	private JMenuItem getJPopupMenuItemOpen() {
		if (jPopupMenuItemOpen == null) {
			jPopupMenuItemOpen = new JMenuItem();
			jPopupMenuItemOpen.setText("Open in browser");  // Generated
			jPopupMenuItemOpen.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					BufferedWriter fw = null;
					try {
						String s = thisEditor.getText();
						File temp = File.createTempFile("bds2s_help_",".htm");
						fw = new BufferedWriter(new FileWriter(temp));
						fw.write(s);
						fw.close();
						Desktop.getDesktop().browse(temp.toURI());
						temp.deleteOnExit();
					} catch (IOException ex) {		
						ToolBox.showException(ex);
					} finally {
						try {
							if (fw != null)
								fw.close();
						} catch (IOException ex) {
							ToolBox.showException(ex);
						};
					}		
				}

			});
		}
		return jPopupMenuItemOpen;
	}

	
	/**
	 * This method initializes jPopupMenuItemCopy
	 * @return javax.swing.JMenuItem
	 */
	private JMenuItem getJPopupMenuItemCopy() {
		if (jPopupMenuItemCopy == null) {
			jPopupMenuItemCopy = new JMenuItem();
			jPopupMenuItemCopy.setText("Copy");  // Generated
			jPopupMenuItemCopy.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					String s = thisEditor.getSelectedText();
					if (s!=null)
						setClipboard(s);
				}
			});
		}
		return jPopupMenuItemCopy;
	}

	/**
	 * This method initializes this frame
	 */
	private void initialize() {
		this.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(600,600);
		this.setContentPane(getJContentPane());
		this.setTitle(Core.getProgVerName()+" Help");
	}

	/**
	 * This method initializes jContentPane
	 * @return javax.swing.JPanel
	 */
	private javax.swing.JPanel getJContentPane() {
		if(jContentPane == null) {
			jContentPane = new javax.swing.JPanel();
			jContentPane.setLayout(new BoxLayout(jContentPane, BoxLayout.X_AXIS));
			jContentPane.add(getJScrollPane(), null);
		}
		return jContentPane;
	}
	/**
	 * This method initializes jScrollPane
	 * @return javax.swing.JScrollPane
	 */
	private JScrollPane getJScrollPane() {
		if (jScrollPane == null) {
			jScrollPane = new JScrollPane();
		}
		return jScrollPane;
	}

	/**
	 * Listener for the popup menu
	 * @author 0xdeadbeef
	 */
	class PopupListener extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent e) {
			maybeShowPopup(e);
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			maybeShowPopup(e);
		}

		private void maybeShowPopup(MouseEvent e) {
			if (e.isPopupTrigger()) {
				jPopupMenuItemCopy.setEnabled(thisEditor.getSelectionStart()!=thisEditor.getSelectionEnd());
				jPopupMenu.show(thisEditor,e.getX(), e.getY());
			}
		}
	}
}


/**
 * Listener to implement the popup chapter selection
 * @author 0xdeadbeef
 */
class helpAnchorListener implements ActionListener {	
	/** the URL of the chapter */
	private URL url;
	/** reference to the editor */
	private JEditorPane editor;
	
	/**
	 * Set anchor URL for this chapter
	 * @param u URL of this chapter
	 */
	public void setUrl(URL u) {
		url = u;
	}
	
	/**
	 * Set editor pane
	 * @param e editor pane
	 */
	public void setEditor(JEditorPane e) {
		editor = e;
	}
	
	public void actionPerformed(ActionEvent e) {
		try {
			editor.setPage(url); 				
		} catch (IOException ex) {
		}
	}	
}
	
