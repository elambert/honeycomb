//
//  Store.h
//  MusicDrop
//
//  Created by Sacha Arnoud on 3/24/06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

#import <Cocoa/Cocoa.h>
#include <hcclient.h>
#import "ThreadArg.h"

@protocol StoreCallback
-(void)storeInit:(int)nbOfSongs;
-(void)storeStatus:(int)nbUploaded
	     outOf:(int)totalToUpload
          withName:(NSString*)name;
-(void)storeCompleted;
-(void)storeFailed:(NSString*)msg;
@end

@interface Store : NSObject {
  NSString *clusterIP;
  NSArray *fileEntries;
  int currentIndex;
  int nbStored;
  NSLock *lock;
  NSConditionLock *done;
  id<StoreCallback> callback;
}

-(id)initIP:(NSString*)ip
   andFiles:(NSArray*)files
andCallback:(id)callback;

-(NSDictionary*)populateMetadata:(NSDictionary*)entry
	       withFile:(NSString*)filename;
-(void)doUpload:(id)arg;

-(void)threadStart:(ThreadArg*)arg;

@end
