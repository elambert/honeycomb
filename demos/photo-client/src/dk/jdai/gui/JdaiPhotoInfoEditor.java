// JdaiPhotoInfoEditor.java
// $Id: JdaiPhotoInfoEditor.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import dk.jdai.model.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.ResourceBundle;
import java.util.Locale;

/**
 * Provides an editor for photo information.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiPhotoInfoEditor implements ActionListener {

	/**
	 * The interface to implement if you need events from the JdaiPhotoList control.
	 */
	public interface Delegate {
		/**
		 * The method called when the selection is updated.
		 * @param photo The JdaiPhoto updated.
		 */
		public void selectionUpdated(JdaiPhoto photo);
	}
	
	class MyWindowListener extends WindowAdapter {
		public void windowClosing(WindowEvent e) {
			doWriteChanges();
			frame.setVisible(false);
		}
	}

	private static ResourceBundle labels =
		ResourceBundle.getBundle(
			"dk.jdai.gui.JdaiPhotoInfoEditorBundle",
			Locale.getDefault());

	private JdaiPhoto[] photos = null;
	private int photoNo = 0;

	private JFrame frame;
	private JPanel pane;
	private JLabel thumbLabel;

	private JRadioButton rotate0Button;
	private JRadioButton rotate90Button;
	private JRadioButton rotate180Button;
	private JRadioButton rotate270Button;
	private JdaiActions.Rotate0Action rotate0Action;
	private JdaiActions.Rotate90Action rotate90Action;
	private JdaiActions.Rotate180Action rotate180Action;
	private JdaiActions.Rotate270Action rotate270Action;

	private JLabel captionLabel;
	private JTextArea captionTextArea;
	private JLabel keywordsLabel;
	private JTextField keywordsTextField;

	private JLabel sectionCaptionLabel;
	private JTextArea sectionCaptionTextArea;
	private JLabel sectionKeywordsLabel;
	private JTextField sectionKeywordsTextField;

	private JButton prevButton;
	private JButton nextButton;

	private Delegate delegate = null;

	/**
	 * Creates a new instance of JdaiPhotoInfoEditor
	 */
	public JdaiPhotoInfoEditor() {
	}

	/**
	 * Creates a new instance of JdaiPhotoInfoEditor with a delegate.
	 * @param delegate The delegate object.
	 */
	public JdaiPhotoInfoEditor(Delegate delegate) {
		this();
		this.delegate = delegate;
	}

	private void initialize() {
		JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panel1.add(thumbLabel = new JLabel(labels.getString("Thumbnail")));
		thumbLabel.setPreferredSize(new Dimension(160, 160));
		thumbLabel.setHorizontalAlignment(JLabel.CENTER);
		thumbLabel.setBorder(BorderFactory.createEtchedBorder());

		JPanel panel1b = new JPanel(new GridLayout(4, 1));
		panel1.add(panel1b);
		initActions();
		panel1b.add(rotate0Button = new JRadioButton(rotate0Action));
		panel1b.add(rotate90Button = new JRadioButton(rotate90Action));
		panel1b.add(rotate180Button = new JRadioButton(rotate180Action));
		panel1b.add(rotate270Button = new JRadioButton(rotate270Action));
		rotate0Button.setHorizontalAlignment(JRadioButton.LEFT);
		rotate90Button.setHorizontalAlignment(JRadioButton.LEFT);
		rotate180Button.setHorizontalAlignment(JRadioButton.LEFT);
		rotate270Button.setHorizontalAlignment(JRadioButton.LEFT);

		JPanel panel2 = new JPanel(new BorderLayout());
		panel2.add(
			captionLabel = new JLabel(labels.getString("Caption")),
			BorderLayout.NORTH);
		panel2.add(captionTextArea = new JTextArea(), BorderLayout.CENTER);
		captionLabel.setLabelFor(captionTextArea);
		captionLabel.setDisplayedMnemonic(
			((Character) labels.getObject("CaptionMnemonic")).charValue());
		captionTextArea.setLineWrap(true);
		captionTextArea.setWrapStyleWord(true);
		captionTextArea.setBorder(BorderFactory.createLoweredBevelBorder());

		JPanel panel3 = new JPanel(new BorderLayout());
		panel3.add(
			keywordsLabel = new JLabel(labels.getString("Keywords")),
			BorderLayout.NORTH);
		panel3.add(keywordsTextField = new JTextField(), BorderLayout.CENTER);
		keywordsLabel.setLabelFor(keywordsTextField);
		keywordsLabel.setDisplayedMnemonic(
			((Character) labels.getObject("KeywordsMnemonic")).charValue());

		JPanel panel4 = new JPanel(new BorderLayout());
		panel4.add(
			sectionCaptionLabel =
				new JLabel(labels.getString("SectionCaption")),
			BorderLayout.NORTH);
		panel4.add(
			sectionCaptionTextArea = new JTextArea(),
			BorderLayout.CENTER);
		sectionCaptionLabel.setLabelFor(sectionCaptionTextArea);
		sectionCaptionLabel.setDisplayedMnemonic(
			((Character) labels.getObject("SectionCaptionMnemonic"))
				.charValue());
		sectionCaptionTextArea.setLineWrap(true);
		sectionCaptionTextArea.setWrapStyleWord(true);
		sectionCaptionTextArea.setBorder(
			BorderFactory.createLoweredBevelBorder());

		JPanel panel5 = new JPanel(new BorderLayout());
		panel5.add(
			sectionKeywordsLabel =
				new JLabel(labels.getString("SectionKeywords")),
			BorderLayout.NORTH);
		panel5.add(
			sectionKeywordsTextField = new JTextField(),
			BorderLayout.CENTER);
		sectionKeywordsLabel.setLabelFor(sectionKeywordsTextField);
		sectionKeywordsLabel.setDisplayedMnemonic(
			((Character) labels.getObject("SectionKeywordsMnemonic"))
				.charValue());

		JPanel photoPanel = new JPanel(new BorderLayout());
		photoPanel.add(panel2, BorderLayout.CENTER);
		photoPanel.add(panel3, BorderLayout.SOUTH);
		photoPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

		JPanel sectionPanel = new JPanel(new BorderLayout());
		sectionPanel.add(panel4, BorderLayout.CENTER);
		sectionPanel.add(panel5, BorderLayout.SOUTH);

		JPanel entryPanel = new JPanel(new GridLayout(2, 1));
		entryPanel.add(photoPanel);
		entryPanel.add(sectionPanel);

		JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		navPanel.add(
			prevButton = new JButton(JdaiActions.instance().prevPhotoAction()));
		navPanel.add(
			nextButton = new JButton(JdaiActions.instance().nextPhotoAction()));

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(panel1, BorderLayout.NORTH);
		topPanel.add(entryPanel, BorderLayout.CENTER);
		topPanel.add(navPanel, BorderLayout.SOUTH);
		topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		updateGuiState();

		pane = topPanel;
		frame = new JFrame(labels.getString("WindowTitle"));
		frame.setContentPane(pane);
		frame.setSize(550, 550);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new MyWindowListener());
	}

	/**
	 * Returns the presentation of the JdaiPhotoInfoEditor for use in composite GUIs.
	 * @return The Component.
	 */
	public Component getPresentation() {
		return pane;
	}

	/**
	 * Set the JDAI photo to use in the editor.
	 * @param photo The photo.
	 */
	public void setPhotoArray(JdaiPhoto[] photos, int photoNo) {
		if (frame == null)
			initialize();
		if (this.photos != null)
			doWriteChanges();

		JdaiSection oldSec = null;
		if (this.photos != null)
			oldSec = this.photos[this.photoNo].getSection();

		this.photos = photos;
		this.photoNo = photoNo;

		if (photos == null) {
			frame.setVisible(false);
		} else {
			frame.setVisible(true);
			JdaiPhoto photo = photos[photoNo];
			JdaiSection sec = photo.getSection();
			if (photo != null) {
				Icon icon = null;
				boolean succes = true;
				try {
					icon = new ImageIcon(photo.getThumbnail());
					thumbLabel.setIcon(icon);
					thumbLabel.setText(null);
				} catch (JdaiReadException e) {
					thumbLabel.setIcon(null);
					thumbLabel.setText(labels.getString("NoThumbnail"));
				}
				String caption = null;
				String keywords = null;
				try {
					caption = sec.getInfoStore().getCaption(photo);
					keywords = sec.getInfoStore().getKeywords(photo);
				} catch (JdaiReadException e) {
					JdaiGuiHelpers.reportException(
						labels.getString("UnableRead"),
						e);
				}
				captionTextArea.setText(caption);
				keywordsTextField.setText(keywords);
				if (!sec.equals(oldSec)) {
					String sectionCaption = null;
					String sectionKeywords = null;
					try {
						sectionCaption = sec.getInfoStore().getCaption(sec);
						sectionKeywords = sec.getInfoStore().getKeywords(sec);
					} catch (JdaiReadException e) {
						JdaiGuiHelpers.reportException(
							labels.getString("UnableRead"),
							e);
					}
					sectionCaptionTextArea.setText(sectionCaption);
					sectionKeywordsTextField.setText(sectionKeywords);
				}
			} else {
				thumbLabel.setIcon(null);
				thumbLabel.setText(labels.getString("NoThumbnail"));
				captionTextArea.setText(null);
				keywordsTextField.setText(null);
				sectionCaptionTextArea.setText(null);
				sectionKeywordsTextField.setText(null);
			}
			updateActionStates();
			updateRotationState();
			updateGuiState();
		}
	}

	private void doWriteChanges() {
		/* Update photo and section info if text is changed */
		JdaiPhoto p = photos[photoNo];
		JdaiSection s = p.getSection();
		try {
			JdaiPhotoInfoStore is = s.getInfoStore();
			if (!is.getCaption(p).equals(captionTextArea.getText()))
				is.setCaption(p, captionTextArea.getText());
			if (!is.getKeywords(p).equals(keywordsTextField.getText()))
				is.setKeywords(p, keywordsTextField.getText());
			if (s != null) {
				if (!is.getCaption(s).equals(sectionCaptionTextArea.getText()))
					is.setCaption(s, sectionCaptionTextArea.getText());
				if (!is
					.getKeywords(s)
					.equals(sectionKeywordsTextField.getText()))
					is.setKeywords(s, sectionKeywordsTextField.getText());
			}
		} catch (JdaiReadException e) {
			JdaiGuiHelpers.reportException(labels.getString("UnableRead"), e);
		} catch (JdaiWriteException e) {
			JdaiGuiHelpers.reportException(labels.getString("UnableWrite"), e);
		}
	}

	/**
	 * This method is the action handler.
	 * @param evt The action event.
	 */
	public void actionPerformed(ActionEvent evt) {
		String command = evt.getActionCommand();
		if (photos != null) {
			JdaiPhoto photo = photos[photoNo];
			JdaiSection sec = photo.getSection();
			boolean updated = false;
			try {
				if (command.equals(rotate0Action.getActionCommand())) {
					sec.getInfoStore().setRotation(
						photo,
						JdaiPhotoInfoStore.NORTH);
					updated = true;
				} else if (command.equals(rotate90Action.getActionCommand())) {
					sec.getInfoStore().setRotation(
						photo,
						JdaiPhotoInfoStore.EAST);
					updated = true;
				} else if (
					command.equals(rotate180Action.getActionCommand())) {
					sec.getInfoStore().setRotation(
						photo,
						JdaiPhotoInfoStore.SOUTH);
					updated = true;
				} else if (
					command.equals(rotate270Action.getActionCommand())) {
					sec.getInfoStore().setRotation(
						photo,
						JdaiPhotoInfoStore.WEST);
					updated = true;
				} else if (
					command.equals(
						JdaiActions
							.instance()
							.prevPhotoAction()
							.getActionCommand())) {
					if (photoNo > 0) {
						setPhotoArray(photos, photoNo - 1);
					}
				} else if (
					command.equals(
						JdaiActions
							.instance()
							.nextPhotoAction()
							.getActionCommand())) {
					if (photoNo < photos.length - 1) {
						setPhotoArray(photos, photoNo + 1);
					}
				}
			} catch (JdaiReadException e) {
				JdaiGuiHelpers.reportException(
					labels.getString("UnableRead"),
					e);
			} catch (JdaiWriteException e) {
				JdaiGuiHelpers.reportException(
					labels.getString("UnableWrite"),
					e);
			}
			if (updated) {
				photo.refreshThumbnail();
				if (delegate != null)
					delegate.selectionUpdated(photo);
			}
			setPhotoArray(photos, photoNo);
		}
	}

	private void initActions() {
		rotate0Action = JdaiActions.instance().rotate0Action();
		registerAction(rotate0Action);
		rotate90Action = JdaiActions.instance().rotate90Action();
		registerAction(rotate90Action);
		rotate180Action = JdaiActions.instance().rotate180Action();
		registerAction(rotate180Action);
		rotate270Action = JdaiActions.instance().rotate270Action();
		registerAction(rotate270Action);

		registerAction(JdaiActions.instance().prevPhotoAction());
		registerAction(JdaiActions.instance().nextPhotoAction());

		updateActionStates();
	}

	private void registerAction(JdaiActions.JdaiAbstractAction action) {
		action.addActionListener(this);
	}

	private void updateActionStates() {
		boolean enabled = true;
		if (photos == null)
			enabled = false;
		rotate0Action.setEnabled(enabled);
		rotate90Action.setEnabled(enabled);
		rotate180Action.setEnabled(enabled);
		rotate270Action.setEnabled(enabled);

		if (photos == null || photoNo == 0)
			JdaiActions.instance().prevPhotoAction().setEnabled(false);
		else
			JdaiActions.instance().prevPhotoAction().setEnabled(true);
		if (photos == null || photoNo >= photos.length - 1)
			JdaiActions.instance().nextPhotoAction().setEnabled(false);
		else
			JdaiActions.instance().nextPhotoAction().setEnabled(true);
	}

	private void updateRotationState() {
		int rotation = -1;
		if (photos != null) {
			JdaiPhoto photo = photos[photoNo];
			try {
				rotation = photo.getSection().getInfoStore().getRotation(photo);
			} catch (JdaiReadException e) {
				JdaiGuiHelpers.reportException(
					labels.getString("UnableRead"),
					e);
			}
			switch (rotation) {
				case JdaiPhotoInfoStore.NORTH :
					rotate0Button.setSelected(true);
					rotate90Button.setSelected(false);
					rotate180Button.setSelected(false);
					rotate270Button.setSelected(false);
					break;
				case JdaiPhotoInfoStore.EAST :
					rotate0Button.setSelected(false);
					rotate90Button.setSelected(true);
					rotate180Button.setSelected(false);
					rotate270Button.setSelected(false);
					break;
				case JdaiPhotoInfoStore.SOUTH :
					rotate0Button.setSelected(false);
					rotate90Button.setSelected(false);
					rotate180Button.setSelected(true);
					rotate270Button.setSelected(false);
					break;
				case JdaiPhotoInfoStore.WEST :
					rotate0Button.setSelected(false);
					rotate90Button.setSelected(false);
					rotate180Button.setSelected(false);
					rotate270Button.setSelected(true);
					break;
				default :
					/* don't know rotation */
					rotate0Button.setSelected(false);
					rotate90Button.setSelected(false);
					rotate180Button.setSelected(false);
					rotate270Button.setSelected(false);
					break;
			}
		}
	}

	private void updateGuiState() {
		boolean enabled = true;
		if (photos == null)
			enabled = false;
		thumbLabel.setEnabled(enabled);
		captionLabel.setEnabled(enabled);
		captionTextArea.setEnabled(enabled);
		keywordsLabel.setEnabled(enabled);
		keywordsTextField.setEnabled(enabled);
		sectionCaptionLabel.setEnabled(enabled);
		sectionCaptionTextArea.setEnabled(enabled);
		sectionKeywordsLabel.setEnabled(enabled);
		sectionKeywordsTextField.setEnabled(enabled);
	}
}
