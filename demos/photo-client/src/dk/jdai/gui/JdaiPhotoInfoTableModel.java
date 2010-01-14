package dk.jdai.gui;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.swing.table.AbstractTableModel;

import dk.jdai.model.EXIFInfo;
import dk.jdai.model.JdaiEXIFInfoPrefs;
import dk.jdai.model.JdaiPhoto;

/**
 * @author jaybe
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class JdaiPhotoInfoTableModel extends AbstractTableModel {

    private static ResourceBundle labels = ResourceBundle.getBundle("dk.jdai.gui.JdaiPhotoInfoTableModelBundle",Locale.getDefault());

	private JdaiPhoto photo;
	private Object[] keys;
	private Map info;
	private String[] header = {labels.getString("TagHeader"), labels.getString("ValueHeader")};

	public JdaiPhotoInfoTableModel() {
		photo = null;
	}

	public void setPhoto(JdaiPhoto photo) {
		this.photo = photo;
		if (photo != null) {
			info = photo.getMetaInfo();
			keys = info.keySet().toArray();
		}
		fireTableStructureChanged();
	}

	/**
	 * @see javax.swing.table.TableModel#getRowCount()
	 */
	public int getRowCount() {
		if (photo != null) {
			return keys.length;
		} else {
			return 0;
		}
	}

	/**
	 * @see javax.swing.table.TableModel#getColumnCount()
	 */
	public int getColumnCount() {
		return header.length;
	}

	/**
	 * @see javax.swing.table.TableModel#getValueAt(int, int)
	 */
	public Object getValueAt(int rowIndex, int columnIndex) {
		Object result = null;
		if (photo != null) {
			if (columnIndex == 0)
				result = keys[rowIndex];
			else
				if (info.containsKey(keys[rowIndex]))
					result = info.get(keys[rowIndex]);
		}
		return result;
	}
	
	public String getColumnName(int column) {
		return header[column];
	}
}
