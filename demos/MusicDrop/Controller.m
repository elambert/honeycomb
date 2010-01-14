#import "Controller.h"

@implementation Controller

- (void)applicationDidFinishLaunching:(NSNotification *)aNotification
{
  [self updatePreferences: nil];
}

-(void)awakeFromNib
{
  [self addLog:@"Ready to store music"];
}

- (IBAction)changeDrawer:(id)sender
{
  int state = [logDrawer state];
  if ((state == NSDrawerClosedState) || (state == NSDrawerClosingState)) {
    [logDrawer open];
  } else {
    [logDrawer close];
  }
}

- (IBAction)updatePreferences:(id)sender
{
  [[NSApplication sharedApplication] beginSheet:preferencesWindow
                                     modalForWindow: dropPanel
                                     modalDelegate: nil
                                     didEndSelector: nil
                                     contextInfo: nil];
}

- (IBAction)preferencesUpdated:(id)sender
{
  [cluster release];
  cluster = [[clusterTF stringValue] copy];
  [preferencesWindow orderOut:sender];
  [[NSApplication sharedApplication] endSheet: preferencesWindow];
  NSLog(@"Value is %@", cluster);
}

- (BOOL)upload:(NSArray*)entries
{
  [dropMusicView setHidden: YES];

  store = [[Store alloc] initIP:cluster
			 andFiles: entries
                         andCallback: self];
  [NSThread detachNewThreadSelector:@selector(doUpload:)
	    toTarget: store
	    withObject: nil];
  
  return(YES);
}

/* Store callback protocol */

-(void)storeInit:(int)nbOfSongs
{
  [storeProgress setIndeterminate: YES];
  [storeProgress setMinValue: 0];
  [storeProgress setMaxValue: nbOfSongs];
  [storeProgress setDoubleValue: 0];
  [storeProgress setUsesThreadedAnimation: YES];
  [storeProgress startAnimation: self];
  [storeProgress setHidden: NO];

  NSLog(@"Store init");
}

-(void)storeStatus:(int)nbUploaded
	     outOf:(int)totalToUpload
          withName:(NSString*)name
{
  if ([storeProgress isIndeterminate]) {
    [storeProgress stopAnimation: self];
    [storeProgress setIndeterminate: NO];
  }

  [storeProgress setDoubleValue: nbUploaded];
  [self addLog: name];
}

-(void)storeCompleted
{
  if ([storeProgress isIndeterminate]) {
    [storeProgress stopAnimation: self];
    [storeProgress setIndeterminate: NO];
  }
  [dropMusicView setHidden: NO];
  [storeProgress setHidden: YES];
  [store release];
  store = nil;

  NSLog(@"Store complete");
}

-(void)storeFailed:(NSString*)msg
{
  NSAlert *alert = [NSAlert alertWithMessageText:@"Upload error"
			    defaultButton: @"OK"
			    alternateButton: nil
			    otherButton: nil
			    informativeTextWithFormat: msg];
  [alert runModal];
  [self storeCompleted];
}

-(void)addLog:(NSString*)log
{
  [logOutput setEditable: YES];
  [logOutput insertText: log];
  [logOutput insertText:@"\n"];
  [logOutput setEditable: NO];
}

-(void)dealloc
{
  [cluster release];
  [store release];
  [super dealloc];
}

@end
