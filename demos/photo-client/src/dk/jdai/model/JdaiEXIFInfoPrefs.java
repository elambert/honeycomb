package dk.jdai.model;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import dk.jdai.gui.JdaiOptions;

public class JdaiEXIFInfoPrefs implements JdaiOptions {

	private static JdaiEXIFInfoPrefs theInstance;
	private static ResourceBundle labels =
		ResourceBundle.getBundle(
			"dk.jdai.model.JdaiEXIFInfoBundle",
			Locale.getDefault());

	private static String[] fieldList =
		{
			"Id",
			"DateTime",
			"ExifImageWidth",
			"ExifImageHeight",
			"ISOSpeedRatings",
			"LightSource",
			"FNumber",
			"ExposureTime",
			"FocalLength",
			"ExposureProgram",
			"MeteringMode",
			"Flash",
			"Make",
			"Model",
			"Software",
			"MN_ISOSetting",
			"MN_ColorMode",
			"MN_Quality",
			"MN_WhiteBalance",
			"MN_ImageSharpening",
			"MN_FocusMode",
			"MN_FlashSetting",
			"MN_ISOSelection",
			"MN_ImageAdjustment",
			"MN_Adapter",
			"MN_ManualFocusDistance",
			"MN_DigitalZoom",
			"MN_AFFocusPosition" };

	private String[] savedFields;
	private FieldListModel selectedFields = new FieldListModel();
	private FieldListModel nonSelectedFields = new FieldListModel();
	private JList nonSelectedList;
	private JList selectedList;
	private Preferences prefs;
    private final String PREF_COUNT  = "exif_info_count";
    private final String PREF_PREFIX  = "exif_info";
	
	private JdaiEXIFInfoPrefs() {
		int prefCount;
		prefs = Preferences.userNodeForPackage(getClass());
		prefCount = prefs.getInt(PREF_COUNT,-1);
		if (prefCount == -1) {
			for (int i = 0; i < fieldList.length; i++) {
				selectedFields.addKey(fieldList[i]);
			}
		} else {
			String[] prefFields = new String[prefCount];
			for (int i = 0;i<prefCount;i++) {
				String field = prefs.get(PREF_PREFIX+i,"");
				prefFields[i] = field;	
			}
			setFields(prefFields);
		}
		savedFields = selectedFields.getKeyArray();
	}
	
	private void setFields(String[] fields) {
		selectedFields.clear();
		nonSelectedFields.clear();
		for (int i=0;i<fields.length;i++) {
			selectedFields.addKey(fields[i]);
		}
		for (int i = 0;i<fieldList.length;i++) {
			if (!selectedFields.containsKey(fieldList[i])) {
				nonSelectedFields.addKey(fieldList[i]);
			}	
		}
		
	}

	public static JdaiEXIFInfoPrefs getInstance() {
		if (theInstance == null) {
			theInstance = new JdaiEXIFInfoPrefs();
		}
		return theInstance;
	}

	private class FieldListModel extends AbstractListModel {
		private List keys = new LinkedList();
		
		/**
		 * @see javax.swing.ListModel#getSize()
		 */
		public int getSize() {
			return keys.size();
		}
		/**
		 * @see javax.swing.ListModel#getElementAt(int)
		 */
		public Object getElementAt(int pos) {
			String tmp = (String)keys.get(pos);
			if (tmp.length()>3 && tmp.substring(0,3).equals("MN_")) {
				return labels.getString((String)keys.get(pos))+" (Nikon)";				
			} else {
				return labels.getString((String)keys.get(pos));
			}
		}
		
		public String getKeyAt(int pos) {
			return (String)keys.get(pos);
		}
		
		public String[] getKeyArray() {
			return (String[]) keys.toArray(new String[0]);
		}

		public void addKey(String key) {
			keys.add(key);
			fireIntervalAdded(this,keys.size()-1,keys.size()-1);
		}
		
		public void removeKey(int pos) {
			keys.remove(pos);
			fireIntervalRemoved(this,0,keys.size()-1);
		}
		
		public void clear() {
			keys.clear();
		}
		
		public boolean containsKey(String key) {
			return keys.contains(key);	
		}
		
		public void setKeyAt(Object key,int pos) {
			keys.set(pos,key);
			fireContentsChanged(this,keys.size()-1,keys.size()-1);
		}
	}

