/* Controller */

#import <Cocoa/Cocoa.h>

#import "Store.h"

@interface Controller : NSObject <StoreCallback>
{
    IBOutlet id clusterTF;
    IBOutlet id dropMusicView;
    IBOutlet id dropPanel;
    IBOutlet id logDrawer;
    IBOutlet id logOutput;
    IBOutlet id preferencesWindow;
    IBOutlet id storeProgress;

    NSString *cluster;
    Store *store;
}
- (IBAction)changeDrawer:(id)sender;
- (IBAction)preferencesUpdated:(id)sender;
- (IBAction)updatePreferences:(id)sender;

-(void)addLog:(NSString*)log;
- (BOOL)upload:(NSArray*)entries;

@end
