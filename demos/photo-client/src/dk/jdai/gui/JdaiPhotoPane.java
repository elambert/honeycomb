// JdaiPhotoPane.java
// $Id: JdaiPhotoPane.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import dk.jdai.model.*;

import java.awt.*;
import javax.swing.*;

/**
 * Provides a pane to display a JDAI photo.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiPhotoPane implements Runnable {

	private JdaiPhoto photo;
	private JdaiPhoto newPhoto;
	private boolean interrupted;
	private JdaiProgressListener progress;

	private JScrollPane pane;
	private JLabel label;

	/**
	 * Creates a new instance of JdaiPhotoPane.
	 */
	public JdaiPhotoPane() {
		photo = null;
		newPhoto = null;
		interrupted = false;
		progress = null;
		label = new JLabel();
		label.setHorizontalAlignment(JLabel.CENTER);
		pane = new JScrollPane(label);
	}

	/**
	 * Creates a new instance of JdaiPhotoPane with a
	 * progress listener.
	 * @param progress The progress listener to receive image read progress
	 */
	public JdaiPhotoPane(JdaiProgressListener progress) {
		this();
		this.progress = progress;
	}

	/**
	 * Returns the presentation of the JdaiPhotoPane for use in composite GUIs.
	 * @return The Component.
	 */
	public Component getPresentation() {
		return pane;
	}

	/**
	 * Set the JDAI photo to display.
	 * @param photos The photo.
	 */
	public void setPhoto(JdaiPhoto photo) {
		String text = null;
		if (photo != null) {
			label.setText("");
			try {
				text = photo.getSection().getInfoStore().getCaption(photo);
			} catch (JdaiReadException e) {
				JdaiGuiHelpers.reportException("Unable to read caption", e);
			}
			if (text == null || text.equals(""))
				text = "No caption set";
		} else {
			label.setIcon(null);
		}
		label.setToolTipText(text);
		setNewPhoto(photo);
	}

	private synchronized void setNewPhoto(JdaiPhoto p) {
		if (this.photo != null) {
			interrupted = true;
			photo.abortRead();
		}
		newPhoto = p;
		notifyAll();
	}

	private synchronized JdaiPhoto waitForPhoto() {
		while (newPhoto == null) {
			try {
				wait();
			} catch (InterruptedException e) {
				/* wake up */
			}
		}
		JdaiPhoto result = newPhoto;
		newPhoto = null;
		interrupted = false;
		return result;
	}

	private class Job implements Runnable {
		private Icon i;
		private JLabel l;

		public Job(Icon i, JLabel l) {
			this.i = i;
			this.l = l;
		}

		public void run() {
			l.setIcon(i);
		}
	}

	public void run() {
		while (true) {
			photo = waitForPhoto();
			if (progress != null)
				photo.setProgressListener(progress);
			Icon i;
			try {
				i = new ImageIcon(photo.getImage(640, 640));
			} catch (JdaiReadException e) {
				i = null;
			}
			if (!interrupted)
				SwingUtilities.invokeLater(new Job(i, label));
		}
	}

}
