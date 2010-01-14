#import "DropBox.h"
#import "Controller.h"
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>

#define ENCODING NSASCIIStringEncoding

@implementation DropBox

-(id)initWithFrame:(NSRect)rect
{
  self = [super initWithFrame:rect];
  [self setTitlePosition: NSNoTitle];
  [self registerForDraggedTypes: [NSArray arrayWithObjects: 
                                            @"CorePasteboardFlavorType 0x6974756E",
                                          nil]];
  return(self);
}

-(void)highlight:(BOOL)flag
{
  if (flag) {
    if (!defaultColor) {
      defaultColor = [label textColor];
      [defaultColor retain];
      [label setTextColor:[NSColor orangeColor]];
    }
  } else {
    if (defaultColor) {
      [label setTextColor:defaultColor];
      [defaultColor release];
      defaultColor = nil;
    }
  }     
}

-(NSDragOperation)draggingEntered:(id <NSDraggingInfo>)sender
{
  [self highlight:YES];
  return(NSDragOperationPrivate);
}

-(void)draggingExited:(id <NSDraggingInfo>)sender
{
  [self highlight:NO];
}

-(BOOL)prepareForDragOperation:(id <NSDraggingInfo>)sender
{
  return(YES);
}

-(BOOL)performDragOperation:(id <NSDraggingInfo>)sender
{
  [self highlight:NO];

  NSDictionary *iTunesDict = [[sender draggingPasteboard] propertyListForType
                                     :@"CorePasteboardFlavorType 0x6974756E"];
  NSDictionary *tracks = [iTunesDict objectForKey:@"Tracks"];
  [controller upload:[tracks allValues]];

  return(YES);
}

-(void)concludeDragOperation:(id <NSDraggingInfo>)sender
{
}

-(void)dealloc
{
  [super dealloc];
}

@end
