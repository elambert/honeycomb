// JdaiPhotoListCellRenderer.java
// $Id: JdaiPhotoListCellRenderer.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import dk.jdai.model.*;

import java.awt.*;
import javax.swing.*;

/**
 * Provides a list cell renderer for JDAI photos. This class is undocumented
 * since the methods are well known from the ListCellRenderer interface.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiPhotoListCellRenderer
	extends JLabel
	implements ListCellRenderer {

	public Component getListCellRendererComponent(
		JList list,
		Object obj,
		int index,
		boolean isSelected,
		boolean hasFocus) {

		if (obj instanceof JdaiPhoto) {
			setHorizontalAlignment(CENTER);
			setVerticalAlignment(CENTER);
			setHorizontalTextPosition(CENTER);
			setVerticalTextPosition(BOTTOM);
			setFont(getFont().deriveFont(Font.PLAIN));
			setOpaque(true);
			setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));

			JdaiPhoto photo = (JdaiPhoto) obj;
			setText(photo.getId());
			Icon icon = null;
			boolean succes = true;
			try {
				icon = new ImageIcon(photo.getThumbnail());
				String caption =
					photo.getSection().getInfoStore().getCaption(photo);
				setToolTipText(caption.equals("") ? null : caption);
			} catch (JdaiReadException e) {
				// image will be missing
				setIcon(null);
				setText("No thumbnail");
				succes = false;
			}
			if (succes) {
				setIcon(icon);
			}
			if (isSelected) {
				setBackground(new Color(153, 204, 255));
			} else {
				setBackground(Color.WHITE);
			}
			return this;
		} else {
			return (Component) obj;
		}
	}
}
