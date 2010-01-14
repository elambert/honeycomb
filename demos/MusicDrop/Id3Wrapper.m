//
//  Id3Wrapper.m
//  MusicDrop
//
//  Created by Sacha Arnoud on 3/21/06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

#include <stdio.h>
#include <fcntl.h>

#import "Id3Wrapper.h"

#include "tag.h"
#include <tstring.h>

@implementation Id3Wrapper

-(id)initWithFilename:(NSString*)name
{
  [filename release];
  filename = name;
  [filename retain];
  const char *fname = [filename UTF8String];
  file = new TagLib::FileRef(fname);
  return(self);
}

-(NSString*)getTitle
{
  return([NSString stringWithCString: file->tag()->title().toCString(true)
                   encoding: NSUTF8StringEncoding]);
}

-(NSString*)getAlbum
{
  return([NSString stringWithCString: file->tag()->album().toCString(true)
                   encoding: NSUTF8StringEncoding]);
}

-(NSString*)getArtist
{
  return([NSString stringWithCString: file->tag()->artist().toCString(true)
                   encoding: NSUTF8StringEncoding]);
}

-(long)getYear
{
  return(file->tag()->year());
}

-(NSString*)get:(char*)frame
{
  return(nil);
}

-(void)dealloc
{
  [filename release];
  if (file) {
    delete(file);
    file = NULL;
  }
  [super dealloc];
}

@end