	private class AddRemove extends AbstractAction {
		static final int ADD = 1;
		static final int REMOVE = 2;
		int type;
		public AddRemove(int type) {
			this.type = type;
		}
		public void actionPerformed(ActionEvent e) {
			if (type == ADD) {
				move(nonSelectedList, selectedList);
			} else {
				move(selectedList, nonSelectedList);
			}
		}
		private void move(JList from, JList to) {
			int[] selection = from.getSelectedIndices();
			FieldListModel toModel = (FieldListModel) to.getModel();
			FieldListModel fromModel = (FieldListModel) from.getModel();

			for (int i = 0; i < selection.length; i++) {
				toModel.addKey(fromModel.getKeyAt(selection[i]));
			}
			for (int i = selection.length-1; i >= 0; i--) {
				fromModel.removeKey(selection[i]);
			}
		}
	}

	private class Move extends AbstractAction {
		static final int UP = 1;
		static final int DOWN = 2;
		int type;
		public Move(int type) {
			this.type = type;
		}
		public void actionPerformed(ActionEvent e) {
			int index = selectedList.getSelectedIndex();
			if (index != -1) {
				if (type == UP && index != 0) {
					swap(index, index - 1);
				} else if (
					type == DOWN
						&& index != selectedList.getModel().getSize() - 1) {
					swap(index, index + 1);
				}
			}
		}
		private void swap(int a, int b) {
			String aValue = (String) selectedFields.getKeyAt(a);
			String bValue = (String) selectedFields.getKeyAt(b);
			selectedFields.setKeyAt(bValue, a);
			selectedFields.setKeyAt(aValue, b);
			selectedList.setSelectedIndex(b);
		}
	}
	
	/**
	 * Returns the preferences component
	 *
	 * @return The component to set preferences
	 */
	public Component getComponent() {
		nonSelectedList = new JList(nonSelectedFields);
		JScrollPane nonSelectedScroll = new JScrollPane(nonSelectedList);
		JPanel nonSelPanel = new JPanel(new BorderLayout());
		selectedList = new JList(selectedFields);
		JScrollPane selectedScroll = new JScrollPane(selectedList);
		JPanel selPanel = new JPanel(new BorderLayout());
		JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
		JPanel buttonPanel = new JPanel(new GridLayout(2, 1));
		JPanel buttonPanel2 = new JPanel(new GridLayout(2, 1));
		JButton addButton = new JButton(">");
		JButton removeButton = new JButton("<");
		JButton upButton = new JButton(labels.getString("Up"));
		JButton downButton = new JButton(labels.getString("Down"));
		addButton.addActionListener(new AddRemove(AddRemove.ADD));
		removeButton.addActionListener(new AddRemove(AddRemove.REMOVE));
		upButton.addActionListener(new Move(Move.UP));
		downButton.addActionListener(new Move(Move.DOWN));
		
		nonSelPanel.add(new JLabel(labels.getString("NotSelected")),BorderLayout.NORTH);
		nonSelPanel.add(nonSelectedScroll,BorderLayout.CENTER);
		selPanel.add(new JLabel(labels.getString("Selected")),BorderLayout.NORTH);
		selPanel.add(selectedScroll,BorderLayout.CENTER);
		buttonPanel.add(addButton);
		buttonPanel.add(removeButton);
		buttonPanel2.add(upButton);
		buttonPanel2.add(downButton);
		c.insets = new Insets(10,10,10,5);
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 1;
		c.insets = new Insets(5,5,5,5);
		panel.add(nonSelPanel,c);
		c.weightx = 0;
		c.weighty = 0;
		c.fill = GridBagConstraints.NONE;
		panel.add(buttonPanel,c);
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		panel.add(selPanel,c);
		c.weightx = 0;
		c.weighty = 0;
		c.fill = GridBagConstraints.NONE;
		panel.add(buttonPanel2,c);

		return panel;
	}

	/**
	 * Returns name og preferences tab
	 *
	 * @return Name of tab
	 */
	public String getName() {
		return labels.getString("PrefName");
	}

	/**
	 * Returns the currently selected fields
	 *
	 * @return The field names
	 */
	public String[] getFieldList() {
		return savedFields;
	}
	
	/**
	 * @see dk.jdai.gui.JdaiOptions#save()
	 */
	public boolean save() {
		savedFields = selectedFields.getKeyArray();
		prefs.putInt(PREF_COUNT,savedFields.length);
		for (int i=0;i<savedFields.length;i++) {
			prefs.put(PREF_PREFIX+i,savedFields[i]);
		}
		return true;
	}
	
	/**
	 * @see dk.jdai.gui.JdaiOptions#cancel()
	 */
	public void cancel() {
		setFields(savedFields);
	}
}
