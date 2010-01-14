// JdaiPhotoZoom.java
// $Id: JdaiPhotoZoom.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import dk.jdai.model.JdaiPhoto;
import dk.jdai.model.JdaiProgressListener;
import dk.jdai.model.JdaiReadException;
import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.nodes.PImage;
import edu.umd.cs.piccolo.nodes.PPath;
import edu.umd.cs.piccolo.nodes.PText;
import edu.umd.cs.piccolox.PFrame;

/**
 * Provides a zoomable pane to display a JDAI photo.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiPhotoZoom implements Runnable {

	private JdaiPhoto[] photos = null;
	private int photoNo = 0;
	private JdaiPhoto photo = null;
	private JdaiPhoto newPhoto = null;
	private boolean newPhotoSet = false;
	private boolean interrupted = false;
	private Timer timer = null;
	private JdaiProgressListener progress = null;

	private PFrame pFrame;
	private PCanvas pCanvas;
	private PImage pImage;
	private PPath pPath;
	private PText pText;

	private boolean noFullscreen = false;

	private static final int SLIDESHOW_DELAY = 5000;

	private static ResourceBundle labels =
		ResourceBundle.getBundle(
			"dk.jdai.gui.JdaiPhotoZoomBundle",
			Locale.getDefault());

	/**
	 * Creates a new instance of JdaiPhotoZoom.
	 */
	public JdaiPhotoZoom() {
		if (System
			.getProperties()
			.getProperty("os.name")
			.startsWith("Windows")) {
			noFullscreen = true;
		}
	}

	private void initialize() {
		pCanvas = new PCanvas();
		pFrame = new PFrame(false, pCanvas);
		pFrame.setSize(800, 600);

		// Added to frame and canvas to make toggle work...
		pFrame.addKeyListener(new MyKeyListener());
		pFrame.getCanvas().addKeyListener(new MyKeyListener());

		pFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		pFrame.setTitle(labels.getString("WindowTitle"));
		pImage = new PImage();
		pImage.setAccelerated(false);

		// Caption node
		pPath = new PPath() {
			public void layoutChildren() {
				this.setBounds(this.getChild(0).getBounds());
			}
		};
		pPath.setPathToRectangle(0, 0, 1, 1);
		pPath.setPaint(new Color(128, 164, 196));
		pPath.setTransparency(0.75f);
		pPath.setOffset(5, 5);
		pPath.addChild(pText = new PText());
		pPath.setVisible(false);
		pText.setFont(new Font("Helvetica", Font.ITALIC, 16));

		// Hierarchy
		pCanvas.getLayer().addChild(pImage);
		pCanvas.getCamera().addChild(pPath);
	}

	/**
	 * Creates a new instance of JdaiPhotoZoom with a
	 * progress listener.
	 * @param progress The progress listener to receive image read progress
	 */
	public JdaiPhotoZoom(JdaiProgressListener progress) {
		this();
		this.progress = progress;
	}

	class MyKeyListener extends KeyAdapter {
		public void keyPressed(KeyEvent e) {
			int key = e.getKeyCode();
			int modifiers = e.getModifiers();
			//System.out.println("KeyEvent: " + e.paramString());
			if (modifiers == 0) {
				// No modifiers
				switch (key) {
					case KeyEvent.VK_HOME :
						doFirstPhoto();
						break;
					case KeyEvent.VK_END :
						doLastPhoto();
						break;
					case KeyEvent.VK_PAGE_UP :
						doPrevPhoto();
						break;
					case KeyEvent.VK_PAGE_DOWN :
					case KeyEvent.VK_SPACE :
						doNextPhoto();
						break;
					case KeyEvent.VK_ESCAPE :
						doCloseWindow();
						break;
					case KeyEvent.VK_F9 :
						doToggleFullscreen();
						break;
					case KeyEvent.VK_UP :
						pCanvas.getCamera().translateView(0, -200);
						break;
					case KeyEvent.VK_DOWN :
						pCanvas.getCamera().translateView(0, 200);
						break;
					case KeyEvent.VK_LEFT :
						pCanvas.getCamera().translateView(-200, 0);
						break;
					case KeyEvent.VK_RIGHT :
						pCanvas.getCamera().translateView(200, 0);
						break;
					case KeyEvent.VK_PLUS :
					case KeyEvent.VK_ADD :
						pCanvas.getCamera().scaleViewAboutPoint(
							1.25,
							pCanvas.getCamera().getViewBounds().getCenterX(),
							pCanvas.getCamera().getViewBounds().getCenterY());
						break;
					case KeyEvent.VK_MINUS :
					case KeyEvent.VK_SUBTRACT :
						pCanvas.getCamera().scaleViewAboutPoint(
							0.8,
							pCanvas.getCamera().getViewBounds().getCenterX(),
							pCanvas.getCamera().getViewBounds().getCenterY());
						break;
				}
			} else if (modifiers == KeyEvent.SHIFT_MASK) {
				// Shift modifier
				switch (key) {
					case KeyEvent.VK_UP :
						pCanvas.getCamera().translateView(0, -20);
						break;
					case KeyEvent.VK_DOWN :
						pCanvas.getCamera().translateView(0, 20);
						break;
					case KeyEvent.VK_LEFT :
						pCanvas.getCamera().translateView(-20, 0);
						break;
					case KeyEvent.VK_RIGHT :
						pCanvas.getCamera().translateView(20, 0);
						break;
					case KeyEvent.VK_PLUS :
					case KeyEvent.VK_ADD :
						pCanvas.getCamera().scaleViewAboutPoint(
							1.02,
							pCanvas.getCamera().getViewBounds().getCenterX(),
							pCanvas.getCamera().getViewBounds().getCenterY());
						break;
					case KeyEvent.VK_MINUS :
					case KeyEvent.VK_SUBTRACT :
						pCanvas.getCamera().scaleViewAboutPoint(
							0.98,
							pCanvas.getCamera().getViewBounds().getCenterX(),
							pCanvas.getCamera().getViewBounds().getCenterY());
						break;
				}
			}
		}
	}

	class SlideShowActionListener implements ActionListener {

		/**
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		public void actionPerformed(ActionEvent e) {
			if (!isVisible() || photoNo >= photos.length - 1) {
				doCloseWindow();
			} else {
				doNextPhoto();
			}
		}
	}

	/**
	 * Returns the presentation of the JdaiPhotoZoom for use in composite GUIs.
	 * @return The Component.
	 */
	public Component getPresentation() {
		return pCanvas;
	}

	/**
	 * Set the JDAI photo to display.
	 * @param photos The photo.array
	 * @param index The index of the photo to display first
	 */
	public void setPhotoArray(JdaiPhoto[] photos, int index) {
		if (pFrame == null)
			initialize();
		if (photos == null) {
			pFrame.setVisible(false);
		} else {
			pImage.setVisible(false); // to hide previous photo
			pFrame.setVisible(true);
			this.photos = photos;
			photoNo = index;
			setNewPhoto(photos[photoNo]);
		}
	}

	private synchronized void setNewPhoto(JdaiPhoto p) {
		setCaption(labels.getString("Reading"));
		if (this.photo != null) {
			interrupted = true;
			photo.abortRead();
		}
		newPhoto = p;
		newPhotoSet = true;
		notifyAll();
	}

	private synchronized JdaiPhoto waitForPhoto() {
		while (newPhotoSet == false) {
			try {
				wait();
			} catch (InterruptedException e) {
				/* wake up */
			}
		}
		JdaiPhoto result = newPhoto;
		newPhoto = null;
		newPhotoSet = false;
		interrupted = false;
		return result;
	}

	private class Job implements Runnable {
		private Image i;
		private String c;
		private JdaiPhotoZoom jpz;

		public Job(Image i, String c, JdaiPhotoZoom jpz) {
			this.i = i;
			this.c = c;
			this.jpz = jpz;
		}

		public void run() {
			jpz.setImage(i);
			jpz.setCaption(c);
		}
	}

	public void run() {
		while (true) {
			photo = waitForPhoto();
			if (progress != null && photo != null)
				photo.setProgressListener(progress);
			Image i = null;
			String caption = "";
			if (photo != null) {
				try {
					caption =
						photo.getSection().getInfoStore().getCaption(photo);
					i = photo.getImage();
				} catch (JdaiReadException e) {
					/* image may be null, caption may be empty */
				}
			}
			if (!interrupted) {
				SwingUtilities.invokeLater(new Job(i, caption, this));
			}
			i = null;
		}
	}

	private void setImage(Image i) {
		if (i != null) {
			pImage.setImage(i);
			pImage.setVisible(true);
		} else {
			pImage.setVisible(false);
		}
		pCanvas.getCamera().animateViewToBounds(
			pImage.getGlobalFullBounds(),
			true,
			0);
	}

	private void setCaption(String c) {
		if (c != null && c.length() > 1) {
			pText.setText(" " + c + " ");
			pPath.setVisible(true);
		} else {
			pPath.setVisible(false);
		}
	}

	private void doFirstPhoto() {
		if (photos != null) {
			setNewPhoto(photos[photoNo = 0]);
		}
	}

	private void doLastPhoto() {
		if (photos != null) {
			setNewPhoto(photos[photoNo = photos.length - 1]);
		}
	}

	private void doPrevPhoto() {
		if (photos != null && photoNo > 0) {
			setNewPhoto(photos[--photoNo]);
		}
	}

	private void doNextPhoto() {
		if (photos != null && photoNo < photos.length - 1) {
			setNewPhoto(photos[++photoNo]);
		}
	}

	private void setFullscreen(boolean fullscreen) {
		if (noFullscreen) {
			int curState = pFrame.getExtendedState();
			if (fullscreen)
				pFrame.setExtendedState(PFrame.MAXIMIZED_BOTH);
			else
				pFrame.setExtendedState(PFrame.NORMAL);
		} else {
			pFrame.setFullScreenMode(fullscreen);
			pFrame.removeEscapeFullScreenModeListener();
		}
		pCanvas.getCamera().animateViewToBounds(
			pImage.getGlobalFullBounds(),
			true,
			0);
	}

	private void doToggleFullscreen() {
		setFullscreen(!isFullscreen());
	}

	public boolean isFullscreen() {
		boolean isFullscreen;
		if (noFullscreen) {
			isFullscreen = (pFrame.getExtendedState() == PFrame.MAXIMIZED_BOTH);
		} else {
			isFullscreen =
				GraphicsEnvironment
					.getLocalGraphicsEnvironment()
					.getDefaultScreenDevice()
					.getFullScreenWindow()
					== pFrame;
		}
		return isFullscreen;
	}

	public boolean isVisible() {
		return pFrame.isVisible();
	}

	public void startSlideshow() {
		doSlideShow();
	}

	private void doSlideShow() {
		setFullscreen(true);
		pFrame.setVisible(true);
		if (timer != null && timer.isRunning()) {
			timer.stop();
		}
		timer = new Timer(SLIDESHOW_DELAY, new SlideShowActionListener());
		timer.start();
	}

	private void doCloseWindow() {
		if (timer != null && timer.isRunning()) {
			timer.stop();
		}
		pFrame.setVisible(false);
	}
}
