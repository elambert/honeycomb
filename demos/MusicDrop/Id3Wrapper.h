//
//  Id3Wrapper.h
//  MusicDrop
//
//  Created by Sacha Arnoud on 3/21/06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

#import <Cocoa/Cocoa.h>

#include "fileref.h"

@interface Id3Wrapper : NSObject {
  NSString *filename;
  TagLib::FileRef *file;
}

-(id)initWithFilename:(NSString*)name;

-(NSString*)getTitle;
-(NSString*)getAlbum;
-(NSString*)getArtist;
-(long)getYear;

-(NSString*)get:(char*)frame;

@end
