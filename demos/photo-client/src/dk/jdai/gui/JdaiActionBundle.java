package dk.jdai.gui;

import java.util.*;
public class JdaiActionBundle extends ListResourceBundle {			 
	
	public Object[][] getContents() { 
		return contents;
	}
	static final Object[][] contents = { 
	   {   "Quit", "Quit" 	} ,
	   {   "Quit the application", "Quit the application" 	} ,
       {   "QuitMnemonic", new Character('Q')} ,
	   {   "About...", "About..." 	} ,
	   {   "About the application", "About the application" 	} ,
       {   "AboutMnemonic", new Character('A')} ,
	   {   "Options", "Options" 	} ,
	   {   "OptionsDesc", "Program Options" 	} ,
       {   "OptionsMnemonic", new Character('O')} ,
	   {   "Select Section", "Select Toplevel Section" 	} ,
	   {   "Select the photo section to explore", "Select the toplevel photo section to explore" 	} ,
       {   "SelectMnemonic", new Character('T')} ,
	   {   "No Rotation", "No Rotation" 	} ,
	   {   "Do not rotate the photo", "Do not rotate the photo" 	} ,
       {   "NoRotMnemonic", new Character('N')} ,
	   {   "Rotate Clockwise", "Rotate Clockwise" 	} ,
	   {   "Rotate the photo 90 degrees clockwise", "Rotate the photo 90 degrees clockwise" 	} ,
       {   "ClockRotMnemonic", new Character('R')} ,
	   {   "Turn Upside-Down", "Turn Upside-Down" 	} ,
	   {   "Turn the photo upside-down", "Turn the photo upside-down" 	} ,
       {   "UpsideMnemonic", new Character('U')} ,
	   {   "Rotate Counter-Clockwise", "Rotate Counter-Clockwise" 	} ,
	   {   "Rotate the photo 90 degrees counter-clockwise", "Rotate the photo 90 degrees counter-clockwise" 	} ,
	   {   "CounterRotMnemonic", new Character('O')} ,
	   {   "Rotate Right", "Rotate Right" 	} ,
	   {   "Rotate the photo to the right", "Rotate the photo to the right" 	} ,
	   {   "RotateRightMnemonic", new Character('R')} ,
	   {   "Rotate Left", "Rotate Left" 	} ,
	   {   "Rotate the photo to the left", "Rotate the photo to the left" 	} ,
	   {   "RotateLeftMnemonic", new Character('L')} ,
	   {   "View", "View" 	} ,
	   {   "View Photo", "View Photo" 	} ,
	   {   "ViewMnemonic", new Character('V')} ,
	   {   "Edit", "Edit" 	} ,
	   {   "Edit Photo", "Edit Photo Information" 	} ,
	   {   "EditMnemonic", new Character('E')} ,
	   {   "Delete", "Delete" 	} ,
	   {   "Delete Photo", "Delete Photo" 	} ,
	   {   "DeleteMnemonic", new Character('D')} ,
	   {   "Slideshow", "Slideshow" 	} ,
	   {   "Start Slideshow", "Start Slideshow" 	} ,
	   {   "SlideshowMnemonic", new Character('S')} ,
	   {   "PrevPhoto", "Previous" 	} ,
	   {   "PrevPhotoLong", "Previous Photo" 	} ,
	   {   "PrevPhotoMnemonic", new Character('P')} ,
	   {   "NextPhoto", "Next" 	} ,
	   {   "NextPhotoLong", "Next Photo" 	} ,
	   {   "NextPhotoMnemonic", new Character('N')} ,
	}; 
} 
